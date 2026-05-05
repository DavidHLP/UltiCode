# 比赛相关 API 修复计划

## TL;DR

> **问题**: `/rankings/global` 返回 404，`/contest/user/history` 返回 500，且比赛相关数据不完整。
>
> **根因**:
> 1. 前端调用 `/rankings/global`，后端端点是 `/contest/global-ranking` → 路径不匹配
> 2. `Long.parseLong("user-jiangly")` → `NumberFormatException` → 500 错误
> 3. `ContestRankingVO` 缺少前端期望的字段：`country`, `maxRating`, `ratingTitle`, `maxRatingTitle`, `contestsAttended`, `badge`
>
> **修复方案**:
> 1. 修改 `ContestRankingVO.userId` 类型：`Long` → `String`
> 2. 移除 3 处 `Long.parseLong()` 调用
> 3. 在 `ContestController` 添加 `/rankings/global` 端点（支持分页）
> 4. 补充 `ContestRankingVO` 缺失字段并填充数据
>
> **影响文件**: 5 个后端文件 + 0 个前端文件（后端兼容前端现有调用）
> **预计工作量**: 中等（30-45 分钟）
> **可并行度**: 高（Wave 1 和 Wave 2 可并行）

---

## 问题诊断

### 问题 1: `/rankings/global` 404

**前端调用** (`console/src/api/contest.ts:309`):
```typescript
apiGet<PaginatedResult<GlobalRankingEntry>>("/rankings/global", { params: { page, limit, country } })
```

**后端端点** (`ContestController.java:236`):
```java
@GetMapping("/global-ranking")  // 路径是 /contest/global-ranking
public Result<List<ContestRankingVO>> getGlobalRanking(@RequestParam Integer limit)
```

**不匹配点**:
| 维度 | 前端期望 | 后端提供 |
|------|----------|----------|
| **路径** | `/rankings/global` | `/contest/global-ranking` |
| **分页** | `page` + `limit` | 仅 `limit` |
| **响应** | `PaginatedResult<GlobalRankingEntry>` | `List<ContestRankingVO>` |
| **字段** | `country`, `maxRating`, `ratingTitle` 等 | 缺失 |

### 问题 2: `/contest/user/history` 500

**堆栈跟踪**:
```
RankingServiceImpl.java:106 → Long.parseLong(participant.getUserId())
RankingServiceImpl.java:124 → Long.parseLong(participant.userId())
ContestServiceImpl.java:283 → Long.parseLong(ranking.getUserId())
```

**根因**: `userId` 在数据库和实体中是 `String`（如 `"user-jiangly"`），但 `ContestRankingVO.userId` 是 `Long`，代码试图将字符串解析为 Long → `NumberFormatException`

**涉及数据**:
```sql
-- 种子数据中的 user_id 是字符串格式
INSERT INTO contest_participants VALUES ('cp-p-170-1', 'contest-biweekly-170', 'user-jiangly', ...);
```

### 问题 3: 响应字段缺失

前端 `GlobalRankingEntry` 期望的字段 vs 后端 `ContestRankingVO` 实际字段:

| 字段 | 前端需要 | 后端有 |
|------|----------|--------|
| `rank` | ✅ | ✅ |
| `userId` | ✅ | ✅ (但类型是 Long) |
| `username` | ✅ | ✅ |
| `avatar` | ✅ | ✅ |
| `country` | ✅ | ❌ **缺失** |
| `rating` | ✅ | ✅ (映射为 score) |
| `maxRating` | ✅ | ❌ **缺失** |
| `ratingTitle` | ✅ | ❌ **缺失** |
| `maxRatingTitle` | ✅ | ❌ **缺失** |
| `contestsAttended` | ✅ | ❌ **缺失** |
| `badge` | ✅ | ❌ **缺失** |

---

## 修复策略

### Wave 1: 核心类型修复（无依赖，可立即执行）

**任务 1.1**: 修改 `ContestRankingVO.userId` 类型
- 文件: `backend-spring/.../dto/ContestRankingVO.java`
- 修改: `private Long userId;` → `private String userId;`

**任务 1.2**: 修复 `RankingServiceImpl` 中的 `Long.parseLong()`
- 文件: `backend-spring/.../service/impl/RankingServiceImpl.java`
- 修改:
  - 第 106 行: `vo.setUserId(Long.parseLong(participant.getUserId()));` → `vo.setUserId(participant.getUserId());`
  - 第 124 行: `vo.setUserId(Long.parseLong(participant.userId()));` → `vo.setUserId(participant.userId());`

