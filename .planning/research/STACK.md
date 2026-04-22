# Technology Stack Additions: v1.9 Performance & Quality

**Project:** UltiCode
**Researched:** 2026-04-22
**Confidence:** HIGH (verified against existing codebase and official docs)

## Executive Summary

v1.9 requires only code changes, not new dependencies. MyBatis-Plus 3.5.16 already includes `selectBatchIds()` for batch fetching. JaCoCo 0.8.12 is already in pom.xml. The missing piece is the `jacoco:check` goal bound to the `verify` phase -- currently only `prepare-agent` and `report` are configured.

## MyBatis-Plus N+1 Resolution

### The Problem

In `AchievementServiceImpl.getUserPoints()` (line 318-329), each `UserAchievement` triggers a separate `achievementMapper.selectById()` call:

```java
for (UserAchievement ua : userAchievements) {
    Achievement achievement = achievementMapper.selectById(ua.getAchievementId()); // N+1!
}
```

### The Fix: selectBatchIds()

MyBatis-Plus provides `selectBatchIds()` which translates to `SELECT * FROM table WHERE id IN (?, ?, ...)`. This is already available via `BaseMapper` -- no new dependencies.

**No MyBatis-Plus version change needed.** The `mybatis-plus-spring-boot3-starter` 3.5.16 already includes this.

### Code Pattern

```java
// BEFORE (N+1)
for (UserAchievement ua : userAchievements) {
    Achievement achievement = achievementMapper.selectById(ua.getAchievementId());
    if (achievement != null && achievement.getPoints() != null) {
        totalPoints += achievement.getPoints();
    }
}

// AFTER (batch fetch)
List<String> achievementIds = userAchievements.stream()
    .map(UserAchievement::getAchievementId)
    .toList();

Map<String, Achievement> achievementMap = achievementMapper.selectBatchIds(achievementIds)
    .stream()
    .collect(Collectors.toMap(Achievement::getId, a -> a));

for (UserAchievement ua : userAchievements) {
    Achievement achievement = achievementMap.get(ua.getAchievementId());
    if (achievement != null && achievement.getPoints() != null) {
        totalPoints += achievement.getPoints();
    }
}
```

### Integration Points

- `AchievementServiceImpl.getUserPoints()` -- primary target
- No entity changes needed (UserAchievement entity already correct)
- No mapper interface changes needed (selectBatchIds is inherited from BaseMapper)
- No new dependencies

## MySQL Composite Index for Follow System

### Current State (V100__follow_schema.sql)

```sql
CREATE TABLE user_follows (
    follower_id VARCHAR(50) NOT NULL,
    following_id VARCHAR(50) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (follower_id, following_id),  -- composite PK
    INDEX idx_user_follows_follower (follower_id),
    INDEX idx_user_follows_following (following_id),
    INDEX idx_user_follows_created (created_at),
    ...
);
```

### Analysis

The existing indexes are suboptimal for the `getFollowers()` and `getFollowing()` paginated queries:

```java
// FollowMapper.java
@Select("SELECT * FROM user_follows WHERE following_id = #{followingId} ORDER BY created_at DESC LIMIT #{offset}, #{limit}")
List<UserFollow> selectByFollowingIdPaged(...);

@Select("SELECT * FROM user_follows WHERE follower_id = #{followerId} ORDER BY created_at DESC LIMIT #{offset}, #{limit}")
List<UserFollow> selectByFollowerIdPaged(...);
```

These queries filter by `following_id` or `follower_id` AND sort by `created_at`. The current single-column indexes can satisfy the WHERE but the ORDER BY requires a filesort.

### Recommended Composite Indexes

Two new composite indexes cover the paginated queries:

```sql
-- For getFollowers: WHERE following_id = ? ORDER BY created_at DESC
CREATE INDEX idx_user_follows_following_created ON user_follows (following_id, created_at DESC);

-- For getFollowing: WHERE follower_id = ? ORDER BY created_at DESC
CREATE INDEX idx_user_follows_follower_created ON user_follows (follower_id, created_at DESC);
```

### Migration File Naming

Use Flyway V101 or later (after V100__follow_schema.sql):

```sql
-- V101__follow_composite_indexes.sql
ALTER TABLE user_follows
    ADD INDEX idx_user_follows_following_created (following_id, created_at DESC),
    ADD INDEX idx_user_follows_follower_created (follower_id, created_at DESC);
```

**Why DESC?** MySQL InnoDB indexes store in ascending order. For `ORDER BY created_at DESC`, MySQL can scan the index in reverse. A composite index `(following_id, created_at)` with `ORDER BY created_at DESC` is still efficient because MySQL's optimizer can read it backward.

### No New Dependencies

MySQL 8.x supports descending indexes explicitly (`created_at DESC`), but the standard ascending index also works for reverse scans. No driver or library changes needed.

## JaCoCo Enforcement Configuration

### Current State

JaCoCo 0.8.12 is already configured in pom.xml:

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.12</version>
    <configuration>
        <!-- excludes only -->
    </configuration>
    <executions>
        <execution>id="prepare-agent" phase="initialize"</execution>
        <execution>id="report" phase="verify"</execution>
    </executions>
</plugin>
```

**Problem:** The `<rules>` block with LINE/BRANCH limits is inside `<configuration>` but there is no `check` goal execution. The limits only apply to report generation, not enforcement.

### Required Change

Add a `check` goal execution bound to `verify` phase:

```xml
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
    <execution>
        <id>check</id>
        <phase>verify</phase>
        <goals><goal>check</goal></goals>
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
    </execution>
</executions>
```

### Key Points

- `haltOnFailure` defaults to `true` -- build fails when thresholds not met
- Move the `<rules>` block into the `check` execution's `<configuration>` (currently it is in the outer `<configuration>` which only applies to report)
- Keep existing thresholds (LINE 50%, BRANCH 40%) as floor, not ceiling
- No version change needed (0.8.12 supports all required features)

## Stack Delta Summary

| Area | Current State | Change Required | Confidence |
|------|--------------|-----------------|------------|
| MyBatis-Plus batch fetch | `selectBatchIds()` available via BaseMapper | Code change only (no new dep) | HIGH |
| Achievement N+1 | `for` loop with `selectById()` | Refactor to batch fetch pattern | HIGH |
| Follow indexes | Single-column idx on follower_id/following_id | Add composite indexes via migration | HIGH |
| JaCoCo enforcement | `jacoco-maven-plugin` 0.8.12 present but not enforcing | Add `<execution id="check">` with `<goal>check</goal>` | HIGH |

## No New Dependencies Required

All three features (batch fetch, composite indexes, JaCoCo enforcement) can be implemented with:
- MyBatis-Plus 3.5.16 (already in pom.xml)
- MySQL 8.x (already in use)
- JaCoCo 0.8.12 (already in pom.xml)

## Sources

- [MyBatis-Plus selectBatchIds()](https://github.com/baomidou/mybatis-plus-doc/blob/master/src/content/docs/guides/data-interface.mdx)
- [JaCoCo check goal documentation](https://www.jacoco.org/jacoco/trunk/doc/check-mojo.html)
