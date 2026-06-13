# ADR-005: 滚动部署 Playbook (Feature Flag + Dual-Read + Envelope Versioning + Canary + Rollback)

| 字段 | 值 |
|------|-----|
| **状态 (Status)** | Proposed |
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

### 2.1 Milestone 拆分 (M1→M5 共 5 个独立可部署单元)

| Milestone | ADR | 改动范围 | 可独立部署 | 可独立回滚 |
|---|---|---|---|---|
| **M1a** | ADR-001 (§2.1-§2.4) | enum 改 + Codec + VerdictResolver, **仅扩不写新值** | ✅ | ✅ (revert commit) |
| **M1b** | ADR-001 (§2.5) | i18n coverage test 接入 CI | ✅ | ✅ |
| **M2a** | ADR-002 | Sandbox port + DockerAdapter + InMemoryAdapter + 5 LanguageProfile, 通过 `sandbox.executor` flag 灰度 | ✅ (默认 flag=legacy) | ✅ (flag 切回 legacy) |
| **M2b** | ADR-002 (cutover) | flag 默认值切 `hexagonal`, 删旧 SandboxServiceImpl 内联实现 | ✅ | ⚠️ (需 revert) |
| **M3a** | ADR-003 (§2.1) | Outbox 表 + dispatcher worker, **dual-write** (旧 afterCommit + 新 outbox 同时跑) | ✅ | ✅ (停 dispatcher) |
| **M3b** | ADR-003 (§2.2-§2.3) | generation + lease 列加入, dual-CAS 兼容旧 worker | ✅ (DB 加列 + 默认 NULL) | ✅ (列保留, 代码 revert) |
| **M3c** | ADR-003 (§2.4 cutover) | JudgeQueue port + Redisson adapter, envelope v2 (含 generation) | ✅ (envelope dual-read) | ✅ (flag) |
| **M3d** | ADR-003 (cutover) | 删旧 afterCommit + reaper, 关闭 envelope v1 | ⚠️ | ⚠️ (需 revert + 旧 envelope 排空) |
| **M4a** | ADR-004 | NotificationIntent + Dispatcher + 3 Channel, 旧 `NotificationDispatchService` 共存 | ✅ | ✅ (废弃 dispatcher 调用即可) |
| **M4b** | ADR-004 (cutover) | 业务模块切到 `notificationDispatcher.dispatch(intent)` , 删旧 path | ⚠️ | ⚠️ |

**关键原则**: 每个 milestone 落地后 main 必须**双轨可运行** (老路径 + 新路径都能跑) , 直到 cutover milestone 才删旧路径。

### 2.2 Feature Flag 矩阵

```yaml
# application.yml
ulticode:
  features:
    sandbox:
      executor: legacy            # legacy | hexagonal
    judge-queue:
      use-port: false             # false=直接 Redisson | true=走 JudgeQueue port
      envelope-version: 1         # 1=旧 JudgeJob | 2=含 generation
    submission:
      use-outbox: false           # false=afterCommit | true=outbox
      use-generation-fence: false # false=无 generation 校验 | true=CAS 带 generation
    notification:
      use-dispatcher: false       # false=旧三模块直调 | true=新 NotificationDispatcher
```

每个 flag 独立可切, 切换无需重启 (用 `@RefreshScope` + Nacos config, 项目已有 Nacos 集成) 。

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

### 2.6 Rollback Drill (每 milestone 一次)

部署前在 staging 环境**主动**执行以下回滚演练并记录耗时:

| Milestone | Rollback 动作 | 期望耗时 |
|---|---|---|
| M2a | `ulticode.features.sandbox.executor: legacy` → Nacos hot reload | < 30s |
| M3a | 停 `JudgeOutboxDispatcher`,outbox 表保留, 新 submit 走旧 afterCommit (flag) | < 1min |
| M3c | `ulticode.features.judge-queue.use-port: false` , 老 worker 直接 RQueue | < 30s |
| M4a | `ulticode.features.notification.use-dispatcher: false` , 业务模块走旧 path | < 30s |

