---
title: Sandbox D-form（评测沙箱）
tags: [entity, sandbox, protocol]
status: living
updated: 2026-06-21
owner: backend
sources:
  - adr/0001-verdict-status-codec.md
  - adr/0002-sandbox-hexagonal-dform.md
  - adr/0003-queue-outbox-fencing.md
  - CODEMAPS/sandbox.md
  - CODEMAPS/architecture.md
aliases: [D-form 沙箱, 评测沙箱, 代码执行沙箱]
---

# Sandbox D-form（评测沙箱）

## 概述

D-form 沙箱是 UltiCode 评测管线末端的**隔离代码执行组件**。它以独立 Docker 容器（`ulticode-sandbox:latest`）运行用户提交的代码，逐测试点产出结构化 verdict（`AC`/`WA`/`TLE`/`MLE`/`RE`/`CE`/`SE`），由 Spring Boot 后端消费并写回 `submissions` + `submission_verdicts`。

沙箱与后端仅通过两个 JSON 文件耦合：

- `input.json` — 声明式任务规格（`problem_id`、`test_cases`、`user_code`、`time_limit_ms`、`memory_limit_kb`、`cpus`）
- `verdict.json` — 结构化结果，除 `/job` 外不进行任何 I/O

系统中的位置见 [[architecture]]（Sandbox runner，与 MySQL / Redis / Nacos 并列作为后端的四类依赖之一）：评测请求由 `console` POST `/submissions` 进入 → `SubmissionService` 写 `submissions` + `judge_outbox` 行 → 后台 `JudgeOutboxPoller` 租约（lease）认领 → fork 沙箱容器 → verdict 回调 → `submission_generation` / `submission_lease` 列守护 exactly-once。

支持语言：Python / C / C++ / Java。镜像名 `ulticode-sandbox-dform:phase2` 为中间构建产物，运行时 tag 为 `ulticode-sandbox:latest`（线上 `.env` 中 `SANDBOX_IMAGE=ulticode-sandbox:latest`）。

## 架构视角

### 六边形架构（ADR-0002）

原版沙箱（2026-05 之前）每种语言一个单文件 harness，通过 `tempfile` 做 I/O，与评测后端紧耦合，导致：新增语言需在 3 处复制样板；staging 逻辑泄漏进运行时镜像（构建巨大、迭代缓慢）；主机（Python 3.14，PEP 649 惰性注解）与镜像（Python 3.11，即时注解）版本漂移造成静默测试差异。

D-form 重构后沙箱组织为**端口-适配器**结构：核心契约是 `input.json` → `verdict.json` 的纯转换，每语言 harness 是一个可替换适配器。运行时单一入口固定为 `/opt/harness/<lang>/main.py`（或 `main.c` / `main.cpp` / `Main.java`）。

### 三阶段构建管线（源 → staging → 镜像）

```
docker/sandbox/harness/{python,c,cpp,java}/     ← 源（dev 时 mount）
                       │
                       ▼  ./docker/sandbox/harness/build.sh <lang>
docker/sandbox/harness-staging/                 ← staging（固定文件清单 cp）
                       │
                       ▼  docker build -f docker/sandbox/Dockerfile
ulticode-sandbox-dform:phase2                   ← intermediate
                       │
                       ▼  docker tag
ulticode-sandbox:latest                          ← runtime image
```

**关键约束：镜像打的是 staging，不是源。** 改 harness 源后必须重建：

```bash
./docker/sandbox/harness/build.sh python            # 刷新 staging + 重建 phase2 + tag latest
./docker/sandbox/harness/build.sh python --no-docker # 仅刷新 staging（不触发 docker build）
```

线上 `SANDBOX_IMAGE=ulticode-sandbox:latest`，重建后**新提交即时生效**（历史提交记录不变）。

### build.sh 固定文件清单

`build.sh` 用**固定文件清单** copy 源到 staging。新增 harness 模块（例如 `_case_runner.py`）必须**同时**：

1. 加进 `build_<lang>()` 的 `cp` 清单
2. 加进对应的 `.pyc` 循环

否则镜像缺文件 → 每个测试点 `RE`。这是 D-form 重构的**负面代价**，已记录在 ADR-0002 与 CODEMAPS/sandbox。

### Verdict Pipeline（端到端）

