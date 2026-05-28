# Plan: Contest Phase 1 — Critical Type Fixes

## Summary
修复 Contest 模块最关键的类型不一致、逻辑错误和 API 参数缺失问题。包括统一 UUID 主键类型（String）、修复 getStats() 业务逻辑错误、删除冗余的 findAll() 方法、补全列表查询接口缺失的过滤参数。

## User Story
As a developer maintaining the UltiCode contest module,
I want to fix critical type mismatches and logic bugs in Phase 1,
So that the contest API returns correct data types, accurate statistics, and supports all documented query filters.

## Problem → Solution
- `ParticipationStatusDTO.contestId` 为 `Long` 与 Contest `String` UUID 主键不一致 → 统一为 `String`
- `getStats()` 用比赛状态计数冒充参与人数 → 改为查询真实的 participant/submission 统计
- `findAll()` 与 `findAllListVO()` 代码重复且无调用方 → 安全删除
- `GET /contest` 接口未映射 `contestType` 和 `isRated` 参数 → 补全 Controller 参数绑定

## Metadata
- **Complexity**: Medium
- **Source PRD**: `docs/contest-api-alignment-analysis.md`
- **PRD Phase**: Phase 1: 关键类型修复
- **Estimated Files**: 10-12 files
- **Estimated Time**: 1-2 天

---

## UX Design

### Before
```
GET /contest/:id/participation
Response: { contestId: 12345, ... }   // contestId 为数字，但 path 是 UUID 字符串

GET /contest/stats
Response: {
  registeredParticipants: 3,    // 实际上是 UPCOMING 比赛的数量
  activeParticipants: 2,        // 实际上是 RUNNING 比赛的数量
  totalSubmissions: 5           // 实际上是比赛总数
}

GET /contest?contestType=ICPC&isRated=true
// contestType 和 isRated 过滤条件被后端忽略
```

### After
```
GET /contest/:id/participation
Response: { contestId: "550e8400-e29b-41d4-a716-446655440000", ... }

GET /contest/stats
Response: {
  registeredParticipants: 152,  // 真实的 REGISTERED 状态 participant 数量
  activeParticipants: 47,       // 真实的 STARTED 状态 participant 数量
  completedParticipants: 891,   // 真实的 FINISHED 状态 participant 数量
  totalSubmissions: 3421        // 真实的提交总数
}

GET /contest?contestType=ICPC&isRated=true
// 两个过滤条件均生效
```

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| Participation API | contestId 返回 Long | contestId 返回 String UUID | 前端已使用 string，后端对齐 |
| Stats API | 返回比赛状态计数 | 返回真实 participant/submission 统计 | 破坏性变更（字段值语义改变） |
| List Query API | contestType/isRated 被忽略 | contestType/isRated 正确过滤 | 无破坏性变更，新增能力 |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `backend-spring/src/main/java/com/ulticode/modules/contest/dto/ParticipationStatusDTO.java` | 1-94 | 需要修改 contestId 类型 |
| P0 | `backend-spring/src/main/java/com/ulticode/modules/contest/dto/ContestRankingVO.java` | 1-150 | 需要修改 contestId 类型 |
| P0 | `backend-spring/src/main/java/com/ulticode/modules/contest/dto/ContestStatsVO.java` | 1-150 | 需要修改 contestId 类型 |
| P0 | `backend-spring/src/main/java/com/ulticode/modules/contest/service/impl/ContestSchedulerServiceImpl.java` | 84-192 | 两处 Long.parseLong(contestId) 需修改 |
| P0 | `backend-spring/src/main/java/com/ulticode/modules/contest/service/impl/ContestServiceImpl.java` | 128-152, 226-236, 414-438 | findAll 删除 + getStats 修复 + findAllListVO |
| P0 | `backend-spring/src/main/java/com/ulticode/modules/contest/service/ContestService.java` | 59, 132 | findAll 接口删除 + getStats 接口 |
| P0 | `backend-spring/src/main/java/com/ulticode/modules/contest/controller/ContestController.java` | 58-87 | 补全参数映射 |
| P1 | `backend-spring/src/main/java/com/ulticode/modules/contest/mapper/ContestParticipantMapper.java` | 1-230 | 需要新增 countByStatus 方法 |
| P1 | `backend-spring/src/main/java/com/ulticode/modules/contest/mapper/ContestSubmissionMapper.java` | 1-26 | 需要新增 countTotal 方法 |
| P2 | `backend-spring/src/test/java/com/ulticode/modules/contest/service/ContestDtoAlignmentTest.java` | 1-289 | 确认测试是否需要更新 |

