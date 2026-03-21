# NestJS 到 Spring Boot 迁移 - Phase 6: 剩余模块

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 完成剩余模块的迁移，包括 WebSocket 实时通信、任务队列、成就系统、订阅系统和邮件服务。

**Architecture:** Spring Boot 3.5 + MyBatis-Plus + Spring WebSocket (STOMP) + Redis (任务队列) + JavaMail

**Tech Stack:** Spring Boot 3.5.12, Spring WebSocket, Spring Mail, Redisson 3.27.0

---

## 任务概览

```
Phase 6: 剩余模块 (5 个模块) ─────────────────────────────────►
    │  ├── Task 1: WebSocket 实时通信 (P1)
    │  │   ├── ContestGateway - 竞赛实时事件
    │  │   └── NotificationGateway - 通知推送
    │  │
    │  ├── Task 2: 任务队列系统 (P1)
    │  │   ├── Redis 任务队列配置
    │  │   └── JudgeQueue - 代码评测队列
    │  │
    │  ├── Task 3: Achievement 成就模块 (P3)
    │  │   ├── 成就定义和触发
    │  │   └── 用户成就进度追踪
    │  │
    │  ├── Task 4: Subscription 订阅模块 (P3)
    │  │   ├── 订阅状态管理
    │  │   └── Premium 访问控制
    │  │
    │  └── Task 5: Email 邮件模块 (P3)
    │      ├── SMTP 发送服务
    │      └── 邮件模板管理
    │
总计: 约 5-7 天
```

---

## Task 1: WebSocket 实时通信

**优先级:** P1 (高)
**预估时间:** 1.5 天
**参考:** `backend/src/contest/realtime/contest.gateway.ts`, `backend/src/notification/notification.gateway.ts`

### 1.1 ContestWebSocketHandler

**文件结构:**
```
backend-spring/src/main/java/com/ulticode/modules/websocket/
├── config/
│   └── WebSocketConfig.java           # WebSocket 配置 (STOMP)
├── contest/
│   ├── ContestWebSocketHandler.java   # 竞赛事件处理
│   ├── dto/
│   │   ├── RankingUpdatePayload.java
│   │   ├── FirstSolvePayload.java
│   │   ├── AnnouncementPayload.java
│   │   └── SubmissionResultPayload.java
│   └── ContestRoomManager.java        # 竞赛房间管理
├── notification/
│   ├── NotificationWebSocketHandler.java
│   ├── dto/
│   │   ├── BadgeEarnedPayload.java
│   │   └── NotificationPayload.java
│   └── UserSessionManager.java        # 用户会话管理
├── interceptor/
│   └── JwtChannelInterceptor.java     # JWT 认证拦截器
└── constants/
    └── WebSocketConstants.java        # 事件常量定义
```

### 1.2 API 设计 (STOMP 协议)

**连接端点:**
- `/ws/contest` - 竞赛 WebSocket 端点
- `/ws/notifications` - 通知 WebSocket 端点

**订阅主题:**
- `/topic/contest/{contestId}/ranking` - 排名更新
- `/topic/contest/{contestId}/announcement` - 公告
- `/user/queue/notification` - 个人通知
- `/topic/contest/{contestId}/status` - 竞赛状态

**客户端发送:**
- `/app/contest/join` - 加入竞赛房间
- `/app/contest/leave` - 离开竞赛房间
- `/app/notification/subscribe/{communityId}` - 订阅社区

### 1.3 实现要点

```java
// WebSocketConfig.java 配置示例
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/contest")
            .setAllowedOriginPatterns("*")
            .withSockJS();
        registry.addEndpoint("/ws/notifications")
            .setAllowedOriginPatterns("*")
            .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }
}
```

### 1.4 测试要求

- 测试 WebSocket 连接认证
- 测试竞赛房间加入/离开
- 测试排名更新推送
- 测试通知推送
- 覆盖率 ≥ 80%

---

## Task 2: 任务队列系统

**优先级:** P1 (高)
**预估时间:** 1 天
**参考:** `backend/src/monitoring/monitoring.service.ts` (BullMQ 使用)

### 2.1 文件结构

```
backend-spring/src/main/java/com/ulticode/modules/queue/
├── config/
│   └── QueueConfig.java               # Redis 队列配置
├── service/
│   ├── QueueService.java              # 队列操作服务
│   └── impl/
│       └── QueueServiceImpl.java
├── job/
│   ├── JudgeJob.java                  # 评测任务定义
│   └── JobProcessor.java              # 任务处理器接口
├── dto/
│   ├── JobRequestDTO.java
│   └── JobStatusDTO.java
└── constants/
    └── QueueConstants.java            # 队列常量
```

### 2.2 API 设计

**内部服务接口:**
```java
public interface QueueService {
    // 添加评测任务
    String enqueueJudgeJob(String submissionId, JudgeRequest request);

    // 获取任务状态
    JobStatusDTO getJobStatus(String jobId);

    // 获取队列统计
    QueueStatsDTO getQueueStats(String queueName);

    // 取消任务
    void cancelJob(String jobId);
}
```

### 2.3 队列类型

