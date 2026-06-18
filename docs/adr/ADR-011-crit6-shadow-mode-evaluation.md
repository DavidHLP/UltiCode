# ADR-011: CRIT-6 (F-ARCH-07) shadow 模式评估结论

| 字段 | 值 |
|------|-----|
| **状态 (Status)** | **Accepted** (R8.5 评估决策，2026-06-17) |
| **日期 (Date)** | 2026-06-17 |
| **作者 (Author)** | DavidHLP |
| **来源** | [REVIEW_V3 §6 CRIT-6 / F-ARCH-07](../contest/REVIEW_V3.md) + [EXECUTION_PLAN_R8 §6](../contest/_archive/EXECUTION_PLAN_R8_2026-06-17.md) |
| **关联 ADR** | [ADR-006 §2.4 灰度策略](./ADR-006-contest-scoring-engine-activation.md) + [ADR-007 §6 / §8](./ADR-007-virtual-contest-lifecycle-and-rating-isolation.md) |

---

## 1. Context

REVIEW_V3 把 CRIT-6 (F-ARCH-07) 列为 review 残留：
- `V20260617120000__Contest_Scoring_Hardening.sql` 把"hardening migration"与"应用层"解耦（"应用层无 shadow 模式"）
- 原 review 建议引入"双写 + 读比 + 灰度"shadow 模式
- R1-R7 期间未实施

## 2. Decision

**不引入独立 `contest.scoring.shadow` flag**。理由：

1. **ADR-006 §2.4 已有灰度策略**：`contest.scoring.engine.v2` flag 占位（未启用）；R4 实施时采用 `penaltyPerWrong` null 兜底 20 + 三模式分支（按 ADR-006 §2.2），已经提供**隐式灰度**——v1 行为（硬编码 20）vs v2 行为（配置 + 模式分支）可平滑切换
2. **flag 增殖代价 > 收益**：每个新 feature flag 需要 ops 协调（灰度 / 监控 / 回滚开关）；F-ARCH-07 描述的"双写 + 读比"需要并行跑 v1 + v2 路径，DB 写入路径 double cost
3. **现有风险已覆盖**：
   - R1 的 `V20260617130000__Contest_Slug_Unique.sql` 在 staging 验证后部署（H1 deploy-checklist）
   - R6.5 的 `V20260617140000__Contest_Real_Unique_And_Session_Length.sql` 有 pre-check + ROLLBACK（HIGH-2 fix）
   - 任何 v2 行为变更都有 ADR + code review + 单测 + deploy-checklist

## 3. Consequences

### 3.1 Positive
- 零 flag 增殖成本
- R4 实施时已隐式灰度（null 兜底兼容 v1 行为）
- 未来若发现真正需要 shadow 模式（比如涉及 ranking 计算的不可逆变更），可以独立 PR 引入

### 3.2 Negative
- 若 v2 行为变更在生产出问题，**回滚只能靠部署 + 数据库迁移回退**（无独立 flag 灰度）
- 这与 ADR-006 §2.4 "rollout / rollback drill"（ADR-005）一致

## 4. Validation

- [x] F-ARCH-07 / CRIT-6 状态从 🟡 改为 ✅
- [x] ADR-011 Accepted
- [x] REVIEW_V3 §6 CRIT-6 行更新为 ✅ 不适用

## 5. References

- [REVIEW_V3 §6 CRIT-6](../contest/REVIEW_V3.md)
- [EXECUTION_PLAN_R8 §6](../contest/_archive/EXECUTION_PLAN_R8_2026-06-17.md)
- [ADR-006 §2.4 灰度策略](./ADR-006-contest-scoring-engine-activation.md)
- [ADR-007 §8 R7 评估](./ADR-007-virtual-contest-lifecycle-and-rating-isolation.md)
