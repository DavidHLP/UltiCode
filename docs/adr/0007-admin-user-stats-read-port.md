# ADR-0007: Admin 用户统计 Read Port 提取 (AdminUserStatsReadPort)

- **Status**: Accepted
- **Date**: 2026-07-04
- **Scope**: `backend-spring` — admin (跨 submission / solution 模块读)
- **Supersedes**: none
- **Tags**: architecture, deep-module, port, locality, cross-module

## Context

`AdminUserServiceImpl.populateStats()` 是 admin 用户详情页的统计聚合点.
重构前它直接注入两个跨模块 mapper:

- `submission.mapper.SubmissionMapper` — `countByUserId` /
  `countAcceptedProblemsByUserId` / `calculateStreak`
- `solution.mapper.SolutionMapper` — `countByUserId`

这是 AdminReadModel seam 在 `AdminSubmissionReadPort` (dashboard 全局统计)
之后遗留的第二处跨模块直连: admin 用户详情页为了 4 个 per-user 计数,
穿透到 submission / solution 两张内部表的 mapper. `AdminSubmissionReadPort`
的 Javadoc 自述 _"Future phases add admin reads for user, contest, and
forum"_ — 本 ADR 落地其中的 user 维度.

submission 模块已有的 `SubmissionAnalyticsPort` 只有 dashboard 用的
`countByStatus` / `countByLanguage` (全局聚合), 没有 per-user 方法;
硬把 4 个 per-user 方法塞进去会破坏其接口内聚 (违反 ISP).

deletion test 验证: 删除 `populateStats` 里的 port 调用会迫使
`AdminUserServiceImpl` 重新直连两个 mapper + 重写 4 处 null 守卫 —
复杂度被浓缩进 adapter 而非平移.

## Decision

提取 **`AdminUserStatsReadPort`** 深模块, 封装 "为某用户聚合跨模块统计"
的读侧:

```
admin/port/AdminUserStatsReadPort.java              // 接口 (4 方法)
admin/port/adapter/AdminUserStatsReadAdapter.java   // 唯一 @Component adapter
```

接口形状 (返回基本类型, 非包装类):

```java
long countSubmissionsByUserId(String userId);
long countAcceptedProblemsByUserId(String userId);
long countSolutionsByUserId(String userId);
int  calculateSubmissionStreak(String userId);
```

- **返回 `long`/`int` 而非 `Long`/`Integer`**: adapter 拥有 null→0 降级,
  接口保证非 null, 调用方 (`AdminUserServiceImpl`) 永不再写 null 守卫 —
  这是该 deep module 的 leverage 所在.
- **adapter 是 admin 内唯一触碰这两个 mapper 的地方**: 跨模块依赖从
  `AdminUserServiceImpl` 收敛到单一 adapter, admin 其余代码只看 typed port.

`AdminUserServiceImpl` 改造:

- 移除 2 个依赖 (`SubmissionMapper` / `SolutionMapper`);
- 新增 1 个 `userStatsReadPort` 依赖;
- `populateStats` 从 12 行 (含 4 处 null 守卫) 缩至 6 行;
- 构造器参数 7 → 6.

测试职责重新划分:

- **adapter 单测** (新增 `AdminUserStatsReadAdapterTest`): 承继 null→0
  降级验证职责 (原 `AdminUserServiceImplTest.nullMapperReturns_defaultsToZero`
  的 null 路径迁移至此) + 值传递.
- **ServiceImpl 单测**: mock `AdminUserStatsReadPort` (基本类型, 永不 null),
  只验证 port 返回值 → VO 组装; 不再 standing up 两个 mapper mock.

## Consequences

**正向**

- `AdminUserServiceImpl` 不再 import `submission.mapper` / `solution.mapper`;
  跨模块耦合从 ServiceImpl 收敛到 adapter. AdminReadModel seam 在 user
  维度闭环.
- null 处理集中到 adapter (4 处 `n == null ? 0 : n`), 接口变窄、调用变干净.
  ServiceImpl 测试里 7 处分散的 mapper stub 简化为单一 port mock + 一个
  `stubStats` helper.
- per-user 统计获得独立测试面: adapter 测 null 降级, ServiceImpl 测组装,
  不再纠缠.
- 命名 / 包结构 / 测试体例与 `AdminSubmissionReadPort` 完全对齐, 复制
  成本低 — 后续 problem / contest / forum 维度可按同模式推进.

**负向 / 权衡**

- adapter 持有 `SubmissionMapper` + `SolutionMapper` 两个 mapper 依赖
  (跨两个模块). 可接受: adapter 是边界类, 职责就是把 admin 的 typed
  读请求翻译成两个目标模块的 mapper 调用; mapper 是无状态工具.
- 单 consumer (仅 `AdminUserServiceImpl.populateStats`). 权衡仍接受,
  价值来自浓缩 + 测试面 + AdminReadModel seam 推进, 而非消除重复.
  若未来 user profile / dashboard 也需要 per-user 统计, port 已就位.
- 接口返回 `long` 但 `AdminUserVO.UserStatsInfo` 字段是 `int`, 调用处
  `(int)` 强转. 可接受: 统计值不会超 `Integer.MAX_VALUE` (且原代码也是
  `Long.intValue()`).

## Alternatives considered

- **把 4 个 per-user 方法塞进既有 `SubmissionAnalyticsPort`** (admin 继续
  通过 `AdminSubmissionReadPort extends SubmissionAnalyticsPort` 用):
  被否. ① `SubmissionAnalyticsPort` 当前是 dashboard 全局统计语义, 混入
  per-user 破坏接口内聚 (ISP); ② `solution.countByUserId` 无处安放
  (solution 模块无 `port/`); ③ 接口膨胀违反 deep module 收敛原则.
- **在 user 模块新建 `UserStatsProjection`** (按 DDD, 用户统计属于 user
  领域): 被否. ① admin 是当前唯一 consumer, 接口归 admin (消费方) 更
  符合既有 `AdminSubmissionReadPort` 体例; ② user 模块当前没有
  submission / solution 的 mapper 持有, 建 projection 反而要反向依赖
  submission / solution, 引入新的循环依赖风险. 待 user 模块独立演化出
  统计读模型后再评估迁移.
- **改造 `populateStats` 走 `AdminSubmissionReadPort` (已存在)**: 被否.
  `AdminSubmissionReadPort` 当前是 dashboard 全局统计
  (`findById`/`countAll`/`countByStatus`/`countByLanguage`), 不含
  per-user; 为 4 个 per-user 方法扩展它会破坏其单一职责. 独立
  `AdminUserStatsReadPort` 更清晰.

## Related

- [[concepts/module-layering]] — Projection / Port / Inspector 模式
- `admin/port/AdminSubmissionReadPort.java` — AdminReadModel seam 第一阶段
  (其 Javadoc 自述 user/contest/forum 为后续阶段)
- `submission/port/SubmissionAnalyticsPort.java` — dashboard 全局统计 port
  (本 ADR 不污染其语义)
- ADR-0004 (moderation projection), ADR-0005 (achievement projection),
  ADR-0006 (problem detail port) — 同类 port/projection 提取决策
