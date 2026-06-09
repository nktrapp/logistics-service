# Fluxo 3 — INBOX idempotente (camada da aplicação)

**Objetivo:** mostrar a **2ª camada** de proteção contra duplicata, dentro da
aplicação. Mesmo quando a mesma mensagem de negócio **chega ao consumidor** (porque
a dedup do broker não pegou), o efeito acontece **uma única vez**.

**O truque (e a ligação com o Fluxo 2):** no Fluxo 2, dedup-ids iguais eram
descartados pelo SQS. Aqui fazemos o oposto de propósito: enviamos o **mesmo
`eventId` no payload** com `MessageDeduplicationId` **diferentes**. Assim **as duas
mensagens passam pelo SQS e chegam ao consumidor** — e quem barra a segunda é o
**nosso inbox**, não o broker.

**Prova:** `routes` para o pacote = **1** e `inbox` para o `eventId` = **1**.

> Pré-requisitos: ambiente no ar (com o `logistics-service` rodando), variáveis do
> AWS CLI exportadas, `PKG_Q` definido.

---

## Passo 0 — (opcional) limpar o estado

```bash
docker compose exec -T mongodb mongosh logistics_db --quiet --eval '
JSON.stringify({inbox:db.inbox.deleteMany({}).deletedCount, routes:db.routes.deleteMany({}).deletedCount})'
```

## Passo 1 — enviar o MESMO `eventId` duas vezes, com dedup-ids DIFERENTES

```bash
BODY='{"eventId":"evt-inbox-1","eventType":"package.created","payload":{"packageId":"pkg-inbox-1","senderCep":"89010000","recipientCep":"01310100"}}'

# 1ª entrega
$SQS send-message --queue-url "$PKG_Q" \
  --message-group-id pkg-inbox-1 \
  --message-deduplication-id "$(uuidgen)" \
  --message-body "$BODY"

# 2ª entrega — MESMO eventId, dedup-id NOVO (dribla a dedup do SQS de propósito)
$SQS send-message --queue-url "$PKG_Q" \
  --message-group-id pkg-inbox-1 \
  --message-deduplication-id "$(uuidgen)" \
  --message-body "$BODY"
```

Como o `MessageGroupId` é o mesmo (`pkg-inbox-1`), o FIFO entrega as duas **em
ordem**, uma de cada vez.

## Passo 2 — aguardar o processamento

```bash
sleep 5
```

## Passo 3 — provar que houve UM único efeito

```bash
docker compose exec -T mongodb mongosh logistics_db --quiet --eval '
JSON.stringify({
  inbox_para_o_evento:  db.inbox.countDocuments({eventId:"evt-inbox-1"}),
  rotas_para_o_pacote:  db.routes.countDocuments({packageId:"pkg-inbox-1"})
}, null, 2)'
```

Esperado:

```json
{
  "inbox_para_o_evento": 1,
  "rotas_para_o_pacote": 1
}
```

## Passo 4 — ver no log a segunda entrega sendo ignorada

```bash
docker compose logs logistics-service | grep -i "already processed"
```

Esperado: uma linha do tipo
`[calculate-route] Event evt-inbox-1 already processed, skipping`.

---

## Como funciona por dentro (fala para a banca)

> "O `eventId` é a chave de idempotência. Logo na entrada o consumidor faz um
> *pre-check* (`existsByEventId`); e dentro da transação faz `saveIfAbsent` na
> coleção `inbox`, que tem índice único no `eventId`. A primeira entrega cria a rota
> e crava o `eventId`; a segunda encontra o `eventId` já gravado e não faz nada. O
> ponto fino: o claim do inbox está na **mesma transação** do efeito — se a
> transação desse rollback, o claim voltava atrás e a mensagem poderia ser
> reprocessada. Idempotência sem inconsistência."

> Por que precisamos disso mesmo tendo a dedup do SQS (Fluxo 2)? Porque a dedup do
> broker só cobre uma janela de 5 minutos e exige o mesmo dedup-id. Entregas
> *at-least-once* em sistemas distribuídos (redelivery por timeout, retry, falha
> parcial) podem trazer o mesmo evento de negócio fora dessas condições — e aí só o
> inbox segura.