**任务 1.3**: 修复 `ContestServiceImpl` 中的 `Long.parseLong()`
- 文件: `backend-spring/.../service/impl/ContestServiceImpl.java`
- 修改:
  - 第 283 行: `vo.setUserId(Long.parseLong(ranking.getUserId()));` → `vo.setUserId(ranking.getUserId());`

### Wave 2: 功能增强（依赖 Wave 1）

**任务 2.1**: 补充 `ContestRankingVO` 缺失字段
- 文件: `backend-spring/.../dto/ContestRankingVO.java`
- 添加字段:
  - `private String country;`
  - `private Integer maxRating;`
  - `private String ratingTitle;`
  - `private String maxRatingTitle;`
  - `private Integer contestsAttended;`
  - `private String badge;`

**任务 2.2**: 修改 `ContestServiceImpl.toRankingVO(GlobalRanking)` 填充新字段
- 文件: `backend-spring/.../service/impl/ContestServiceImpl.java`
- 修改 `toRankingVO(GlobalRanking)` 方法:
  - 设置 `country`, `maxRating`, `ratingTitle`, `maxRatingTitle`, `contestsAttended`, `badge`

**任务 2.3**: 添加 `/rankings/global` 分页端点
- 文件: `backend-spring/.../controller/ContestController.java`
- 添加新方法:
  ```java
  @GetMapping("/rankings/global")
  public Result<PageResult<ContestRankingVO>> getGlobalRankingsPaginated(
          @RequestParam(required = false, defaultValue = "1") Integer page,
          @RequestParam(required = false, defaultValue = "50") Integer limit) {
      // 调用 contestService.getGlobalRanking() 并包装为分页结果
  }
  ```

**任务 2.4**: 在 `ContestService` 接口和实现中添加分页查询方法
- 文件: `backend-spring/.../service/ContestService.java` 和 `ContestServiceImpl.java`
- 添加: `PageResult<ContestRankingVO> getGlobalRankingsPaginated(Integer page, Integer limit);`

---

## TODOs

- [x] 1. 修复 `ContestRankingVO.userId` 类型（Long → String）

  **What to do**:
  - 修改 `ContestRankingVO.java` 第 39 行
  - 将 `private Long userId;` 改为 `private String userId;`

  **Must NOT do**:
  - 不要修改其他字段的类型
  - 不要添加或删除其他字段（在 Wave 2 中处理）

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: []
    - 这是一个简单的类型修改，不需要特殊技能

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1（与任务 1.2、1.3 并行）
  - **Blocks**: 任务 2.1（需要 userId 已经是 String 才能正确填充）
  - **Blocked By**: 无

  **References**:
  - `ContestRankingVO.java:39` - userId 字段定义
  - `RankingServiceImpl.java:106` - 使用 userId 的位置
  - `RankingServiceImpl.java:124` - 使用 userId 的位置
  - `ContestServiceImpl.java:283` - 使用 userId 的位置

  **Acceptance Criteria**:
  - [ ] `ContestRankingVO.userId` 类型为 `String`
  - [ ] 编译通过：`./mvnw compile -pl backend-spring`

  **QA Scenarios**:
  ```
  Scenario: 编译验证
    Tool: Bash
    Steps:
      1. cd backend-spring && ./mvnw compile -DskipTests
    Expected Result: BUILD SUCCESS
  ```

  **Commit**: YES
  - Message: `fix(contest): change ContestRankingVO.userId from Long to String`
  - Files: `backend-spring/.../dto/ContestRankingVO.java`

---

