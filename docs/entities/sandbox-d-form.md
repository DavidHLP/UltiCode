---
title: Sandbox D-form（D-form 沙箱）
tags: [entity, sandbox, judging]
status: living
updated: 2026-06-21
owner: sandbox
aliases: [沙箱, D-form, sandbox]
sources:
  - docker/sandbox/Dockerfile
  - docker/sandbox/harness/
  - docker/sandbox/harness/README.md
  - docker/sandbox/seccomp-profile.json
  - shared/sandbox-types/src/
  - backend-spring/src/main/java/com/ulticode/modules/submission/sandbox/
  - init-db/migrations/V20260616120000__Add_Problem_Resource_Limits.sql
---

# Sandbox D-form（D-form 沙箱）

> 执行用户代码、产出 verdict 的隔离运行时。**Form D** 是当前主力形态（harness 编译进镜像 + 反射派发），取代 legacy Form A。决策背景见 [[0002-sandbox-d-form-hexagonal]]。

## 四语言 harness（`docker/sandbox/harness/`）

| 语言 | 目录 | 成熟度 |
| --- | --- | --- |
| Java | `harness/java/` | ✅ 完整：反射调用 Solution、ListNode/TreeNode、per-case worker 线程 + 软 TLE、stdout 捕获 |
| Python | `harness/python/` | ✅ 完整：`inspect.signature` 注解做 ListNode/TreeNode 适配，镜像 Java 契约 |
| C++ | `harness/cpp/` | 🚧 骨架（smoke 二进制打空 envelope） |
| C | `harness/c/` | 🚧 骨架（C 无反射，需显式注册） |

## 三层构建（源 → staging → 镜像）

1. **源** `harness/{lang}/`
2. **host 预编译** → `harness/build.sh` 产出到 `harness-staging/`（host 已有 JDK17/21、Python3、gcc、g++；避免镜像里拉重工具链）
3. **镜像 COPY** staging 到 `/opt/harness/{lang}/`

> ⚠️ **改 harness 源必须重建**：`./docker/sandbox/harness/build.sh <lang>` 刷新 staging + 重建 `ulticode-sandbox-dform:phase2`。`build.sh` 用**固定文件清单** copy——新增模块（如 `_case_runner.py`）必须同时加进 `build_<lang>()` 的 cp 清单，否则镜像缺文件 → 每用例 RE。

## 镜像契约（`docker/sandbox/Dockerfile`）

- base = `ulticode-sandbox:base-17`（JDK-17 + python3 + gcc/g++，Debian bookworm，`sandbox` uid=1000）。**刻意不 `FROM :latest`**——`:latest` 是滚动 tag，会形成自依赖循环。
- 多阶段：builder 编译 harness → runtime `debian:bookworm-slim` 仅装运行时依赖。
- 运行时：用户代码 `/job/Solution.{ext}` + `/job/input.json`；stdout = 单个 JSON envelope；`exit 0` = envelope 有效，`exit 2` = harness panic（stderr 出栈，非用户输出）。
- 隔离：seccomp（`seccomp-profile.json`）+ 非 root uid 1000。

## Envelope 契约（`shared/sandbox-types/src/envelope.ts`）

```json
{
  "harness_version": "1.0",
  "language": "java | python | cpp | c",
  "exit_code": 0,
  "total_elapsed_ms": 245,
  "results": [
    { "case_id": "...", "label": "...", "status": "Accepted | Wrong Answer | Runtime Error | Time Limit Exceeded",
      "elapsed_ms": 12, "result": <jsonable>, "user_stdout": "", "user_stderr": "",
      "interrupted": true, "error": { "type": "...", "message": "...", "stack": ["..."] } }
  ]
}
```

- `interrupted` 仅 TLE 出现；`error` 仅 RE 出现，`stack` 剥离 harness 帧、只留用户帧。
- harness panic（解析错 / 无 Solution 类）→ stderr 栈 + `exit 2`，无 envelope。

## Verdict 集（`shared/sandbox-types/src/verdict.ts`）

- **D-form 原生**（5）：`Accepted` / `Wrong Answer` / `Runtime Error` / `Time Limit Exceeded` / `Compile Error`
- **含 legacy Form A 表述**（统一集，backend 映射）：另含 `Memory Limit Exceeded` / `Output Limit Exceeded` / `Presentation Error` / `System Error` / `Sandbox Error`（Form A daemon 级 fork 失败）/ `Judging` / `Pending`
- 后端把两种沙箱表述映射到同一组 [[submission]] 的 `SubmissionStatus`，前端无需区分。

## OJ 数据类型（`shared/sandbox-types/src/oj-type.ts`）

`int / long / double / boolean / String / int[] / int[][] / long[] / String[] / ListNode / ListNode[] / TreeNode / TreeNode[]`。Java 靠反射按 Solution 参数类型派发，Python 靠 `adapt_arg(value, hint, type_override)`。**加新类型必须 harness 真的支持**，否则 per-case 路径吃 RE。

## 后端集成（`submission/sandbox/`）

- `executor/SandboxExecutorImpl` —— 挂载 `/job/` 调 harness，解析 envelope。
- `LanguageProfile` port + `profile/{C,Cpp,Java,JavaScript,...}LanguageProfile` —— 每语言编译/运行命令策略（hexagonal，见 [[0002-sandbox-d-form-hexagonal]]）。
- `adapter/InMemorySandboxAdapter` —— 测试用。
- 资源限制来自 `V20260616120000__Add_Problem_Resource_Limits.sql`（题目级 time/memory limit）。

## 关联

- **为什么 D-form + hexagonal** → [[0002-sandbox-d-form-hexagonal]]
- **verdict 映射到提交状态** → [[submission]]
- **判题链路里的位置** → [[codemap/judging-pipeline]]