| 队列名 | 用途 | 优先级 |
|--------|------|--------|
| `judge_queue` | 代码评测任务 | 高 |
| `email_queue` | 邮件发送任务 | 中 |
| `notification_queue` | 批量通知任务 | 低 |

### 2.4 实现要点

使用 Redisson 的 RQueue 或 RPriorityQueue:

```java
@Service
public class QueueServiceImpl implements QueueService {

    @Autowired
    private RedissonClient redissonClient;

    private static final String JUDGE_QUEUE = "judge_queue";

    @Override
    public String enqueueJudgeJob(String submissionId, JudgeRequest request) {
        RQueue<JudgeJob> queue = redissonClient.getQueue(JUDGE_QUEUE);
        JudgeJob job = new JudgeJob(submissionId, request);
        queue.add(job);
        return job.getId();
    }
}
```

### 2.5 测试要求

- 测试任务入队/出队
- 测试任务状态查询
- 测试队列统计
- 覆盖率 ≥ 80%

---

## Task 3: Achievement 成就模块

**优先级:** P3 (低)
**预估时间:** 1 天
**参考:** `backend/src/achievement/achievement.service.ts`

### 3.1 文件结构

```
backend-spring/src/main/java/com/ulticode/modules/achievement/
├── entity/
│   ├── Achievement.java               # 成就定义
│   └── UserAchievement.java           # 用户获得的成就
├── mapper/
│   ├── AchievementMapper.java
│   └── UserAchievementMapper.java
├── service/
│   ├── AchievementService.java
│   ├── AchievementTriggerService.java # 成就触发服务
│   └── impl/
│       ├── AchievementServiceImpl.java
│       └── AchievementTriggerServiceImpl.java
├── dto/
│   ├── AchievementDTO.java
│   ├── AchievementProgressDTO.java
│   └── AchievementQueryDTO.java
├── controller/
│   └── AchievementController.java
├── constants/
│   └── AchievementType.java           # 成就类型枚举
└── event/
    └── AchievementEarnedEvent.java    # 成就获得事件
```

### 3.2 API 设计

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/api/achievements` | 获取所有成就列表 |
| GET | `/api/achievements/{id}` | 获取单个成就详情 |
| GET | `/api/achievements/user/me` | 获取当前用户成就进度 |
| GET | `/api/achievements/user/me/points` | 获取用户成就积分 |
| POST | `/api/achievements` | 创建成就 (管理员) |
| PUT | `/api/achievements/{id}` | 更新成就 (管理员) |
| DELETE | `/api/achievements/{id}` | 删除成就 (管理员) |

### 3.3 成就类型

```java
public enum AchievementType {
    PROBLEMS_SOLVED,          // 解决题目数
    SUBMISSIONS_MADE,         // 提交次数
    CONTEST_PARTICIPATION,    // 参加竞赛次数
    CONTEST_WINS,             // 竞赛获胜次数
    CONTEST_PLACED,           // 竞赛排名
    FORUM_POSTS,              // 论坛帖子数
    SOLUTIONS_WRITTEN,        // 题解数量
    STREAK_DAYS,              // 连续天数
    RATING_MILESTONE,         // 评级里程碑
    COMMUNITY_CONTRIBUTOR     // 社区贡献者
}
```

### 3.4 成就触发机制

```java
@Service
public class AchievementTriggerServiceImpl {

    @Autowired
    private AchievementService achievementService;

    // 当用户解决问题时触发
    @Async
    public void onProblemSolved(String userId, int totalSolved) {
        achievementService.checkAndAwardAchievements(
            userId,
            AchievementType.PROBLEMS_SOLVED,
            totalSolved
        );
    }

    // 当用户参加竞赛时触发
    @Async
    public void onContestJoined(String userId, int totalJoined) {
        achievementService.checkAndAwardAchievements(
            userId,
            AchievementType.CONTEST_PARTICIPATION,
            totalJoined
        );
    }
}
```

### 3.5 测试要求

- 测试成就 CRUD
- 测试成就触发逻辑
- 测试用户成就进度
- 覆盖率 ≥ 80%

---

## Task 4: Subscription 订阅模块

**优先级:** P3 (低)
**预估时间:** 0.5 天
**参考:** `backend/src/subscription/subscription.service.ts`

### 4.1 文件结构

```
backend-spring/src/main/java/com/ulticode/modules/subscription/
├── entity/
│   └── Subscription.java              # 订阅记录
├── mapper/
│   └── SubscriptionMapper.java
├── service/
│   ├── SubscriptionService.java
│   └── impl/
│       └── SubscriptionServiceImpl.java
├── dto/
│   ├── SubscriptionDTO.java
│   └── SubscriptionCheckResultDTO.java
├── controller/
│   ├── SubscriptionController.java
│   └── UserSubscriptionController.java
├── constants/
│   ├── SubscriptionPlan.java          # 订阅计划枚举
│   └── SubscriptionStatus.java        # 订阅状态枚举
└── annotation/
    └── RequirePremium.java            # Premium 访问注解
