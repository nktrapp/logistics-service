# Catálogo de invariantes (I1–I10)

Este é o documento central do laboratório. Cada invariante tem quatro partes:

- **Enunciado formal** — a propriedade, em uma frase verificável.
- **Mecanismo** — a classe/arquivo real que a implementa (verificável no código).
- **Evidência** — o teste Java que a prova em nível de unidade/integração e o cenário do lab
  que a prova de fora (caixa-preta, binário nativo, broker real emulado).
- **Violação hipotética** — o que aconteceria se o mecanismo não existisse.

Caminhos de código são relativos à raiz do workspace (`trabalho-final-furb/`).

---

## I1 — Pacote nunca regride de estado

**Enunciado.** Para todo pacote, a sequência de status observada é um caminho válido na
máquina de estados; nenhum evento, retry ou chamada HTTP move um pacote para um estado
anterior ou proibido.

**Mecanismo.** `package-service/domain/src/main/java/br/furb/pkg/domain/model/PackageStatus.java`
— `getAllowedTransitions()` define o grafo fechado de transições (estados terminais
`DELIVERED`/`FAILED` retornam `Set.of()`); `isValidTransition(target)` é consultado tanto
pelo agregado (que lança `InvalidPackageStateException`) quanto pelos consumidores de evento
(que fazem **skip + WARN** em vez de lançar — ver
`ProcessRouteCalculatedUseCase`, linha `if (!pkg.getStatus().isValidTransition(...))`).

**Evidência.**
- Teste Java: `package-service/domain/src/test/java/br/furb/pkg/domain/model/PackageTest.java`
  ("Given an invalid target status, should throw exception when updating package status").
- Cenário do lab: `04-stale-route` (asserção "status did not regress").

**Violação hipotética.** Sem o grafo de transições, um `route.calculated` atrasado chegando
depois de `IN_TRANSIT` voltaria o pacote para `ROUTE_CALCULATED`; um `PATCH` direto para
`DELIVERED` pularia o transporte. O histórico do pacote deixaria de ser confiável.

---

## I2 — Duplicata não gera efeito duplicado

**Enunciado.** N entregas da mesma mensagem (mesmo `eventId`) produzem exatamente o mesmo
estado que 1 entrega — o efeito colateral (rota criada, pacote atualizado) ocorre no máximo
uma vez.

**Mecanismo.** `MongoInboxRepositoryAdapter.saveIfAbsent(eventId, eventType)` (existe nos
dois serviços, p.ex.
`package-service/infrastructure/src/main/java/br/furb/pkg/infrastructure/adapter/out/persistence/repository/MongoInboxRepositoryAdapter.java`)
— **upsert com `$setOnInsert`** sobre o índice único de `eventId`. O upsert (e não
insert + catch de `DuplicateKeyException`) é deliberado: um erro de escrita dentro de
transação Mongo **aborta a transação inteira mesmo se capturado**; com upsert, a reentrega
casa com o documento existente, a transação segue saudável e a duplicata é confirmada (ACK)
em vez de reentregue até a DLQ (o javadoc do método documenta exatamente isso).

**Evidência.**
- Teste Java: `package-service/infrastructure/src/test/java/br/furb/pkg/infrastructure/adapter/out/persistence/OutboxInboxIntegrationTest.java`
  ("inbox saveIfAbsent is idempotent on the unique eventId"; equivalente no
  `logistics-service`).
- Cenário do lab: `03-inbox-idempotency` (mesmo `eventId` reenviado → exatamente 1 rota).

**Violação hipotética.** Reentrega do SQS (at-least-once) criaria uma segunda rota para o
mesmo pacote, ou — na variante insert+catch — abortaria a transação a cada reentrega,
mandando um evento legítimo já processado para a DLQ.

---

## I3 — Evento já processado não é reaplicado

**Enunciado.** O registro "evento X foi processado" e o efeito do evento X são gravados na
**mesma transação**; portanto não existe estado em que o efeito ocorreu sem registro (ou
vice-versa) que permita reaplicação.

