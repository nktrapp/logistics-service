# 05 — Poison message: isolamento na DLQ sem bloquear outros agregados

## Objetivo

Provar que uma mensagem estruturalmente inválida ("poison message" — sem `eventId`/`eventType`, quebrando o contrato do envelope) **não derruba o consumidor nem bloqueia o fluxo dos demais pacotes**: após esgotar as tentativas de redelivery, ela é movida para a DLQ, e somente o **MessageGroupId dela** fica bloqueado durante as tentativas. Também demonstra que um *redrive* ingênuo (devolver a mensagem para a fila principal sem corrigi-la) apenas a faz voltar para a DLQ — poison continua poison.

## Invariantes

- **I7** — Mensagens que violam o contrato são isoladas na DLQ após `maxReceiveCount` tentativas, sem perda silenciosa e sem bloquear outros grupos de mensagens (outros agregados continuam convergindo).

**Fórmula do bloqueio (FIFO + RedrivePolicy):**

```
bloqueio máximo do grupo = maxReceiveCount × visibilityTimeout
```

- Em produção: `3 × 60s = 180s` de bloqueio máximo **apenas do grupo da mensagem poison**.
- No experimento: o script reduz o `VisibilityTimeout` para 5s, logo `3 × 5s = 15s`, para a demo caber na janela de tempo.
- Importante: em uma fila FIFO, **somente o `MessageGroupId` da mensagem poison fica bloqueado** enquanto ela é re-entregue. Todos os outros grupos (= outros `packageId`) continuam fluindo normalmente — é exatamente isso que o passo do "pacote saudável" comprova.

## Pré-condições

- Stack local de pé: `docker compose up -d` (package-service :8081, logistics-service :8082, MiniStack :4566, MongoDB :27017).
- Internet disponível (ViaCEP real é consultado para o pacote saudável).
- `aws` CLI e `jq` instalados no host (para os passos manuais).
- DLQ `package-events-dlq.fifo` vazia (o script drena antes de medir).

## Passos manuais (copy-paste)

```bash
# 0) URLs das filas
PKG_Q=$(aws --endpoint-url http://localhost:4566 sqs get-queue-url \
  --queue-name package-events-queue.fifo --query QueueUrl --output text)
PKG_DLQ=$(aws --endpoint-url http://localhost:4566 sqs get-queue-url \
  --queue-name package-events-dlq.fifo --query QueueUrl --output text)

# 1) Encurtar o ciclo de redelivery (3 x 5s em vez de 3 x 60s)
aws --endpoint-url http://localhost:4566 sqs set-queue-attributes \
  --queue-url "$PKG_Q" --attributes VisibilityTimeout=5

# 2) Injetar a mensagem poison (sem eventId/eventType -> consumidor lança exceção)
aws --endpoint-url http://localhost:4566 sqs send-message \
  --queue-url "$PKG_Q" \
  --message-body '{"poison":true}' \
  --message-group-id poison-demo \
  --message-deduplication-id "poison-$(date +%s)"

# 3) Imediatamente, criar um pacote saudável (outro MessageGroupId)
PKG=$(curl -s -X POST http://localhost:8081/api/v1/packages \
  -H 'Content-Type: application/json' \
  -d '{"senderCep":"89010000","recipientCep":"88010000","weight":1.5,"description":"demo poison"}' | jq -r .id)
echo "packageId=$PKG"

# 4) Acompanhar a DLQ ate a poison chegar (~15s com visibility 5s; ate ~180s com 60s)
watch -n 2 "aws --endpoint-url http://localhost:4566 sqs get-queue-attributes \
  --queue-url $PKG_DLQ --attribute-names ApproximateNumberOfMessages \
  --query 'Attributes.ApproximateNumberOfMessages' --output text"

# 5) Conferir o conteudo e o numero de recebimentos da poison na DLQ
aws --endpoint-url http://localhost:4566 sqs receive-message \
  --queue-url "$PKG_DLQ" --visibility-timeout 0 \
  --attribute-names ApproximateReceiveCount | jq .

# 6) Provar que o pacote saudavel convergiu ENQUANTO a poison era re-entregue
curl -s http://localhost:8081/api/v1/packages/$PKG | jq .status   # esperado: ROUTE_CALCULATED

# 7) Redrive ingenuo: reenviar o corpo da DLQ para a fila principal -> ela volta para a DLQ
BODY=$(aws --endpoint-url http://localhost:4566 sqs receive-message \
  --queue-url "$PKG_DLQ" --visibility-timeout 0 | jq -r '.Messages[0].Body')
aws --endpoint-url http://localhost:4566 sqs send-message \
  --queue-url "$PKG_Q" --message-body "$BODY" \
  --message-group-id poison-demo --message-deduplication-id "redrive-$(date +%s)"
# (apague-a da DLQ com receive-message + delete-message, e repita o passo 4)

# 8) Limpeza: drenar a DLQ e restaurar o VisibilityTimeout
aws --endpoint-url http://localhost:4566 sqs purge-queue --queue-url "$PKG_DLQ"
aws --endpoint-url http://localhost:4566 sqs set-queue-attributes \
  --queue-url "$PKG_Q" --attributes VisibilityTimeout=60

# Logs do consumidor durante as tentativas:
docker compose logs --since 5m logistics-service | grep -iE "poison|deserial|invalid|error"
```

## Saída esperada

- `PASS I7 poison moved to DLQ (visible count)` — a poison aparece na DLQ depois de ~3 recebimentos.
- `PASS I7 receives before DLQ` (>= 3) — **ou** `SKIP` se o emulador não reportar `ApproximateReceiveCount`.
- `PASS I7 healthy aggregate converged while poison retried (group isolation)` — o pacote saudável chega a `ROUTE_CALCULATED` mesmo com a poison em retry.
- `PASS I7 redrive of a contract-broken message returns it to DLQ (poison stays poison)`.
- Se o MiniStack **não implementar** RedrivePolicy/SetQueueAttributes, o script emite `SKIP` com a evidência observada (poison ainda na fila principal) — nunca um PASS falso nem um FAIL culpando a aplicação. Nesse caso o script drena a fila para não contaminar os cenários seguintes.

## O que dizer para a banca

> "Injetamos uma mensagem que viola o contrato do envelope — sem `eventId` nem `eventType`. O consumidor falha de forma controlada e o SQS re-entrega; após `maxReceiveCount = 3` tentativas a mensagem é movida automaticamente para a DLQ, sem perda silenciosa. O ponto-chave da fila FIFO é o isolamento por grupo: o bloqueio máximo é `maxReceiveCount × visibilityTimeout` — 3×60s em produção, 3×5s no experimento — e **só vale para o MessageGroupId da mensagem poison**. Provamos isso criando um pacote saudável durante as re-entregas: ele convergiu para `ROUTE_CALCULATED` normalmente. Por fim, mostramos que devolver a mensagem da DLQ sem corrigi-la apenas a faz voltar para a DLQ: a DLQ não é um mecanismo de 'retry mágico', é uma quarentena que exige intervenção humana sobre a causa raiz."
