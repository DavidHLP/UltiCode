# Plan: Moderation 模块 API 颗粒度对齐

## Summary

根据 `docs/moderation-api-granularity-analysis.md` 分析报告，本计划针对 6 个前后端不对齐问题进行修复。核心工作包括：重构 `performAction()` 方法（90行/8分支 → 策略模式）、后端 DTO 类型化、Console 举报功能补全、前端幽灵类型清理。

## User Story

As a **moderator**, I want the moderation system to have type-safe DTOs and a maintainable `performAction()` implementation, so that I can efficiently handle reported content with clear action traceability.

As a **user**, I want to be able to report wrong answers and copyright violations on the platform, so that I can flag inappropriate content accurately.

## Problem → Solution

| 问题 | 当前状态 | 目标状态 |
|------|---------|---------|
| `performAction()` 方法过粗 | 90行方法含8个分支switch | 拆分为策略模式处理器 |
| 后端 DTO `action` 为裸 String | `String action` + `Set.of("DELETED",...)` | `ModerationActionType action` 枚举类型 |
| Console 举报缺少 2 个类别 | 7 个 ReportCategory | 9 个 ReportCategory |
| Console 举报缺失 evidence | 只发 4 字段 | 发送 5 字段含 evidence |
| 前端 7 个幽灵类型 | 未使用但存在 | 移除或实现对应 API |
| `/moderation/enums` 端点未使用 | 存在但无前端调用 | 集成到前端或移除 |

## Metadata

- **Complexity**: Large
- **Source PRD**: docs/moderation-api-granularity-analysis.md
- **PRD Phase**: Standalone (分析报告，非 PRD)
- **Estimated Files**: 15-20 files
- **Estimated Tasks**: 10 个原子任务

---

## UX Design

### Before
```
N/A — 内部技术重构，无用户可见 UX 变化
```

### After
```
N/A — 内部技术重构，无用户可见 UX 变化
（但 Console 用户举报表单将增加 2 个类别选项和证据上传功能）
```

### Interaction Changes
| Touchpoint | Before | After | Notes |
|------------|--------|-------|-------|
| Console 举报对话框 | 7 个类别 | 9 个类别 | 增加 WRONG_ANSWER, COPYRIGHT |
| Console 举报对话框 | 无证据上传 | 有证据上传 | 新增 evidence 字段 |
| Management 操作面板 | 无变化 | 无变化 | 内部重构 |

---

## Mandatory Reading

Files that MUST be read before implementing:

| Priority | File | Lines | Why |
|----------|------|-------|-----|
| P0 | `backend-spring/src/main/java/com/ulticode/modules/moderation/service/impl/ModerationServiceImpl.java` | 189-277 | performAction() 重构核心 |
| P0 | `backend-spring/src/main/java/com/ulticode/modules/moderation/dto/PerformModerationActionDTO.java` | all | DTO 类型化 |
| P0 | `backend-spring/src/main/java/com/ulticode/modules/moderation/entity/enums/ModerationActionType.java` | all | 现有枚举定义 |
| P1 | `console/src/components/ReportDialog.vue` | all | 举报对话框修改 |
| P1 | `backend-spring/src/main/java/com/ulticode/modules/moderation/controller/ModerationController.java` | all | 端点确认 |
| P2 | `management/src/api/admin/moderation.ts` | 1-100 | 前端类型参考 |
| P2 | `backend-spring/src/main/java/com/ulticode/modules/moderation/service/impl/ModerationServiceImpl.java` | 1-100 | Service 结构 |

---

## External Documentation

| Topic | Source | Key Takeaway |
|-------|--------|--------------|
| Java Strategy Pattern | Java 17+ Sealed Classes | 使用 sealed interface + record 实现策略模式 |
| Spring Boot DTO Validation | Jakarta Validation | @NotBlank + 枚举类型验证 |

---

## Patterns to Mirror

### NAMING_CONVENTION
// SOURCE: ModerationServiceImpl.java:1-30
Service 接口方法使用 camelCase：`getQueueItems`, `performAction`, `createReport`
实现类后缀 `Impl`： `ModerationServiceImpl`

### ERROR_HANDLING
// SOURCE: ModerationServiceImpl.java:191-204
使用 `BusinessException` + `ErrorCode` 枚举：
```java
if (item == null) {
    throw new BusinessException(ErrorCode.MODERATION_QUEUE_NOT_FOUND);
}
```

