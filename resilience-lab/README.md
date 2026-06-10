# Laboratório de Resiliência e Consistência Eventual para Validação de um Sistema Distribuído Assíncrono

> Abordagem **Jepsen-lite**: o sistema é tratado como **caixa-preta** (só APIs HTTP, filas SQS e
> coleções Mongo observáveis de fora), um **nemesis** injeta falhas controladas (parar/pausar
> contêineres, forjar eventos, mensagens poison) e um **verificador** checa invariantes de
> consistência (I1–I10) emitindo vereditos `PASS`/`FAIL`/`SKIP`/`ERROR`.

## O que é e por que existe

Este laboratório existe para **defender com evidência executável** as garantias de mensageria
do sistema (TCC): dois microsserviços Spring Boot **nativos** (GraalVM) que conversam por
eventos sobre **SQS FIFO** com **outbox/inbox transacionais** sobre MongoDB replica set.

Afirmar "o sistema é idempotente e converge" em slide é barato. O lab transforma cada
afirmação em um **experimento reproduzível**: injeta a falha, observa o sistema apenas pelas
bordas e dá um veredito. As garantias em si (at-least-once, dedup em camadas, ordem por
agregado, janela de inconsistência) estão documentadas na seção **"Garantias e trade-offs de
mensageria"** dos READMEs dos dois serviços — o lab **não as repete, as testa**:

- [`logistics-service/README.md`](../README.md) (seção "Garantias e trade-offs de mensageria")
- [`package-service/README.md`](../../package-service/README.md) (idem)

## Pré-requisitos

| Item | Observação |
|---|---|
| Docker + Docker Compose v2 | o stack inteiro sobe via compose |
| bash ≥ 3.2 | os scripts são compatíveis com o bash do macOS |
| `curl` e `jq` | usados pela lib de asserções |
| AWS CLI (opcional) | sem ela, a lib usa a imagem `amazon/aws-cli` via `docker run` na rede `logistics-service_default` (fallback automático, ver `lib/common.sh`) |
| Internet | o ViaCEP é uma **chamada real** — sem internet os cenários que criam pacote terminam em `ERROR` (pré-condição), não em `FAIL` |

## Quickstart

```bash
cd logistics-service/resilience-lab

# roda tudo: sobe o stack (compose.yml + compose.override.yml + compose.lab.yml),
# executa os cenários 00–06 em ordem e imprime o relatório agregado
./run-chaos-demo.sh

# só um cenário, sem subir o stack de novo (assume stack saudável)
./run-chaos-demo.sh --scenario 04 --no-up

# ao final, derruba o stack e apaga os volumes (estado zerado na próxima execução)
./run-chaos-demo.sh --down-volumes
```

Flags do runner (`run-chaos-demo.sh`): `--scenario <NN>` (filtra por prefixo numérico),
`--no-up` (não sobe o stack; falha se ele não estiver saudável), `--keep` (no-op explícito —
manter o stack de pé já é o padrão), `--down-volumes` (faz `down -v` no final).

Os resultados ficam em `.results/run-<timestamp>.results` (e uma cópia em
`.results/latest.results`), uma linha `RESULT|cenário|invariante|veredito|detalhe` por
asserção — fácil de versionar e de colar na monografia.

## Estrutura de pastas

```
resilience-lab/
├── README.md                ← você está aqui
├── run-chaos-demo.sh        ← runner: sobe stack, executa cenários, agrega vereditos
├── compose.lab.yml          ← override mínimo: fixa portas 8081/8082 no host
├── lib/
│   └── common.sh            ← caixa-preta + nemesis: HTTP, SQS (com fallback docker),
│                              mongosh, caos de contêiner, asserções e relatório
├── scenarios/
│   ├── 00-baseline.sh       ← pré-condições (sem caos)
│   ├── 01-happy-path.sh     ← fluxo feliz ponta a ponta
│   ├── 02-broker-dedup.sh   ← dedup do broker FIFO
│   ├── 03-inbox-idempotency.sh
│   ├── 04-stale-route.sh    ← cenário central da defesa
│   ├── 05-poison-dlq.sh
│   └── 06-outage-recovery.sh
├── docs/
│   ├── invariants.md        ← catálogo I1–I10 + matriz de rastreabilidade (peça central)
│   ├── distributed-systems.md ← arquitetura, fluxos de eventos, máquina de estados
│   ├── consistency-model.md ← forte local × eventual global, PACELC, dedup × idempotência
│   ├── failure-scenarios.md ← tabela falha → comportamento → invariante → evidência
│   └── chaos-experiments.md ← hipótese/método/métrica por cenário + riscos do emulador
└── requests/
    ├── http-client.env.json ← ambiente "lab" (hosts 8081/8082)
    ├── 00-health.http       ← health/liveness/readiness dos dois serviços
    ├── 10-hubs.http         ← hubs e conexões (logistics-service)
    ├── 20-packages.http     ← ciclo de vida do pacote (package-service)
    └── 30-routes.http       ← consulta de rotas (logistics-service)
```

