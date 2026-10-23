# Build one Spring Boot owner service from the Maven reactor.
# The CI/publish matrices pass SERVICE_MODULE, SERVICE_ARTIFACT, SERVICE_PORT,
# SERVICE_HEALTH_PATH, and the optional runtime capabilities below.
ARG SERVICE_MODULE=auth
ARG SERVICE_ARTIFACT=app.jar
ARG SERVICE_PORT=9101
ARG SERVICE_HEALTH_PATH=/api/v1/auth/health
ARG INSTALL_DOCKER_CLI=false
ARG SERVICE_HEALTHCHECK=true

FROM eclipse-temurin:24-jdk-alpine@sha256:8fdbcb6bc6b846640cea7058e6eeb56c311fae4efaa506a213789134065c6b90 AS builder

ARG SERVICE_MODULE
ARG SERVICE_ARTIFACT

WORKDIR /app

# Copy the entire Maven reactor (shared modules + owner services).
COPY services/ ./services/

WORKDIR /app/services

RUN --mount=type=cache,target=/root/.m2 chmod +x ./mvnw \
    && ./mvnw -pl "${SERVICE_MODULE}" -am -B -DskipTests package \
    && cp "${SERVICE_MODULE}/target/${SERVICE_ARTIFACT}" /app/service.jar

FROM eclipse-temurin:24-jre-alpine@sha256:4044b6c87cb088885bcd0220f7dc7a8a4aab76577605fa471945d2e98270741f AS runtime

ARG SERVICE_VERSION=
ARG SERVICE_PORT
ARG SERVICE_HEALTH_PATH
ARG INSTALL_DOCKER_CLI
ARG SERVICE_HEALTHCHECK

WORKDIR /app

RUN apk upgrade --no-cache \
    && apk add --no-cache curl \
    && if [ "${INSTALL_DOCKER_CLI}" = "true" ]; then apk add --no-cache docker-cli; fi

COPY --from=builder /app/service.jar ./app.jar

# Pin the runtime user to uid/gid 1000: the judge worker writes the shared
# sandbox workspace provisioned for uid 1000 and launches child containers
# with `--user 1000:1000`. Per-run directories use explicit 0755 traversal
# permissions so a rootless remote daemon can access the read-only bind mount;
# source and input files remain read-only.
RUN addgroup -S -g 1000 appgroup && adduser -S -u 1000 -G appgroup appuser
USER appuser

ENV SERVER_PORT=${SERVICE_PORT}
ENV HEALTH_PATH=${SERVICE_HEALTH_PATH}
ENV SERVICE_HEALTHCHECK=${SERVICE_HEALTHCHECK}
EXPOSE ${SERVICE_PORT}
STOPSIGNAL SIGTERM

LABEL org.opencontainers.image.version="${SERVICE_VERSION}"

HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
    CMD if [ "${SERVICE_HEALTHCHECK}" != "true" ]; then exit 0; fi; \
        curl -fsS "http://localhost:${SERVER_PORT}${HEALTH_PATH}" >/dev/null || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
