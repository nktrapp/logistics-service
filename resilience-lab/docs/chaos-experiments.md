# Experimentos de caos

Cada cenário segue o formato de um experimento: **hipótese** (a invariante, falsificável),
**método** (a ação de caos do nemesis), **métrica observada** (o que a caixa-preta mede) e
**sinal de falha** (o que faria a hipótese cair). Scripts em `../scenarios/`.

## Como rodar um único cenário

```bash
# com o stack já de pé (ex.: depois de um run completo, ou de docker compose up):
./run-chaos-demo.sh --scenario 04 --no-up

# o filtro casa pelo prefixo numérico do arquivo (scenarios/04-*.sh)
```

Sem `--no-up`, o runner sobe o stack (`compose.yml` + `compose.override.yml` se existir +
`compose.lab.yml`) e espera o liveness dos dois serviços antes de executar.

---

## 00-baseline — pré-condições do stack

- **Hipótese:** o ambiente está apto a produzir resultados confiáveis.
- **Método:** nenhum caos. Probe de liveness nos dois serviços, resolução das 6 filas
  (`package-events`, `logistics-events`, `hub-events` + DLQs) e leitura da `RedrivePolicy`
  da fila principal de pacotes.
- **Métrica:** HTTP 200 nos liveness; URL não vazia por fila; `RedrivePolicy` contendo
  `maxReceiveCount` = 3.
- **Sinal de falha:** qualquer fila ausente ou serviço sem responder — os demais cenários
  ficam sem valor. Se o emulador não reportar o atributo `RedrivePolicy`, o check vira
  `SKIP` (risco R1, abaixo).
- **Invariantes:** PRE (pré-condições, não é invariante do sistema).

## 01-happy-path — fluxo feliz ponta a ponta

- **Hipótese:** sem caos, o sistema converge dentro da janela nominal (I8) e nenhum evento
  fica para trás na outbox (I5).
- **Método:** `POST /packages` (Blumenau `89010000` → Joinville `89201000`, ambos com hub
  no seeder) e observação passiva.
- **Métrica:** status do pacote chega a `ROUTE_CALCULATED` (timeout 90 s); exatamente **1**
  documento em `logistics_db.routes` para o pacote; `routeInfo` visível pela API do
  package-service; outbox de **ambos** os bancos sem `PENDING`/`FAILED` para o grupo.
- **Sinal de falha:** timeout de convergência, 0 ou 2+ rotas, outbox com resíduo.
- **Invariantes:** I5, I8.

## 02-broker-dedup — dedup do broker não é exactly-once

- **Hipótese:** o SQS FIFO descarta reenvio com o **mesmo** `MessageDeduplicationId`
  (janela de 5 min), mas isso é conveniência: um dedup-id novo com o mesmo conteúdo entra
  de novo — at-least-once na fronteira do broker (I9).
- **Método:** consumidor (`logistics-service`) **parado** para a fila reter as mensagens;
  envia-se o mesmo envelope 2× com o mesmo dedup-id e depois 1× com dedup-id novo.
- **Métrica:** profundidade da fila (`ApproximateNumberOfMessages`): base+1 após os dois
  primeiros sends; base+2 após o terceiro.
- **Sinal de falha:** profundidade base+2 logo após os dois sends idênticos *(se o emulador
  simplesmente não dedupa, vira `SKIP` — risco R3)*; ou o terceiro send **não** entrar
  (significaria dedup por conteúdo, que mascararia duplicatas de negócio).
- **Invariantes:** I9 (complementado pelos testes Java de ordenação por grupo:
  `OutboxRelaySchedulerAdapterTest` / `OutboxInboxIntegrationTest`).

## 03-inbox-idempotency — a aplicação não depende do broker

- **Hipótese:** mesmo `eventId` entregue duas vezes (com dedup-ids **diferentes**, furando
  o broker de propósito) produz exatamente um efeito (I2) e a segunda entrega é detectada
  como já processada (I3).
- **Método:** envelope `package.created` forjado e enviado 2× com `MessageDeduplicationId`
  distintos — simula reentrega/replay fora da janela de dedup.
- **Métrica:** `logistics_db.routes` com **1** rota para o `packageId`; log do logistics
  com `"already processed, skipping"`; **1** documento no inbox para o `eventId`.
- **Sinal de falha:** 2 rotas (violação direta de I2) ou ausência do log de skip.
- **Pré-condição:** a primeira entrega precisa gerar rota — sem internet (ViaCEP) o cenário
  morre em `ERROR`, não em `FAIL`.
- **Invariantes:** I2, I3.

## 04-stale-route — o evento do passado (cenário central da defesa)

- **Hipótese:** um `route.calculated` **causalmente obsoleto** (calculado para o destino
  antigo) chegando depois da mudança de destino é descartado com ACK: o estado não regride
  (I1), a projeção não é sobrescrita (I4) e o resultado é o mesmo de uma execução sem o
  evento intruso (I10).
- **Método:** pacote criado → rota calculada → `PATCH /destination` para São Paulo
  (`01310100`) → espera reconvergir → **forja** um `route.calculated` com o
  `destinationCep` antigo (`89201000`) e um hub sentinela (`Hub Lab Stale`), injetado
  direto na `logistics-events-queue.fifo` com o `MessageGroupId` do pacote.
