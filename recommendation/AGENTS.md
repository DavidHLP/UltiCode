# AGENTS.md — Recommendation Service

> **Last Updated**: 2026-05-07

## OVERVIEW

Dubbo3 + Spark microservices recommendation system (ports 9004/9005). Maven multi-module with 6 modules. **MUST build before backend-spring** — backend depends on `recommend-api`.

## STRUCTURE

```
recommendation/
├── recommend-api/          # Dubbo service interfaces (dependency for backend-spring)
├── recommend-core/         # Algorithm: recall/rank/rerank strategies, 301 tests
├── recommend-feature/      # Feature extraction: UserFeatureExtractor, ProblemFeatureExtractor
├── recommend-provider/     # Dubbo service impl (port 9004), ProviderApplication.java
├── recommend-web/          # REST API (port 9005), WebApplication.java
└── recommend-spark/        # Scala/Spark offline batch jobs
```

## WHERE TO LOOK

| Need | Location |
|------|----------|
| Dubbo service interface | `recommend-api/src/main/java/com/ulticode/recommend/api/` |
| RecommendService impl | `recommend-provider/src/main/java/.../provider/RecommendServiceImpl.java` |
| Recall strategies | `recommend-core/src/main/java/com/ulticode/recommend/core/recall/` |
| Rank/rerank logic | `recommend-core/src/main/java/com/ulticode/recommend/core/rank/`, `rerank/` |
| Feature extraction | `recommend-feature/src/main/java/com/ulticode/recommend/feature/` |
| Spark jobs (Scala) | `recommend-spark/src/main/scala/com/ulticode/recommend/spark/` |
| Strategy tests | `*StrategyTest.java` (ColdStartStrategyTest, HotRecallStrategyTest, etc.) |

## CONVENTIONS

- **Build**: `mvn install -DskipTests` from `recommendation/` root — installs all modules to local repo
- **Start Provider**: `mvn -pl recommend-provider spring-boot:run` (port 9004, Dubbo 20881)
- **Start Web**: `mvn -pl recommend-web spring-boot:run` (port 9005)
- **Tests**: JUnit 5, extensive coverage in `recommend-core` (301 tests) and `recommend-feature` (96 tests)
- **Spark**: Scala 2.13.12, compiled via `scala-maven-plugin`; jobs: OfflineFeatureJob, SimilarityJob, CFTrainingJob
- **Service discovery**: Nacos (port 28848); dev uses `address: N/A` (direct connection)

## ANTI-PATTERNS

- **Scala compile error** `ClassNotFoundException: xsbt.CompilerInterface`: Zinc cache issue — use `mvn compile` (no clean) or delete `~/.sbt`
- **Backend fails to compile**: `recommend-spark` must `mvn install -DskipTests` before `backend-spring` builds — `recommend-api` is a dependency
- **CI uses system `mvn`**: `.github/workflows/ci-recommendation.yml` uses `mvn` not `mvnw` (no wrapper in this project)
- **Provider must start first**: Web has `dubbo.consumer.check=false` but startup order matters for stability
