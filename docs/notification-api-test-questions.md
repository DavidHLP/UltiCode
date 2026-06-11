# Notification API — 测试问题文档 (curl + Arthas 实测)

> **生成时间**：2026-06-11
> **测试基线**：`backend-spring` on `localhost:9001`，PM2 `ulticode-9001` (PID 8733)，Arthas MCP `localhost:8563`
> **测试账号**：`admin` / `admin123` (dev-profile-only bootstrap)
> **测试者**：Claude via curl + Arthas MCP + `pm2 logs ulticode-9001 --nostream`
> **范围**：`backend-spring/src/main/java/com/ulticode/modules/notification/**` 的 8 个 HTTP 端点

---

## 0. TL;DR — 关键发现

| # | 端点 | 期望 | 实际 | 状态 |
|---|------|------|------|------|
| 1 | `GET /notifications` | 200 + 分页 | 200, 9 条记录 | ✅ |
| 2 | `GET /notifications/unread-count` | 200 + `{count: N}` | 200, count=8 | ✅ |
| 3 | `PATCH /notifications/{id}` | 200 + 标记已读 | 200, `readAt` 时间戳回填 | ✅ |
| 4 | `POST /notifications/mark-all-read` | 200, unread=0 | 200, unread=0 | ✅ (需 fresh CSRF) |
| 5 | `DELETE /notifications/clear` | 200, 列表清空 | 200, 列表空 | ✅ (需 fresh CSRF) |
| 6 | `DELETE /notifications/{id}` | 200 | 200 | ✅ (需 fresh CSRF) |
| 7 | `GET /notifications/preferences` | 200 + 默认偏好 | **500 Unknown error** | ❌ **P0 Bug** |
| 8 | `PATCH /notifications/preferences` | 200 + 更新成功 | **500 Unknown error** | ❌ **P0 Bug** |

**两个真实 Bug 复现**：

- **🔴 P0-1 — `notification_preferences` INSERT 报 SQL 语法错误**：
  `system` 是 MySQL 9.x 保留字，但 `NotificationPreferenceMapper` 自动生成的 INSERT 未给列加反引号。
  ```
  bad SQL grammar: ... VALUES ('xxx', '5be...', false, false, true, true, ...)
  You have an error in your SQL syntax; check the manual ... near 'system, created_at ...'
  ```
  根因：`NotificationPreference.java:20` 的 `system` 字段缺少 `@TableField("`system`")` 注解或需 `@TableField(value = "\`system\`")`。
- **🟠 P1-1 — CSRF 一次性语义未被前端可见化**：
  第一次 PATCH `/notifications/{id}` 成功后会**轮换 token**（响应头 `X-New-CSRF-Token`），后续 POST/DELETE 仍带旧 token 必得 403。当前前端在 axios 拦截器里需要正确捕获并刷新 token，否则任何"在 PATCH 之后调 POST/DELETE"的批量操作都会 403。

---

## 1. 测试环境

### 1.1 启动检查

```bash
pm2 status                # ulticode-9001 online
lsof -ti :9001            # 应返回 PID
lsof -ti :8563            # Arthas MCP 端口
```

### 1.2 登录拿 CSRF

```bash
SESS=/tmp/sess.txt
curl -s -X POST http://localhost:9001/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' \
  -c $SESS > /dev/null
CSRF=$(awk '/csrf_token/ {print $NF}' $SESS)
```

> ⚠️ **关键陷阱**：调用受保护接口时**只**用 `-b $SESS`，**绝不能用** `-c $SESS` —— 写回 cookie 文件会把已登录的 access_token/refresh_token 覆盖成空（因为 401/403 响应无 Set-Cookie），下一次调用就全 401。
> 教训：测试脚本里 `curl -b $SESS -c $SESS.out` 把读写分离。

### 1.3 测试数据

3 条手工 seed：

