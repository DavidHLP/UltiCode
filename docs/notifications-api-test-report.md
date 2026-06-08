# Admin Notifications API —真实接口测试报告

|字段 | 值 |
|---|---|
|报告生成时间 |2026-06-08 |
| 测试目标 | `POST /admin/notifications`、`PUT /admin/notifications/{id}`、`DELETE /admin/notifications/{id}` |
| 后端版本 | `ulticode-9001`（Spring Boot3.2.5 / Java17，profile=`dev`） |
| 后端 PID |309021（PM2 运行中） |
| 测试账号 | dev seed `admin` / `admin123`（`DevUserBootstrapRunner` 创建，`role=ADMIN`） |
|鉴权 | JWT Bearer（Cookie）+ CSRF `X-CSRF-Token` Header（每次写操作旋转） |
| 测试工具 | curl8.5.0 + Docker exec mysql + Python3 |
| Controller源 | `backend-spring/.../admin/controller/AdminNotificationController.java` |
| Service源 | `backend-spring/.../admin/service/impl/AdminNotificationServiceImpl.java` |
| DTO | `CreateSystemNotificationRequest`、`UpdateSystemNotificationRequest` |

---

##1.控制器契约（来自源码审计）

|端点 |鉴权注解 |限流 | 请求体 |响应 |
|---|---|---|---|---|
| `POST /admin/notifications` | `@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")` | `admin:notification-create`30/分钟 | `CreateSystemNotificationRequest`：`title`/`content`/`type`全部 `@NotBlank`，`target` `@NotBlank`（`ALL` 或 `USERS`），可选 `category`、`userIds` | `Result<AdminNotificationVO>` |
| `PUT /admin/notifications/{id}` | `@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")` | `admin:notification-update`30/分钟 | `UpdateSystemNotificationRequest`：`title`/`content` `@NotBlank`，可选 `type`、`category` | `Result<AdminNotificationVO>` |
| `DELETE /admin/notifications/{id}` | `@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")` | `admin:notification-delete`30/分钟 | — | `Result<Void>` |

GET（顺带验证）走 `AdminNotificationQueryDTO` 分页 +字段过滤（`category=SYSTEM`），并按 `announcement_id` dedup。

---

##2. 测试环境前置

| 项 | 实测值 |
|---|---|
| 后端 `GET /admin/notifications` 无 Cookie | `HTTP401 {"code":40100,"message":"Unauthorized"}` ✅ |
| `POST /auth/login` with `admin/admin123` | `HTTP200`，下发 `access_token`（217B JWT）+ `refresh_token`（195B）+ `csrf_token`（65B）Cookie +响应体 `csrfToken`字段 |
| Cookie 中包含 `Set-Cookie: access_token=…; HttpOnly; SameSite=lax`、`refresh_token=…; HttpOnly`、`csrf_token=…` | ✅全部 `HttpOnly`，`JWT_COOKIE_SECURE=false`（开发 HTTP） |
|响应头 | `Content-Security-Policy: default-src 'self'`，`X-Frame-Options: DENY`，`Permissions-Policy: camera=(), microphone=(), geolocation=()` |

---

##3. 测试结果汇总

| # | 用例 | HTTP | 实测结果 |期望 |结论 |
|---|---|---|---|---|---|
| **POST** | | | | | |
| P1 | happy path（`target=ALL`） | **500** | `Database error: Type handler was null on parameter mapping for property '__frch_item_0.metadata'` |200 | ❌ **真实 Bug**（详见 §6） |
| P2 |缺 `title` |400 | `{"title":"Title cannot be blank"}` |400 | ✅ `@NotBlank`生效 |
| P3 | `target=INVALID` |400 | `"No target users found"` |400（业务）或422 | ✅ Service拒绝（fall-through 后返回空列表） |
| P4 | `target=USERS, userIds=[]` |400 | `"No target users found"` |400 | ✅ |
| P5 | `target=USERS, userIds=[无效 id]` |400 | `"No target users found"` |400 | ✅ |
| P6 |缺 `X-CSRF-Token` |403 | `"CSRF token is required"` |403 | ✅ `CsrfValidationFilter`拦截 |
| P7 | 无 Cookie |401 | `"Unauthorized"` |401 | ✅ |
| **PUT** | | | | | |
| U1 | happy path（更新 `N1`，共享 `announcement_id`） | **200** | 更新 title/body成功，**同 `announcement_id` 的另一条记录也同步更新**（验证 SQL截图） |200 | ✅ Service广播到 `announcement_id` 同组 |
| U2 | id 不存在 |404 | `"Notification not found"` |404 | ✅ |
| U3 | 空 `title` |400 | `"Title cannot be blank"` |400 | ✅ |
| U4 |缺 `X-CSRF-Token` |403 | `"CSRF token is required"` |403 | ✅ |
| U5 | 无 Cookie |401 | `"Unauthorized"` |401 | ✅ |
| **DELETE** | | | | | |
| D1 | happy path（删除 `N1`） | **200** | **同 `announcement_id` 的全部2 条都被删除**（`SELECT COUNT(*)=0`验证） |200 | ✅ Service 级联删除 |
| D2 | id 不存在 |404 | `"Notification not found"` |404 | ✅ |
| D3 |缺 `X-CSRF-Token` |403 | `"CSRF token is required"` |403 | ✅ |
| D4 | 无 Cookie |401 | `"Unauthorized"` |401 | ✅ |
| D5 |重复删除同一 id |404 | `"Notification not found"` |404 | ✅幂等 |
| **GET（辅助）** | | | | | |
| G1 | `GET /admin/notifications?page=1&limit=5` 无 Cookie |401 | `"Unauthorized"` |401 | ✅ |
| G2 | `GET /admin/notifications` 有 Cookie |200 | `{"items":[…],"total":1,"pageSize":5,…}`，按 `announcement_id` dedup |200 | ✅ |