### LOGGING_PATTERN
// SOURCE: ModerationServiceImpl.java:275
使用 Lombok `@Slf4j`，INFO 级别记录操作：
```java
log.info("Moderation action {} performed on queue item {} by moderator {}", action, id, moderatorId);
```

### SERVICE_LAYER_PATTERN
// SOURCE: ModerationServiceImpl.java:1-50
构造器注入依赖，final 字段：
```java
@Service
@RequiredArgsConstructor
@Slf4j
public class ModerationServiceImpl implements ModerationService {
    private final ModerationQueueMapper queueMapper;
    private final ModerationActionMapper actionMapper;
    // ...
}
```

### DTO_VALIDATION
// SOURCE: PerformModerationActionDTO.java
使用 Jakarta Validation 注解：
```java
@NotBlank(message = "Action is required")
private String action;
```

### SWITCH_EXPRESSION
// SOURCE: ModerationServiceImpl.java:222-268
Java 14+ switch expression 模式（当前使用传统 switch，未来重构后应保持）

### TYPESCRIPT_ENUM
// SOURCE: management/src/api/admin/moderation.ts:7-17
TypeScript 使用 `enum` 定义枚举：
```typescript
export enum ReportCategory {
  SPAM = 'SPAM',
  WRONG_ANSWER = 'WRONG_ANSWER',
  COPYRIGHT = 'COPYRIGHT',
  // ...
}
```

---

## Files to Change

### Backend (Java)

| File | Action | Justification |
|------|--------|---------------|
| `modules/moderation/dto/PerformModerationActionDTO.java` | UPDATE | `action` 字段改为枚举类型 |
| `modules/moderation/service/ModerationService.java` | UPDATE | 新增策略处理器接口 |
| `modules/moderation/service/impl/ModerationServiceImpl.java` | UPDATE | 重构 performAction() + 新增处理器 |
| `modules/moderation/service/impl/ActionHandler.java` | CREATE | 策略模式处理器接口 |
| `modules/moderation/service/impl/*ActionHandler.java` | CREATE | 各类别处理器实现 |
| `modules/moderation/entity/enums/ModerationActionType.java` | UPDATE | 确认枚举完整性 |

### Frontend Console

| File | Action | Justification |
|------|--------|---------------|
| `console/src/components/ReportDialog.vue` | UPDATE | 添加类别 + evidence 字段 |

### Frontend Management

| File | Action | Justification |
|------|--------|---------------|
| `management/src/api/admin/moderation.ts` | UPDATE | 清理幽灵类型或确认用途 |

---

## NOT Building

- 不实现 UserWarning/UserBan 的独立 CRUD API（当前只是预留类型）
- 不修改 `/moderation/enums` 端点（如果决定不使用则保留不动）
- 不修改 `batchAction()` 方法的业务逻辑（只复用重构后的 `performAction()`）
- 不修改 Appeal 相关端点（已对齐）

---

## Step-by-Step Tasks

### TASK-001: [Backend] 创建策略模式处理器接口

- **ACTION**: 创建 `ActionHandler` 策略接口
- **IMPLEMENT**: 在 `service/impl/` 下创建 `ModerationActionHandler` sealed interface，包含 `perform()` 方法和 `canHandle()` 静态工厂方法
- **MIRROR**: 参考 `java/coding-style.md` 中 sealed interface 模式
- **IMPORTS**:
  ```java
  import com.ulticode.modules.moderation.entity.ModerationQueue;
  import com.ulticode.modules.moderation.entity.ModerationAction;
  ```
- **GOTCHA**: 处理器需要访问 `ModerationQueue` 原始实体用于更新，需通过参数传入
- **VALIDATE**: 编译通过，Javax 注解正确

### TASK-002: [Backend] 实现删除/隐藏内容处理器 (DeleteHideHandler)

- **ACTION**: 创建处理 `DELETED` / `HIDDEN` 动作的处理器
- **IMPLEMENT**:
  ```java
  public final class DeleteHideHandler implements ModerationActionHandler {
      private final boolean hide; // true=HIDDEN, false=DELETED
      @Override
      public void perform(ModerationQueue item, String moderatorId, String note, ModerationAction action) {
          updateContentFlagStatus(item.getEntityType(), item.getEntityId(), true, note);
          item.setStatus("RESOLVED");
          item.setResolvedAt(now);
      }
  }
  ```
- **MIRROR**: 参考现有 `performAction()` 中 case "DELETED"/case "HIDDEN" 分支
- **IMPORTS**:
  ```java
  import com.ulticode.modules.moderation.service.impl.ModerationActionHandler;
  ```
