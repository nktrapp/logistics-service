# Cenário 01 — Happy path (fluxo feliz ponta a ponta)

## Objetivo

Provar o fluxo completo sem nenhuma falha injetada: criar um pacote no `package-service`, observar o evento `package.created` atravessar o outbox → SQS FIFO → inbox do `logistics-service`, a rota ser calculada (Dijkstra sobre os hubs + ViaCEP) e o evento `route.calculated` voltar, levando o pacote ao status `ROUTE_CALCULATED`. Este cenário estabelece a linha de base de latência e comportamento contra a qual os cenários de caos são comparados.

## Invariantes provadas

- **I5** — outbox converge: nenhum documento `PENDING` ou `FAILED` permanece no outbox (em `package_db` e `logistics_db`) para o `groupId` do pacote após o fluxo terminar.
- **I8** — exatamente-uma-rota: o pacote atinge `ROUTE_CALCULATED`, existe exatamente 1 documento na coleção `routes` para o `packageId`, e o `routeInfo` (com `hubs`) fica visível pela API do pacote.

## Pré-condições

- Cenário 00 (baseline) passando.
- Internet disponível: o cálculo de rota consulta o ViaCEP real.
- CEPs usados são de cidades com hub semeado pelo profile local: 89010000 (Blumenau) → 89201000 (Joinville).

## Passos manuais (copy-paste)

```bash
# 1. criar o pacote (esperado: HTTP 201 com "id")
curl -sS -X POST http://localhost:8081/api/v1/packages \
  -H 'Content-Type: application/json' \
  -d '{"senderCep":"89010000","recipientCep":"89201000","weight":1.0,"description":"resilience-lab"}'

# guarde o id retornado:
PKG=<id-retornado>

# 2. acompanhar o status até ROUTE_CALCULATED (esperado em < 90s)
curl -sS http://localhost:8081/api/v1/packages/$PKG | jq '{status, routeInfo}'

# 3. rota no logistics-service (esperado: HTTP 200 com a rota e os hubs)
curl -sS "http://localhost:8082/api/v1/routes?packageId=$PKG" | jq .

# 4. exatamente 1 rota persistida no Mongo
docker compose exec -T mongodb mongosh logistics_db --quiet \
  --eval "print(db.routes.countDocuments({packageId:'$PKG'}))"

# 5. outbox convergiu nos dois bancos (esperado: 0 e 0 em cada banco)
for db in package_db logistics_db; do
  docker compose exec -T mongodb mongosh "$db" --quiet \
    --eval "print(db.outbox.countDocuments({groupId:'$PKG', status:'PENDING'}))"
  docker compose exec -T mongodb mongosh "$db" --quiet \
    --eval "print(db.outbox.countDocuments({groupId:'$PKG', status:'FAILED'}))"
done
```

Execução via runner:

```bash
./run-chaos-demo.sh --scenario 01
```

## Saída esperada

- `created package: <uuid>`
- `PASS [I8] package <uuid> reached ROUTE_CALCULATED`
- `PASS [I8] routes for package: expected='1' actual='1'`
- `PASS [I8] routeInfo present: contains '"hubs"'`
- 4 linhas `PASS [I5] ... outbox PENDING/FAILED ... expected='0' actual='0'` (package_db e logistics_db)
- Rodapé: `scenario 01-happy-path: 7 passed, 0 failed, 0 skipped` e exit code 0.

## O que dizer para a banca

"Este é o experimento de controle: sem nenhuma falha injetada, um pacote criado em Blumenau com destino a Joinville percorre todo o pipeline orientado a eventos — outbox transacional, fila FIFO no emulador SQS, inbox idempotente, cálculo de rota com Dijkstra e consulta real ao ViaCEP — e converge para `ROUTE_CALCULATED` com exatamente uma rota persistida e o outbox completamente drenado nos dois serviços. As invariantes I5 e I8 medidas aqui são exatamente as mesmas que vamos reavaliar sob caos: se elas continuarem verdadeiras com containers pausados, mortos ou filas degradadas, demonstramos que a arquitetura é resiliente por construção, não por sorte."
