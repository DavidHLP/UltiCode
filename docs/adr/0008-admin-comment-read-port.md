# ADR-0008: Admin 评论视图 Read Port 提取 (AdminCommentReadPort)

- **Status**: Accepted
- **Date**: 2026-07-04
- **Scope**: `backend-spring` — admin (跨 user / forum / solution 模块读)
- **Supersedes**: none
- **Tags**: architecture, deep-module, port, locality, cross-module

## Context

`AdminCommentServiceImpl` 同时承担两类职责：

1. **评论 CRUD 本身**（flag / unflag / delete / bulk）：直接操作
   `ForumCommentMapper` + `SolutionCommentMapper` — 这是 admin
   评论管理的合法主战场，非泄漏。
2. **评论视图的跨模块 enrichment**：为渲染 `AdminCommentVO` 需要
   三种跨模块读 — 作者摘要（user）、父帖子标题（forum post）、
   父题解标题（solution）。重构前由 5 个 mapper 注入中的 3 个泄漏
   完成：

   - `user.mapper.UserMapper` — `selectById` / `selectBatchIds`
     （作者资料：username / avatar）
   - `forum.mapper.ForumPostMapper` — `selectById` / `selectBatchIds`
     （帖子标题）
   - `solution.mapper.SolutionMapper` — `selectById` / `selectBatchIds`
     （题解标题）

   分布在 `getForumComments` / `getSolutionComments` / `getAllComments`
   3 个列表路径 + `getComment` 单条路径，外加 3 个 batch-load 私有
   helper（`batchLoadUsers` / `batchLoadPosts` / `batchLoadSolutions`）。

这是 AdminReadModel seam 在 `AdminSubmissionReadPort` (dashboard 全局)
与 `AdminUserStatsReadPort` (per-user 统计) 之后遗留的 forum 维度
跨模块直连 — 正是 ADR-0007 / `AdminSubmissionReadPort` Javadoc 自述
_"Future phases add admin reads for user, contest, and forum"_ 中
的 forum 维度。本 ADR 落地 forum（评论侧）。

`AdminUserStatsReadPort` 返回基本类型 `long`/`int`，语义是 per-user
计数；评论 enrichment 需要的是实体摘要（id/username/avatar）+ 字符串
标题，语义完全不同，硬塞进去会破坏其接口内聚（违反 ISP）。

deletion test 验证: 删除 port 会迫使 `AdminCommentServiceImpl`
重新直连 3 个 mapper + 重写 3 个 batch-load helper + 重写
`forumToAdminVO` / `solutionToAdminVO` 里的 4 处 null 守卫
（`post != null ? post.getTitle() : null` 等） — 复杂度被浓缩进
adapter 而非平移。

## Decision

提取 **`AdminCommentReadPort`** 深模块, 封装 "为 admin 评论视图
enrichment 的跨模块读" 的读侧:

```
admin/port/AdminCommentReadPort.java              // 接口 (3 方法 + AuthorSummary record)
admin/port/adapter/AdminCommentReadAdapter.java   // 唯一 @Component adapter
```

接口形状 (返回 typed view, 非实体):

```java
Map<String, AuthorSummary> findAuthorSummariesByIds(Set<String> userIds);
Map<String, String>        findForumPostTitlesByIds(Set<String> postIds);
Map<String, String>        findSolutionTitlesByIds(Set<String> solutionIds);

record AuthorSummary(String id, String username, String avatar) {}
```

- **返回 typed view 而非实体**: `AuthorSummary` record + title `String`
  释放 `AdminCommentServiceImpl` 不再 import `User` / `ForumPost` /
  `Solution` 三个实体 — 这是该 deep module 的 leverage 所在 (对比
  `AdminSubmissionReadPort` 返回 `Submission` 实体, 那里 submission
  本身是 admin 操作目标; 这里 user/post/solution 仅用于 enrichment,
  返回实体会让 leverage 平移而非浓缩)。
- **`AuthorSummary` 独立于 `AdminCommentVO.AuthorInfo`**: port 是
  架构 seam, VO 是前端契约, 两者演化原因不同; 解耦后 port 不被
  VO 字段变更绑架。`AdminCommentServiceImpl` 多一行
  `new AdminCommentVO.AuthorInfo(s.id(), s.username(), s.avatar())`
  映射, 代价可接受。
- **空输入短路**: adapter 对 `Set.isEmpty()` 直接返回 `Map.of()`,
  不调 mapper — 列表为空时不触发无谓查询。
- **null-value 容忍**: post / solution title 可能为 null,
  `Collectors.toMap` 会 NPE; adapter 用手动 `HashMap` 累加, 保留
  null value, 让调用方区分 "实体缺失" (map 不含 key) vs "实体存在,
  标题为 null" (map 含 key, value null)。
- **adapter 是 admin 内唯一触碰这 3 个 mapper 的地方**: 跨模块依赖
  从 `AdminCommentServiceImpl` 收敛到单一 adapter。

