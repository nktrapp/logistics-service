# ─── Stage 1: Build ───
FROM eclipse-temurin:25-jdk-alpine AS builder

WORKDIR /workspace

# Copy build configuration first for dependency caching
COPY gradle/ gradle/
COPY gradlew build.gradle.kts settings.gradle.kts gradle.properties ./
COPY domain/build.gradle.kts domain/
COPY core/build.gradle.kts core/
COPY infrastructure/build.gradle.kts infrastructure/
COPY app/build.gradle.kts app/

RUN chmod +x gradlew && ./gradlew dependencies --no-daemon || true

# Copy source code
COPY domain/ domain/
COPY core/ core/
COPY infrastructure/ infrastructure/
COPY app/ app/

RUN ./gradlew :app:bootJar -x test --no-daemon

# ─── Stage 2: Runtime ───
FROM eclipse-temurin:25-jre-alpine

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

COPY --from=builder /workspace/app/build/libs/*.jar app.jar

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