```
1. SubmissionController POST /submissions
   → SubmissionService 写：
       submissions 行（generation=N, lease=NULL, lease_expires_at=NULL）
       judge_outbox 行（status=PENDING）
2. JudgeOutboxPoller（后台 @Scheduled）
   → SELECT … FOR UPDATE SKIP LOCKED  （fencing）
   → UPDATE submissions SET generation=generation+1, lease=…, lease_expires_at=now+ttl
   → docker run --rm -e SOLUTION_DIR=/job -v <tmp>:/job ulticode-sandbox:latest \
       python3 /opt/harness/python/main.py /job/input.json
3. Sandbox harness（容器内）：
       - 读 input.json（problem_id, test_cases, user_code, time_limit_ms, memory_limit_kb, cpus）
       - 编译（c/cpp/java）
       - 逐 test_case：
           * cgroup 限制下 spawn subprocess（time/memory/cpus）
           * 捕获 stdout/stderr/exit_code/wall_time/peak_mem
           * 与 expected 归一化 diff
       - 输出 verdict.json
4. Harness 退出；JudgeOutboxPoller 解析 verdict.json
   → UPDATE submissions SET status=verdict, generation_used=…
   → INSERT submission_verdicts（逐测试点）
   → STOMP /topic/user/{id} + /topic/contest/{id} 推送
5. notification_intents 行（"verdict ready"）→ delivery ledger
```

沙箱本身**不**负责 outbox 围栏或 verdict 幂等写 —— 那是后端 `JudgeOutboxPoller` 的职责（ADR-0003）。沙箱只输出 `verdict.json` 即退出，所有 SQL 写入都在 poller 侧完成。

### 每语言 harness 布局（`/opt/harness/<lang>/`）

| Lang   | 关键文件                          | 备注                              |
| ------ | --------------------------------- | --------------------------------- |
| python | `main.py`, `runner.py`, `harness.py` | typing 预注入；**用户代码零 import** |
| c      | `main.c`, `runner.c`, `sandbox.h` | seccomp + rlimit                  |
| cpp    | `main.cpp`, `runner.cpp`, `sandbox.h` | cgroup memory cap                 |
| java   | `Main.java`, `Runner.java`        | 容器内 JDK 17                     |

### Verdict Status Codec（ADR-0001）

闭合 7 值枚举（两字母短码）。同一枚举在 `shared/sandbox-types`（TS）与沙箱 harness（Python）逐字重复，并用于数据库列 `submissions.status`、通知文案、结果表 —— 不存在"展开"形式。

| 码  | 含义       | UI 文案            |
| --- | ---------- | ------------------ |
| AC  | 通过       | 通过               |
| WA  | 答案错误   | 答案错误           |
| TLE | 超时       | 超出时间限制       |
| MLE | 超内存     | 超出内存限制       |
| RE  | 运行错误   | 运行错误           |
| CE  | 编译错误   | 编译错误           |
| SE  | 沙箱错误   | 沙箱错误           |

新增状态（如 `OLE` 输出超限）需后端 + harness + 前端 + 数据库协调改动，按 ADR 模板记录。

## Python Preamble 契约

Python 用户代码**零 import**。`harness.py::build_solution_preamble()` 在用户代码前注入一个受控前导段：

- `typing.__all__`（`List` / `Dict` / `Optional` / ...）
- 纯计算标准库：`heapq`、`math`、`bisect`、`itertools`、`functools`、`operator`、`string`、`fractions`、`decimal`、`statistics`、`re`、`collections`
- collections 高频符号：`deque`、`Counter`、`defaultdict`、`OrderedDict`、`namedtuple`
- 数据结构 stub：`ListNode`、`TreeNode`

**绝不注入**（破坏沙箱隔离）：`os`、`sys`、`subprocess`、`socket`、`shutil`、`ctypes`、`multiprocessing`。exit guard 只拦截 `_exit` / `sys.exit` —— 放行这些模块会让用户代码逃逸沙箱。

链表/树问题返回 `None`（空输入）会被 `normalize_return_value()` 规范化为 `[]`（LeetCode 约定）；diff 时不要按 `'null'` 处理。

## Python 3.11 版本陷阱

- 镜像 base：**Debian bookworm → Python 3.11**（类型注解**即时求值**）
- 主机可能是 3.14（PEP 649 惰性求值）
- 本地 `pytest` 可能"假通过"而镜像侧 3.11 失败（或反之）
- **改注解 / preamble 逻辑后必须用 `docker run` 在镜像内端到端验证**：

