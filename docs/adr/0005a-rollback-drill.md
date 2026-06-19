---
title: 回滚演练自动化
tags: [adr, devops, drill, automation]
status: accepted
updated: 2026-06-19
date: 2026-06-xx
deciders: devops
supersedes: N/A
superseded_by: N/A
---

# 0005a — 回滚演练自动化

## 背景

ADR-0005 要求**季度回滚演练**。没有自动化时，2026 Q1 跳过一次演练，因为没人记得排期。

## 决策

`scripts/adr-005/create-milestone-issues.sh` 提前为演练创建好 GitHub issues：

- 一个**里程碑 issue** 跟踪本季度演练窗口
- 每个 on-call 一个**任务 issue**（轮转），带 `rollback-drill` 标签
- 每个任务是一份清单：针对 staging 跑 `cd-rollback.yml`、记录耗时、把流程上的任何漏洞作为新 issue 提交

脚本是幂等的：同季度重跑不会重复创建 issue。它写一个 `last-run.json` 标记文件（gitignored）记录状态。

## 备选方案

1. **CI 里的 cron** — 拒绝：依赖 CI 的 cron 调度器，难以审计
2. **日历提醒** — 拒绝：人会忘
3. **靠 on-call 轮转强制** — 拒绝：交接成为失败点

## 影响

**正面** — 演练不再依赖某个人记得；漏洞在里程碑里可见。

**负面** — 一次性需要配一个 GitHub PAT（具有 `issues:write` 范围）；见脚本头注释。

**运维影响** — 如果脚本失败，on-call 手动开事故；失败模式是"演练跳过"，不是"部署挂了"。

## 参考

- **代码**：`scripts/adr-005/create-milestone-issues.sh`
- **相关 ADR**：`0005`（父决策）
