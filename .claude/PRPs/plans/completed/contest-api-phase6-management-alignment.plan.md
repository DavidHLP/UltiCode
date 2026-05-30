# Plan: Contest API Phase 6 — Management端对齐

## Summary
对齐 Management 前端与后端的 Contest DTO 字段，确保枚举类型在前端和后端之间一致使用，消除 `CreateContestDto` 中 `slug` 字段的存在歧义（前端误传、后端自动生成），以及统一枚举类型在前端 API 层和后端 DTO 层的使用。

## User Story
As an admin,
I want the Management frontend's contest creation form to match the backend's `CreateContestDTO` exactly,
So that contest creation/edit works reliably without field mismatch errors.

## Problem → Solution
Management 前端 `CreateContestDto` 包含 `slug?: string`，但后端 `CreateContestDTO` 没有此字段（slug 由后端自动生成）。前端可能误传 slug，但后端忽略它。此外，前端和后端对枚举类型的使用方式不一致（前端用 TS enum，后端用 raw String），导致类型安全边界模糊。

---

## Metadata
- **Complexity**: Small
- **Source PRD**: `docs/contest-api-alignment-analysis.md`
- **PRD Phase**: Phase 6: 管理端对齐 (2-3 天)
- **Estimated Files**: 5 files, ~200 lines
- **Files to Modify**: 3 backend DTOs + 1 management API file + 1 test file

---

## UX Design

### Before
```
前端 CreateContestDto
{
  slug?: string,      // ❌ 后端 CreateContestDTO 没有此字段
  title: "...",
  contestType: ContestType.ICPC,  // TS enum — 运行时可能传 "ICPC" 字符串
  ...
}
```

### After
```
前端 CreateContestDto
{
  title: "...",
  contestType: ContestType.ICPC,  // ✅ 使用与后端对齐的类型
  ...
}
后端 CreateContestDTO
{
  String title;
  String contestType;   // ✅ 用 String 接收，前端传枚举名
  String slug;          // ✅ 添加 slug 字段（后端自动生成，但保留前端可传的权利）
}
```

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| `CreateContestDto.slug` | 存在但后端忽略 | 移除 | slug 由后端 `generateSlug()` 自动生成 |
| `contestType` | TS enum | TS enum + 传字符串值 | 后端 DTO 用 String 接收 |
| `UpdateContestDto.slug` | 存在但后端忽略 | 移除 | 同上 |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `backend-spring/.../admin/dto/AdminContestVO.java` | 1-80 | 理解后端 DTO 结构 |
| P0 | `backend-spring/.../contest/dto/CreateContestDTO.java` | 1-60 | Phase 6 核心修改对象 |
| P0 | `backend-spring/.../contest/dto/UpdateContestDTO.java` | 1-60 | 同上 |
| P1 | `management/src/api/admin/contests.ts` | 76-130 | 前端 DTO 对齐目标 |
| P1 | `backend-spring/.../admin/service/impl/AdminContestServiceImpl.java` | 114-165 | 看 createContest 如何处理 slug 生成 |
| P2 | `backend-spring/.../test/.../ContestDtoAlignmentTest.java` | 72-120 | 已有测试验证对齐 |

---

## Patterns to Mirror

### ADMIN_DTO_PATTERN
// SOURCE: `backend-spring/.../admin/dto/AdminContestVO.java:12`
Admin 侧 DTO 使用 `String` 作为枚举字段类型：
```java
public class AdminContestVO {
    private String id;
    private String slug;
    private String title;
    private String contestType;    // raw String，不是枚举
    private String status;         // raw String
    private Boolean isVisible;
    private Boolean isPremium;
    private Boolean isPublished;
    private Integer participantCount;
    private Integer problemCount;
    private String createdAt;
    private String updatedAt;
}
```

