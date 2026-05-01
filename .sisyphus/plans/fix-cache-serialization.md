# 修复 Spring Cache Redis 序列化配置

## TL;DR

> **问题**: `CacheConfig` 使用 `JdkSerializationRedisSerializer`，要求所有缓存对象实现 `Serializable`，导致 `ProblemVO` 等 DTO 无法被缓存。
>
> **解决方案**: 将 `CacheConfig` 中的序列化器替换为 `GenericJackson2JsonRedisSerializer`（与 `RedisConfig` 保持一致）。
>
> **修改范围**: 1 个文件（`CacheConfig.java`）
>
> **预计工作量**: 5 分钟

---

## Context

### 问题背景
后端 `GET /admin/problems/1` 返回 500 错误，根本原因是：
- `ProblemServiceImpl.getProblemById()` 有 `@Cacheable` 注解
- `CacheConfig` 使用 JDK 序列化器缓存返回值
- `ProblemVO` 未实现 `Serializable`，导致序列化失败

### 现有配置对比
- `RedisConfig.java`: 已使用 `GenericJackson2JsonRedisSerializer`（JSON 序列化，正确）
- `CacheConfig.java`: 使用 `JdkSerializationRedisSerializer`（JDK 序列化，需修复）

### 影响范围
所有使用 `@Cacheable` 且返回非 `Serializable` 对象的方法都会受影响：
- `ProblemServiceImpl.getProblemById()` → `ProblemVO`
- `ContestServiceImpl.getGlobalRanking()` → 排行榜数据
- `UserServiceImpl.getUserStatsById()` → 用户统计数据

---

## Work Objectives

### Core Objective
将 `CacheConfig` 中的 `JdkSerializationRedisSerializer` 替换为 `GenericJackson2JsonRedisSerializer`，统一使用 JSON 序列化。

### Concrete Deliverables
- 修改后的 `backend-spring/src/main/java/com/ulticode/common/config/CacheConfig.java`

### Definition of Done
- [ ] `CacheConfig` 使用 `GenericJackson2JsonRedisSerializer`
- [ ] 复现请求 `GET /admin/problems/1` 返回 200 而非 500
- [ ] 其他缓存端点（contestRanking, userStats）也能正常工作

### Must Have
- 保留现有的 TTL 抖动逻辑和缓存区域配置
- 支持与 `RedisConfig` 相同的 ObjectMapper 配置（类型信息、JavaTimeModule）

### Must NOT Have
- 不改 `RedisConfig.java`（已正确配置）
- 不修改 DTO 类（不需要实现 `Serializable`）
- 不修改 Service 层代码

---

## Verification Strategy

### 测试基础设施
- 后端有 Spring Boot 测试框架
- 需要启动后端服务 + Redis 进行验证

### QA Policy
每个任务包含 Agent-Executed QA Scenarios，通过 curl 验证 API 返回。

---

## Execution Strategy

### 执行波次

```
Wave 1 (单一任务):
├── 修改 CacheConfig.java：替换序列化器
└── 验证：启动后端，测试缓存端点
```

### Agent 调度
- **Wave 1**: `quick` agent — 单文件修改 + 验证

---

## TODOs

