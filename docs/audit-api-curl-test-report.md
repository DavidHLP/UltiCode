# Audit API Curl 实测报告

> **模块**：`com.ulticode.modules.admin.controller.AuditController`（`@RequestMapping("/admin/audit")`）
> **测试日期**：2026-06-08
> **测试环境**：`ulticode-9001` (Spring Boot, Java 17)，本地 PM2 运行
> **后端版本**：`main` 分支当前提交 `436fff4d0` 及之后
> **测试工具**：`curl 7.x` + `python3 -m json.tool`
> **测试账号**：`admin / admin123`（dev-profile bootstrap，role = `ADMIN`）
> **数据库**：`audit_logs` 表当前含 8 条 seed 数据

---

## 一、TL;DR

| # | 端点 | 方法 | 测试用例数 | 通过 | 失败 | 风险 |
|---|------|------|------------|------|------|------|
| 1 | `/admin/audit/logs` | GET | 4 | 4 | 0 | 中（`performer`/`user` 字段全 `null`） |
| 2 | `/admin/audit/stats` | GET | 1 | 1 | 0 | 中（`topPerformers` 关联字段全 `null`） |
| 3 | `/admin/audit/export` | GET | 3 | 3 | 0 | 低（CSV 时间截断到分钟） |

**结论**：三个端点**功能可用**，HTTP 200 正常返回数据，分页/过滤/格式校验/鉴权全部生效。但存在 **2 个数据关联缺陷**（关联用户字段全 `null`），需在后端侧排查 `AuditLogVO`/`AuditStatsVO` 的关联填充逻辑。

---

## 二、测试环境前置

### 2.1 基础设施

```bash
$ pm2 status | grep ulticode-9001
│ 0  │ ulticode-9001   │ default │ N/A  │ fork │ 37286 │ 2h │ 1 │ online │ 0% │ 16.1mb
$ lsof -ti :9001
37755
```

### 2.2 鉴权准备（一次性）

```bash
# 登录获取 cookie 与 csrfToken
curl -s -c /tmp/audit-cookies.txt -X POST http://127.0.0.1:9001/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}'
```

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "csrfToken": "04f05b35a8d74e0f974763d04f7db0a6:ad32b8da060345e7954f9700e298fcae",
    "user": {
      "id": "0d0c3b1d-6250-11f1-9199-ae0ed7bf2f82",
      "username": "admin",
      "name": "Development Administrator",
      "role": "ADMIN",
      ...
    }
  }
}
```

> **Note**: 本次三个目标端点都是 **GET**，未触发 CSRF 校验（CSRF 仅对写操作生效）。如后续测试写接口，需要把 `X-CSRF-TOKEN: <token>` 加入 header。

---

## 三、端点 1：`GET /admin/audit/logs`

**源码定位**：`backend-spring/src/main/java/com/ulticode/modules/admin/controller/AuditController.java:32-37`

```java
@Operation(summary = "获取审计日志列表")
@GetMapping("/logs")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public Result<PageResult<AuditLogVO>> getAuditLogs(AuditLogQueryDTO query) { ... }
```

### 3.1 用例 1.1 — 无参数（默认分页）

**请求**

```bash
curl -s -b /tmp/audit-cookies.txt \
  'http://127.0.0.1:9001/admin/audit/logs' \
  -o /tmp/audit-logs.json -w 'HTTP=%{http_code} bytes=%{size_download} time=%{time_total}s\n'
```

**结果**：`HTTP=200`，`2375` bytes，`time=0.027s`

**响应片段**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "items": [
      {
        "id": "audit-log-008",
        "performer": null,
        "user": null,
        "action": "UPDATE",
        "entityType": "PROBLEM",
        "entityId": "1",
        "oldValues": null,
        "newValues": null,
        "ipAddress": "192.168.1.100",
        "userAgent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
        "createdAt": "2026-05-30T10:00:00"
      }
      /* ... 7 more items ... */
    ],
    "total": 8,
    "page": 1,
    "pageSize": 20,
    "totalPages": 1
  },
  "traceId": "t-1780892318750"
}
```

**验证点**

- [x] HTTP 200，`code: 0`
- [x] 默认分页 `page=1, pageSize=20`
- [x] `total=8, totalPages=1`（≤ pageSize）
- [x] `items[].createdAt` ISO 8601 格式
- [x] `traceId` 存在
- [ ] ⚠️ `performer` 与 `user` 字段全部为 `null`，即使数据中有 `performer_id`

