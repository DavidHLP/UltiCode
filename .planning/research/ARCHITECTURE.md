# Architecture Patterns

**Project:** UltiCode v1.9 Performance & Quality
**Researched:** 2026-04-22
**Domain:** Spring Boot 3.2.5 + MyBatis-Plus 3.5.16 + MySQL + JaCoCo 0.8.12

---

## Recommended Architecture

### Component Boundaries

| Component | Responsibility | Communicates With |
|-----------|---------------|-------------------|
| `AchievementServiceImpl` | Achievement CRUD, user progress, points calculation | `AchievementMapper`, `UserAchievementMapper`, `SubmissionMapper` |
| `AchievementTriggerServiceImpl` | Async achievement checking and awarding | `AchievementMapper`, `UserAchievementMapper`, `RealtimeService` |
| `FollowServiceImpl` | Follow/unfollow, follower/following lists with pagination | `FollowMapper`, `UserMapper`, `NotificationService` |
| `MybatisPlusConfig` | MyBatis-Plus interceptor chain (pagination, optimistic lock) | Framework-level |
| `db-manager` | Flyway migration execution via Python CLI | MySQL via Docker |

### Data Flow

**Achievement N+1 Query Pattern (before):**
```
getUserPoints(userId)
  1. userAchievementMapper.findByUserId(userId)         -- query 1: N user_achievements
  2. FOR EACH ua: achievementMapper.selectById(...)    -- query 2..N+1: N separate achievement lookups
  = N+1 queries total
```

**Achievement Optimized Pattern (after):**
```
getUserPoints(userId)
  1. achievementMapper.findByUserIdWithAchievements(userId)  -- single JOIN query
  = 1 query total
```

**Follow Pagination Pattern (before and after):**
```
getFollowers(userId, page, pageSize)
  1. followMapper.selectByFollowingIdPaged(...)   -- index scan on following_id
  2. userMapper.selectBatchIds(...)                 -- batch fetch user details
  = 2 queries total (no change needed, already efficient)
```

---

## MyBatis-Plus Query Optimization Integration

### Current MybatisPlusInterceptor Configuration

**File:** `backend-spring/src/main/java/com/ulticode/common/config/MybatisPlusConfig.java`

```java
@Bean
public MybatisPlusInterceptor mybatisPlusInterceptor() {
    MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
    interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
    interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
    return interceptor;
}
```

**Current state:** No JOIN optimization interceptor is configured. The interceptor chain has:
- `PaginationInnerInterceptor` -- for paginated queries
- `OptimisticLockerInnerInterceptor` -- for optimistic locking

### Achievement N+1 Query Fixes

#### Fix 1: `getUserPoints()` N+1 (HIGH priority)

**Location:** `AchievementServiceImpl.java` lines 318-330

**Current code:**
```java
@Override
public UserPointsVO getUserPoints(String userId) {
    List<UserAchievement> userAchievements = userAchievementMapper.findByUserId(userId);
    int totalPoints = 0;
    for (UserAchievement ua : userAchievements) {
        Achievement achievement = achievementMapper.selectById(ua.getAchievementId()); // N+1
        if (achievement != null && achievement.getPoints() != null) {
            totalPoints += achievement.getPoints();
        }
    }
    return new UserPointsVO(totalPoints, userAchievements.size());
}
```

**Solution: Add JOIN-based mapper method (recommended)**

In `AchievementMapper.java`, add:
```java
@Select("SELECT a.* FROM achievements a " +
        "INNER JOIN user_achievements ua ON a.id = ua.achievement_id " +
        "WHERE ua.user_id = #{userId}")
List<Achievement> findByUserIdWithPoints(@Param("userId") String userId);
```

Then refactor `getUserPoints()`:
```java
@Override
public UserPointsVO getUserPoints(String userId) {
    List<Achievement> achievements = achievementMapper.findByUserIdWithPoints(userId);
    int totalPoints = achievements.stream()
            .mapToInt(a -> a.getPoints() != null ? a.getPoints() : 0)
            .sum();
    return new UserPointsVO(totalPoints, achievements.size());
}
```

