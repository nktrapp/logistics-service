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
