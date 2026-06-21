---
title: 运维深读（Ops）
tags: [ops, runbook]
status: living
updated: 2026-06-21
owner: ops
---

# 运维深读（Ops）

> 工具向与场景向的运维参考。区别于 `decisions/`（为什么）和 `codemap/`（代码长什么样）——本目录记「**怎么操作 / 怎么排障**」。

## 何时写进 ops

- 引入一个需要反复查阅用法的工具（如 Arthas MCP）。
- 出现一个值得固化的排障场景 / 降级路径。
- 某类故障的 on-call 手势值得沉淀。

## 与根文件分工

[`CLAUDE.md`](../../CLAUDE.md) / [`AGENTS.md`](../../AGENTS.md) 已含大量运维命令（PM2 / docker / Flyway / 启动顺序）。本目录只放**需要叙事展开**的深读，不复述根文件的命令清单。

## 现有运维页

- [[arthas-runtime-diagnostics]] — Arthas MCP 诊断 + 三路互斥 + 阻塞命令降级路径
