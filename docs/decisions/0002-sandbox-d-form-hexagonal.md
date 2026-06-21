---
title: 0002 — 沙箱选 D-form + Hexagonal
tags: [decision, sandbox, judging]
status: accepted
updated: 2026-06-21
deciders: architect
sources:
  - docker/sandbox/Dockerfile
  - docker/sandbox/harness/README.md
  - shared/sandbox-types/src/
  - backend-spring/src/main/java/com/ulticode/modules/submission/sandbox/
---

# 0002 — 沙箱选 D-form + Hexagonal

## 背景（Context）

OJ 沙箱要在隔离环境里跑用户代码、按 LeetCode 风格（Solution 方法 + JSON I/O + ListNode/TreeNode）产出 verdict。旧 **Form A**（daemon 级 fork）与新 **Form D**（harness 编译进镜像 + 反射派发）需择一为主力，且要支持多语言可扩展、可测试。

## 决策（Decision）

1. **D-form 为主力**：harness（java/python 完整 + cpp/c 骨架）host 预编译 → staging → 镜像 `/opt/harness/{lang}/`，envelope JSON 契约（见 [[sandbox-d-form]]）。
2. **Hexagonal ports & adapters**：`LanguageProfile` port + 每语言 `{C,Cpp,Java,JavaScript}LanguageProfile` adapter；`SandboxExecutor` + `InMemorySandboxAdapter`（测试）。后端 `CodeExecutionHelperImpl` 把 D-form 与 legacy Form A 的 verdict 表述统一映射到同一 `SubmissionStatus` 集（`shared/sandbox-types/verdict.ts`），前端无需区分。
3. base image `ulticode-sandbox:base-17`，刻意不 `FROM :latest`（避免自依赖循环）。

## 替代方案（Alternatives）

- **保留 Form A**：daemon 级 fork 在 pids-limit 下会 `Sandbox Error`，且无 per-case 结构化结果。否决作主力，保留为 fallback 表述。
- **镜像内多阶段编译 harness**：每次构建拉重工具链（maven/gcc/g++/python）。否决——host 已有工具链，host 预编译更快、构建上下文更小。
- **每语言硬编码到 executor**：加语言要改核心。否决——hexagonal 让新语言只加一个 `LanguageProfile` adapter。

## 后果（Consequences）

- ✅ 多语言可扩展（加语言 = 加 harness 目录 + 一个 `LanguageProfile`），可测试（InMemory adapter）。
- ✅ verdict 统一，前端无感。
- ⚠️ harness 改动必须重建镜像（`build.sh` 固定文件清单，新增模块要同步加 cp 清单 + .pyc 循环，否则镜像缺文件 → 每用例 RE）。
- ⚠️ Python 镜像 base 是 3.11，类型注解即时求值；主机可能是 3.14（惰性求值），本地 pytest 可能假通过——改注解/preamble 必须用 `docker run` 在镜像内端到端验证。

## 参考

- 实体全景 → [[sandbox-d-form]]
- 判题链路位置 → [[codemap/judging-pipeline]]