### CREATE_DTO_PATTERN
// SOURCE: `backend-spring/.../contest/dto/CreateContestDTO.java`
后端 DTO 字段（当前 Phase 6 需要对齐的）：
```java
public class CreateContestDTO {
    private String title;
    private String description;
    private String contestType;     // String — 与前端 string enum 对齐
    private LocalDateTime startTime;
    private Integer duration;
    private Integer maxParticipants;
    private Boolean isPremium;
    private Boolean isPublished;
    private List<Long> problemIds;
    private List<String> tags;
    private String scoringRuleId;
    // ⚠️ 没有 slug 字段 — slug 由后端自动生成
}
```

### FRONTEND_DTO_PATTERN
// SOURCE: `management/src/api/admin/contests.ts:91-104`
前端 DTO（Phase 6 需要对齐的目标）：
```typescript
export interface CreateContestDto {
  slug?: string          // ❌ 需要移除 — 后端无此字段
  title: string
  description?: string
  contestType?: ContestType
  startTime: string
  duration: number
  maxParticipants?: number
  isPremium?: boolean
  isPublished?: boolean
  problemIds?: number[]
  tags?: string[]
  scoringRuleId?: string
}
```

### SERVICE_CREATE_PATTERN
// SOURCE: `backend-spring/.../admin/service/impl/AdminContestServiceImpl.java:114-140`
create 时 slug 生成：
```java
@Override
public AdminContestVO createContest(CreateContestDTO dto) {
    String slug = generateSlug(dto.getTitle());
    // ...
}
private String generateSlug(String title) {
    String base = title.toLowerCase()
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-|-$", "");
    return base + "-" + UUID.randomUUID().toString().substring(0, 8);
}
```

### ENUM_STRING_PATTERN
// SOURCE: `backend-spring/.../contest/entity/enums/ContestType.java`
后端枚举定义：
```java
public enum ContestType {
    WEEKLY("weekly"),
    DAILY("daily"),
    ICPC("ICPC"),
    IOI("IOI"),
    CUSTOM("custom");
    // ...
}
```

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `backend-spring/.../contest/dto/CreateContestDTO.java` | UPDATE | 添加 `slug` String 字段（与前端对齐，后端自动生成逻辑保持不变） |
| `backend-spring/.../contest/dto/UpdateContestDTO.java` | UPDATE | 同步字段与 CreateContestDTO 对齐 |
| `backend-spring/.../admin/controller/AdminContestController.java` | UPDATE | 验证 create 时 slug 处理逻辑（已由 service 处理，可确认无特殊校验） |
| `management/src/api/admin/contests.ts` | UPDATE | 移除 `CreateContestDto.slug` 和 `UpdateContestDto.slug` |
| `backend-spring/.../test/.../ContestDtoAlignmentTest.java` | UPDATE | 更新测试用例中的字段断言（已有 `hasSlug` 测试需要改为验证 slug 字段存在） |

---

## NOT Building
- 不修改前端枚举类型定义（`ContestType` enum 保持不变）
- 不修改后端枚举（仍然用 `String` 接收，由 service 层做枚举转换）
- 不修改任何业务逻辑（仅对齐字段，不改功能）
- 不修改 Console 前端（Phase 6 专注 Management 端）

---

## Step-by-Step Tasks

### Task 1: 更新后端 CreateContestDTO 添加 slug 字段
- **ACTION**: 在 `CreateContestDTO.java` 中添加 `private String slug;` 字段及其 getter
- **IMPLEMENT**: 添加后端 slug 字段，前端可选择性传入，后端 service 层保持现有的 `generateSlug()` 逻辑（如果前端传入 slug 则优先使用前端传入的）
- **MIRROR**: CREATE_DTO_PATTERN — 遵循现有 DTO 结构
- **IMPORTS**: 无新 import
- **GOTCHA**: 后端 service 目前根据 title 自动生成 slug。如果前端传入 slug，需要决定是使用前端 slug 还是仍用后端自动生成。保持现有行为（后端自动生成），但允许字段存在以对齐前端
- **VALIDATE**: 编译通过，`mvn compile` 无错误

