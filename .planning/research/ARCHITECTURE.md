# Architecture Patterns: v1.5 Technical Debt Remediation

**Domain:** Spring Boot 3.2.5 + MyBatis-Plus + Redis integration
**Researched:** 2026-04-19

## Integration Architecture

### Component Boundaries

| Component | Responsibility | Communicates With |
|-----------|---------------|-------------------|
| RateLimitAspect | Intercepts @RateLimit annotations, enforces limits via Redis | RedissonClient, HttpServletResponse |
| RedisCacheConfig | Configures Spring Cache with Redisson backing | RedissonClient, CacheManager |
| JaCoCoConfig | Configures coverage reporting and thresholds | Maven plugin (build-time only) |
| N+1 Mapper Refactoring | Explicit JOIN queries in XML mappers | MyBatis-Plus QueryWrapper |

### Data Flow

```
Request → Filter Chain → RateLimitAspect (@RateLimit) → Controller → Service → Mapper (JOIN query)
                                    ↓
                              Redisson RRateLimiter (atomic)
```

```
Read Request → @Cacheable → Redis Cache Hit? → Return cached
                    ↓ No
               MyBatis Mapper → Return + Cache result
```

## Pattern 1: Rate Limiting with AOP

### Integration Point

The `@RateLimit` annotation is on methods/classes. The `RateLimitAspect` should run AFTER the Spring Security filter chain (so authenticated user context is available) but BEFORE the controller method executes.

**Spring AOP vs AspectJ:** Use Spring AOP (proxy-based). It integrates cleanly with Spring Security's filter chain and doesn't require AspectJ compiler.

### Recommended Aspect Structure

```java
@Aspect
@Component
@Slf4j
public class RateLimitAspect {

    private final RedissonClient redissonClient;

    // Pointcut: all controllers with @RateLimit
    @Around("@annotation(rateLimit) || @within(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        // 1. Extract key (user ID from SecurityContext or IP)
        // 2. Get or create RRateLimiter
        // 3. tryAcquire() - non-blocking
        // 4. If denied: return 429 with Retry-After header
        // 5. If acquired: proceed with request
    }
}
```

### Why Filter Chain vs AOP?

| Approach | Pros | Cons |
|----------|------|------|
| **AOP (recommended)** | Annotation-driven, flexible per-method config, easy to disable per endpoint | Slight overhead from proxy |
| Filter | Fast, centralized | Less granular, harder to configure per-endpoint |

The existing `@RateLimit` annotation design supports method-level configuration, so AOP is the natural fit.

## Pattern 2: Redis Caching at Service Layer

### Integration Point

Caching at the service layer (not controller) keeps the cache key close to business logic and allows invalidation when domain objects change.

### Recommended Cache Configuration

```java
@Configuration
@EnableCaching
public class RedisCacheConfig {

    @Bean
    public CacheManager cacheManager(RedissonClient redissonClient) {
        Map<String, CacheConfig> configs = new HashMap<>();

        // Problem cache: 5 minute TTL
        configs.put("problems", new CacheConfig(
            Duration.ofMinutes(5), null));

        // User cache: 15 minute TTL
        configs.put("users", new CacheConfig(
            Duration.ofMinutes(15), null));

        // Rankings: 1 minute TTL (frequently changing)
        configs.put("rankings", new CacheConfig(
            Duration.ofMinutes(1), null));

        return new RedissonCacheManager(redissonClient, configs);
    }
}
```

### Service Layer Annotation Pattern

```java
@Service
@Slf4j
public class ProblemServiceImpl implements ProblemService {

    @Cacheable(value = "problems", key = "#id", unless = "#result == null")
    public Optional<Problem> findById(Long id) {
        return Optional.ofNullable(problemMapper.selectById(id));
    }

    @CacheEvict(value = "problems", key = "#result.id")
    public Problem updateProblem(Long id, UpdateProblemDTO dto) {
        // ...
    }

    @CacheEvict(value = "problems", allEntries = true)
    public void clearProblemCache() {
        // Bulk invalidation
    }
}
```

## Pattern 3: MyBatis-Plus N+1 Fixes

### The Problem

MyBatis-Plus does NOT support JPA-style entity graphs. When you fetch a list of entities and access a lazy-loaded collection, MyBatis issues a separate query per entity.

### Solution: Explicit JOIN in XML Mapper

```xml
<!-- ProblemMapper.xml -->
<resultMap id="ProblemWithTagsMap" type="com.ulticode.modules.problem.entity.Problem">
    <id property="id" column="id"/>
    <result property="title" column="title"/>
    <!-- ... other fields ... -->
    <!-- Manual association for tags (avoids N+1) -->
    <collection property="tags" column="id"
        select="selectTagsByProblemId"/>
</resultMap>

<!-- Instead, use JOIN for batch loading -->
<select id="selectProblemsWithTags" resultMap="ProblemWithTagsMap">
    SELECT p.*, GROUP_CONCAT(t.label) as tag_labels
    FROM problems p
    LEFT JOIN problem_tag_relations ptr ON p.id = ptr.problem_id
    LEFT JOIN problem_tags t ON ptr.tag_id = t.id
    WHERE p.is_published = 1
    GROUP BY p.id
</select>
```

### Batch Fetch Alternative (for complex scenarios)

