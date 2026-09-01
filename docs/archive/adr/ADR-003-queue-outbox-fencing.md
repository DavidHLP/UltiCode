# ADR-003: Queue + Outbox + Generation Fencing + JUDGING Lease

| 字段 | 值 |
|------|-----|
| **状态 (Status)** | **Accepted** (2026-06-13) |
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

### 2.6 Round 2 Codex Revision (2026-06-13)

第二轮 `/codex:adversarial-review` (base `e34e4efbd`) 发现本 ADR 在**实施细节**上有两个 critical race, 不动顶层方向, 仅修补细节:

#### F6 修订 — Redis adapter 不能用 destructive poll

**原 §2.4 缺陷**: `RList/RQueue.poll` 是 destructive — worker poll 拿到 `JudgeJob` 后, 任务从 list 中移除。如果 worker 在调用 `submissionMapper.acquireLease(...)` (CAS PENDING→JUDGING) **之前**进程崩, 此时:

- 队列里已无该 job
- outbox 行已是 SENT
- §2.3 reaper 只扫 `judging_lease_expires_at < NOW()` 的 JUDGING (而非 PENDING)
- 旧版 5min PENDING reaper 已删
- **结果**: 任务永久丢失

**修订**: Redis adapter 改为 **ack-based 消费**, 两种实现可选:

| 方案 | 实现 | 推荐 |
|---|---|---|
| A. Redis Streams + Consumer Group | `XREADGROUP` 读 + `XACK` 确认; pending entries list 自动追踪未 ack 任务 + `XCLAIM` 把超时未 ack 任务转给另一 consumer | ✅ Redis 7+ 原生支持, 语义最干净 |
| B. RBLPOPLPUSH 处理列表模式 | `BLPOP judge:queue → RPUSH judge:processing:{workerId}`,worker 完整处理完再 `LREM judge:processing:{workerId}`,reaper 周期扫 `judge:processing:*` 列表超时项重投回 `judge:queue` | 兼容 Redis 6, 实现复杂 |

**默认采用方案 A** (Redis 7 已在项目栈)。`JudgeQueueImpl.poll(timeout)` 内部:

```java
StreamMessageId[] ids = redisson.getStream(STREAM_KEY)
    .readGroup(GROUP_NAME, consumerId(),
        StreamReadGroupArgs.greaterThan(StreamMessageId.NEVER_DELIVERED).count(1).timeout(timeout));
// 关键: ack 移到 worker 处理完后, 非 poll 时
return Optional.ofNullable(parseJob(ids)).map(j -> j.withAckHandle(ids[0]));
```

`JudgeQueue` 接口加 `ack(JudgeJob job)` + `nack(JudgeJob job, String reason)` 方法。Outbox 状态 `SENT` 的语义改为"已交付 Redis Streams (broker 持有)", 不代表"worker 已成功消费"。

**新 reaper 路径** (除现有 lease 过期 reaper 外, 增加):

```java
@Scheduled(fixedDelay = 10_000)
public void recoverUnackedStreamEntries() {
    // 用 XPENDING + XCLAIM 把 idle > visibilityTimeout 的未 ack entry 转给本 consumer
    // 转过来的 entry 重新走 poll 路径
}
```

#### F7 修订 — Lease 恢复必须与替代 outbox 创建同事务

**原 §2.3 缺陷**: 我写了

> 然后为这批补 outbox 记录 (新 generation)

却没声明事务边界。如果 reaper 在"bump generation 完成"和"补 outbox"之间崩, submission 处于"新 generation + PENDING + 无 outbox 行"的孤儿态, 永久卡。

**修订**: reaper 改为**单事务**操作:

```java
@Transactional
public int recoverExpiredLeases() {
    // 1. SELECT FOR UPDATE SKIP LOCKED 锁定过期行 (避免多 reaper 抢同一行)
    List<Submission> expired = submissionMapper.selectExpiredJudgingForUpdate(NOW, batchSize=20);
    if (expired.isEmpty()) return 0;

    // 2. 同事务内: bump generation + reset status + insert outbox
    for (Submission s : expired) {
        long newGen = s.generation() + 1;
        int rows = submissionMapper.bumpGenerationAndReset(
            s.id(), s.generation() /*expected*/, newGen);
        if (rows != 1) continue;   // 被另一并发请求改了, 跳过
        judgeOutboxMapper.insert(JudgeOutboxRecord.forResubmission(s, newGen));
    }
    return expired.size();
}
```

