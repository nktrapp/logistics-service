# logistics-service

Microsserviço de **logística e roteirização**. Mantém a malha de _hubs_ (centros de
distribuição) e as conexões entre eles, e calcula a **melhor rota** para cada pacote
usando **Dijkstra** sobre esse grafo. Resolve CEPs em cidade/estado via **ViaCEP**.

Comunica-se com o `package-service` exclusivamente de forma **assíncrona**, por mensagens
SQS FIFO (nunca por HTTP direto).

- Porta local: **http://localhost:8082** (via `docker compose`, container expõe 8080)
- Banco: MongoDB (`logistics_db`) — precisa ser **replica set** (transações do outbox/inbox)
- Cache: Redis (cache de CEPs do ViaCEP, TTL 24h)
- Mensageria: SQS FIFO (MiniStack no ambiente local)

---

## Arquitetura (hexagonal, 4 módulos Gradle)

`domain` → `application` (use cases) → `infrastructure` → `app`. As dependências apontam
para dentro; o `domain` é Java puro + Jakarta Validation.

- **domain**: entidades (`Hub`, `HubConnection`, `Route`, `CepInfo`), eventos, exceções e _ports_.
- **application**: casos de uso + o serviço de cálculo `RouteCalculationService` (Dijkstra).
- **infrastructure**: adaptadores Mongo, integração ViaCEP, Redis, SQS, outbox/inbox, `MongoConfig`.
- **app**: classe principal, controllers REST, `GlobalExceptionHandler` (RFC-7807), `UseCaseConfig`.

---

## Conceitos de domínio

| Conceito | Descrição |
|----------|-----------|
| **Hub** | Centro de distribuição. Tem `name`, `cep`, `city`, `state`, coordenadas e `active`. Cidade/estado são resolvidos pelo ViaCEP no cadastro. |
| **HubConnection** | Aresta **bidirecional** entre dois hubs, com `distanceKm` e `transitTimeHours`. É a "estrada" do grafo. |
| **Route** | Rota calculada para um pacote: hub de origem, hub de destino, lista de `hops` (saltos na ordem), distância total e tempo estimado. |

---

## Regras de negócio

1. **Cadastro de hub** exige `name` (3–100 caracteres) e `cep` (8 dígitos). A cidade e o
   estado **não** são informados pelo cliente: são obtidos no **ViaCEP** a partir do CEP.
   Se o ViaCEP não encontrar o CEP, o cadastro é rejeitado.
2. **Cadastro de conexão** exige que **os dois hubs já existam** (senão `404`), `distanceKm > 0`
   e `transitTimeHours > 0`. A conexão vale nos **dois sentidos** (basta cadastrar uma vez).
3. **Seleção de hubs candidatos a partir de um CEP** (origem e destino do pacote):
   - resolve-se o CEP em **cidade/estado** via ViaCEP;
   - candidatos preferenciais: hubs ativos na **mesma cidade**;
   - se não houver, _fallback_ para hubs ativos no **mesmo estado**;
   - se não houver hub no estado, **não há candidato** → a rota falha (ver "Falhas tratadas").
   - ⚠️ **Não existe seleção por proximidade geográfica.** Um hub fisicamente próximo, mas
     em **outro estado**, não é considerado. As coordenadas do hub não são usadas no
     roteamento (um _fallback_ por haversine exigiria geocodificar o CEP, o que o ViaCEP não
     fornece).
4. **Cálculo da rota (Dijkstra):** entre todos os pares (candidato de origem × candidato de
   destino), escolhe-se a rota de **menor distância total**; em caso de empate, a de **menor
   tempo de trânsito** (e, por fim, critérios determinísticos de desempate por nome/id, para
   resultado estável).
5. **Recálculo (mudança de destino):** o remetente não muda, então:
   - se já existe rota, **mantém-se o hub de origem** original e recalcula-se apenas o destino;
   - se ainda não existe rota, a origem é derivada do **CEP do remetente** (igual ao cálculo
     inicial) — **nunca** se usa um hub arbitrário.
6. **Idempotência:** todo evento consumido é registrado no **inbox** por `eventId`. Reentregas
   do mesmo evento são ignoradas (processa-se uma única vez).
7. **Publicação confiável (outbox):** os eventos de saída (`route.calculated`,
   `route.recalculated`, `route.failed`) são gravados na coleção **outbox** na mesma transação
   da escrita da rota, e publicados de forma assíncrona por um _relay_ agendado (com retry).

---

## Fluxos de negócio

### Fluxo 1 — Cadastro de hub
`POST /api/v1/hubs` → valida `name`/`cep` → consulta **ViaCEP** (com cache no Redis) →
preenche cidade/estado → persiste o hub → `201 Created`.

