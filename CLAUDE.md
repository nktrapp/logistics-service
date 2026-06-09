# logistics-service

Spring Boot microservice for route calculation (Dijkstra over a hub graph) + ViaCEP lookup. Independent project.

## Commands
- Build + test: `./gradlew build`
- Test only: `./gradlew test`
- Run full local stack: `docker compose up` (service on :8082, Mongo :27017, Redis :6379, MiniStack :4566)

## Architecture (hexagonal, 4 Gradle modules)
`domain` → `core` → `infrastructure` → `app`. Same layering rules as package-service (`domain` is pure, use cases wired in `UseCaseConfig`).
- Routing: `RouteCalculationService` (Dijkstra). Candidate hubs resolved same-city → same-state (never "all hubs").
- ViaCEP: `ViaCepClient` (RestClient + retry) with a Redis cache (`RedisCepCacheAdapter`, 24h TTL).

## Messaging (transactional outbox/inbox over SQS FIFO)
- Consumer = `SqsLogisticsEventListener` → `CalculateRouteUseCase` (`package.created`) / `RecalculateRouteUseCase` (`package.destination.changed`).
- Emits `route.calculated` / `route.recalculated`. FIFO `MessageGroupId = packageId`, dedup = `eventId`.

## Gotchas
- **Mongo MUST be a replica set** (transactions) — `MongoTransactionSupportVerifier` fails startup otherwise.
- **Keep ViaCEP calls + Dijkstra OUTSIDE the write transaction.** Consumers pre-check the inbox, compute, then do a narrow `TransactionTemplate` write (inbox-claim + route + outbox). Do not put `@Transactional` around the whole use case.
- New use case → add a `@Bean` in `UseCaseConfig`.
- JDK 25: JUnit BOM + `net.bytebuddy.experimental=true` pinned in `build.gradle.kts` — required.
- New code: prefer explicit types over `var`.
- **Native image (GraalVM/AOT):** the build is profile-agnostic — Spring AOT freezes the bean graph under the *default* profile. **Never gate a needed bean on a runtime value** (`@Profile`/`@ConditionalOnProperty`/property default) or it is pruned silently from the image. Include the bean and no-op it (see `HubDataSeeder`'s in-`run()` `matchesProfiles` guard) or read a runtime value inside the bean. `AotBeanGraphContractTest` (`./gradlew test` → `processAot`) guards this per-PR. Full contract: `NATIVE.md`.
- **Tracing on/off in native** = `MANAGEMENT_TRACING_SAMPLING_PROBABILITY` (`0` disables), NOT `management.tracing.export.otlp.enabled` (AOT-frozen no-op); the OTLP exporter is always present in the image.

## Infra
Per-service Terraform in `terraform/` (consumes `terraform/base/` via remote state). Uses ElastiCache (Redis).
