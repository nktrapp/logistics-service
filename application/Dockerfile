FROM ghcr.io/graalvm/native-image-community:25 AS build

WORKDIR /app

COPY gradle gradle
COPY gradlew .
COPY build.gradle* settings.gradle* ./
COPY gradle.properties .

COPY application application
COPY domain domain
COPY infrastructure infrastructure


RUN chmod +x ./gradlew

RUN ./gradlew :application:nativeCompile -x test --no-daemon


FROM debian:bookworm-slim

RUN groupadd -r appgroup \
    && useradd -r -g appgroup appuser

WORKDIR /app

COPY --from=build /app/application/build/native/nativeCompile/logistics-service .

RUN chmod +x logistics-service \
    && chown appuser:appgroup /app

USER appuser

EXPOSE 8080

ENTRYPOINT ["./logistics-service"]