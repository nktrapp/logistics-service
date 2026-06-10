# Cenário 03 — Inbox torna o consumo idempotente

## Objetivo

Provar que o **inbox transacional** do `logistics-service` garante processamento
único por `eventId`, mesmo quando o broker entrega o mesmo evento mais de uma vez
(o cenário 02 mostrou que isso é possível e esperado). O mesmo envelope
`package.created` é entregue duas vezes — com `MessageDeduplicationId` diferentes,
para passar pelo dedup do broker — e o efeito colateral (rota calculada) acontece
**exatamente uma vez**.

## Invariantes

- **I2** — efeito colateral único: um evento processado duas vezes não produz
  duas rotas.
- **I3** — o duplicado é detectado pelo inbox (`eventId` já registrado) e
  descartado com ACK, sem reprocessar e sem envenenar a fila.

## Pré-condições

- Stack local no ar: `docker compose up`.
- Internet disponível (o cálculo de rota consulta o ViaCEP).
- `aws` CLI e `jq` instalados; credenciais dummy exportadas
  (`AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_DEFAULT_REGION=us-east-1`).

## Passos manuais (copy-paste)

```bash
SQS="aws --endpoint-url http://localhost:4566 sqs"
PKG_Q=$($SQS get-queue-url --queue-name package-events-queue.fifo --query QueueUrl --output text)

# 1) envelope package.created com eventId FIXO (é ele que o inbox indexa)
EVENT_ID="33333333-3333-3333-3333-333333333333"
PKG_ID="demo-p03"
BODY='{"eventId":"'$EVENT_ID'","eventType":"package.created","occurredAt":"2026-01-01T00:00:00Z","source":"resilience-lab","version":"1.0","payload":{"packageId":"'$PKG_ID'","senderCep":"89010000","recipientCep":"89201000"}}'

# 2) primeira entrega
$SQS send-message --queue-url "$PKG_Q" --message-body "$BODY" \
  --message-group-id "$PKG_ID" --message-deduplication-id entrega-1

# 3) aguardar a rota ser calculada (ViaCEP + Dijkstra são assíncronos)
docker compose exec mongodb mongosh logistics_db --quiet \
  --eval "db.routes.countDocuments({packageId:'$PKG_ID'})"
# repetir até retornar 1 (~5-15s)

# 4) REENTREGAR o MESMO envelope (mesmo eventId, dedupId NOVO → fura o broker)
$SQS send-message --queue-url "$PKG_Q" --message-body "$BODY" \
  --message-group-id "$PKG_ID" --message-deduplication-id entrega-2

# 5) após ~15s: continua existindo UMA rota (I2)
docker compose exec mongodb mongosh logistics_db --quiet \
  --eval "db.routes.countDocuments({packageId:'$PKG_ID'})"

# 6) o log mostra o inbox rejeitando o duplicado (I3)
docker compose logs logistics-service | grep "already processed, skipping"

# 7) o inbox tem UMA entrada para o eventId, não duas (I3)
docker compose exec mongodb mongosh logistics_db --quiet \
  --eval "db.inbox.countDocuments({eventId:'$EVENT_ID'})"
```

## Saída esperada

```
[PASS] I2 routes after duplicate: expected 1, got 1
[PASS] I3 duplicate eventId rejected by inbox (log: 'already processed, skipping')
[PASS] I3 inbox entries for event: expected 1, got 1
```

Se o passo 3 nunca chegar a `1`, o problema é a primeira entrega (ViaCEP fora do
ar / sem internet) — o cenário aborta com erro de setup, não com FAIL, porque a
invariante nem chegou a ser exercitada.

## O que dizer para a banca

O cenário 02 mostrou que a fronteira do broker é at-least-once: duplicatas
**vão acontecer** (retry fora da janela de dedup, visibility timeout expirado,
replay operacional). Este cenário mostra a defesa da aplicação: antes de
processar, o consumidor registra o `eventId` numa coleção **inbox**, na mesma
transação Mongo que grava a rota e o outbox. Na segunda entrega, o `eventId` já
existe; o consumidor loga `already processed, skipping` e **confirma (ACK)** a
mensagem sem reexecutar nada. O resultado: efeito colateral exatamente-uma-vez
construído sobre transporte at-least-once — que é o único exactly-once que
existe na prática em sistemas distribuídos. A prova é tripla: uma rota, uma
entrada no inbox e o log de descarte.