```

### 4.2 API 设计

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/api/subscriptions/me` | 获取当前用户订阅状态 |
| POST | `/api/subscriptions` | 创建订阅 |
| POST | `/api/subscriptions/{id}/cancel` | 取消订阅 |
| GET | `/api/subscriptions/check-premium` | 检查 Premium 访问 |

### 4.3 订阅计划

```java
public enum SubscriptionPlan {
    FREE,              // 免费版
    PREMIUM_MONTHLY,   // 月度会员
    PREMIUM_YEARLY     // 年度会员
}
```

### 4.4 Premium 访问控制

```java
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("@subscriptionService.hasPremiumAccess()")
public @interface RequirePremium {
}

// 使用示例
@RestController
@RequestMapping("/api/premium")
public class PremiumController {

    @RequirePremium
    @GetMapping("/features")
    public Result<List<Feature>> getPremiumFeatures() {
        // ...
    }
}
```

### 4.5 测试要求

- 测试订阅创建/取消
- 测试 Premium 访问检查
- 测试订阅过期处理
- 覆盖率 ≥ 80%

---

## Task 5: Email 邮件模块

**优先级:** P3 (低)
**预估时间:** 1 天
**参考:** `backend/src/email/email.service.ts`

### 5.1 文件结构

```
backend-spring/src/main/java/com/ulticode/modules/email/
├── entity/
│   ├── EmailTemplate.java             # 邮件模板
│   └── EmailLog.java                  # 邮件发送日志
├── mapper/
│   ├── EmailTemplateMapper.java
│   └── EmailLogMapper.java
├── service/
│   ├── EmailService.java
│   └── impl/
│       └── EmailServiceImpl.java
├── dto/
│   ├── SendEmailDTO.java
│   ├── CreateTemplateDTO.java
│   ├── EmailLogDTO.java
│   └── EmailStatsDTO.java
├── controller/
│   └── EmailController.java
└── constants/
    └── EmailStatus.java               # 邮件状态枚举
```

### 5.2 API 设计

| 方法 | 端点 | 说明 |
|------|------|------|
| POST | `/api/email/send` | 发送邮件 |
| GET | `/api/email/templates` | 获取邮件模板列表 |
| POST | `/api/email/templates` | 创建邮件模板 |
| PUT | `/api/email/templates/{id}` | 更新邮件模板 |
| DELETE | `/api/email/templates/{id}` | 删除邮件模板 |
| GET | `/api/email/logs` | 获取邮件发送日志 |
| GET | `/api/email/stats` | 获取邮件统计 |

### 5.3 配置要求

```yaml
# application.yml
spring:
  mail:
    host: ${SMTP_HOST:localhost}
    port: ${SMTP_PORT:587}
    username: ${SMTP_USER:}
    password: ${SMTP_PASSWORD:}
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true
```

### 5.4 模板变量替换

```java
@Service
public class EmailServiceImpl implements EmailService {

    private String renderTemplate(String template, Map<String, Object> variables) {
        String result = template;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}",
                                   String.valueOf(entry.getValue()));
        }
        return result;
    }
}
```

### 5.5 测试要求

- 测试邮件发送
- 测试模板渲染
- 测试邮件日志
- 覆盖率 ≥ 80%

---

## 错误码分配

| 模块 | 范围 |
|------|------|
| WebSocket | 15xxxx |
| Queue | 16xxxx |
| Achievement | 17xxxx |
| Subscription | 18xxxx |
| Email | 19xxxx |

---

## 验收标准

### Task 1 验收 (WebSocket)
- [ ] WebSocket 连接可通过 JWT 认证
- [ ] 竞赛排名更新实时推送
- [ ] 通知消息实时推送
- [ ] 房间订阅/取消正常

### Task 2 验收 (任务队列)
- [ ] 任务可入队/出队
- [ ] 任务状态可查询
- [ ] 队列统计正确

### Task 3 验收 (成就)
- [ ] 成就 CRUD 正常
- [ ] 成就触发逻辑正确
- [ ] 用户进度显示正确

### Task 4 验收 (订阅)
- [ ] 订阅创建/取消正常
- [ ] Premium 访问控制生效
- [ ] 过期订阅自动处理

### Task 5 验收 (邮件)
- [ ] 邮件发送正常
- [ ] 模板渲染正确
- [ ] 日志记录完整

---

## 依赖关系

```
Task 1 (WebSocket) ─────┐
                        ├──► Task 3 (Achievement) ──► 需要通知推送
Task 2 (Queue) ─────────┘

Task 4 (Subscription) ──► 独立

Task 5 (Email) ─────────► 独立，可使用队列
```

**推荐执行顺序:**
1. Task 1 (WebSocket) - 被多个模块依赖
2. Task 2 (Queue) - 基础设施
3. Task 3 (Achievement) - 依赖 WebSocket
4. Task 4 (Subscription) - 独立
5. Task 5 (Email) - 独立

---

## 开始执行

```bash
# 按顺序执行任务
1. Task 1: WebSocket 实时通信
2. Task 2: 任务队列系统
3. Task 3: Achievement 成就模块
4. Task 4: Subscription 订阅模块
5. Task 5: Email 邮件模块
```
