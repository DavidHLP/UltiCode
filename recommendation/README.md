# UltiCode 推荐模块

> 基于 Dubbo3 + Spark 的分布式编程题目推荐系统

## 项目简介

UltiCode 推荐模块是一个为编程练习平台提供个性化题目推荐的系统。采用分层渐进式架构设计，支持多路召回、多因子排序和多样化重排序策略。

### 核心特性

- 🎯 **多路召回**: 热门召回、内容召回、协同过滤召回、冷启动召回
- 📊 **智能排序**: 基于规则的多因子评分 (难度匹配 + 标签匹配 + 新鲜度 + 质量)
- 🔄 **多样化重排序**: 标签多样性 + 弱项强化
- ⚡ **高性能**: 本地缓存 + TTL 过期策略，响应时间 < 200ms
- 🔧 **微服务架构**: Dubbo3 服务 + REST API
- 📈 **离线计算**: Spark 批处理特征计算和模型训练

## 技术栈

| 组件 | 版本 | 说明 |
|------|------|------|
| Java | 17 | LTS 版本 |
| Spring Boot | 3.2.5 | Web 框架 |
| Dubbo | 3.2.14 | RPC 框架 |
| Apache Spark | 3.5.1 | 离线计算 |
| Scala | 2.13.12 | Spark 开发语言 |
| MySQL | 8.0+ | 数据存储 |
| Redis | 7.0+ | 缓存 (可选) |
| Maven | 3.9+ | 构建工具 |

## 项目结构

```
recommend-module/
├── pom.xml                              # 父 POM
├── recommend-api/                       # Dubbo 服务接口
│   └── src/main/java/com/ulticode/recommend/api/
│       ├── RecommendService.java        # 服务接口
│       ├── dto/                         # 数据传输对象
│       └── enums/                       # 枚举定义
├── recommend-core/                      # 核心算法层
│   └── src/main/java/com/ulticode/recommend/core/
│       ├── recall/                      # 召回策略
│       ├── rank/                        # 排序策略
│       ├── rerank/                      # 重排序策略
│       ├── model/                       # 数据模型
│       ├── evaluator/                   # 离线评估
│       └── RecommendEngine.java         # 推荐引擎
├── recommend-feature/                   # 特征工程层
│   └── src/main/java/com/ulticode/recommend/feature/
│       ├── UserFeatureExtractor.java    # 用户特征提取
│       ├── ProblemFeatureExtractor.java # 题目特征提取
│       └── FeatureStore.java            # 特征存储
├── recommend-provider/                  # Dubbo 服务实现
│   └── src/main/
│       ├── java/.../provider/
│       │   ├── RecommendServiceImpl.java
│       │   └── config/
│       └── resources/application.yml
├── recommend-web/                       # REST API
│   └── src/main/
│       ├── java/.../web/
│       │   ├── RecommendController.java
│       │   └── WebApplication.java
│       └── resources/application.yml
└── recommend-spark/                     # Spark 离线计算
    └── src/main/scala/com/ulticode/recommend/spark/
        ├── OfflineFeatureJob.scala      # 特征计算
        ├── SimilarityJob.scala          # 相似度计算
        └── CFTrainingJob.scala          # CF 模型训练
```

## 快速开始

### 1. 环境准备

```bash
# 检查 Java 版本 (需要 17+)
java -version

# 检查 Maven 版本 (需要 3.9+)
mvn -version
```

### 2. 克隆并编译

```bash
cd /home/davidhlp/project/recommendation

# 编译所有模块
mvn compile

# 运行测试
mvn test
```

### 3. 启动服务

#### 启动 Provider (Dubbo 服务)

```bash
cd recommend-provider
mvn spring-boot:run

# 或者打包后运行
mvn package -DskipTests
java -jar target/recommend-provider-1.0.0-SNAPSHOT.jar
```

Provider 服务端口:
- HTTP: 8081
- Dubbo: 20881

#### 启动 Web API (REST 服务)

```bash
cd recommend-web
mvn spring-boot:run

# 或者打包后运行
mvn package -DskipTests
java -jar target/recommend-web-1.0.0-SNAPSHOT.jar
```

Web 服务端口: 8080

### 4. 测试 API

```bash
# 健康检查
curl http://localhost:8080/api/recommend/health

# 获取推荐
curl -X POST http://localhost:8080/api/recommend \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user123",
    "size": 10,
    "scenario": "DAILY",
    "includeSolved": false
  }'
```

## API 文档

### 推荐接口

**POST** `/api/recommend`

获取个性化题目推荐

**请求参数:**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| userId | String | 是 | - | 用户 ID |
| size | int | 否 | 10 | 返回数量 |
| scenario | String | 否 | DAILY | 推荐场景 |
| sourceProblemId | Long | 否 | null | 源题目 ID (SIMILAR 场景) |
| targetTags | List<String> | 否 | null | 目标标签 |
| includeSolved | boolean | 否 | false | 是否包含已解决 |

**推荐场景 (scenario):**