## Invariantes (resumo)

O catálogo completo — enunciado formal, mecanismo no código, evidência e violação
hipotética — está em [`docs/invariants.md`](docs/invariants.md).

| Tag | Invariante (resumo) |
|---|---|
| **I1** | Pacote nunca regride de estado (máquina de estados `PackageStatus`) |
| **I2** | Duplicata não gera efeito duplicado (inbox upsert `$setOnInsert` transacional) |
| **I3** | Evento já processado não é reaplicado (dedup por `eventId` na mesma transação do efeito) |
| **I4** | Evento antigo não sobrescreve estado novo (guard causal `destinationCep` × `recipientCep`) |
| **I5** | Evento gravado na outbox não se perde (mesma transação Mongo + relay com retry/backoff) |
| **I6** | Falha pós-commit/pré-publicação é recuperável (relay retoma `PENDING`/`IN_PROGRESS`, claim atômico) |
| **I7** | Mensagem poison é isolada na DLQ sem bloquear o fluxo (bloqueio ≤ `maxReceiveCount × visibility`, só no grupo) |
| **I8** | Após a recuperação da falha, o sistema converge |
| **I9** | FIFO ordena por agregado mas não é exactly-once; a aplicação não depende do dedup do broker |
| **I10** | O estado final é determinístico sob retries, duplicatas e concorrência |

## Cenários (caos → invariantes)

| Cenário | Caos injetado | Invariantes |
|---|---|---|
| `00-baseline` | nenhum — valida liveness, as 6 filas e a `RedrivePolicy` | PRE |
| `01-happy-path` | nenhum — criar pacote → rota calculada → outbox drenada | I5, I8 |
| `02-broker-dedup` | reenvio com o **mesmo** `MessageDeduplicationId` e depois com um novo | I9 |
| `03-inbox-idempotency` | reentrega do **mesmo `eventId`** com dedup-id novo (fura o broker) | I2, I3 |
| `04-stale-route` | `route.calculated` **forjado** com `destinationCep` antigo após mudança de destino | I1, I4, I10 |
| `05-poison-dlq` | JSON inválido na fila (poison) com visibility reduzida para a demo | I7 |
| `06-outage-recovery` | (A) consumidor parado com fila acumulando; (B) broker pausado com outbox acumulando | I5, I6, I8, I10 |

## Como ler o veredito

| Veredito | Significado |
|---|---|
| `PASS` | a asserção da invariante foi verificada no sistema real |
| `FAIL` | a invariante foi **violada** — bug ou regressão; o runner sai com código ≠ 0 |
| `SKIP` | **limitação do emulador detectada e explicada** — a asserção não é verificável neste ambiente |
| `ERROR` | pré-condição do experimento falhou (ex.: ViaCEP sem internet, stack não saudável) — o resultado não diz nada sobre a invariante |

**Regra de honestidade do `SKIP`:** o MiniStack emula SQS, não é SQS. Quando um cenário
depende de um comportamento que o emulador pode não implementar (janela de dedup FIFO,
enforcement de redrive, `ApproximateReceiveCount`...), o script **detecta** a ausência em
runtime e marca `SKIP` com a explicação — nunca um falso `PASS`. A lista de riscos (R1–R4) e a
estratégia detect-and-SKIP estão em [`docs/chaos-experiments.md`](docs/chaos-experiments.md).
`ERROR` é diferente de `FAIL` pelo mesmo motivo: pré-condição quebrada não é evidência contra
a invariante.

## Relação com `demo/` e com os testes Java

- **`logistics-service/demo/`** — runbooks manuais (copiar e colar) que demonstram outbox,
  dedup FIFO, inbox e DLQ um a um, com inspeção visual via mongo-express. O lab **generaliza**
  essa ideia: mesmos pilares, porém **automatizados, com asserções e veredito**, cobrindo
  também cenários que o demo não cobre (stale route, outage/recovery, concorrência). O demo
  permanece — continua sendo o melhor formato para apresentação guiada em banca.
- **Testes Java** — cada invariante tem também evidência em teste de unidade/integração
  (Testcontainers com Mongo replica set). O lab valida as mesmas propriedades **de fora**,
  com os binários nativos reais, broker real (emulado) e rede real. A matriz invariante ×
  teste Java × cenário está em [`docs/invariants.md`](docs/invariants.md).

## Portas fixas

O `compose.lab.yml` fixa as portas no host: **package-service em `:8081`** e
**logistics-service em `:8082`** (MiniStack SQS em `:4566`, Mongo em `:27017`). Os scripts e
os arquivos `requests/*.http` assumem essas portas.