## External Documentation

No external research needed — feature uses established internal patterns (MyBatis-Plus, Lombok, Spring Boot).

---

## Patterns to Mirror

### NAMING_CONVENTION
// SOURCE: `ContestParticipantMapper.java:73-74`
```java
@Select("SELECT COUNT(*) FROM contest_participants WHERE contest_id = #{contestId}")
long countByContestId(@Param("contestId") String contestId);
```
Mapper 方法命名：`countBy{Field}` 或 `countBy{Field}And{Field}`，返回 `long`，使用 `@Param` 注解。

### ERROR_HANDLING
// SOURCE: `ContestSchedulerServiceImpl.java:42-46`
```java
Contest contest = contestMapper.selectById(contestId);
if (contest == null) throw new BusinessException(ErrorCode.CONTEST_NOT_FOUND);
```
查询后 null 检查统一抛出 `BusinessException` 配合 `ErrorCode` 枚举。

### LOGGING_PATTERN
// SOURCE: `ContestServiceImpl.java:83`
```java
log.info("Contest created: {} by user {}", contest.getId(), userId);
```
使用 SLF4J 的 `{}` 占位符格式，关键操作（CRUD、状态变更）记录 info 级别日志。

### SERVICE_PATTERN
// SOURCE: `ContestServiceImpl.java:44-47`
```java
@Slf4j
@Service
@RequiredArgsConstructor
public class ContestServiceImpl implements ContestService {
```
所有 ServiceImpl 使用 `@Slf4j` + `@Service` + `@RequiredArgsConstructor`（构造器注入）。

### MAPPER_QUERY_PATTERN
// SOURCE: `ContestParticipantMapper.java:221-224`
```java
@Select("<script>SELECT * FROM contest_participants WHERE contest_id IN " +
        "<foreach item='item' collection='contestIds' open='(' separator=',' close=')'>" +
        "#{item}</foreach> ORDER BY contest_id, registered_at ASC</script>")
List<ContestParticipant> findByContestIds(@Param("contestIds") List<String> contestIds);
```
复杂 SQL 使用 XML `<script>` 标签支持动态语法（如 `<foreach>`）。

### TEST_STRUCTURE
// SOURCE: `ContestControllerTest.java:94-120`
```java
@Nested
@DisplayName("POST /contest")
class CreateContestTests {
    @Test
    @WithMockUser(roles = {"ADMIN"})
    @DisplayName("should return 200 with created contest when admin creates contest")
    void createContest_success_asAdmin() throws Exception {
        // Arrange
        when(contestService.createContest(any(CreateContestDTO.class), anyString()))
                .thenReturn(contestVO);
        // Act & Assert
        mockMvc.perform(...)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("contest-uuid-123"));
    }
}
```
测试使用 `@Nested` + `@DisplayName` 组织，MockMvc 做集成测试，Mockito `when(...).thenReturn(...)` 模拟 Service。

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `ParticipationStatusDTO.java` | UPDATE | contestId: Long → String |
| `ContestRankingVO.java` | UPDATE | contestId: Long → String |
| `ContestStatsVO.java` | UPDATE | contestId: Long → String |
| `ContestSchedulerServiceImpl.java` | UPDATE | 删除两处 Long.parseLong(contestId) |
| `ContestService.java` | UPDATE | 删除 findAll() 接口声明 |
| `ContestServiceImpl.java` | UPDATE | 删除 findAll() 实现 + 重写 getStats() |
| `ContestController.java` | UPDATE | getContestList 补全 contestType/isRated 映射 |
| `ContestParticipantMapper.java` | UPDATE | 新增 countByStatus(String) 方法 |
| `ContestSubmissionMapper.java` | UPDATE | 新增 countTotal() 方法 |
| `ContestDtoAlignmentTest.java` | VERIFY | 确认类型变更不影响现有测试 |