### Task 2: 更新后端 UpdateContestDTO 添加 slug 字段
- **ACTION**: 在 `UpdateContestDTO.java` 中添加 `private String slug;` 字段及其 getter
- **IMPLEMENT**: 更新时 slug 不可修改（由后端管理），但字段保留以对齐前端类型
- **MIRROR**: CREATE_DTO_PATTERN — 与 CreateContestDTO 保持一致
- **IMPORTS**: 无新 import
- **GOTCHA**: Update 时 slug 修改应被忽略（只有创建时 slug 有意义）
- **VALIDATE**: 编译通过

### Task 3: 验证 AdminContestController create 逻辑
- **ACTION**: 检查 `AdminContestController.createContest` 是否正确传递 DTO 到 service
- **IMPLEMENT**: 确认 DTO 传递无误，service 层处理 slug 生成
- **MIRROR**: 现有 controller → service 传递模式
- **VALIDATE**: 确认 create 端点仍正常工作

### Task 4: 移除前端 CreateContestDto.slug 和 UpdateContestDto.slug
- **ACTION**: 从 `management/src/api/admin/contests.ts` 移除两个 DTO 中的 `slug?: string` 字段
- **IMPLEMENT**: 
  ```typescript
  // 移除 CreateContestDto 中的 slug 字段
  export interface CreateContestDto {
    // slug?: string  // REMOVED — slug 由后端自动生成
    title: string
    ...
  }
  // 移除 UpdateContestDto 中的 slug 字段
  export interface UpdateContestDto {
    // slug?: string  // REMOVED — slug 不可由前端修改
    ...
  }
  ```
- **MIRROR**: FRONTEND_DTO_PATTERN — 保持现有 TypeScript 接口风格
- **VALIDATE**: `pnpm type-check` 在 management 目录通过

### Task 5: 更新 ContestDtoAlignmentTest 测试用例
- **ACTION**: 修改测试用例以匹配新的 DTO 结构
- **IMPLEMENT**: 测试 `CreateContestDTO` 现在应该有 `slug` 字段；测试 `UpdateContestDTO` 也有 `slug` 字段
- **MIRROR**: 现有测试模式
- **VALIDATE**: `mvn test -Dtest=ContestDtoAlignmentTest` 通过

---

## Testing Strategy

### Unit Tests

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| `createContestDTO_hasSlug` | 检查 `CreateContestDTO.slug` 字段 | 存在且类型为 `String` | ⚠️ 测试需要从"无 slug"改为"有 slug" |
| `createContestDTO_hasContestType` | 检查 `CreateContestDTO.contestType` | 存在且类型为 `String` | 否 |
| `createContestDTO_hasScoringRuleId` | 检查 `CreateContestDTO.scoringRuleId` | 存在且类型为 `String` | 否 |
| `updateContestDTO_hasSlug` | 检查 `UpdateContestDTO.slug` | 新增字段 | 是 — 新增字段需要新测试 |
| management_api_slug_removed | TypeScript `CreateContestDto` | `slug` 字段不存在 | 前端类型检查 |

### Edge Cases Checklist
- [ ] 前端传入包含 `slug` 字段的请求 — 后端忽略，前端收到正确响应
- [ ] 前端传入不包含 `slug` 字段的请求 — 后端正常自动生成 slug
- [ ] Update 时传入 `slug` — 后端忽略，slug 保持不变
- [x] 空 title 时创建 — 由后端校验（已在现有代码中）
- [x] 无效 contestType — 由后端校验

---

## Validation Commands

### Backend
```bash
cd backend-spring && ./mvnw compile -q
```
EXPECT: 编译成功，无错误

```bash
cd backend-spring && ./mvnw test -Dtest=ContestDtoAlignmentTest -q
```
EXPECT: 所有 ContestDtoAlignmentTest 测试通过

### Frontend (Management)
```bash
cd management && pnpm type-check 2>&1 | head -30
```
EXPECT: 零类型错误

```bash
cd management && pnpm lint --max-warnings 0 src/api/admin/contests.ts
```
EXPECT: 无 lint 错误

