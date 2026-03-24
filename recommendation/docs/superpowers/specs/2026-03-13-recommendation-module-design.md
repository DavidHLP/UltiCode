# UltiCode 推荐模块设计文档

> 基于 Dubbo3 + Spark 的分布式编程题目推荐系统

## 一、项目概述

### 1.1 项目背景

UltiCode 是一个面向编程竞赛选手的在线编程训练平台。本项目为该平台的**推荐模块**，采用 Dubbo3 分布式微服务架构，基于 Spark 进行大规模数据处理和机器学习推荐。

### 1.2 项目定位

- **项目性质**：本科毕业设计
- **核心功能**：为用户提供个性化的编程题目推荐
- **技术特色**：分布式微服务架构 + 大数据推荐算法

### 1.3 设计决策

| 决策项 | 选择 | 理由 |
|--------|------|------|
| 实现范围 | Phase 1-4 完整实现 | 满足毕业设计完整性要求 |
| Spark 部署 | 本地 Spark 模式 | 适合开发测试，足够演示 |
| 实现策略 | 算法优先 | 便于快速验证和调试 |
| 开发方案 | 分层渐进式 | 每阶段成果可验证 |

---

## 二、系统架构

### 2.1 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        UltiCode 主平台                           │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐            │
│  │ 用户服务 │  │ 题目服务 │  │ 竞赛服务 │  │ 论坛服务 │            │
│  └────┬────┘  └────┬────┘  └────┬────┘  └────┬────┘            │
└───────┼────────────┼────────────┼────────────┼──────────────────┘
        │            │            │            │
        └────────────┴─────┬──────┴────────────┘
                           │ Dubbo3 RPC
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│                      推荐模块 (本项目)                            │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │                    Dubbo3 服务层                             ││
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐    ││
│  │  │推荐API服务│  │特征计算服务│  │排序服务  │  │召回服务   │    ││
│  │  └──────────┘  └──────────┘  └──────────┘  └──────────┘    ││
│  └─────────────────────────────────────────────────────────────┘│
│  ┌─────────────────────────────────────────────────────────────┐│
│  │                    Spark 计算层                              ││
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐    ││
│  │  │离线特征  │  │模型训练  │  │相似度计算│  │协同过滤   │    ││
│  │  └──────────┘  └──────────┘  └──────────┘  └──────────┘    ││
│  └─────────────────────────────────────────────────────────────┘│
│  ┌─────────────────────────────────────────────────────────────┐│
│  │                    数据存储层                                ││
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐    ││
│  │  │  MySQL   │  │  Redis   │  │  HDFS    │  │ClickHouse│    ││
│  │  └──────────┘  └──────────┘  └──────────┘  └──────────┘    ││
│  └─────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 技术栈

| 层次 | 技术选型 | 版本 |
|------|----------|------|
| **微服务框架** | Dubbo + Spring Boot | 3.2.x + 3.2.x |
| **大数据处理** | Apache Spark | 3.5.x |
| **数据库** | MySQL | 9.x |
| **缓存** | Redis | 7.x |
| **消息队列** | Apache Kafka | 3.x |
| **开发语言** | Java / Scala | 17 / 2.13 |
| **构建工具** | Maven | 3.9.x |

### 2.3 模块划分