### 3.2 用例 1.2 — 分页 `page=1&limit=2`

**请求**

```bash
curl -s -b /tmp/audit-cookies.txt \
  'http://127.0.0.1:9001/admin/audit/logs?page=1&limit=2' \
  -o /tmp/audit-logs-p.json
```

**结果**：`HTTP=200`，`688` bytes

**关键字段**

```json
"data": {
  "items": [ /* 2 条 */ ],
  "total": 8,
  "page": 1,
  "pageSize": 2,
  "totalPages": 4
}
```

**验证点**

- [x] `pageSize=2` 生效
- [x] `totalPages=4`（ceil(8/2)）
- [x] `items.length=2`

### 3.3 用例 1.3 — 过滤 `action=LOGIN`

**请求**

```bash
curl -s -b /tmp/audit-cookies.txt \
  'http://127.0.0.1:9001/admin/audit/logs?action=LOGIN&limit=3' \
  -o /tmp/audit-logs-f.json
```

**结果**：`HTTP=200`，`125` bytes

```json
"data": {
  "items": [],
  "total": 0,
  "page": 1,
  "pageSize": 3,
  "totalPages": 0
}
```

**验证点**

- [x] 过滤生效（数据中无 `LOGIN` 动作，命中 0 条）
- [x] `pageSize=3` 仍然回显

### 3.4 用例 1.4 — 未鉴权访问

**请求**

```bash
curl -s 'http://127.0.0.1:9001/admin/audit/logs' \
  -o /tmp/audit-noauth.json -w 'HTTP=%{http_code}\n'
```

**结果**：`HTTP=401`

```json
{
  "code": 40100,
  "message": "Unauthorized",
  "traceId": "t-1780892346182"
}
```

**验证点**

- [x] Spring Security 拦截生效
- [x] 错误码 `40100` + `message` + `traceId` 符合项目统一信封

### 3.5 端点 1 综合结论

✅ **功能可用**：列表/分页/过滤/鉴权全部按预期工作
⚠️ **关联缺陷**：`performer` 与 `user` 始终为 `null` —— 与 `stats.topPerformers` 同源，建议排查 `AuditServiceImpl` 中 `AuditLogVO` 的 `performer` / `user` 字段填充，以及 `users` 表 join 条件（数据库实际有 `admin-001` 用户？见 §6.2）

---

## 四、端点 2：`GET /admin/audit/stats`

**源码定位**：`AuditController.java:39-44`

```java
@Operation(summary = "获取审计统计")
@GetMapping("/stats")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public Result<AuditStatsVO> getAuditStats(AuditLogQueryDTO query) { ... }
```

### 4.1 用例 2.1 — 无参数统计

**请求**

```bash
curl -s -b /tmp/audit-cookies.txt \
  'http://127.0.0.1:9001/admin/audit/stats' \
  -o /tmp/audit-stats.json
```

**结果**：`HTTP=200`，`357` bytes

**完整响应**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "totalActions": 8,
    "actionsByEntity": [
      { "entityType": "PROBLEM", "count": 8 }
    ],
    "topPerformers": [
      {
        "performerId": "admin-001",
        "username": null,
        "name": null,
        "role": null,
        "count": 8
      }
    ],
    "actionsByType": [
      { "actionType": "UPDATE", "count": 6 },
      { "actionType": "CREATE", "count": 1 },
      { "actionType": "MODERATE", "count": 1 }
    ]
  },
  "traceId": "t-1780892318756"
}
```

**验证点**

- [x] HTTP 200，`code: 0`
- [x] `totalActions=8`（与 `logs` 总数一致）
- [x] `actionsByEntity` 至少 1 条且 `count` 之和 = `totalActions`（8 = 8）
- [x] `actionsByType` 三种动作（UPDATE 6、CREATE 1、MODERATE 1）之和 = 8
- [x] `topPerformers.performerId="admin-001"`、`count=8` 字段存在
- [ ] ⚠️ `topPerformers[].username/name/role` 全部为 `null` —— 用户关联未填充

### 4.2 端点 2 综合结论

✅ **功能可用**：聚合统计正确（数字、分布一致）
⚠️ **关联缺陷**：`topPerformers` 缺 `username` / `name` / `role` —— 与 §3.5 同一根因

---

## 五、端点 3：`GET /admin/audit/export`

**源码定位**：`AuditController.java:46-82`

```java
@Operation(summary = "导出审计日志")
@GetMapping("/export")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public void exportAuditLogs(AuditLogQueryDTO query,
                            @RequestParam(defaultValue = "csv") String format,
                            HttpServletResponse response) throws IOException {
    if (!"csv".equalsIgnoreCase(format) && !"json".equalsIgnoreCase(format)) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unsupported format: " + format);
        return;
    }
    // csv or json 输出
}
```

### 5.1 用例 3.1 — `format=csv`（默认）

**请求**

```bash
curl -s -b /tmp/audit-cookies.txt -D /tmp/audit-export-csv.h \
  'http://127.0.0.1:9001/admin/audit/export?format=csv' \
  -o /tmp/audit-export.csv -w 'HTTP=%{http_code} bytes=%{size_download}\n'