Cutover milestone (M2b / M3d / M4b) 回滚需要 git revert + 重新部署, 不在热回滚范围, 因此**只在前一个 milestone 至少 7 天平稳后**才执行。

### 2.7 比赛 (Contest) 部署窗口

- M2b / M3d / M4b **禁止**在比赛进行中或比赛开始前 24 小时内部署
- M3a / M3b 涉及 DB schema 变更, 必须在比赛结束后 4 小时内执行 (避开实时 peak)
- 部署前查 `contest.start_time` 表确认无 active contest

## 3. Consequences

### 3.1 Positive

- 每个 milestone 是**真"可独立部署 + 可热回滚"** 单元
- DB 迁移 expand-contract 分离, 不可逆迁移留在 contract milestone (受加倍审视)
- Envelope versioning 让新旧 worker 共存数日, 排空残留任务后才 cutover
- Canary gate 把"上线后崩了才发现"的窗口从天级缩短到小时级
- Rollback drill 不是"事到临头想"的演习, 而是部署前已知耗时

### 3.2 Negative

- Milestone 从 3 个膨胀到 11 个 (M1a/M1b/M2a/M2b/M3a/M3b/M3c/M3d/M4a/M4b) , 项目管理成本上升
- 双轨期 (M2a→M2b / M3a→M3d) 代码体积更大, code review 负担重
- Feature flag 矩阵 5 个独立 flag, 测试矩阵 = 2^5 = 32 组合, 全测不现实 → 选 4-5 个关键组合
- Cutover milestone 仍需 revert + 重新部署, 不是完美热回滚

### 3.3 Risks

| 风险 | 缓解 |
|---|---|
| Feature flag 长时间未清理 → 代码遍布 `if (flag)` 分支变僵尸 | 每个 flag 在引入 ADR 中标注 "removal milestone" , cutover 完成立即开 cleanup PR |
| Nacos 配置漂移 → 生产 flag 与代码默认值不一致 | 启动时打印所有 `ulticode.features.*` 当前值, 写 prod runbook |
| 双轨期一致性 bug (新旧 path 行为微差) | M2a/M3a/M4a 各加"双跑校验" 模式 (shadow mode): 新 path 跑但不写结果, 比对与旧 path 是否一致, 不一致告警 |
| 11 milestone 拖战线过长 → 人换团队失忆 | 每完成一个 milestone 更新本 ADR 状态表, ADR-005 § 2.1 表格添加 "shipped at" 列 |
| Contest 窗口限制让进度卡死 | 与运营对齐 12 周 release cadence, 每周固定窗口 |

## 4. Validation (针对本 ADR 自身)

- [ ] 11 个 milestone 全部录入项目 issue tracker (每 milestone 一个 issue + acceptance criteria)
- [ ] Staging 环境**首次** rollback drill 完成时间记录到本 ADR § 2.6 表中
- [ ] Nacos `ulticode.features.*` 配置项交接给运维, 文档化在 `docs/RUNBOOK.md`
- [ ] CI 加入 "all features off" 与 "all features on" 两套测试 profile
- [ ] DB 迁移文件名遵守 `V{ts}__{description}.sql` 格式 (CLAUDE.md 约定)

## 5. References

- [ADR-000](./ADR-000-hexagonal-grilling-session.md) — F5 原文
- [ADR-001](./ADR-001-verdict-status-codec.md)、[ADR-002](./ADR-002-sandbox-hexagonal.md)、[ADR-003](./ADR-003-queue-outbox-fencing.md)、[ADR-004](./ADR-004-notification-intents.md) — 本 ADR 为它们排序
- Martin Fowler, "Expand-Contract Pattern" — DB 迁移基础
- Sam Newman, "Building Microservices" Ch. 5 — Feature toggle 长寿管理
- 现有 Nacos 配置: 项目 `nacos` 容器 + Spring Cloud Alibaba 配置中心
- 项目规约: CLAUDE.md (Verification Matrix / Flyway 命名), `.claude/rules/backend/07-java-design.md` (#11 开闭, #14 设计沉淀文档)
