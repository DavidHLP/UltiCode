# 修复 Admin Contest Rankings 404 错误

## TL;DR

> **问题**: 前端调用 `GET /admin/contests/{id}/rankings` 返回 404
> **根因**: `AdminContestController` 缺少该 endpoint，且前后端数据结构不匹配
> **修复**: 新建 `AdminContestRankingVO` DTO + 在 `AdminContestController` 添加 endpoint
>
> **涉及文件**: 2 个（新建 1 个，修改 1 个）
> **预计工作量**: 15-30 分钟
> **并行度**: 低（顺序执行）

---

## Context

### 原始问题
前端在管理后台查看比赛排名时，API 调用返回 404：
```
GET http://localhost:9001/admin/contests/contest-biweekly-170/rankings 404 (Not Found)
```

### 调查结果

**前端调用位置** (`management/src/api/admin/contests.ts:154-157`):
```typescript
async getRankings(id: string): Promise<{ data: ContestRanking[] }> {
  const response = await apiGet<{ data: ContestRanking[] }>(`/admin/contests/${id}/rankings`)
  return response
}
```

**前端期望的数据结构** (`management/src/api/admin/contests.ts:42-54`):
```typescript
export interface ContestRanking {
  id: string
  contestId: string
  userId: string
  totalScore: number
  totalPenalty: number
  rank: number
  user: {
    id: string
    username: string
    name: string | null
  }
}
```

**后端现状**:
- `AdminContestController` 只有 CRUD + announcements，**无 rankings endpoint**
- `ContestController` 有 `GET /contest/{id}/ranking`（单数，非 admin 路径）
- 后端 `ContestRankingVO` 字段名与前端不匹配（`score` vs `totalScore`，扁平字段 vs 嵌套 `user`）

### Metis 审查发现
- ✅ 诊断正确：endpoint 确实缺失
- ❌ 原方案不完整：只解决 404，没解决数据结构不匹配
- ⚠️ 字段映射：`score` → `totalScore`，`penalty` → `totalPenalty`
- ⚠️ 嵌套对象：前端需要 `user: {id, username, name}`，后端是平铺字段
- ⚠️ 缺失 `id` 字段
- ⚠️ `RankingService` 硬编码上限 100 条

---

## Work Objectives

### Core Objective
在 `AdminContestController` 中添加 `GET /{id}/rankings` endpoint，返回与前端数据结构匹配的排名列表。

### Concrete Deliverables
1. **新建** `backend-spring/src/main/java/com/ulticode/modules/admin/dto/AdminContestRankingVO.java`
2. **修改** `backend-spring/src/main/java/com/ulticode/modules/admin/controller/AdminContestController.java`

### Definition of Done
- [ ] Admin contest rankings endpoint 返回 200 而非 404
- [ ] 响应数据结构匹配前端 `ContestRanking` 接口
- [ ] 字段名正确：`totalScore`、`totalPenalty`、嵌套 `user` 对象
- [ ] 权限控制：`@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")`

### Must Have
- 新建 `AdminContestRankingVO` DTO 匹配前端结构
- 在 `AdminContestController` 注入 `RankingService`
- Controller 层做字段映射转换
- 返回 `Result<List<AdminContestRankingVO>>`

### Must NOT Have
- 不修改 `ContestRankingVO`（避免影响公开 API）
- 不修改前端代码（后端适配前端）
- 不改数据库 schema

---

## Verification Strategy

### Test Decision
- **基础设施**: 后端有 Maven + Spring Boot 测试框架
- **自动化测试**: 无（本修复为 Bug 修复，不涉及新功能测试）
- **验证方式**: Agent 执行 curl 调用验证 endpoint 可用性和数据结构

### QA Policy
通过 curl 调用验证 endpoint：
1. 验证 404 已修复（返回 200）
2. 验证响应字段匹配前端期望
3. 验证权限控制生效

---

## Execution Strategy

### Wave 1（顺序执行，2 个任务）

```
Wave 1:
├── Task 1: 新建 AdminContestRankingVO DTO
└── Task 2: 修改 AdminContestController 添加 rankings endpoint
```

---

## TODOs

