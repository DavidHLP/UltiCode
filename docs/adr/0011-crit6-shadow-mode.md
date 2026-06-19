---
title: CRIT-6 shadow mode 评估
tags: [adr, security, shadow, observability]
status: proposed
updated: 2026-06-19
date: 2026-06-xx
deciders: security-reviewer, backend
supersedes: N/A
superseded_by: N/A
---

# 0011 — CRIT-6 shadow mode 评估

## 背景

CRIT-6 是安全审查中标记的项目：新的鉴权逻辑缺少**shadow-mode** 路径 — 一种让新代码与老代码并行运行、比较结果但不影响生产鉴权的方式。

截至 2026-06-19，这一项仍处于**proposed**；生产鉴权仍是单体的，任何改动都是高风险。

## 决策（proposed）

**Shadow mode** 由一个部署开关（`SECURITY_SHADOW_MODE=true`）控制，它：

- 在每个请求上跑新鉴权路径，但结果**只观测、不生效**（老路径是真源）
- 发出每请求的指标：
  - `shadow.auth.verdict_match`（布尔）
  - `shadow.auth.verdict_diff`（分类型：A vs B）
- 在生产 ≥ 7 天绿指标后，新路径被切为生效，shadow mode 在后续发布中移除

新 ADR（`0012-shadow-mode-activation`）将记录激活，引用本篇的指标。

## 备选方案

1. **金丝雀发布** — 鉴权场景拒绝：金丝雀上错误的判定等于部分用户的错误判定；shadow mode 永远不影响活跃用户
2. **A/B 测试** — 拒绝：需要每用户 flag，且 secret 一旦泄露就绕过
3. **代码里的完整 feature flag** — 可行，但失去 shadow mode 设计的并行可观测性

## 影响

**正面** — 安全关键代码零风险发布；我们对每次偏离都有事后复盘级别的记录。

**负面** — 鉴权路径 CPU 翻倍；需要一个能扛 ~10k req/s 的指标管道。从安全收益看可以接受。

**运维影响** — 部署剧本（ADR-0005）多一项检查：受影响的路径下一次非 shadow 发布前，"shadow mode 无偏离天数"计数必须 ≥ 7。

## 参考

- **代码（proposed）**：`backend-spring/.../security/ShadowAuthDecorator.java`（目标位置）
- **安全审查**：`docs/security-review-2026-06-06.md`（待写）
- **相关 ADR**：[[0005-rolling-deploy-rollback]]（部署钩子）、[[0008-websocket-cookie-auth]]（WebSocket 鉴权 — 可能是这个模式的第一个消费者）
