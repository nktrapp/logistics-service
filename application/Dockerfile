FROM ghcr.io/graalvm/native-image-community:25 AS build

WORKDIR /app

COPY gradle gradle
COPY gradlew .
COPY build.gradle settings.gradle gradle.properties ./
COPY domain/build.gradle domain/
COPY infrastructure/build.gradle infrastructure/
COPY application/build.gradle application/

RUN chmod +x ./gradlew

# Warm the dependency cache in its own layer so source-only changes don't re-resolve everything.
RUN ./gradlew :application:dependencies --no-daemon || true

COPY domain domain
COPY infrastructure infrastructure
COPY application application

RUN ./gradlew :application:nativeCompile -x test --no-daemon


FROM gcr.io/distroless/base-debian12:nonroot

WORKDIR /app

COPY --from=build /app/application/build/native/nativeCompile/logistics-service .

ENV SERVER_PORT=8080
EXPOSE 8080

ENTRYPOINT ["./logistics-service"]
