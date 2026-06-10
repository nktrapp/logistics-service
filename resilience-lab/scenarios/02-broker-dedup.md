# Cenário 02 — Dedup do broker FIFO não é exactly-once

## Objetivo

Provar que a deduplicação do SQS FIFO (`MessageDeduplicationId`) é uma **janela de
conveniência de 5 minutos**, não uma garantia de exactly-once. O broker descarta o
reenvio *imediato* com o mesmo `MessageDeduplicationId`, mas qualquer envio com um
id de deduplicação diferente (retry do produtor após a janela, replay manual,
reprocessamento) entra na fila normalmente. A fronteira do broker é, na prática,
**at-least-once** — quem fecha a lacuna é o inbox da aplicação (cenário 03).

## Invariantes

- **I9** — a entrega é at-least-once; o dedup do broker é apenas uma janela de 5
  minutos sobre `MessageDeduplicationId`, não exactly-once fim-a-fim.

## Pré-condições

- Stack local no ar: `docker compose up` (MiniStack em `:4566`).
- `aws` CLI e `jq` instalados; credenciais dummy exportadas
  (`AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_DEFAULT_REGION=us-east-1`).
- O consumidor (`logistics-service`) será **parado** durante o teste para que a
  profundidade da fila seja mensurável.

## Passos manuais (copy-paste)

```bash
# 0) atalho para o endpoint do emulador
SQS="aws --endpoint-url http://localhost:4566 sqs"

# 1) parar o consumidor para a profundidade ser observável
docker compose stop logistics-service

# 2) URL e profundidade base da fila de entrada do logistics
PKG_Q=$($SQS get-queue-url --queue-name package-events-queue.fifo --query QueueUrl --output text)
$SQS get-queue-attributes --queue-url "$PKG_Q" \
  --attribute-names ApproximateNumberOfMessages

# 3) montar um envelope sintético de package.created (eventId fixo)
BODY='{"eventId":"11111111-1111-1111-1111-111111111111","eventType":"package.created","occurredAt":"2026-01-01T00:00:00Z","source":"resilience-lab","version":"1.0","payload":{"packageId":"demo-p02","senderCep":"89010000","recipientCep":"89201000"}}'

# 4) enviar DUAS vezes com o MESMO MessageDeduplicationId
$SQS send-message --queue-url "$PKG_Q" --message-body "$BODY" \
  --message-group-id demo-p02 --message-deduplication-id dedup-fixo-1
$SQS send-message --queue-url "$PKG_Q" --message-body "$BODY" \
  --message-group-id demo-p02 --message-deduplication-id dedup-fixo-1

# 5) conferir: profundidade subiu apenas +1 (o segundo envio foi deduplicado)
$SQS get-queue-attributes --queue-url "$PKG_Q" \
  --attribute-names ApproximateNumberOfMessages

# 6) enviar a MESMA mensagem com um MessageDeduplicationId NOVO
$SQS send-message --queue-url "$PKG_Q" --message-body "$BODY" \
  --message-group-id demo-p02 --message-deduplication-id dedup-novo-2

# 7) conferir: profundidade subiu +2 no total — o broker aceitou o "duplicado"
$SQS get-queue-attributes --queue-url "$PKG_Q" \
  --attribute-names ApproximateNumberOfMessages

# 8) limpeza: esvaziar a fila ANTES de religar o consumidor
$SQS purge-queue --queue-url "$PKG_Q"
docker compose start logistics-service
```

## Saída esperada

```
[PASS] I9 same dedupId deduplicated by broker (depth N -> N+1 after 2 sends)
[PASS] I9 distinct dedupId enqueues again (at-least-once at broker boundary): expected N+2, got N+2
```

Se o emulador não implementar a janela de dedup FIFO (a profundidade vai para
`+2` já no passo 5), o cenário emite `SKIP` para a primeira verificação — isso é
uma limitação do emulador, não uma falha do sistema. A segunda verificação
(id distinto entra de novo) continua valendo.

## O que dizer para a banca

O SQS FIFO deduplica por `MessageDeduplicationId` dentro de uma janela de **5
minutos**. Isso protege contra um caso específico: o retry imediato do produtor
(ex.: timeout de rede no `SendMessage`). Não protege contra reenvio após a
janela, replay operacional, nem contra a **reentrega ao consumidor** (visibility
timeout expirado antes do ACK). Ou seja: exactly-once fim-a-fim **não existe** na
fronteira do broker — o contrato real é at-least-once. A garantia de
processamento único é responsabilidade da aplicação, via **inbox transacional**
indexado por `eventId` — exatamente o que o cenário 03 demonstra. Este cenário
existe para mostrar que confiar só no dedup do broker seria um falso conforto.