#### Fix 2: `checkAndAwardAchievements()` inner loop (MEDIUM priority)

**Location:** `AchievementTriggerServiceImpl.java` lines 133-143

**Current code:**
```java
for (Achievement achievement : matchingAchievements) {
    UserAchievement existing = userAchievementMapper.findByUserAndAchievement(
            userId, achievement.getId()); // One query per achievement in loop
    // ...
}
```

**Current state:** This loops over `matchingAchievements` (filtered list, typically small) and calls `findByUserAndAchievement` per achievement. The filter reduces the set before the loop, so this is not a severe N+1. However, it can be batched.

**Solution:** Add batch lookup:
```java
// In UserAchievementMapper
@Select("<script>" +
        "SELECT * FROM user_achievements WHERE user_id = #{userId} " +
        "AND achievement_id IN <foreach item='id' collection='achievementIds' open='(' separator=',' close=')'>${id}</foreach>" +
        "</script>")
List<UserAchievement> findByUserIdAndAchievementIds(
        @Param("userId") String userId,
        @Param("achievementIds") List<String> achievementIds);
```

Then replace the inner loop with a single batch query before the loop.

#### Fix 3: `getUserProgress()` -- Already efficient (LOW priority)

**Location:** `AchievementServiceImpl.java` lines 41-56

This method makes 4 independent queries that run once each (not in a loop). The problem is not N+1 here but the number of round trips. For a production system with network latency, this could be combined but the impact is minor since each query is simple.

### MyBatis-Plus Version Constraints

**Project uses:** MyBatis-Plus 3.5.16

MyBatis-Plus 3.5+ has a `mybatis-plus-join` extension that provides `JoinWrapper` for fluent JOIN queries. However, since:
1. The project already uses `mybatis-plus-jsqlparser` for custom SQL
2. Adding another dependency (`mybatis-plus-join`) introduces complexity
3. The N+1 fixes above require only simple INNER JOINs achievable with `@Select` annotations

**Recommendation:** Do NOT add `mybatis-plus-join`. Use custom `@Select` annotations with explicit JOINs for the specific queries needed.

---

## Database Migration Strategy for Composite Indexes

### Current `user_follows` Indexes

**Migration file:** `db-manager/migrations/V100__follow_schema.sql`

```sql
CREATE TABLE user_follows (
    follower_id VARCHAR(50) NOT NULL,
    following_id VARCHAR(50) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (follower_id, following_id),
    INDEX idx_user_follows_follower (follower_id),
    INDEX idx_user_follows_following (following_id),
    INDEX idx_user_follows_created (created_at),
    ...
);
```

### Missing Indexes for Pagination

**Paginated queries in `FollowMapper.java`:**
```java
@Select("SELECT * FROM user_follows WHERE following_id = #{followingId} ORDER BY created_at DESC LIMIT #{offset}, #{limit}")
List<UserFollow> selectByFollowingIdPaged(...);

@Select("SELECT * FROM user_follows WHERE follower_id = #{followerId} ORDER BY created_at DESC LIMIT #{offset}, #{limit}")
List<UserFollow> selectByFollowerIdPaged(...);
```

**Analysis:**
- `selectByFollowingIdPaged`: Filters on `following_id`, orders by `created_at` DESC, limits. Current `(following_id)` index covers the filter but MySQL must sort using a filesort. A composite index `(following_id, created_at)` enables index-only scan with ordered traversal.
- `selectByFollowerIdPaged`: Same pattern with `follower_id`.

### New Migration File: `V101__add_follow_indexes.sql`

