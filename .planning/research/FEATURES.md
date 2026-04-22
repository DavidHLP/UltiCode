# Feature Landscape: v1.9 Performance & Quality

**Domain:** Performance optimization and quality enforcement for Spring Boot + MyBatis-Plus backend
**Researched:** 2026-04-22
**Overall confidence:** HIGH

## Executive Summary

This milestone addresses three performance/quality gaps: (1) an N+1 query in AchievementService.getUserPoints(), (2) missing composite indexes on the user_follows table, and (3) JaCoCo coverage thresholds configured but not enforced in the build lifecycle. All three have clear, low-risk solutions using standard MyBatis-Plus and MySQL patterns.

---

## 1. N+1 Query Optimization (AchievementService)

### Problem: getUserPoints() method (lines 318-330)

```java
public UserPointsVO getUserPoints(String userId) {
    List<UserAchievement> userAchievements = userAchievementMapper.findByUserId(userId);
    int totalPoints = 0;
    for (UserAchievement ua : userAchievements) {
        Achievement achievement = achievementMapper.selectById(ua.getAchievementId()); // N+1!
        if (achievement != null && achievement.getPoints() != null) {
            totalPoints += achievement.getPoints();
        }
    }
    return new UserPointsVO(totalPoints, userAchievements.size());
}
```

**Root cause:** Iterates over userAchievements, issuing one SELECT per achievement.

**Solution options:**

| Approach | How | Tradeoff |
|----------|-----|----------|
| JOIN FETCH via XML | Custom resultMap with nested association | Most flexible, requires XML |
| MyBatis-Plus-Join | `MPJBaseMapper` with `selectMS` | Additional dependency |
| Batch fetch (IN clause) | Collect IDs, single query | Simple, 2 queries total |
| @OneToMany lazy + batch size | Entity lazy loading with global config | Transparent but complex |

**Recommended:** Batch fetch (IN clause) - simplest and most maintainable:

```java
// In AchievementServiceImpl.getUserPoints():
List<String> achievementIds = userAchievements.stream()
    .map(UserAchievement::getAchievementId)
    .toList();
if (achievementIds.isEmpty()) {
    return new UserPointsVO(0, 0);
}
Map<String, Achievement> achievementMap = achievementMapper.selectBatchIds(achievementIds)
    .stream().collect(Collectors.toMap(Achievement::getId, a -> a));
```

### Verification Approach

After fix, measure:
- Query count: should drop from N+1 to 2 (userAchievements + batch select)
- Response time: measurable improvement for users with many achievements

---

## 2. Composite Index Design (Follow System)

### Current State

The `user_follows` table has a composite primary key `(follower_id, following_id)` but the entity has no explicit index definitions. The FollowMapper uses these queries:

| Query | SQL Pattern | Needs Index |
|-------|------------|-------------|
| getFollowers (paginated) | `WHERE following_id = ? ORDER BY created_at DESC LIMIT ?` | `(following_id, created_at)` |
| getFollowing (paginated) | `WHERE follower_id = ? ORDER BY created_at DESC LIMIT ?` | `(follower_id, created_at)` |
| count followers | `WHERE following_id = ?` | covered by above |
| count following | `WHERE follower_id = ?` | covered by above |
| exists check | `WHERE follower_id = ? AND following_id = ?` | covered by PK |

### Recommended Indexes

**Migration file needed:**

```sql
-- Flyway migration for follow system indexes
CREATE INDEX idx_user_follows_following_created ON user_follows(following_id, created_at DESC);
CREATE INDEX idx_user_follows_follower_created ON user_follows(follower_id, created_at DESC);
```

### Covering Index Consideration

For paginated queries, `(following_id, created_at)` is a covering index - MySQL can satisfy `ORDER BY created_at DESC` entirely from the index without touching table rows, reducing I/O.

### Leftmost Prefix Rule

The composite primary key `(follower_id, following_id)` satisfies equality lookups on `follower_id` alone, but does NOT help with `ORDER BY created_at` or range scans on `created_at`. This is why separate indexes are needed.

### Verification Approach

After indexes added:
- Run `EXPLAIN` on paginated follow queries - should show `Using index` (covering) not `Using index condition`
- Response time improvement on follower/following lists

---

## 3. JaCoCo Coverage Enforcement

### Current Configuration (pom.xml lines 259-311)

JaCoCo is configured with thresholds but the `check` goal is NOT bound to the verify lifecycle:

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.12</version>
    <configuration>
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
        <!-- MISSING: check goal binding! -->
    </executions>
</plugin>
```

### Problem

Only `report` goal runs. Coverage is generated but never checked - builds pass regardless of coverage.

### Fix Required

Add check goal execution:

```xml
<execution>
    <id>check</id>
    <phase>verify</phase>
    <goals>
        <goal>check</goal>
    </goals>
</execution>
```

JaCoCo will automatically read the `<rules>` configuration from the plugin's `<configuration>` block and fail the build if coverage is below thresholds.

### CI/CD Integration

The `check` goal binds to the `verify` phase, which runs:
- Locally: `./mvnw verify` or `./mvnw test`
- In CI: automatically when using the `verify` or `install` lifecycle

**No additional CI configuration needed** - the Maven plugin handles enforcement.

### Important Note on forkCount

JaCoCo documentation states: "When using maven-surefire-plugin or maven-failsafe-plugin you must not use a forkCount of 0 or set forkMode to never as this would prevent the execution of the tests with the javaagent set." The current surefire configuration (implicit default) is compatible.

---

## Feature Dependencies

```
Achievement N+1 fix
  └─> AchievementServiceImpl.getUserPoints() modification
  └─> No schema change needed

Follow composite indexes
  └─> Flyway migration (V{n}__add_follow_indexes.sql)
  └─> No code change

JaCoCo enforcement
  └─> pom.xml check goal binding
  └─> No CI changes
```

---

## MVP Recommendation

**Priority order:**
1. **JaCoCo enforcement** - Minimal change (add 1 execution block), immediate quality feedback
2. **Follow composite indexes** - Flyway migration, validates with EXPLAIN
3. **Achievement N+1 fix** - Service layer change, validates with query count logging

All three are independent and can ship in any order within the milestone.

---

## Confidence Assessment

| Area | Confidence | Reason |
|------|------------|--------|
| N+1 solution | HIGH | Standard MyBatis-Plus pattern, no new dependencies |
| Composite indexes | HIGH | Standard MySQL, leftmost prefix rule well understood |
| JaCoCo enforcement | HIGH | Official JaCoCo docs confirm check goal behavior |
| Query verification | MEDIUM | Will need EXPLAIN and runtime measurement |

## Sources

- MyBatis-Plus documentation (Context7: `/baomidou/mybatis-plus-doc`)
- MySQL 8.4 indexes documentation (official)
- JaCoCo 0.8.12 Maven plugin documentation (official: `jacoco.org/jacoco/trunk/doc/check-mojo.html`)