```
recommend-module/
├── pom.xml                          # 父 POM
├── recommend-api/                   # Dubbo 服务接口定义
│   ├── src/main/java/
│   │   └── com/ulticode/recommend/api/
│   │       ├── RecommendService.java
│   │       ├── FeatureService.java
│   │       └── dto/
│   │           ├── RecommendRequest.java
│   │           ├── RecommendResult.java
│   │           └── UserFeatures.java
│   └── pom.xml
│
├── recommend-core/                  # 核心推荐算法 (Phase 1)
│   ├── src/main/java/
│   │   └── com/ulticode/recommend/core/
│   │       ├── recall/              # 召回层
│   │       │   ├── RecallStrategy.java
│   │       │   ├── CFRecallStrategy.java
│   │       │   ├── ContentRecallStrategy.java
│   │       │   └── HotRecallStrategy.java
│   │       ├── rank/                # 排序层
│   │       │   ├── RankStrategy.java
│   │       │   └── RuleRankStrategy.java
│   │       ├── rerank/              # 重排序
│   │       │   ├── ReRankStrategy.java
│   │       │   ├── DiversityReRankStrategy.java
│   │       │   └── FreshnessReRankStrategy.java
│   │       └── RecommendEngine.java # 推荐引擎入口
│   ├── src/test/java/               # 单元测试
│   └── pom.xml
│
├── recommend-feature/               # 特征工程 (Phase 2)
│   ├── src/main/java/
│   │   └── com/ulticode/recommend/feature/
│   │       ├── UserFeatureExtractor.java
│   │       ├── ProblemFeatureExtractor.java
│   │       ├── FeatureStore.java
│   │       └── model/
│   │           ├── UserFeatures.java
│   │           └── ProblemFeatures.java
│   └── pom.xml
│
├── recommend-provider/              # Dubbo 服务实现 (Phase 3)
│   ├── src/main/java/
│   │   └── com/ulticode/recommend/provider/
│   │       └── RecommendServiceImpl.java
│   ├── src/main/resources/
│   │   └── application.yml
│   └── pom.xml
│
├── recommend-web/                   # Web 服务入口 (Phase 3)
│   ├── src/main/java/
│   │   └── com/ulticode/recommend/web/
│   │       ├── RecommendController.java
│   │       └── RecommendApplication.java
│   ├── src/main/resources/
│   │   └── application.yml
│   └── pom.xml
│
└── recommend-spark/                 # Spark 离线计算 (Phase 4)
    ├── src/main/scala/
    │   └── com/ulticode/recommend/spark/
    │       ├── OfflineFeatureJob.scala
    │       ├── SimilarityJob.scala
    │       └── CFTrainingJob.scala
    └── pom.xml
```

---

## 三、数据模型

### 3.1 核心数据实体（已有）

基于现有数据库结构：

#### 用户数据 (users)
| 字段 | 类型 | 说明 |
|------|------|------|
| id | varchar(40) | 用户ID |
| username | varchar(120) | 用户名 |
| rating | int | 竞赛积分 |
| preferred_language | varchar(50) | 偏好编程语言 |
| role | enum | 用户角色 |

#### 题目数据 (problems)
| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 题目ID |
| slug | varchar(120) | 题目标识 |
| title | varchar(255) | 题目标题 |
| difficulty | enum | 难度 (Easy/Medium/Hard) |
| acceptance_rate | decimal | 通过率 |

#### 题目标签 (problem_tags)
| 字段 | 类型 | 说明 |
|------|------|------|
| id | varchar(40) | 标签ID |
| label | varchar(120) | 标签名称 |
| slug | varchar(120) | 标签标识 |

#### 提交记录 (submissions)
| 字段 | 类型 | 说明 |
|------|------|------|
| id | varchar(40) | 提交ID |
| user_id | varchar(40) | 用户ID |
| problem_id | bigint | 题目ID |
| language | varchar(20) | 编程语言 |
| status | enum | 提交状态 |
| runtime | int | 运行时间(ms) |
| memory | int | 内存使用(KB) |

#### 全球排名 (global_rankings)
| 字段 | 类型 | 说明 |
|------|------|------|
| user_id | varchar(40) | 用户ID |
| rating | int | 当前积分 |
| max_rating | int | 历史最高积分 |
| rating_title | enum | 段位称号 |

### 3.2 推荐系统数据模型（新增）

#### 用户特征 (UserFeatures)
```java
public class UserFeatures {
    private String userId;
    private int rating;                              // 当前积分
    private int maxRating;                           // 历史最高积分
    private String ratingTitle;                      // 段位称号
    private String preferredLanguage;                // 偏好语言
    private Map<String, Double> tagMastery;          // 各标签掌握度 (0-1)
    private Map<String, Integer> difficultyStats;    // 各难度通过数
    private int totalSolved;                         // 总通过数
    private int totalAttempts;                       // 总提交数
    private double averageAcceptanceRate;            // 平均通过率
    private LocalDateTime lastActiveTime;            // 最后活跃时间
}
```