```sql
-- Composite indexes for follow system pagination optimization
-- Supports FollowMapper.selectByFollowingIdPaged() and selectByFollowerIdPaged()

SET FOREIGN_KEY_CHECKS = 0;

-- Covering index for: SELECT * FROM user_follows WHERE following_id = ? ORDER BY created_at DESC LIMIT ?
CREATE INDEX idx_user_follows_following_created ON user_follows (following_id, created_at DESC);

-- Covering index for: SELECT * FROM user_follows WHERE follower_id = ? ORDER BY created_at DESC LIMIT ?
CREATE INDEX idx_user_follows_follower_created ON user_follows (follower_id, created_at DESC);

SET FOREIGN_KEY_CHECKS = 1;
```

### Migration Execution

```bash
cd db-manager
.db-manager/.venv/bin/python -m db_manager.cli migrate
```

### Verification

```bash
docker exec ulticode-mysql mysql -u ulticode -pulticode ulticode -e "SHOW INDEX FROM user_follows;"
```

---

## JaCoCo Maven Plugin Integration

### Current Configuration

**File:** `backend-spring/pom.xml` lines 259-311

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.12</version>
    <configuration>
        <excludes>
            <exclude>**/*Mapper.java</exclude>
            <exclude>**/*Mapper.xml</exclude>
            <exclude>**/entity/*.java</exclude>
            <exclude>**/*DTO.java</exclude>
            <exclude>**/*VO.java</exclude>
            <exclude>**/*BO.java</exclude>
            <exclude>**/*Response.java</exclude>
            <exclude>**/*Request.java</exclude>
            <exclude>**/*Config.java</exclude>
            <exclude>**/*Properties.java</exclude>
            <exclude>**/*Application.java</exclude>
        </excludes>
        <rules>
            <rule>
                <element>BUNDLE</element>
                <limits>
                    <limit>
                        <counter>LINE</counter>
                        <value>COVEREDRATIO</value>
                        <minimum>0.50</minimum>
                    </limit>
                    <limit>
                        <counter>BRANCH</counter>
                        <value>COVEREDRATIO</value>
                        <minimum>0.40</minimum>
                    </limit>
                </limits>
            </rule>
        </rules>
    </configuration>
    <executions>
        <execution>
            <id>prepare-agent</id>
            <phase>initialize</phase>
            <goals><goal>prepare-agent</goal></goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>verify</phase>
            <goals><goal>report</goal></goals>
        </execution>
    </executions>
</plugin>
```

### JaCoCo Integration Points

| Phase | Goal | Bound To | Purpose |
|-------|------|----------|---------|
| `initialize` | `prepare-agent` | `jacoco-agent.jar` | Instruments JVM with coverage tracking |
| `verify` | `report` | `target/site/jacoco/jacoco.xml` | Generates coverage report and enforces thresholds |

### How It Works with Existing Build

1. **Maven test phase** runs with `jacoco-agent.jar` attached via `-javaagent`
2. **JaCoCo data file** (`target/jacoco.exec`) is written during test execution
3. **`verify` phase** reads the data file and enforces `<minimum>` thresholds
4. **If thresholds not met:** Maven build fails with non-zero exit code

### Enforcement Scope

**`<element>BUNDLE</element>`** -- each Maven module is one BUNDLE.

Since `backend-spring` is a single-module Maven project, the BUNDLE is the entire project. This means LINE 50% and BRANCH 40% coverage applies to all non-excluded classes combined.

### Threshold Adjustment Rationale

| Threshold | Current | Recommendation | Reason |
|-----------|---------|----------------|--------|
| LINE | 0.50 | Keep 0.50 for v1.9 | Established baseline from v1.5 |
| BRANCH | 0.40 | Keep 0.40 for v1.9 | Established baseline from v1.5 |

Tightening thresholds (e.g., to 0.60 LINE) should happen after the N+1 optimization PR adds new code paths that need test coverage. Setting a higher threshold now would block the milestone unnecessarily.

---

## Test Execution Strategy for Coverage Measurement

### Standard Maven Build with JaCoCo

```bash
cd backend-spring
./mvnw clean verify
```

This runs:
1. `clean` -- removes previous build artifacts
2. `initialize` phase -- JaCoCo prepare-agent
3. `test` phase -- all JUnit tests with coverage instrumentation
4. `verify` phase -- JaCoCo report generation + threshold enforcement

### Coverage Report Location

```
backend-spring/target/site/jacoco/index.html
backend-spring/target/site/jacoco/jacoco.xml  (machine-readable)
backend-spring/target/site/jacoco/jacoco.csv  (CSV format)
```

### CI/CD Integration

The `verify` phase failing stops the build. In CI:

```bash
./mvnw verify
# Exit code non-zero if JaCoCo thresholds not met
```

### Current Test Suite State

**Achievement tests:** `AchievementServiceTest.java` -- comprehensive unit tests with `@ExtendWith(MockitoExtension.class)`, mocks all mapper dependencies.

**Follow tests:** `FollowServiceImplTest.java` -- exists based on file discovery.

**No integration tests** with real MySQL (Testcontainers is in pom.xml but not actively used for achievement/follow modules).

### Verification Command

```bash
cd backend-spring && ./mvnw test -Dtest=AchievementServiceTest
```

---

## Impact on Existing Services and APIs

### Services Affected

| Service | Method | Change Type | Risk |
|---------|--------|------------|------|
| `AchievementServiceImpl` | `getUserPoints()` | Query optimization | LOW -- changes internal query, same API contract |
| `AchievementTriggerServiceImpl` | `checkAndAwardAchievements()` | Query optimization | LOW -- changes internal query, same API contract |
| `FollowServiceImpl` | `getFollowers()`, `getFollowing()` | Index-only | LOW -- no code change, just better query plans |

### API Contracts Unchanged

All `FollowController`, `AchievementController` endpoints return the same response format. The optimization is entirely at the data access layer.

### Database Migration Impact

**New migration:** `V101__add_follow_indexes.sql`

- **Additive only** -- adds indexes, does not modify table structure
- **MySQL online DDL:** Adding secondary indexes on InnoDB tables uses `INPLACE` algorithm by default in MySQL 8.0+, meaning minimal table locking
- **No downtime required** for the migration
- **Reversible:** Index can be dropped if issues arise

### JaCoCo Build Impact

| Scenario | Result |
|----------|--------|
| Coverage >= thresholds | Build succeeds |
| Coverage < thresholds | Build FAILS with message indicating which counter failed |

The JaCoCo enforcement is binary -- it does not gradual degrade. A single class at 0% coverage can pull down the BUNDLE average below 0.50.

---

## Recommended Phase Structure

Based on research findings:

1. **Phase 1: Achievement N+1 Fix (getUserPoints)** -- Single mapper method + service refactor. Smallest scope, highest impact.
2. **Phase 2: Achievement N+1 Fix (checkAndAwardAchievements)** -- Batch lookup method. Isolated change.
3. **Phase 3: Follow Composite Index Migration** -- New Flyway migration file, no code change. Low risk.
4. **Phase 4: JaCoCo Verification** -- Run `./mvnw verify` and confirm thresholds still pass after changes.

**Phase ordering rationale:**
- Achievement fixes first (highest user-facing latency impact)
- Follow index migration is independent and can run in parallel
- JaCoCo verification is the final gate

**Research flags for phases:**
- Phase 1: JaCoCo thresholds may need temporary adjustment if new code paths reduce coverage
- Phase 3: Verify MySQL version supports online index creation (should be MySQL 8.0+ based on Docker image)

---

## Sources

- MyBatis-Plus 3.5.16 documentation (Context7: `/baomidou/mybatis-plus-doc`)
- Current codebase: `AchievementServiceImpl.java`, `AchievementTriggerServiceImpl.java`, `FollowServiceImpl.java`
- Current config: `MybatisPlusConfig.java`, `pom.xml` (JaCoCo plugin)
- Migration: `V100__follow_schema.sql`