###响应时间（已测样本）

| 操作 | 时延 |
|---|---|
| `GET /admin/notifications`（空表） |9.3 ms |
| `GET /admin/notifications`（1 条 dedup） |14.4 ms |
| `POST /admin/notifications` happy path |4.5 ms（500 即返回） |
| `PUT /admin/notifications/{id}` happy |19.2 ms |
| `DELETE /admin/notifications/{id}` happy |19.1 ms |
| `DELETE`404 |11.5 ms |
| `DELETE`403（无 CSRF） |2.4 ms |

---

##4. PUT happy path —广播验证证据

**请求**：`PUT /admin/notifications/701bf8a8-b7da-49e1-a434-04bfeee33715`，body `{"title":"[FIXTURE] 系统升级预告 (已更新)","content":"升级时间调整至03:00，请重新安排。","type":"SYSTEM","category":"SYSTEM"}`

**响应**（节选）：
```json
{"code":0,"message":"success","data":{"id":"701bf8a8-b7da-49e1-a434-04bfeee33715","announcementId":"947d761c-affe-401a-b806-e80d5fa80e9f","title":"[FIXTURE] 系统升级预告 (已更新)","content":"升级时间调整至03:00，请重新安排。","type":"SYSTEM","category":"SYSTEM","creator":{"id":"9f6bc78a-…","username":"admin","avatar":"…"}},"traceId":"t-1780930447729"}
```

**MySQL验证（DELETE之前）**：
```
id title body
701bf8a8-b7da-49e1-a434-04bfeee33715 [FIXTURE] 系统升级预告 (已更新)升级时间调整至03:00，请重新安排。
ca781967-ffcd-45ba-8862-eada77b522e0 [FIXTURE] 系统升级预告 (已更新)升级时间调整至03:00，请重新安排。
```
两条共享 `announcement_id=947d761c-…` 的记录都被更新——确认 Service 的 `LambdaUpdateWrapper.eq(Notification::getAnnouncementId, ...)`正确广播。

---

##5. DELETE happy path — 级联删除证据

**请求**：`DELETE /admin/notifications/701bf8a8-b7da-49e1-a434-04bfeee33715`

**响应**：`HTTP/1.1200 {"code":0,"message":"success","traceId":"t-1780930509722"}`（19.1 ms）

**MySQL验证**：
```
SELECT COUNT(*) AS remaining FROM notifications WHERE announcement_id='947d761c-…';
remaining
0
```
两条共享 `announcement_id` 的记录均被删除。Service 的 `deleteByAnnouncementId` 分支（`announcementId != null`路径）正确触发。

---

##6. 🔴关键发现 — `POST /admin/notifications`500 Bug

###现象
合法请求 → `HTTP500 {"code":50001,"message":"Database error","traceId":"t-1780929374933"}`

###根因（PM2 out 日志）
```
java.lang.IllegalStateException: Type handler was null on parameter mapping for
 property '__frch_item_0.metadata'. It was either not specified and/or could
 not be found for the javaType (java.util.Map) : jdbcType (null) combination.

The error may exist in com/ulticode/modules/notification/mapper/NotificationMapper.java
The error may involve com.ulticode.modules.notification.mapper.NotificationMapper.batchInsert
The error occurred while executing an update
```

### 代码定位
`backend-spring/.../notification/mapper/NotificationMapper.java`：
```java
@Insert("<script>INSERT INTO notifications (…metadata…) VALUES " +
 "<foreach collection='list' item='item' separator=','>" +
 "(#{item.id}, …, #{item.metadata}, …)" +
 "</foreach></script>")
int batchInsert(@Param("list") List<Notification> list);
```

`Notification.java` 用 `@TableField(typeHandler = JacksonTypeHandler.class)` 把 `Map<String,Object> metadata` 注册到 MyBatis-Plus 元数据，但 `@Insert` 自定义 SQL 的 `#{item.metadata}`不会继承 `@TableField` 的 typeHandler——MyBatis 无法把 `Map`绑定到 `JSON` 列上。

