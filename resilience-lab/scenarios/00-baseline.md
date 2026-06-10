# Cenário 00 — Baseline (pré-condições do stack)

## Objetivo

Provar que o ambiente está pronto para os cenários de caos: os dois serviços nativos respondem ao liveness probe, as 6 filas SQS FIFO existem no MiniStack e a fila principal de pacotes tem redrive policy apontando para a DLQ com `maxReceiveCount=3`. Nenhum caos é injetado aqui — este cenário só valida o terreno.

## Invariantes provadas

- **PRE** — pré-condições do laboratório: serviços vivos, filas resolvíveis, redrive policy configurada. Se PRE falha, os resultados dos demais cenários não são confiáveis.

## Pré-condições

- Docker + Docker Compose instalados.
- Stack de pé. O runner sobe automaticamente, ou manualmente a partir da raiz do repositório:

```bash
docker compose -f compose.yml -f compose.override.yml -f resilience-lab/compose.lab.yml up -d
```

(omita `-f compose.override.yml` se o arquivo não existir)

- `aws` CLI no host é opcional: sem ela a lib usa a imagem `amazon/aws-cli` via `docker run` na rede `logistics-service_default`.

## Passos manuais (copy-paste)

```bash
# 1. liveness dos dois serviços (esperado: HTTP 200 nos dois)
curl -i http://localhost:8081/management/health/liveness
curl -i http://localhost:8082/management/health/liveness

# 2. credenciais fake do MiniStack
export AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_DEFAULT_REGION=us-east-1 AWS_PAGER=""

# 3. resolver as 6 filas (esperado: uma URL não vazia por fila)
for q in package-events-queue.fifo logistics-events-queue.fifo hub-events-queue.fifo \
         package-events-dlq.fifo logistics-events-dlq.fifo hub-events-dlq.fifo; do
  aws --endpoint-url http://localhost:4566 sqs get-queue-url \
    --queue-name "$q" --query QueueUrl --output text
done

# 4. redrive policy da fila principal de pacotes (esperado: JSON com maxReceiveCount=3)
PKG_Q="$(aws --endpoint-url http://localhost:4566 sqs get-queue-url \
  --queue-name package-events-queue.fifo --query QueueUrl --output text)"
aws --endpoint-url http://localhost:4566 sqs get-queue-attributes \
  --queue-url "$PKG_Q" --attribute-names RedrivePolicy \
  --query Attributes.RedrivePolicy --output text
```

Execução via runner:

```bash
./run-chaos-demo.sh --scenario 00
```

## Saída esperada

- `PASS [PRE] liveness 200 ...`
- 6 linhas `PASS [PRE] queue url resolved: ...`
- `PASS [PRE] pkg RedrivePolicy: contains 'maxReceiveCount'` e `... contains '3'` — ou `SKIP [PRE] RedrivePolicy attribute not reported by emulator` se o MiniStack não expuser o atributo.
- Rodapé: `scenario 00-baseline: N passed, 0 failed, ...` e exit code 0.

## O que dizer para a banca

"Antes de injetar qualquer falha, validamos o contrato do ambiente: os dois serviços compilados nativamente estão vivos, as seis filas FIFO existem e a política de redrive está configurada com no máximo 3 recebimentos antes de enviar a mensagem para a DLQ. Isso garante que, nos cenários seguintes, qualquer comportamento anômalo observado é efeito do caos injetado — e não de um ambiente mal montado. É o mesmo princípio de um experimento científico: primeiro se estabelece o grupo de controle."