- [x] 2. 修复 `RankingServiceImpl` 中的 `Long.parseLong()`

  **What to do**:
  - 修改 `RankingServiceImpl.java`
  - 第 106 行: `vo.setUserId(Long.parseLong(participant.getUserId()));` → `vo.setUserId(participant.getUserId());`
  - 第 124 行: `vo.setUserId(Long.parseLong(participant.userId()));` → `vo.setUserId(participant.userId());`

  **Must NOT do**:
  - 不要修改其他逻辑
  - 不要修改方法签名

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1（与任务 1.1、1.3 并行）
  - **Blocks**: 无
  - **Blocked By**: 无

  **References**:
  - `RankingServiceImpl.java:100-112` - `toRankingVO(ContestParticipant)` 方法
  - `RankingServiceImpl.java:117-133` - `toRankingVO(ContestParticipantWithUser)` 方法

  **Acceptance Criteria**:
  - [ ] 两处 `Long.parseLong()` 已移除
  - [ ] 编译通过

  **QA Scenarios**:
  ```
  Scenario: 编译验证
    Tool: Bash
    Steps:
      1. cd backend-spring && ./mvnw compile -DskipTests
    Expected Result: BUILD SUCCESS
  ```

  **Commit**: YES（可与任务 1.1 合并）
  - Message: `fix(contest): remove Long.parseLong for userId in RankingServiceImpl`
  - Files: `backend-spring/.../service/impl/RankingServiceImpl.java`

---

- [x] 3. 修复 `ContestServiceImpl` 中的 `Long.parseLong()`

  **What to do**:
  - 修改 `ContestServiceImpl.java`
  - 第 283 行: `vo.setUserId(Long.parseLong(ranking.getUserId()));` → `vo.setUserId(ranking.getUserId());`

  **Must NOT do**:
  - 不要修改其他逻辑

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1（与任务 1.1、1.2 并行）
  - **Blocks**: 无
  - **Blocked By**: 无

  **References**:
  - `ContestServiceImpl.java:279-289` - `toRankingVO(GlobalRanking)` 方法

  **Acceptance Criteria**:
  - [ ] `Long.parseLong()` 已移除
  - [ ] 编译通过

  **QA Scenarios**:
  ```
  Scenario: 编译验证
    Tool: Bash
    Steps:
      1. cd backend-spring && ./mvnw compile -DskipTests
    Expected Result: BUILD SUCCESS
  ```

  **Commit**: YES（可与任务 1.1、1.2 合并）
  - Message: `fix(contest): remove Long.parseLong for userId in ContestServiceImpl`
  - Files: `backend-spring/.../service/impl/ContestServiceImpl.java`

---

- [x] 4. 补充 `ContestRankingVO` 缺失字段

  **What to do**:
  - 在 `ContestRankingVO.java` 中添加以下字段：
    ```java
    private String country;
    private Integer maxRating;
    private String ratingTitle;
    private String maxRatingTitle;
    private Integer contestsAttended;
    private String badge;
    ```

  **Must NOT do**:
  - 不要修改已有字段
  - 不要删除任何字段

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2（与任务 2.2、2.3、2.4 并行）
  - **Blocks**: 无
  - **Blocked By**: 任务 1.1（userId 必须是 String）

  **References**:
  - `ContestRankingVO.java` - 现有字段定义
  - `console/src/types/contest.ts:242-256` - 前端 `GlobalRankingEntry` 接口定义
  - `GlobalRanking.java` - 实体字段（country, maxRating, ratingTitle, maxRatingTitle, contestsAttended, badge）

  **Acceptance Criteria**:
  - [ ] 6 个新字段已添加
  - [ ] 字段类型与 `GlobalRanking` 实体一致
  - [ ] 编译通过

  **QA Scenarios**:
  ```
  Scenario: 编译验证
    Tool: Bash
    Steps:
      1. cd backend-spring && ./mvnw compile -DskipTests
    Expected Result: BUILD SUCCESS
  ```

  **Commit**: YES
  - Message: `feat(contest): add missing fields to ContestRankingVO`
  - Files: `backend-spring/.../dto/ContestRankingVO.java`

---

