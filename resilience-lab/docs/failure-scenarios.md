# Cenários de falha → comportamento esperado

Tabela de referência: para cada falha possível no caminho de um evento, o comportamento
projetado, a invariante que o protege (ver [`invariants.md`](invariants.md)) e onde isso é
provado. "Cenário NN" = script em `../scenarios/`; testes Java com caminho na matriz de
rastreabilidade.

| # | Cenário de falha | Comportamento esperado | Invariante | Evidência |
|---|---|---|---|---|
| 1 | **Crash após o commit local, antes do publish** (processo morre com o evento `PENDING` na outbox) | Nada se perde: o evento já está commitado junto com o estado. No próximo tick, o relay (desta ou de outra instância) faz o claim atômico e publica. | I5, I6 | `OutboxInboxIntegrationTest` (persist/claim/publish); cenário `06-outage-recovery` |
| 2 | **Crash após o publish, antes do `markAsPublished`** (evento fica `IN_PROGRESS`) | Após o `processing-timeout-ms` (60 s), a entrada volta a ser elegível e é republicada → **duplicata deliberada**, absorvida pelo dedup do broker (mesmo `MessageDeduplicationId`) e, se a janela passou, pelo inbox do consumidor. | I6, I9, I2 | `OutboxInboxIntegrationTest` (logistics: "does not re-claim an in-progress event until its processing has timed out"); cenários `02` + `03` |
| 3 | **Broker fora do ar durante o publish** | `markForRetry` com backoff exponencial (5 s × 2ⁿ, teto 60 s) até 5 tentativas; depois o evento vira **`FAILED`** — visível no gauge **`outbox.failed.count`** (`OutboxMetricsConfig`) e bloqueando o grupo até replay manual (reset para `PENDING`). | I5, I9 | `OutboxRelaySchedulerAdapterTest` (backoff/cap); `OutboxInboxIntegrationTest` ("marks the event FAILED once exhausted", "FAILED keeps blocking the group"); cenário `06-outage-recovery` (B) |
| 4 | **Consumidor cai antes do ack** (depois ou antes de commitar o efeito) | A mensagem reaparece após o `visibilityTimeout` (60 s) e é reentregue. Se o efeito **não** commitou, reprocessa do zero (o claim do inbox rolou atrás junto); se commitou, o inbox detecta e faz ACK ("already processed"). | I2, I3 | `OutboxInboxIntegrationTest` ("a failed transaction rolls back the inbox claim so the event is reprocessed"); cenário `03-inbox-idempotency` |
| 5 | **Mensagem poison** (JSON inválido / contrato violado) | Exceção imediata e explícita a cada receive → após `maxReceiveCount = 3`, o SQS move a mensagem para a DLQ (retenção 14 d), preservando a evidência. Bloqueio máximo: `3 × 60 s`, **só no `MessageGroupId` afetado** — os demais pacotes fluem. | I7 | cenário `05-poison-dlq` |
| 6 | **Redrive da DLQ** (replay de uma mensagem quarentenada) | Se a mensagem era duplicata/atrasada legítima: inbox/guards absorvem (ACK). Se era poison de verdade: falha de novo e **volta à DLQ** — o redrive não "conserta" poison, só reprocessa o recuperável. | I2, I3, I7 | cenário `05-poison-dlq` (redrive de poison volta à DLQ) |
| 7 | **Duas instâncias do consumidor / do relay** | No consumo, o FIFO entrega um grupo a um consumidor por vez (ordem por `MessageGroupId`); no relay, o claim é `findAndModify` atômico e `existsEarlierUnpublished` impede inverter a ordem do grupo mesmo com workers concorrentes. | I9, I10 | `OutboxInboxIntegrationTest` ("two concurrent relay workers publish a group's events in creation order" / "concurrent claimers cannot invert per-group publish order") |
| 8 | **Duas escritas HTTP concorrentes no mesmo pacote** (ex.: duas mudanças de destino) | A transação perdedora aborta com `WriteConflict` no Mongo → `DataIntegrityViolationException` → **HTTP 409** ("please retry") pelo `GlobalExceptionHandler`. Nenhuma atualização é perdida em silêncio; só transações commitadas deixam eventos na outbox. | I10 | `ConcurrentDestinationChangeIntegrationTest` |
| 9 | **Evento stale após mudança de destino** (`route.calculated` do destino antigo chega depois do recálculo) | Guard causal: `destinationCep` do evento ≠ `recipientCep` atual → **skip + WARN ("Stale route for package...") + ACK**. Estado, `routeInfo` e DLQ intactos. | I1, I4, I10 | `StaleRouteConvergenceIntegrationTest`; cenário `04-stale-route` |

Observações transversais:

- As linhas 1–3 cobrem o lado **produtor** (outbox), 4–6 o lado **consumidor**
  (inbox/DLQ), 7–9 a **concorrência**. Juntas, fecham o ciclo de vida de um evento.
- Em nenhuma linha o comportamento esperado é "perde o dado" ou "estado corrompido": cada
  falha degrada para **atraso** (janela maior), **duplicata absorvida** ou **quarentena
  explícita** (DLQ / outbox `FAILED`) — nunca para perda silenciosa. É essa propriedade,
  agregada, que o cenário `06-outage-recovery` verifica como convergência (I8).
- Falha de **negócio** (CEP inexistente, sem hub no estado, grafo sem caminho) não está na
  tabela porque não é falha de infraestrutura: é fluxo normal — o logistics publica
  `route.failed` com ACK e o pacote termina `FAILED` (ver
  [`distributed-systems.md`](distributed-systems.md), fluxo 3).
