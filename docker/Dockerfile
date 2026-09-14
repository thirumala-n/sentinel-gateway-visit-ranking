# ══════════════════════════════════════════════════════════════
# Sentinel — Multi-stage Docker Build
# ══════════════════════════════════════════════════════════════

# ── Stage 1: Build ────────────────────────────────────────────
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /build

# Copy Maven wrapper and POM first for dependency caching
COPY mvnw mvnw.cmd pom.xml ./
COPY .mvn .mvn

# Download dependencies (cached unless pom.xml changes)
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source and build
COPY src src
RUN ./mvnw package -DskipTests -B

# ── Stage 2: Runtime ─────────────────────────────────────────
FROM eclipse-temurin:21-jre

LABEL maintainer="LPDG Team"
LABEL description="Sentinel — Gateway Visit Prioritization Service"

RUN groupadd --system sentinel && useradd --system --gid sentinel sentinel

WORKDIR /app

COPY --from=builder /build/target/*.jar app.jar

# Data directory mount point — challenge data is NEVER baked in
RUN mkdir -p /app/data && chown sentinel:sentinel /app/data
VOLUME /app/data

USER sentinel

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
