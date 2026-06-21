---
title: 决策记录（Decisions）
tags: [decision, governance]
status: living
updated: 2026-06-21
owner: architect
---

# 决策记录（Decisions）

> 记录「一个不那么显然的决策的**为什么**」——不是代码现状（那是 [[codemap/README]]），不是某实体全景（那是 `entities/`）。决策一旦 `accepted` 即 **frozen**：要改请新写一篇并置旧篇为 `superseded`，不要原地改历史决策。

## 何时写一篇

写决策，当某个选择：

- 难以撤销 / 影响多个模块 / 有明显替代方案被否决；
- 或未来维护者会问「为什么不是 X？」。

不写：显而易见的最佳实践、纯局部实现细节（这些归 [[codemap/README]]）。

## 文件命名

`NNNN-kebab-slug.md`（4 位零填充），如 `0001-judge-outbox-and-generation-fencing.md`。编号单调递增、不复用。补充/演练篇用 `NNNNa-slug.md`。

## 模板

```markdown
---
title: NNNN — <决策标题>
tags: [decision, <子系统>]
status: proposed | accepted | superseded | deprecated
updated: YYYY-MM-DD
deciders: <角色/人>
superseded_by: <NNNN（若有）>
sources:
  - <迁移全文件名 / 代码模块路径>
---

# NNNN — <决策标题>

## 背景（Context）
为什么现在要决定？ forces / 约束 / 触发事件。

## 决策（Decision）
我们选了什么。一句话能说清。

## 替代方案（Alternatives）
被否决的选项 + 为什么否决。

## 后果（Consequences）
正面 / 负面 / 风险 / 缓解。

## 参考
相关迁移、实体页 [[...]]、概念页 [[...]]。
```

## 状态流转

`proposed`（评审中）→ `accepted`（生效，frozen）→ 若被取代 `superseded`（加 `superseded_by: NNNN`，新旧在 [[index]] 并存）。

## 现有决策

- [[0001-judge-outbox-and-generation-fencing]] — 判题为何用 outbox + generation fence
- [[0002-sandbox-d-form-hexagonal]] — 沙箱为何选 D-form + hexagonal
- [[0003-refresh-token-hash-only-storage]] — refresh token 为何 hash-only DB
- [[0004-notification-intent-and-delivery-ledger]] — 通知为何 intent 与 ledger 解耦
