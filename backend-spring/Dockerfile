# Stage 1: Build
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

# Copy Maven wrapper and pom.xml first for dependency caching
COPY backend-spring/.mvn/ ./.mvn/
COPY backend-spring/mvnw ./mvnw
COPY backend-spring/pom.xml ./pom.xml

# Copy recommendation-api module (local dependency not in Maven Central)
COPY recommendation/pom.xml ./recommend-pom.xml
COPY recommendation/recommend-api ./recommend-api

# Copy Maven wrapper to recommend-api (it doesn't have its own)
COPY backend-spring/mvnw ./recommend-api/mvnw
COPY backend-spring/.mvn/ ./recommend-api/.mvn

# Build and install recommend-api to local Maven repository
WORKDIR /app/recommend-api
RUN chmod +x ./mvnw && ./mvnw install -DskipTests -B

# Return to app directory and download backend dependencies
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
    CMD curl -f http://localhost:9001/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
