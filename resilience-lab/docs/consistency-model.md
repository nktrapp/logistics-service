# Modelo de consistência

## Consistência forte local × eventual entre serviços

O sistema combina dois regimes, com a fronteira exatamente na borda do serviço:

- **Dentro de cada serviço: consistência forte.** O MongoDB roda como **replica set** e
  toda escrita de consumo/comando acontece numa **transação multi-documento**:
  claim do inbox + estado de domínio + evento na outbox commitam ou abortam **juntos**
  (é por isso que o `MongoTransactionSupportVerifier` derruba o startup se o Mongo não for
  replica set). Não existe estado intermediário observável dentro do serviço.
- **Entre serviços: consistência eventual.** Nenhuma chamada síncrona cruza a fronteira;
  o que cruza são eventos at-least-once via SQS FIFO. Entre o commit no produtor e o commit
  no consumidor existe uma **janela de inconsistência** em que as duas bases divergem — por
  design, não por acidente.

## Ownership dos dados

| Dado | Dono (source of truth) | Réplica/projeção |
|---|---|---|
| `Route` (hubs, distância, tempo) | **logistics-service** (`logistics_db.routes`) | `RouteInfo` embutido no pacote (`package_db`) — projeção **eventualmente consistente** |
| `Package` (status, CEPs, peso) | **package-service** (`package_db`) | o logistics não replica o pacote; só reage aos eventos dele |
| Grafo de hubs/conexões | **logistics-service** | — |

A pergunta "qual é a rota do pacote?" tem duas respostas com semânticas diferentes:
`GET /api/v1/routes?packageId=` (verdade do dono) e o `routeInfo` em
`GET /api/v1/packages/{id}` (projeção, pode estar atrasada pela janela). O cenário
`01-happy-path` verifica que as duas convergem.

## Janela de inconsistência

- **Nominal (sem falha): ~2–6 s.** Composição: tick do relay da outbox (`fixedDelay`
  default 2 s) + trânsito no SQS + consumo + tick do relay do outro lado. É o tempo típico
  entre `201 Created` e o pacote aparecer `ROUTE_CALCULATED`.
- **Sob falha de consumo:** cada tentativa malsucedida devolve a mensagem à fila após o
  `visibilityTimeout`; o teto antes da quarentena é
  **`maxReceiveCount × visibilityTimeout = 3 × 60 s = 180 s`** — depois disso a mensagem vai
  para a DLQ, onde fica retida por **14 dias** (1 209 600 s, definidos no módulo
  `sqs-contracts` da IaC) aguardando diagnóstico/redrive.
- **Sob falha de publicação:** o relay tenta com backoff exponencial (5 s × 2ⁿ, teto 60 s)
  até 5 tentativas; depois o evento vira **`FAILED` na outbox** — estado explícito que
  **exige intervenção manual** (resetar o documento para `PENDING`) e é monitorável pelo
  gauge `outbox.failed.count`. Importante: `FAILED` também **bloqueia o grupo** no relay
  (ordem acima de liveness — ver I9), então a intervenção é parte do modelo, não um buraco
  nele.

## PACELC e a ausência de 2PC

Na taxonomia PACELC, o sistema entre serviços é **PC/EL**:

- **P→C (partição: consistência de cada lado, indisponibilidade do todo):** se o broker ou
  o consumidor somem, nenhum lado inventa estado — o produtor continua aceitando escritas
  (e acumulando outbox `PENDING`), o consumidor processa o que tem; o *fluxo conjunto* fica
  indisponível, mas cada base permanece internamente consistente e a recuperação converge
  (cenário `06-outage-recovery`). Não há fallback que sacrifique consistência para
  responder.
- **E→L (sem partição: latência em vez de consistência global):** em operação normal o
  sistema **escolhe latência**: o `POST /packages` responde `201` em milissegundos e a
  consistência entre as bases chega ~2–6 s depois. A alternativa (esperar a rota para
  responder) acoplaria a disponibilidade do package-service ao ViaCEP e ao Dijkstra.

**Por que não 2PC (two-phase commit)?** Um commit distribuído entre Mongo do
package-service, SQS e Mongo do logistics-service exigiria um coordenador, travaria
recursos durante a janela de prepare (o oposto de E→L), e SQS sequer participa de XA. Mais
grave: 2PC converte falha parcial em **indisponibilidade total** (participante preso em
prepare bloqueia todos). O padrão outbox/inbox alcança a mesma atomicidade *observável* —
"estado mudou ⇔ evento existe" — com commits estritamente **locais**: a atomicidade global
é substituída por (commit local) + (entrega at-least-once) + (idempotência no destino).
O preço é a janela de inconsistência; o lab existe para demonstrar que esse preço é pago de
forma controlada (I8, I10).

## Deduplicação técnica × idempotência de negócio

Distinção central da defesa — são duas coisas diferentes que costumam ser confundidas:

| | Deduplicação técnica (broker) | Idempotência de negócio (aplicação) |
|---|---|---|
| Onde | SQS FIFO, `MessageDeduplicationId = eventId` | inbox (`saveIfAbsent` upsert `$setOnInsert`) + máquina de estados + guard causal |
| Escopo | **janela de 5 minutos**, por fila | ilimitado no tempo (inbox TTL 30 d ≫ retenção fila 4 d + DLQ 14 d) |
| O que dedupa | o **mesmo send** repetido (mesmo id) | o **mesmo fato de negócio**, ainda que reentregue, reenviada fora da janela, ou com dedup-id diferente |
| Falha que não cobre | reentrega ao consumidor (receive ≠ send), retry do relay > 5 min, replay manual da DLQ | — (é a camada final) |
| Papel | **otimização** (menos tráfego/ruído) | **garantia de correção** |

O cenário `02-broker-dedup` demonstra a camada técnica e seu limite (mesmo conteúdo com
dedup-id novo entra de novo); o `03-inbox-idempotency` demonstra que, quando o broker deixa
passar, a aplicação segura. E mesmo que **ambas** falhassem para um evento de rota repetido,
a terceira camada — máquina de estados (`ROUTE_CALCULATED → ROUTE_CALCULATED` proibida) e
guard de `destinationCep` — ainda tornaria o efeito inaplicável. Defesa em profundidade:
correção nunca depende do broker.
