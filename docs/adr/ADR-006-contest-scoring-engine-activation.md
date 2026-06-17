# ADR-006: Contest 评分引擎激活 — penalty 配置化与 SCORE 分支定义

| 字段 | 值 |
|------|-----|
| **状态 (Status)** | **Proposed** (待评审 + 实施验证) |
| **日期 (Date)** | 2026-06-17 |
| **作者 (Author)** | DavidHLP |
| **来源** | [REVIEW_V3.md](../contest/REVIEW_V3.md) §3 P0-1 / F-02 验收标准 |
| **执行计划** | [EXECUTION_PLAN.md Round 4](../contest/EXECUTION_PLAN.md) |
| **关联代码** | `contest/service/impl/ContestScoringServiceImpl.java` (L176 penalty 硬编码 / L111-114 重复AC / L165-175 首杀原子), `contest/entity/Contest.java` (L51 `penaltyPerWrong`), `contest/entity/enums/ContestScoringMode.java`, `contest/service/impl/ContestServiceImpl.java` (L512 scoringMode 入口) |
| **关联 DB** | 无 schema 变更（字段已存在）；仅运行时行为 |

---

## 1. Context

### 1.1 现状（REVIEW_V3 已核实）

评分引擎是竞赛模块的核心，但当前**事实上未生效**——这是 REVIEW_V3 给出"不建议合入"裁决的首要阻断项（F-02 验收标准 ⭐⭐⭐⭐）：

| 位置 | 现状 | 问题 |
|------|------|------|
| `Contest.java:51` | `private Integer penaltyPerWrong;`（包装类型，**无默认值**，可为 null） | 配置字段已存在，但… |
| `ContestScoringServiceImpl.java:176` | `int penalty = 20;`（硬编码字面量） | **完全忽略 `contest.getPenaltyPerWrong()`**——配置字段是死字段，运营无法调罚时 |
| `ContestScoringMode.java` | 枚举值 `SCORE, ICPC, IOI` | 三模式枚举存在，但评分入口是否三分支正确分发**未验证** |
| `ContestScoringServiceImpl.java:111-114` | 重复 AC 保护 | 已存在（确认生效） |
| `ContestScoringServiceImpl.java:165-175` | 首杀原子性（条件更新） | 已存在（确认生效） |

### 1.2 为什么是 ADR（三个条件均满足）

1. **难逆转** — 评分规则上线后，已结算的 `final_rank` / `total_score` 口径会固化；改回硬编码或换 SCORE 语义需重新结算，扰动历史排名。
2. **令人意外** — `penaltyPerWrong` 字段存在于实体却被硬编码 `20` 忽略，未来读者会困惑"为什么有字段不用"。SCORE 模式的语义在本项目无既有定义。
3. **真实权衡** — 硬编码（简单）vs 配置化（灵活）；SCORE 语义有多种合理解读（累加分 / 最高分 / AC 数），需定档。

---

## 2. Decision

### 2.1 penalty 配置化 + null 兜底

```java
// ContestScoringServiceImpl.java:176 替换
// 原: int penalty = 20;
int penalty = contest.getPenaltyPerWrong() != null
    ? contest.getPenaltyPerWrong()
    : 20;  // null 兜底，与当前硬编码值一致 → 零行为回归
```

**null 兜底选 20 的理由**：当前所有历史 contest 都按 `penalty=20` 结算，兜底 20 保证未配置的 contest 行为不变。若兜底改 0，历史 contest 复算时罚时清零，不可接受。

### 2.2 评分模式三分支语义定档

追溯评分入口 `ContestServiceImpl:512 contest.getScoringMode()`，按 `ContestScoringMode` 分支：