- [ ] 1. 新建 AdminContestRankingVO DTO

  **What to do**:
  - 在 `backend-spring/src/main/java/com/ulticode/modules/admin/dto/` 目录下新建 `AdminContestRankingVO.java`
  - 数据结构必须匹配前端 `ContestRanking` 接口：
    ```java
    @Data
    public class AdminContestRankingVO {
        private String id;
        private String contestId;
        private String userId;
        private Long totalScore;      // 映射自 ContestRankingVO.score
        private Long totalPenalty;    // 映射自 ContestRankingVO.penalty
        private Integer rank;
        private UserInfo user;        // 嵌套对象
        
        @Data
        public static class UserInfo {
            private String id;        // 映射自 userId
            private String username;  // 映射自 username
            private String name;      // 映射自 name
        }
    }
    ```

  **Must NOT do**:
  - 不要添加前端不需要的额外字段
  - 不要使用 `ContestRankingVO` 的字段名（如 `score`、`penalty`）

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: []
  - **Reason**: 简单的 DTO 创建，不需要复杂逻辑

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Blocks**: Task 2

  **References**:
  - **Pattern Reference**: `backend-spring/src/main/java/com/ulticode/modules/contest/dto/ContestRankingVO.java` - 参考 Lombok `@Data` 注解和字段定义方式
  - **Pattern Reference**: `backend-spring/src/main/java/com/ulticode/modules/admin/dto/AdminContestVO.java` - 参考 admin 模块 DTO 的包结构和命名规范

  **Acceptance Criteria**:
  - [ ] 文件创建成功，编译通过
  - [ ] 包含所有前端期望的字段：`id`, `contestId`, `userId`, `totalScore`, `totalPenalty`, `rank`, `user`
  - [ ] `user` 为嵌套对象，包含 `id`, `username`, `name`
  - [ ] 使用 Lombok `@Data` 注解

  **QA Scenarios**:
  ```
  Scenario: DTO 编译验证
    Tool: Bash
    Steps:
      1. cd backend-spring && ./mvnw compile -pl . -q
    Expected Result: 编译成功，无错误
    Evidence: .sisyphus/evidence/task-1-compile.log
  ```

  **Commit**: YES
  - Message: `feat(admin): add AdminContestRankingVO DTO for contest rankings`
  - Files: `backend-spring/src/main/java/com/ulticode/modules/admin/dto/AdminContestRankingVO.java`

- [ ] 2. 修改 AdminContestController 添加 rankings endpoint

  **What to do**:
  - 在 `AdminContestController` 中注入 `RankingService`：
    ```java
    private final RankingService rankingService;
    ```
  - 在构造函数或 `@RequiredArgsConstructor` 自动注入（如果已使用）
  - 添加新的 GET endpoint：
    ```java
    @Operation(summary = "Get contest rankings", description = "Get rankings for a specific contest")
    @GetMapping("/{id}/rankings")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<List<AdminContestRankingVO>> getContestRankings(
            @Parameter(description = "Contest ID")
            @PathVariable String id,
            @Parameter(description = "Maximum number of rankings to return")
            @RequestParam(required = false, defaultValue = "100") Integer limit) {
        
        // 调用 rankingService 获取分页结果
        PageResult<ContestRankingVO> pageResult = rankingService.getContestRanking(id, 1, limit);
        
        // 转换为 AdminContestRankingVO 列表
        List<AdminContestRankingVO> rankings = pageResult.getItems().stream()
            .map(this::convertToAdminRanking)
            .collect(Collectors.toList());
        
        return Result.success(rankings);
    }
    
    private AdminContestRankingVO convertToAdminRanking(ContestRankingVO vo) {
        AdminContestRankingVO adminVo = new AdminContestRankingVO();
        // 需要设置 id - 但 ContestRankingVO 中没有 id 字段
        // 可能需要从其他来源获取或使用 contestId + userId 组合
        adminVo.setContestId(vo.getContestId() != null ? vo.getContestId().toString() : null);
        adminVo.setUserId(vo.getUserId());
        adminVo.setTotalScore(vo.getScore());        // 字段映射
        adminVo.setTotalPenalty(vo.getPenalty());     // 字段映射
        adminVo.setRank(vo.getRank());
        
        AdminContestRankingVO.UserInfo user = new AdminContestRankingVO.UserInfo();
        user.setId(vo.getUserId());                   // 映射
        user.setUsername(vo.getUsername());
        user.setName(vo.getName());
        adminVo.setUser(user);
        
        return adminVo;
    }
    ```
  - 需要导入：
    ```java
    import com.ulticode.modules.contest.dto.ContestRankingVO;
    import com.ulticode.modules.contest.service.RankingService;
    import java.util.stream.Collectors;
    import org.springdoc.core.annotations.ParameterObject;
    import io.swagger.v3.oas.annotations.Parameter;
    ```

  **重要注意**：`ContestRankingVO` 没有 `id` 字段，需要确定如何处理：
  - 选项 A：使用 `contestId + "_" + userId` 作为 id
  - 选项 B：检查 `ContestParticipant` 实体是否有 id 可以映射
  - 选项 C：前端可能不需要这个 id（建议先检查前端是否真正使用它）

  **Must NOT do**:
  - 不要修改 `ContestRankingVO`
  - 不要修改 `RankingService` 接口
  - 不要返回 `PageResult`（前端期望数组）

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: []
  - **Reason**: Controller 层添加 endpoint，主要是字段映射

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Blocked By**: Task 1（需要 AdminContestRankingVO 先创建）

  **References**:
  - **Pattern Reference**: `backend-spring/src/main/java/com/ulticode/modules/contest/controller/ContestController.java:288-310` - 参考 `getContestRanking` 的实现方式
  - **API Reference**: `backend-spring/src/main/java/com/ulticode/modules/contest/service/RankingService.java` - RankingService 接口定义
  - **Type Reference**: `backend-spring/src/main/java/com/ulticode/modules/contest/dto/ContestRankingVO.java` - 后端 VO 字段定义

  **Acceptance Criteria**:
  - [ ] `AdminContestController` 成功注入 `RankingService`
  - [ ] 新增 `GET /admin/contests/{id}/rankings` endpoint
  - [ ] 返回 `Result<List<AdminContestRankingVO>>`（非分页）
  - [ ] 字段映射正确：`score` → `totalScore`，`penalty` → `totalPenalty`
  - [ ] 嵌套 `user` 对象正确构建
  - [ ] 有 `@PreAuthorize` 权限控制
  - [ ] 编译通过

  **QA Scenarios**:
  ```
  Scenario: 验证 404 已修复
    Tool: Bash (curl)
    Preconditions: 后端服务运行，admin 用户已登录获取 cookie
    Steps:
      1. 登录获取 cookie: curl -s -X POST http://localhost:9001/auth/login -H "Content-Type: application/json" -d '{"username":"admin","password":"admin123"}' -c /tmp/admin_cookies.txt
      2. 调用 rankings endpoint: curl -s http://localhost:9001/admin/contests/contest-biweekly-170/rankings -b /tmp/admin_cookies.txt | jq '.code'
    Expected Result: code 为 0（成功），不是 404
    Failure Indicators: code 为 404 或请求失败
    Evidence: .sisyphus/evidence/task-2-rankings-response.json

  Scenario: 验证响应数据结构
    Tool: Bash (curl)
    Preconditions: 同上
    Steps:
      1. curl -s http://localhost:9001/admin/contests/contest-biweekly-170/rankings -b /tmp/admin_cookies.txt | jq '.data[0] | keys'
    Expected Result: 包含 id, contestId, userId, totalScore, totalPenalty, rank, user
    Failure Indicators: 缺少字段或字段名错误
    Evidence: .sisyphus/evidence/task-2-data-structure.json

  Scenario: 验证嵌套 user 对象
    Tool: Bash (curl)
    Preconditions: 同上
    Steps:
      1. curl -s http://localhost:9001/admin/contests/contest-biweekly-170/rankings -b /tmp/admin_cookies.txt | jq '.data[0].user'
    Expected Result: {"id": "...", "username": "...", "name": ...}
    Failure Indicators: user 为 null 或字段缺失
    Evidence: .sisyphus/evidence/task-2-user-object.json

  Scenario: 验证权限控制
    Tool: Bash (curl)
    Preconditions: 无登录 cookie
    Steps:
      1. curl -s http://localhost:9001/admin/contests/contest-biweekly-170/rankings | jq '.code'
    Expected Result: 401 或 403（未授权）
    Failure Indicators: 200（权限控制失效）
    Evidence: .sisyphus/evidence/task-2-auth-check.json
  ```

  **Commit**: YES
  - Message: `fix(admin): add contest rankings endpoint to AdminContestController`
  - Files: `backend-spring/src/main/java/com/ulticode/modules/admin/controller/AdminContestController.java`

