# Native-first (GraalVM Native Image / Spring AOT)

This service ships as a GraalVM **native image**. Spring AOT (`processAot`) runs during
the build and **freezes the bean graph** into generated code; GraalVM then compiles it.
Conditions (`@Profile`, `@ConditionalOnProperty`, property defaults) are evaluated **at
build time**, not at runtime.

## The one rule
**Never make the _existence_ of a needed bean depend on a runtime-only value** (profile,
env var, or a property whose value only arrives in prod). At build time the value is
absent/default, the condition fails, and the bean is **silently pruned** from the image —
it will not come back when ECS sets the env var at runtime.

Instead:
- include the bean **always** and make it a no-op when disabled — e.g. `HubDataSeeder` is a
  `@Component`/`CommandLineRunner` guarded **inside** `run()` by
  `environment.matchesProfiles("local")`, not by `@Profile("local")`; or
- control behavior with a **value the bean reads at runtime** — e.g. turn tracing off with
  `MANAGEMENT_TRACING_SAMPLING_PROBABILITY=0`, **not** with
  `management.tracing.export.otlp.enabled` (that is an AOT-frozen condition and a no-op in
  the native image).

## The image is profile-agnostic
The `Dockerfile` runs `:application:nativeCompile` with **no** `SPRING_PROFILES_ACTIVE`, so
AOT freezes the graph under the **default** profile. `application-prod.yml` /
`application-local.yml` still load at runtime and change config **values** — what is frozen
is the bean **topology**. One image runs every environment; do **not** bake a profile into
the build.

## Env-var contract
**Build-time** (frozen into the image — changing requires a rebuild): bean topology,
reflection/resource hints, exporter presence, `management.tracing.propagation.type`.

**Runtime** (set by ECS / `application-prod.yml`, no rebuild):

| Var | Required (prod) | Default | Effect |
|-----|-----------------|---------|--------|
| `MONGODB_URI` | yes | — | Mongo connection (must be a replica set) |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | host yes | — / 6379 / empty | CEP cache |
| `AWS_REGION` | no | us-east-1 | AWS SDK + X-Ray region |
| `APP_MESSAGING_INBOUND_QUEUE` / `_OUTBOUND_QUEUE` / `_HUB_EVENTS_QUEUE` | no | local `.fifo` names | SQS queue **names** (resolved by name + `GetQueueUrl`, never by URL) |
| `MANAGEMENT_OTLP_TRACING_ENDPOINT` | no | empty | OTLP collector endpoint (ADOT sidecar) |
| `MANAGEMENT_TRACING_SAMPLING_PROBABILITY` | no | 1.0 | trace sampling; **set `0` to disable tracing** |
| `SERVER_PORT` | no | 0 (random) | HTTP port |

> The OTLP span exporter is **always present** in the image (build-time topology). It is
> NOT toggled by an env var — control it operationally via sampling + endpoint.

## Guard test — `AotBeanGraphContractTest`
`./gradlew :application:test` runs it (the `test` task `dependsOn processAot`). It inspects
`build/generated/aotSources` and **fails if a critical bean was pruned** (MapStruct mappers,
SQS listeners, `HubDataSeeder`, use-case wiring, `OtlpHttpSpanExporter`). This is the per-PR
safety net — it runs inside the existing `ci.yml` `build`. When you add a
`@Bean`/`@Profile`/`@ConditionalOnProperty`, run this test.

## Manual native hints (and why they stay)
GraalVM only includes what is statically reachable; two things here are not, so they are
registered explicitly. These are **necessary, not workarounds** — do not remove them.
- `IbgeCsvMunicipalityAdapter.GeoResourceHints` registers `geo/municipios.csv`. GraalVM
  **never** auto-includes classpath resources; without it the CSV is absent and geocoding
  fails at runtime (not at build).
- `NativeHintsConfig` registers Jackson binding reflection for the SQS event payloads,
  `ViaCepResponse` and `CepInfo`. These types are reached only through runtime `ObjectMapper`
  / `RestClient` calls in adapter code (no static reachability, not an auto-hinted role like a
  controller body), so native reflection needs them registered or (de)serialization fails at
  runtime.

To empirically re-verify necessity: remove a hint on a branch, run the **Native Check**
workflow, then exercise the path on the native binary — it fails without the hint.

## Validation matrix
1. **JVM build + AOT guard:** `./gradlew build` — runs on every PR via `ci.yml`.
2. **Native compile:** `./gradlew :application:nativeCompile` (slow) — also the manual
   **Native Check** workflow (`.github/workflows/native-check.yml`); not per-PR, by cost.
3. **Native container + ECS-like env:** `docker compose up` locally; smoke the REST
   endpoints, `/management/health/liveness`, and confirm traces reach the collector.