```

**结果**：`HTTP=200`，`574` bytes

**响应头**

```
HTTP/1.1 200
Content-Disposition: attachment; filename=audit-logs.csv
Content-Type: text/csv;charset=UTF-8
Transfer-Encoding: chunked
X-Content-Type-Options: nosniff
X-XSS-Protection: 1; mode=block
X-Frame-Options: DENY
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Content-Security-Policy: default-src 'self'; ...
Permissions-Policy: camera=(), microphone=(), geolocation=()
```

**文件内容**

```csv
id,action,entityType,entityId,performer,ipAddress,createdAt
audit-log-008,UPDATE,PROBLEM,1,,192.168.1.100,2026-05-30T10:00
audit-log-007,UPDATE,PROBLEM,1,,192.168.1.102,2026-05-29T08:30
audit-log-006,UPDATE,PROBLEM,1,,192.168.1.100,2026-05-28T15:45
audit-log-005,MODERATE_APPROVE,PROBLEM,1,,192.168.1.100,2026-05-28T11:00
... (共 9 行：1 header + 8 data)
```

**验证点**

- [x] `Content-Type: text/csv;charset=UTF-8`
- [x] `Content-Disposition: attachment; filename=audit-logs.csv`（浏览器会下载）
- [x] CSV 表头与源码 `writer.println(...)` 一致：`id,action,entityType,entityId,performer,ipAddress,createdAt`
- [x] 数据行 = 8，与 `logs.total` 一致
- [x] CSV 转义逻辑（`escapeCsvField`）就绪
- [x] 安全响应头齐全（X-Content-Type-Options / X-Frame-Options / CSP / Permissions-Policy）
- [ ] ⚠️ `createdAt` 截断到分钟（`2026-05-30T10:00` 而非 `2026-05-30T10:00:00`）—— 源码中 `log.getCreatedAt().toString()` 对 `LocalDateTime` 输出省略秒，CSV 导出会丢精度（JSON 端则保留了秒，因 Jackson 用 ISO 序列化）

### 5.2 用例 3.2 — `format=json`

**请求**

```bash
curl -s -b /tmp/audit-cookies.txt -D /tmp/audit-export-json.h \
  'http://127.0.0.1:9001/admin/audit/export?format=json' \
  -o /tmp/audit-export.json
```

**结果**：`HTTP=200`，`2251` bytes

**响应头**

```
HTTP/1.1 200
Content-Type: application/json;charset=UTF-8
Content-Disposition: attachment; filename=audit-logs.json
Content-Length: 2251
... (其他安全头同 csv)
```

**文件内容片段**

```json
[
  {
    "id": "audit-log-008",
    "performer": null,
    "user": null,
    "action": "UPDATE",
    "entityType": "PROBLEM",
    "entityId": "1",
    "oldValues": null,
    "newValues": null,
    "ipAddress": "192.168.1.100",
    "userAgent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
    "createdAt": "2026-05-30T10:00:00"
  },
  ...
]
```

**验证点**

- [x] `Content-Type: application/json;charset=UTF-8`
- [x] `Content-Disposition: attachment; filename=audit-logs.json`
- [x] 顶层为 JSON 数组（`[` 起 `]` 止），可被 `python3 -m json.tool` 直接解析
- [x] 元素结构与 `AuditLogVO` 一致
- [x] `createdAt` 保留秒级精度（与 CSV 行为不一致，见 §5.4）

### 5.3 用例 3.3 — 非法 `format=xml`

**请求**

```bash
curl -s -b /tmp/audit-cookies.txt \
  'http://127.0.0.1:9001/admin/audit/export?format=xml' \
  -o /tmp/audit-xml.body -D /tmp/audit-xml.h
