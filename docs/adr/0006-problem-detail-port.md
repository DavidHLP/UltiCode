# ADR-0006: Problem 模块提取 ProblemDetailPort 深模块

- **Status**: Accepted
- **Date**: 2026-07-04
- **Scope**: `backend-spring` — problem
- **Supersedes**: none
- **Tags**: architecture, deep-module, port, locality

## Context

`ProblemServiceImpl` 的写路径在 `ProblemProjection`（读侧）提取后仍残留一个
**111 LOC 的 `updateProblemDetail` 巨方法**（行 212–322），它做四件相互正交的
低级实体操作：

1. `ProblemDetail` upsert（selectOne → 字段级条件更新 → insert/updateById）；
2. `ProblemLanguage` 重建（delete + 按 `findByValue` 校验后 batch insert）；
3. `ProblemExample` 重建（解析 JSON → delete → batch insert）；
4. `ProblemTagRelation` 重建（按 label 查 `ProblemTag` → delete relations → batch insert）。

加上辅助方法 `updateProblemLanguages`（26 LOC），合计 **137 LOC 的低级实体操作**
裸露在 service 里。它既不是状态机编排（状态机只动 `problems` 行），也不是
投影（投影只读）。它是一个被遗忘的「写侧领域」——`ProblemProjection` 提取
读侧时，对应的写侧没有一起搬走。

deletion test 验证：删除这 137 LOC 会浓缩复杂度（4 个 satellite 写操作各自
就位 + ProblemServiceImpl 回归纯状态机），而不是平移复杂度。

## Decision

提取 **`ProblemDetailPort`** 深模块，封装 `problem_details` 行及其 3 张
satellite 表的写生命周期：

```
problem/port/ProblemDetailPort.java          // 接口
problem/port/DefaultProblemDetailPort.java   // 唯一 adapter
```

接口形状（单一方法，对齐 `SubmissionWritePort.submit(userId, CreateSubmissionDTO)`
既接受 controller DTO 的项目先例）：

```java
void applyDetailUpdate(Long problemId, Problem problem, UpdateProblemDTO updateDTO);
```

- 传 `Problem` 而非仅 `id`：新建 `ProblemDetail` 行时需 denormalize
  `problem.slug`（`problem_details.slug` NOT NULL）。
- `@Transactional` 在 adapter 方法上（与 `DefaultSubmissionWritePort` 一致）；
  `ProblemServiceImpl.updateProblem` 保留自己的 `@Transactional`，Spring
  `PROPAGATION_REQUIRED` 让 port 加入外层事务，原子性不变。

`ProblemServiceImpl` 改造：

- 移除 6 个依赖（5 个 mapper + `ObjectMapper`）；
- 新增 1 个 `problemDetailPort` 依赖；
- `updateProblem` 内 `updateProblemDetail(...)` 改为
  `problemDetailPort.applyDetailUpdate(...)`；
- 删除两个 private 方法（`updateProblemDetail` / `updateProblemLanguages`）。

## Consequences

**正向**

- `ProblemServiceImpl` 409 → ~250 LOC，回归纯状态机职责（CRUD on `problems` +
  premium guard + cross-module `findById/findBySlug/toVO` facade）。
- 4 个 satellite 写分支获得独立测试面（mock 5 mapper + 真实 `ObjectMapper`），
  不再需要为写路径 standing up `ProblemMapper` / `ProblemVersionService` /
  `ProblemProjection`。
- 与 `ProblemProjection`（读）对称补全 problem 模块的写侧 seam，命名/包结构
  对齐 `submission/port/`。

**负向 / 权衡**

- 单 consumer（仅 `ProblemServiceImpl`；`AdminProblemServiceImpl` 只读 +
  bulk-edit 仅改 difficulty，不触发 detail 写）。权衡：仍接受，因为价值来自
  浓缩 + 测试面，而非消除重复。如果未来 admin / import 路径需要 detail 写，
  port 已就位。
- `Problem` entity 跨过 seam 边界（仅为 denormalize slug）。可接受：port 与
  state machine 同模块，知道 `Problem` 不构成跨域耦合。
- 4 个 mapper（`ProblemDetailMapper` / `ProblemExampleMapper` /
  `ProblemLanguageMapper` / `ProblemTagMapper` / `ProblemTagRelationMapper`）
  现在被 `DefaultProblemProjection`（读）与 `DefaultProblemDetailPort`（写）
  双持有——这是预期的 read/write 分离，mapper 是无状态工具。

## Alternatives considered

- **引入 `ProblemDetailUpdate` record 包一层**（不接 `UpdateProblemDTO`）：
  被否。`SubmissionWritePort.submit(userId, CreateSubmissionDTO)` 已确立
  「port 直接接 controller DTO」的先例；额外 record 类型增加文件数且要求
  service 做一次 DTO→record 转换，收益不抵成本。
- **拆 4 个 port 方法**（`upsertDetail` / `rebuildLanguages` / ...）：
  被否。破坏原子性——4 个写操作必须在同一事务里要么全成要么全回滚。拆开
  后调用方需在事务里串 4 次，接口变宽且 rebuild 语义泄漏。
- **改造 `AdminProblemServiceImpl` 也走 port**：被否。admin 当前不写 detail
  路径，无重复可消；保持现状避免无收益改动。

## Related

- [[concepts/module-layering]] — Projection / Port / Inspector 模式
- `submission/port/SubmissionWritePort.java` — port + DTO 参数先例
- `problem/projection/ProblemProjection.java` — 读侧对称模块
- ADR-0004 (moderation projection), ADR-0005 (achievement projection) — 同类
  Projection 提取决策