| 场景 | 说明 |
|------|------|
| DAILY | 日常练习 - 综合推荐 |
| SIMILAR | 相似题目 - 基于源题目推荐 |
| WEAK_POINT | 弱点强化 - 针对薄弱标签 |
| CHALLENGE | 挑战模式 - 推荐难度更高的题目 |

**请求示例:**

```json
{
  "userId": "user123",
  "size": 10,
  "scenario": "DAILY",
  "includeSolved": false
}
```

**响应示例:**

```json
{
  "success": true,
  "code": 200,
  "message": "success",
  "data": {
    "items": [
      {
        "problemId": 1,
        "slug": "two-sum",
        "title": "Two Sum",
        "difficulty": "Easy",
        "score": 0.95,
        "tags": ["array", "hash-table"],
        "reason": "难度匹配度高，标签符合用户偏好"
      }
    ],
    "totalCount": 10,
    "scenario": "DAILY",
    "generatedAt": "2026-03-14T12:00:00"
  }
}
```

### 健康检查

**GET** `/api/recommend/health`

**响应示例:**

```json
{
  "status": "UP",
  "timestamp": "2026-03-14T12:00:00"
}
```

## 推荐算法

### 召回阶段 (Recall)

多路召回策略，按优先级执行:

| 策略 | 优先级 | 说明 |
|------|--------|------|
| ColdStartStrategy | 5 | 冷启动召回，新用户使用 |
| HotRecallStrategy | 10 | 热门题目召回 |
| ContentRecallStrategy | 20 | 基于内容标签召回 |
| CFRecallStrategy | 30 | 协同过滤召回 |

### 排序阶段 (Rank)

多因子加权评分:

```
score = 0.35 × difficultyMatch
      + 0.30 × tagMatch
      + 0.15 × freshness
      + 0.20 × quality
```

### 重排序阶段 (ReRank)

| 策略 | 优先级 | 说明 |
|------|--------|------|
| DiversityReRankStrategy | 50 | 标签多样性，轮询选择 |
| FreshnessReRankStrategy | 40 | 弱项强化，对不熟悉标签加权 |

## Spark 离线任务

### 特征计算任务

```bash
spark-submit \
  --class com.ulticode.recommend.spark.OfflineFeatureJob \
  --master local[*] \
  recommend-spark/target/recommend-spark-1.0.0-SNAPSHOT.jar \
  --input /data/submissions \
  --problems /data/problems \
  --output /features/users \
  --date 2026-03-14
```

### 相似度计算任务

```bash
spark-submit \
  --class com.ulticode.recommend.spark.SimilarityJob \
  --master local[*] \
  recommend-spark/target/recommend-spark-1.0.0-SNAPSHOT.jar \
  --input /data/problems \
  --output /similarity \
  --threshold 0.3 \
  --topK 50
```

### CF 模型训练任务

```bash
spark-submit \
  --class com.ulticode.recommend.spark.CFTrainingJob \
  --master local[*] \
  recommend-spark/target/recommend-spark-1.0.0-SNAPSHOT.jar \
  --input /data/submissions \
  --output /model/cf \
  --rank 10 \
  --maxIter 10
```

## 配置说明

### Provider 配置 (application.yml)

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
    address: N/A  # 开发环境使用直连
  protocol:
    name: dubbo
    port: 20881
  scan:
    base-packages: com.ulticode.recommend.provider
```

### Web 配置 (application.yml)

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
    check: false  # 启动时不检查 Provider
  scan:
    base-packages: com.ulticode.recommend.web
```

## 性能优化

### 缓存策略

- **Provider 缓存**: Caffeine 本地缓存，最大 1000 条，TTL 5 分钟
- **FeatureStore**: TTL 过期策略，默认 5 分钟自动清理

### 性能指标

| 指标 | 目标值 |
|------|--------|
| API 响应时间 | < 200ms |
| 缓存命中率 | > 80% |
| 并发支持 | 1000 QPS |

## 测试覆盖

```bash
# 运行所有测试
mvn test

# 查看覆盖率报告
mvn jacoco:report
```

| 模块 | 测试数 | 覆盖率 |
|------|--------|--------|
| recommend-core | 301 | 95%+ |
| recommend-feature | 96 | 95%+ |
| recommend-web | 11 | 90%+ |

## 常见问题

### Q: 启动时报 Dubbo 连接错误?

A: 确保 Provider 先于 Web 启动，或者设置 `dubbo.consumer.check=false`

### Q: Scala 编译报错 `ClassNotFoundException: xsbt.CompilerInterface`?

A: 这是 Zinc 缓存问题，使用 `mvn compile` (不带 clean) 即可。如果必须 clean，删除 `~/.sbt` 目录后重试。

### Q: 如何修改推荐数量?

A: 在请求中设置 `size` 参数，默认为 10

## 版本历史

- **v1.0.0-SNAPSHOT** (2026-03-14)
  - 完整实现推荐系统核心功能
  - 支持 4 种推荐场景
  - 集成 Spark 离线计算
  - 完成性能优化和测试

## 作者

UltiCode Team

## 许可证

MIT License
