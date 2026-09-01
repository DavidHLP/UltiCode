# Contest i18n 同步审计报告 (R10.3)

> **作用**：R10.3 产物 —— 跨端 en-US / zh-CN i18n locale key 一致性审计
> **创建**：2026-06-17
> **关联**：[_archive/EXECUTION_PLAN_R10 §4](./_archive/EXECUTION_PLAN_R10_2026-06-18.md)

## 方法

1. **management 端**（已有 `validate:i18n-keys` 脚本）：跑 `pnpm validate:i18n-keys` 检查 358 个 Vue 组件的 i18n key 引用
2. **console 端**（无内置 validator）：用 Python 脚本手动 diff `console/src/i18n/locales/en-US/contest.ts` 与 `zh-CN/contest.ts` 的 top-level + nested keys
3. **management contest 模块**：`management/src/i18n/locales/en-US/modules/contests.ts` vs `zh-CN/modules/contests.ts` 同上

## 验收结果

### management 全局（pnpm validate:i18n-keys）

```
> admin-frontend@0.0.0 validate:i18n-keys
> node scripts/validate-i18n-keys.js

Checking i18n keys...

Found 358 Vue component(s)
Found 23 problems.versionHistory.* key(s) in components
All keys found! ✓
```

**结论**：✅ **0 缺失**。23 个 `problems.versionHistory.*` 引用全部在 locale 中有定义。

### console/contest.ts (top-level)

| | en-US | zh-CN | 一致 |
|---|---|---|---|
| Top-level keys | 22 | 22 | ✅ |
| 文件行数 | 295 | 284 | — |

22 个 top-level keys 全部对齐。

### console/contest.ts (nested)

| 指标 | 数量 |
|---|---|
| en-US nested keys | 187 |
| zh-CN nested keys | 181 |
| 表面 EN-only | 6 (false positive) |
| 实际漂移 | **0** |

**EN-only 误报**（regex 把 value 侧的英文误识别为 key）：

| "Key" | 实际位置 | 说明 |
|---|---|---|
| `Duration` | en-US L31 / L77 | value: `"Duration"` —— UI label |
| `Rated` | en-US L28 / L70 | value: `"Rated"` / `"Rated:"` |
| `Remaining` | en-US L27 | value: `"Remaining:"` |
| `Score` | en-US L120 / L126 | value: `"Score"` |
| `Time` | en-US L127 | value: `"Time"` |
| `ID` | (无实际匹配，regex 误捕) | — |

**修正审计**：用更严格 regex（要求 key 不在引号内）可消除全部误报，**实际 0 漂移**。

### management/contests.ts

| | en-US | zh-CN | 一致 |
|---|---|---|---|
| Top-level keys | 24 | 24 | ✅ |
| Nested keys | 196 | 196 | ✅ |

完全对齐。

## 漂移清单

**0** 个真漂移 key。

## 风险标注

### 1. console 端缺自动化 validator

console 项目**没有** `validate:i18n-keys` 脚本，所有 i18n 检查依赖手工。**建议**（非 R10 强制）：

- 把 management 的 `scripts/validate-i18n-keys.js` 移植到 console（或抽到 `shared/`）
- 添加到 console 的 `pnpm lint` / pre-commit 钩子

### 2. R9_PLACEHOLDER.ts 删除后回归验证

R9.3 删除了 `console/src/i18n/R9_PLACEHOLDER.ts`（[commit 2c92acd6a](https://github.com/...)），将 keys 写回 `contest.ts`。当前 status（2026-06-18 验证）：

```
D console/src/i18n/R9_PLACEHOLDER.ts  (R9.3 已删,文件不存在)
A console/src/i18n/locales/{en-US,zh-CN}/contest.ts  (R9 已写回)
```

**收口状态**：R9.3 删除动作已与 R9 工作流一并提交，无遗留 untracked 文件。

## R10.3 收口结论

| 维度 | 状态 |
|---|---|
| en-US / zh-CN top-level 一致 | ✅ |
| en-US / zh-CN nested 一致 | ✅ |
| management 358 components key 完整 | ✅ |
| console 漂移 key | **0** |

**R10.3 收口**：无 R10.2 finding 增量（参见 R10.2 计划误判说明）。

## R10 后续 i18n 任务

- **R10.2 plan 误判**（2026-06-18 验证）：R10.2 假设需手动接线 4 个 view（ContestBrowseView / ContestRankingsView / MyContests / WS banner），但 R9 阶段已用业务命名空间（`contest.list.*` / `contest.myContests.*` / `contest.ranking.*`）完成 i18n 接线；9 个未引用的 key 是死键（locale 有但 view 零引用）。详见 [_archive/EXECUTION_PLAN_R10 §3](./_archive/EXECUTION_PLAN_R10_2026-06-18.md) 误判说明
- 死键处置（非 R10 强制）：
  - 选项 A：保留（无引用是观察性的，对未来扩展无害）
  - 选项 B：从 locale 文件删除（精简），需 R10.x 单独 PR
- R9.3 banner 缺陷（`ContestRankingsView.vue:28` `showReconnecting` ref 模板未渲染）属 R9 收口漏网，与 i18n 接线正交，独立小修复可作为 R10.x 候选

## 审计脚本

保留此 Python 脚本供后续 R10.x / R11 复用：

```python
import re
from pathlib import Path

def top_level_keys(path):
    if not path.exists(): return set(), 0
    content = path.read_text()
    m = re.search(r'export\s+default\s*\{', content)
    if not m: return set(), 0
    rest = content[m.end():]
    keys = set()
    for line in rest.split('\n'):
        km = re.match(r'^\s*([a-zA-Z][a-zA-Z0-9_]*)\s*:\s*\{', line)
        if km: keys.add(km.group(1))
    return keys, len(content.split('\n'))
```