- [x] 5. 修改 `ContestServiceImpl.toRankingVO()` 填充新字段

  **What to do**:
  - 修改 `ContestServiceImpl.java` 中的 `toRankingVO(GlobalRanking ranking)` 方法
  - 添加以下字段映射：
    ```java
    vo.setCountry(ranking.getCountry());
    vo.setMaxRating(ranking.getMaxRating());
    vo.setRatingTitle(ranking.getRatingTitle());
    vo.setMaxRatingTitle(ranking.getMaxRatingTitle());
    vo.setContestsAttended(ranking.getContestsAttended());
    vo.setBadge(ranking.getBadge());
    ```

  **Must NOT do**:
  - 不要修改现有字段映射逻辑
  - 不要修改方法签名

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2（与任务 2.1、2.3、2.4 并行）
  - **Blocks**: 无
  - **Blocked By**: 任务 1.3（userId 修复）、任务 2.1（新字段已添加）

  **References**:
  - `ContestServiceImpl.java:279-289` - 现有 `toRankingVO` 方法
  - `GlobalRanking.java` - 实体字段列表
  - `console/src/types/contest.ts:242-256` - 前端期望的字段

  **Acceptance Criteria**:
  - [ ] 6 个新字段已在 `toRankingVO` 中填充
  - [ ] 编译通过

  **QA Scenarios**:
  ```
  Scenario: 编译验证
    Tool: Bash
    Steps:
      1. cd backend-spring && ./mvnw compile -DskipTests
    Expected Result: BUILD SUCCESS
  ```

  **Commit**: YES（可与任务 2.1 合并）
  - Message: `feat(contest): populate new fields in toRankingVO`
  - Files: `backend-spring/.../service/impl/ContestServiceImpl.java`

---

- [x] 6. 添加 `/rankings/global` 分页端点

  **What to do**:
  - 在 `ContestController.java` 中添加新方法：
    ```java
    @Operation(summary = "Get global rankings with pagination", description = "Get paginated global leaderboard")
    @ApiResponse(responseCode = "200", description = "Global rankings retrieved", content = @Content(schema = @Schema(implementation = PageResult.class)))
    @GetMapping("/rankings/global")
    public Result<PageResult<ContestRankingVO>> getGlobalRankingsPaginated(
            @Parameter(description = "Page number (1-based)")
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @Parameter(description = "Number of items per page")
            @RequestParam(required = false, defaultValue = "50") Integer limit) {
        
        PageResult<ContestRankingVO> result = contestService.getGlobalRankingsPaginated(page, limit);
        return Result.success(result);
    }
    ```

  **Must NOT do**:
  - 不要修改现有 `/contest/global-ranking` 端点（保持向后兼容）
  - 不要删除任何现有方法

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2（与任务 2.1、2.2、2.4 并行）
  - **Blocks**: 无
  - **Blocked By**: 任务 2.4（service 方法必须先存在）

  **References**:
  - `ContestController.java:236-243` - 现有 `/contest/global-ranking` 端点
  - `ContestController.java:278-289` - 分页端点示例（`/contest/{id}/ranking`）
  - `console/src/api/contest.ts:302-316` - 前端调用方式

  **Acceptance Criteria**:
  - [ ] 新端点 `/rankings/global` 已添加
  - [ ] 支持 `page` 和 `limit` 参数
  - [ ] 返回 `PageResult<ContestRankingVO>`
  - [ ] 编译通过

  **QA Scenarios**:
  ```
  Scenario: API 测试 - 正常请求
    Tool: Bash (curl)
    Preconditions: 后端服务运行中
    Steps:
      1. curl "http://localhost:9001/rankings/global?page=1&limit=10"
    Expected Result: HTTP 200，返回 JSON 包含 items, total, page, limit, totalPages
    
  Scenario: API 测试 - 默认参数
    Tool: Bash (curl)
    Steps:
      1. curl "http://localhost:9001/rankings/global"
    Expected Result: HTTP 200，默认 page=1, limit=50
  ```

  **Commit**: YES
  - Message: `feat(contest): add /rankings/global paginated endpoint`
  - Files: `backend-spring/.../controller/ContestController.java`

---