## NOT Building

- 不涉及前端代码修改（前端 `ParticipationStatus.contestId` 已经是 `string`）
- 不修改 `ContestQueryDTO` 的别名字段（留到 Phase 2）
- 不拆分 `ContestRankingVO`（留到 Phase 2）
- 不修复 `getGlobalRankingsPaginated` 内存分页（留到 Phase 4）
- 不引入 Zod schema（留到 Phase 3）
- 不改管理端 API（留到 Phase 6）

---

## Step-by-Step Tasks

### Task 1: 统一 DTO contestId 类型为 String
- **ACTION**: 修改 `ParticipationStatusDTO`、`ContestRankingVO`、`ContestStatsVO` 中的 `contestId` 字段类型从 `Long` 改为 `String`
- **IMPLEMENT**:
  - `ParticipationStatusDTO.java` 第 18 行：`private Long contestId;` → `private String contestId;`
  - `ContestRankingVO.java` 第 19 行：`private Long contestId;` → `private String contestId;`
  - `ContestStatsVO.java` 第 19 行：`private Long contestId;` → `private String contestId;`
- **MIRROR**: 参照 `Contest.java:16` 中 `private String id;` 的定义方式
- **IMPORTS**: 三个文件都使用 Lombok `@Data`，无需额外 import（删除 `java.lang.Long` 的隐性依赖）
- **GOTCHA**:
  - `ContestRankingVO` 的 `contestId` 在现有转换器中从未被填充（`toRankingVO` 方法不设置该字段），所以改类型不会影响运行时
  - `ContestStatsVO` 的 `contestId` 同样可能未被设置（因为 `getStats()` 是全局统计接口）
- **VALIDATE**: `./mvnw compile` 通过，无编译错误

### Task 2: 修复 ContestSchedulerServiceImpl 中的类型转换
- **ACTION**: 删除 `ContestSchedulerServiceImpl` 中两处 `Long.parseLong(contestId)`，改为直接赋值
- **IMPLEMENT**:
  - 第 90 行：`status.setContestId(Long.parseLong(contestId));` → `status.setContestId(contestId);`
  - 第 181 行：`status.setContestId(Long.parseLong(contestId));` → `status.setContestId(contestId);`
- **MIRROR**: 参照同文件中其他 `contestId` 的直接使用（如第 42 行 `contestMapper.selectById(contestId)`）
- **GOTCHA**: 确保不引入其他 `Long.parseLong` 调用；检查整个文件是否有遗漏
- **VALIDATE**: `./mvnw compile` 通过

### Task 3: 删除冗余的 findAll() 方法
- **ACTION**: 从 `ContestService` 接口和 `ContestServiceImpl` 实现中删除 `findAll()` 方法
- **IMPLEMENT**:
  - `ContestService.java` 第 59 行：删除 `PageResult<ContestVO> findAll(ContestQueryDTO query, String userId);`
  - `ContestServiceImpl.java` 第 128-152 行：删除 `findAll()` 完整实现
- **MIRROR**: 保留 `findAllListVO()` 作为唯一列表查询入口
- **GOTCHA**: `findAll()` 在 AdminContestController 中**没有**被调用（它调用的是 `findAllAdmin()`），可安全删除
- **VALIDATE**: `./mvnw compile` 通过；grep 确认无其他调用方

### Task 4: 重写 getStats() 实现
- **ACTION**: 重写 `ContestServiceImpl.getStats()`，使用真实的 participant/submission 统计数据替代比赛状态计数
- **IMPLEMENT**:
  ```java
  @Override
  public ContestStatsVO getStats() {
      ContestStatsVO stats = new ContestStatsVO();
      long registered = participantMapper.countByStatus(ContestParticipantStatus.REGISTERED.name());
      long active = participantMapper.countByStatus(ContestParticipantStatus.STARTED.name());
      long completed = participantMapper.countByStatus(ContestParticipantStatus.FINISHED.name());
      long totalSubmissions = contestSubmissionMapper.countTotal();
      stats.setRegisteredParticipants((int) registered);
      stats.setActiveParticipants((int) active);
      stats.setCompletedParticipants((int) completed);
      stats.setTotalSubmissions(totalSubmissions);
      return stats;
  }
  ```