**Mecanismo.** O claim do inbox é a **primeira instrução** do use case transacional —
`ProcessRouteCalculatedUseCase.execute()`
(`package-service/application/src/main/java/br/furb/pkg/application/usecase/ProcessRouteCalculatedUseCase.java`,
`@Transactional`): `if (!inboxRepository.saveIfAbsent(...)) { log "already processed,
skipping"; return; }`. Se a transação falha depois do claim, o claim **rola atrás junto** e a
reentrega reprocessa do zero; se commita, a reentrega é detectada e ignorada.

**Evidência.**
- Testes Java: `package-service/application/src/test/java/br/furb/pkg/application/usecase/StaleRouteConvergenceIntegrationTest.java`
  (a reentrega do evento stale é deduplicada);
  `OutboxInboxIntegrationTest` ("a failed transaction rolls back the inbox claim so the
  event is reprocessed");
  `logistics-service/application/src/test/java/br/furb/logistics/application/usecase/CalculateRouteUseCaseTest.java`
  ("Given an already processed event, should skip without any lookup or persistence").
- Cenário do lab: `03-inbox-idempotency` (log `already processed, skipping` + 1 entrada no
  inbox).

**Violação hipotética.** Inbox gravado **fora** da transação do efeito abre duas janelas:
claim antes/efeito falha → evento legítimo nunca mais é aplicado (perda); efeito antes/claim
falha → reentrega reaplica o efeito (duplicação).

---

## I4 — Evento antigo não sobrescreve estado novo

**Enunciado.** Um `route.calculated`/`route.recalculated` calculado para um destino que **já
não é** o destino atual do pacote é descartado com ACK (skip + WARN), nunca aplicado.

**Mecanismo.** Guard causal em `ProcessRouteCalculatedUseCase`:
`if (destinationCep != null && !destinationCep.equals(pkg.getRecipientCep()))` → log WARN
`"Stale route for package ..."` e `return` (a transação commita só com o claim do inbox — o
descarte também é idempotente). Para isso funcionar, os eventos de rota do
`logistics-service` **carregam o `destinationCep`** para o qual a rota foi calculada — o dado
causal viaja no evento.

**Evidência.**
- Teste Java: `package-service/application/src/test/java/br/furb/pkg/application/usecase/StaleRouteConvergenceIntegrationTest.java`
  ("skips a stale route event, applies the fresh one and deduplicates the stale
  redelivery").
- Cenário do lab: `04-stale-route` — o cenário central da defesa: forja um
  `route.calculated` com o CEP antigo após a mudança de destino e prova que `recipientCep`,
  `status` e `routeInfo` não mudam e que a DLQ não cresce (delta 0 — descarte é ACK, não
  erro).

**Violação hipotética.** Sob mudança de destino com recálculo em voo, o evento da rota
antiga poderia chegar **depois** do da rota nova (retry, reentrega, corrida) e sobrescrever a
projeção — o pacote exibiria uma rota para o endereço errado, em silêncio.

---

## I5 — Evento gravado na outbox não se perde

**Enunciado.** Todo evento de domínio commitado é eventualmente publicado no broker (ou
fica explicitamente `FAILED`, visível e recuperável) — nunca desaparece.

**Mecanismo.**
- Escrita: `MongoOutboxRepositoryAdapter.save()` grava o evento na coleção `outbox` na
  **mesma transação Mongo** do estado de domínio (sem dual-write).
- Publicação: `OutboxRelaySchedulerAdapter.relay()` (`@Scheduled`, default 2 s) faz claim e
  publica; falha → `markForRetry` com backoff exponencial (`5000 ms × 2^n`, teto 60 s) até
  `max-attempts` (default 5), quando o evento vira `FAILED` — exposto pelo gauge
  `outbox.failed.count` (`OutboxMetricsConfig`).

**Evidência.**
- Teste Java: `OutboxInboxIntegrationTest` (nos dois serviços: "persists an event, claims
  it, publishes it, then purges it"; "schedules retries while attempts remain and marks the
  event FAILED once exhausted") e `OutboxRelaySchedulerAdapterTest` (retry/backoff).
- Cenários do lab: `01-happy-path` (outbox dos dois serviços termina sem
  `PENDING`/`FAILED` para o grupo) e `06-outage-recovery` (B: broker pausado → `PENDING`
  acumula → unpause drena).

**Violação hipotética.** Publicar direto no broker dentro do request (dual-write): commit no
Mongo + broker fora do ar = evento perdido para sempre; broker ok + rollback no Mongo =
evento fantasma de um estado que não existe.

---

## I6 — Falha pós-commit/pré-publicação é recuperável

**Enunciado.** Se o processo morre depois do commit local e antes (ou durante) a publicação,
outro tick/instância do relay retoma o evento sem perdê-lo nem publicá-lo duas vezes "por
design" (duplicata residual é absorvida por I2/I9).

**Mecanismo.** `MongoOutboxRepositoryAdapter.claimNext()` — claim **atômico** via
`findAndModify` (`PENDING` → `IN_PROGRESS` + `processingStartedAt`); entradas
`IN_PROGRESS` cujo `processingStartedAt` excedeu `processing-timeout-ms` (60 s) voltam a ser
elegíveis (crash do relay no meio da publicação não prende o evento). `markAsPublished` só
ocorre **após** o publish — morrer entre publish e markAsPublished gera reenvio com o mesmo
`MessageDeduplicationId = eventId`, absorvido pelo broker e/ou pelo inbox.

**Evidência.**
- Teste Java: `logistics-service/infrastructure/src/test/java/br/furb/logistics/infrastructure/adapter/out/persistence/OutboxInboxIntegrationTest.java`
  ("does not re-claim an in-progress event until its processing has timed out").
- Cenário do lab: `06-outage-recovery` (B): broker pausado durante a publicação, evento
  preso em retry, unpause → converge sem intervenção.

**Violação hipotética.** Claim não atômico (find + update separados) deixaria duas
instâncias do relay publicarem o mesmo lote; sem timeout de `IN_PROGRESS`, um crash no meio
do tick congelaria eventos para sempre.

---

## I7 — Mensagem poison é isolada na DLQ sem bloquear o fluxo

**Enunciado.** Uma mensagem indecifrável (JSON inválido, contrato violado) é movida para a
DLQ após `maxReceiveCount` tentativas; o bloqueio que ela causa é limitado a
`maxReceiveCount × visibilityTimeout` e **apenas ao seu `MessageGroupId`** — os demais
pacotes seguem fluindo.

**Mecanismo.** Configuração das filas (IaC: `logistic-iac/modules/sqs-contracts/main.tf`):
`RedrivePolicy` com `maxReceiveCount = 3`, `visibility_timeout_seconds = 60`, DLQs `*-dlq.fifo`
com retenção de 14 dias (1 209 600 s). No consumo, erro de contrato propaga exceção
(deliberado: preservar a evidência na DLQ, não engolir), enquanto descarte de negócio
(stale/transição inválida) é ACK — só o que é realmente indecifrável chega à DLQ. Fórmula do
bloqueio: `3 × 60 s = 180 s`, restrito ao grupo (FIFO entrega os grupos independentes em
paralelo).

**Evidência.**
- Cenário do lab: `05-poison-dlq` (visibility reduzida para 5 s na demo; poison → 3
  receives → DLQ; um pacote de outro grupo flui durante o bloqueio; redrive da poison volta
  à DLQ).
- Documentação de decisão: seção "Garantias e trade-offs" dos READMEs (por que
  `maxReceiveCount: 3`).

**Violação hipotética.** Sem `RedrivePolicy`, a poison ficaria em loop infinito de
reentrega; numa fila FIFO, isso **bloquearia o grupo do pacote para sempre** (head-of-line
blocking sem fim) e poluiria os logs indefinidamente.

---

## I8 — Após recuperação, o sistema converge

**Enunciado.** Cessada a falha (consumidor de volta, broker de volta), todo pacote criado
durante a falha termina com exatamente uma rota e status consistente, sem intervenção.

**Mecanismo.** É a composição dos anteriores: outbox com retry (I5) + claim recuperável
(I6) + inbox idempotente (I2/I3) + máquina de estados (I1) + guard causal (I4). Não há um
"mecanismo de convergência" separado — convergência é o **teorema**, os mecanismos são os
axiomas.

**Evidência.**
- Cenários do lab: `01-happy-path` (convergência nominal: `ROUTE_CALCULATED`, 1 rota,
  outbox drenada) e `06-outage-recovery` (A: consumidor parado, fila acumula, restart →
  cada pacote converge com exatamente 1 rota; B: broker pausado → unpause → converge).
- Testes Java: `StaleRouteConvergenceIntegrationTest` (convergência sob evento stale +
  reentrega).

**Violação hipotética.** Qualquer quebra de I2–I6 quebra I8: rota duplicada, rota perdida,
rota do destino antigo ou pacote preso em `ROUTE_PENDING` para sempre.

---

## I9 — FIFO ordena por agregado, mas não é exactly-once

**Enunciado.** A ordem por `MessageGroupId = packageId` é garantida em duas camadas (relay e
broker), mas a entrega é **at-least-once**: a aplicação nunca depende do dedup do broker
para correção — ele é otimização, o inbox é a garantia.

**Mecanismo.**
- Ordem na origem: `OutboxRelaySchedulerAdapter.publishEntry()` chama
  `MongoOutboxRepositoryAdapter.existsEarlierUnpublished(groupId, createdAt, outboxId)`
  (ordenação por `createdAt`, desempate por `_id`; `PENDING`/`IN_PROGRESS`/`FAILED`
  bloqueiam o grupo) e, havendo irmão anterior não publicado, faz `releaseClaim` (volta a
  `PENDING` **sem contar retry**). Consequência deliberada: um evento `FAILED` bloqueia o
  grupo até replay manual — ordem acima de liveness do grupo.
- Dedup do broker: `MessageDeduplicationId = eventId`, janela de **5 minutos** — uma
  conveniência temporal, não exactly-once (reentrega ao consumidor e reenvio fora da janela
  continuam possíveis).

**Evidência.**
- Testes Java: `OutboxRelaySchedulerAdapterTest` (nos dois serviços: "Given an earlier
  unpublished sibling..., should release the claim without publishing or retrying") e
  `OutboxInboxIntegrationTest` ("two concurrent relay workers publish a group's events in
  creation order" / "concurrent claimers cannot invert per-group publish order"; "a FAILED
  earlier sibling keeps blocking the group until manual replay"; desempate por `_id`).
- Cenário do lab: `02-broker-dedup` — mesmo `MessageDeduplicationId` 2× → 1 mensagem na
  fila; **novo** dedup-id com o mesmo conteúdo → entra de novo (prova que o broker dedupa
  por id, não por conteúdo/negócio — quem segura a segunda é o inbox, cenário 03).

**Violação hipotética.** Confiar no dedup do broker como garantia: um retry do relay 6
minutos depois (fora da janela) duplicaria o evento; sem `existsEarlierUnpublished`, dois
ticks concorrentes do relay poderiam publicar `package.destination.changed` **antes** de
`package.created` do mesmo pacote.

---

## I10 — Estado final determinístico sob retries, duplicatas e concorrência

**Enunciado.** Para qualquer intercalação de reentregas, retries e escritas concorrentes, o
estado final observável é um dos resultados serializáveis legítimos — nunca um estado
misto/perdido — e nenhuma atualização é perdida em silêncio.

**Mecanismo.** Máquina de estados (I1) + guards de consumo (I4) + transações Mongo no
replica set: escrita concorrente no mesmo documento gera `WriteConflict`, que o Spring
traduz para `DataIntegrityViolationException` e o
`GlobalExceptionHandler.handleConcurrentModification()`
(`package-service/application/src/main/java/br/furb/pkg/application/adapter/in/web/exception/GlobalExceptionHandler.java`)
converte em **HTTP 409** ("please retry") — o perdedor da corrida é avisado, não ignorado.

**Evidência.**
- Teste Java: `package-service/application/src/test/java/br/furb/pkg/application/usecase/ConcurrentDestinationChangeIntegrationTest.java`
  ("never loses an update silently and only committed transactions leave outbox events").
- Cenários do lab: `04-stale-route` (routeInfo não é sobrescrita pela rota forjada; DLQ
  delta 0) e `06-outage-recovery` (estado final idêntico ao nominal apesar da falha).

**Violação hipotética.** Sem a transação + 409, duas mudanças de destino concorrentes
fariam last-write-wins silencioso: uma delas sumiria, mas seu evento poderia já ter sido
publicado — projeções divergentes permanentes entre os dois serviços.

---

## Matriz de rastreabilidade

| Invariante | Mecanismo (código) | Teste Java | Cenário do lab |
|---|---|---|---|
| **I1** — não regride de estado | `PackageStatus.getAllowedTransitions()` + guards no agregado e nos consumidores | `PackageTest` (`package-service/domain/.../model/PackageTest.java`) | `04-stale-route` |
| **I2** — duplicata sem efeito duplicado | `MongoInboxRepositoryAdapter.saveIfAbsent()` (upsert `$setOnInsert`, índice único `eventId`) | `OutboxInboxIntegrationTest` ("saveIfAbsent is idempotent") — ambos os serviços | `03-inbox-idempotency` |
| **I3** — já processado não reaplica | claim do inbox como 1ª instrução do use case `@Transactional` (`ProcessRouteCalculatedUseCase`, `CalculateRouteUseCase`) | `StaleRouteConvergenceIntegrationTest` (redelivery); `OutboxInboxIntegrationTest` (rollback do claim); `CalculateRouteUseCaseTest` (skip) | `03-inbox-idempotency` |
| **I4** — stale não sobrescreve | guard `destinationCep` × `recipientCep` em `ProcessRouteCalculatedUseCase` (skip + WARN + ACK) | `StaleRouteConvergenceIntegrationTest` | `04-stale-route` |
| **I5** — outbox não perde evento | `MongoOutboxRepositoryAdapter.save()` na mesma tx + `OutboxRelaySchedulerAdapter` (retry/backoff, `FAILED` + gauge `outbox.failed.count`) | `OutboxInboxIntegrationTest` (persist/claim/publish/purge; retries → FAILED); `OutboxRelaySchedulerAdapterTest` | `01-happy-path`, `06-outage-recovery` |
| **I6** — falha pós-commit recuperável | `claimNext()` atômico (`findAndModify`) + timeout de `IN_PROGRESS` (60 s) | `OutboxInboxIntegrationTest` (logistics: "does not re-claim an in-progress event until its processing has timed out") | `06-outage-recovery` (B) |
| **I7** — poison isolada na DLQ | `RedrivePolicy` (`maxReceiveCount=3`, visibility 60 s, DLQ 14 d — `logistic-iac/modules/sqs-contracts/main.tf`); erro de contrato propaga exceção | — (propriedade de infraestrutura; validada só pelo lab) | `05-poison-dlq` |
| **I8** — convergência pós-falha | composição de I1–I6 (sem mecanismo próprio) | `StaleRouteConvergenceIntegrationTest` | `01-happy-path`, `06-outage-recovery` |
| **I9** — ordem por grupo, não exactly-once | `existsEarlierUnpublished()`/`releaseClaim()` no relay; dedup do broker = janela de 5 min (conveniência) | `OutboxRelaySchedulerAdapterTest` (per-group ordering); `OutboxInboxIntegrationTest` (ordem sob concorrência, FAILED bloqueia grupo, desempate `_id`) — ambos os serviços | `02-broker-dedup` |
| **I10** — estado final determinístico | máquina de estados + guards + tx Mongo (`WriteConflict` → `GlobalExceptionHandler` → HTTP 409) | `ConcurrentDestinationChangeIntegrationTest` | `04-stale-route`, `06-outage-recovery` |

> Leitura da matriz: cada invariante tem (a) um mecanismo apontável no código, (b) um teste
> Java que falha se o mecanismo for removido e (c) um cenário do lab que a observa de fora,
> no sistema real. I7 é a exceção consciente: é propriedade da infraestrutura (SQS), não do
> código — por isso só o lab a cobre, e com a regra de honestidade do `SKIP` quando o
> emulador não a implementa.