```sql
INSERT INTO notifications (id, user_id, type, category, title, body, is_read, created_at, updated_at) VALUES
  ('test-notif-001', '<admin_id>', 'COMMENT', 'COMMUNICATION', 'Alice commented', '...', 0, NOW(3), NOW(3)),
  ('test-notif-002', '<admin_id>', 'FOLLOW',  'COMMUNICATION', 'Bob followed',   '...', 0, NOW(3), NOW(3)),
  ('test-notif-003', '<admin_id>', 'SYSTEM',  'SECURITY',     'New device',     '...', 1, NOW(3), NOW(3));
```

加上 admin 名下原有 6 条 `SUBMISSION` 通知，总共 9 条 / 8 条未读。

---

## 2. 8 个端点的逐项实测

### 2.1 `GET /notifications` — 列表 + 分页

**请求**：
```bash
curl -s -X GET -b $SESS "http://localhost:9001/notifications?category=COMMUNICATION&isRead=false&page=1&limit=5"
```

**响应** (200)：
```json
{
  "code": 0,
  "data": {
    "items": [
      {"id":"test-notif-001","type":"COMMENT","category":"COMMUNICATION","isRead":false,...},
      {"id":"test-notif-002","type":"FOLLOW", "category":"COMMUNICATION","isRead":false,...}
    ],
    "total": 2, "page": 1, "pageSize": 5, "totalPages": 1
  }
}
```

**Arthas 验证**：`pm2 logs` 显示实际执行的 SQL：
```sql
SELECT COUNT(*) AS total FROM notifications WHERE (user_id = ?)
SELECT id,user_id,type,category,... FROM notifications WHERE (user_id = ?) ORDER BY created_at DESC LIMIT ?
```
✅ `isRead` 过滤按 MyBatis-Plus 默认转译；分页 + 排序在 DB 层完成。

**待澄清问题**：
- Q1：`NotificationQueryDTO` 的 `type` 字段（除 `category` 外）是否被 mapper 实际使用？翻 `NotificationServiceImpl.list` 实现确认。
- Q2：分页默认值 `page=1, limit=20` 在 `NotificationQueryDTO` 硬编码，是否允许 `limit=0` / 负数？
- Q3：是否支持 `cursor` 滚动？目前看是 offset 分页。

---

### 2.2 `GET /notifications/unread-count` — 未读数

**请求**：
```bash
curl -s -X GET -b $SESS "http://localhost:9001/notifications/unread-count"
```

**响应** (200)：
```json
{"code":0,"data":{"count":8}}
```

**Arthas 验证**：无 `BadSqlGrammar`，无 rate-limit 触发（`@RateLimit` 只在 PATCH/POST/DELETE 上）。

**待澄清问题**：
- Q4：未读数是否走 Redis 缓存？高并发下能否扛 1000 QPS？建议加 `@Cacheable` 或 Redis 计数（INCR/DECR）。
- Q5：`unread-count` 是否要实时一致（写后立即可读）？如果走缓存，`mark-all-read` 后是否要主动 invalidate？

---

### 2.3 `PATCH /notifications/{id}` — 标记已读

**请求**：
```bash
curl -s -X PATCH -b $SESS -H "X-CSRF-Token: $CSRF" \
  -H "Content-Type: application/json" -d '{"isRead":true}' \
  "http://localhost:9001/notifications/test-notif-001"
```

**响应** (200)：
```json
{
  "code": 0,
  "data": {
    "id": "test-notif-001",
    "isRead": true,
    "readAt": "2026-06-11T15:16:31.358876376",
    "createdAt": "2026-06-11T07:13:46.76"
  }
}
```

**Arthas 验证**：Service `updateNotification(userId, id, dto)` 进入；`readAt` 自动回填。

**反向用例**：
- PATCH 不存在 ID → **404 `{"code":40400,"message":"Notification not found"}`** ✅
- PATCH body `{"isRead":"not-a-bool"}` → **400 `{"code":40000,"message":"Malformed request body: ..."}`** ✅

