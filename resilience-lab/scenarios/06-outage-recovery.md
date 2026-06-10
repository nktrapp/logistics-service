# 06 — Recuperação após indisponibilidade de consumidor e de broker

## Objetivo

Provar, em duas partes, que o sistema **não perde eventos** durante indisponibilidades e **converge sozinho** após a recuperação:

- **Parte A (consumidor fora):** com o `logistics-service` parado, os eventos `package.created` se acumulam no broker (entrega *at-least-once* + buffering do SQS). Ao religar o consumidor, todos os pacotes convergem para `ROUTE_CALCULATED` com **exatamente uma rota** cada (idempotência via inbox) e **nada cai na DLQ**.
- **Parte B (broker fora):** com o MiniStack pausado, a API REST continua aceitando escritas (o commit local no Mongo é independente do broker) e o evento fica **estacionado no outbox** (`status=PENDING`). Essa é exatamente a janela que o padrão *transactional outbox* elimina: sem ele, "commit local OK + publish falhou" significaria evento perdido. Quando o broker volta, o relay drena o outbox e o pacote converge fim-a-fim.

## Invariantes

- **I5** — Nenhum evento é perdido durante indisponibilidade: ou está retido no broker (Parte A) ou estacionado no outbox (Parte B).
- **I6** — A escrita local não depende do broker; após a recuperação, o outbox é drenado e o evento é entregue.
- **I8** — O sistema converge ao estado final esperado (`ROUTE_CALCULATED`) após a recuperação, sem mensagens indo parar na DLQ.
- **I10** — Idempotência: exatamente **uma** rota por pacote, mesmo com redeliveries acumuladas durante a indisponibilidade.

## Pré-condições

- Stack local de pé: `docker compose up -d` (package-service :8081, logistics-service :8082, MiniStack :4566, MongoDB :27017 em replica set).
- Internet disponível (ViaCEP real).
- `aws` CLI, `jq` e `docker compose` no host.
- Relay do outbox do package-service ativo (publica a cada 2s com backoff).

## Passos manuais (copy-paste)

### Parte A — consumidor fora do ar

```bash
PKG_Q=$(aws --endpoint-url http://localhost:4566 sqs get-queue-url \
  --queue-name package-events-queue.fifo --query QueueUrl --output text)

# 1) Derrubar o consumidor
docker compose stop logistics-service

# 2) Profundidade da fila ANTES (baseline)
aws --endpoint-url http://localhost:4566 sqs get-queue-attributes \
  --queue-url "$PKG_Q" --attribute-names ApproximateNumberOfMessages \
  --query 'Attributes.ApproximateNumberOfMessages' --output text

# 3) Criar 3 pacotes via REST (continuam funcionando: CREATED)
for DEST in 89201000 80010000 01310100; do
  curl -s -X POST http://localhost:8081/api/v1/packages \
    -H 'Content-Type: application/json' \
    -d '{"senderCep":"89010000","recipientCep":"'$DEST'","weight":1.0,"description":"demo outage"}' | jq -r .id
done
# guarde os 3 ids impressos

# 4) A fila cresce (eventos retidos no broker — repita o passo 2 e compare)

# 5) Religar o consumidor e acompanhar a convergência
docker compose start logistics-service
curl -s http://localhost:8081/api/v1/packages/<ID> | jq .status   # esperado: ROUTE_CALCULATED (para cada id)

# 6) Exatamente UMA rota por pacote (idempotência)
docker compose exec mongodb mongosh logistics_db --quiet \
  --eval 'db.routes.countDocuments({packageId:"<ID>"})'           # esperado: 1

# 7) DLQ continua vazia
PKG_DLQ=$(aws --endpoint-url http://localhost:4566 sqs get-queue-url \
  --queue-name package-events-dlq.fifo --query QueueUrl --output text)
aws --endpoint-url http://localhost:4566 sqs get-queue-attributes \
  --queue-url "$PKG_DLQ" --attribute-names ApproximateNumberOfMessages \
  --query 'Attributes.ApproximateNumberOfMessages' --output text   # esperado: 0
```

### Parte B — broker fora do ar (outbox segura o evento)

```bash
# 1) Baseline do outbox
docker compose exec mongodb mongosh package_db --quiet \
  --eval 'db.outbox.countDocuments({status:"PENDING"})'

# 2) Pausar o broker (congela o processo — conexões passam a falhar)
docker compose pause ministack

# 3) A API REST continua aceitando escritas (commit local independente do broker)
PKG=$(curl -s -X POST http://localhost:8081/api/v1/packages \
  -H 'Content-Type: application/json' \
  -d '{"senderCep":"89010000","recipientCep":"80010000","weight":1.0,"description":"demo broker down"}' | jq -r .id)
echo "packageId=$PKG"   # esperado: HTTP 201 + id valido

# 4) O evento fica estacionado no outbox (PENDING cresce; nada de chamadas aws aqui!)
docker compose exec mongodb mongosh package_db --quiet \
  --eval 'db.outbox.countDocuments({status:"PENDING"})'
docker compose logs --since 1m package-service | grep -iE "outbox|relay|publish"

# 5) Religar o broker -> relay drena o outbox
docker compose unpause ministack
docker compose exec mongodb mongosh package_db --quiet \
  --eval 'db.outbox.countDocuments({status:"PENDING"})'            # volta ao baseline

# 6) Convergência fim-a-fim + idempotência
curl -s http://localhost:8081/api/v1/packages/$PKG | jq .status    # esperado: ROUTE_CALCULATED
docker compose exec mongodb mongosh logistics_db --quiet \
  --eval "db.routes.countDocuments({packageId:\"$PKG\"})"          # esperado: 1
```

## Saída esperada

Parte A:

- `PASS I8 status of <id> while consumer down` (x3) — pacotes criados ficam `CREATED`.
- `PASS I5 events accumulated in broker while consumer down` — profundidade da fila cresce (ou `SKIP` se o emulador não reportar `ApproximateNumberOfMessages`).
- `PASS I8 <id> converged after consumer recovery` (x3) e `PASS I10 exactly one route for <id>` (x3).
- `PASS I8 DLQ delta after recovery` — DLQ inalterada.

Parte B:

- `PASS I6 local write succeeded while broker down` — HTTP 201 com o broker pausado.
- `PASS I5 event parked in outbox while broker unreachable` — `PENDING` cresce (ou evidência de backoff via `FAILED`).
- `PASS I6 outbox drained after broker recovery` — `PENDING` volta ao baseline.
- `PASS I8 package converged end-to-end after broker recovery` e `PASS I10 exactly one route`.

## O que dizer para a banca

> "A Parte A demonstra a entrega *at-least-once* com buffering no broker: derrubamos o consumidor, criamos três pacotes — a API segue respondendo e os eventos se acumulam na fila. Ao religar o consumidor, tudo converge sozinho, com exatamente uma rota por pacote (o inbox deduplica redeliveries) e DLQ intacta. A Parte B ataca a janela clássica de *dual write*: broker fora do ar no exato momento do commit. Sem outbox, o serviço comitaria o pacote no Mongo e perderia o evento ao falhar o publish. Com o *transactional outbox*, o evento é gravado **na mesma transação** do pacote; com o broker pausado, ele fica estacionado como `PENDING` (I5 — nada se perde) e, assim que o broker volta, o relay o entrega e o fluxo converge fim-a-fim (I6/I8). Ou seja: indisponibilidade de infraestrutura vira apenas latência, nunca perda de dados."