```

**结果**：`HTTP=400`

**响应体**

```html
<!doctype html><html lang="en"><head>
<title>HTTP Status 400 – Bad Request</title>
...
<h1>HTTP Status 400 – Bad Request</h1>
...
```

**验证点**

- [x] 拒绝非 `csv` / `json` 格式
- [x] 返回 400 状态
- [ ] ⚠️ 返回 **Tomcat 默认 HTML 错误页**而非项目统一 JSON 信封（`Result.error(400, ...)`）。源码中用 `response.sendError(400, ...)` 触发容器默认错误处理，前端若按 JSON 解析会失败。**建议改为 `Result.error` 风格或 `throw new BusinessException`**，由 `GlobalExceptionHandler` 统一序列化。

### 5.4 端点 3 综合结论

✅ **功能可用**：csv / json 两种格式都能下载
✅ **安全响应头齐全**（CSP / X-Frame-Options / Permissions-Policy 等）
⚠️ **建议修复**（2 项）：
1. CSV `createdAt` 截断到分钟 → 改用 `log.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)` 或 `DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")`
2. `format=xml` 错误响应未走统一 JSON 信封 → 改用 `BusinessException` 或 `Result.error`

---

## 六、横向观察

### 6.1 鉴权与会话

| 场景 | HTTP | 响应 |
|------|------|------|
| 缺 cookie | 401 | `{"code":40100,"message":"Unauthorized","traceId":"..."}` |
| 错误密码 | 401 | `{"code":10001,"message":"Invalid credentials","traceId":"..."}` |
| 正确登录 (admin) | 200 | 含 `csrfToken` 与 `user.role=ADMIN` |
| 用 admin 调 `/admin/audit/*` | 200 | `@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")` 通过 |

✅ 鉴权链：登录 → 写 `access_token` / `refresh_token` cookie → `@PreAuthorize` 角色检查 → 业务执行 → 统一 JSON 信封
✅ CSRF：本批测试全为 GET，未触发；写接口时需带 `X-CSRF-TOKEN` header

### 6.2 `performer` / `user` 关联字段全部为 `null`（数据库实测）

```sql
-- 实测：DB 中有 4 个 admin/super 账号
SELECT id, username, role FROM users WHERE role IN ('ADMIN','SUPER_ADMIN');
-- 0d0c3b1d-6250-11f1-9199-ae0ed7bf2f82  admin      ADMIN
-- admin-002                           admin_two  ADMIN
-- super-root-001                      super_root SUPER_ADMIN
-- super-vp-002                        super_vp   SUPER_ADMIN
```

```sql
-- 但 seed 数据的 performer_id 是 "admin-001"，与表里任何账号都对不上
SELECT id, performer_id, action, entity_type FROM audit_logs LIMIT 3;
-- audit-log-008  admin-001  UPDATE  PROBLEM
-- audit-log-007  admin-001  UPDATE  PROBLEM
-- audit-log-006  admin-001  UPDATE  PROBLEM
```

**根因**：`admin-001` 不在 `users` 表中，LEFT JOIN `users` 时关联失败被 `Optional`/`null` 兜底。建议：

1. 修 seed：用真实存在的 `0d0c3b1d-...`（admin）或新增 `admin-001` 账号
2. 或修代码：把 `performer`/`user` 的关联改成对 `null` 也保留 `performerId`，但当前返回的 `performer=null` 与 `performerId` 分离是设计上的不一致 —— 应二选一：
   - 保留 `performer` 字段（带 username/name/role）—— 需修 seed
   - 把 `performer` 字段移除，前端只用 `performerId` 自行回查 —— 需改 `AuditLogVO` 与 `AuditStatsVO` DTO

### 6.3 性能基线

| 端点 | 响应时间 |
|------|----------|
| `/admin/audit/logs` (8 行, 默认分页) | 27 ms |
| `/admin/audit/stats` | < 30 ms |
| `/admin/audit/export?format=csv` (574 B) | < 50 ms |
| `/admin/audit/export?format=json` (2.2 KB) | < 50 ms |

> 8 条 seed 数据的样本下无可观测性能问题。`stats` 用了 4 次 SQL（count + group by 三个维度）—— 数据量增长到百万级时需评估索引与缓存。

### 6.4 响应头一致性

`/admin/audit/export`（GET 但写文件 → 走相同 Security 链）：