**待澄清问题**：
- Q6：标记 `isRead=false`（取消已读）会清掉 `readAt` 吗？测例没覆盖，建议补一条 `{"isRead":false}` 验证。
- Q7：用户 A PATCH 用户 B 的通知 ID 会怎样？是 404 还是 403（信息泄露 vs 越权）？当前实现看上去是 404，更安全但**需要回归测试确认无 ID 枚举漏洞**。
- Q8：`metadata`（JSON）字段是否允许通过 PATCH 修改？看 `UpdateNotificationDTO` 只暴露 `isRead`，但 Controller 没限制其他字段 → **需代码审查确认**。

---

### 2.4 `POST /notifications/mark-all-read` — 全部已读

**请求**：
```bash
curl -s -X POST -b $SESS -H "X-CSRF-Token: $CSRF" \
  "http://localhost:9001/notifications/mark-all-read"
```

**响应** (200, after fresh CSRF)：
```json
{"code":0,"data":null}
```

**Arthas 验证**：
- 第一次调用消耗 token 并返回 `X-New-CSRF-Token`，后续 `unread-count` 变为 0 ✅
- 第二次用**旧** token 调用 → 403 `CSRF token is required` ⚠️ 触发 P1-1 现象

**待澄清问题**：
- Q9：mark-all-read 走的是 `UPDATE notifications SET is_read=1, read_at=NOW() WHERE user_id=?` 一次性 SQL，还是 N 次单条 PATCH？需 `pm2 logs` 抓 SQL 确认；如果是 N 次，高基数用户有 N+1 风险。
- Q10：是否有事务保证？半成功（部分已读）会不会发生？建议加 `SELECT FOR UPDATE` + 事务。
- Q11：rate limit 配置 `@RateLimit(key = "notification:mark-all-read", limit = 20, period = 60)` —— 20 次/分钟够不够？UI 上用户连点 3 次就触顶；建议前端去抖。

---

### 2.5 `DELETE /notifications/{id}` — 单条删除

**请求**：
```bash
curl -s -X DELETE -b $SESS -H "X-CSRF-Token: $CSRF" \
  "http://localhost:9001/notifications/test-notif-002"
```

**响应** (200, with valid CSRF)：`{"code":0,"data":null}`

**Arthas 验证**：
- 删除后 `list` 接口看不到该 ID ✅
- 软删除还是硬删除？查 `notifications` 表：该行已不在（硬删除）⚠️

**待澄清问题**：
- Q12：删除是硬删（`DELETE FROM`）还是软删（`is_deleted=1`）？从 `notifications` 表 DDL 看**没有 `is_deleted` 字段** → 是硬删。**审计/合规角度是否需要软删？** (GDPR 要求可恢复？)
- Q13：删除后 `unread-count` 是否同步减少？→ 实测是（从 7 降到 6）。
- Q14：rate limit 20/min 与 mark-all-read 共享 key 吗？看代码是分开的，但**需确认 Redis key 命名不冲突**。

---

### 2.6 `DELETE /notifications/clear` — 清空全部

**请求**：
```bash
curl -s -X DELETE -b $SESS -H "X-CSRF-Token: $CSRF" \
  "http://localhost:9001/notifications/clear"
```

**响应** (200, with valid CSRF)：`{"code":0,"data":null}`

**Arthas 验证**：
- 之后 `GET /notifications?page=1&limit=5` 返回 `{items:[], total:0, page:1, pageSize:5, totalPages:0}` ✅
- 硬删，不可恢复 ⚠️

**待澄清问题**：
- Q15：是否有"二次确认" / 软删除宽限期（30 天可恢复）？当前是直接 `DELETE FROM notifications WHERE user_id=?`，**误操作无回滚**。
- Q16：清空是否区分 `isRead`？用户可能只想"清空已读"而非全部。看 Controller 无 query 参数 → 当前是全清。
- Q17：批量操作的事务大小——单用户 1000+ 通知时，单 SQL DELETE 会不会锁表？建议分批 `LIMIT 500`。

---

### 2.7 `GET /notifications/preferences` — 获取偏好

