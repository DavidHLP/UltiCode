---
title: Submission（提交）
tags: [entity, submission, lifecycle, protocol]
status: living
updated: 2026-06-21
owner: backend
sources:
  - adr/0001-verdict-status-codec.md
  - adr/0003-queue-outbox-fencing.md
  - adr/0005a-rollback-drill.md
  - CODEMAPS/architecture.md
  - CODEMAPS/backend.md
  - CODEMAPS/data.md
  - CODEMAPS/sandbox.md
aliases: [提交, 判题提交]
---

# Submission（提交）

## 概述

一次 **submission（提交 / 判题提交）** 是用户对一道题的一次解答提交，经判题流水线产出 **verdict（评测结果）**。它是 OJ 平台的核心写入实体：用户代码进入系统后，先在 `submissions` 表落盘，再通过 outbox + lease 围栏机制被沙箱 worker 认领、在隔离容器中执行，最终以闭合 7 值 verdict 码回到数据库与 UI。

判题是一个**异步、幂等、exactly-once verdict** 的管线：提交写入即返回，verdict 由后台轮询器产出并回调。设计目标是单 MySQL 实例即可应对 10k 提交/天，无需外部 broker、无需 2PC、无需 dedup 表。

## 架构视角

### 数据层（`submissions` + `judge_outbox` + `submission_verdicts`）

来自 [[CODEMAPS/data]] § "Submissions & Judging"：

- `submissions` —— 提交主表，承载用户代码、题目、语言、`status`（verdict 终态）等。`submissions.status` 使用与 verdict 同一套闭合枚举（见下），PENDING 为流水线中间态。
- `judge_outbox` —— 事务性 outbox 行，与 `submissions.insert` 在**同一事务**里写入（[[0003-queue-outbox-fencing|ADR-0003]]）。背景 `JudgeOutboxPoller` 在其上轮询认领。
- `submission_verdicts` —— 每个测试点的逐点结果，由 worker 解析 `verdict.json` 后写入；`submissions.status` 为整题聚合状态。

**防重列（fencing，ADR-0003）**——`submissions` 表新增的 3 列，是 exactly-once verdict 的关键：

- `generation`（即 ADR-0003 所述 `submission_generation` 列）—— 围栏 token，worker 认领时 `generation=generation+1`；verdict 写入对 `(submission_id, generation)` 幂等。
- `lease` / `submission_lease` —— 当前持有该提交评测权的 worker id。
- `lease_expires_at` —— lease 的 TTL；过期 lease 由 TTL 清理器回收，使崩溃 worker 不再永久阻塞提交。

Schema 真源迁移：
- `init-db/migrations/V20260613100000__Create_Judge_Outbox.sql`
- `init-db/migrations/V20260613110000__Add_Submission_Generation_And_Lease.sql`

### 模块层（submission module + infrastructure worker）

来自 [[CODEMAPS/backend]]：

- **REST 入口** —— `modules/submission/` 暴露 `/submissions` 与 `/problems/{id}/submissions`。
- **`SubmissionService`** —— 写 `submissions` 行 + `judge_outbox` 行（同一事务），是 outbox 模式的写入侧。
- **`JudgeOutboxPoller`**（`infrastructure/`）—— 后台轮询器：`SELECT … FOR UPDATE SKIP LOCKED` 认领 outbox 行，`UPDATE … SET generation=generation+1, lease=<worker_id>, lease_expires_at=now+ttl`，然后 fork 沙箱容器、回收 verdict.json、回写 `submissions.status` + `submission_verdicts`，并经 STOMP `/topic/user/{id}` 与 `/topic/contest/{id}` 推送。
- **`SandboxRunnerService`** —— fork 沙箱容器、轮询 verdict。

### 判题完整链路

来自 [[CODEMAPS/architecture]] § "Submission judge" 与 [[CODEMAPS/sandbox]] § "Verdict Pipeline"：

```
console POST /submissions
  → SubmissionService 写 submissions(generation=N, lease=NULL) + judge_outbox(PENDING)
  → JudgeOutboxPoller @Scheduled
       SELECT … FOR UPDATE SKIP LOCKED          (围栏)
       UPDATE submissions SET generation=+1, lease=…, lease_expires_at=now+ttl
       docker run --rm … ulticode-sandbox:latest python3 /opt/harness/<lang>/main.py /job/input.json
  → 沙箱 harness（容器内）：读 input.json → 编译 → 逐测试点 spawn + 限额执行 + diff → emit verdict.json
  → harness 退出；JudgeOutboxPoller 解析 verdict.json
       UPDATE submissions SET status=<verdict>
       INSERT submission_verdicts (逐点)
       STOMP /topic/user/{id} + /topic/contest/{id}
  → notification_intents 行（"verdict ready"）→ notification_delivery_ledger
```