```bash
docker run --rm -e SOLUTION_DIR=/job -v "$TMP":/job ulticode-sandbox:latest \
  python3 /opt/harness/python/main.py /job/input.json
```

## 沙箱隔离机制

容器层保障（cgroup + seccomp + 网络禁用）：

- `pids_limit`（每容器）
- `memory.limit_in_bytes`（cgroup v1）/ `memory.max`（v2）
- `cpu.cfs_quota_us`（来自 `cpus` 列，迁移 `V20260616120000`）
- seccomp profile：仅允许 stdio + exit
- `--network=none`
- 只读根文件系统 + 可写 `/job` tmpfs

## 决策记录

- [[0001-verdict-status-codec|ADR-0001]] — verdict status codec（沙箱 ↔ 后端编解码；闭合 7 值枚举）
- [[0002-sandbox-hexagonal-dform|ADR-0002]] — 沙箱六边形重构（D-form；源→staging→镜像三阶段、Python preamble 契约）
- [[0003-queue-outbox-fencing|ADR-0003]] — 评测 outbox 围栏（`judge_outbox` + lease；沙箱消费侧的 exactly-once 语义来自这里）

沙箱消费 outbox 的围栏机制（`SELECT … FOR UPDATE SKIP LOCKED` + `generation` fencing token + `lease_expires_at` TTL 回收）在 [[exactly-once]] 实体页统一阐述；本页只关注沙箱容器内部。

## 已知行为与运维信号

### Troubleshooting Signals

| 信号                                                | 原因                                       |
| --------------------------------------------------- | ------------------------------------------ |
| 全部 `SE` + detail `Cannot fork`                    | 宿主机 / cgroup 压力                       |
| 全部 `SE` + detail `Unable to find image`           | 镜像缺失 —— 重建                           |
| 简单题全部 `TLE`                                    | 用户代码死循环 / 错误算法                  |
| **新增 harness 文件后全部 `RE`**                     | 该文件未加入 `build_<lang>()` 的 `cp` 清单 |

最后一行是 D-form 重构最常见的回归：每个测试点 `RE` 通常是镜像缺文件，而非真实评测失败。排查流程：

```bash
./docker/sandbox/harness/build.sh python --no-docker   # 只刷新 staging
diff docker/sandbox/harness/python/ docker/sandbox/harness-staging/python/
```

源目录与 staging 目录不对齐 = `build_<lang>()` 清单漏了新文件。

### 误报排查

当评测结果疑似误报时，同时核对 `submission_verdicts.status` 与 harness 的 `verdict.json` —— 它们**必须**一致。不一致意味着编解码漂移 bug（TS 枚举与 Python 枚举不同步），不是真实评测失败。

### 镜像刷新

```bash
./docker/sandbox/harness/build.sh python        # 刷新 staging + 重建镜像
./docker/sandbox/harness/build.sh python --no-docker   # 仅刷新 staging
# 运行时 SANDBOX_IMAGE=ulticode-sandbox:latest in .env
# 重建后新提交即用新镜像（历史提交不变）
```

## 参考

- **代码**：
  - `docker/sandbox/harness/{python,c,cpp,java}/` — harness 源
  - `docker/sandbox/harness/build.sh` — staging + 镜像编排（固定文件清单）
  - `docker/sandbox/Dockerfile` — 镜像构建（COPY staging → `/opt/harness/<lang>/`）
  - `shared/sandbox-types/` — TS 枚举（`verdict`、`test_result`），与 harness 逐字重复
  - `backend-spring/.../infrastructure/JudgeOutboxPoller.java` — 消费侧（fork 沙箱 + 写 verdict）
  - `backend-spring/.../submission/service/SubmissionService.java` — 写 `submissions` + `judge_outbox`
  - `backend-spring/.../submission/verdict/...` — verdict 归一化
- **迁移**：
  - `init-db/migrations/V20260613100000__Create_Judge_Outbox.sql`
  - `init-db/migrations/V20260613110000__Add_Submission_Generation_And_Lease.sql`
- **CODEMAPS**：[[sandbox]]（§ Build Pipeline / Verdict Pipeline / Python Preamble Contract / Python Version Trap / Troubleshooting Signals）、[[architecture]]（§ Data Flow — Submission judge）
- **相关实体**：[[submission]]（提交生命周期）、[[exactly-once]]（outbox 围栏机制）