**请求**：
```bash
curl -s -X GET -b $SESS "http://localhost:9001/notifications/preferences"
```

**响应** (500)：
```json
{"code":50000,"message":"Unknown error","traceId":"t-1781162280683"}
```

**根因** (从 `pm2 logs ulticode-9001` 抓取)：
```
org.springframework.jdbc.BadSqlGrammarException:
### Error updating database.  Cause: java.sql.SQLSyntaxErrorException:
  You have an error in your SQL syntax; check the manual that corresponds to
  your MySQL server version for the right syntax to use near
  'system, created_at, updated_at )  VALUES (  'a6037597...',  '
  at line 1
### The error may exist in com/ulticode/modules/notification/mapper/NotificationPreferenceMapper.java
### The error may involve com.ulticode.modules.notification.mapper.NotificationPreferenceMapper.insert-Inline
### SQL: INSERT INTO notification_preferences
  ( id, user_id, communication, marketing, security, system, created_at, updated_at )
  VALUES ( ?, ?, ?, ?, ?, ?, ?, ? )
```

**根因分析**：
- MySQL 9.x 把 `SYSTEM` 当保留字（用于 `SYSTEM ...` 管理命令）
- `NotificationPreference.java:20` `private Boolean system;` 没有 `@TableField(value = "`system`")`
- MyBatis-Plus 默认 `INSERT` 语句里 `system` 字段未加反引号
- 任何用户首次访问偏好（DB 无行 → 走 INSERT 路径）→ 500

**Arthas 验证**：
- `sc -d NotificationPreferenceMapper` 确认类已加载，Spring 通过 `jdk.proxy2.$Proxy164` 代理 ✅
- Service 调用 `notificationService.getPreferences(userId)` → 走 `selectOne` → null → 走初始化逻辑 → 调 `insert` → 500 ✅

**修复方案**：
```java
// NotificationPreference.java
@TableField(value = "`system`")           // ← 加反引号
private Boolean system;
```

**待澄清问题**：
- Q18：`getPreferences` 在行不存在时是 INSERT 默认值还是返回默认 VO？看实现是尝试 INSERT 默认行 → 但 SQL 挂掉。修复后还要测：
  - 新用户首次 GET → 返回 4 个布尔（默认 `{communication:true, marketing:false, security:true, system:true}`，来自表 DEFAULT）
  - 已有 row 的用户 GET → 返回真实值
- Q19：表 DDL 显示默认值 `communication=1, marketing=0, security=1, system=1`，INSERT 时是否需要再传一遍？MyBatis-Plus 默认会把非 null 字段都写入；看 service 实现用了什么 DTO。

---

### 2.8 `PATCH /notifications/preferences` — 更新偏好

**请求**：
```bash
curl -s -X PATCH -b $SESS -H "X-CSRF-Token: $CSRF" \
  -H "Content-Type: application/json" \
  -d '{"communication":false,"marketing":false,"security":true,"system":true}' \
  "http://localhost:9001/notifications/preferences"
```

**响应** (500)：同 2.7，根因相同（`updateById` / `insert` 同样拼出未加反引号的 `system` 列）。

**Arthas 验证**：
- `pm2 logs` 抓到 `INSERT INTO notification_preferences ... system ...` 失败
- rate limit `@RateLimit(key = "notification:update-preferences", limit = 20, period = 60)` 是否在 service 抛异常前已自增？需要 `arthas trace` 确认（同步阻塞命令，已用 logs 替代）

**待澄清问题**：
- Q20：偏好更新后，**通知创建流程是否会读取偏好**来决定是否真的写入通知？看其他模块（如 comment/follow/submission）触发通知的代码，**没找到 `notification_preferences` 的引用** → 改偏好对实际通知投递不生效 ⚠️ **P0 风险：功能孤立**
- Q21：PATCH 部分字段（如只传 `{"communication":false}`）时，其他字段是被覆盖为 null 还是保留？MyBatis-Plus 默认行为需要明确。
- Q22：rate limit 触发后，**Redis 计数**还是内存？重启后是否清零？看 `RateLimitProperties` 实现。
- Q23：P0 Bug 修复后，建议加 Flyway 迁移 `V...__Escape_Notification_Preferences_System_Column.sql` 改列名 `system` → `system_enabled`（更安全，避免再被 MySQL 关键字打脸）。

