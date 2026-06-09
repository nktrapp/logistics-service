# Demonstração de mensageria — OUTBOX, INBOX, dedup FIFO e DLQ

Runbooks para apresentar, de forma **determinística**, os pilares de mensageria do
`logistics-service` numa banca. São documentos para **copiar e colar** comandos
(bash + AWS CLI + mongosh) — pensados para macOS/Linux.

> Este projeto usa **MongoDB**, não SQL. As "queries" de inspeção são `mongosh`
> (executadas via `docker compose exec`), não SQL.

## Fluxos (leia nesta ordem)

| # | Documento | Demonstra |
|---|---|---|
| 1 | [flows/01-outbox.md](flows/01-outbox.md) | **Outbox transacional**: entidade + evento commitam juntos; relay publica depois |
| 2 | [flows/02-fifo-deduplication.md](flows/02-fifo-deduplication.md) | **Deduplicação FIFO do SQS**: o broker descarta reenvio com mesmo `MessageDeduplicationId` |
| 3 | [flows/03-inbox.md](flows/03-inbox.md) | **Inbox idempotente**: a aplicação ignora reentrega do mesmo `eventId` |
| 4 | [flows/04-dlq.md](flows/04-dlq.md) | **DLQ**: mensagem "veneno" é isolada após esgotar as tentativas |

A ordem conta uma história: o evento sai com segurança (1), há **duas camadas** de
defesa contra duplicata — a do broker (2) e a da aplicação (3) — e o que não tem
conserto é isolado (4).

---

## 1. Conceitos (fala de abertura, 1 min)

```
package-service ──(package.created)──▶ package-events-queue.fifo
                                              │
                                              ▼
                                   logistics-service
                                     • INBOX   (idempotência por eventId)
                                     • OUTBOX  (evento gravado na MESMA transação)
                                              │
                                   relay (polling) publica DEPOIS, fora da transação
                                              ▼
                        logistics-events-queue.fifo (route.calculated)
```

- **OUTBOX**: o use case grava a entidade **e** o evento na coleção `outbox` na
  **mesma transação Mongo**. Nunca publica no broker dentro da transação → sem
  dual-write. Um relay agendado lê linhas já commitadas e publica no SQS.
- **Dedup FIFO (SQS)**: a fila descarta mensagens com o mesmo
  `MessageDeduplicationId` dentro de uma janela de **5 minutos**. É a 1ª camada.
- **INBOX**: a aplicação registra o `eventId` processado (`inbox`) e ignora
  reentregas. É a 2ª camada — cobre o que a dedup do broker não pega (janela
  expirada, ou dedup-id diferente para o mesmo evento de negócio).
- **DLQ**: cada fila tem `maxReceiveCount = 3`. Quem falha 3× é movido para
  `*-dlq.fifo` em vez de ficar em loop.

> Transações multi-documento exigem **replica set** — por isso o Mongo sobe com
> `--replSet rs0`.

---

## 2. Pré-requisitos (macOS)

```bash
# AWS CLI v2 e jq (opcional, deixa a saída legível)
brew install awscli jq

# Docker Desktop instalado e rodando
docker --version
```

Internet é necessária: o ViaCEP é uma **chamada real**.

---

## 3. Subir o ambiente

```bash
# a partir da raiz do repositório
docker compose -f compose.yml -f demo/compose.demo.yml up -d
```

