# UltiCode 推荐模块实施计划

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 构建基于 Dubbo3 + Spark 的分布式编程题目推荐系统，实现个性化题目推荐

**Architecture:** 采用分层渐进式架构：纯算法核心层 → 特征工程层 → Dubbo3 微服务封装 → Spark 离线计算

**Tech Stack:** Java 17, Spring Boot 3.2.x, Dubbo 3.2.x, Spark 3.5.x, MySQL 9.x, Redis 7.x, Maven 3.9.x

**Spec Document:** `docs/superpowers/specs/2026-03-13-recommendation-module-design.md`

---

## 文件结构

```
recommend-module/
├── pom.xml                                    # 父 POM
├── recommend-api/                             # Dubbo 服务接口
│   ├── pom.xml
│   └── src/main/java/com/ulticode/recommend/api/
│       ├── RecommendService.java              # 推荐服务接口
│       ├── dto/
│       │   ├── RecommendRequest.java
│       │   ├── RecommendResult.java
│       │   └── RecommendResponse.java
│       └── enums/
│           └── RecommendScenario.java
├── recommend-core/                            # 核心算法
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/ulticode/recommend/core/
│       │   ├── recall/
│       │   │   ├── RecallStrategy.java        # 召回接口
│       │   │   ├── CFRecallStrategy.java      # 协同过滤召回
│       │   │   ├── ContentRecallStrategy.java # 内容召回
│       │   │   ├── HotRecallStrategy.java     # 热门召回
│       │   │   └── ColdStartStrategy.java     # 冷启动召回
│       │   ├── rank/
│       │   │   ├── RankStrategy.java          # 排序接口
│       │   │   └── RuleRankStrategy.java      # 规则排序
│       │   ├── rerank/
│       │   │   ├── ReRankStrategy.java        # 重排序接口
│       │   │   ├── DiversityReRankStrategy.java
│       │   │   └── FreshnessReRankStrategy.java
│       │   ├── model/
│       │   │   ├── RecommendContext.java
│       │   │   ├── RecommendItem.java
│       │   │   └── UserProfile.java
│       │   ├── evaluator/
│       │   │   ├── OfflineEvaluator.java      # 离线评估
│       │   │   └── OfflineMetrics.java        # 评估指标
│       │   └── RecommendEngine.java           # 推荐引擎入口
│       └── test/java/com/ulticode/recommend/core/
│           ├── recall/
│           ├── rank/
│           ├── rerank/
│           └── evaluator/
├── recommend-feature/                         # 特征工程
│   ├── pom.xml
│   └── src/main/java/com/ulticode/recommend/feature/
│       ├── UserFeatureExtractor.java
│       ├── ProblemFeatureExtractor.java
│       ├── FeatureStore.java
│       └── model/
│           ├── UserFeatures.java
│           └── ProblemFeatures.java
├── recommend-provider/                        # Dubbo 服务实现
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/ulticode/recommend/provider/
│       │   └── RecommendServiceImpl.java
│       └── resources/
│           └── application.yml
├── recommend-web/                             # Web 入口
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/ulticode/recommend/web/
│       │   ├── RecommendController.java
│       │   └── RecommendApplication.java
│       └── resources/
│           └── application.yml
└── recommend-spark/                           # Spark 离线计算
    ├── pom.xml
    └── src/main/scala/com/ulticode/recommend/spark/
        ├── OfflineFeatureJob.scala
        ├── SimilarityJob.scala
        └── CFTrainingJob.scala
```

---

## Chunk 1: 项目骨架与数据准备

### Task 1.1: 创建父 POM

**Files:**
- Create: `pom.xml`

