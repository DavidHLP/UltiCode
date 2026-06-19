---
title: 滚动部署 + 回滚演练
tags: [adr, devops, deploy, rollback]
status: accepted
updated: 2026-06-19
date: 2026-06-xx
deciders: devops, architect
supersedes: N/A
superseded_by: N/A
---

# 0005 — 滚动部署 + 回滚演练

## 背景

后端最初作为单个 `docker run` 直接对运行中的 MySQL 部署。回滚意味着手动 `docker stop` + 撤销 commit + 再 `docker run`，没有文档化流程。我们当时：

- 镜像发布到流量切换之间没有冒烟测试
- 没有"已知良好"标签可以回滚
- 没有文档化的 on-call 剧本 — 第一次回滚用了 47 分钟

## 决策

采用**带冒烟门控的滚动部署**，加上**每季度预演**的回滚：

- **镜像晋升**：`ghcr.io/.../backend:<sha>` 是唯一不可变引用；`:latest` 不可部署
- **冒烟门控**：`cd-deploy.yml` 在任何流量切换前，对已 stage 的实例跑 `./mvnw -Dtest='*IT' test -B` 和 `curl -fsS http://localhost:9001/auth/me`
- **回滚演练**：`scripts/adr-005/create-milestone-issues.sh` 为每季度回滚演练生成 GitHub issues；`RUNBOOK.md` §5.3 引用 `cd-rollback.yml`
- **回滚触发**：{SEV-1、prod 冒烟门控失败、错误率 5 分钟内 > 1%} 任一即触发

## 备选方案

1. **通过独立环境的蓝绿** — 当前拒绝：基础设施成本翻倍；我们还没到需要的规模
2. **ArgoCD 风格的 GitOps** — 拒绝：当前团队的运维面太大
3. **手动 `docker stop` + `git checkout`** — 原版的问题

## 影响

**正面** — 回滚是一行命令（`gh workflow run cd-rollback.yml -f ref=<sha>`），带冒烟门控；on-call 不再在压力下即兴发挥。

**负面** — 季度演练是一个持续的税（每次约 2 小时工程师时间）；值得，因为我们在第一次事故中实测 47 分钟。

**运维影响** — 演练 issue 由 `scripts/adr-005/create-milestone-issues.sh` 自动创建；处理人在 on-call 中轮转。SLA 内未完成会升级到 devops 负责人。

## 参考

- **代码**：`scripts/adr-005/create-milestone-issues.sh`
- **工作流**：`.github/workflows/cd-deploy.yml`、`.github/workflows/cd-rollback.yml`
- **CODEMAPS**：[`architecture.md`](../CODEMAPS/architecture.md) § "Security Boundaries"
- **RUNBOOK**：[`RUNBOOK.md`](../RUNBOOK.md) §5（回滚）、§9（升级）
- **相关 ADR**：`0005a`（演练自动化）、`0011`（CRIT-6 shadow mode — 用到部署钩子）