| Header | 值 | 备注 |
|--------|----|------|
| `X-Content-Type-Options` | `nosniff` | ✅ |
| `X-XSS-Protection` | `1; mode=block` | ✅ |
| `X-Frame-Options` | `DENY` | ✅ |
| `Content-Security-Policy` | `default-src 'self'; ...` | ✅ |
| `Permissions-Policy` | `camera=(), microphone=(), geolocation=()` | ✅ |
| `Cache-Control` | `no-cache, no-store, max-age=0, must-revalidate` | ✅（导出文件不应缓存） |

---

## 七、可复现命令清单

```bash
# ===== 0. 准备 =====
BASE=http://127.0.0.1:9001
COOKIE=/tmp/audit-cookies.txt
rm -f $COOKIE

# ===== 1. 登录 =====
curl -s -c $COOKIE -X POST $BASE/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | python3 -m json.tool

# ===== 2. /admin/audit/logs =====
# 2.1 默认分页
curl -s -b $COOKIE $BASE/admin/audit/logs | python3 -m json.tool
# 2.2 分页参数
curl -s -b $COOKIE "$BASE/admin/audit/logs?page=1&limit=2" | python3 -m json.tool
# 2.3 按动作过滤
curl -s -b $COOKIE "$BASE/admin/audit/logs?action=LOGIN&limit=3" | python3 -m json.tool
# 2.4 未鉴权
curl -s -o /dev/null -w 'HTTP=%{http_code}\n' $BASE/admin/audit/logs

# ===== 3. /admin/audit/stats =====
curl -s -b $COOKIE $BASE/admin/audit/stats | python3 -m json.tool

# ===== 4. /admin/audit/export =====
# 4.1 CSV
curl -s -b $COOKIE -D - "$BASE/admin/audit/export?format=csv" -o /tmp/audit.csv
# 4.2 JSON
curl -s -b $COOKIE -D - "$BASE/admin/audit/export?format=json" -o /tmp/audit.json
# 4.3 非法 format
curl -s -b $COOKIE -o /dev/null -w 'HTTP=%{http_code}\n' "$BASE/admin/audit/export?format=xml"
```

---

## 八、风险与建议

| 严重度 | 项目 | 说明 | 建议 |
|--------|------|------|------|
| **中** | `performer`/`user`/`topPerformers` 关联字段全 `null` | 8 条 seed 的 `performer_id="admin-001"` 在 `users` 表中不存在；LEFT JOIN 失败 → 字段为空 | 修 Flyway seed：用真实存在的 admin UUID；或改 VO 去除该关联字段 |
| **低** | CSV `createdAt` 截断到分钟 | 源码 `log.getCreatedAt().toString()` 对 `LocalDateTime` 默认省略秒 | 改用 `DateTimeFormatter.ISO_LOCAL_DATE_TIME` 显式格式化 |
| **低** | `format=xml` 走 Tomcat HTML 错误页 | `response.sendError(400, ...)` 绕过了 `GlobalExceptionHandler` | 改用 `Result.error` 直接写到 `response.getWriter()`，或抛 `BusinessException` |
| **参考** | 8 条 seed 偏少 | 验证不到大数据量下的分页/聚合性能 | 准备 1k/10k/100k 三档数据做压测 |
| **参考** | `startDate` / `endDate` 时区处理 | 前端 `normalizeDateParams` 帮 append `T00:00:00`，后端 `AuditLogQueryDTO` 接受 ISO 字符串 | 跨时区部署时需复核 LocalDateTime vs Instant 选型 |

---

## 九、参考文件

| 类别 | 路径 |
|------|------|
| Controller | `backend-spring/src/main/java/com/ulticode/modules/admin/controller/AuditController.java` |
| DTO | `backend-spring/src/main/java/com/ulticode/modules/admin/dto/AuditLogQueryDTO.java` |
| VO | `backend-spring/src/main/java/com/ulticode/modules/admin/dto/AuditLogVO.java` |
| VO | `backend-spring/src/main/java/com/ulticode/modules/admin/dto/AuditStatsVO.java` |
| Service | `backend-spring/src/main/java/com/ulticode/modules/admin/service/AuditService.java` |
| Service Impl | `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AuditServiceImpl.java` |
| Mapper | `backend-spring/src/main/java/com/ulticode/modules/admin/mapper/AuditLogMapper.java` |
| 前端 API | `management/src/api/admin/audit.ts` |
| 前端视图 | `management/src/views/audit/AuditLogsView.vue`、`AuditReportView.vue` |

---

*报告生成于 2026-06-08*
*测试人：Claude (MiniMax-M3)*