- [x] 7. 在 `ContestService` 接口和实现中添加分页查询方法

  **What to do**:
  - 在 `ContestService.java` 接口中添加：
    ```java
    PageResult<ContestRankingVO> getGlobalRankingsPaginated(Integer page, Integer limit);
    ```
  - 在 `ContestServiceImpl.java` 中实现：
    ```java
    @Override
    @Cacheable(value = "contestRanking", key = "'globalPaginated:' + #page + ':' + #limit")
    public PageResult<ContestRankingVO> getGlobalRankingsPaginated(Integer page, Integer limit) {
        int currentPage = (page != null && page > 0) ? page : 1;
        int currentLimit = (limit != null && limit > 0) ? Math.min(limit, 100) : 50;
        
        // 获取所有全局排名数据
        List<ContestRankingVO> allRankings = getGlobalRanking(null);
        
        int total = allRankings.size();
        int skip = (currentPage - 1) * currentLimit;
        List<ContestRankingVO> paginatedList = allRankings.stream()
                .skip(skip)
                .limit(currentLimit)
                .collect(Collectors.toList());
        
        return PageResult.of(paginatedList, (long) total, currentPage, currentLimit);
    }
    ```

  **Must NOT do**:
  - 不要修改 `getGlobalRanking(Integer limit)` 方法（保持向后兼容）
  - 不要删除任何现有方法

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2（与任务 2.1、2.2、2.3 并行）
  - **Blocks**: 任务 2.3（Controller 需要调用此方法）
  - **Blocked By**: 任务 1.3（userId 修复）、任务 2.1（新字段已添加）

  **References**:
  - `ContestService.java` - 接口定义
  - `ContestServiceImpl.java:192-195` - 现有 `getGlobalRanking` 方法
  - `RankingServiceImpl.java:28-55` - 分页逻辑示例

  **Acceptance Criteria**:
  - [ ] 接口中已添加 `getGlobalRankingsPaginated` 方法
  - [ ] 实现中已添加分页逻辑
  - [ ] 编译通过

  **QA Scenarios**:
  ```
  Scenario: 编译验证
    Tool: Bash
    Steps:
      1. cd backend-spring && ./mvnw compile -DskipTests
    Expected Result: BUILD SUCCESS
  ```

  **Commit**: YES（可与任务 2.3 合并）
  - Message: `feat(contest): add getGlobalRankingsPaginated service method`
  - Files: `backend-spring/.../service/ContestService.java`, `backend-spring/.../service/impl/ContestServiceImpl.java`

---

## Final Verification Wave

> 所有修复完成后，执行以下验证步骤：

- [x] F1. **API 功能测试** ✅ PASSED
  - 测试 `/contest/user/history`（需登录）→ 返回 200，空数组（用户没有历史记录）
  - 测试 `/contest/rankings/global` → 返回 200，分页数据完整
  - 响应包含所有前端期望的字段：rank, userId, username, avatar, country, rating, maxRating, ratingTitle, maxRatingTitle, contestsAttended, badge

- [x] F2. **响应字段完整性检查** ✅ COMPLETED
  - 确认 `/rankings/global` 返回的每个对象包含：rank, userId, username, avatar, country, rating, maxRating, ratingTitle, maxRatingTitle, contestsAttended, badge

- [x] F3. **编译和测试** ✅ PASSED
  - `./mvnw compile -DskipTests` → BUILD SUCCESS
  - `./mvnw test -Dtest='!*IT'` → 需要数据库运行

---

## Commit Strategy

**Wave 1 提交**（类型修复）:
```
fix(contest): fix userId type mismatch causing NumberFormatException

- Change ContestRankingVO.userId from Long to String
- Remove Long.parseLong() in RankingServiceImpl (2 locations)
- Remove Long.parseLong() in ContestServiceImpl (1 location)

Files: ContestRankingVO.java, RankingServiceImpl.java, ContestServiceImpl.java
```

**Wave 2 提交**（功能增强）:
```
feat(contest): add paginated /rankings/global endpoint and missing fields

- Add missing fields to ContestRankingVO: country, maxRating, ratingTitle, maxRatingTitle, contestsAttended, badge
- Populate new fields in ContestServiceImpl.toRankingVO()
- Add /rankings/global paginated endpoint in ContestController
- Add getGlobalRankingsPaginated() in ContestService interface and impl

Files: ContestRankingVO.java, ContestServiceImpl.java, ContestService.java, ContestController.java
```

---

## Success Criteria

### 必须修复的问题
- [x] `/contest/user/history` 不再返回 500，而是返回 200 + 用户比赛历史
- [x] `/rankings/global` 不再返回 404，而是返回 200 + 分页全局排名
- [x] `/rankings/global` 响应包含所有前端期望的字段

### 验证命令
```bash
# 1. 编译
cd backend-spring && ./mvnw compile -DskipTests

# 2. 测试 API（后端运行后）
curl -s "http://localhost:9001/rankings/global?page=1&limit=10" | jq .
curl -s -b /tmp/cookies.txt "http://localhost:9001/contest/user/history" | jq .

# 3. 运行单元测试
./mvnw test -Dtest='!*IT'
```

### 最终检查清单
- [ ] 所有 "Must Have" 已修复
- [ ] 编译成功
- [ ] 测试通过
- [ ] API 响应格式正确