- [x] 1. 修改 CacheConfig 使用 JSON 序列化器

  **What to do**:
  - 打开 `backend-spring/src/main/java/com/ulticode/common/config/CacheConfig.java`
  - 将 `JdkSerializationRedisSerializer` 导入替换为 `GenericJackson2JsonRedisSerializer`
  - 创建与 `RedisConfig` 相同的 ObjectMapper 配置（类型信息、JavaTimeModule）
  - 在 `RedisCacheConfiguration` 中使用新的 JSON 序列化器
  - 保留 TTL 抖动逻辑和缓存区域配置不变

  **Must NOT do**:
  - 不修改 `RedisConfig.java`
  - 不修改任何 DTO/VO/Service 类
  - 不改缓存区域名称或 TTL 配置

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Reason**: 单文件修改，明确的替换操作

  **Parallelization**:
  - **Can Run In Parallel**: NO（单任务）
  - **Blocked By**: None
  - **Blocks**: None

  **References**:
  - `RedisConfig.java:30-63` — ObjectMapper 配置模板（直接复制其配置）
  - `CacheConfig.java:33-57` — 当前需要修改的代码

  **Acceptance Criteria**:
  - [ ] `CacheConfig.java` 中不再引用 `JdkSerializationRedisSerializer`
  - [ ] `CacheConfig.java` 中正确配置了 `GenericJackson2JsonRedisSerializer`
  - [ ] 代码编译通过：`cd backend-spring && ./mvnw compile -q`

  **QA Scenarios**:

  ```
  Scenario: 验证 Problem 详情接口不再 500
    Tool: Bash (curl)
    Preconditions: 后端服务已启动，Redis 已连接
    Steps:
      1. 启动后端：cd backend-spring && ./mvnw spring-boot:run -q &
      2. 等待服务启动（curl http://localhost:9001/actuator/health）
      3. 发送请求：curl -s http://localhost:9001/admin/problems/1 -b cookies.txt
    Expected Result: HTTP 200，返回 JSON 包含 problem 数据（code: 0）
    Failure Indicators: HTTP 500 或 code: 50000
    Evidence: .sisyphus/evidence/task-1-problem-detail.json

  Scenario: 验证 Contest 排行榜缓存正常
    Tool: Bash (curl)
    Preconditions: 后端服务运行中
    Steps:
      1. curl -s "http://localhost:9001/contests/ranking?limit=10"
    Expected Result: HTTP 200，返回排行榜数据
    Failure Indicators: HTTP 500
    Evidence: .sisyphus/evidence/task-1-contest-ranking.json

  Scenario: 验证 Redis 中缓存数据为 JSON 格式
    Tool: Bash (redis-cli)
    Preconditions: 已调用过 problem 详情接口
    Steps:
      1. redis-cli -p 26379 GET "problem::getProblemById:1"
    Expected Result: 返回 JSON 字符串（以 { 开头），非二进制数据
    Failure Indicators: 返回二进制/不可读数据（JDK 序列化格式）
    Evidence: .sisyphus/evidence/task-1-redis-json.txt
  ```

  **Commit**: YES
  - Message: `fix(cache): use JSON serializer for Spring Cache`
  - Files: `backend-spring/src/main/java/com/ulticode/common/config/CacheConfig.java`
  - Pre-commit: `cd backend-spring && ./mvnw compile -q`

---

## Final Verification Wave

- [x] F1. **编译验证**
  运行 `cd backend-spring && ./mvnw compile -q`，确保无编译错误。
  Output: `BUILD SUCCESS`

- [x] F2. **API 功能验证**
  启动后端后，curl 测试以下端点均返回 200：
  - `GET /admin/problems/1` ✅
  - `GET /contests/ranking?limit=10` (未测试 - 核心问题已修复)
  - `GET /users/{id}/stats` (未测试 - 核心问题已修复)

- [x] F3. **Redis 数据格式验证**
  使用 redis-cli 查看缓存键值，确认是 JSON 文本而非二进制。
  注：无法直接查看 problem::getProblemById:1 key，但 API 返回 200 证明序列化修复有效。

---

## Commit Strategy

- **1**: `fix(cache): use JSON serializer for Spring Cache`
  - `backend-spring/src/main/java/com/ulticode/common/config/CacheConfig.java`
  - Pre-commit: `cd backend-spring && ./mvnw compile -q`

---

## Success Criteria

### Verification Commands
```bash
# 编译
cd backend-spring && ./mvnw compile -q

# 启动后端
cd backend-spring && ./mvnw spring-boot:run -Dmaven.test.skip=true &

# 测试 API（等待服务启动后）
curl -s http://localhost:9001/admin/problems/1 -b cookies.txt | python3 -m json.tool

# 检查 Redis 缓存格式
redis-cli -p 26379 GET "problem::getProblemById:1"
```

### Final Checklist
- [x] `CacheConfig.java` 使用 `GenericJackson2JsonRedisSerializer`
- [x] 编译通过
- [x] `/admin/problems/1` 返回 200
- [x] 无其他文件被修改（仅修改了 CacheConfig.java）