### Fluxo 2 — Cadastro de conexão
`POST /api/v1/hubs/connections` → valida existência dos dois hubs (`404` se faltar) →
valida `distanceKm`/`transitTimeHours` positivos → persiste a aresta (bidirecional) → `201`.

### Fluxo 3 — Cálculo de rota (consome `package.created`)
1. `package-service` publica `package.created` na fila `package-events-queue.fifo`.
2. `LogisticsEventListenerAdapter` consome; verifica o **inbox** (idempotência).
3. Resolve `senderCep` e `recipientCep` em cidade/estado (ViaCEP).
4. Seleciona hubs candidatos (mesma cidade → mesmo estado) para origem e destino.
5. Roda Dijkstra e escolhe a melhor rota (menor distância, depois menor tempo).
6. Em **uma transação**: grava a `Route` (status `CALCULATED`) + escreve `route.calculated`
   no outbox + marca o inbox.
7. O _relay_ publica `route.calculated` em `logistics-events-queue.fifo`.
8. Se não houver rota/hub elegível → publica **`route.failed`** (ver "Falhas tratadas").

### Fluxo 4 — Recálculo de rota (consome `package.destination.changed`)
Igual ao Fluxo 3, mas dispara na mudança de destino. Mantém o hub de origem se a rota já
existe; recalcula o destino; grava a rota (preservando `id`/`createdAt`) e publica
**`route.recalculated`** — ou `route.failed` se o novo destino não for roteável.

### Fluxo 5 — Consulta de rota
- `GET /api/v1/routes/{id}` — busca por id da rota.
- `GET /api/v1/routes?packageId=...` — busca a rota de um pacote (`400` sem o parâmetro,
  `404` se não houver rota).

---

## Mensageria

| Direção | Fila | Eventos |
|---------|------|---------|
| **Consome** | `package-events-queue.fifo` | `package.created`, `package.destination.changed` |
| **Produz**  | `logistics-events-queue.fifo` | `route.calculated`, `route.recalculated`, `route.failed` |

FIFO: `MessageGroupId = packageId` (ordem por pacote), `MessageDeduplicationId = eventId`.
Garantias: **outbox** (publicação atômica + retry) e **inbox** (idempotência no consumo).

---

## Falhas tratadas

| Situação | Tipo | Comportamento |
|----------|------|---------------|
| CEP inexistente no ViaCEP / sem hub na cidade e no estado / grafo sem caminho | **Permanente** | Publica `route.failed` e **confirma (ACK)** a mensagem — o `package-service` move o pacote para `FAILED`. Não vai para a DLQ. |
| ViaCEP indisponível (rede) | **Transitória** | A exceção propaga, a mensagem é **reentregue** (até `maxReceiveCount`) e, persistindo, vai para a **DLQ**. |
| Conexão referenciando hub inexistente | Validação | `404` no cadastro. |
| `name`/`cep`/`distanceKm`/`transitTimeHours` inválidos | Validação | `400` (RFC-7807). |

---

## Endpoints

| Método | Caminho | Sucesso |
|--------|---------|---------|
| POST | `/api/v1/hubs` | 201 |
| GET | `/api/v1/hubs` | 200 |
| GET | `/api/v1/hubs/{id}` | 200 / 404 |
| POST | `/api/v1/hubs/connections` | 201 / 404 |
| GET | `/api/v1/routes/{id}` | 200 / 404 |
| GET | `/api/v1/routes?packageId=` | 200 / 400 / 404 |

Actuator em `/management` (`health`, `info`, `metrics`, `loggers`).

---

## Data Seeder (perfil `local`)

`HubDataSeeder` popula um **grafo padrão** no startup quando o banco está vazio (idempotente,
só no perfil `local`). Útil para demonstração: os fluxos de pacote já funcionam sem cadastrar
hubs à mão.

```
Hub São Paulo (SP) ──410km/6h── Hub Curitiba (PR) ──130km/3h── Hub Joinville (SC) ──90km/2h── Hub Blumenau (SC)
                                       │                                                              │
                                       └───────────────300km/5h─── Hub Florianópolis (SC) ──140km/3h──┘
```

Cidades usadas (CEP de exemplo): Blumenau `89010000`, Joinville `89201000`,
Florianópolis `88010000`, Curitiba `80010000`, São Paulo `01310100`. Os nomes de cidade
batem com o que o ViaCEP retorna, de modo que pacotes com esses CEPs casam com os hubs.

---

## Como executar e testar

```bash
# stack completo (os dois serviços + Mongo + Redis + MiniStack), a partir da raiz do repositório:
docker compose up -d --build
```

Testes HTTP prontos em [`http/logistics.http`](http/logistics.http). Guia de validação
manual ponta a ponta em [`../VALIDATION.md`](../VALIDATION.md).