- **GOTCHA**: 两个动作共享逻辑但 flag 值相同
- **VALIDATE**: 单元测试验证 `DELETED` 和 `HIDDEN` 动作处理正确

### TASK-003: [Backend] 实现恢复/忽略/已处理处理器 (RestoreDismissHandler)

- **ACTION**: 创建处理 `RESTORED` / `DISMISSED` / `RESOLVED` 动作的处理器
- **IMPLEMENT**: 与 TASK-002 类似，但 `updateContentFlagStatus(..., false, null)` 解除标记
- **MIRROR**: 参考现有 `performAction()` 中 case "RESTORED"/case "DISMISSED"/case "RESOLVED" 分支
- **IMPORTS**: 同 TASK-002
- **GOTCHA**: 这三个动作都设置 `flagged=false`
- **VALIDATE**: 单元测试验证三个动作处理正确

### TASK-004: [Backend] 实现警告用户处理器 (WarnHandler)

- **ACTION**: 创建处理 `WARNED` 动作的处理器
- **IMPLEMENT**: 调用 `createUserWarning()` 方法，设置 `item.setStatus("RESOLVED")`
- **MIRROR**: 参考现有 `performAction()` 中 case "WARNED" 分支
- **IMPORTS**:
  ```java
  import com.ulticode.modules.moderation.mapper.UserWarningMapper;
  ```
- **GOTCHA**: 需要注入 `UserWarningMapper` 依赖
- **VALIDATE**: 单元测试验证 UserWarning 创建正确

### TASK-005: [Backend] 实现临时/永久封禁处理器 (BanHandler)

- **ACTION**: 创建处理 `TEMP_BANNED` / `PERM_BANNED` 动作的处理器
- **IMPLEMENT**: 调用 `createUserBan()` 方法，设置相应状态
- **MIRROR**: 参考现有 `performAction()` 中 case "TEMP_BANNED"/case "PERM_BANNED" 分支
- **IMPORTS**:
  ```java
  import com.ulticode.modules.moderation.mapper.UserBanMapper;
  ```
- **GOTCHA**: `PERM_BANNED` 的 `durationDays` 为 null，`isPermanent=true`
- **VALIDATE**: 单元测试验证封禁创建正确

### TASK-006: [Backend] 实现申诉相关处理器 (AppealHandler)

- **ACTION**: 创建处理 `APPEAL_PENDING` / `APPEAL_APPROVED` / `APPEAL_REJECTED` 动作的处理器
- **IMPLEMENT**:
  - `APPEAL_PENDING`: 只更新状态为 `APPEAL_PENDING`，不设置 `resolvedAt`
  - `APPEAL_APPROVED`: `updateContentFlagStatus(..., false, null)` + `RESOLVED`
  - `APPEAL_REJECTED`: 直接 `RESOLVED`
- **MIRROR**: 参考现有 `performAction()` 中三个 appeal 相关分支
- **IMPORTS**: 同 TASK-002
- **GOTCHA**: `APPEAL_PENDING` 不应设置 `resolvedAt`
- **VALIDATE**: 单元测试验证三种申诉状态处理正确

### TASK-007: [Backend] 重构 performAction() 使用策略模式

- **ACTION**: 重构 `ModerationServiceImpl.performAction()` 方法，使用策略处理器
- **IMPLEMENT**:
  1. 保留验证逻辑（queue item 存在性检查）
  2. 使用 `ModerationActionType.valueOf(action)` 替代 `Set.of(...)` 验证
  3. 通过 `ModerationActionHandler.from(action)` 获取处理器
  4. 调用 `handler.perform(item, ...)`
  5. 保留 `updateReportsStatus()` 调用
  6. 保留日志记录
- **MIRROR**: 参考 `java/patterns.md` 中 Service Layer 模式
- **IMPORTS**:
  ```java
  import com.ulticode.modules.moderation.entity.enums.ModerationActionType;
  ```
- **GOTCHA**: 需要处理 `IllegalArgumentException`（无效枚举值）并转为 `BusinessException`
- **VALIDATE**: 编译通过，所有 action 类型处理结果与原逻辑一致

### TASK-008: [Backend] DTO action 字段改为枚举类型

- **ACTION**: 修改 `PerformModerationActionDTO.action` 从 `String` 改为 `ModerationActionType`
- **IMPLEMENT**:
  ```java
  @NotBlank(message = "Action is required")
  private ModerationActionType action;
  ```
- **MIRROR**: 参考 `backend-spring/src/main/java/com/ulticode/modules/moderation/dto/PerformModerationActionDTO.java`
- **IMPORTS**:
  ```java
  import com.ulticode.modules.moderation.entity.enums.ModerationActionType;
  ```
