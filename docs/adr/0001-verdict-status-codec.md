---
title: 评测状态码（沙箱 ↔ 后端）
tags: [adr, sandbox, protocol]
status: accepted
updated: 2026-06-19
date: 2026-05-xx
deciders: architect, java-reviewer
supersedes: N/A
superseded_by: N/A
---

# 0001 — 评测状态码（沙箱 ↔ 后端）

## 背景

评测结果由一个 out-of-process 沙箱容器产出，被 Spring Boot 后端消费。传输协议必须：

- **稳定** — 新增评测状态是非破坏性变更
- **紧凑** — `verdict.json` 逐测试点解析，热路径
- **UI 可映射** — 短码出现在审核 UI、比赛排行榜、E2E 测试里

## 决策

我们使用**闭合的 7 值枚举**，全是两个字母。该编解码在 `shared/sandbox-types`（TypeScript）和沙箱 harness（Python）里逐字重复。同一个枚举也用在数据库列（`submissions.status`）、通知文案、结果表里 — 不存在"展开"形式。

| 码 | 含义      | UI 文案            |
| ---- | ------------ | ------------------- |
| AC   | 通过     | 通过            |
| WA   | 答案错误 | 答案错误        |
| TLE  | 超时   | 超出时间限制 |
| MLE  | 超内存 | 超出内存限制 |
| RE   | 运行错误  | 运行错误       |
| CE   | 编译错误  | 编译错误       |
| SE   | 沙箱错误  | 沙箱错误       |

## 备选方案

1. **开放字符串 + 任意标签** — 拒绝：会把校验逻辑散到 3 种语言和 SQL 层
2. **HTTP 风格数字码**（200/300/400/500） — 拒绝：和 Spring 自己的状态码冲突；UI 文案难本地化
3. **长名（`WRONG_ANSWER`）** — 拒绝：`verdict.json` 存储翻倍，日志噪音翻倍

## 影响

**正面** — 单一真源（`shared/sandbox-types`），UI 拿到稳定字符串做 i18n，归一化逻辑（`normalize_return_value()`）只需处理 7 种情况。

**负面** — 新增评测状态（如 `OLE` 表示输出超限）需要后端、harness、前端、数据库协调改动。遵循 ADR 模板记录新增。

**运维影响** — 当评测结果误报时，同时核对 `submission_verdicts.status` 和 harness 的 `verdict.json`；它们**必须**一致。不一致意味着编解码漂移 bug，不是真实评测失败。

## 参考

- **代码**：
  - `shared/sandbox-types/`（TS 枚举：`verdict`、`test_result`）
  - `docker/sandbox/harness/{python,c,cpp,java}/` — 产出 `verdict.json`
  - `backend-spring/.../submission/verdict/...` — 消费侧归一化
- **CODEMAPS**：[[sandbox]] § "Verdict Status Codec"
- **相关 ADR**：[[0002-sandbox-hexagonal-dform]]（D-form 沙箱）、[[0003-queue-outbox-fencing]]（outbox 围栏）
