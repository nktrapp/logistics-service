# Fluxo 2 — Deduplicação FIFO do SQS (camada do broker)

**Objetivo:** mostrar a **1ª camada** de proteção contra duplicata, que acontece
**antes** da aplicação: a fila FIFO descarta mensagens com o mesmo
`MessageDeduplicationId` dentro de uma janela de **5 minutos**
(`content_based_deduplication=false`, então a dedup é pela chave que enviamos).

**Prova determinística:** vamos **parar o consumidor** para as mensagens se
acumularem na fila e **contar** quantas realmente entraram. Mesmo `dedup-id` → o
SQS conta **1**. `dedup-id` diferente → conta **2**.

> Pré-requisitos: ambiente no ar, variáveis do AWS CLI exportadas, `PKG_Q` definido.

---

## Passo 1 — parar o consumidor (para as mensagens ficarem visíveis na fila)

```bash
docker compose stop logistics-service
```

Atalho para ler a profundidade da fila:

```bash
depth() { $SQS get-queue-attributes --queue-url "$PKG_Q" \
  --attribute-names ApproximateNumberOfMessages \
  --query 'Attributes.ApproximateNumberOfMessages' --output text; }

echo "baseline: $(depth)"
```

## Passo 2 — enviar 2× com o MESMO `MessageDeduplicationId`

```bash
for i in 1 2; do
  $SQS send-message --queue-url "$PKG_Q" \
    --message-group-id fifo-dedup-demo \
    --message-deduplication-id DUP-FIXO-A \
    --message-body '{"eventId":"fifo-same","eventType":"package.created","payload":{"packageId":"pkg-fifo-same","senderCep":"89010000","recipientCep":"01310100"}}'
done

sleep 3
echo "após 2 envios com dedup-id IGUAL: $(depth)"
```

Esperado: a profundidade aumentou em **1** (a 2ª mensagem foi **descartada pelo
SQS** — o `send-message` até "sucede", mas nenhuma mensagem nova é enfileirada).

## Passo 3 — enviar 2× com `MessageDeduplicationId` DIFERENTES

```bash
for i in 1 2; do
  $SQS send-message --queue-url "$PKG_Q" \
    --message-group-id fifo-dedup-demo \
    --message-deduplication-id "$(uuidgen)" \
    --message-body "{\"eventId\":\"fifo-diff-$i\",\"eventType\":\"package.created\",\"payload\":{\"packageId\":\"pkg-fifo-diff-$i\",\"senderCep\":\"89010000\",\"recipientCep\":\"01310100\"}}"
done

sleep 3
echo "após 2 envios com dedup-id DIFERENTE: $(depth)"
```

Esperado: a profundidade aumentou em **2** (ambas entraram).

## Resumo da contagem

| Envio | dedup-id | Mensagens que entraram |
|---|---|---|
| 2× iguais | `DUP-FIXO-A` (fixo) | **1** (SQS descartou a 2ª) |
| 2× diferentes | `uuidgen` | **2** |

## Passo 4 — religar o consumidor

```bash
docker compose start logistics-service
```

(As mensagens acumuladas serão processadas; a do `eventId` repetido será depois
barrada pelo **inbox** — é o assunto do próximo fluxo.)

---

## Fala para a banca

> "A primeira linha de defesa contra duplicata é o próprio SQS FIFO: enviei a mesma
> mensagem duas vezes com o mesmo `MessageDeduplicationId` e a fila só aceitou uma,
> dentro de uma janela de 5 minutos. Isso protege contra reenvios do **produtor**.
> Mas essa janela é curta e depende do dedup-id ser igual — se o mesmo evento de
> negócio chegar com um dedup-id diferente, ou depois de 5 minutos, o broker não
> pega. Para isso existe a segunda camada, na aplicação: o **inbox**."

> Como isso aparece no nosso relay (publicações de saída): ele envia com
> `MessageGroupId = packageId` (ordem por pacote) e `MessageDeduplicationId =`
> `eventId do envelope`. Como o consumidor a jusante usa esse mesmo `eventId` no
> próprio inbox, a dedup do broker e a idempotência da aplicação se reforçam — usam
> a mesma identidade de evento. Já na **entrada** (que esta demo manipula), o
> produtor controla o group-id e o dedup-id separadamente; foi por isso que
> conseguimos isolar cada camada aqui.