- **MIRROR**: 参照 `ContestServiceImpl.java:226-236` 现有结构和 `ContestParticipantMapper` 的查询模式
- **IMPORTS**: 需确认 `ContestParticipantStatus` 枚举已导入；`ContestSubmissionMapper` 通过构造器注入（`@RequiredArgsConstructor`）
- **GOTCHA**:
  - `ContestServiceImpl` 当前没有注入 `ContestSubmissionMapper`，需要在字段列表中新增
  - 新增字段：`private final ContestSubmissionMapper contestSubmissionMapper;`
- **VALIDATE**: `./mvnw compile` 通过；启动后访问 `GET /contest/stats` 返回合理数值

### Task 5: 新增 ContestParticipantMapper.countByStatus 方法
- **ACTION**: 在 `ContestParticipantMapper` 中新增按状态统计参与者数量的方法
- **IMPLEMENT**:
  ```java
  @Select("SELECT COUNT(*) FROM contest_participants WHERE status = #{status}")
  long countByStatus(@Param("status") String status);
  ```
- **MIRROR**: 参照 `countByContestId` 和 `countByContestIdAndStatus` 的命名和注解风格
- **GOTCHA**: 方法名 `countByStatus` 不要与 `countByContestIdAndStatus` 混淆；这个是**全局**统计（不带 contest_id 过滤）
- **VALIDATE**: `./mvnw compile` 通过

### Task 6: 新增 ContestSubmissionMapper.countTotal 方法
- **ACTION**: 在 `ContestSubmissionMapper` 中新增统计所有提交总数的方法
- **IMPLEMENT**:
  ```java
  @Select("SELECT COUNT(*) FROM contest_submissions")
  long countTotal();
  ```
- **MIRROR**: 参照 `countByContestId` 的注解风格
- **VALIDATE**: `./mvnw compile` 通过

### Task 7: 补全 ContestController.getContestList 参数映射
- **ACTION**: 在 `ContestController.getContestList` 方法中补全 `contestType` 和 `isRated` 参数到 `ContestQueryDTO` 的映射
- **IMPLEMENT**:
  - 当前方法签名已有 `contestType` 参数（第 72 行），但只设置了 `query.setContestType(contestType)`
  - 需要新增 `isRated` 参数：
    ```java
    @Parameter(description = "Filter by rated status")
    @RequestParam(required = false) Boolean isRated,
    ```
  - 在 DTO 构建中新增：`query.setIsRated(isRated);`
- **MIRROR**: 参照现有 `@RequestParam` 参数的模式（如 `page`, `pageSize`, `status` 等）
- **GOTCHA**:
  - 当前 Controller 方法已有 `contestType` 参数并正确映射，只需确认它已在 DTO 中设置（是的，第 81 行）
  - 真正缺失的是 `isRated` 参数
- **VALIDATE**: `./mvnw compile` 通过；Swagger UI (`/swagger-ui.html`) 中确认 `GET /contest` 显示 `isRated` 参数

---

## Testing Strategy

### Unit Tests

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| `getStats_returnsCorrectParticipantCounts` | Mock participantMapper.countByStatus 返回 10/5/3 | stats.registered=10, active=5, completed=3 | Yes — zero counts |
| `getStats_returnsCorrectSubmissionCount` | Mock submissionMapper.countTotal 返回 100 | stats.totalSubmissions=100 | Yes — zero submissions |
| `getContestList_mapsIsRatedParam` | GET /contest?isRated=true | ContestQueryDTO.isRated = true | Yes — null param |
| `getParticipationStatus_returnsStringContestId` | contestId="uuid-123" | DTO.contestId="uuid-123" | Yes — verify no NumberFormatException |