```java
// In service: batch load related entities
public List<ProblemVO> listProblemsWithTags(ProblemQueryDTO query) {
    // Step 1: Fetch problem IDs
    List<Problem> problems = problemMapper.selectList(wrapper);

    // Step 2: Batch fetch tags for all problem IDs
    if (!problems.isEmpty()) {
        List<Long> ids = problems.stream().map(Problem::getId).collect(Collectors.toList());
        List<Tag> allTags = tagMapper.selectByProblemIds(ids);  // WHERE problem_id IN (ids)

        // Step 3: Group tags by problem ID
        Map<Long, List<Tag>> tagsByProblemId = allTags.stream()
            .collect(Collectors.groupingBy(Tag::getProblemId));

        // Step 4: Attach to problem VOs
        // ...
    }
}
```

### MyBatis-Plus QueryWrapper JOIN Pattern

```java
public List<ProblemVO> listProblemsJoinTags(ProblemQueryDTO query) {
    LambdaQueryWrapper<Problem> wrapper = new LambdaQueryWrapper<>();

    // Use MyBatis-Plus's join capability
    return problemMapper.selectMaps(wrapper)
        .stream()
        .map(this::convertToVO)
        .collect(Collectors.toList());
}
```

## Pattern 4: JaCoCo Maven Configuration

### Integration Point

Build phase - JaCoCo is a Maven plugin that instruments bytecode during the `prepare-agent` phase and generates reports during `report` phase.

### Recommended pom.xml Configuration

```xml
<build>
    <finalName>app</finalName>
    <plugins>
        <!-- Existing plugins... -->

        <!-- JaCoCo Coverage -->
        <plugin>
            <groupId>org.jacoco</groupId>
            <artifactId>jacoco-maven-plugin</artifactId>
            <version>0.8.11</version>
            <executions>
                <!-- Prepare agent for instrumentation -->
                <execution>
                    <id>prepare-agent</id>
                    <goals>
                        <goal>prepare-agent</goal>
                    </goals>
                </execution>
                <!-- Check coverage thresholds -->
                <execution>
                    <id>check</id>
                    <goals>
                        <goal>check</goal>
                    </goals>
                    <configuration>
                        <rules>
                            <rule>
                                <element>BUNDLE</element>
                                <limits>
                                    <limit>
                                        <counter>LINE</counter>
                                        <value>COVEREDRATIO</value>
                                        <minimum>0.50</minimum>  <!-- 50% initial -->
                                    </limit>
                                    <limit>
                                        <counter>BRANCH</counter>
                                        <value>COVEREDRATIO</value>
                                        <minimum>0.40</minimum>  <!-- 40% initial -->
                                    </limit>
                                </limits>
                            </rule>
                        </rules>
                        <excludes>
                            <!-- Exclude configuration and value objects -->
                            <exclude>**/*Application.class</exclude>
                            <exclude>**/*Config.class</exclude>
                            <exclude>**/*Exception.class</exclude>
                            <exclude>**/dto/**</exclude>
                            <exclude>**/vo/**</exclude>
                            <exclude>**/entity/**</exclude>
                            <exclude>**/mapper/**</exclude>
                        </excludes>
                    </configuration>
                </execution>
                <!-- Generate HTML report -->
                <execution>
                    <id>report</id>
                    <goals>
                        <goal>report</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

### Exclusions Rationale

| Pattern | Why Excluded |
|---------|--------------|
| `*Application.class` | Entry point, no business logic |
| `*Config.class` | Spring configuration, not testable |
| `*Exception.class` | Simple error wrappers |
| `dto/` `vo/` `entity/` | Data transfer objects - getters/setters don't need coverage |
| `mapper/` | MyBatis generated implementations |

## Anti-Patterns to Avoid

### Anti-Pattern 1: Caching at Controller Layer
**Why bad:** Cache keys become URL-centric, loses domain meaning. Invalidating becomes harder.

**Instead:** Cache at service layer where domain objects are managed.

### Anti-Pattern 2: Using @Cacheable on Methods Returning Entity Objects
**Why bad:** Entity objects may be mutated later, corrupting cache.

**Instead:** Return DTOs/VOes from cached methods, or use defensive copies.

### Anti-Pattern 3: N+1 with MyBatis-Plus Wrapper Alone
**Why bad:** MyBatis-Plus's `selectList` doesn't support JPA-style entity graphs. Accessing relations triggers individual queries.

**Instead:** Write explicit JOINs in XML mappers or use batch fetch.

### Anti-Pattern 4: Enforcing 80% Coverage Cold Turkey
**Why bad:** Existing codebase likely below threshold. Build will fail immediately, blocking all work.

**Instead:** Start at 50%, increment gradually (50% -> 60% -> 70% -> 80%).

## Scalability Considerations

| Concern | At 100 users | At 10K users | At 1M users |
|---------|--------------|--------------|-------------|
| Rate Limiting | Local in-memory sufficient | Redis-backed required | Redis cluster needed |
| Cache | Single Redis instance | Redis with replicas | Redis cluster + local cache |
| N+1 Queries | Acceptable | Need JOIN optimization | Need query result limits |
| Coverage | 50% threshold | 60% threshold | 80% threshold |

## Build Order Considerations

1. **Rate Limiting** must come before Caching (provides infrastructure)
2. **JaCoCo** should be added early (establishes baseline before adding code)
3. **N+1 Fixes** require mapper XML changes - coordinate with feature work
4. **Caching** should be added after rate limiting to protect cache layer from abuse

## Sources

- Context7: Spring Boot 3.2 AOP documentation
- Context7: Spring Cache with Redisson
- Context7: MyBatis-Plus query optimization
- Official JaCoCo Maven plugin documentation