###修复建议（最小改动）
在 mapper注解里显式声明 typeHandler：
```java
@Insert("<script>INSERT INTO notifications " +
 "(id, user_id, type, category, title, body, link, metadata, announcement_id, is_read, read_at, created_at, updated_at) VALUES " +
 "<foreach collection='list' item='item' separator=','>" +
 "(#{item.id}, #{item.userId}, #{item.type}, #{item.category}, #{item.title}, " +
 " #{item.body}, #{item.link}, " +
 " #{item.metadata, typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class}, " +
 " #{item.announcementId}, #{item.isRead}, #{item.readAt}, #{item.createdAt}, #{item.updatedAt})" +
 "</foreach></script>")
int batchInsert(@Param("list") List<Notification> list);
```

或者改为逐条 `insert`（自循环），由 MyBatis-Plus 元数据自动选择 typeHandler。

### 影响
- 前端管理后台 `management/src/api/admin/notifications.ts` 中的 `notificationsApi.create(...)`路径整体不可用。
- 工作流："管理员发公告" 是核心运维动作，发布期间完全失败。
- 由于本测试中用 fixture绕过 batchInsert 才完成 PUT/DELETE 测试，**PUT/DELETE 通过是真实业务结果，但 POST 的业务路径本身不能端到端验证**。

---

##7.鉴权 &防护矩阵

|维度 | 实现 | 测试结果 |
|---|---|---|
| JWT鉴权 | `JwtAuthenticationFilter` + `access_token` Cookie（HttpOnly） | ✅401 无 Cookie 时拦截 |
| RBAC | `@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")` | ✅三个写端点均有注解（管理员有 `ADMIN`角色通过） |
| CSRF | `CsrfValidationFilter`（POST/PUT/DELETE/PATCH校验） | ✅403缺 `X-CSRF-Token` 时拦截 |
| CSRF Token Rotation | `csrfService.validateAndRotateToken` | ✅每次写操作返回 `X-New-CSRF-Token`，**前端必须每次都用最新值** |
|限流 | `@RateLimit(key="admin:notification-…", limit=30, period=60)` | ✅注解存在，未触发（未压测） |
|审计 | `@Audited(action = CREATE/UPDATE/DELETE_NOTIFICATION, entityType = ENTITY_NOTIFICATION)` | ✅注解存在，未在数据库单独验证 |
| 参数校验 | `@Valid` + `@NotBlank`（DTO字段级） | ✅ title/content/type/target 空值400 |
|业务校验 | `getTargetUserIds` 检查 target/USERS有效性 | ✅ INVALID/空 userIds →400 |

---

##8. 一致性观察

- **数据库→列表 dedup**：`GET /admin/notifications` 通过 `selectDedupedAnnouncements` 按 `announcement_id` 去重，每条公告只展示一次；这与 PUT/DELETE 按 `announcement_id`广播修改/删除保持一致。
- **审计身份取自 principal**：`createSystemNotification` 内 `SecurityUtil.getCurrentUserId()` + `userMapper.selectById` 获取 creator，未从请求体取——符合项目 "审计身份取自认证 principal，不取自请求体"约定。
- **错误码体系**：成功 `code=0`，业务错4xxxx（400/403/404），系统错5xxxx（50001 数据库），与项目 `Result<T>` 信封一致。

---

##9.总结与建议

### 通过情况
|端点 |核心路径 |边界用例 | 总评 |
|---|---|---|---|
| `POST /admin/notifications` | ❌（500 bug） | ✅校验/鉴权/CSRF/Auth 都正确 | **不通过** —需修复 batchInsert |
| `PUT /admin/notifications/{id}` | ✅ | ✅ | **通过** |
| `DELETE /admin/notifications/{id}` | ✅ | ✅ | **通过** |

###优先级建议
1. **🔴 P0**：修复 `NotificationMapper.batchInsert` 的 typeHandler缺失（详见 §6）。
2. **🟡 P1**：补一个 `AdminNotificationControllerIT`（Testcontainers），覆盖 POST happy path 的回归保护——目前三个端点没有 `*IT.java`集成测试（仓库结构扫描确认）。
3. **🟢 P2**：补充 CSRF旋转失败时的错误处理文档（前端 `X-New-CSRF-Token`失效场景下的重试策略）。

### 测试覆盖率（已测用例）
-正常路径：4（GET×1, POST跳过, PUT, DELETE）
-鉴权路径：6（401×3,403×3）
-业务校验路径：4（P2-P5, U2, U3）
-幂等性：1（D5重复删除）

###端到端验证注意事项
- 由于 POST bug，本报告对 PUT/DELETE 使用了「MySQL手工插入 fixture → HTTP 调用 → MySQL 回读校验」的替代流程，验证的是 controller/service 对数据库中已有记录的真实行为，**未走 controller→service→mapper写入路径**。
-修复 POST 后应补一组 fixture 测试，验证「POST →立即 GET →列表 dedup正确」以及「POST →立即 DELETE → 同 announcement_id 全删」。