### Integration Smoke Test
```bash
# 手动测试：POST /admin/contest 不带 slug，验证返回 201 且 body 中有 slug
curl -X POST http://localhost:9001/admin/contest \
  -H "Content-Type: application/json" \
  -d '{"title":"Test Contest","contestType":"ICPC","startTime":"2026-06-01T10:00:00","duration":120}'
EXPECT: 返回 201，body.slug 存在且非空

# 手动测试：POST /admin/contest 带 slug（如果允许），验证 slug 被接受
curl -X POST http://localhost:9001/admin/contest \
  -H "Content-Type: application/json" \
  -d '{"title":"Test Contest","slug":"custom-slug","contestType":"ICPC","startTime":"2026-06-01T10:00:00","duration":120}'
EXPECT: 返回 201，body.slug 为 "custom-slug"
```

---

## Acceptance Criteria
- [ ] `CreateContestDTO.java` 包含 `slug` 字段（String 类型）
- [ ] `UpdateContestDTO.java` 包含 `slug` 字段（String 类型）
- [ ] Management 前端 `CreateContestDto` 和 `UpdateContestDto` 不包含 `slug` 字段
- [ ] `ContestDtoAlignmentTest` 测试通过
- [ ] 后端编译成功
- [ ] 前端 type-check 通过
- [ ] create 端点手动验证通过
- [ ] 无 lint 错误

---

## Completion Checklist
- [ ] 代码遵循已发现的模式
- [ ] 错误处理符合代码风格
- [ ] 日志遵循代码约定（本次修改无需日志）
- [ ] 测试遵循测试模式
- [ ] 无硬编码值
- [ ] 文档无需更新（字段对齐无文档影响）
- [ ] 无不必要的作用域添加
- [ ] 自包含 — 实现期间无需额外提问

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| 前端误传 slug 导致冲突 | Low | Medium | 后端 service 已用 UUID 后缀确保 slug 唯一，即使 title 相同也不会冲突 |
| Phase 6 完成后发现其他对齐问题 | Medium | Low | Phase 6 仅针对 P7-1 和 P7-2，其他问题已在之前 phase 修复 |

---

## Notes

### 后端 slug 生成逻辑确认
`AdminContestServiceImpl.generateSlug()` 使用 title + 8位 UUID 后缀：
```java
private String generateSlug(String title) {
    String base = title.toLowerCase()
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-|-$", "");
    return base + "-" + UUID.randomUUID().toString().substring(0, 8);
}
```
这个逻辑保证了即使多个比赛 title 相同，slug 也不会冲突。添加 `slug` 字段到 DTO 不影响这个行为。

### 前端传入 slug 的处理
目前后端 service 没有检查 DTO.slug 是否为空。如果前端传入 slug，可以选择：
1. **忽略前端 slug**（当前行为）— 后端始终用 `generateSlug()` 生成
2. **优先使用前端 slug**（可选增强）— 如果前端传入且格式有效则使用，否则 fallback 到自动生成

Phase 6 保持选项 1（忽略前端 slug），不引入额外逻辑变更。

---

## Report

**Plan Created**: `.claude/PRPs/plans/contest-api-phase6-management-alignment.plan.md`
**Source PRD**: `docs/contest-api-alignment-analysis.md`
**Phase**: Phase 6: 管理端对齐
**Complexity**: Small
**Scope**: 5 files, 5 tasks
**Key Patterns**:
1. 后端 DTO 用 raw `String` 作为枚举字段类型
2. 前端 DTO 使用 TypeScript enum
3. slug 由后端 `generateSlug()` 自动生成，不依赖前端传入
4. AdminContestVO 作为后端 → 管理前端的标准响应类型

**External Research**: 无 — Phase 6 纯粹是字段对齐，不涉及新的外部库或 API

**Risks**: 无关键风险，轻量字段对齐

**Confidence Score**: 9/10 — 字段变更明确，已有测试覆盖类似场景