#### 题目特征 (ProblemFeatures)
```java
public class ProblemFeatures {
    private long problemId;
    private String slug;
    private String title;
    private String difficulty;                       // Easy/Medium/Hard
    private double acceptanceRate;                   // 通过率
    private Set<String> tags;                        // 标签集合
    private int submissionCount;                     // 提交数
    private int solvedCount;                         // 通过数
    private double qualityScore;                     // 质量分数
    private LocalDateTime createdAt;                 // 创建时间
}
```

#### 推荐结果 (RecommendResult)
```java
public class RecommendResult {
    private long problemId;
    private String title;
    private String difficulty;
    private double score;                            // 推荐分数
    private String reason;                           // 推荐理由
    private List<String> tags;
}
```

---

## 四、推荐算法设计

### 4.1 推荐流程

```
用户请求 → 特征提取 → 多路召回 → 合并去重 → 排序 → 重排序 → 返回结果
```

### 4.2 召回策略

#### 4.2.1 协同过滤召回 (CFRecallStrategy)

**原理**：找到与当前用户相似的 K 个用户，推荐他们做过但当前用户未做的题目

**算法**：
1. 计算用户-题目交互矩阵
2. 基于做题记录计算用户相似度（余弦相似度）
3. 找出相似用户做过但当前用户未做的题目
4. 按相似度加权排序，取 Top N

**数据依赖**：`submissions` 表

#### 4.2.2 内容召回 (ContentRecallStrategy)

**原理**：基于题目内容相似度，推荐与用户已做题目相似的题目

**算法**：
1. 提取用户已通过题目的标签分布
2. 计算未做题目与已做题目的标签相似度
3. 按相似度排序，取 Top N

**数据依赖**：`problems`, `problem_tags`, `submissions` 表

#### 4.2.3 热门召回 (HotRecallStrategy)

**原理**：推荐热门且符合用户难度的题目

**算法**：
1. 按提交量和通过率筛选热门题目
2. 过滤用户已做题目
3. 按用户当前能力匹配难度
4. 取 Top N

**数据依赖**：`problems` 表

### 4.3 排序策略

#### 4.3.1 规则排序 (RuleRankStrategy)

**评分公式**：
```
score = w1 * difficultyMatch + w2 * tagMatch + w3 * freshness + w4 * quality
```

| 因子 | 说明 | 权重 |
|------|------|------|
| difficultyMatch | 难度匹配度（基于用户 rating） | 0.35 |
| tagMatch | 标签匹配度（基于用户偏好标签） | 0.30 |
| freshness | 新鲜度（新题优先） | 0.15 |
| quality | 质量分数（通过率、点赞数） | 0.20 |

### 4.4 重排序策略

#### 4.4.1 多样性重排序 (DiversityReRankStrategy)

**目标**：避免推荐结果过于集中在某类题目

**算法**：
1. 对候选结果按标签分组
2. 从每组中选择高分题目
3. 确保最终结果标签分布均匀

#### 4.4.2 新鲜度重排序 (FreshnessReRankStrategy)

**目标**：优先推荐用户较长时间未练习的标签类型

**算法**：
1. 分析用户最近做题的标签分布
2. 对较少出现的标签类型题目加权

### 4.5 冷启动策略 (P0 关键)

#### 4.5.1 新用户冷启动

**问题**：新注册用户无历史做题记录，协同过滤和内容召回无法工作

**解决方案**：

```java
public class ColdStartStrategy implements RecallStrategy {

    @Override
    public List<RecommendResult> recall(String userId, int size) {
        // 1. 检查是否有问卷偏好（如果有）
        UserPreference preference = getUserPreference(userId);
        if (preference != null) {
            return recommendByPreference(preference, size);
        }

        // 2. 基于用户 rating 初始值推荐
        int rating = getUserRating(userId);
        String difficulty = mapRatingToDifficulty(rating);

        // 3. 热门题目 + 难度匹配
        return hotRecall.recallByDifficulty(difficulty, size);
    }

    private String mapRatingToDifficulty(int rating) {
        if (rating < 1200) return "Easy";
        if (rating < 1600) return "Easy,Medium";
        return "Medium,Hard";
    }
}
```