| 模式 | 语义（本项目定档） | 排名键 |
|------|-------------------|--------|
| **ICPC** | 罚时制：AC 后罚时 = `错误提交数 × penaltyPerWrong` + AC 耗时（分钟）；未 AC 题不计分但错误提交计数 | 解题数 ↓，罚时 ↑ |
| **IOI** | 每题取**最高分**（多次提交取分最高那次）；总分 = 各题最高分之和 | 总分 ↓ |
| **SCORE** | 按 `problem.score` 累加的**简化总分制**（AC 即得该题满分，不取最高次，不扣罚时）；区别于 IOI 的"逐次取最高"和 ICPC 的"罚时" | 总分 ↓ |

**SCORE 为何如此定义**（权衡记录）：
- 候选 A：累加分（AC 得满分）← **选定**。简单、可预测，适合"练习赛 / 积分赛"场景。
- 候选 B：等同 IOI（逐次最高分）。则 SCORE 与 IOI 语义重复，枚举冗余。
- 候选 C：AC 数。则与 ICPC 的"解题数"维度重复。
- 选 A：让 SCORE 成为"无罚时、AC 即满分"的独立模式，与 ICPC（有罚时）/ IOI（取最高分）正交。

### 2.3 历史数据不复算

- 评分引擎激活后，**仅对新提交生效**。
- **不做**历史 contest 批量复算——口径变更可能扰动已公示的排名/积分，引发争议。
- 若未来确需复算，提供**可选脚本**（默认不跑，需运营显式触发 + 备份）。

### 2.4 灰度策略（可选）

若担心上线风险，引入 feature flag `contest.scoring.engine.v2`（默认 true）：
- `true`：走配置化 + 三分支
- `false`：回退硬编码 `penalty=20` + 原分支（逃生通道）

> 鉴于 null 兜底已保证零回归，**默认可不引入 flag**（YAGNI）；若评审认为评分改动需灰度，再加。

---

## 3. Consequences

### 3.1 Positive

- 评分真正生效（解除 F-02 阻断）
- 运营可按赛事调 `penaltyPerWrong`（练习赛 0、正式赛 20、严格赛 30）
- 三模式语义明确、正交，无歧义
- null 兜底保证零行为回归

### 3.2 Negative

- SCORE 语义是本项目自定义（非标准 ICPC/IOI），需在 ADR + PRD 文档化，否则未来读者误解
- 评分逻辑复杂度上升（三分支 + null 兜底），需充分单测

### 3.3 Risks

| 风险 | 缓解 |
|------|------|
| `penaltyPerWrong` 为 null 时 NPE | null 兜底 20（§2.1） |
| SCORE 分支实现与 IOIC/ICPC 混淆 | 三模式单测矩阵强制覆盖（§4） |
| 历史排名口径被误改 | 不复算（§2.3）；新提交才生效 |
| tieBreaker 同分不稳定 | final_rank 计算加确定性次序键（AC 时间 / 首杀 / user_id） |

---

## 4. Validation

实施时（Round 4）必须勾选：

- [ ] 三模式单测矩阵：`{ICPC, IOI, SCORE}` × `{首杀, 重复AC, 罚时累计, 同分tiebreak, penaltyPerWrong=null, penaltyPerWrong=自定义}`
- [ ] 构造提交序列，断言 `total_score` / `total_penalty` / `final_rank` 符合 §2.2 定义
- [ ] `penaltyPerWrong=null` 兜底 20，无 NPE
- [ ] 重复 AC 不重复计分；首杀标记正确
- [ ] 同分参赛者 tieBreaker 确定且稳定（多次结算结果一致）
- [ ] 历史提交结算结果**未变**（证明不复算 + 兜底一致）

---

## 5. References

- [REVIEW_V3.md §3 P0-1](../contest/REVIEW_V3.md) — 验收基线
- [EXECUTION_PLAN.md Round 4](../contest/EXECUTION_PLAN.md)
- 现有代码：`ContestScoringServiceImpl.java`、`ContestScoringMode.java`、`Contest.java`
- ICPC/IOI 评分规则：ICPC World Finals Rules / IOI Scoring Guidelines
