# Fluxo 1 — OUTBOX transacional

**Objetivo:** mostrar que a entidade (rota) e o evento (`route.calculated`) são
gravados na **mesma transação** e que a publicação no broker acontece **depois**,
feita por um relay que só lê o que já foi commitado. Nunca há "salvou no banco mas
não publicou" nem "publicou mas o banco deu rollback".

**Prova:** a linha na coleção `outbox` nasce `PENDING` e vira `PUBLISHED`; a rota
aparece em `routes`. Provamos pelo **estado durável**, não por espiar a fila.

> Pré-requisitos: ambiente no ar e variáveis do AWS CLI exportadas (ver
> `demo/README.md`, seções 3). `PKG_Q` já definido.

---

## Passo 0 — (opcional) limpar o estado

```bash
docker compose exec -T mongodb mongosh logistics_db --quiet --eval '
JSON.stringify({inbox:db.inbox.deleteMany({}).deletedCount, outbox:db.outbox.deleteMany({}).deletedCount, routes:db.routes.deleteMany({}).deletedCount})'
```

## Passo 1 — publicar um `package.created` (simula o package-service)

Pacote de **Blumenau (89010000)** para **São Paulo (01310100)** — ambos têm hub
semeado, então a rota é calculável.

```bash
$SQS send-message \
  --queue-url "$PKG_Q" \
  --message-group-id pkg-outbox-1 \
  --message-deduplication-id "$(uuidgen)" \
  --message-body '{"eventId":"evt-outbox-1","eventType":"package.created","payload":{"packageId":"pkg-outbox-1","senderCep":"89010000","recipientCep":"01310100"}}'
```

O consumidor (`SqsLogisticsEventListener`) recebe, resolve os CEPs no ViaCEP, roda
o Dijkstra **fora** da transação e então, **numa transação só**, grava:
`inbox` (claim) + `routes` (a rota) + `outbox` (`route.calculated`).

## Passo 2 — ver a linha do OUTBOX em `PENDING` (corra: o relay está em 8s)

Rode **logo após** o envio:

```bash
docker compose exec -T mongodb mongosh logistics_db --quiet --eval '
db.outbox.find({groupId:"pkg-outbox-1"}, {eventType:1, status:1, _id:0}).toArray()'
```

Esperado (dentro dos primeiros ~8s):

```json
[ { "eventType": "route.calculated", "status": "PENDING" } ]
```

> **Esse é o ponto-chave.** O evento já está commitado no banco, junto com a rota,
> mas ainda **não** foi para o SQS. Se o processo morrer agora, ele será publicado
> assim que voltar — nada se perde.

## Passo 3 — ver virar `PUBLISHED` (após o relay rodar)

Espere ~10s e repita:

```bash
docker compose exec -T mongodb mongosh logistics_db --quiet --eval '
db.outbox.find({groupId:"pkg-outbox-1"}, {eventType:1, status:1, publishedAt:1, _id:0}).toArray()'
```

Esperado:

```json
[ { "eventType": "route.calculated", "status": "PUBLISHED", "publishedAt": "..." } ]
```

## Passo 4 — confirmar a rota persistida (mesmo commit)

```bash
docker compose exec -T mongodb mongosh logistics_db --quiet --eval '
db.routes.find({packageId:"pkg-outbox-1"}, {originHubId:1, destinationHubId:1, totalDistanceKm:1, estimatedTransitHours:1, "hops.hubName":1, _id:0}).toArray()'
```

Esperado: 1 rota (Blumenau → … → São Paulo), com `hops`, distância e tempo.

## Passo 5 — (opcional, rigoroso) ver o evento entregue no SQS

A fila de saída `logistics-events-queue.fifo` é consumida pelo **package-service**.
Para *ver* a mensagem lá, pare-o antes:

```bash
docker compose stop package-service

# reenvie um package.created com OUTRO packageId/eventId e espere ~10s, depois:
OUT_Q=$($SQS get-queue-url --queue-name logistics-events-queue.fifo --query QueueUrl --output text)
$SQS receive-message --queue-url "$OUT_Q" --max-number-of-messages 1 --visibility-timeout 0 \
  --query 'Messages[0].Body' --output text

docker compose start package-service
```

Esperado: um envelope JSON com `"eventType":"route.calculated"` e o `payload` da rota.

---

## Fala para a banca

> "A rota e o evento são escritos na mesma transação Mongo. Se qualquer parte
> falhasse, as duas voltavam atrás — nunca existe rota sem evento nem evento órfão.
> A entrega ao broker é assíncrona: um relay agendado lê só linhas já commitadas e
> publica, marcando `PUBLISHED`. Isso elimina o *dual-write* (gravar no banco e
> publicar no broker em dois passos que podem falhar no meio)."

## Bônus — o OUTBOX do hub (feature de malha automática)

O mesmo padrão dispara a malha de hubs. Cadastrar um hub grava `hub.created` no
outbox na mesma transação; o relay publica na fila **interna** `hub-events-queue.fifo`
(sem consumidor concorrente — 100% determinístico), e o consumo gera as conexões.

```bash
curl -s -X POST http://localhost:8082/api/v1/hubs \
  -H 'Content-Type: application/json' \
  -d '{"name":"Hub Itajai Demo","cep":"88301000"}'
echo

# pegue o id no retorno acima e veja o outbox do hub.created virar PUBLISHED:
docker compose exec -T mongodb mongosh logistics_db --quiet --eval '
db.outbox.find({eventType:"hub.created"}, {status:1, groupId:1, _id:0}).sort({createdAt:-1}).limit(1).toArray()'

# e as conexões criadas pelo consumo do evento:
docker compose exec -T mongodb mongosh logistics_db --quiet --eval '
db.hub_connections.find({}, {originHubId:1, destinationHubId:1, distanceKm:1, _id:0}).sort({_id:-1}).limit(5).toArray()'
```