---

## Final Verification Wave

- [ ] F1. **Plan Compliance Audit** — `oracle`
  读取计划，验证：
  - `AdminContestRankingVO.java` 已创建且字段正确
  - `AdminContestController.java` 已添加 rankings endpoint
  - 无 404 错误
  - 响应数据结构匹配前端期望
  Output: `VERDICT: APPROVE/REJECT`

- [ ] F2. **编译和运行验证** — `quick`
  ```bash
  cd backend-spring && ./mvnw spring-boot:run -Dmaven.test.skip=true
  # 另开终端验证
  curl -s http://localhost:9001/admin/contests/contest-biweekly-170/rankings -b /tmp/admin_cookies.txt | jq
  ```
  Output: `BUILD [PASS/FAIL] | API [PASS/FAIL]`

---

## Commit Strategy

- **Task 1**: `feat(admin): add AdminContestRankingVO DTO for contest rankings`
- **Task 2**: `fix(admin): add contest rankings endpoint to AdminContestController`

---

## Success Criteria

### Verification Commands
```bash
# 1. 验证 endpoint 不再返回 404
curl -s http://localhost:9001/admin/contests/contest-biweekly-170/rankings \
  -b /tmp/admin_cookies.txt | jq '.code'
# Expected: 0

# 2. 验证响应字段
curl -s http://localhost:9001/admin/contests/contest-biweekly-170/rankings \
  -b /tmp/admin_cookies.txt | jq '.data[0] | {id, contestId, userId, totalScore, totalPenalty, rank, user}'
# Expected: 所有字段存在且类型正确

# 3. 验证权限
curl -s http://localhost:9001/admin/contests/contest-biweekly-170/rankings | jq '.code'
# Expected: 401 或 403
```

### Final Checklist
- [ ] AdminContestRankingVO.java 已创建
- [ ] AdminContestController 已添加 rankings endpoint
- [ ] 不再返回 404
- [ ] 响应数据结构匹配前端 ContestRanking 接口
- [ ] 权限控制生效（未登录返回 401/403）
- [ ] 编译通过
