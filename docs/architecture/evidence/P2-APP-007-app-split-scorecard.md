# P2-APP-007 App 候选部署单元 scorecard

> status: COMPLETE
> evidence level: Repository Implemented (no production traffic/capacity claim)
> depends_on: P2-APP-002, P2-APP-004, P2-APP-005, P2-APP-006
> gate: GATE-APP-SPLIT-CANDIDATE — **No-Go: 保持当前单 App 部署，继续逻辑 module 深化**

## 评分维度（8 维，任一必备 FAIL 即 No-Go）

| # | 维度 | 证据来源 | 当前得分 | 必备 | 判定 |
|---|---|---|---|---|---|
| 1 | Team ownership | 仓库无独立团队/Owner 声明；`P2-APP-002` §3 | 0/5 | 必备 | FAIL |
| 2 | Release cadence | App Owner 单一 `service.version.app` 发布四模块（`services/app/pom.xml:16`） | 1/5 | 必备 | FAIL |
| 3 | Scale / capacity | 无生产流量、p95、容量曲线；`P2-APP-002` 明确不以 LOC 决策 | 0/5 | 条件 | NOT MET |
| 4 | Fault isolation | Problem/Forum/Solution/Contest 共享同 DB 实例与 Redis；P1 证据仍为单点拓扑 | 1/5 | 条件 | NOT MET |
| 5 | Data / transaction | Forum `forum_posts/comments`、Solution `solutions` 事务仍在 `app-web` 单库；Moderation/Problem 领域服务无跨库事务（`P2-APP-002`） | 2/5 | 必备 | PARTIAL |
| 6 | Interface / contract | `app-api` 71 interfaces 已收窄（P2-APP-003 退休 4 个、P2-APP-004 保留深接口）；无陌生 God-aggregate | 3/5 | 必备 | PASS (保持深化) |
| 7 | Remote cost | 拆分将新增 RPC、mixed-version、事务补偿；未评估 | 0/5 | 必备 | FAIL |
| 8 | Test / deploy | 独立可测/可部署边界未建立；模块仍为库形态（`backend-problem-domain` 无 Boot 插件） | 1/5 | 必备 | FAIL |

## 候选单元评估

| 候选 | 逻辑归属 | 物理拆分成本 | 证据 | 结论 |
|---|---|---|---|---|
| Problem | `backend-problem-domain` 库模块 + `app-web` Provider | 新服务需独立数据源/migration/事务补偿 | 领域服务已纯化（P2-APP-005） | **深化库模块，不拆进程** |
| Contest | `backend-contest-domain` 库模块 + `app-web` | 同上，且与 Submission 仅 assoc 表关联 | Compile 依赖已聚合 | **同上** |
| Forum | `app-web` feature 模块，无私有子模块 | 需新建模块、独立表契约 | `MapperScanConfig` 扫描 `forum.mapper` | **保持 app-web** |
| Solution | `app-web` feature 模块 | 同 Forum | 同上 | **保持 app-web** |
| Moderation | `backend-moderation-domain` + `app-web` 状态机 | 审核状态机与 Forum/Solution 同事务 | 领域服务已抽离但状态机在 `app-web` | **保持 app-web** |
| WebSocket | `app-web` only，无业务表 | 无独立数据，需共享会话 | STOMP simple broker | **保持 app-web** |
| Search | 派生索引，派生自 App 事件 | 已有独立 Worker `backend-search` | Worker 已独立 | **已独立，无需再拆 App** |

## 决策规则（来自 plan §8.1）

- 保持当前 deployment 的条件（满足即保持）：无独立团队/发布/扩缩容证据 — **已满足，保持**。
- 物理拆分仅进入设计的前置：data/transaction 清晰、interface 收窄、独立测试/部署可承担 — **仅 interface 收窄部分满足，其余不满足**。
- 任何 Go 只允许进入设计，不自动实施 — **本轮为 No-Go，无设计授权**。

## 与 P2-APP-004/005 的一致性

- P2-APP-004 保留 deep owner contracts（`ProblemAdminReadPort` 等），拒绝以方法数拆 God aggregate — 本 scorecard 继承该结论。
- P2-APP-005 已证明单进程内可通过库模块提升 locality，无需进程边界。

## 触发器（何时重评）

出现以下任一可观测、跨版本稳定的证据时，由 project owner 重启 `GATE-APP-SPLIT-CANDIDATE`：

1. 独立团队对 Problem/Contest 的明确 ownership 与 on-call 责任边界书面确认
2. Forum 或 Solution 出现与 Problem/Moderation 无关的独立发布节奏（≥3 个连续版本无 co-change）
3. App 单实例容量瓶颈的 production capacity evidence（p95 latency/fault 隔离收益可量化）
4. 可承担的独立数据源、migration 归属、补偿事务与可回滚部署方案的 ADR

## 验证

```bash
bash scripts/test/api-contract-boundary-contract.sh      # 71 interfaces, ownership gated
bash scripts/dev/architecture-contract-test.sh            # dependency graph
cat docs/architecture/evidence/P2-APP-002-app-domain-matrix.md  # domain matrix inputs
```

## 结论

**No-Go for physical split.** 深化现有私有库模块，Forum/Solution 保持 `app-web`；任何未来拆分必须先满足上述必备维度并通过 `GATE-APP-SPLIT-CANDIDATE` 的人工复核。
