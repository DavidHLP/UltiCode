# Stage 1: Build
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

# Copy Maven wrapper and pom.xml first for dependency caching
COPY backend-spring/.mvn/ ./.mvn/
COPY backend-spring/mvnw ./mvnw
COPY backend-spring/pom.xml ./pom.xml

# Download backend dependencies (recommendation/ module was removed in
# commit 65dd5cc51; do not re-introduce the COPY lines without a real module.)
WORKDIR /app
RUN chmod +x ./mvnw && ./mvnw dependency:go-offline -B

# Copy source and build
COPY backend-spring/src ./src
RUN ./mvnw package -DskipTests -B

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine AS runtime

WORKDIR /app

# Add healthcheck utility
RUN apk add --no-cache curl

# Copy built JAR
COPY --from=builder /app/target/app.jar ./app.jar

# Create non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

ENV SERVER_PORT=9001
EXPOSE 9001

HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
    CMD curl -fsS 'http://localhost:9001/contest?page=1&size=1' >/dev/null || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
