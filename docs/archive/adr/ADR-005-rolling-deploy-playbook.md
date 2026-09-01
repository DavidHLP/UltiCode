# ADR-005: 滚动部署 Playbook (Feature Flag + Dual-Read + Envelope Versioning + Canary + Rollback)

| 字段 | 值 |
|------|-----|
| **状态 (Status)** | Proposed (stays Proposed pending rollback drill completion — §2.6 表 M3a/M3c/M4a 三处全 `_TBD_`,从未执行首次 drill;不因依赖 ADR-001/002 已 Accepted 或 M1a-M3c 已 shipped 提前升级 Accepted) |
| **日期 (Date)** | 2026-06-13 |
| **作者 (Author)** | DavidHLP |
| **解决的 Finding** | [ADR-000 / F5](./ADR-000-hexagonal-grilling-session.md#2-codex-adversarial-review-摘要) |
| **依赖 ADR** | ADR-001 / ADR-002 / ADR-003 / ADR-004 (本 ADR 为它们排序) |
| **关联代码** | `application.yml` 新增 feature flag 区, PM2 `ecosystem.config.cjs` 不变 |

---

## 1. Context

### 1.1 Codex F5 否决的原方案

原 ADR-001 §4 提议 "M1 sandbox → M2 queue → M3 notification, 4-6 周串行 merge", 但:

- **M2 把队列表示 + 状态字段 + 入队时机 + 恢复机制四件事打一包** → 一次性合入 main 后, 任一子项出问题无法 partial rollback
- **新 + 旧代码在 main 共存 4-6 周** 却没有 feature flag, 老 worker 抓不到新 envelope, 新 worker 不认旧 envelope
- **无 envelope versioning** → Redis 队列里的旧 job 和新 job 字段不同, 反序列化炸
- **无 rollback drill** → 真出事时不知道怎么退

### 1.2 OJ 部署特征 (本 ADR 假设)

- 后端**单 JVM** + 1 个 Spring Boot 进程 (PM2 `ulticode-9001`)
- 暂无多副本滚动, 但 `pm2 reload` 能做 zero-downtime restart
- 前端 PWA, 用户可能 cache 老版本 JS (前端版本 skew 必须考虑)
- 数据库迁移走 Flyway, **不可逆** (除非新增 down migration)
- 比赛 (Contest) 是高峰流量场景, 部署窗口必须避开

## 2. Decision

### 2.1 Milestone 拆分 (M1a→M4b 共 10 个独立可部署单元)

| Milestone | ADR | 改动范围 | 可独立部署 | 可独立回滚 | shipped at |
|---|---|---|---|---|---|
| **M1a** | ADR-001 (§2.1-§2.4) | enum 改 + Codec + VerdictResolver, **仅扩不写新值** | ✅ | ✅ (revert commit) | **shipped** (见 [ADR-001 Accepted](./ADR-001-verdict-status-codec.md)) |
| **M1b** | ADR-001 (§2.5) | i18n coverage test 接入 CI | ✅ | ✅ | **shipped** (见 [ADR-001 Accepted](./ADR-001-verdict-status-codec.md)) |
| **M2a** | ADR-002 | Sandbox port + DockerAdapter + InMemoryAdapter + 5 LanguageProfile, 通过 `sandbox.executor` flag 灰度 | ✅ (默认 flag=legacy) | ✅ (flag 切回 legacy) | **shipped** (见 [ADR-002 Accepted](./ADR-002-sandbox-hexagonal.md)) |
| **M2b** | ADR-002 (cutover) | flag 默认值切 `hexagonal`, 删旧 SandboxServiceImpl 内联实现 | ✅ | ⚠️ (需 revert) | **shipped** (见 [ADR-002 Accepted](./ADR-002-sandbox-hexagonal.md)) |
| **M3a** | ADR-003 (§2.1) | Outbox 表 (含 `is_shadow`) + dispatcher **shadow-only** (只观察不入队, §2.8 F8 修订) | ✅ | ✅ (停 dispatcher / `useJudgeOutbox=off`) | **2026-06-13 `09c97d1b8`** |
| **M3b** | ADR-003 (§2.2-§2.3) | generation/lease 列 + CAS fence + reaper + worker heartbeat, dual-CAS 兼容旧 worker | ✅ (DB 加列 + DEFAULT) | ✅ (列保留, 代码 revert / `useGenerationFence=off`) | **2026-06-13 `09c97d1b8`** |
| **M3c** | ADR-003 (§2.4 cutover) | JudgeQueue port + Redisson adapter, envelope v2 (含 generation) | ✅ (envelope dual-read) | ✅ (flag) | **2026-06-13 `b34ac01be` + `3e8504f1b` + `3ec758c41`** |
| **M3d** | ADR-003 (cutover) | 删旧 afterCommit + reaper, 关闭 envelope v1 | ⚠️ | ⚠️ (需 revert + 旧 envelope 排空) | — |
| **M4a** | ADR-004 | NotificationIntent + Dispatcher + 3 Channel, 旧 `NotificationDispatchService` 共存 | ✅ | ✅ (废弃 dispatcher 调用即可) | — |
| **M4b** | ADR-004 (cutover) | 业务模块切到 `notificationDispatcher.dispatch(intent)` , 删旧 path | ⚠️ | ⚠️ | — |

**关键原则**: 每个 milestone 落地后 main 必须**双轨可运行** (老路径 + 新路径都能跑) , 直到 cutover milestone 才删旧路径。

### 2.2 Feature Flag 矩阵 (cutover 相关, prefix = `app.features.*`)

```yaml
# application.yml — 仅与 ADR-003 / ADR-004 cutover 相关的 flag 子集
app:
  features:
    judge-queue:
      use-port: false             # false=直接 Redisson | true=走 JudgeQueue port
      envelope-version: 1         # 1=旧 JudgeJob | 2=含 generation
    submission:
      use-outbox: false           # false=afterCommit | true=outbox
      use-generation-fence: false # false=无 generation 校验 | true=CAS 带 generation
    notification:
      use-dispatcher: false       # false=旧三模块直调 | true=新 NotificationDispatcher
# 其余 5 个产品功能 flag (useNewContestSystem / realtimeRankingEnabled /
# firstSolveNotificationsEnabled / anticheatEnabled / contestAnalyticsEnabled)
# 见 docs/RUNBOOK.md §10.6
#
# 注意: sandbox 切换 (`sandbox.executor`) 已在 ADR-002 落地, 不在
# FeatureFlagsProperties 范围内. 实际项目用 `code-execution.sandbox.d-form.enabled`
# (env `SANDBOX_DFORM_ENABLED`, 详见 docs/RUNBOOK.md §10.7), 自 commit `8c13ec61f`
# 起 D-form 是唯一 dispatch 路径, **flag 不再 toggle dispatcher** — M2a 不在本
# ADR "5min 热回滚" 演练范围, 见 §2.6 表脚注 ¹. 真正的 D-form rollback 需另起
# ADR-009 (git revert + 重建 sandbox image).
```

每个 flag 独立可切, 切换需 `pm2 reload ulticode-9001` (重启级, 详见 §2.8 F10 修订). Nacos Config client 集成是 ADR-008 范围, 不在本 ADR.

### 2.3 DB 迁移策略 (expand-contract)

| Milestone | Migration | Phase | 回滚 |
|---|---|---|---|
| M3a | `V20260613100000__Create_Judge_Outbox.sql` | expand (加表) | drop 表 (单独 down migration) |
| M3b | `V20260613110000__Add_Submission_Generation_And_Lease.sql` | expand (加列 default NULL) | 保留列, 不删 |
| M3d | `V20260613120000__Make_Submission_Generation_Not_Null.sql` | contract (after backfill) | 改回 nullable (单独 ADR) |

**规则**:

- 加列必须 default NULL 或 sensible default, 老代码读到 NULL 不爆
- 删列 / 改列类型必须在 contract milestone, 且前一个 milestone 已经停止写入
- 不允许在一次 migration 里既加列又改其它列

### 2.4 Queue Envelope Versioning

```java
public sealed interface JudgeJobEnvelope permits JudgeJobV1, JudgeJobV2 {
    int version();
    String submissionId();
}

public record JudgeJobV1(...) implements JudgeJobEnvelope { public int version() { return 1; } }
public record JudgeJobV2(..., long generation, String attemptId) implements JudgeJobEnvelope {
    public int version() { return 2; }
}
```

Codec:

```java
public class JudgeJobCodec implements Codec {
    @Override public Object decode(ByteBuf buf, State state) {
        JsonNode node = JSON.readTree(buf);
        int v = node.path("version").asInt(1);
        return switch (v) {
            case 1 -> JSON.treeToValue(node, JudgeJobV1.class);
            case 2 -> JSON.treeToValue(node, JudgeJobV2.class);
            default -> throw new IllegalStateException("Unknown envelope version: " + v);
        };
    }
}
```

Worker 启动 M3c 后, **同时识别 v1 + v2** ; M3d cutover 后 envelope v1 入队代码删除, **但 decode 仍保留** 至少 2 周以排空残留 v1 任务。

### 2.5 Canary Gate

每个 milestone 上线后必须满足以下条件才能进下一个 milestone:

| 指标 | 阈值 |
|---|---|
| `pm2 status ulticode-9001` 连续 24h 无 unplanned restart | ✅ |
| `judge.outbox.failure_rate` (M3a 起) | < 0.1% |
| `judge.stale_result.dropped` (M3b 起) | 出现即调研, 默认应为 0 |
| `notification.dispatch.failure` (M4a 起) | < 1% per channel |
| 一次 `pm2 reload ulticode-9001` 后 verdict 接受流转无中断 (集成测试 30 min 持续提交) | ✅ |
| 前端 i18n key coverage 100% (M1b 起 CI gate) | ✅ |

### 2.6 Rollback Drill (每 milestone 一次, 实际耗时由 §4 #2 沉淀)

部署前在 dev 拓扑 (与 staging 等价, 详见 RUNBOOK §7) **主动**执行以下回滚演练并记录耗时.
`TBD` 表示待首次 drill 后填写, 不阻塞 ADR 自身验收. 详细 drill 协议见
`docs/adr/ADR-005a-rollback-drill-protocol.md` (ADR-005 的执行子协议, 编号 `005a`).

| Milestone | Rollback 动作 | 期望耗时 | 实际耗时 | 完成时间 (UTC) | 执行人 | 备注 |
|-----------|--------------|----------|----------|---------------|--------|------|
| M2a | ~~`app.features.sandbox.executor: legacy`~~ **不可 hot rollback**: 实际 flag `code-execution.sandbox.d-form.enabled` (env `SANDBOX_DFORM_ENABLED`) 在 commit `8c13ec61f` 后**不再 toggle dispatcher** (D-form 永远 on). 见脚注 ¹ | _N/A_ | _N/A_ | _N/A_ | M2a rollback 需 `git revert 8c13ec61f 095a01fd5` + 重建 sandbox image |
| M3a | `app.features.use-judge-outbox: false` + `pm2 reload ulticode-9001` | < 5min | _TBD_ | _TBD_ | _TBD_ | _TBD_ |
| M3c | `app.features.judge-queue.use-port: false` + `pm2 reload ulticode-9001` | < 5min | _TBD_ | _TBD_ | _TBD_ | _TBD_ |
| M4a | `app.features.use-notification-intent: false` + `pm2 reload ulticode-9001` | < 5min | _TBD_ | _TBD_ | _TBD_ | _TBD_ |

¹ **M2a 实际机制** (来自 `backend-spring/src/main/resources/application.yml:130-165`):
- `code-execution.sandbox.d-form.enabled` (env `SANDBOX_DFORM_ENABLED`) 唯一作用是
  验证 CodeExecutionService 收到的 language 集合 vs 实际可执行 harness 集合
  (java + python only),**不**切换 dispatcher 路径
- D-form 自 commit `8c13ec61f` 后是**唯一** dispatch 路径, Form A 旧 path 已删
- 想真 rollback D-form 需 `git revert 8c13ec61f 095a01fd5` + 重建 sandbox image
  (`cd docker/sandbox && ./harness/build.sh` — 自 commit `9xxx` 起 build.sh 内部完成 pre-compile + docker build + tag `:latest`,
  无需单独再 `docker build`. 见 [ADR-002 §7.1](./ADR-002-sandbox-hexagonal.md#71-docker-latest-tag-自动重打-close-65-1))
- 故 M2a 不在本 ADR "5min 热回滚"演练范围, 需另起 ADR-009 (D-form rollback)

Cutover milestone (M2b / M3d / M4b) 回滚需要 git revert + 重新部署, 不在热回滚范围, 因此**只在前一个 milestone 至少 7 天平稳后**才执行。

### 2.7 比赛 (Contest) 部署窗口

- M2b / M3d / M4b **禁止**在比赛进行中或比赛开始前 24 小时内部署
- M3a / M3b 涉及 DB schema 变更, 必须在比赛结束后 4 小时内执行 (避开实时 peak)
- 部署前查 `contest.start_time` 表确认无 active contest

### 2.8 Round 2 Codex Revision (2026-06-13)

第二轮 codex 评审发现两条 finding (1 critical + 1 high), 修订如下:

#### F8 修订 — M3a 必须避免双 producer

**原 §2.1 缺陷**: M3a 让 "旧 afterCommit 入队" + "新 outbox dispatcher 入队" **同时跑**, 但 Redis dedup (M3c) 与 generation fence (M3b) 都还没上。两条 producer 路径独立, 同一 submission 入队两次 → 双重判题 → 双 verdict 写 → 双 notification。

**修订**: M3a 的 outbox 改为 **shadow-only** (写不投), 仅在所有 fence 都上线后才接管投递。

| Milestone | 修订前 | 修订后 |
|---|---|---|
| M3a | Outbox 表 + dispatcher 真 enqueue (dual-write) | Outbox 表 + dispatcher **只比对** (旧 path 入了什么 vs outbox 记录了什么) , 不 enqueue |
| M3b | 加 generation/lease 列 | 加 generation/lease 列 + **fence CAS 写入路径上线** (worker 写结果前校验 generation) |
| M3c | JudgeQueue port + envelope v2 + Redis Streams ack 化 (ADR-003 §2.6 F6 修订) | + **cutover**: outbox dispatcher 接管真 enqueue, 旧 afterCommit 入队删除 |
| M3d | 删旧 afterCommit + envelope v1 关闭 | 仅清理 envelope v1 decode + 旧代码 |

**关键不变量**: 任何时刻**最多一个 active producer** 写 Redis Streams。M3a 阶段只有"旧 afterCommit" 是 active, outbox 只观察; M3c cutover 后只有"outbox dispatcher" 是 active, 旧 path 彻底关。

新增 shadow-mode 实现要求:

```java
@Component
public class OutboxShadowComparator {
    /**
     * M3a 阶段: submission 落库 + outbox 落库 (同事务) , 但 outbox dispatcher
     * 处于 shadow 模式 — 只比对 Redis 队列里是否已经存在对应 submission 的入队记录
     * (用 Redis Set tracking key), 不实际 enqueue。差异计入指标 outbox.shadow.diff。
     * 累计 7 天 diff = 0 是 M3b cutover 进入的硬门禁。
     */
    @Scheduled(fixedDelay = 5000)
    void shadowCompare() { ... }
}
```

#### F10 修订 — Rollback 机制要可执行

**原 §2.2 缺陷**: 我声称 "切换无需重启 (用 @RefreshScope + Nacos config, 项目已有 Nacos 集成)" , 但:

- 项目仅运行 Nacos **服务端容器** (Nacos 控制台 `:28848`)
- backend `pom.xml` **没有** `spring-cloud-starter-alibaba-nacos-config` 依赖
- 代码库**没有** `@RefreshScope` 使用, 没有 `bootstrap.yml` 配置导入
- "亚分钟 hot rollback" 的承诺**无法实现**

**修订**: 两条平行路径, 选其一作为 M2a 上线的硬前置:

| 路径 | 内容 | 工作量 |
|---|---|---|
| **A (推荐, 短期)** | 重新定义 rollback 为 **"重启级"**: `pm2 reload ulticode-9001` (zero-downtime 重启, 重新读 `application.yml` 中的 feature flag); 配合 git revert 配置改动 | 0 — 用现有机制 |
| B (长期, 单独 ADR-008) | 真正引入 Nacos Config client: 加 `spring-cloud-starter-alibaba-nacos-config` 依赖 + bootstrap.yml + `@RefreshScope` 注解 + Nacos namespace/group 划分 + auth + 自动 refresh 集成测试 | 1-2 周专项 PRD + ADR-008 |

**本 ADR 默认采用路径 A**, 重写本节 §2.2 + §2.6 的 rollback 表如下:

```diff
- 切换无需重启 (用 @RefreshScope + Nacos config, 项目已有 Nacos 集成)
+ 切换需 `pm2 reload ulticode-9001` (zero-downtime 重启, < 5s 接入新 flag)
+ Nacos Config client 集成是未来 ADR-008 范围, 不阻塞 M2a 上线
```

```diff
- M2a rollback: `app.features.sandbox.executor: legacy` → Nacos hot reload, < 30s
+ M2a rollback: `application.yml` 改 sandbox.executor: legacy → git revert 该配置 commit + `pm2 reload ulticode-9001`, 端到端 < 5min
- M3a rollback: 停 JudgeOutboxDispatcher → Nacos toggle, < 1min
+ M3a rollback: 配置 `app.features.use-judge-outbox: false` → `pm2 reload`, < 5min
```

**Canary Gate** (§2.5) 不受影响, 仍然成立。Rollback drill (§2.6) 每个 milestone 仍然必须做, 期望耗时由 "< 30s" 调整为 "< 5min" (含 `pm2 reload` + 健康检查) 。

#### 不在本 ADR 修订范围

- Nacos Config client 集成 → ADR-008
- 多副本部署 → 当前项目单副本 backend, 多副本是另一专项

## 3. Consequences

### 3.1 Positive

- 每个 milestone 是**真"可独立部署 + 可热回滚"** 单元
- DB 迁移 expand-contract 分离, 不可逆迁移留在 contract milestone (受加倍审视)
- Envelope versioning 让新旧 worker 共存数日, 排空残留任务后才 cutover
- Canary gate 把"上线后崩了才发现"的窗口从天级缩短到小时级
- Rollback drill 不是"事到临头想"的演习, 而是部署前已知耗时

### 3.2 Negative

- Milestone 从 3 个膨胀到 10 个 (M1a/M1b/M2a/M2b/M3a/M3b/M3c/M3d/M4a/M4b) , 项目管理成本上升
- 双轨期 (M2a→M2b / M3a→M3d) 代码体积更大, code review 负担重
- Feature flag 矩阵 5 个独立 flag, 测试矩阵 = 2^5 = 32 组合, 全测不现实 → 选 4-5 个关键组合
- Cutover milestone 仍需 revert + 重新部署, 不是完美热回滚

### 3.3 Risks

| 风险 | 缓解 |
|---|---|
| Feature flag 长时间未清理 → 代码遍布 `if (flag)` 分支变僵尸 | 每个 flag 在引入 ADR 中标注 "removal milestone" , cutover 完成立即开 cleanup PR |
| Nacos 配置漂移 → 生产 flag 与代码默认值不一致 | 启动时打印所有 `app.features.*` 当前值, 写 prod runbook |
| 双轨期一致性 bug (新旧 path 行为微差) | M2a/M3a/M4a 各加"双跑校验" 模式 (shadow mode): 新 path 跑但不写结果, 比对与旧 path 是否一致, 不一致告警 |
| 10 milestone 拖战线过长 → 人换团队失忆 | 每完成一个 milestone 更新本 ADR 状态表, ADR-005 § 2.1 表格添加 "shipped at" 列 |
| Contest 窗口限制让进度卡死 | 与运营对齐 12 周 release cadence, 每周固定窗口 |

## 4. Validation (针对本 ADR 自身)

- [ ] **10** 个 milestone 全部录入项目 issue tracker (每 milestone 一个 issue + acceptance criteria) — 见 `scripts/adr-005/create-milestone-issues.sh` 脚本 (commit 待用户手动跑)
- [ ] Dev 拓扑 (与 staging 等价) **首次** rollback drill 完成时间记录到本 ADR § 2.6 表中
- [ ] `app.features.*` 配置项交接给运维, 文档化在 `docs/RUNBOOK.md` §10
- [ ] CI 加入 "all features off" 与 "all features on" 两套测试 profile — `application-features-{off,on}.yml` + ci.yml matrix
- [ ] DB 迁移文件名遵守 `V{ts}__{description}.sql` 格式 (CLAUDE.md 约定) — `flyway-filename-lint` job

## 5. References

- [ADR-000](./ADR-000-hexagonal-grilling-session.md) — F5 原文
- [ADR-001](./ADR-001-verdict-status-codec.md)、[ADR-002](./ADR-002-sandbox-hexagonal.md)、[ADR-003](./ADR-003-queue-outbox-fencing.md)、[ADR-004](./ADR-004-notification-intents.md) — 本 ADR 为它们排序
- Martin Fowler, "Expand-Contract Pattern" — DB 迁移基础
- Sam Newman, "Building Microservices" Ch. 5 — Feature toggle 长寿管理
- 现有 Nacos 配置: 项目 `nacos` 容器 + Spring Cloud Alibaba 配置中心
- 项目规约: CLAUDE.md (Verification Matrix / Flyway 命名), `.claude/rules/backend/07-java-design.md` (#11 开闭, #14 设计沉淀文档)