- **Métrica:** log do package-service com `"Stale route for package"`; `recipientCep`
  continua `01310100`; status continua `ROUTE_CALCULATED`; `routeInfo.hubs` **não** contém
  o hub sentinela; **DLQ delta 0** (descarte é ACK, não erro — o evento stale não polui a
  quarentena).
- **Sinal de falha:** o hub sentinela aparecer no `routeInfo` (o passado sobrescreveu o
  presente) ou a DLQ crescer (o sistema tratou fato esperado como poison).
- **Invariantes:** I1, I4, I10.

## 05-poison-dlq — quarentena sem contaminar o fluxo

- **Hipótese:** uma mensagem indecifrável é isolada na DLQ após `maxReceiveCount = 3`
  receives, bloqueando no máximo `maxReceiveCount × visibilityTimeout` e **somente o seu
  `MessageGroupId`** (I7).
- **Método:** JSON inválido injetado na fila de pacotes. Para a demo não levar 3 × 60 s, o
  `VisibilityTimeout` da fila é **reduzido para 5 s** (e restaurado para 60 s no cleanup) —
  a fórmula vira 3 × 5 s ≈ 15 s. Em paralelo, um pacote legítimo (grupo diferente) é criado
  para provar o isolamento. Por fim, redrive da poison da DLQ para a fila principal.
- **Métrica:** contagem visível na DLQ (+1 após ~3 receives); pacote legítimo converge
  durante o bloqueio; após o redrive, a poison **volta** à DLQ (replay não conserta
  poison).
- **Sinal de falha:** poison em loop infinito (nunca chega à DLQ), ou o pacote legítimo
  travado pelo grupo alheio.
- **Invariantes:** I7. *(Cenário mais exposto aos riscos R1/R2/R4 do emulador — qualquer
  comportamento de redrive não implementado vira `SKIP` explicado.)*

## 06-outage-recovery — indisponibilidade e convergência

- **Hipótese:** indisponibilidade de um componente degrada para **atraso**, nunca para
  perda ou duplicação: removida a falha, todo pacote converge com exatamente uma rota (I5,
  I6, I8, I10).
- **Método:**
  - **(A) consumidor fora:** `docker compose stop logistics-service` → criam-se pacotes →
    a fila acumula → `start` → espera de convergência.
  - **(B) broker fora:** `docker compose pause ministack` → criam-se pacotes → a outbox do
    package-service acumula `PENDING` (com retries/backoff do relay) → `unpause` → a outbox
    drena e o fluxo completa.
- **Métrica:** profundidade da fila durante (A); contagem de `PENDING` na outbox durante
  (B); após a recuperação: cada pacote em `ROUTE_CALCULATED`, **1 rota por pacote** (sem
  duplicata apesar dos retries), outbox sem resíduo.
- **Sinal de falha:** pacote que nunca converge (perda), 2+ rotas (duplicação por retry) ou
  outbox `FAILED` inesperado.
- **Invariantes:** I5, I6, I8, I10.

---

## SKIP: significado e legitimidade

O MiniStack **emula** SQS; tratá-lo como SQS de verdade produziria falsos PASS (assertar
algo que o emulador nem implementa) ou falsos FAIL (culpar o sistema por limitação do
ambiente). A regra de honestidade do lab: **toda asserção que depende de comportamento
possivelmente não implementado primeiro detecta a capacidade em runtime; na ausência,
registra `SKIP` com a explicação** — nunca silêncio, nunca veredito inventado. `SKIP` é um
resultado de primeira classe no relatório: diz "esta invariante é verificada pelos testes
Java e/ou pela AWS real, não por este emulador".

Riscos mapeados do emulador e a estratégia correspondente:

| Risco | Comportamento possivelmente ausente/divergente | Cenário afetado | Estratégia detect-and-SKIP |
|---|---|---|---|
| **R1** | **Enforcement de redrive**: o emulador pode aceitar a `RedrivePolicy` no create mas não mover mensagens para a DLQ após `maxReceiveCount` | 00, 05 | 00 lê o atributo e faz `SKIP` se não reportado; 05 espera a DLQ com timeout — sem movimento, `SKIP` explicando que o redrive não é aplicado pelo emulador |
| **R2** | **`ApproximateReceiveCount`** impreciso ou ausente nos atributos da mensagem | 05 | a contagem de receives é tratada como telemetria auxiliar, não como asserção; a asserção primária é a chegada na DLQ |
| **R3** | **Janela de dedup FIFO** não implementada (reenvio com mesmo dedup-id enfileira de novo) | 02 | se a profundidade estabiliza em base+2 após os sends idênticos, `SKIP` ("emulator does not implement FIFO dedup window") — e a defesa real contra duplicata (inbox) é provada no 03, que não depende do broker |
| **R4** | **`SetQueueAttributes`** (mudança de `VisibilityTimeout` em fila existente) não suportado ou sem efeito | 05 | se a chamada falha, o cenário ou roda com os 60 s reais (mais lento) ou marca `SKIP` no passo dependente; o cleanup de restauração é empilhado **antes** do uso |

Duas observações finais:

- Os riscos são todos do **plano do broker**. As invariantes de aplicação (I1–I6, I8, I10)
  não dependem de fidelidade do emulador — inbox, outbox, máquina de estados e guards são
  exercitados de verdade em qualquer caso.
- `ERROR` cobre o complemento: pré-condição do **ambiente** quebrada (ViaCEP sem internet,
  stack não saudável). A tripartição FAIL/SKIP/ERROR é o que mantém o relatório honesto:
  só `FAIL` acusa o sistema.
