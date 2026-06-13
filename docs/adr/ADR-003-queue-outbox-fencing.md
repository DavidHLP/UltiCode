# ADR-003: Queue + Outbox + Generation Fencing + JUDGING Lease

| 字段 | 值 |
|------|-----|
| **状态 (Status)** | Proposed |
| **日期 (Date)** | 2026-06-13 |
| **作者 (Author)** | DavidHLP |
| **解决的 Finding** | [ADR-000 / F2 + F3](./ADR-000-hexagonal-grilling-session.md#2-codex-adversarial-review-摘要) |
| **依赖 ADR** | [ADR-001](./ADR-001-verdict-status-codec.md) (SubmissionStatus enum + Kind 分类) |
| **被依赖 ADR** | ADR-004 (NotificationIntent 引用 generation/attemptId) |
| **关联代码** | `submission/service/impl/SubmissionServiceImpl.java`,`queue/service/impl/QueueServiceImpl.java`,`queue/processor/JudgeWorkerProcessor.java`,`admin/.../AdminSubmissionServiceImpl.java` (rejudge) |
| **关联 DB** | 新增 `judge_outbox` 表, 修改 `submission` 表 (加 `generation`,`judging_lease_expires_at`,`current_attempt_id`) |

---

## 1. Context

### 1.1 Codex F2 + F3 联合问题分析

| 子问题 | 失败现象 | 根因 |
|---|---|---|
| **JUDGING 永久卡死** | worker 抓任务 → CAS PENDING→JUDGING 成功 → 进程崩 → reaper 只扫 PENDING 不救 JUDGING → 永远停在 JUDGING | 单一状态字段不足以表达 "有人占着但已死" |
| **Admin rejudge 被拒** | `Accepted → Pending` 不在 ALLOWED 转换表 → admin 改不动终态 → 错题永远改不对 | 终态被建模为绝对终点, 忽略业务"重判"需求 |
| **旧 worker 覆盖新 rejudge 结果** | rejudge 后老 worker 苏醒, 用旧 generation 写结果, 覆盖新一轮 JUDGING 中的真值 | 缺 generation/attempt 维度的 fence token |
| **afterCommit 缺口** | DB commit 成功 → 进程在 afterCommit 回调触发前崩 → Redis 永远没收到 → submission 卡 PENDING | 跨系统投递必须有持久化 outbox, 不能依赖进程内回调 |
| **Redis 模糊超时** | `RQueue.add(job)` 网络超时 → 实际入队成功 / 失败未知 → reaper 重试 → 同一任务进队 2 次 | RQueue 是 list 不去重, job.id 复用也无 set 唯一约束 |
| **时钟偏移破坏 5min 窗口** | reaper 用 Java `LocalDateTime.now()` 与 submission.created_at (也是 Java 时钟) 对比, 多 worker 时钟漂移 → 早扫/晚扫 | created_at 应来自 DB `NOW()` |

### 1.2 现有事实 (Codex 已扫读)

- `AdminSubmissionServiceImpl.rejudge` / `batchRejudge` 已经在用 — 把终态改回 Pending 是**正在使用的业务流程** , 不能不支持
- `QueueConfig` 中 `judgeQueue` 实例化为 `RedissonClient.getQueue(...)` 用 `JsonJacksonCodec` , list 语义
- `submission.created_at` 当前由 Java 侧 `setCreatedAt(LocalDateTime.now())` 写入 — Codex F3 #3 验证 (`rg "setCreatedAt"`)

## 2. Decision

本 ADR 同时引入四件相互锁紧的机制:

### 2.1 Outbox 表 (替代 afterCommit + reaper, 解决 F3)

```sql
CREATE TABLE judge_outbox (
    id              VARCHAR(36)  PRIMARY KEY,
    submission_id   VARCHAR(36)  NOT NULL,
    generation      BIGINT       NOT NULL,                  -- ADR-003 §2.2 fence
    payload         JSON         NOT NULL,                  -- 完整 JudgeJob 序列化
    state           VARCHAR(16)  NOT NULL DEFAULT 'PENDING', -- PENDING / SENT / DEAD
    attempts        INT          NOT NULL DEFAULT 0,
    last_error      TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at         TIMESTAMP    NULL,
    next_retry_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uniq_dispatch (submission_id, generation),    -- ★ 同一代次最多入队一次
    KEY idx_state_retry (state, next_retry_at)
);
```

**关键不变量**:

- `(submission_id, generation)` 唯一 → 双重入队物理不可能
- `created_at` / `next_retry_at` 均使用 DB `CURRENT_TIMESTAMP` , 不再依赖 Java 时钟
- `state` 离散三态, 不允许回退 PENDING (失败 → DEAD 由 outbox-worker 决定)

```java
@Transactional
public SubmissionVO submit(...) {
    submissionMapper.insert(submission);
    judgeOutboxMapper.insert(JudgeOutboxRecord.of(submission, generation));   // 同事务
    return vo;
}

@Component
public class JudgeOutboxDispatcher {
    @Scheduled(fixedDelay = 2000)
    public void dispatch() {
        var batch = outboxMapper.claim(NOW, batchSize=50);   // SELECT ... FOR UPDATE SKIP LOCKED
        for (var row : batch) {
            try {
                judgeQueue.enqueue(row.toJudgeJob());        // Redis 端做 idempotent (见 §2.4)
                outboxMapper.markSent(row.id, NOW);
            } catch (Exception e) {
                outboxMapper.markRetry(row.id,
                    nextRetry = NOW + backoff(row.attempts + 1),
                    error = truncate(e));
                if (row.attempts + 1 >= maxAttempts) outboxMapper.markDead(row.id);
            }
        }
    }
}
```

**afterCommit + 5min reaper 方案彻底废弃** (ADR-000 §5 永久拒绝清单) 。

### 2.2 Generation Fence (解决 F2 之"旧 worker 覆盖新 rejudge")

`submission` 表加列:

```sql
ALTER TABLE submission
    ADD COLUMN generation BIGINT NOT NULL DEFAULT 1,
    ADD COLUMN current_attempt_id VARCHAR(36) NULL,
    ADD COLUMN judging_lease_expires_at TIMESTAMP NULL,
    ADD KEY idx_lease_expiry (status, judging_lease_expires_at);
```

**规则**:

- 每次 admin rejudge → `UPDATE submission SET generation = generation + 1, status = 'Pending', current_attempt_id = NULL, judging_lease_expires_at = NULL WHERE id = ?` (单 UPDATE 原子)
- 入 outbox 必须带 `generation`
- worker 处理时, 写结果前 CAS: `UPDATE ... SET status = ?, ... WHERE id = ? AND generation = ? AND current_attempt_id = ?` — 任一不匹配, **结果丢弃, 不写 DB**
- 因此旧 worker 苏醒后写结果会被 fence (generation mismatch) → DEBUG 日志 + 指标 `judge.stale_result.dropped`

### 2.3 JUDGING Lease + Heartbeat (解决 F2 之 "worker 崩 → JUDGING 永久卡死")

- worker 抓任务时: `UPDATE submission SET status = 'Judging', current_attempt_id = ?, judging_lease_expires_at = NOW() + INTERVAL leaseTtl SECOND WHERE id = ? AND status IN ('Pending') AND generation = ?` (CAS)
- worker 处理过程中**每 leaseTtl/3 秒**续约: `UPDATE ... SET judging_lease_expires_at = NOW() + INTERVAL leaseTtl SECOND WHERE id = ? AND current_attempt_id = ?`
- 续约失败 (affected = 0) → 当前 attempt 已被 reaper 接管, worker 主动放弃结果

**新 Reaper** (替代原 PENDING-only 版本):

```java
@Scheduled(fixedDelay = 5000)
public void recoverExpiredLeases() {
    // 把过期 JUDGING 回退为 Pending, 同时 bump generation 防止老 worker 再写
    submissionMapper.expireLease(NOW);
    // SQL:
    //   UPDATE submission
    //   SET status = 'Pending',
    //       generation = generation + 1,
    //       current_attempt_id = NULL,
    //       judging_lease_expires_at = NULL
    //   WHERE status = 'Judging'
    //     AND judging_lease_expires_at < NOW();
    // 然后为这批补 outbox 记录 (新 generation)
}
```

### 2.4 Queue Port (窄接口) + Idempotent Enqueue

```java
public interface JudgeQueue {
    /** 幂等: 同一 (submissionId, generation) 重复 enqueue 无副作用 */
    void enqueue(JudgeJob job);

    /** 阻塞 timeout, 返回时已通过 Redis 端 SETNX 占位 */
    Optional<JudgeJob> poll(Duration timeout);

    JobStatus getStatus(String jobId);
    QueueStats stats();
}
```

**Redis Adapter 实现**:

- 用 **Redisson `RBucket<String> SETNX`** 占 `judge:dispatch:{submissionId}:{generation}` 键 (TTL = leaseTtl × 5) 做去重 → 第二次 enqueue 检测到 key 存在直接 noop
- 任务体放 `RList<JudgeJob>` (向后兼容现有 worker poll 模式)
- worker poll 成功后**不立即删 dispatch 键** (留到 outbox markSent 之后被 outbox-worker 显式删), 防止 ack 期间崩溃导致丢失

`InMemoryAdapter` 用 `ConcurrentHashMap` (dispatch dedup) + `LinkedBlockingDeque` (任务体) , 等价语义。

### 2.5 状态机 ALLOWED 表 (扩展)

```java
public enum SubmissionStatus { /* 见 ADR-001 */ }

public final class SubmissionStateMachine {
    private static final Map<SubmissionStatus, Set<SubmissionStatus>> SYSTEM_ALLOWED = Map.of(
        PENDING,              Set.of(JUDGING, SYSTEM_ERROR),
        JUDGING,              Set.of(ACCEPTED, PRESENTATION_ERROR, WRONG_ANSWER,
                                     TIME_LIMIT_EXCEEDED, MEMORY_LIMIT_EXCEEDED,
                                     OUTPUT_LIMIT_EXCEEDED, RUNTIME_ERROR,
                                     COMPILE_ERROR, SANDBOX_ERROR, SYSTEM_ERROR,
                                     PENDING /* via lease expiry */)
        // 终态: SYSTEM_ALLOWED 无 entry → 系统侧禁止迁移
    );

    /** Admin rejudge 是显式跨 terminal→Pending 的特权操作, 走专用 path */
    private static final Set<SubmissionStatus> ADMIN_REJUDGE_FROM = EnumSet.of(
        ACCEPTED, PRESENTATION_ERROR, WRONG_ANSWER, TIME_LIMIT_EXCEEDED,
        MEMORY_LIMIT_EXCEEDED, OUTPUT_LIMIT_EXCEEDED, RUNTIME_ERROR,
        COMPILE_ERROR, SANDBOX_ERROR, SYSTEM_ERROR
    );

    public static boolean canSystemTransition(SubmissionStatus from, SubmissionStatus to) { ... }
    public static boolean canAdminRejudgeFrom(SubmissionStatus from) {
        return ADMIN_REJUDGE_FROM.contains(from);
    }
}
```

Admin rejudge 调用 `AdminSubmissionServiceImpl.rejudge(submissionId)` 内部走 generation bump + outbox 重投, 不走 SYSTEM_ALLOWED 通道。

## 3. Consequences

### 3.1 Positive

- **任务丢失收敛到 0** — outbox 是 DB 事务一部分, 进程崩了下次启动 outbox-dispatcher 接力
- **双重入队物理不可能** — `(submission_id, generation)` 唯一约束 + Redis SETNX 双保险
- **JUDGING 卡死可自愈** — lease 过期 → reaper 接管 → generation bump → 新 worker 接手
- **旧 worker 写结果被 fence** — generation/attempt mismatch 直接丢弃
- **admin rejudge 一等支持** — 不再依赖"塞进 ALLOWED 表"的 hack
- 所有时间戳来自 DB `NOW()` , 不受 Java/容器时钟漂移影响

### 3.2 Negative

- 加 1 张表 + 修改 1 张表 + 2 个新 worker (outbox dispatcher / lease reaper)
- DB 写放大: submit 一次写 2 行 (submission + outbox), update 加 generation 比较
- Outbox `SELECT ... FOR UPDATE SKIP LOCKED` 需 MySQL 8+ (项目 9.1, 满足)
- Heartbeat 增加 DB QPS (每 worker 每 lease/3 秒一次 UPDATE) — 实测评估, 必要时改 Redis 续约

### 3.3 Risks

| 风险 | 缓解 |
|---|---|
| Outbox 表无限增长 | `state=SENT AND sent_at < NOW() - 7 DAY` 周期归档 / 删除 |
| `FOR UPDATE SKIP LOCKED` 在主从复制场景行为 | 项目目前单 primary, 上从库前在 ADR 中校核 |
| Lease TTL 调过短 → 大量 heartbeat 撞库 / 调过长 → 故障恢复慢 | 默认 60s, lease/3=20s heartbeat;指标 `judge.lease.miss_renew` 监控调参 |
| Admin rejudge 在 JUDGING 时被调用 | `AdminSubmissionService.rejudge` 内部先 `judging_lease_expires_at = NOW()` 强制过期, 等 reaper 处理后再 bump generation |
| 历史 submission 没有 generation 列 | 迁移脚本 `UPDATE submission SET generation = 1 WHERE generation IS NULL` , 然后 `ALTER COLUMN NOT NULL` |

## 4. Validation

- [ ] Outbox round-trip 集成测试 (Testcontainers MySQL + Redis): submit → outbox 落库 → dispatcher 入队 → worker 消费 → markSent
- [ ] 故意杀 worker 进程在 JUDGING 中, 验证 reaper 5-10 秒内回收, 新 worker 接管, 最终 verdict 正确
- [ ] Admin rejudge 并发场景: 同时 rejudge × 2 + 旧 worker 苏醒, 最终只有最新 generation 结果写入
- [ ] 模拟 Redis 网络分区: outbox-dispatcher 抛错 → next_retry_at 退避 → 网络恢复后自动续投
- [ ] Flyway 迁移 `V20260613xxxx__Add_Outbox_And_Fence.sql` 在 CI 通过 (含 historical data backfill)
- [ ] grep 确认旧 `TransactionSynchronizationManager.registerSynchronization` 用于入队的代码为零

## 5. References

- [ADR-000 / F2 + F3](./ADR-000-hexagonal-grilling-session.md) — Codex finding 原文
- [ADR-001](./ADR-001-verdict-status-codec.md) — SubmissionStatus + Kind
- [ADR-005](./ADR-005-rolling-deploy-playbook.md) — 拆分到 M2a/M2b/M2c
- 现有代码: `backend-spring/.../admin/service/impl/AdminSubmissionServiceImpl.java#rejudge` (rejudge 流程参考)
- 现有代码: `backend-spring/.../queue/service/impl/QueueServiceImpl.java` (RQueue 用法)
- Outbox Pattern: Chris Richardson, "Microservices Patterns" Ch. 3
- 项目规约: `.claude/rules/backend/07-java-design.md` (#1 存储方案评审, #3 状态图)
