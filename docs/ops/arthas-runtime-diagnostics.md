---
title: Arthas 运行时诊断
tags: [ops, runbook, diagnostics, arthas]
status: living
updated: 2026-06-21
owner: ops
aliases: [Arthas, 运行时诊断]
sources:
  - tools/arthas-boot.jar
  - infrastructure/arthas/arthas.properties
  - scripts/start-arthas.sh
  - scripts/arthas-cli.sh
  - .claude/settings.json
  - ../../CLAUDE.md
---

# Arthas 运行时诊断

> Arthas（4.2.2）attach 到 Spring Boot JVM（9001），提供运行时诊断。项目把它包成 **MCP 服务**供 Claude Code 直接调用。本页记三路互斥、协议锁定、阻塞命令降级——命令清单见 [`CLAUDE.md`](../../CLAUDE.md)。

## MCP 端点

- `http://localhost:8563/mcp`（**STATELESS**，强制项目级约定）。
- Arthas agent 跑在目标 JVM（9001）内——9001 与 8563 共享 PID 是**预期**。

## 三路互斥（PM2 / hook / cli）

`scripts/start-arthas.sh` 由三路都能拉起，**任何一路先起来其他跳过**（端口 `:8563` + PID 文件 `.claude/.arthas/wrapper.pid` 双重检测，格式 `PID\nLAUNCHER`）：

| 路径 | 触发 | 退出时谁管 |
| --- | --- | --- |
| **PM2**（主） | `pm2 start ecosystem.config.cjs`（`ulticode-arthas` app） | pm2 自己 |
| **Claude hook** | `.claude/settings.json` `SessionStart/SessionEnd` | SessionEnd 只停 `launcher=hook` 的 |
| **CLI**（兜底） | `scripts/arthas-cli.sh start` | `arthas-cli.sh stop` 只停 `launcher=cli` 的 |

wrapper 自愈：监控 `:8563`，端口死了（如 `pm2 restart 9001`）自动重 attach。

## 协议锁定 STATELESS（关键）

arthas 4.2.2 默认 **STREAMABLE**，强制 `mcp-session-id` header；Claude Code 内置 MCP 客户端不维护 session → 阻塞命令持续收 4.4KB "Session ID required" 错误、看似超时。

- `infrastructure/arthas/arthas.properties` 锁 `arthas.mcpProtocol=STATELESS`。
- wrapper attach 前 `sync_arthas_properties()` 把它 diff 同步到**实际生效位置** `~/.arthas/lib/4.2.2/arthas/arthas.properties`（不是 `~/.arthas/arthas.properties`）。
- 改协议后须 `pm2 restart ulticode-9001` 触发重 attach。

## 阻塞命令降级路径（强制）

`dashboard` / `trace` / `watch` / `monitor` / `tt` 在 Claude Code 同步 MCP 里**固定 30s 超时**。遇到**不要重试**，按序降级：

1. **首选** `pm2 logs ulticode-9001 --nostream --lines 200` —— 同步拉最近 200 行，绝大多数性能/异常在此定位。
2. `--raw` 版 —— 含未格式化堆栈，便于与 `jad` 反编译类匹配。
3. `scripts/arthas-cli.sh` 进交互式 telnet（不受 MCP 30s 限制），跑 `dashboard -n 1` / `thread -n 3` / `trace <Class> <method> -n 3`。
4. 回退 `./mvnw -Dtest='*IT' test -B` 跑问题集成测试对照。
5. 同步 MCP 阻塞时，用 `ctx_execute` 跑 java 反射/grep 类检查（后台子进程，无 MCP 超时）。

阻塞命令必须带 `-n N`（N ≤ 5）限制执行次数（见 `.claude/rules/backend/09-java-runtime-diagnostics.md`）。增强命令并发模式：后台 bash `run_in_background=true` 持续 curl 触发目标端点（选没限流的 register/GET，login 是 60s/5 次窗口会被拦），同步发 `trace/watch/stack` 配 `numberOfExecutions=1` + `timeout=12`（MCP 客户端 30s，arthas 端永远 ≤25）。

## 典型场景

- 方法耗时过长 → `trace` + `monitor`
- 接口参数/返回值异常 → `watch`
- 类加载问题 → `sc` + `jad`
- 线程死锁 → `thread -b`
- 内存问题 → `dashboard` + `heapdump`

## 关联

- 命令清单与启动细节 → [`CLAUDE.md`](../../CLAUDE.md) 运行时调试章节
- ops 子目录约定 → [[README]]