### Edge Cases Checklist
- [ ] `getStats()` 当 contest_participants 表为空时返回全 0
- [ ] `getStats()` 当 contest_submissions 表为空时 totalSubmissions=0
- [ ] `getContestList()` 当 `isRated` 为 null 时不设置过滤条件
- [ ] `getParticipationStatus()` 当 contestId 为非法 UUID 字符串时正常传递（不做 parseLong）
- [ ] 删除 `findAll()` 后确认 `./mvnw compile` 无 "symbol not found" 错误

---

## Validation Commands

### Static Analysis
```bash
cd /home/david/project/UltiCode-Public-Next/backend-spring
./mvnw compile -q
```
EXPECT: Zero compilation errors

### Unit Tests
```bash
cd /home/david/project/UltiCode-Public-Next/backend-spring
./mvnw test -Dtest=ContestDtoAlignmentTest,ContestControllerTest,ContestServiceImplTest -q
```
EXPECT: All tests pass (可能需要更新测试以匹配类型变更)

### Full Backend Test Suite
```bash
cd /home/david/project/UltiCode-Public-Next/backend-spring
./mvnw test -q
```
EXPECT: No regressions

### Integration Tests
```bash
cd /home/david/project/UltiCode-Public-Next/backend-spring
./mvnw verify -Pci -q
```
EXPECT: All integration tests pass

### Manual Validation
- [ ] 启动后端 `pm2 restart ulticode-9001`
- [ ] 访问 Swagger UI (`http://localhost:9001/swagger-ui.html`) 确认 `GET /contest` 显示 `isRated` 参数
- [ ] 调用 `GET /contest/stats` 确认返回的数值合理（非比赛数量）
- [ ] 调用 `GET /contest/:id/participation` 确认 `contestId` 返回 String UUID

---

## Acceptance Criteria
- [ ] `ParticipationStatusDTO.contestId` 类型为 `String`
- [ ] `ContestRankingVO.contestId` 类型为 `String`
- [ ] `ContestStatsVO.contestId` 类型为 `String`
- [ ] `ContestSchedulerServiceImpl` 中无 `Long.parseLong(contestId)` 调用
- [ ] `findAll()` 方法已从 `ContestService` 和 `ContestServiceImpl` 中删除
- [ ] `getStats()` 返回真实的 participant 和 submission 统计（非比赛状态计数）
- [ ] `GET /contest` 接口支持 `isRated` 过滤参数
- [ ] 所有编译通过
- [ ] 所有测试通过
- [ ] Swagger 文档正确显示新增参数

## Completion Checklist
- [ ] Code follows discovered patterns (Lombok, MyBatis-Plus annotations, SLF4J logging)
- [ ] Error handling matches codebase style (BusinessException + ErrorCode)
- [ ] Logging follows codebase conventions
- [ ] Tests follow test patterns (@Nested + @DisplayName)
- [ ] No hardcoded values
- [ ] Documentation updated (Swagger annotations already present)
- [ ] No unnecessary scope additions
- [ ] Self-contained — no questions needed during implementation

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| contestId 类型变更影响前端（Management 端） | Low | Medium | 前端已是 `string`，验证 Management 端无类型错误 |
| getStats() 语义变更破坏现有调用方 | Medium | Low | 当前数据本身就是错误的，修复后调用方会获得正确数据 |
| 新增 Mapper 方法 SQL 语法错误 | Low | High | 使用简单 `SELECT COUNT(*) ...` 语法，编译期无法检查，需运行时验证 |
| findAll() 删除后发现隐藏调用方 | Low | High | 实施前已用 grep 全局搜索确认无调用方 |

## Notes
- `ContestRankingVO.contestId` 在现有代码中**从未被填充**（`toRankingVO` 转换器不设置该字段），所以改为 String 是安全的，不会影响任何现有响应
- `ContestStatsVO` 设计上有 `contestId` 字段，但 `getStats()` 是无参的全局统计接口。这个设计矛盾在 Phase 2 中应被审视（可能需要拆分为全局统计 VO 和单个比赛统计 VO）
- `ContestController.getContestList` 当前签名中已有 `contestType` 参数并正确映射到 DTO（第 72 行、第 81 行），真正缺失的只有 `isRated`
