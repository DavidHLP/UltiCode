# ADR-004: Notification Intents + Per-Channel Projection + 失败隔离

| 字段 | 值 |
|------|-----|
| **状态 (Status)** | Accepted |
| **日期 (Date)** | 2026-06-13 |
| **作者 (Author)** | DavidHLP |
| **解决的 Finding** | [ADR-000 / F4](./ADR-000-hexagonal-grilling-session.md#2-codex-adversarial-review-摘要) |
| **依赖 ADR** | [ADR-001](./ADR-001-verdict-status-codec.md) (SubmissionCompletedIntent 含 SubmissionStatus), [ADR-003](./ADR-003-queue-outbox-fencing.md) (含 submissionId+generation) |
| **关联代码** | `notification/service/NotificationDispatchService.java`,`websocket/service/RealtimeService.java`,`email/service/EmailService.java`,`achievement/.../AchievementTriggerServiceImpl.java` (event 风格参考) |
| **关联 DB** | 不修改 `notification_preferences` 表 (channel-level preference 列入未来 ADR) |
| **实施 commits** | M4a: `feat(notification): ADR-004 M4a — ledger + intent records + dispatcher skeleton` (`e38e340`) · M4b: `feat(notification): ADR-004 M4b — 3 channel impls + EmailTemplates` (`bf02f48ec`) · M4c: `refactor(notification): ADR-004 M4c — migrate 4 callers to typed intent dispatch` (`9ecf10ec9`) · M4d: `test(notification): ADR-004 M4d — dispatcher contract + idempotency + perf + per-channel tests` (`62a4dcabe`); **M4d-1 follow-up** (2026-06-14): `fix(notification): ADR-004 M4d-1 review — NPE / wire-contract / silent-skip` (`d32882198`) · `feat(notification): ADR-004 M4d-1 — intentId 防撞` (`b7dc1378c`) · `refactor(notification): ADR-004 M4d-1 — 删 NotificationCategory.CONTEST dead branch` (`ce629194b`) · `feat(notification): ADR-004 M4d-1 — NotificationLedgerReaper` (`33c9a41ba`) |

---

## 1. Context

### 1.1 现状: 三模块孤立

| 模块 | 渠道 | 主要 entity |
|---|---|---|
| `notification` | In-App (`Notification` 表) | `Notification` + `NotificationPreference` (字段 `communication`/`marketing`/`security`/`system` 四 boolean) |
| `email` | SMTP | `EmailService` |
| `websocket/RealtimeService` | WebSocket | 实时 STOMP 推送 |

业务侧 (`SubmissionService` / `AchievementTriggerService` / `ContestService`) 想做 "全渠道通知" 必须手工调三次。

### 1.2 Codex F4 否决的原方案

原 ADR-001 §2.5 提议:

```java
@EventListener
void onSubmissionCompleted(SubmissionCompletedEvent e) {
    channels.stream()
        .filter(c -> prefs.allows(c.channelId(), e.category()))   // ❌ 没有 channel-level pref
        .forEach(c -> c.send(envelope(e)));                       // ❌ 一处异常中断 stream
}
```

具体缺陷:

1. `prefs.allows(channelId, category)` 假设 `NotificationPreference` 支持 channel × category 二维, 实际只有 category 维 (四个 boolean) 。**模型不存在**
2. **泛型 envelope** 装不下渠道特化数据: email 要 recipient / template / HTML、WebSocket 要 contest / score / time / memory、In-App 要 link / metadata。塞进 `Map<String, Object>` 等于把强类型扔了
3. `supports()` 声明却没人调
4. `.forEach(c -> c.send(...))` 一个 channel 抛异常 → stream 中断 → 后续 channel 不发
5. 异步 in-process event 既没重试也没持久化

### 1.3 已知约束 (本 ADR 不改)

- `notification_preferences` 表 (字段: `communication` / `marketing` / `security` / `system` 四 boolean) 保留, channel-level preference 列入**未来 ADR** (依赖前端 UI 改造)
- AchievementTriggerService 已有的 `ApplicationEventPublisher` + `@Async` 模式保留, 团队熟悉

## 2. Decision

### 2.1 NotificationIntent (typed sum type 替代泛型 envelope)

```java
/** 顶层密封接口, sealed 防止三方乱加 */
public sealed interface NotificationIntent
    permits SubmissionCompletedIntent,
            AchievementEarnedIntent,
            ContestStartingIntent,
            FollowReceivedIntent,
            CommentReplyIntent,
            SystemAlertIntent {

    String userId();
    NotificationCategory category();          // 仍是 4 类: COMMUNICATION / MARKETING / SECURITY / SYSTEM
    String intentId();                        // 幂等 key
}

public record SubmissionCompletedIntent(
    String intentId,
    String userId,
    String submissionId,
    long generation,                          // ← ADR-003 fence
    SubmissionStatus status,                  // ← ADR-001 enum
    String problemId,
    String problemTitle,
    long elapsedMs,
    long memoryBytes,
    String contestId,                         // nullable
    Long contestScoreDelta,                   // nullable
    NotificationCategory category
) implements NotificationIntent {}

public record AchievementEarnedIntent(
    String intentId,
    String userId,
    String achievementId,
    String achievementName,
    String achievementIconUrl,
    NotificationCategory category
) implements NotificationIntent {}

// ... 其余 intent
```

**关键不变量**:

- 每种业务事件**对应一个 record 类型** , 字段强类型, 编译期校验
- 字段集合**最大化保留场景信息** , 渠道按需投影 (即使 email 不用 memoryBytes, 也保留以备 contest 邮件模板)
- `intentId` 是幂等 key, 加 `(intentId, channelId)` 双键去重

### 2.2 Channel Port (per-intent 显式 `supports()` 被强制调用)

```java
public interface NotificationChannel {
    String channelId();                                       // "in_app" / "email" / "websocket"
    boolean supports(NotificationIntent intent);              // 由 Dispatcher 强制调用
    void send(NotificationIntent intent);                     // 失败抛 ChannelDispatchException
}
```

每个 channel 自己负责"intent → channel projection":

```java
@Component
public class EmailNotificationChannel implements NotificationChannel {
    @Override public String channelId() { return "email"; }

    @Override public boolean supports(NotificationIntent intent) {
        return switch (intent) {
            case SubmissionCompletedIntent s -> s.status().kind() != Kind.IN_FLIGHT;
            case AchievementEarnedIntent a -> true;
            case ContestStartingIntent c -> true;
            default -> false;
        };
    }

    @Override public void send(NotificationIntent intent) {
        EmailMessage msg = switch (intent) {
            case SubmissionCompletedIntent s -> EmailTemplates.submissionResult(s);
            case AchievementEarnedIntent a -> EmailTemplates.achievementEarned(a);
            case ContestStartingIntent c -> EmailTemplates.contestStarting(c);
            default -> throw new IllegalStateException("Unsupported intent: " + intent);
        };
        emailService.send(msg);
    }
}
```

### 2.3 Dispatcher: 失败隔离 + 强制 `supports()`

```java
@Component
@RequiredArgsConstructor
public class NotificationDispatcher {
    private final List<NotificationChannel> channels;
    private final NotificationPreferenceService preferences;

    public void dispatch(NotificationIntent intent) {
        var pref = preferences.get(intent.userId());
        if (!pref.allowsCategory(intent.category())) return;     // category 级仍生效

        for (var channel : channels) {
            if (!channel.supports(intent)) continue;
            try {
                channel.send(intent);                            // 各 channel 独立 try
            } catch (Exception e) {
                log.error("channel {} failed for intent {}: {}",
                          channel.channelId(), intent.intentId(), e.toString());
                meterRegistry.counter("notification.dispatch.failure",
                    "channel", channel.channelId(),
                    "intent", intent.getClass().getSimpleName()).increment();
                // 不 rethrow, 继续下一个 channel
            }
        }
    }
}
```

### 2.4 业务侧统一入口

业务模块**不再调用** `EmailService` / `NotificationDispatchService` / `RealtimeService` , 改成:

```java
// SubmissionServiceImpl 或 JudgeWorkerProcessor
notificationDispatcher.dispatch(new SubmissionCompletedIntent(...));

// AchievementTriggerServiceImpl (替换现有 ApplicationEventPublisher.publishEvent 调用)
notificationDispatcher.dispatch(new AchievementEarnedIntent(...));
```

也可以保留 `ApplicationEventPublisher` 作为"业务发事件 → 单一 NotificationListener 转 intent → dispatch" 二段式, 但**不强制** 。本 ADR 默认走 dispatcher 直调, 调用链 IDE 可追。

### 2.5 持久化与可靠性

In-App channel 写 `Notification` 表 (DB 原子保证); Email 走 SMTP 调用, 失败计入指标但不重试 (邮件失败可接受); WebSocket 推送是 best-effort (用户不在线本就丢) 。

**真正需要持久化重试的 intent** (如 contest 关键通知) → 在该 intent 上加 `@RequiresDurable` 标记, dispatcher 走 outbox path (复用 ADR-003 Outbox 机制) → 未来 ADR 扩展, 不在本 ADR 范围。

### 2.6 channel × category 二维 preference

**本 ADR 不引入** — 保持现有 4 category 模型。前端 UI / DB 迁移 / 用户引导是另一个工程, 列入未来 ADR-006 (notification-channel-preference) 。Dispatcher 此时通过 category 粗粒度过滤, channel 通过 `supports()` 自我裁决。

### 2.7 Round 2 Codex Revision (2026-06-13)

第二轮 codex 评审发现一处 high finding, 不动 typed intent 顶层方向, 仅修补幂等存储:

#### F9 修订 — Notification 幂等性必须有持久化存储

**原 §2.5 缺陷**: 我承诺 `(intentId, channelId)` 幂等, 但又声称"现有 `Notification` 表 schema 不变":

- `Notification` 表无 `intent_id` / `channel_id` 列, 无对应唯一索引
- §2.3 Email channel 的"内部去重 cache" 是 in-memory, 进程重启即丢
- 多副本 / pm2 reload 场景, 同 intent 可能投递 N 次

**修订**: 新增**独立** delivery ledger 表, 不动 `Notification` 业务表 schema:

```sql
-- V20260613xxxxxx__Create_Notification_Delivery_Ledger.sql
CREATE TABLE notification_delivery_ledger (
    id              BIGINT       AUTO_INCREMENT PRIMARY KEY,
    intent_id       VARCHAR(64)  NOT NULL,           -- NotificationIntent.intentId()
    channel_id      VARCHAR(32)  NOT NULL,           -- "in_app" / "email" / "websocket"
    user_id         VARCHAR(36)  NOT NULL,           -- 索引 + 故障排查
    intent_type     VARCHAR(64)  NOT NULL,           -- record class simpleName
    delivered_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    delivery_state  VARCHAR(16)  NOT NULL,           -- DELIVERED / SKIPPED / FAILED
    failure_reason  VARCHAR(500) NULL,
    UNIQUE KEY uniq_intent_channel (intent_id, channel_id),
    KEY idx_user_time (user_id, delivered_at)
);
```

**Dispatcher 改造**:

```java
public void dispatch(NotificationIntent intent) {
    var pref = preferences.get(intent.userId());
    if (!pref.allowsCategory(intent.category())) return;

    for (var channel : channels) {
        if (!channel.supports(intent)) continue;

        // 1. 预占 ledger (INSERT IGNORE), 已存在 → 跳过 (幂等)
        int inserted = ledgerMapper.tryClaim(
            intent.intentId(), channel.channelId(),
            intent.userId(), intent.getClass().getSimpleName());
        if (inserted == 0) {
            log.debug("intent {} channel {} already delivered, skip",
                      intent.intentId(), channel.channelId());
            continue;
        }

        // 2. 发送
        try {
            channel.send(intent);
            ledgerMapper.markDelivered(intent.intentId(), channel.channelId());
        } catch (Exception e) {
            ledgerMapper.markFailed(intent.intentId(), channel.channelId(), truncate(e));
            log.error("channel {} failed for intent {}", channel.channelId(), intent.intentId(), e);
            // 不 rethrow, 继续下一 channel (失败隔离仍生效)
        }
    }
}
```

`tryClaim` 用 `INSERT ... ON DUPLICATE KEY UPDATE id=id` 或者 `INSERT IGNORE`, 返回 affected rows == 0 表示已存在。

**In-App channel 内部**仍写 `Notification` 表 (业务可见的通知列表), 但**不再依赖 `Notification` 表做幂等** — 由 ledger 兜底。如果某 intent 已在 ledger 标 DELIVERED, In-App channel 直接 skip, 不会插入第二条 `Notification` 行。

#### 重试策略

ledger 状态 `FAILED` 不自动重试 (一些 channel 的失败不该重试, 例如用户邮箱无效)。需重试的 intent (例如 Contest 关键通知) 走另一路径:

- 标 `@RequiresDurable` 的 intent → dispatcher 提前写 `judge_outbox` 同款 outbox 表 ([ADR-003](./ADR-003-queue-outbox-fencing.md))
- Outbox dispatcher 拉出后调用 `NotificationDispatcher.dispatch(intent)` , 失败累计 `attempts`, 退避重投
- 这条路径**列入未来 ADR-007** (durable notifications), 本 ADR 仅声明: ledger UNIQUE 约束保证即使重投也不会重复投递

#### 不在本 ADR 修订范围

- channel × category 二维 preference 仍列入未来 ADR
- 邮件模板 i18n 完整化仍列入 frontend 后续工作

## 2.8 M4d-1 Review Follow-up (2026-06-14)

M4d 后跑了 7-angle adversarial review (line-by-line / removed-behavior / cross-file / reuse / simplification / efficiency / altitude), 7 个候选 findings 经 1-vote recall-biased verify 后**确认 6 个需修复** + 1 个细化 (Tier 4 副本) 列入 backlog。4 个 commit 落地,代码净增量 169 行,测试调整 5 个文件。

| # | Finding | 修复 | Commit |
|---|---|---|---|
| 1 | `EmailTemplates.forIntent` 5 处 `Map.of(...)` 对 `achievementName`/`contestTitle`/`replierUsername`/`title` 缺 null 合并 → null 字段导致 NPE | 加 `== null ? "" : ...` 守卫 | `d32882198` |
| 2 | `WebSocketNotificationChannel.send` 5 个 intent 的 `NotificationPayload.type` 用 lowercase (`"submission"`/`"follow"`/`"contest_reminder"`/`"reply"`/`"system"`),与 legacy 大写契约不符 — 前端 `payload.type === 'FOLLOW'` 等 case-sensitive 比较会失配 | 改回大写 (`SUBMISSION`/`FOLLOW`/`CONTEST REMINDER`/`REPLY`/`SYSTEM`) 对齐 legacy | `d32882198` |
| 3 | `EmailNotificationChannel.send` 在用户无 email 时 throw `BusinessException` → dispatcher 标 FAILED + warn spam;旧路径是静默 no-op | 改 throw 为 silent return + `log.debug`,ledger 自然 DELIVERED | `d32882198` |
| 4 | `tryClaim` 对已存在的 `CLAIMED` 行(进程 crash 后未转 DELIVERED/FAILED)也返回 0 → dispatcher 当 "已交付" 跳过,**永久丢该 channel 的投递**;`DeliveryState` Javadoc 显式承认 "future reaper" 缺失 | 新增 `NotificationLedgerReaper` (`@Scheduled(fixedDelay=5min)`) + mapper `reapStaleClaimed()` SQL;10min grace 覆盖慢 SMTP;非零 reaper 计数暴露在 `notification.ledger.reaper.reaped` 指标 | `33c9a41ba` |
| 5 | `SubmissionCompletedIntent.of()` 在 `submission.getGeneration() == null` 时 fallback 到 `0L` → 与真实 `generation=0` 撞 intentId,第二次 dispatch 静默 drop | factory 抛 `IllegalStateException` 强制 fail-fast (null gen 是 hydration bug,非用户数据) | `b7dc1378c` |
| 6 | `AchievementEarnedIntent.intentId()` 不含 `earnedAt` → tier-up 重新发布时同 intentId 被 ledger 去重,新事件 InApp/Email/WS 全部静默 drop;ADR §2.1 docstring 已承认是缺陷 | intent record 加 `earnedAt` 字段,intentId 格式 `achievement:{userId}:{achievementId}:at{epochMs}` | `b7dc1378c` |
| 7 | `NotificationCategory.CONTEST` 枚举值无 caller(全部 caller 用 `SYSTEM`);dispatcher 有 dead-branch 映射到 `p.getSystemEnabled()` 与 `SYSTEM` 分支等价 → typo 风险 | 删 enum 值 + 删 dead branch;未来有专用 preference 列时再加回 | `ce629194b` |

**未修列入 ADR-007 backlog** (低 severity,值得做但不阻塞 Accepted 状态):

- **#8 Tier 4 副本** — `tierSlug` (WebSocket) / `tierName` (EmailTemplates) / `getTierString` (AchievementNotificationListener + AchievementTriggerServiceImpl) 4 份 `1→Bronze/2→Silver/3→Gold/4→Platinum` 映射。建议抽到 `com.ulticode.modules.achievement.constants.Tier` enum with `displayName()`/`slug()`,由 achievement 模块拥有。
- **#9 sealed-type `IllegalStateException` 死代码** — 6 处 instanceof 链末尾的 `throw new IllegalStateException("Unhandled intent: " + ...)` 在 sealed `NotificationIntent` 下 unreachable;Java 17 无 switch pattern 只能保留,但 Javadoc 应说明 "sealed guarantees this is dead code; kept for future defensive programming"。
- **#10 4 处 flag-gated 重复** — `SubmissionServiceImpl` × 2 + `AchievementNotificationListener` + `FollowServiceImpl` + `ContestScheduler` 各有 ~20 行 `if (featureFlags.isUseNotificationIntent()) { typed } else { legacy }`。建议抽 `NotificationFacade` 集中管理。
- **Reaper IT 缺失** — `NotificationLedgerReaper` 本身没有 IT (Testcontainers + 时钟控制);加 `NotificationLedgerReaperIT` 验证 stuck CLAIMED → FAILED 转换。
- **Channel-level preference (ADR §1.3 列入未来)** — 真正的 channel × category 二维 preference 仍是 follow-up,需要先做前端 UI 改造。

**Long-term**: ADR-007 "durable notifications" — `@RequiresDurable` intent 走另一条 outbox path (复用 ADR-003 的 `judge_outbox` 设计),`NotificationDispatcher.dispatch` 失败累计 `attempts` 退避重投;本 ADR 的 ledger `UNIQUE(intent_id, channel_id)` 已经为重投的去重提供了物理保证。

## 3. Consequences

### 3.1 Positive

- **泛型 envelope 消除** — 每个 intent 强类型, 字段 IDE 补全, 改 intent 即编译期暴露所有受影响 channel
- **失败隔离** — 一个 channel 死不拖累其它
- **`supports()` 真正生效** — channel 自报"我不发这种 intent"
- **业务模块只 import `NotificationDispatcher` + `*Intent`** — 不再耦合 EmailService / RealtimeService 实现
- **加 channel 只新增 1 个 `NotificationChannel` bean** — 开闭原则 (#11) 落地
- **Codex F4 全部要求满足** (typed intent, per-channel projection, supports 强制调, 失败隔离)
- **M4d-1 后 ledger 真实可恢复** — Reaper 堵上了 ADR §2.5 留的 "future reaper" TODO;crash-recovery 不会永久丢通知

### 3.2 Negative

- 每种业务事件都要定义一个 record, 类数膨胀 (预估 8-12 个 intent)
- 渠道适配代码量增加 (每 channel 对每 intent 一个 instanceof arm)
- 重复信息 (intent record 与现有 `Notification` entity 字段有重叠) — 维护双向映射
- **per-intent projection 在 3 个 channel 各实现一份** (InApp/Email/WS) — 加 1 个 intent 改 3 处;M4d-1 暂未合并 (列入 backlog)

### 3.3 Risks

| 风险 | 缓解 |
|---|---|
| Channel preference 维度缺失 → 用户想关 email 关不掉 | 现状 category 级已能粗粒度关 COMMUNICATION → email 不会发 SUBMISSION_RESULT;UI 文案 + 帮助说明同步 |
| 老代码 (`NotificationDispatchService.dispatch(...)`) 与新 `NotificationDispatcher.dispatch(intent)` 共存期 | M4a 加 `@Deprecated` , M4c 切完,M4d 末段已删注,M4d-1 仍保留二者(等 prod flag-on ≥1 cycle 后删) |
| `NotificationCategory` enum 与 `NotificationPreference` 列名不一致 | 单独 Codec 类映射, 加 round-trip 单测 (M4d-1 删了未用的 `CONTEST` 死枚举,减少误读面) |
| Email 模板分散在 `EmailTemplates.xxx(intent)` 多处 | `EmailTemplates` 单一类聚合 + 模板 i18n 兜底;M4d-1 加 null 合并防 NPE |
| `WebSocketNotificationChannel` 标 `DELIVERED` 即使用户离线 (best-effort) | 离线用户确实未收到推送;ledger 行为符合 ADR §2.5 语义;但 `notification.dispatch.delivered` 指标会"虚高",M4d-1 标 backfill:在指标上加 `delivered_but_offline` 标签或换 `attempted` 命名 |
| `tryClaim` 0 返回的语义模糊 (delivered / claim failed / channel不支持) | 调度器内部 3 路径清晰 (skipped/delivered/failed 各走各的 ledger 转换);M4d-1 加 reaper 处理"claim 后 channel 死"的 edge case |

## 4. Validation

- [x] 每种 intent 至少 1 个 channel `supports()` 返回 true (无孤儿 intent) — `NotificationChannelContractTest#everyIntentHasAtLeastOneChannel`
- [x] Dispatcher 单测: 注入 3 mock channel, 其一抛异常, 验证其余两个仍被调用 — `NotificationDispatcherTest#channelFailureDoesNotBlockOthers`
- [x] `intentId` 幂等性测试: 同一 intent 发 3 次, In-App channel 写一行 (DB 唯一约束) , Email 也只发 1 次 (channel 内部去重 cache) — `NotificationDispatcherTest#idempotencyThreeDispatches`
- [x] 现有 `Notification` 表 schema 不变 (Flyway 校验) — M4a migration 仅新增 `notification_delivery_ledger`,无 ALTER
- [ ] grep 确认业务模块不再直接 import `EmailService` / `RealtimeService` — **deferred to M4b cutover**:当前 flag-gated 双轨仍保留 legacy 分支,channel 实现之外的 4 个业务模块(`SubmissionServiceImpl` / `AchievementNotificationListener` / `AchievementTriggerServiceImpl` / `ContestScheduler`)在 flag-off (legacy) 分支仍 import `EmailService` / `RealtimeService`;只有 flag-on (typed intent) 分支走 `NotificationDispatcher` + `*Intent`。legacy 分支保留至 §2.8 backlog #10 的 M4b cleanup (抽 `NotificationFacade` 后删双轨),届时本条转 `[x]`
- [x] 性能: dispatcher 单次 dispatch 延迟 < 50ms (3 channel 串行, 大头是 Email SMTP) — `NotificationDispatcherTest#dispatcherLatencyUnder50ms`

## 5. References

- [ADR-000 / F4](./ADR-000-hexagonal-grilling-session.md) — Codex finding 原文
- [ADR-001](./ADR-001-verdict-status-codec.md) — SubmissionStatus 入 intent
- [ADR-003](./ADR-003-queue-outbox-fencing.md) — generation/intentId 复用 outbox 幂等机制
- 现有代码: `notification/service/NotificationDispatchService.java` (将被新 dispatcher 取代)
- 现有代码: `achievement/.../AchievementTriggerServiceImpl.java` (event-driven 风格参考)
- 未来 ADR-006 (channel-level preference, UI 改造前置)
- 项目规约: `.claude/rules/backend/07-java-design.md` (#8 单一原则, #11 开闭原则, #15 隔离变化点)