- **GOTCHA**:
  1. Jackson 序列化需确保前端传 `"DELETED"` 能正确反序列化为枚举
  2. Service 层验证从 `Set.of(...)` 改为 `action != null` 检查（类型安全）
  3. `BatchModerationActionDTO` 的 `action` 字段同样需要修改
- **VALIDATE**:
  - 编译通过
  - API 测试验证 `{"action": "DELETED"}` 正确反序列化
  - 原有单元测试 `ModerationDtoAlignmentTest` 仍然通过

### TASK-009: [Console] 举报对话框添加缺失类别

- **ACTION**: 在 `ReportDialog.vue` 的 `categories` 数组中添加 `WRONG_ANSWER` 和 `COPYRIGHT`
- **IMPLEMENT**:
  ```typescript
  const categories = [
    { value: "SPAM", label: "垃圾信息" },
    { value: "HARASSMENT", label: "骚扰" },
    { value: "HATE_SPEECH", label: "仇恨言论" },
    { value: "VIOLENCE", label: "暴力内容" },
    { value: "SEXUAL_CONTENT", label: "色情内容" },
    { value: "MISINFORMATION", label: "虚假信息" },
    { value: "WRONG_ANSWER", label: "答案错误" },        // 新增
    { value: "COPYRIGHT", label: "版权侵权" },           // 新增
    { value: "OTHER", label: "其他" },
  ];
  ```
- **MIRROR**: 参考 `management/src/api/admin/moderation.ts:7-17` 中 ReportCategory 枚举
- **IMPORTS**: 无新增 imports
- **GOTCHA**: 中文标签需本地化，但当前使用硬编码中文（与现有其他类别一致）
- **VALIDATE**:
  - `pnpm type-check` 通过
  - `pnpm lint` 通过
  - 视觉验证：举报对话框显示 9 个选项

### TASK-010: [Console] 举报对话框添加证据上传字段

- **ACTION**: 在 `ReportDialog.vue` 中添加 `evidence` 字段到提交数据
- **IMPLEMENT**:
  1. 新增 `evidence` ref
  2. 添加 Textarea 输入框（参考 reason 字段样式）
  3. 在 `apiPost` 调用中添加 `evidence: evidence.value`
- **MIRROR**: 参考现有 `reason` 字段实现模式
- **IMPORTS**:
  ```typescript
  // 无需新增 imports，Textarea 已导入
  ```
- **GOTCHA**: evidence 字段为可选，后端 DTO 中 `evidence` 为 `String`（无 @NotBlank）
- **VALIDATE**:
  - `pnpm type-check` 通过
  - 表单提交验证：打开 Network 面板确认 `/moderation/reports` 请求包含 `evidence` 字段

### TASK-011: [Management] 前端幽灵类型清理决策与执行

- **ACTION**: 分析 7 个幽灵类型，决定移除或保留
- **IMPLEMENT**:
  1. 读取 `management/src/api/admin/moderation.ts` 第 171-326 行
  2. 对每个幽灵类型进行判断：
     - `UserWarning`/`UserBan` 等 Entity 类型：保留（后端 Entity 定义存在，未来可能需要 API）
     - `QueryUserWarningsParams`/`QueryUserBansParams`：标记为 `@Deprecated` 并添加注释说明
     - `CreateUserBanDto`/`RevokeBanDto`：标记为 `@Deprecated`
     - `ModerationAction.performer`：移除嵌套 performer 字段（后端无此结构）
- **MIRROR**: 参考 TypeScript 类型定义规范
- **IMPORTS**: 无新增 imports
- **GOTCHA**: 不要删除后端不存在的所有类型，而是标记为预留 + 注释原因
- **VALIDATE**:
  - `pnpm type-check` 通过
  - 确认 Management 前端 moderation 模块功能正常

---

## Testing Strategy

### Unit Tests