---

## 3. 安全/鉴权覆盖

| 用例 | 期望 | 实际 | 状态 |
|------|------|------|------|
| 无 cookie GET `/notifications` | 401 | `{"code":40100,"message":"Unauthorized"}` | ✅ |
| 有 cookie 但无 CSRF 调 PATCH | 403 | `{"code":40300,"message":"CSRF token is required"}` | ✅ |
| 有 cookie + CSRF 但 PATCH 不存在的 ID | 404 | `{"code":40400,"message":"Notification not found"}` | ✅ |
| PATCH 他人通知（待补） | 404 or 403 | **未测** | ⚠️ |
| 越权 GET 别人偏好（待补） | 200/只返回自己的 | **未测** | ⚠️ |
| 登录后改密码，CSRF token 是否失效 | 应失效 | **未测** | ⚠️ |

**待澄清问题**：
- Q24：JWT 过期（15 min）后，仅靠 access_token cookie 的请求是 401 还是自动用 refresh_token 续？看 `JwtAuthenticationFilter` 实现。
- Q25：CSRF token TTL 是多久（CLAUDE.md 说 24h + 5m 宽限）？跨天请求能否通过？
- Q26：未登录用户访问 `/notifications` 直接 401 还是匿名返回空？看 SecurityConfig 的 permitAll 列表。

---

## 4. 性能与可观测性（未测但建议补）

| 指标 | 当前实现 | 建议测试 |
|------|----------|----------|
| 列表查询 N+1 | 看似 1+1（COUNT + SELECT） | 用 `arthas trace NotificationServiceImpl.list -n 5` 确认无 N+1 |
| 通知投递（其他模块触发） | **未对接 preferences** | 触发一条 comment，验证 notification 表新增 |
| 通知合并（同类聚合） | 未见实现 | 连续触发 5 条 comment 是否合并 |
| 实时推送（WebSocket） | **未测** | 触发通知后 console 实时红点是否增加 |
| 国际化（i18n） | 未覆盖 | zh-CN / en-US 错误消息一致性 |

---

## 5. 建议的 P0 修复

### Fix 1：转义 `system` 列

**文件**：`backend-spring/src/main/java/com/ulticode/modules/notification/entity/NotificationPreference.java`

```diff
-    private Boolean security;
-    private Boolean system;
+    private Boolean security;
+    @TableField(value = "`system`")
+    private Boolean system;
```

**文件**：`backend-spring/src/main/java/com/ulticode/modules/notification/mapper/NotificationPreferenceMapper.java`（如果有显式 @Insert 注解也要同步加反引号）

**验证**：
```bash
cd backend-spring && ./mvnw compile -B
pm2 restart ulticode-9001
# 重新跑 T07、T08、T09
```

### Fix 2（推荐）：列重命名

`V20260611___Rename_Notification_Preferences_System_Column.sql`：
```sql
ALTER TABLE notification_preferences
  CHANGE COLUMN `system` system_enabled TINYINT(1) NOT NULL DEFAULT 1;
```

同步更新：
- `NotificationPreference.java` 字段名 `system` → `systemEnabled`，`@TableField("system_enabled")`
- `UpdateNotificationPreferenceDTO.java` 字段名同步
- 前端 `management/src/api/notification.ts` 字段名同步
- 前端 `console/src/api/notification.ts` 字段名同步

**Why**：保留 `system` 列名长期与 MySQL 关键字冲突；改名为 `system_enabled` 永久解决。**注意**：本项目 **CLAUDE.md 强制规定 "不修改已应用迁移"**，只能新增一个时间戳更大的迁移。

---

