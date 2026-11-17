# syntax=docker/dockerfile:1.7
# ---------------------------------------------------------------------------
# Stage 1: Build mit Maven Wrapper + JDK 21
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# Zuerst nur Build-Dateien -> Dependency-Layer wird gecacht, solange pom.xml gleich bleibt
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline

COPY src/ src/
# Version aus dem Git-Tag (CI: --build-arg VERSION=1.2.3); Tests laufen bereits im CI-Job vorher
ARG VERSION=0.0.0-SNAPSHOT
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests -Drevision=${VERSION} \
 && java -Djarmode=tools -jar target/app.jar extract --destination target/extracted

# ---------------------------------------------------------------------------
# Stage 2: schlankes JRE-Runtime-Image (kein JDK, kein Maven, kein Quellcode)
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
ARG VERSION=0.0.0-SNAPSHOT
ARG GIT_SHA=local
LABEL org.opencontainers.image.title="biztrips-backend" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.revision="${GIT_SHA}" \
      org.opencontainers.image.source="https://github.com/Panat-ZHH/refcard-05-be-springboot-businesstrips"

RUN addgroup -S app && adduser -S app -G app && mkdir -p /data/images && chown app:app /data/images
WORKDIR /app
# Libraries und App getrennt kopieren -> bei Code-Aenderungen aendert sich nur der kleine App-Layer
COPY --from=build --chown=app:app /workspace/target/extracted/lib/ lib/
COPY --from=build --chown=app:app /workspace/target/extracted/app.jar app.jar
USER app

ENV GIT_SHA=${GIT_SHA} \
    STORAGE_LOCAL_DIR=/data/images \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