Sobe `ministack` (SQS em :4566), `mongodb` (replica set), `redis`,
`mongo-express` (GUI em http://localhost:8888), `logistics-service` (em **:8082**)
e `package-service`. O perfil `local` **semeia 5 hubs** (Blumenau, Joinville,
Florianópolis, Curitiba, São Paulo) + 5 conexões, então rotas já são calculáveis.

O override `demo/compose.demo.yml` fixa a porta **8082** e deixa o relay do outbox
em **8s** de propósito (para o status `PENDING` ficar visível).

> Deixe o **mongo-express** (http://localhost:8888) projetado: é a prova visual das
> coleções `outbox`, `inbox`, `routes`.

### Variáveis de ambiente do AWS CLI (rode uma vez por terminal)

```bash
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION=us-east-1
export AWS_PAGER=""

# atalho usado nos runbooks
SQS="aws --endpoint-url http://localhost:4566 sqs"

# URLs das filas que vamos usar
PKG_Q=$($SQS get-queue-url --queue-name package-events-queue.fifo --query QueueUrl --output text)
PKG_DLQ=$($SQS get-queue-url --queue-name package-events-dlq.fifo --query QueueUrl --output text)
echo "$PKG_Q"
echo "$PKG_DLQ"
```

---

## 4. Cheat-sheet de inspeção (deixe à mão)

```bash
# ---- contagens gerais das coleções ----
docker compose exec -T mongodb mongosh logistics_db --quiet --eval '
JSON.stringify({
  hubs: db.hubs.countDocuments({}),
  hub_connections: db.hub_connections.countDocuments({}),
  routes: db.routes.countDocuments({}),
  inbox: db.inbox.countDocuments({}),
  outbox_total: db.outbox.countDocuments({}),
  outbox_PENDING: db.outbox.countDocuments({status:"PENDING"}),
  outbox_PUBLISHED: db.outbox.countDocuments({status:"PUBLISHED"}),
  outbox_FAILED: db.outbox.countDocuments({status:"FAILED"})
}, null, 2)'

# ---- últimas linhas do outbox (status) ----
docker compose exec -T mongodb mongosh logistics_db --quiet --eval '
db.outbox.find({}, {eventType:1, status:1, groupId:1, _id:0}).sort({createdAt:-1}).limit(5).toArray()'

# ---- profundidade de uma fila (visíveis / em voo) ----
$SQS get-queue-attributes --queue-url "$PKG_Q" \
  --attribute-names ApproximateNumberOfMessages ApproximateNumberOfMessagesNotVisible \
  --query Attributes

# ---- logs do serviço (acompanhe em outra aba) ----
docker compose logs -f logistics-service
```

### Resetar o estado entre ensaios

```bash
docker compose exec -T mongodb mongosh logistics_db --quiet --eval '
JSON.stringify({
  inbox:  db.inbox.deleteMany({}).deletedCount,
  outbox: db.outbox.deleteMany({}).deletedCount,
  routes: db.routes.deleteMany({}).deletedCount
})'
# hubs/hub_connections são mantidos (vêm do seeder).
```

---

## 5. Pegadinhas (para não passar vergonha)

| Sintoma | Causa | Ação |
|---|---|---|
| `route.calculated` "some" da fila de saída | `package-service` consome `logistics-events-queue.fifo` | normal; prove pelo `outbox` (PUBLISHED). Para espiar a fila, `docker compose stop package-service` |
| Rota não é criada | ViaCEP sem internet, ou cidade sem hub | use os CEPs do seeder: `89010000` (BLU) → `01310100` (SP) |
| Não vi o `PENDING` no outbox | relay rápido | suba com `demo/compose.demo.yml` (relay em 8s) e consulte logo após o envio |
| Demo de dedup FIFO "não dedupa" ao repetir | janela de 5 min expirou, ou reusou outro dedup-id | use um `MessageDeduplicationId` fixo e repita dentro de 5 min |
| `hub-events-queue.fifo` não existe | IaC não aplicada | localmente ela é criada no `docker compose up` (terraform-contracts lê o catálogo); se faltar, rode `docker compose up -d terraform-contracts` |
| DLQ demora muito | `VisibilityTimeout` em 60s | o runbook da DLQ baixa para 5s |

---

## 6. Arquivos `.http` (opcional)

`http/hubs.http`, `http/routes.http`, `http/management.http` — para quem usar a
extensão REST Client (VS Code) / HTTP Client (IntelliJ). Equivalem aos `curl` dos
runbooks. Ajuste `@baseUrl` se não fixou a porta 8082.

## 7. Nomes exatos

**Filas** (sem prefixo no local): `package-events-queue.fifo`,
`logistics-events-queue.fifo`, `hub-events-queue.fifo` e DLQs
`package-events-dlq.fifo`, `logistics-events-dlq.fifo`, `hub-events-dlq.fifo`.

**Coleções** (DB `logistics_db`): `hubs`, `hub_connections`, `routes`, `outbox`,
`inbox`.
