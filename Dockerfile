# syntax=docker/dockerfile:1.7
# ============================================================================
# Build stage — uses the Maven wrapper already in the repo so the runtime image
# never needs an installed `mvn` binary or package-manager state.
# ============================================================================
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /app

# Copy only the wrapper + pom first to maximize Docker layer caching for deps.
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q -DskipTests dependency:go-offline

COPY src src
RUN ./mvnw -B -q -DskipTests package \
    && cp target/*.jar /app/app.jar

# ============================================================================
# Runtime stage — JRE only, non-root, read-only filesystem-friendly.
# ============================================================================
FROM eclipse-temurin:21-jre-jammy

# `curl` is used by HEALTHCHECK below. Everything else is removed to keep the
# image small and avoid carrying package-manager state into prod.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Drop into a real, non-root user with a known UID. Helps Kubernetes
# `runAsNonRoot: true` admission policies and avoids the default UID 0.
RUN groupadd --system --gid 1001 chipin \
    && useradd --system --uid 1001 --gid chipin --home /home/chipin --shell /usr/sbin/nologin chipin \
    && mkdir -p /home/chipin && chown chipin:chipin /home/chipin

WORKDIR /app

COPY --from=builder --chown=chipin:chipin /app/app.jar /app/app.jar

USER chipin

EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"

# Health probe hits the Spring Boot actuator. Tune the timing in your
# orchestrator's liveness/readiness probes; this is a backstop only.
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
  CMD curl -fsS http://127.0.0.1:8080/actuator/health || exit 1

# Use exec form so signals (SIGTERM) reach the JVM directly.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