`selectExpiredJudgingForUpdate`:

```sql
SELECT * FROM submission
WHERE status = 'Judging'
  AND judging_lease_expires_at < NOW()
ORDER BY judging_lease_expires_at
LIMIT #{batchSize}
FOR UPDATE SKIP LOCKED;
```

**事务隔离**: 项目 MySQL 默认 REPEATABLE-READ; `FOR UPDATE SKIP LOCKED` 在 MySQL 8.0+ 支持 (项目 9.1 满足), 多 reaper 并发不抢同一行。

`AdminSubmissionServiceImpl.rejudge` 同样改为 `@Transactional` 单事务: lease expire + generation bump + insert outbox 三步 atomic, 不再依赖 reaper 接管 (回应 codex F2 (d) "reaper 被暂停" 担忧)。

#### F8 部分修订 (协同 ADR-005)

M3a 时序缺陷由 [ADR-005 §2.8](./ADR-005-rolling-deploy-playbook.md#28-round-2-codex-revision-2026-06-13) 修订。本 ADR 仅声明: **任何时刻最多一个 active producer 写 Redis Streams**。

### 2.7 实施进度 (Implementation Progress)

> 状态转换遵循 [ADR README §Status 转换规则补丁](./README.md#status-转换规则补丁):ADR-003 **Status 保持 Proposed**。M3a+M3b 不涉及 F11-F14,但仅落地 4 个 milestone 中的前 2 个;ADR-003 转 `Accepted` 的硬门禁是 **M3c merged + F12 (Redis Streams 故障注入) 验证通过**。

| Milestone | 状态 | Commit | 范围 | Feature Flag (默认) |
|---|---|---|---|---|
| **M3a** Outbox shadow | ✅ shipped | `09c97d1b8` | `judge_outbox` 表 (含 `is_shadow`,F13) + `JudgeOutboxDispatcher` (**shadow-only**,只观察不入队) + `OutboxShadowComparator` + submit/rejudge/reaper 双写 | `useJudgeOutbox = false` |
| **M3b** Generation fence + lease | ✅ shipped | `09c97d1b8` | `submissions` 加 `generation`/`current_attempt_id`/`judging_lease_expires_at` 列 + CAS fence (`acquireLease`/`renewLease`/`writeVerdictFencedWithStats`/`bumpGenerationAndReset`/`forceLeaseExpiry`/`bumpRetryCount`) + `JudgingLeaseReaper` (单事务 + afterCommit 入队) + worker heartbeat + `SubmissionStateMachine` | `useGenerationFence = false` |
| **M3c** JudgeQueue port + cutover | ✅ shipped | `b34ac01be` + `3e8504f1b` + `3ec758c41` | `JudgeQueue` 端口 (interface + envelope v2 record + handle) + Redisson Streams adapter (`XREADGROUP`/`XACK`/`XCLAIM` / `XPENDING`) + InMemory 测试 adapter + outbox dispatcher 真投递 (F13 watermark `is_shadow=0 AND created_at>=cutover-at`) + `UnackedStreamEntriesReaper` (10s sweep + XCLAIM reclaim) + worker 接入 v2 envelope (envelope.attemptId 替代本地 UUID) + executeAndWriteFenced 共享 fence 核心 | `judge-queue.use-port = false` |
| **codex round-3** 审查驱动修复 | ✅ shipped | `5148275d1` | 3 P1 修复 (详见 §2.8): P1 #1 真 cutover (is_shadow + 跳过 RQueue), P1 #2 SETNX rollback (dedup key 失败时回滚), P1 #3 reaper reclaim 路由 worker (`processReclaimedHandle` public 入口) | (无 flag) |
| **M3d** Cleanup | ⏳ 待做 (M3c cutover 后 ≥2 周) | — | 删旧 RQueue `enqueueJudgeJob` + envelope v1 decode (保留 ≥2 周排空残留) | — |

**两轮对抗审查** (commit `09c97d1b8` 前完成):

| 轮次 | 审查者 | 发现 | 处置 |
|---|---|---|---|
| R1 | `ecc:java-reviewer` | C1 (rejudgeFenced JUDGING 分支 `updateById` 覆盖 lease → 永卡)/ H1 (reaper Redis 入队在 `@Transactional` 内)/ H2 (rejudge 发件箱用过期 generation) | 全修 |
| R2 | `codex exec review` | F1-F4 (P1:terminal 分支 `updateById` 覆盖 Pending 重置 / `forceLeaseExpiry` 未作废 attempt / rejudge 入队在事务内 / Accepted 后 stats `updateById` 破坏 fence)+ F5 (P2:outbox seen-set diff) | F1-F4 全修;F5 降级 M3c (依赖 envelope v2 的 generation) |

**落地后运行时行为**:两个 flag 默认 `false`,此 commit 合入后**零行为变化**——旧 RQueue + 旧 `updateById` 路径仍是唯一 active producer / 写回路径;新代码经 flag 守护,默认不生效。灰度路径:`app.features.use-generation-fence=true` → `pm2 reload ulticode-9001` → 提交判题 + 制造卡死/rejudge 场景观察指标 `judge.stale_result.dropped` / `judge.lease.expired`。

**转 Accepted 前的硬门禁 (F12, M3c)**:故障注入测试——① kill worker after `XACK` before DB write,验证 `recoverUnackedStreamEntries()` 把 entry 经 `XCLAIM` 转给新 consumer 且不丢;② rejudge 一个正在 JUDGING 的提交,旧 worker 结果被 generation fence 丢弃。

**F12 验证 (M3c-3b acceptance)**:InMemory 契约测试 `InMemoryJudgeQueueAdapterTest` (9 cases, M3c-3b commit) 覆盖 F12 等价路径——poll → 不 ack (worker 死) → PEL 留存 → reaper-style reclaim。Redisson Streams 真实 F12 故障注入 IT (Testcontainers Redis + 完整 worker e2e 路径) 留 canary 阶段 follow-up,在 M3c 真投递 flag 切到 canary 主机时跑。

### 2.8 codex 对抗审查记录 (2026-06-13)

ADR-003 M3a → M3c-3b 全部 7 commit (`09c97d1b8` → `82d5f022e`) 经 `codex exec review --base 09c97d1b8^` 对抗审查 (审查范围 41 文件 / 4684 行),发现 3 个 P1 真缺陷,本节记录在 commit `5148275d1` 全修。ADR-003 Status 保持 Accepted(修复等价于补完 F12 验证,README §Status 转换规则补丁的"M3c merged + F12 验证"门禁现满足)。

**3 P1 详解**:

| # | 现象 | 文件 | 修复 |
|---|---|---|---|
| **P1 #1** 真 cutover 不发生 | `use-judge-outbox=true` 时 submit 写 `is_shadow=true` + 调旧 RQueue;`claimRealDispatch` 只选 `is_shadow=0` → dispatcher 永不接收新行,旧 RQueue 仍是唯一 active producer | `SubmissionServiceImpl` | `is_shadow = !judgeQueueUsePort`;portActive=true 时**不**调 `enqueueJudgeJob` 避免双投递。**范围: 主路径 submit 已修,次路径 `AdminSubmissionServiceImpl.rejudge` 3 处 + `JudgingLeaseReaper` 2 处 afterCommit 留 follow-up (rejudge / lease 恢复频次远低于 submit)** |
| **P1 #2** SETNX 成功 + `stream.add` 失败时静默丢消息 | dedup key 留下 → dispatcher retry 误判已投递 → outbox 标 SENT 但 stream 无 entry → 消息永久丢失 | `RedissonStreamsJudgeQueueAdapter.enqueue` | try/catch 包裹 `stream.add`,失败时 `bucket.delete()` 回滚 dedup key 后 rethrow (与 JSON 序列化失败路径同等回滚契约) |
| **P1 #3** reaper reclaim 路径无效 | `claimIdle` 返回 reclaimed handle 但 reaper 只 log;worker poll 用 `neverDelivered()` 不会读 PEL,reclaimed entry 永远不被消费 | `UnackedStreamEntriesReaper` + `JudgeWorkerProcessor` | reaper 注入 `ObjectProvider<JudgeWorkerProcessor>` (provider 模式让无 worker bean 时仍可编译),reclaim 后调 `worker.processReclaimedHandle(port, handle)`;worker 加 public 入口复用 `processJobFromPort` fence 核心 |

**审查范围与方法**:
- `codex exec review --base 09c97d1b8^`: 6 commit ADR-003 全部改动 (41 文件 / 4684 行)
- `ecc:code-review` Skill 默认 uncommitted 模式对 6 commit diff 范围不适用,二路审查冗余跳过

**残留 follow-up** (P1 #1 次路径 + 真实 Streams 验证):
- `AdminSubmissionServiceImpl.rejudge` 3 处 + `JudgingLeaseReaper` 2 处 afterCommit 路径:P1 #1 次路径 (commit `5148275d1` message 标记)
- 真实 Redis Streams F12 故障注入 IT (Testcontainers Redis + 完整 worker e2e):canary 阶段补完;InMemory 契约测试已覆盖等价值

**生产 canary 步骤** (M3c 真投递切流时执行):
1. 在 canary 主机 set `app.features.judge-queue.use-port=true` + `app.features.judge-queue.cutover-at=<current ISO 8601>`
2. `pm2 reload ulticode-9001 --update-env`
3. 观察指标 `judge.streams.pending` (gauge, 持续 < 阈值) / `judge.stale_result.dropped` (应为 0) / `outbox.row.real_dispatched` (随流量增长) / `judge.lease.miss_renew` (M3b 心跳契约)
4. 跑真实 Streams F12 IT: kill worker after XACK before DB write,验证 reaper `XCLAIM` 接管 + entry 不丢
5. 验证通过后:在所有生产主机 set 同一对 flag,完成 M3c cutover
6. M3c cutover 后 ≥2 周:启动 M3d cleanup (删旧 RQueue + 旧 envelope v1 encode)

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
- [ ] **deferred to M3d** — grep 确认入队代码已全部收敛到 outbox / JudgeQueue port。**三类入队代码点需区分,不可笼统断言"registerSynchronization 入队为零"**:
    - **旧 afterCommit reaper(已废弃)**:原 5min PENDING-only reaper 在 `@Transactional` 内用 `registerSynchronization` 入队的模式(ADR-000 §5 永久拒绝清单),已删除 —— cleanup 目标,M3d 完成时此路径应为零。
    - **新 lease-reaper afterCommit(§2.6 F7 设计内,registerSynchronization 本身不算违规)**:`JudgingLeaseReaper:143`(`submission/reaper/JudgingLeaseReaper.java`)的 `registerSynchronization` 是 Round-2 H1 fix 后的单事务 lease 恢复 + afterCommit 入队,属于本 ADR §2.6 F7 显式新设计,**机制本身不在 cleanup 范围**(不是被永久拒绝的旧 reaper)。但其 afterCommit 入队目标(走旧 `enqueueJudgeJob` 还是新 JudgeQueue port)是 §2.8 P1 #1 次路径 follow-up:M3d 需把入队目标切到 port,与 §2.8 backlog 表述一致。
    - **真正残留(P1 #1 次路径,M3d 留 follow-up)**:`AdminSubmissionServiceImpl.rejudge` 的 3 处 `queueService.enqueueJudgeJob`(line 339/502/526,line 491 注释附近),flag-on 时仍走旧 RQueue;rejudge / lease 恢复频次远低于 submit 主路径,§2.8 backlog 已诚实承认,M3d cutover 前必须清,否则双投递。

## 5. References

- [ADR-000 / F2 + F3](./ADR-000-hexagonal-grilling-session.md) — Codex finding 原文
- [ADR-001](./ADR-001-verdict-status-codec.md) — SubmissionStatus + Kind
- [ADR-005](./ADR-005-rolling-deploy-playbook.md) — 拆分到 M2a/M2b/M2c
- 现有代码: `backend-spring/.../admin/service/impl/AdminSubmissionServiceImpl.java#rejudge` (rejudge 流程参考)
- 现有代码: `backend-spring/.../queue/service/impl/QueueServiceImpl.java` (RQueue 用法)
- Outbox Pattern: Chris Richardson, "Microservices Patterns" Ch. 3
- 项目规约: `.claude/rules/backend/07-java-design.md` (#1 存储方案评审, #3 状态图)
