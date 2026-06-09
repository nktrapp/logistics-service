# Fluxo 4 — Dead-Letter Queue (DLQ)

**Objetivo:** mostrar que uma mensagem que falha **sempre** (poison message) não
fica em loop infinito: depois de `maxReceiveCount = 3` tentativas, o SQS a move para
a fila `package-events-dlq.fifo`, onde fica isolada para análise.

**Prova:** a mensagem aparece, **visível**, na DLQ — e some da fila principal.

> Pré-requisitos: ambiente no ar (com `logistics-service` rodando), variáveis do
> AWS CLI exportadas, `PKG_Q` e `PKG_DLQ` definidos (ver `demo/README.md`).

---

## Passo 1 — acelerar a demo: baixar o VisibilityTimeout para 5s

Com os 60s padrão, 3 reentregas levariam ~3 minutos. Para a demo, 5s → ~15s.

```bash
$SQS set-queue-attributes --queue-url "$PKG_Q" --attributes VisibilityTimeout=5
```

## Passo 2 — enviar uma mensagem INVÁLIDA (sem `packageId`)

Falta `payload.packageId`. O listener estoura no parsing **antes** de qualquer
lógica de negócio — então falha de forma idêntica em toda entrega.

```bash
$SQS send-message --queue-url "$PKG_Q" \
  --message-group-id poison-grp \
  --message-deduplication-id "$(uuidgen)" \
  --message-body '{"eventId":"poison-1","eventType":"package.created","payload":{}}'
```

> Usamos um `MessageGroupId` isolado (`poison-grp`) para a mensagem-veneno não
> bloquear a ordem de outros pacotes (no FIFO, a cabeça travada de um grupo segura o
> grupo inteiro até ela ir para a DLQ).

## Passo 3 — acompanhar a falha nos logs (opcional)

```bash
docker compose logs -f logistics-service | grep -i "Error processing message"
# Ctrl-C após ver ~3 ocorrências
```

## Passo 4 — esperar e conferir a DLQ

```bash
sleep 20

$SQS get-queue-attributes --queue-url "$PKG_DLQ" \
  --attribute-names ApproximateNumberOfMessages \
  --query 'Attributes.ApproximateNumberOfMessages' --output text
```

Esperado: **1**.

## Passo 5 — ler a mensagem isolada na DLQ (corpo + nº de recebimentos)

```bash
$SQS receive-message --queue-url "$PKG_DLQ" \
  --max-number-of-messages 1 --visibility-timeout 0 \
  --attribute-names ApproximateReceiveCount \
  --query 'Messages[0].{recebimentos:Attributes.ApproximateReceiveCount, corpo:Body}'
```

Esperado: o corpo da mensagem inválida e `recebimentos` ≈ `3` (a DLQ não tem
consumidor — por isso a mensagem **fica lá, visível**).

## Passo 6 — restaurar o VisibilityTimeout

```bash
$SQS set-queue-attributes --queue-url "$PKG_Q" --attributes VisibilityTimeout=60
```

---

## Negócio x veneno (fala para a banca — diferença importante)

Nem toda falha vai para a DLQ. Há duas naturezas:

| Tipo de falha | Exemplo | O que o serviço faz | Vai para DLQ? |
|---|---|---|---|
| **Negócio** (esperada) | CEP inexistente, sem hub alcançável | trata e emite `route.failed` no outbox | **Não** |
| **Veneno / infra** (inesperada) | mensagem malformada, falha persistente | lança exceção → não confirma → reentrega → esgota tentativas | **Sim** |

> "Erro de negócio é um resultado válido do processamento — viramos um evento
> `route.failed` e seguimos. Já uma mensagem que o sistema não consegue processar de
> jeito nenhum não pode ficar em loop nem sumir: depois de 3 tentativas ela é
> isolada na DLQ, onde um operador pode inspecionar e reprocessar. A DLQ é a rede de
> segurança do consumo."

### (Opcional) ver o caminho de negócio: `route.failed`

Mande um CEP válido mas de uma cidade **sem hub** (ex.: `69900000`, Rio Branco/AC):

```bash
$SQS send-message --queue-url "$PKG_Q" \
  --message-group-id pkg-bizfail \
  --message-deduplication-id "$(uuidgen)" \
  --message-body '{"eventId":"evt-bizfail-1","eventType":"package.created","payload":{"packageId":"pkg-bizfail","senderCep":"69900000","recipientCep":"01310100"}}'

sleep 5
docker compose exec -T mongodb mongosh logistics_db --quiet --eval '
db.outbox.find({eventType:"route.failed"}, {status:1, groupId:1, _id:0}).sort({createdAt:-1}).limit(1).toArray()'
```

Esperado: um evento `route.failed` no outbox — **e nada na DLQ** para esse caso.
