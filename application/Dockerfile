# ─── Stage 1: Build ───
FROM eclipse-temurin:25-jdk-alpine AS builder

WORKDIR /workspace

# Copy build configuration first for dependency caching
COPY gradle/ gradle/
COPY gradlew build.gradle settings.gradle gradle.properties ./
COPY domain/build.gradle domain/
COPY infrastructure/build.gradle infrastructure/
COPY application/build.gradle application/

RUN chmod +x gradlew && ./gradlew dependencies --no-daemon || true

# Copy source code
COPY domain/ domain/
COPY infrastructure/ infrastructure/
COPY application/ application/

RUN ./gradlew :application:bootJar -x test --no-daemon

# ─── Stage 2: Runtime ───
FROM eclipse-temurin:25-jre-alpine

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

COPY --from=builder /workspace/application/build/libs/*.jar app.jar

RUN chown -R appuser:appgroup /app
USER appuser

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/management/health/liveness || exit 1

ENTRYPOINT ["java", \
  "-XX:+UseZGC", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
