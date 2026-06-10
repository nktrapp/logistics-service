# Cenário 04 — Evento antigo não sobrescreve estado novo

## Objetivo

Provar a **guarda causal** do `package-service`: um `route.calculated` que se
refere a um destino **antigo** do pacote (evento causalmente obsoleto, "stale")
é detectado, **descartado com ACK** e não sobrescreve o estado atual. O cenário
cria um pacote, troca o destino (gerando recálculo), e então forja um
`route.calculated` com o CEP **anterior** — simulando um evento atrasado que
chega depois do recálculo legítimo.

## Invariantes

- **I1** — a máquina de estados do pacote não regride: o status permanece
  `ROUTE_CALCULATED`.
- **I4** — guarda causal: `destinationCep` do evento ≠ `recipientCep` atual ⇒
  o evento é reconhecido como stale e ignorado (`Stale route for package ...
  skipping` no log); o `recipientCep` não muda.
- **I10** — o evento stale é **ACKado**, não envenenado: `routeInfo` continua
  refletindo o destino atual e a DLQ não cresce.

## Pré-condições

- Stack local no ar: `docker compose up` (package-service `:8081`,
  logistics-service `:8082`, MiniStack `:4566`).
- Internet disponível (ViaCEP) — o fluxo legítimo de roteirização precisa
  funcionar antes de injetarmos o evento stale.
- `aws` CLI, `curl` e `jq`; credenciais dummy exportadas
  (`AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_DEFAULT_REGION=us-east-1`).

## Passos manuais (copy-paste)

```bash
SQS="aws --endpoint-url http://localhost:4566 sqs"

# 1) criar um pacote Blumenau -> Joinville
PKG=$(curl -s -X POST http://localhost:8081/api/v1/packages \
  -H 'Content-Type: application/json' \
  -d '{"senderCep":"89010000","recipientCep":"89201000","weight":1.0,"description":"lab 04"}' \
  | jq -r .id)
echo "$PKG"

# 2) aguardar a primeira rota (status ROUTE_CALCULATED, ~5-15s)
curl -s http://localhost:8081/api/v1/packages/$PKG | jq '{status, recipientCep}'

# 3) trocar o destino para São Paulo (dispara recálculo)
curl -s -X PATCH http://localhost:8081/api/v1/packages/$PKG/destination \
  -H 'Content-Type: application/json' -d '{"newCep":"01310100"}'

# 4) aguardar o recálculo (status volta a ROUTE_CALCULATED, recipientCep 01310100)
curl -s http://localhost:8081/api/v1/packages/$PKG | jq '{status, recipientCep, routeInfo}'

# 5) base da DLQ ANTES de injetar o evento stale
LOG_DLQ=$($SQS get-queue-url --queue-name logistics-events-dlq.fifo --query QueueUrl --output text)
$SQS get-queue-attributes --queue-url "$LOG_DLQ" --attribute-names ApproximateNumberOfMessages

# 6) forjar um route.calculated STALE: destinationCep aponta para o destino ANTIGO
LOG_Q=$($SQS get-queue-url --queue-name logistics-events-queue.fifo --query QueueUrl --output text)
STALE='{"eventId":"44444444-4444-4444-4444-444444444444","eventType":"route.calculated","occurredAt":"2026-01-01T00:00:00Z","source":"resilience-lab","version":"1.0","payload":{"packageId":"'$PKG'","destinationCep":"89201000","totalDistanceKm":1.0,"estimatedTransitHours":1,"hops":[{"name":"Hub Lab Stale"}]}}'
$SQS send-message --queue-url "$LOG_Q" --message-body "$STALE" \
  --message-group-id "$PKG" --message-deduplication-id lab04-stale-1

# 7) o package-service detecta e descarta (I4)
docker compose logs package-service | grep "Stale route for package"

# 8) o estado NÃO mudou (I1, I4, I10)
curl -s http://localhost:8081/api/v1/packages/$PKG | jq '{status, recipientCep, routeInfo}'
# esperado: status ROUTE_CALCULATED, recipientCep 01310100,
# routeInfo SEM "Hub Lab Stale"

# 9) a DLQ não cresceu (I10): o stale foi ACKado, não envenenado
$SQS get-queue-attributes --queue-url "$LOG_DLQ" --attribute-names ApproximateNumberOfMessages
```

## Saída esperada

```
[PASS] I4 stale event detected and skipped (log: 'Stale route for package')
[PASS] I4 recipientCep unchanged: expected 01310100, got 01310100
[PASS] I1 status did not regress: expected ROUTE_CALCULATED, got ROUTE_CALCULATED
[PASS] I10 route info still reflects current destination (hubs: ...)
[PASS] I10 DLQ delta: expected 0, got 0
```

## O que dizer para a banca

Este é o cenário central da tese. Em um sistema assíncrono, um evento pode
chegar **depois** de ter deixado de ser verdade: aqui, uma rota calculada para
o destino antigo chega quando o pacote já aponta para outro CEP. Há três
respostas possíveis, e duas estão erradas:

1. **Aplicar o evento** — corrompe o estado: o pacote exibiria a rota do destino
   errado (violação silenciosa, a pior categoria de falha).
2. **Lançar exceção** — o evento não é "inválido", é apenas obsoleto; a exceção
   força reentrega, esgota o `maxReceiveCount` e transforma um evento legítimo
   em poison message na DLQ — e, em fila FIFO, **bloqueia o grupo inteiro**
   (todos os eventos futuros daquele pacote) atrás dele.
3. **Guarda causal + descarte com ACK** (a escolha do projeto): o produtor
   carimba o evento com `destinationCep` (o contexto causal em que a rota foi
   calculada); o consumidor compara com o `recipientCep` **atual**. Divergiu?
   O evento é stale — loga `Stale route for package ... skipping` e confirma a
   mensagem. Nada quebra, nada bloqueia, nada corrompe.

A lição de projeto: **máquina de estados estrita** (o status nunca regride — I1)
combinada com **consumidor tolerante** (eventos causalmente obsoletos são
descartados como operação normal, não como erro — I4/I10). Exceção é para falha
inesperada; obsolescência causal é um caso de negócio previsto. A DLQ fica
reservada para o que realmente precisa de intervenção humana.