- [ ] **Step 1: 创建父 POM 文件**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.ulticode</groupId>
    <artifactId>recommend-module</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>pom</packaging>

    <name>UltiCode Recommend Module</name>
    <description>Distributed programming problem recommendation system based on Dubbo3 + Spark</description>

    <modules>
        <module>recommend-api</module>
        <module>recommend-core</module>
        <module>recommend-feature</module>
        <module>recommend-provider</module>
        <module>recommend-web</module>
        <module>recommend-spark</module>
    </modules>

    <properties>
        <java.version>17</java.version>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>

        <!-- Spring Boot -->
        <spring-boot.version>3.2.5</spring-boot.version>

        <!-- Dubbo -->
        <dubbo.version>3.2.14</dubbo.version>

        <!-- Spark -->
        <spark.version>3.5.1</spark.version>
        <scala.version>2.13.12</scala.version>
        <scala.binary.version>2.13</scala.binary.version>

        <!-- Database -->
        <mysql.version>8.0.33</mysql.version>
        <mybatis.version>3.0.3</mybatis.version>

        <!-- Cache -->
        <jedis.version>5.1.0</jedis.version>

        <!-- Testing -->
        <junit.version>5.10.2</junit.version>
        <mockito.version>5.11.0</mockito.version>

        <!-- Utilities -->
        <lombok.version>1.18.30</lombok.version>
        <slf4j.version>2.0.12</slf4j.version>
        <jackson.version>2.17.0</jackson.version>

        <!-- Testing -->
        <junit.version>5.10.2</junit.version>
        <mockito.version>5.11.0</mockito.version>
        <jacoco.version>0.8.11</jacoco.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <!-- Spring Boot BOM -->
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>${spring-boot.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>

            <!-- Dubbo BOM -->
            <dependency>
                <groupId>org.apache.dubbo</groupId>
                <artifactId>dubbo-bom</artifactId>
                <version>${dubbo.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>

            <!-- Internal Modules -->
            <dependency>
                <groupId>com.ulticode</groupId>
                <artifactId>recommend-api</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.ulticode</groupId>
                <artifactId>recommend-core</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.ulticode</groupId>
                <artifactId>recommend-feature</artifactId>
                <version>${project.version}</version>
            </dependency>

            <!-- MySQL -->
            <dependency>
                <groupId>com.mysql</groupId>
                <artifactId>mysql-connector-j</artifactId>
                <version>${mysql.version}</version>
            </dependency>

            <!-- MyBatis -->
            <dependency>
                <groupId>org.mybatis.spring.boot</groupId>
                <artifactId>mybatis-spring-boot-starter</artifactId>
                <version>${mybatis.version}</version>
            </dependency>

            <!-- Redis (Jedis) -->
            <dependency>
                <groupId>redis.clients</groupId>
                <artifactId>jedis</artifactId>
                <version>${jedis.version}</version>
            </dependency>

            <!-- Spark -->
            <dependency>
                <groupId>org.apache.spark</groupId>
                <artifactId>spark-core_${scala.binary.version}</artifactId>
                <version>${spark.version}</version>
            </dependency>
            <dependency>
                <groupId>org.apache.spark</groupId>
                <artifactId>spark-sql_${scala.binary.version}</artifactId>
                <version>${spark.version}</version>
            </dependency>
            <dependency>
                <groupId>org.apache.spark</groupId>
                <artifactId>spark-mllib_${scala.binary.version}</artifactId>
                <version>${spark.version}</version>
            </dependency>

            <!-- Lombok -->
            <dependency>
                <groupId>org.projectlombok</groupId>
                <artifactId>lombok</artifactId>
                <version>${lombok.version}</version>
            </dependency>

            <!-- SLF4J -->
            <dependency>
                <groupId>org.slf4j</groupId>
                <artifactId>slf4j-api</artifactId>
                <version>${slf4j.version}</version>
            </dependency>

            <!-- Jackson -->
            <dependency>
                <groupId>com.fasterxml.jackson.core</groupId>
                <artifactId>jackson-databind</artifactId>
                <version>${jackson.version}</version>
            </dependency>
            <dependency>
                <groupId>com.fasterxml.jackson.datatype</groupId>
                <artifactId>jackson-datatype-jsr310</artifactId>
                <version>${jackson.version}</version>
            </dependency>

            <!-- Testing -->
            <dependency>
                <groupId>org.junit.jupiter</groupId>
                <artifactId>junit-jupiter</artifactId>
                <version>${junit.version}</version>
                <scope>test</scope>
            </dependency>
            <dependency>
                <groupId>org.mockito</groupId>
                <artifactId>mockito-core</artifactId>
                <version>${mockito.version}</version>
                <scope>test</scope>
            </dependency>
            <dependency>
                <groupId>org.mockito</groupId>
                <artifactId>mockito-junit-jupiter</artifactId>
                <version>${mockito.version}</version>
                <scope>test</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-maven-plugin</artifactId>
                    <version>${spring-boot.version}</version>
                </plugin>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <version>3.12.1</version>
                    <configuration>
                        <source>${java.version}</source>
                        <target>${java.version}</target>
                        <encoding>${project.build.sourceEncoding}</encoding>
                    </configuration>
                </plugin>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-surefire-plugin</artifactId>
                    <version>3.2.5</version>
                </plugin>
                <plugin>
                    <groupId>net.alchim31.maven</groupId>
                    <artifactId>scala-maven-plugin</artifactId>
                    <version>4.8.1</version>
                </plugin>
                <plugin>
                    <groupId>org.jacoco</groupId>
                    <artifactId>jacoco-maven-plugin</artifactId>
                    <version>${jacoco.version}</version>
                    <executions>
                        <execution>
                            <goals>
                                <goal>prepare-agent</goal>
                            </goals>
                        </execution>
                        <execution>
                            <id>report</id>
                            <phase>test</phase>
                            <goals>
                                <goal>report</goal>
                            </goals>
                        </execution>
                    </executions>
                </plugin>
            </plugins>
        </pluginManagement>
    </build>
</project>
```

- [ ] **Step 2: 验证 POM 语法（使用 xmllint）**

Run: `xmllint --noout /home/davidhlp/project/recommendation/pom.xml && echo "POM syntax valid"`
Expected: `POM syntax valid`

- [ ] **Step 3: 创建 .gitignore**

```gitignore
# /home/davidhlp/project/recommendation/.gitignore

# Maven
target/
pom.xml.tag
pom.xml.releaseBackup
pom.xml.versionsBackup
release.properties

# IDE
.idea/
*.iml
*.iws
*.ipr
.vscode/
*.swp

# OS
.DS_Store
Thumbs.db

# Logs
*.log
logs/
```

- [ ] **Step 4: 初始化 Git 仓库并提交**

```bash
cd /home/davidhlp/project/recommendation
[ -d .git ] || git init
git add pom.xml .gitignore
git commit -m "chore: initialize project with parent POM"
```

---

### Task 1.2: 创建 recommend-core 模块骨架

**Files:**
- Create: `recommend-core/pom.xml`
- Create: `recommend-core/src/main/java/com/ulticode/recommend/core/package-info.java`
- Create: `recommend-core/src/test/java/com/ulticode/recommend/core/package-info.java`

- [ ] **Step 1: 创建 recommend-core 目录结构**

```bash
mkdir -p recommend-core/src/main/java/com/ulticode/recommend/core/{recall,rank,rerank,model,evaluator}
mkdir -p recommend-core/src/test/java/com/ulticode/recommend/core/{recall,rank,rerank,evaluator}
```

- [ ] **Step 2: 创建 recommend-core/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.ulticode</groupId>
        <artifactId>recommend-module</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>recommend-core</artifactId>
    <name>Recommend Core</name>
    <description>Core recommendation algorithms</description>

    <dependencies>
        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <scope>provided</scope>
        </dependency>

        <!-- SLF4J -->
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
        </dependency>

        <!-- Jackson -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.datatype</groupId>
            <artifactId>jackson-datatype-jsr310</artifactId>
        </dependency>

        <!-- Testing -->
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.mockito</groupId>
            <artifactId>mockito-core</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.mockito</groupId>
            <artifactId>mockito-junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 3: 创建 package-info.java**

`recommend-core/src/main/java/com/ulticode/recommend/core/package-info.java`:
```java
/**
 * Core recommendation algorithms.
 *
 * <p>This module contains:
 * <ul>
 *   <li>Recall strategies - candidate generation</li>
 *   <li>Rank strategies - scoring and ordering</li>
 *   <li>Re-rank strategies - diversity and freshness</li>
 *   <li>Evaluation - offline metrics</li>
 * </ul>
 */
package com.ulticode.recommend.core;
```

- [ ] **Step 4: 验证模块编译**

Run: `cd /home/davidhlp/project/recommendation && mvn compile -pl recommend-core`
Expected: `BUILD SUCCESS`

- [ ] **Step 5: 提交**

```bash
git add recommend-core/
git commit -m "chore(core): add recommend-core module skeleton"
```

---

### Task 1.3: 创建核心数据模型

**Files:**
- Create: `recommend-core/src/main/java/com/ulticode/recommend/core/model/RecommendItem.java`
- Create: `recommend-core/src/main/java/com/ulticode/recommend/core/model/RecommendContext.java`
- Create: `recommend-core/src/main/java/com/ulticode/recommend/core/model/UserProfile.java`
- Test: `recommend-core/src/test/java/com/ulticode/recommend/core/model/RecommendItemTest.java`

- [ ] **Step 1: 编写 RecommendItem 测试**

`recommend-core/src/test/java/com/ulticode/recommend/core/model/RecommendItemTest.java`:
```java
package com.ulticode.recommend.core.model;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RecommendItemTest {

    @Test
    void shouldCreateRecommendItemWithBuilder() {
        RecommendItem item = RecommendItem.builder()
                .problemId(1L)
                .title("Two Sum")
                .difficulty("Easy")
                .score(0.95)
                .tags(Set.of("Array", "Hash Table"))
                .build();

        assertEquals(1L, item.getProblemId());
        assertEquals("Two Sum", item.getTitle());
        assertEquals("Easy", item.getDifficulty());
        assertEquals(0.95, item.getScore(), 0.001);
        assertTrue(item.getTags().contains("Array"));
    }

    @Test
    void shouldCalculateScoreCorrectly() {
        RecommendItem item = RecommendItem.builder()
                .problemId(1L)
                .difficulty("Medium")
                .difficultyMatchScore(0.8)
                .tagMatchScore(0.6)
                .freshnessScore(0.5)
                .qualityScore(0.9)
                .build();

        // score = 0.35*0.8 + 0.30*0.6 + 0.15*0.5 + 0.20*0.9 = 0.71
        assertEquals(0.71, item.calculateFinalScore(), 0.001);
    }

    @Test
    void shouldCompareByScore() {
        RecommendItem item1 = RecommendItem.builder().problemId(1L).score(0.8).build();
        RecommendItem item2 = RecommendItem.builder().problemId(2L).score(0.9).build();

        assertTrue(item2.compareTo(item1) > 0);
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd /home/davidhlp/project/recommendation && mvn test -pl recommend-core -Dtest=RecommendItemTest`
Expected: `Tests run: 3, Failures: 3` (class not found)

- [ ] **Step 3: 实现 RecommendItem**

`recommend-core/src/main/java/com/ulticode/recommend/core/model/RecommendItem.java`:
```java
package com.ulticode.recommend.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * Represents a single recommendation item (problem).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendItem implements Comparable<RecommendItem> {

    /** Problem ID */
    private Long problemId;

    /** Problem slug (identifier) */
    private String slug;

    /** Problem title */
    private String title;

    /** Difficulty level: Easy, Medium, Hard */
    private String difficulty;

    /** Final recommendation score (0-1) */
    private double score;

    /** Problem tags */
    private Set<String> tags;

    /** Reason for recommendation */
    private String reason;

    // Score components
    private double difficultyMatchScore;
    private double tagMatchScore;
    private double freshnessScore;
    private double qualityScore;

    /** Score weights */
    private static final double WEIGHT_DIFFICULTY = 0.35;
    private static final double WEIGHT_TAG = 0.30;
    private static final double WEIGHT_FRESHNESS = 0.15;
    private static final double WEIGHT_QUALITY = 0.20;

    /**
     * Calculate final score from components.
     */
    public double calculateFinalScore() {
        return WEIGHT_DIFFICULTY * difficultyMatchScore
             + WEIGHT_TAG * tagMatchScore
             + WEIGHT_FRESHNESS * freshnessScore
             + WEIGHT_QUALITY * qualityScore;
    }

    @Override
    public int compareTo(RecommendItem other) {
        return Double.compare(other.score, this.score); // Descending order
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd /home/davidhlp/project/recommendation && mvn test -pl recommend-core -Dtest=RecommendItemTest`
Expected: `Tests run: 3, Failures: 0`

- [ ] **Step 5: 编写 UserProfile 测试**

`recommend-core/src/test/java/com/ulticode/recommend/core/model/UserProfileTest.java`:
```java
package com.ulticode.recommend.core.model;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class UserProfileTest {

    @Test
    void shouldCreateUserProfileWithBuilder() {
        UserProfile profile = UserProfile.builder()
                .userId("user-001")
                .rating(1500)
                .solvedProblems(Set.of(1L, 2L, 3L))
                .tagMastery(Map.of("Array", 0.8, "DP", 0.3))
                .preferredDifficulty("Medium")
                .build();

        assertEquals("user-001", profile.getUserId());
        assertEquals(1500, profile.getRating());
        assertEquals(3, profile.getSolvedProblems().size());
        assertEquals(0.8, profile.getTagMastery().get("Array"), 0.001);
    }

    @Test
    void shouldIdentifyWeakTags() {
        UserProfile profile = UserProfile.builder()
                .userId("user-001")
                .tagMastery(Map.of(
                        "Array", 0.9,
                        "DP", 0.2,
                        "Graph", 0.3,
                        "Tree", 0.8
                ))
                .build();

        Set<String> weakTags = profile.getWeakTags(0.4);

        assertTrue(weakTags.contains("DP"));
        assertTrue(weakTags.contains("Graph"));
        assertFalse(weakTags.contains("Array"));
    }

    @Test
    void shouldDetermineAppropriateDifficulty() {
        UserProfile newbie = UserProfile.builder().rating(1000).build();
        UserProfile expert = UserProfile.builder().rating(2500).build();

        assertEquals("Easy", newbie.getAppropriateDifficulty());
        assertEquals("Hard", expert.getAppropriateDifficulty());
    }
}
```

- [ ] **Step 6: 运行测试确认失败**

Run: `cd /home/davidhlp/project/recommendation && mvn test -pl recommend-core -Dtest=UserProfileTest`
Expected: `Tests run: 3, Failures: 3` (class not found)

- [ ] **Step 7: 实现 UserProfile**

`recommend-core/src/main/java/com/ulticode/recommend/core/model/UserProfile.java`:
```java
package com.ulticode.recommend.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * User profile for recommendation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile {

    /** User ID */
    private String userId;

    /** User's current rating */
    private int rating;

    /** Historical max rating */
    private int maxRating;

    /** Preferred programming language */
    private String preferredLanguage;

    /** Set of solved problem IDs */
    private Set<Long> solvedProblems;

    /** Mastery level per tag (0-1) */
    private Map<String, Double> tagMastery;

    /** Submission count per difficulty */
    private Map<String, Integer> difficultyStats;

    /** User's preferred difficulty based on rating */
    private String preferredDifficulty;

    /** Total solved count */
    private int totalSolved;

    /** Total attempt count */
    private int totalAttempts;

    /**
     * Get weak tags where mastery is below threshold.
     */
    public Set<String> getWeakTags(double threshold) {
        if (tagMastery == null) {
            return Set.of();
        }
        return tagMastery.entrySet().stream()
                .filter(e -> e.getValue() < threshold)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    /**
     * Get appropriate difficulty based on rating.
     */
    public String getAppropriateDifficulty() {
        if (rating < 1200) {
            return "Easy";
        } else if (rating < 1800) {
            return "Medium";
        } else {
            return "Hard";
        }
    }

    /**
     * Check if problem is already solved.
     */
    public boolean hasSolved(Long problemId) {
        return solvedProblems != null && solvedProblems.contains(problemId);
    }
}
```

- [ ] **Step 8: 运行测试确认通过**

Run: `cd /home/davidhlp/project/recommendation && mvn test -pl recommend-core -Dtest=UserProfileTest`
Expected: `Tests run: 3, Failures: 0`

- [ ] **Step 9: 编写 RecommendContext 测试**

`recommend-core/src/test/java/com/ulticode/recommend/core/model/RecommendContextTest.java`:
```java
package com.ulticode.recommend.core.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RecommendContextTest {

    @Test
    void shouldCreateContextWithDefaults() {
        RecommendContext context = RecommendContext.builder()
                .userId("user-001")
                .build();

        assertEquals("user-001", context.getUserId());
        assertEquals(10, context.getSize()); // default size
        assertEquals(RecommendContext.Scenario.DAILY, context.getScenario());
    }

    @Test
    void shouldSupportDifferentScenarios() {
        RecommendContext daily = RecommendContext.builder()
                .scenario(RecommendContext.Scenario.DAILY)
                .build();
        RecommendContext weakPoint = RecommendContext.builder()
                .scenario(RecommendContext.Scenario.WEAK_POINT)
                .build();
        RecommendContext challenge = RecommendContext.builder()
                .scenario(RecommendContext.Scenario.CHALLENGE)
                .build();

        assertEquals(RecommendContext.Scenario.DAILY, daily.getScenario());
        assertEquals(RecommendContext.Scenario.WEAK_POINT, weakPoint.getScenario());
        assertEquals(RecommendContext.Scenario.CHALLENGE, challenge.getScenario());
    }
}
```

- [ ] **Step 10: 运行测试确认失败**

Run: `cd /home/davidhlp/project/recommendation && mvn test -pl recommend-core -Dtest=RecommendContextTest`
Expected: `Tests run: 2, Failures: 2` (class not found)

- [ ] **Step 11: 实现 RecommendContext**

`recommend-core/src/main/java/com/ulticode/recommend/core/model/RecommendContext.java`:
```java
package com.ulticode.recommend.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Context for recommendation request.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendContext {

    /** User ID */
    private String userId;

    /** Number of recommendations to return */
    @Builder.Default
    private int size = 10;

    /** Recommendation scenario */
    @Builder.Default
    private Scenario scenario = Scenario.DAILY;

    /** Source problem ID (for similar problem scenario) */
    private Long sourceProblemId;

    /** Target tags (for weak point scenario) */
    private String[] targetTags;

    /** Minimum difficulty */
    private String minDifficulty;

    /** Maximum difficulty */
    private String maxDifficulty;

    /** Whether to include solved problems */
    @Builder.Default
    private boolean includeSolved = false;

    /**
     * Recommendation scenario enum.
     */
    public enum Scenario {
        /** Daily recommendation */
        DAILY,
        /** Similar problems */
        SIMILAR,
        /** Weak point practice */
        WEAK_POINT,
        /** Challenge mode */
        CHALLENGE
    }
}
```

- [ ] **Step 12: 运行测试确认通过**

Run: `cd /home/davidhlp/project/recommendation && mvn test -pl recommend-core -Dtest=RecommendContextTest`
Expected: `Tests run: 2, Failures: 0`

- [ ] **Step 13: 运行所有模型测试**

Run: `cd /home/davidhlp/project/recommendation && mvn test -pl recommend-core -Dtest="*Test"`
Expected: `Tests run: 8, Failures: 0`

- [ ] **Step 14: 提交**

```bash
git add recommend-core/
git commit -m "feat(core): add core data models (RecommendItem, UserProfile, RecommendContext)"
```

---

### Task 1.4: 创建召回策略接口

**Files:**
- Create: `recommend-core/src/main/java/com/ulticode/recommend/core/recall/RecallStrategy.java`
- Test: `recommend-core/src/test/java/com/ulticode/recommend/core/recall/RecallStrategyTest.java`

- [ ] **Step 1: 编写 RecallStrategy 测试**

`recommend-core/src/test/java/com/ulticode/recommend/core/recall/RecallStrategyTest.java`:
```java
package com.ulticode.recommend.core.recall;

import com.ulticode.recommend.core.model.RecommendContext;
import com.ulticode.recommend.core.model.RecommendItem;
import com.ulticode.recommend.core.model.UserProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecallStrategyTest {

    @Test
    void shouldCreateSimpleRecallStrategy() {
        RecallStrategy strategy = (context, profile, problems) -> List.of(
                RecommendItem.builder().problemId(1L).score(0.9).build(),
                RecommendItem.builder().problemId(2L).score(0.8).build()
        );

        List<RecommendItem> results = strategy.recall(
                RecommendContext.builder().userId("user-001").build(),
                UserProfile.builder().userId("user-001").build(),
                List.of()
        );

        assertEquals(2, results.size());
        assertEquals(0.9, results.get(0).getScore(), 0.001);
    }

    @Test
    void shouldHaveDefaultGetName() {
        RecallStrategy strategy = (context, profile, problems) -> List.of();

        assertNotNull(strategy.getName());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd /home/davidhlp/project/recommendation && mvn test -pl recommend-core -Dtest=RecallStrategyTest`
Expected: `Tests run: 2, Failures: 2` (class not found)

- [ ] **Step 3: 实现 RecallStrategy 接口**

`recommend-core/src/main/java/com/ulticode/recommend/core/recall/RecallStrategy.java`:
```java
package com.ulticode.recommend.core.recall;

import com.ulticode.recommend.core.model.RecommendContext;
import com.ulticode.recommend.core.model.RecommendItem;
import com.ulticode.recommend.core.model.UserProfile;

import java.util.List;

/**
 * Strategy interface for recall phase.
 *
 * <p>Recall is the first stage of recommendation pipeline,
 * responsible for generating candidate items from large corpus.
 */
public interface RecallStrategy {

    /**
     * Recall candidate items.
     *
     * @param context recommendation context
     * @param profile user profile
     * @param availableProblems all available problems (pre-filtered)
     * @return list of candidate items (unsorted)
     */
    List<RecommendItem> recall(
            RecommendContext context,
            UserProfile profile,
            List<RecommendItem> availableProblems
    );

    /**
     * Get strategy name for logging and debugging.
     */
    default String getName() {
        return this.getClass().getSimpleName();
    }

    /**
     * Get priority (higher = more important).
     * Used for multi-strategy fusion.
     */
    default int getPriority() {
        return 0;
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd /home/davidhlp/project/recommendation && mvn test -pl recommend-core -Dtest=RecallStrategyTest`
Expected: `Tests run: 2, Failures: 0`

- [ ] **Step 5: 提交**

```bash
git add recommend-core/
git commit -m "feat(core): add RecallStrategy interface"
```

---

### Task 1.5: 创建排序策略接口

**Files:**
- Create: `recommend-core/src/main/java/com/ulticode/recommend/core/rank/RankStrategy.java`
- Test: `recommend-core/src/test/java/com/ulticode/recommend/core/rank/RankStrategyTest.java`

- [ ] **Step 1: 编写 RankStrategy 测试**

`recommend-core/src/test/java/com/ulticode/recommend/core/rank/RankStrategyTest.java`:
```java
package com.ulticode.recommend.core.rank;

import com.ulticode.recommend.core.model.RecommendContext;
import com.ulticode.recommend.core.model.RecommendItem;
import com.ulticode.recommend.core.model.UserProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RankStrategyTest {

    @Test
    void shouldSortByScore() {
        RankStrategy strategy = (items, context, profile) -> items.stream()
                .sorted()
                .toList();

        List<RecommendItem> items = List.of(
                RecommendItem.builder().problemId(1L).score(0.5).build(),
                RecommendItem.builder().problemId(2L).score(0.9).build(),
                RecommendItem.builder().problemId(3L).score(0.7).build()
        );

        List<RecommendItem> ranked = strategy.rank(
                items,
                RecommendContext.builder().build(),
                UserProfile.builder().build()
        );

        assertEquals(3, ranked.size());
        assertEquals(2L, ranked.get(0).getProblemId()); // highest score first
        assertEquals(3L, ranked.get(1).getProblemId());
        assertEquals(1L, ranked.get(2).getProblemId());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd /home/davidhlp/project/recommendation && mvn test -pl recommend-core -Dtest=RankStrategyTest`
Expected: `Tests run: 1, Failures: 1` (class not found)

- [ ] **Step 3: 实现 RankStrategy 接口**

`recommend-core/src/main/java/com/ulticode/recommend/core/rank/RankStrategy.java`:
```java
package com.ulticode.recommend.core.rank;

import com.ulticode.recommend.core.model.RecommendContext;
import com.ulticode.recommend.core.model.RecommendItem;
import com.ulticode.recommend.core.model.UserProfile;

import java.util.List;

/**
 * Strategy interface for ranking phase.
 *
 * <p>Ranking is the second stage of recommendation pipeline,
 * responsible for scoring and ordering candidate items.
 */
public interface RankStrategy {

    /**
     * Rank candidate items.
     *
     * @param items candidate items from recall phase
     * @param context recommendation context
     * @param profile user profile
     * @return ranked list of items (sorted by score descending)
     */
    List<RecommendItem> rank(
            List<RecommendItem> items,
            RecommendContext context,
            UserProfile profile
    );

    /**
     * Get strategy name.
     */
    default String getName() {
        return this.getClass().getSimpleName();
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd /home/davidhlp/project/recommendation && mvn test -pl recommend-core -Dtest=RankStrategyTest`
Expected: `Tests run: 1, Failures: 0`

- [ ] **Step 5: 提交**

```bash
git add recommend-core/
git commit -m "feat(core): add RankStrategy interface"
```

---

### Task 1.6: 创建重排序策略接口

**Files:**
- Create: `recommend-core/src/main/java/com/ulticode/recommend/core/rerank/ReRankStrategy.java`
- Test: `recommend-core/src/test/java/com/ulticode/recommend/core/rerank/ReRankStrategyTest.java`

- [ ] **Step 1: 编写 ReRankStrategy 测试**

`recommend-core/src/test/java/com/ulticode/recommend/core/rerank/ReRankStrategyTest.java`:
```java
package com.ulticode.recommend.core.rerank;

import com.ulticode.recommend.core.model.RecommendContext;
import com.ulticode.recommend.core.model.RecommendItem;
import com.ulticode.recommend.core.model.UserProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReRankStrategyTest {

    @Test
    void shouldApplyReRanking() {
        ReRankStrategy strategy = (items, context, profile) -> items; // identity

        List<RecommendItem> items = List.of(
                RecommendItem.builder().problemId(1L).score(0.9).build()
        );

        List<RecommendItem> result = strategy.rerank(
                items,
                RecommendContext.builder().build(),
                UserProfile.builder().build()
        );

        assertEquals(1, result.size());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd /home/davidhlp/project/recommendation && mvn test -pl recommend-core -Dtest=ReRankStrategyTest`
Expected: `Tests run: 1, Failures: 1` (class not found)

- [ ] **Step 3: 实现 ReRankStrategy 接口**

`recommend-core/src/main/java/com/ulticode/recommend/core/rerank/ReRankStrategy.java`:
```java
package com.ulticode.recommend.core.rerank;

import com.ulticode.recommend.core.model.RecommendContext;
import com.ulticode.recommend.core.model.RecommendItem;
import com.ulticode.recommend.core.model.UserProfile;

import java.util.List;

/**
 * Strategy interface for re-ranking phase.
 *
 * <p>Re-ranking is the final stage of recommendation pipeline,
 * responsible for adjusting ranking based on business rules
 * (diversity, freshness, etc.)
 */
public interface ReRankStrategy {

    /**
     * Re-rank items.
     *
     * @param items ranked items from rank phase
     * @param context recommendation context
     * @param profile user profile
     * @return re-ranked list of items
     */
    List<RecommendItem> rerank(
            List<RecommendItem> items,
            RecommendContext context,
            UserProfile profile
    );

    /**
     * Get strategy name.
     */
    default String getName() {
        return this.getClass().getSimpleName();
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd /home/davidhlp/project/recommendation && mvn test -pl recommend-core -Dtest=ReRankStrategyTest`
Expected: `Tests run: 1, Failures: 0`

- [ ] **Step 5: 提交**

```bash
git add recommend-core/
git commit -m "feat(core): add ReRankStrategy interface"
```

---

### Task 1.7: 验证 Chunk 1 完成

- [ ] **Step 1: 运行所有测试**

Run: `cd /home/davidhlp/project/recommendation && mvn test -pl recommend-core`
Expected: `Tests run: 12, Failures: 0`

- [ ] **Step 2: 检查代码覆盖率**

Run: `cd /home/davidhlp/project/recommendation && mvn jacoco:report -pl recommend-core`
Expected: Coverage > 80% for model classes

- [ ] **Step 3: 确认文件结构**

Run: `tree recommend-core/src -I target`
Expected structure matches design.

---

**Chunk 1 Complete.** Ready for review before proceeding to Chunk 2.

---

## Chunk 2: 召回策略实现

### Task 2.1: 实现热门召回 (HotRecallStrategy)

**Files:**
- Create: `recommend-core/src/main/java/com/ulticode/recommend/core/recall/HotRecallStrategy.java`
- Test: `recommend-core/src/test/java/com/ulticode/recommend/core/recall/HotRecallStrategyTest.java`

**实现要点:**
- 按提交量和通过率筛选热门题目
- 过滤用户已做题目
- 按用户 rating 匹配难度

### Task 2.2: 实现内容召回 (ContentRecallStrategy)

**Files:**
- Create: `recommend-core/src/main/java/com/ulticode/recommend/core/recall/ContentRecallStrategy.java`
- Test: `recommend-core/src/test/java/com/ulticode/recommend/core/recall/ContentRecallStrategyTest.java`

**实现要点:**
- 提取用户已通过题目的标签分布
- 计算未做题目与已做题目的标签相似度
- 按相似度排序，取 Top N

### Task 2.3: 实现协同过滤召回 (CFRecallStrategy)

**Files:**
- Create: `recommend-core/src/main/java/com/ulticode/recommend/core/recall/CFRecallStrategy.java`
- Create: `recommend-core/src/main/java/com/ulticode/recommend/core/recall/UserSimilarityCalculator.java`
- Test: `recommend-core/src/test/java/com/ulticode/recommend/core/recall/CFRecallStrategyTest.java`

**实现要点:**
- 计算用户-题目交互矩阵
- 基于做题记录计算用户相似度（余弦相似度）
- 找出相似用户做过但当前用户未做的题目

### Task 2.4: 实现冷启动召回 (ColdStartStrategy)

**Files:**
- Create: `recommend-core/src/main/java/com/ulticode/recommend/core/recall/ColdStartStrategy.java`
- Test: `recommend-core/src/test/java/com/ulticode/recommend/core/recall/ColdStartStrategyTest.java`

**实现要点:**
- 新用户：基于初始 rating → 热门题目 + 难度匹配
- 新题目：基于标签相似度推荐 + 新题加权

---

## Chunk 3: 排序与重排序策略

### Task 3.1: 实现规则排序 (RuleRankStrategy)

**Files:**
- Create: `recommend-core/src/main/java/com/ulticode/recommend/core/rank/RuleRankStrategy.java`
- Test: `recommend-core/src/test/java/com/ulticode/recommend/core/rank/RuleRankStrategyTest.java`

**评分公式:**
```
score = 0.35 * difficultyMatch + 0.30 * tagMatch + 0.15 * freshness + 0.20 * quality
```

### Task 3.2: 实现多样性重排序 (DiversityReRankStrategy)

**Files:**
- Create: `recommend-core/src/main/java/com/ulticode/recommend/core/rerank/DiversityReRankStrategy.java`
- Test: `recommend-core/src/test/java/com/ulticode/recommend/core/rerank/DiversityReRankStrategyTest.java`

**算法:**
1. 对候选结果按标签分组
2. 从每组中选择高分题目
3. 确保最终结果标签分布均匀

### Task 3.3: 实现新鲜度重排序 (FreshnessReRankStrategy)

**Files:**
- Create: `recommend-core/src/main/java/com/ulticode/recommend/core/rerank/FreshnessReRankStrategy.java`
- Test: `recommend-core/src/test/java/com/ulticode/recommend/core/rerank/FreshnessReRankStrategyTest.java`

**算法:**
1. 分析用户最近做题的标签分布
2. 对较少出现的标签类型题目加权

---

## Chunk 4: 推荐引擎与离线评估

### Task 4.1: 实现推荐引擎 (RecommendEngine)

**Files:**
- Create: `recommend-core/src/main/java/com/ulticode/recommend/core/RecommendEngine.java`
- Test: `recommend-core/src/test/java/com/ulticode/recommend/core/RecommendEngineTest.java`

**流程:**
```
用户请求 → 特征提取 → 多路召回 → 合并去重 → 排序 → 重排序 → 返回结果
```

### Task 4.2: 实现离线评估器 (OfflineEvaluator)

**Files:**
- Create: `recommend-core/src/main/java/com/ulticode/recommend/core/evaluator/OfflineMetrics.java`
- Create: `recommend-core/src/main/java/com/ulticode/recommend/core/evaluator/OfflineEvaluator.java`
- Test: `recommend-core/src/test/java/com/ulticode/recommend/core/evaluator/OfflineEvaluatorTest.java`

**指标:**
- Precision, Recall, F1-Score
- NDCG (归一化折损累积增益)
- Coverage (覆盖率)
- Diversity (多样性)

### Task 4.3: 实现演示 Main 方法

**Files:**
- Create: `recommend-core/src/main/java/com/ulticode/recommend/core/Demo.java`

---

## Chunk 5: 特征工程层

### Task 5.1: 创建 recommend-feature 模块

**Files:**
- Create: `recommend-feature/pom.xml`
- Create: `recommend-feature/src/main/java/com/ulticode/recommend/feature/`

### Task 5.2: 实现用户特征提取器

**Files:**
- Create: `recommend-feature/src/main/java/com/ulticode/recommend/feature/model/UserFeatures.java`
- Create: `recommend-feature/src/main/java/com/ulticode/recommend/feature/UserFeatureExtractor.java`
- Test: `recommend-feature/src/test/java/com/ulticode/recommend/feature/UserFeatureExtractorTest.java`

### Task 5.3: 实现题目特征提取器

**Files:**
- Create: `recommend-feature/src/main/java/com/ulticode/recommend/feature/model/ProblemFeatures.java`
- Create: `recommend-feature/src/main/java/com/ulticode/recommend/feature/ProblemFeatureExtractor.java`
- Test: `recommend-feature/src/test/java/com/ulticode/recommend/feature/ProblemFeatureExtractorTest.java`

### Task 5.4: 实现特征存储

**Files:**
- Create: `recommend-feature/src/main/java/com/ulticode/recommend/feature/FeatureStore.java`
- Test: `recommend-feature/src/test/java/com/ulticode/recommend/feature/FeatureStoreTest.java`

---

## Chunk 6: Dubbo3 微服务封装

### Task 6.1: 创建 recommend-api 模块

**Files:**
- Create: `recommend-api/pom.xml`
- Create: `recommend-api/src/main/java/com/ulticode/recommend/api/RecommendService.java`
- Create: `recommend-api/src/main/java/com/ulticode/recommend/api/dto/*.java`

### Task 6.2: 创建 recommend-provider 模块

**Files:**
- Create: `recommend-provider/pom.xml`
- Create: `recommend-provider/src/main/java/com/ulticode/recommend/provider/RecommendServiceImpl.java`
- Create: `recommend-provider/src/main/resources/application.yml`

### Task 6.3: 创建 recommend-web 模块

**Files:**
- Create: `recommend-web/pom.xml`
- Create: `recommend-web/src/main/java/com/ulticode/recommend/web/RecommendController.java`
- Create: `recommend-web/src/main/java/com/ulticode/recommend/web/RecommendApplication.java`
- Create: `recommend-web/src/main/resources/application.yml`

---

## Chunk 7: Spark 离线计算

### Task 7.1: 创建 recommend-spark 模块

**Files:**
- Create: `recommend-spark/pom.xml`
- Create: `recommend-spark/src/main/scala/com/ulticode/recommend/spark/`

### Task 7.2: 实现离线特征计算任务

**Files:**
- Create: `recommend-spark/src/main/scala/com/ulticode/recommend/spark/OfflineFeatureJob.scala`

### Task 7.3: 实现相似度计算任务

**Files:**
- Create: `recommend-spark/src/main/scala/com/ulticode/recommend/spark/SimilarityJob.scala`

### Task 7.4: 实现 CF 模型训练任务

**Files:**
- Create: `recommend-spark/src/main/scala/com/ulticode/recommend/spark/CFTrainingJob.scala`

---

## Chunk 8: 集成测试与优化

### Task 8.1: 端到端测试

**Files:**
- Create: `recommend-web/src/test/java/com/ulticode/recommend/web/E2ETest.java`

### Task 8.2: 性能优化

**优化项:**
- Redis 缓存策略
- 推荐响应时间 < 200ms

### Task 8.3: 文档完善

**Files:**
- Create: `docs/DEPLOYMENT.md`
- Create: `docs/API.md`

---

## 执行顺序

```
Chunk 1 (项目骨架) → Chunk 2 (召回) → Chunk 3 (排序) → Chunk 4 (引擎)
                                                                      ↓
Chunk 8 (测试) ← Chunk 7 (Spark) ← Chunk 6 (Dubbo) ← Chunk 5 (特征)
```

---

*计划版本: 1.0*
*创建日期: 2026-03-13*
*作者: Claude Code*
