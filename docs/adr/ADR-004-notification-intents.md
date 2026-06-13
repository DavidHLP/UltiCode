# ADR-004: Notification Intents + Per-Channel Projection + 失败隔离

| 字段 | 值 |
|------|-----|
| **状态 (Status)** | Proposed |
| **日期 (Date)** | 2026-06-13 |
| **作者 (Author)** | DavidHLP |
| **解决的 Finding** | [ADR-000 / F4](./ADR-000-hexagonal-grilling-session.md#2-codex-adversarial-review-摘要) |
| **依赖 ADR** | [ADR-001](./ADR-001-verdict-status-codec.md) (SubmissionCompletedIntent 含 SubmissionStatus), [ADR-003](./ADR-003-queue-outbox-fencing.md) (含 submissionId+generation) |
| **关联代码** | `notification/service/NotificationDispatchService.java`,`websocket/service/RealtimeService.java`,`email/service/EmailService.java`,`achievement/.../AchievementTriggerServiceImpl.java` (event 风格参考) |
| **关联 DB** | 不修改 `notification_preferences` 表 (channel-level preference 列入未来 ADR) |

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

## 3. Consequences

### 3.1 Positive

- **泛型 envelope 消除** — 每个 intent 强类型, 字段 IDE 补全, 改 intent 即编译期暴露所有受影响 channel
- **失败隔离** — 一个 channel 死不拖累其它
- **`supports()` 真正生效** — channel 自报"我不发这种 intent"
- **业务模块只 import `NotificationDispatcher` + `*Intent`** — 不再耦合 EmailService / RealtimeService 实现
- **加 channel 只新增 1 个 `NotificationChannel` bean** — 开闭原则 (#11) 落地
- **Codex F4 全部要求满足** (typed intent, per-channel projection, supports 强制调, 失败隔离)

### 3.2 Negative

- 每种业务事件都要定义一个 record, 类数膨胀 (预估 8-12 个 intent)
- 渠道适配代码量增加 (每 channel 对每 intent 一个 switch arm)
- 重复信息 (intent record 与现有 `Notification` entity 字段有重叠) — 维护双向映射

### 3.3 Risks

| 风险 | 缓解 |
|---|---|
| Channel preference 维度缺失 → 用户想关 email 关不掉 | 现状 category 级已能粗粒度关 COMMUNICATION → email 不会发 SUBMISSION_RESULT;UI 文案 + 帮助说明同步 |
| 老代码 (`NotificationDispatchService.dispatch(...)`) 与新 `NotificationDispatcher.dispatch(intent)` 共存期 | M3a 加 `@Deprecated` , M3c 删除;coverage by code review |
| `NotificationCategory` enum 与 `NotificationPreference` 列名不一致 | 单独 Codec 类映射, 加 round-trip 单测 |
| Email 模板分散在 `EmailTemplates.xxx(intent)` 多处 | `EmailTemplates` 单一类聚合 + 模板 i18n 兜底 |

## 4. Validation

- [ ] 每种 intent 至少 1 个 channel `supports()` 返回 true (无孤儿 intent)
- [ ] Dispatcher 单测: 注入 3 mock channel, 其一抛异常, 验证其余两个仍被调用
- [ ] `intentId` 幂等性测试: 同一 intent 发 3 次, In-App channel 写一行 (DB 唯一约束) , Email 也只发 1 次 (channel 内部去重 cache)
- [ ] 现有 `Notification` 表 schema 不变 (Flyway 校验)
- [ ] grep 确认业务模块不再直接 import `EmailService` / `RealtimeService` (只允许 channel 实现 import)
- [ ] 性能: dispatcher 单次 dispatch 延迟 < 50ms (3 channel 串行, 大头是 Email SMTP)

## 5. References

- [ADR-000 / F4](./ADR-000-hexagonal-grilling-session.md) — Codex finding 原文
- [ADR-001](./ADR-001-verdict-status-codec.md) — SubmissionStatus 入 intent
- [ADR-003](./ADR-003-queue-outbox-fencing.md) — generation/intentId 复用 outbox 幂等机制
- 现有代码: `notification/service/NotificationDispatchService.java` (将被新 dispatcher 取代)
- 现有代码: `achievement/.../AchievementTriggerServiceImpl.java` (event-driven 风格参考)
- 未来 ADR-006 (channel-level preference, UI 改造前置)
- 项目规约: `.claude/rules/backend/07-java-design.md` (#8 单一原则, #11 开闭原则, #15 隔离变化点)
