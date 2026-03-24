# UltiCode Recommendation System - Deployment Guide

> Distributed programming problem recommendation system based on Dubbo3 + Spark

## Table of Contents

1. [System Requirements](#1-system-requirements)
2. [Quick Start](#2-quick-start)
3. [Module Overview](#3-module-overview)
4. [Configuration](#4-configuration)
5. [Running Services](#5-running-services)
6. [Production Deployment](#6-production-deployment)
7. [Troubleshooting](#7-troubleshooting)

---

## 1. System Requirements

### 1.1 Required Software

| Software | Version | Purpose |
|----------|---------|---------|
| **Java** | 17+ | Runtime environment |
| **Maven** | 3.9+ | Build tool |
| **MySQL** | 8.0+ | Primary database |

### 1.2 Optional Software

| Software | Version | Purpose |
|----------|---------|---------|
| **Redis** | 7.0+ | Distributed caching (production) |
| **Apache Spark** | 3.5+ | Offline batch processing |
| **Zookeeper** | 3.8+ | Dubbo service registry (production) |

### 1.3 Hardware Requirements

**Development Environment:**
- CPU: 4+ cores
- RAM: 8GB+
- Disk: 20GB+

**Production Environment:**
- CPU: 8+ cores
- RAM: 16GB+
- Disk: 100GB+ (SSD recommended)

---

## 2. Quick Start

### 2.1 Clone and Build

```bash
# Clone the repository
git clone <repository-url>
cd recommendation

# Build all modules
mvn clean install -DskipTests

# Build with tests
mvn clean install
```

### 2.2 Configure Database

1. Create MySQL database:

```sql
CREATE DATABASE ulticode_recommend CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

2. Update database configuration in `recommend-provider/src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ulticode_recommend
    username: your_username
    password: your_password
```

### 2.3 Start Services

```bash
# Terminal 1: Start Provider
cd recommend-provider
mvn spring-boot:run

# Terminal 2: Start Web API
cd recommend-web
mvn spring-boot:run
```

### 2.4 Verify Installation

```bash
# Health check
curl http://localhost:8080/api/recommend/health

# Expected response:
# {"status":"UP","timestamp":"2026-03-14T10:00:00"}
```

---

## 3. Module Overview

### 3.1 Module Structure

```
recommend-module/
├── recommend-api/          # Dubbo3 service interface definitions
├── recommend-core/         # Core recommendation algorithms
├── recommend-feature/      # Feature engineering
├── recommend-provider/     # Dubbo3 service implementation
├── recommend-web/          # REST API gateway
└── recommend-spark/        # Spark offline jobs
```

### 3.2 Module Descriptions

#### recommend-api

**Purpose:** Defines Dubbo3 service interfaces and DTOs.

**Key Components:**
- `RecommendService` - Main recommendation service interface
- `RecommendRequest` - Request DTO
- `RecommendResponse` - Response wrapper
- `RecommendResult` - Result container
- `RecommendItem` - Single recommendation item
- `RecommendScenario` - Recommendation scenarios enum

**Dependencies:** None (pure interface module)

---

#### recommend-core

**Purpose:** Core recommendation algorithms and strategies.

**Key Components:**
- **Recall Layer:** CFRecallStrategy, ContentRecallStrategy, HotRecallStrategy, ColdStartStrategy
- **Rank Layer:** RuleRankStrategy
- **Re-rank Layer:** DiversityReRankStrategy, FreshnessReRankStrategy
- **Evaluation:** OfflineEvaluator

**Algorithm Pipeline:**
```
Recall -> Rank -> Re-rank -> Result
```

---

#### recommend-feature

**Purpose:** Feature extraction and management.

**Key Components:**
- `UserFeatureExtractor` - Extract user features
- `ProblemFeatureExtractor` - Extract problem features
- `FeatureStore` - In-memory feature cache with TTL

**Feature Types:**
- User features: ability, preference, behavior
- Problem features: difficulty, tags, quality

---

#### recommend-provider

**Purpose:** Dubbo3 service provider implementation.

**Key Components:**
- `RecommendServiceImpl` - Main service implementation
- Spring Cache (Caffeine) integration

**Ports:**
- HTTP: 8081
- Dubbo: 20881

---

#### recommend-web

**Purpose:** REST API gateway.

**Key Components:**
- `RecommendController` - REST endpoints

**Endpoints:**
- `POST /api/recommend` - Get recommendations
- `GET /api/recommend/health` - Health check

**Port:** 8080

---

#### recommend-spark

**Purpose:** Offline batch processing jobs.

**Use Cases:**
- Feature pre-computation
- Model training
- Similarity calculation
- User behavior analysis

---

## 4. Configuration

### 4.1 Provider Configuration

File: `recommend-provider/src/main/resources/application.yml`

```yaml
spring:
  application:
    name: recommend-provider
  cache:
    type: caffeine
    caffeine:
      spec: maximumSize=1000,expireAfterWrite=5m

server:
  port: 8081

dubbo:
  application:
    name: recommend-provider
  registry:
    address: N/A  # Use direct connection in dev
  protocol:
    name: dubbo
    port: 20881
  scan:
    base-packages: com.ulticode.recommend.provider
```

### 4.2 Web Configuration

File: `recommend-web/src/main/resources/application.yml`

```yaml
spring:
  application:
    name: recommend-web

server:
  port: 8080

dubbo:
  application:
    name: recommend-web
  registry:
    address: N/A
  consumer:
    check: false
  scan:
    base-packages: com.ulticode.recommend.web
```

### 4.3 Database Configuration

Add to `application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ulticode_recommend
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:}
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
```

### 4.4 Redis Configuration (Production)

```yaml
spring:
  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}
    password: ${REDIS_PASSWORD:}
    timeout: 3000ms
```

### 4.5 Dubbo Registry Configuration (Production)

```yaml
dubbo:
  registry:
    address: zookeeper://${ZK_HOST:localhost}:2181
  protocol:
    name: dubbo
    port: 20881
```

### 4.6 Spark Configuration

For offline jobs, set in Spark submit:

```bash
spark-submit \
  --master spark://localhost:7077 \
  --conf spark.executor.memory=4g \
  --conf spark.executor.cores=2 \
  --class com.ulticode.recommend.spark.OfflineFeatureJob \
  recommend-spark-1.0.0-SNAPSHOT.jar
```

---

## 5. Running Services

### 5.1 Development Mode

**Start Provider:**
```bash
cd recommend-provider
mvn spring-boot:run
```

**Start Web API:**
```bash
cd recommend-web
mvn spring-boot:run
```

### 5.2 Production Mode

**Build JAR files:**
```bash
mvn clean package -DskipTests
```

**Run Provider:**
```bash
java -jar recommend-provider/target/recommend-provider-1.0.0-SNAPSHOT.jar \
  --spring.profiles.active=prod
```

**Run Web API:**
```bash
java -jar recommend-web/target/recommend-web-1.0.0-SNAPSHOT.jar \
  --spring.profiles.active=prod
```

### 5.3 Spark Jobs

**Submit offline feature computation:**
```bash
spark-submit \
  --class com.ulticode.recommend.spark.OfflineFeatureJob \
  recommend-spark/target/recommend-spark-1.0.0-SNAPSHOT.jar \
  --input hdfs://localhost:9000/data/submissions \
  --output hdfs://localhost:9000/features
```

---

## 6. Production Deployment

### 6.1 Architecture Overview

```
                    ┌─────────────────┐
                    │  Load Balancer  │
                    │    (Nginx)      │
                    └────────┬────────┘
                             │
         ┌───────────────────┼───────────────────┐
         │                   │                   │
         ▼                   ▼                   ▼
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│  recommend-web  │ │  recommend-web  │ │  recommend-web  │
│    (node 1)     │ │    (node 2)     │ │    (node 3)     │
│   Port: 8080    │ │   Port: 8080    │ │   Port: 8080    │
└────────┬────────┘ └────────┬────────┘ └────────┬────────┘
         │                   │                   │
         └───────────────────┼───────────────────┘
                             │ Dubbo3 RPC
         ┌───────────────────┼───────────────────┐
         │                   │                   │
         ▼                   ▼                   ▼
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│recommend-provider│ │recommend-provider│ │recommend-provider│
│    (node 1)     │ │    (node 2)     │ │    (node 3)     │
│  Port: 8081     │ │  Port: 8081     │ │  Port: 8081     │
│  Dubbo: 20881   │ │  Dubbo: 20881   │ │  Dubbo: 20881   │
└────────┬────────┘ └────────┬────────┘ └────────┬────────┘
         │                   │                   │
         └───────────────────┼───────────────────┘
                             │
         ┌───────────────────┼───────────────────┐
         │                   │                   │
         ▼                   ▼                   ▼
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│    Zookeeper    │ │      MySQL      │ │      Redis      │
│   (Registry)    │ │    (Primary)    │ │     (Cache)     │
└─────────────────┘ └─────────────────┘ └─────────────────┘
```

### 6.2 Docker Deployment

**Dockerfile for recommend-web:**

```dockerfile
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY recommend-web/target/recommend-web-1.0.0-SNAPSHOT.jar app.jar

ENV JAVA_OPTS="-Xms512m -Xmx1024m"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

**Dockerfile for recommend-provider:**

```dockerfile
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY recommend-provider/target/recommend-provider-1.0.0-SNAPSHOT.jar app.jar

ENV JAVA_OPTS="-Xms512m -Xmx1024m"

EXPOSE 8081 20881

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

**Build and run:**
```bash
# Build images
docker build -t ulticode/recommend-web:latest -f Dockerfile.web .
docker build -t ulticode/recommend-provider:latest -f Dockerfile.provider .

# Run containers
docker run -d -p 8080:8080 --name recommend-web ulticode/recommend-web:latest
docker run -d -p 8081:8081 -p 20881:20881 --name recommend-provider ulticode/recommend-provider:latest
```

### 6.3 Kubernetes Deployment

**Deployment YAML example:**

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: recommend-web
  labels:
    app: recommend-web
spec:
  replicas: 3
  selector:
    matchLabels:
      app: recommend-web
  template:
    metadata:
      labels:
        app: recommend-web
    spec:
      containers:
      - name: recommend-web
        image: ulticode/recommend-web:latest
        ports:
        - containerPort: 8080
        env:
        - name: JAVA_OPTS
          value: "-Xms512m -Xmx1024m"
        - name: DUBBO_REGISTRY_ADDRESS
          valueFrom:
            configMapKeyRef:
              name: recommend-config
              key: dubbo.registry.address
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
        livenessProbe:
          httpGet:
            path: /api/recommend/health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /api/recommend/health
            port: 8080
          initialDelaySeconds: 5
          periodSeconds: 5
---
apiVersion: v1
kind: Service
metadata:
  name: recommend-web
spec:
  selector:
    app: recommend-web
  ports:
  - port: 80
    targetPort: 8080
  type: ClusterIP
```

### 6.4 Monitoring

**Health Check Endpoint:**
```
GET /api/recommend/health
```

**Response:**
```json
{
  "status": "UP",
  "timestamp": "2026-03-14T10:00:00"
}
```

**Recommended Monitoring Stack:**
- Prometheus + Grafana for metrics
- ELK Stack for logging
- Zipkin/Jaeger for distributed tracing

**Key Metrics to Monitor:**
- Request latency (P50, P99)
- Error rate
- Cache hit ratio
- Dubbo service call success rate
- JVM memory usage

### 6.5 Logging

**Log Configuration (logback-spring.xml):**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <springProfile name="dev">
        <root level="DEBUG">
            <appender-ref ref="CONSOLE"/>
        </root>
    </springProfile>
    <springProfile name="prod">
        <root level="INFO">
            <appender-ref ref="FILE"/>
        </root>
    </springProfile>
</configuration>
```

---

## 7. Troubleshooting

### 7.1 Common Issues

**Issue: Provider fails to start**

```
Solution: Check database connection and ensure MySQL is running.
Verify application.yml datasource configuration.
```

**Issue: Dubbo connection refused**

```
Solution: Ensure provider is running before starting web module.
Check Dubbo port (20881) is not blocked by firewall.
```

**Issue: Cache not working**

```
Solution: Verify Caffeine configuration in application.yml.
Check @EnableCaching is present in configuration class.
```

### 7.2 Debug Mode

Enable debug logging:

```yaml
logging:
  level:
    com.ulticode.recommend: DEBUG
    org.apache.dubbo: DEBUG
```

### 7.3 Performance Tuning

**JVM Options:**
```bash
JAVA_OPTS="-Xms1g -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
```

**Cache Tuning:**
```yaml
spring:
  cache:
    caffeine:
      spec: maximumSize=5000,expireAfterWrite=10m
```

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2026-03-14 | Initial release |

---

*Document Version: 1.0*
*Last Updated: 2026-03-14*
*Author: UltiCode Development Team*
