# Stage 1: Build
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

# Copy Maven wrapper and pom.xml first for dependency caching
COPY backend-spring/.mvn/ ./.mvn/
COPY backend-spring/mvnw ./mvnw
COPY backend-spring/pom.xml ./pom.xml

# Download dependencies (cached layer)
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
COPY --from=builder /app/target/ulticode-backend-0.0.1-SNAPSHOT.jar ./app.jar

# Create non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

ENV SERVER_PORT=9001
EXPOSE 9001

HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
    CMD curl -f http://localhost:9001/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