**冷启动推荐流程**：
```
新用户 → 检查问卷偏好 → 有 → 基于偏好推荐
                     → 无 → 基于初始 rating → 热门题目 + 难度匹配
```

#### 4.5.2 新题目冷启动

**问题**：新上架题目无提交记录，协同过滤无法推荐

**解决方案**：
1. 基于标签相似度推荐（内容召回）
2. 新题加权（freshness factor）
3. 初始曝光池机制

```java
public class NewProblemStrategy implements RecallStrategy {

    @Override
    public List<RecommendResult> recall(String userId, int size) {
        // 1. 获取新题列表（7天内上架）
        List<Problem> newProblems = getNewProblems(7);

        // 2. 基于用户已做题目标签匹配
        Set<String> userTags = getUserDoneTags(userId);

        // 3. 计算标签匹配度并排序
        return newProblems.stream()
            .filter(p -> hasCommonTag(p, userTags))
            .sorted((a, b) -> Double.compare(
                calcTagMatchScore(b, userTags),
                calcTagMatchScore(a, userTags)))
            .limit(size)
            .collect(toList());
    }
}
```

### 4.6 算法评估指标 (P0 关键)

#### 4.6.1 离线评估指标

```java
public class OfflineMetrics {

    // 准确率：推荐题目中用户实际完成的比例
    private double precision;

    // 召回率：用户完成的题目中被推荐的比例
    private double recall;

    // F1 分数
    private double f1Score;

    // NDCG：归一化折损累积增益（考虑排序位置）
    private double ndcg;

    // 覆盖率：推荐题目占总题目的比例
    private double coverage;

    // 多样性：推荐结果的标签分布熵
    private double diversity;

    // 新颖性：推荐冷门题目的比例
    private double novelty;
}
```

**离线评估流程**：
```
1. 时间切分：使用前 N 天数据训练，后 M 天数据测试
2. 对测试集中的用户生成推荐
3. 计算推荐结果与实际行为的匹配度
4. 输出各指标值
```

#### 4.6.2 在线评估指标

```java
public class OnlineMetrics {

    // 点击率：推荐题目被点击的比例
    private double ctr;

    // 完成率：推荐题目被完成的比例
    private double solveRate;

    // 平均完成时间
    private double avgSolveTime;

    // 用户满意度（点赞、收藏）
    private double satisfactionRate;

    // 推荐采纳率：用户从推荐列表开始做题的比例
    private double adoptionRate;
}
```

#### 4.6.3 A/B 测试设计

```java
public class ABTestConfig {

    // 实验ID
    private String experimentId;

    // 实验组配置
    private Map<String, StrategyConfig> experimentGroups;

    // 流量分配（如：对照组 50%，实验组 50%）
    private Map<String, Double> trafficAllocation;

    // 评估周期（天）
    private int evaluationPeriod;

    // 显著性水平
    private double significanceLevel;
}
```

**A/B 测试流程**：
1. 用户分流：基于用户ID hash 分配到不同组
2. 策略执行：不同组使用不同推荐策略
3. 数据收集：记录用户行为和推荐结果
4. 效果评估：对比各组的在线指标

---

## 五、接口设计

### 5.1 Dubbo 服务接口