沙箱如何消费提交、`verdict.json` 的形态、harness 契约（零 import 用户代码、preamble 注入、`normalize_return_value()`）见 [[sandbox-dform]]（即 [[0002-sandbox-hexagonal-dform|ADR-0002]] 与 [[CODEMAPS/sandbox]]）。

### Verdict 状态码（7 值闭合枚举）

来自 [[0001-verdict-status-codec|ADR-0001]] —— 全是两个字母、闭合 7 值，在 `shared/sandbox-types`（TS）、沙箱 harness（Python）、数据库列（`submissions.status`）、通知文案、结果表中**逐字重复**，不存在"展开形式"。

| 码   | 含义         | UI 文案             |
| ---- | ------------ | ------------------- |
| AC   | 通过         | 通过                |
| WA   | 答案错误     | 答案错误            |
| TLE  | 超时         | 超出时间限制        |
| MLE  | 超内存       | 超出内存限制        |
| RE   | 运行错误     | 运行错误            |
| CE   | 编译错误     | 编译错误            |
| SE   | 沙箱错误     | 沙箱错误            |

> 编解码漂移排查：当 verdict 误报时，同时核对 `submission_verdicts.status` 与 harness 产出的 `verdict.json`，二者**必须**一致；不一致是编解码漂移 bug，不是真实评测失败（ADR-0001 运维影响）。

## 决策记录

- [[0001-verdict-status-codec|ADR-0001]] —— verdict 状态码编解码（沙箱 ↔ 后端）。7 值闭合枚举；新增状态（如 `OLE`）需后端 / harness / 前端 / DB 协调，按 ADR 模板记录。
- [[0003-queue-outbox-fencing|ADR-0003]] —— outbox + 行级 lease 围栏，防"重复评测"与"提交丢失"。`submission_generation` 列即围栏 token；过期 lease 由 TTL 清理器回收；迁移 `V20260613110000__Add_Submission_Generation_And_Lease` 是 schema 真源（被前滚则轮询器停滞，走标准 Flyway 前向修复）。
- [[0005a-rollback-drill|ADR-0005a]] —— 回滚演练自动化。提交状态恢复不在此 ADR 直接描述，但其季度演练流程（`scripts/adr-005/create-milestone-issues.sh`，每个 on-call 一个带 `rollback-drill` 标签的任务 issue）是 verdict 流水线 schema 变更（如 ADR-0003 迁移）安全回滚的兜底演练机制。

## 已知行为与运维信号

来自 [[RUNBOOK]]：

- **全 SE 故障**（§4.5）—— 当所有提交产 `SE`：`Cannot fork` → 宿主机/cgroup 压力（查 `sysctl kernel.pid_max`、`pids.current`）；`Unable to find image` → 沙箱镜像缺失（`docker build -t ulticode-sandbox:latest -f docker/sandbox/Dockerfile docker/sandbox/`）；加新 Python 文件后全 `RE` → 未进入 `build_<lang>()` cp 清单（`./docker/sandbox/harness/build.sh python` 重建）。
- **查最近提交状态**（§快速诊断）——
  ```bash
  docker exec -e MYSQL_PWD="$DB_PASSWORD" ulticode-mysql \
    mysql --default-character-set=utf8mb4 -u "$DB_USER" "$DB_NAME" \
    -e "SELECT id, status, created_at FROM submissions ORDER BY created_at DESC LIMIT 10;"
  ```
- **卡在 PENDING 的提交** —— ADR-0003 描述的失败模式：worker 在 fork 与 verdict 写之间崩溃，`submissions.status` 永久卡 PENDING。恢复路径是 TTL 清理器回收过期 `lease_expires_at`，使行被重新认领。（具体查 PENDING 行的 SQL 与清理命令在 [[RUNBOOK]] 未直接列出 —— 待核实，可参考 ADR-0003 描述的 `lease_expires_at` 回收机制。）

## 参考

- **代码**：`backend-spring/src/main/java/com/ulticode/modules/submission/`、`backend-spring/.../infrastructure/JudgeOutboxPoller.java`、`backend-spring/.../submission/service/SubmissionService.java`
- **迁移**：`init-db/migrations/V20260613100000__Create_Judge_Outbox.sql`、`init-db/migrations/V20260613110000__Add_Submission_Generation_And_Lease.sql`
- **沙箱消费侧**：`docker/sandbox/harness/{python,c,cpp,java}/`
- **CODEMAPS**：[[CODEMAPS/architecture]] § "Data Flow"、[[CODEMAPS/backend]] § "Background Workers"、[[CODEMAPS/data]] § "Submissions & Judging"、[[CODEMAPS/sandbox]] § "Verdict Pipeline"
- **交叉引用**：[[sandbox-dform]]（沙箱消费）、[[exactly-once]]（verdict 幂等语义）、[[contest]]（比赛内提交与计分）、[[0001-verdict-status-codec|ADR-0001]]、[[0003-queue-outbox-fencing|ADR-0003]]、[[0005a-rollback-drill|ADR-0005a]]
