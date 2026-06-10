# [1.7.0](https://github.com/nktrapp/logistics-service/compare/v1.6.0...v1.7.0) (2026-06-10)


### Bug Fixes

* compatibiliza a imagem nativa GraalVM com o ambiente local ([9fd7dcb](https://github.com/nktrapp/logistics-service/commit/9fd7dcbaec7814df4687428930d34cb4915fd1c0))
* corrige desserializacao do ViaCEP e adiciona cobertura de testes ([dfb5a67](https://github.com/nktrapp/logistics-service/commit/dfb5a67f79b2ea86a704031f99dba2ff1eb02b3a))
* exporta traces OTLP para o ADOT/X-Ray ([88f16e3](https://github.com/nktrapp/logistics-service/commit/88f16e3aae5b9dd7b6fce919aa0201ff7643100a))
* habilita OTLP tracing export por default para incluir o exporter na imagem nativa ([d430450](https://github.com/nktrapp/logistics-service/commit/d430450580a5018291c32f20cc429c44b7b0b20a))
* Merge branch 'main' of https://github.com/nktrapp/logistics-service ([2625f48](https://github.com/nktrapp/logistics-service/commit/2625f481bf5249608a8772a6330b27ce6a61a5aa))
* native image nao processava o AOT do spring. ([90f514a](https://github.com/nktrapp/logistics-service/commit/90f514a6d8b70a41f17d5bac1d0609c9ed3ff1da))
* release script ([e466552](https://github.com/nktrapp/logistics-service/commit/e46655215cd19769b6373075bb14b6505171f1a7))
* release script ([76287ed](https://github.com/nktrapp/logistics-service/commit/76287edb05c10617d100496186e9a71a2a57c6a0))
* removido profile local do spring. ([0ccdbd6](https://github.com/nktrapp/logistics-service/commit/0ccdbd61b50d1992f9055f5abef12405faf3107d))
* usa componentModel spring no MapStruct para compatibilidade com GraalVM native ([d49405e](https://github.com/nktrapp/logistics-service/commit/d49405e5e99ac3a3e99f3a3c83f91b496a09cca8))


### Features

* Adicionado Banner do Spring e ajustado as properties ([d18fed1](https://github.com/nktrapp/logistics-service/commit/d18fed1b1e4721ee21f5905df4d0bdef2563fab4))
* force semantic release build image ([ceaa0ea](https://github.com/nktrapp/logistics-service/commit/ceaa0ea0fcf70cdb0db4da14efd19f8c21013f8c))
* force semantic release build image ([dc4c84b](https://github.com/nktrapp/logistics-service/commit/dc4c84b81a3e85b7f1d9bb90e0a1a0be748379d8))
* gera malha de hubs automaticamente ao cadastrar hub ([dab86e3](https://github.com/nktrapp/logistics-service/commit/dab86e373ef4925cb1d82e17d9b67f30462bed61))
* Inicia projeto ([911f164](https://github.com/nktrapp/logistics-service/commit/911f16464c295d2b884f2cc20d28a14b5595b113))
* Inicia projeto ([c759f4d](https://github.com/nktrapp/logistics-service/commit/c759f4d78f06c0f970b1b867c0b2b3531d63be20))
* justes de logs e observabilidade. Integrando com xray, adicionado traceID de ponta a ponta e spanId. Melhorado logs com timestamp, thread e afins. ([d3b2698](https://github.com/nktrapp/logistics-service/commit/d3b2698196d4d416fc28d0f62281c621dd13378a))
* Migrando pra GraalVM ([a827980](https://github.com/nktrapp/logistics-service/commit/a8279807824dcae430b0054b73799c439a983659))
* Migrando pra GraalVM ([a5577fc](https://github.com/nktrapp/logistics-service/commit/a5577fcd799fa201cbd1f90492f74a8704238814))
* Migrando pra GraalVM ([13e1b76](https://github.com/nktrapp/logistics-service/commit/13e1b76e1f612af639ae8fcb21a492dce86801fe))
* Migrando pra GraalVM ([af3bc52](https://github.com/nktrapp/logistics-service/commit/af3bc5265c85a78ca5c48bc4bc9a71f527a62d0c))

# [1.6.0](https://github.com/nktrapp/logistics-service/compare/v1.5.4...v1.6.0) (2026-06-10)


### Bug Fixes

* blinda mensageria contra eventos stale, duplicatas e inversao de ordem ([c94d5b6](https://github.com/nktrapp/logistics-service/commit/c94d5b6cb83464526355cc02daeb2cd8cf2adc23))


### Features

* adiciona resilience-lab — laboratorio de caos e invariantes (Jepsen-lite) ([5b94d58](https://github.com/nktrapp/logistics-service/commit/5b94d5810e6a1526bd4241c6733b863671733cc7))

## [1.5.4](https://github.com/nktrapp/logistics-service/compare/v1.5.3...v1.5.4) (2026-06-09)


### Bug Fixes

* habilita OTLP tracing export por default para incluir o exporter na imagem nativa ([d430450](https://github.com/nktrapp/logistics-service/commit/d430450580a5018291c32f20cc429c44b7b0b20a))

## [1.5.3](https://github.com/nktrapp/logistics-service/compare/v1.5.2...v1.5.3) (2026-06-09)


### Bug Fixes

* compatibiliza a imagem nativa GraalVM com o ambiente local ([9fd7dcb](https://github.com/nktrapp/logistics-service/commit/9fd7dcbaec7814df4687428930d34cb4915fd1c0))
* exporta traces OTLP para o ADOT/X-Ray ([88f16e3](https://github.com/nktrapp/logistics-service/commit/88f16e3aae5b9dd7b6fce919aa0201ff7643100a))

## [1.5.2](https://github.com/nktrapp/logistics-service/compare/v1.5.1...v1.5.2) (2026-06-09)


### Bug Fixes

* removido profile local do spring. ([0ccdbd6](https://github.com/nktrapp/logistics-service/commit/0ccdbd61b50d1992f9055f5abef12405faf3107d))

## [1.5.1](https://github.com/nktrapp/logistics-service/compare/v1.5.0...v1.5.1) (2026-06-09)


### Bug Fixes

* usa componentModel spring no MapStruct para compatibilidade com GraalVM native ([d49405e](https://github.com/nktrapp/logistics-service/commit/d49405e5e99ac3a3e99f3a3c83f91b496a09cca8))

# [1.5.0](https://github.com/nktrapp/logistics-service/compare/v1.4.0...v1.5.0) (2026-06-08)


### Bug Fixes

* corrige desserializacao do ViaCEP e adiciona cobertura de testes ([dfb5a67](https://github.com/nktrapp/logistics-service/commit/dfb5a67f79b2ea86a704031f99dba2ff1eb02b3a))


### Features

* gera malha de hubs automaticamente ao cadastrar hub ([dab86e3](https://github.com/nktrapp/logistics-service/commit/dab86e373ef4925cb1d82e17d9b67f30462bed61))
* justes de logs e observabilidade. Integrando com xray, adicionado traceID de ponta a ponta e spanId. Melhorado logs com timestamp, thread e afins. ([d3b2698](https://github.com/nktrapp/logistics-service/commit/d3b2698196d4d416fc28d0f62281c621dd13378a))

# [1.4.0](https://github.com/nktrapp/logistics-service/compare/v1.3.0...v1.4.0) (2026-06-08)


### Bug Fixes

* native image nao processava o AOT do spring. ([90f514a](https://github.com/nktrapp/logistics-service/commit/90f514a6d8b70a41f17d5bac1d0609c9ed3ff1da))


### Features

* Inicia projeto ([911f164](https://github.com/nktrapp/logistics-service/commit/911f16464c295d2b884f2cc20d28a14b5595b113))
