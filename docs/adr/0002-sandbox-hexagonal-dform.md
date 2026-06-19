---
title: 沙箱六边形重构（D-form）
tags: [adr, sandbox, architecture]
status: accepted
updated: 2026-06-19
date: 2026-05-xx
deciders: architect
supersedes: N/A
superseded_by: N/A
---

# 0002 — 沙箱六边形重构（D-form）

## 背景

原版沙箱（2026-05 之前）是每种语言一个单文件 harness，通过 `tempfile` 做文件 I/O，和评测后端耦合得很紧。问题：

- 新增语言意味着在 3 个仓库（沙箱源、docker 镜像、后端 fork 逻辑）复制样板
- staging 逻辑泄漏进运行时镜像（构建巨大，迭代缓慢）
- 主机（3.14，PEP 649 惰性注解）和镜像（3.11，eager 注解）的 Python 版本漂移造成静默的测试差异

## 决策

沙箱组织为**三阶段构建管线**（源 → staging → 镜像），每种语言在 `build.sh` 里有**固定的文件清单**。运行时契约是：

- `input.json` — 声明式任务规格（题目、测试点、用户代码、限制）
- `verdict.json` — 结构化结果，除 `/job` 外不进行 I/O
- 单一入口：`/opt/harness/<lang>/main.py`（或 `.c` / `.cpp` / `Main.java`）

Python 用户代码**零 import** — harness 注入一个受控的前导段（typing + 纯计算标准库），用户永远看不到 `import os`。

## 备选方案

1. **sidecar 编译器**（在主机编译，只发二进制） — 拒绝：`cgroup + seccomp` 的安全保证只在编译也在沙箱内时成立
2. **只支持解释型语言**（砍掉 C/C++/Java） — 拒绝：必须匹配我们承诺给用户的比赛语言集合
3. **单镜像 + copy-on-write overlay** — 拒绝：镜像大 2-3 倍，缓存失效复杂

## 影响

**正面** — 新增语言只需在 `build.sh` 里写一个 `build_<lang>()` 函数；刷新镜像只需一行 CLI。沙箱的所有保障（cgroup、seccomp、禁用网络、只读根文件系统）统一生效。

**负面** — 给 harness 新增文件必须同时改 `build_<lang>()` 的 `cp` 清单**和** `.pyc` 循环，否则每个测试点都 RE。已记录在 `CODEMAPS/sandbox.md`。

**运维影响** — 当一道题所有测试点突然全部 RE，先看 harness 源和 staging 清单是否对得上：
```bash
./docker/sandbox/harness/build.sh python --no-docker   # 只刷新 staging
diff docker/sandbox/harness/python/ docker/sandbox/harness-staging/python/
```

## 参考

- **代码**：
  - `docker/sandbox/harness/`（源）
  - `docker/sandbox/harness/build.sh`（staging + 镜像编排）
  - `docker/sandbox/Dockerfile`
- **CODEMAPS**：[[sandbox]] § "Build Pipeline"、
  "Python Preamble Contract"、"Python Version Trap"
- **相关 ADR**：[[0001-verdict-status-codec]]（评测编解码）、[[0003-queue-outbox-fencing]]（outbox 围栏）
