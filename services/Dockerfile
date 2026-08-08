# Build one Spring Boot owner service from the Maven reactor.
# The CI/publish matrices pass SERVICE_MODULE, SERVICE_ARTIFACT, SERVICE_PORT,
# and SERVICE_HEALTH_PATH for auth, admin, and app owner services.
ARG SERVICE_MODULE=auth
ARG SERVICE_ARTIFACT=app.jar
ARG SERVICE_PORT=9101
ARG SERVICE_HEALTH_PATH=/api/v1/auth/health

FROM eclipse-temurin:17-jdk-alpine AS builder

ARG SERVICE_MODULE
ARG SERVICE_ARTIFACT

WORKDIR /app

# Copy the entire Maven reactor (shared modules + owner services).
COPY services/ ./services/

WORKDIR /app/services

RUN chmod +x ./mvnw \
    && ./mvnw -pl "${SERVICE_MODULE}" -am -B -DskipTests package \
    && cp "${SERVICE_MODULE}/target/${SERVICE_ARTIFACT}" /app/service.jar

FROM eclipse-temurin:17-jre-alpine AS runtime

ARG SERVICE_PORT
ARG SERVICE_HEALTH_PATH

WORKDIR /app

RUN apk add --no-cache curl

COPY --from=builder /app/service.jar ./app.jar

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

ENV SERVER_PORT=${SERVICE_PORT}
ENV HEALTH_PATH=${SERVICE_HEALTH_PATH}
EXPOSE ${SERVICE_PORT}

HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
    CMD curl -fsS "http://localhost:${SERVER_PORT}${HEALTH_PATH}" >/dev/null || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