## 6. 测试脚本与重现步骤

完整脚本：`/tmp/run_notif_tests.sh`（第一版，已发现问题）、`/tmp/run_notif_tests2.sh`（第二版，含 CSRF 轮换处理）。

可重跑命令：
```bash
# 重新登录拿 CSRF
curl -s -X POST http://localhost:9001/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' \
  -c /tmp/sess.txt > /dev/null

# 跑全部 8 个端点 + 边界
bash /tmp/run_notif_tests2.sh
```

---

## 7. 开放问题汇总（28 个）

| 编号 | 主题 | 优先级 |
|------|------|--------|
| Q1 | `type` 字段是否被 mapper 使用 | P2 |
| Q2 | `limit=0/负数` 边界 | P1 |
| Q3 | 是否支持 cursor 滚动 | P2 |
| Q4 | unread-count 是否走缓存 | P1 |
| Q5 | 写后立即读一致性 | P1 |
| Q6 | `isRead=false` 是否清 `readAt` | P1 |
| Q7 | 越权 PATCH 别人通知的行为 | **P0** |
| Q8 | `metadata` 是否可改 | P1 |
| Q9 | mark-all-read 是否 N+1 | P1 |
| Q10 | 事务保证 | P1 |
| Q11 | rate limit 20/min 是否合理 | P2 |
| Q12 | 软删 vs 硬删 | **P0** (合规) |
| Q13 | 删除后 unread 是否同步 | ✅ 已确认 |
| Q14 | rate-limit Redis key 命名 | P2 |
| Q15 | 清空二次确认/宽限 | P1 |
| Q16 | 清空是否区分 isRead | P2 |
| Q17 | 大批量删除锁表 | P1 |
| Q18 | preferences 默认值策略 | P1 |
| Q19 | INSERT 时是否写默认值 | P1 |
| Q20 | **preferences 是否被通知投递读取** | **P0** (功能孤立) |
| Q21 | PATCH 部分字段覆盖 | P1 |
| Q22 | rate-limit 存储/重启 | P2 |
| Q23 | 列重命名迁移 | **P0** |
| Q24 | access 过期后行为 | P1 |
| Q25 | CSRF TTL 跨天 | P2 |
| Q26 | 匿名访问策略 | P2 |
| Q27 | N+1 性能 | P1 |
| Q28 | 通知 WebSocket 实时 | P1 |

---

## 8. 后续行动

1. **立即修复 P0-1（`system` 列转义）**，否则 preferences 模块**完全不可用**
2. **确认 P0-2（Q7 越权 PATCH 行为）**：写 JUnit 集成测试 `NotificationSecurityIT`
3. **写 JUnit 回归 `NotificationPreferenceIT`** 覆盖 PATCH/GET 默认值
4. **Flyway 迁移**：`V20260611___Rename_Notification_Preferences_System_Column.sql`
5. **前后端联调**：preferences 修好后，跑 console + management 双端的偏好设置页
6. **加 Arthas 监控脚本**：`scripts/arthas-watch-notification.sh` 持续 5 分钟观察 GC / DB 连接

---

## 9. 附录：Arthas MCP 调用记录

| 时间 | 工具 | 命令 | 用途 |
|------|------|------|------|
| 15:14:00 | `mcp__arthas-mcp__sc` | `sc -n 5 NotificationServiceImpl` | 确认 Service 类加载 |
| 15:14:05 | `mcp__arthas-mcp__watch` | `watch ... * '{params, returnObj, throwExp, cost}' -n 15` | 监听方法调用（60s 超时，改走 logs 路线） |
| 15:18:03 | `mcp__arthas-mcp__sc` | `sc -d -n 3 NotificationPreferenceMapper` | 确认 Mapper 代理 `jdk.proxy2.$Proxy164` |

> 同步阻塞的 `watch` / `trace` / `monitor` 在 Claude Code MCP 上下文里固定 30s 超时，已按 CLAUDE.md 强制约束降级到 `pm2 logs --nostream` 路径。