```java
public interface RecommendService {

    /**
     * 获取每日推荐
     * @param userId 用户ID
     * @param size 推荐数量
     * @return 推荐结果列表
     */
    List<RecommendResult> getDailyRecommend(String userId, int size);

    /**
     * 获取相似题目
     * @param problemId 题目ID
     * @param size 推荐数量
     * @return 相似题目列表
     */
    List<RecommendResult> getSimilarProblems(long problemId, int size);

    /**
     * 获取薄弱点推荐
     * @param userId 用户ID
     * @param size 推荐数量
     * @return 薄弱点题目列表
     */
    List<RecommendResult> getWeakPointRecommend(String userId, int size);

    /**
     * 获取进阶推荐
     * @param userId 用户ID
     * @param size 推荐数量
     * @return 进阶题目列表
     */
    List<RecommendResult> getChallengeRecommend(String userId, int size);

    /**
     * 实时反馈更新
     * @param userId 用户ID
     * @param problemId 题目ID
     * @param type 反馈类型
     */
    void updateFeedback(String userId, long problemId, FeedbackType type);
}
```

### 5.2 REST API

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 每日推荐 | GET | `/api/v1/recommend/daily` | 获取每日推荐题目 |
| 相似题目 | GET | `/api/v1/recommend/similar/{problemId}` | 获取相似题目 |
| 薄弱点推荐 | GET | `/api/v1/recommend/weak-point` | 获取薄弱点题目 |
| 进阶推荐 | GET | `/api/v1/recommend/challenge` | 获取进阶题目 |
| 反馈 | POST | `/api/v1/recommend/feedback` | 提交用户反馈 |

---

## 六、实施计划

### Phase 0: 数据准备阶段 (预计 0.5 周)

**目标**：数据清洗和样本数据准备

**任务清单**：
- [ ] 数据探索与分析
  - [ ] 分析 users、problems、submissions 表数据分布
  - [ ] 统计用户活跃度、题目热度
  - [ ] 识别数据质量问题（缺失值、异常值）
- [ ] 数据清洗
  - [ ] 处理缺失的标签数据
  - [ ] 过滤无效提交记录（如编译错误）
  - [ ] 标准化数据格式
- [ ] 样本数据准备
  - [ ] 提取训练样本（用户-题目交互）
  - [ ] 时间切分（训练集/测试集）
  - [ ] 生成离线评估基准数据

**验收标准**：
- 完成数据质量报告
- 训练/测试数据集就绪
- 可用于算法开发

### Phase 1: 算法核心层 (预计 2 周)

**目标**：实现可独立运行的推荐算法

**任务清单**：
- [ ] 创建 Maven 多模块项目结构
- [ ] 实现 `recommend-core` 模块
  - [ ] 召回策略接口和实现
    - [ ] RecallStrategy 接口
    - [ ] CFRecallStrategy
    - [ ] ContentRecallStrategy
    - [ ] HotRecallStrategy
    - [ ] ColdStartStrategy (P0)
  - [ ] 排序策略接口和实现
    - [ ] RankStrategy 接口
    - [ ] RuleRankStrategy
  - [ ] 重排序策略接口和实现
    - [ ] ReRankStrategy 接口
    - [ ] DiversityReRankStrategy
    - [ ] FreshnessReRankStrategy
  - [ ] RecommendEngine 入口类
- [ ] 实现离线评估模块 (P0)
  - [ ] OfflineEvaluator
  - [ ] 指标计算（Precision, Recall, NDCG, Coverage）
- [ ] 编写单元测试（覆盖率 > 80%）
- [ ] 实现 Main 方法演示

**验收标准**：
- 所有单元测试通过
- 可通过 Main 方法演示推荐结果
- 代码覆盖率 > 80%
- 离线评估指标可输出

### Phase 2: 特征工程层 (预计 1 周)

**目标**：实现特征提取和存储

**任务清单**：
- [ ] 实现 `recommend-feature` 模块
  - [ ] UserFeatureExtractor
  - [ ] ProblemFeatureExtractor
  - [ ] FeatureStore（基于 Redis）
- [ ] 实现特征更新机制
  - [ ] 增量更新
  - [ ] 全量更新
- [ ] 集成到 recommend-core
- [ ] 编写单元测试

**验收标准**：
- 特征提取功能正常
- 特征可存储到 Redis
- 与 recommend-core 集成成功

### Phase 3: Dubbo3 微服务封装 (预计 2 周)

**目标**：将算法封装为 Dubbo 微服务