`AdminCommentServiceImpl` 改造:

- 移除 3 个依赖 (`UserMapper` / `ForumPostMapper` / `SolutionMapper`);
- 新增 1 个 `commentReadPort` 依赖 (构造器 5 → 3);
- 删除 3 个 batch-load helper;
- `forumToAdminVO` / `solutionToAdminVO` 签名从
  `(comment, User, ForumPost)` 改为
  `(comment, AuthorSummary, String postTitle)`;
- `getComment` 单条路径用 `Set.of(id)` 包装走 batch port, 与列表
  路径共用同一组方法 (接口保持窄)。

**写侧不动**: flag / unflag / delete / bulk 继续直接操作
`ForumCommentMapper` + `SolutionCommentMapper` — 那是 admin 评论
CRUD 的目标实体, 非 cross-module leakage。这是本 port 接口窄的
关键: 只动读侧 enrichment。

测试职责重新划分:

- **adapter 单测** (新增 `AdminCommentReadAdapterTest`): 钉住空输入
  短路、entity→view 强制转换、缺失 id 不在 map、null title 容忍
  (对 pin `Collectors.toMap` 会 NPE 的回归点尤其关键)。
- **ServiceImpl 单测**: mock `AdminCommentReadPort` (返回 AuthorSummary
  / title map), 不再 standing up 3 个跨模块 mapper mock; 构造器
  从 5 mock 缩至 3。

## Consequences

**正向**

- `AdminCommentServiceImpl` 不再 import `user.mapper` /
  `forum.mapper.ForumPostMapper` / `solution.mapper.SolutionMapper`;
  也不再 import `User` / `ForumPost` / `Solution` 实体。跨模块
  耦合从 ServiceImpl 收敛到 adapter。AdminReadModel seam 在 forum
  (评论) 维度闭环。
- 列表 / 单条 / 全量三路径 + VO 组装的 4 处 null 守卫逻辑集中
  到 adapter (空 map + 缺失 key), ServiceImpl 的 `forumToAdminVO`
  只剩一句 `author != null ? ... : null`。
- enrichment 获得独立测试面: adapter 测边界 (空/null/缺失),
  ServiceImpl 测 port→VO 组装, 不再纠缠。
- 命名 / 包结构 / 测试体例与 `AdminSubmissionReadPort` /
  `AdminUserStatsReadPort` 完全对齐, 复制成本低 — 后续 contest /
  其他维度可按同模式推进。

**负向 / 权衡**

- adapter 持有 3 个跨模块 mapper 依赖 (user + forum + solution)。
  可接受: adapter 是边界类, 职责就是把 admin 的 typed 读请求翻译
  成三个目标模块的 mapper 调用; mapper 是无状态工具。
- 单 consumer (仅 `AdminCommentServiceImpl`)。权衡仍接受, 价值来自
  浓缩 + 测试面 + AdminReadModel seam 推进, 而非消除重复。若未来
  forum / solution 模块自身的 admin 读路径也需要这些摘要, port
  已就位。
- `getComment` 单条路径用 `Set.of(id)` 包装 batch port, 多一次
  `Map.get`。可接受: 避免为单条另设一组 single 方法 (会破坏窄接口)。

## Alternatives considered

- **把 enrichment 方法塞进既有 `AdminUserStatsReadPort`**: 被否。
  那个 port 是 per-user 计数语义 (返回 `long`/`int`), 混入 author
  摘要 + post/solution title 破坏接口内聚 (ISP), 且 post/solution
  title 与 user stats 无关。
- **port 返回实体 (`User`/`ForumPost`/`Solution`) 而非 typed view**:
  被否。ServiceImpl 仍要 import 实体 + 写 `post.getTitle()` + null
  守卫, leverage 平移而非浓缩, deletion test 不通过。
- **只提取 user 部分, 复用既有 user 读模型**: 被否。当前 user 模块
  没有为 admin 提供 author 摘要 port; 而 forum post / solution title
  无处安放。三者为评论 enrichment 共生, 拆开提取会引入 3 个微型
  port, 违反 deep module 收敛原则。
- **改造写侧 (flag/unflag/delete) 也走 port**: 被否。写侧操作的是
  admin 评论管理本身的目标实体 (ForumComment/SolutionComment),
  非 cross-module leakage; 把它们塞进 port 会让接口变宽且语义模糊
  (读 + 写混合)。读/写分离, 写侧保留直接 mapper 操作。

## Related

- `admin/port/AdminSubmissionReadPort.java` — AdminReadModel seam 第一阶段
- `admin/port/AdminUserStatsReadPort.java` — AdminReadModel seam 第二阶段
  (per-user 统计)
- ADR-0007 (admin user stats read port) — 同系列 per-user 维度决策
- ADR-0004 (moderation projection), ADR-0005 (achievement projection),
  ADR-0006 (problem detail port) — 同类 port/projection 提取决策
