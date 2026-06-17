# Contest 模块术语表

Contest 模块（含真实竞赛与虚拟竞赛）的领域术语。阅读本目录任何文档前，先对齐这些术语，避免"真榜/虚拟榜"、"参赛者/会话"等概念混淆。

## Language

### 参赛者类型

**Real Participant（真实参赛者）**:
在竞赛正式窗口内参赛的参赛者，`is_virtual = 0`。其成绩计入真实排行榜（Real Ranking）与 rating 积分。
_Avoid_: normal participant, regular participant, 普通参赛者

**Virtual Participant（虚拟参赛者）**:
在竞赛结束后重播（replay）该竞赛的参赛者，`is_virtual = 1`。其成绩计入虚拟排行榜（Virtual Ranking），**不计入 rating**。
_Avoid_: ghost participant, replay user, 虚拟用户

### 会话

**Virtual Session（虚拟会话）**:
一个虚拟参赛者的一次重播实例，由 `virtual_session_id`（UUID）标识。同一用户可对同一竞赛创建**多个** Virtual Session（多次重播），但同一时刻只允许**一个活跃（STARTED）**会话。
_Avoid_: virtual run, replay session（除非显式指代单次重播）

### 评分

**Scoring Mode（评分模式）**:
竞赛的评分规则，枚举 `SCORE` / `ICPC` / `IOI`，三模式语义正交（定义见 [ADR-006](../adr/ADR-006-contest-scoring-engine-activation.md#22-评分模式三分支语义定档)）。
- **ICPC**：罚时制（AC 数 + 罚时）
- **IOI**：每题取最高分
- **SCORE**：AC 即得满分的累加总分制
_Avoid_: scoring type, contest type（ContestType 是另一概念：RATED/PRACTICE 等）

**Penalty Per Wrong（单次错误罚时）**:
ICPC 模式下，每次错误提交累加的罚时分钟数，字段 `penaltyPerWrong`。null 时兜底 20。
_Avoid_: penalty, wrong penalty（penalty 指总罚时，见下）

**Penalty（总罚时）**:
ICPC 模式下参赛者的累计罚时 = `错误提交数 × Penalty Per Wrong` + AC 耗时。
_Avoid_: time cost

**First Solve（首杀）**:
某道题在全竞赛范围内第一个 AC 的提交。首杀标记影响排名 tieBreaker。
_Avoid_: first blood, first accept

### 排名

**Final Rank（最终排名）**:
参赛者在竞赛内的最终名次，按 Scoring Mode 的排名键计算。同分时按确定性次序键 tieBreaker。
_Avoid_: position, standing

**Total Score（总分）**:
参赛者在所有题目上的累计得分（IOI/SCORE 模式的主要排名键）。
_Avoid_: points

**Real Ranking（真实排行榜）**:
仅含 Real Participant 的排行榜，`is_virtual = 0` 过滤。前端"竞赛排行榜"主视图。
_Avoid_: main ranking, official ranking

**Virtual Ranking（虚拟排行榜）**:
仅含 Virtual Participant 的排行榜，`is_virtual = 1` 过滤。前端虚拟赛独立视图。
_Avoid_: replay ranking

### 生命周期

**Auto-finish（自动结算）**:
后端 scheduler 在竞赛/虚拟会话到期时，自动将参赛者状态置为 `FINISHED` 并结算成绩的机制。不依赖参赛者在线。
_Avoid_: auto-complete, auto-end

**Rating（积分）**:
基于真实竞赛表现计算的类 Elo 积分（`global_rankings`）。**仅 Real Participant 参与**，Virtual Participant 不影响 rating。
_Avoid_: ELO, contest points（contest points 指竞赛内得分，非 rating）