| Test | Input | Expected Output | Edge Case? |
|------|-------|-----------------|------------|
| DeleteHideHandler.DELETED | ModerationQueue item | flagged=true, status=RESOLVED | entityType 为空 |
| DeleteHideHandler.HIDDEN | ModerationQueue item | flagged=true, status=RESOLVED | 与 DELETED 行为一致 |
| RestoreDismissHandler.RESTORED | ModerationQueue item | flagged=false, status=RESOLVED | - |
| RestoreDismissHandler.DISMISSED | ModerationQueue item | flagged=false, status=RESOLVED | - |
| WarnHandler | ModerationQueue item | UserWarning 创建, status=RESOLVED | authorId 为空 |
| BanHandler.TEMP_BANNED | ModerationQueue item, durationDays=30 | UserBan 创建, isPermanent=false | durationDays 边界值 |
| BanHandler.PERM_BANNED | ModerationQueue item | UserBan 创建, isPermanent=true | - |
| AppealHandler.APPEAL_PENDING | ModerationQueue item | status=APPEAL_PENDING, resolvedAt=null | - |
| AppealHandler.APPEAL_APPROVED | ModerationQueue item | flagged=false, status=RESOLVED | - |
| AppealHandler.APPEAL_REJECTED | ModerationQueue item | status=RESOLVED | - |
| performAction_invalidEnum | action="INVALID" | BusinessException | - |
| PerformModerationActionDTO_serialization | {"action":"DELETED"} | ModerationActionType.DELETED | - |

### Edge Cases Checklist
- [x] Empty input (entityId 为空)
- [x] Maximum size input (durationDays=3650)
- [x] Invalid types (无效枚举值)
- [x] Concurrent access (已有 assignee 的 item)
- [x] Permission denied (非 moderator 操作)
- [x] Network failure (N/A — 单元测试)

---

## Validation Commands

### Backend

```bash
# 编译
cd backend-spring && ./mvnw compile -Dmaven.test.skip=true

# 单元测试
cd backend-spring && ./mvnw test -Dtest=ModerationServiceImplTest

# DTO 对齐测试
cd backend-spring && ./mvnw test -Dtest=ModerationDtoAlignmentTest
```

EXPECT: 编译成功，所有测试通过

### Frontend Console

```bash
cd console && pnpm type-check
cd console && pnpm lint
cd console && pnpm test --run
```

EXPECT: 类型检查通过，lint 通过，测试通过

### Frontend Management

```bash
cd management && pnpm type-check
cd management && pnpm lint
cd management && pnpm test --run
```

EXPECT: 类型检查通过，lint 通过，测试通过

---

## Acceptance Criteria

- [ ] TASK-001 到 TASK-006: 所有策略处理器创建完成
- [ ] TASK-007: `performAction()` 重构完成，行为与原逻辑一致
- [ ] TASK-008: DTO action 字段改为枚举类型，API 兼容
- [ ] TASK-009: Console 举报对话框显示 9 个类别
- [ ] TASK-010: Console 举报支持 evidence 字段
- [ ] TASK-011: 前端幽灵类型已清理或标记为预留
- [ ] 所有验证命令通过
- [ ] 无类型错误
- [ ] 无 lint 错误
- [ ] 单元测试覆盖 80%+

---

## Completion Checklist

- [ ] 代码遵循 discovered patterns
- [ ] 错误处理匹配 codebase style (`BusinessException` + `ErrorCode`)
- [ ] 日志遵循 codebase conventions (`@Slf4j` + `log.info`)
- [ ] 测试遵循 test patterns (`@ExtendWith(MockitoExtension.class)`)
- [ ] 无 hardcoded values（使用常量/枚举）
- [ ] 文档已更新（如需要）
- [ ] 无不必要的 scope 添加
- [ ] Self-contained — 实现过程中无需额外搜索

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| 策略处理器导致循环依赖 | Low | High | 处理器只接收数据，不直接访问 Service |
| 枚举序列化兼容性问题 | Medium | High | 验证 Jackson 序列化配置 |
| 前端类型清理影响其他模块 | Medium | Medium | 确认无其他模块依赖这些类型 |
| 原 `performAction()` 分支过多导致遗漏 | Low | High | 编写覆盖所有 action 类型的单元测试 |

---

## Notes

1. **策略模式选择理由**: `performAction()` 的 8 个分支各自处理不同副作用（删除/隐藏/警告/封禁/恢复），适合用策略模式分解。每个处理器独立测试，降低回归风险。

2. **DTO 类型化理由**: 后端已有 `ModerationActionType` 枚举，但 DTO 使用裸 `String`，导致 Service 层需要手动 `Set.of(...)` 验证。改为枚举类型后，编译时即可捕获无效值。

3. **Console evidence 字段**: 当前 Console 举报不发送 evidence，但后端 DTO 接受此字段。这是 P4 低优先级问题，但实现简单，一并修复。

4. **幽灵类型清理**: `UserWarning`/`UserBan` 等类型保留但标记为 `@Deprecated`，因为后端 Entity 存在，未来可能需要对应 API。`ModerationAction.performer` 因后端无此结构，应移除。