**任务清单**：
- [ ] 实现 `recommend-api` 模块
  - [ ] 定义服务接口
  - [ ] 定义 DTO 类
  - [ ] 定义请求/响应包装类
- [ ] 实现 `recommend-provider` 模块
  - [ ] 服务实现类
  - [ ] Dubbo 配置
- [ ] 实现 `recommend-web` 模块
  - [ ] REST Controller
  - [ ] Spring Boot 配置
- [ ] 联调测试
  - [ ] 与主平台 Dubbo 调用联调
  - [ ] 集成测试

**验收标准**：
- Dubbo 服务可正常注册
- REST API 可正常调用
- 返回正确推荐结果
- 与主平台联调成功

### Phase 4: Spark 离线计算 (预计 1.5 周)

**目标**：实现离线特征计算和模型训练

**任务清单**：
- [ ] 实现 `recommend-spark` 模块
  - [ ] OfflineFeatureJob（用户特征计算）
  - [ ] SimilarityJob（题目相似度计算）
  - [ ] CFTrainingJob（协同过滤模型训练）
- [ ] 配置本地 Spark 运行环境
- [ ] 模型存储与加载
- [ ] 编写任务调度脚本

**验收标准**：
- Spark 任务可正常运行
- 特征数据可正确存储
- 模型可正确加载

### Phase 5: 集成测试与优化 (预计 0.5 周)

**目标**：端到端测试和性能优化

**任务清单**：
- [ ] 端到端测试
  - [ ] 完整推荐流程测试
  - [ ] 异常场景测试
- [ ] 性能优化
  - [ ] 缓存策略优化
  - [ ] 推荐响应时间 < 200ms
- [ ] 文档完善
  - [ ] 部署文档
  - [ ] API 文档

**验收标准**：
- 端到端测试通过
- 性能指标达标
- 文档完整

### 时间估算汇总

| 阶段 | 原计划 | 调整后 | 理由 |
|------|--------|--------|------|
| Phase 0: 数据准备 | - | **0.5 周** | 新增，数据是算法基础 |
| Phase 1: 算法核心 | 1.5 周 | **2 周** | 增加冷启动和评估模块 |
| Phase 2: 特征工程 | 1 周 | **1 周** | 合理 |
| Phase 3: Dubbo 封装 | 1.5 周 | **2 周** | 增加联调时间 |
| Phase 4: Spark 计算 | 1 周 | **1.5 周** | Spark 集成复杂度 |
| Phase 5: 集成测试 | - | **0.5 周** | 新增，端到端测试 |
| **总计** | **5 周** | **7.5 周** | 更现实的估算 |

---

## 七、测试策略

### 7.1 单元测试

- 使用 JUnit 5 + Mockito
- 覆盖率目标：> 80%
- 重点测试：召回、排序、重排序策略

### 7.2 集成测试

- 使用 Spring Boot Test
- 测试 Dubbo 服务调用
- 测试数据库和 Redis 连接

### 7.3 性能测试

- 推荐响应时间 < 200ms (P99)
- 支持并发请求 > 100 QPS

---

## 八、部署架构

### 8.1 开发环境

| 组件 | 端口 | 说明 |
|------|------|------|
| MySQL | 23306 | 主数据库 |
| Redis | 26379 | 缓存/特征存储 |
| Zookeeper | 2181 | Dubbo 注册中心 |
| recommend-web | 8080 | Web 服务 |

### 8.2 运行命令

```bash
# 启动 Web 服务
java -jar recommend-web/target/recommend-web.jar

# 运行 Spark 离线任务
spark-submit --class com.ulticode.recommend.spark.OfflineFeatureJob \
  recommend-spark/target/recommend-spark.jar
```

---

## 九、风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 算法效果不佳 | 用户体验差 | 多策略融合，A/B 测试 |
| 性能瓶颈 | 响应慢 | Redis 缓存，异步计算 |
| 数据稀疏 | 协同过滤效果差 | 结合内容推荐，热门兜底 |

---

*文档版本: 1.0*
*创建日期: 2026-03-13*
*作者: Claude Code*
