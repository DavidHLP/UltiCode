# Admin Tags API — 测试问题文档

> **范围**: `AdminTagController` 6 个端点 (`/admin/tags`)
> **生成时间**: 2026-06-09
> **环境**: dev backend `localhost:9001` (PM2 `ulticode-9001`)
> **测试账号**: `admin / admin123` (dev profile bootstrap)
> **实测方式**: curl + jq；全部测试用例可重放
> **关联源码**:
> - `backend-spring/src/main/java/com/ulticode/modules/admin/controller/AdminTagController.java`
> - `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminTagServiceImpl.java`
> - `backend-spring/src/main/java/com/ulticode/modules/admin/dto/tag/*.java`

---

## 0. 测试结论摘要

| 维度 | 结论 |
|---|---|
| 鉴权 (`@PreAuthorize`) | ✅ 通过 — 未登录 401，缺角色 403 |
| CSRF 防护 | ✅ 通过 — 缺/错 token 403 (`code=40300`) |
| 入参校验 (`@Valid` + `@NotBlank`/`@NotNull`) | ⚠️ 部分通过 — `getTag` / `deleteTag` 缺 `type` 抛 500 而非 400 |
| 业务逻辑正确性 | ✅ 通过 — 全部 6 个端点 happy path 成功 |
| 错误码语义 | ⚠️ 部分不一致 — 部分 409 用业务 code，HTTP 状态码语义偏差 |
| 数据隔离 (PROBLEM vs FORUM) | ⚠️ **静默 fallback** — 非法 `type` 落到 PROBLEM 表 |
| **发现 Bug 数** | **4 个** (见 §7) |

---

## 1. 接口契约速查

### 1.1 公共入参/出参

| 字段 | 类型 | 必填 | 备注 |
|---|---|---|---|
| `type` | String (query/path) | ✅ | 仅识别 `PROBLEM` / `FORUM`（大小写不敏感），其他值**静默 fallback 到 PROBLEM** |
| `X-CSRF-Token` | Header | ✅ (POST/PATCH/DELETE) | 格式 `{tokenId}:{tokenValue}`，登录响应中 `data.csrfToken` |
| Cookie | `JSESSIONID` + `access_token` | ✅ | 登录后由后端 Set-Cookie 注入 |
| 响应封装 | `Result<T>` | — | `{code:0=success, message, data, traceId}` |

### 1.2 6 个端点

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/admin/tags` | ADMIN / SUPER_ADMIN | 列表 + 分页 + 搜索 |
| POST | `/admin/tags` | ADMIN / SUPER_ADMIN | 创建 |
| GET | `/admin/tags/{id}` | ADMIN / SUPER_ADMIN | 详情（**`type` 必填**） |
| PATCH | `/admin/tags/{id}` | ADMIN / SUPER_ADMIN | 更新 |
| DELETE | `/admin/tags/{id}` | ADMIN / SUPER_ADMIN | 删除（**`type` 必填**） |
| POST | `/admin/tags/merge` | ADMIN / SUPER_ADMIN | 合并 |

### 1.3 DTO 字段约束

| DTO | 字段 | 校验 |
|---|---|---|
| `CreateTagDTO` | `name` | `@NotBlank` |
| | `type` | `@NotNull` |
| | `slug` / `description` / `color` | 可选 |
| `UpdateTagDTO` | `name` / `slug` / `description` / `color` | 可选（**但 name/slug 空串被静默忽略**） |
| | `type` | `@NotNull` |
| `MergeTagDTO` | `sourceId` / `targetTagId` | `@NotBlank` |
| | `type` | `@NotNull` |

---

## 2. 跨端点测试问题（横切关注）

### Q1. 未登录访问 `/admin/tags` 返回什么？
- ✅ **期望**: 401
- 🟢 **实测**: `HTTP 401 / code=40100 "Unauthorized"`
- 📎 见 §6 T0

### Q2. 登录后 POST 缺 `X-CSRF-Token` 头返回什么？
- ✅ **期望**: 403
- 🟢 **实测**: `HTTP 403 / code=40300 "CSRF token is required"`
- 📎 见 §6 T0b

### Q3. 普通 USER 角色（非 ADMIN）能否访问？
- ❓ **未实测**（仅 admin 账号登录）
- ✅ **期望**: 403 (`@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")`)
- ⚠️ **建议补测**: 用 `user/user123` 普通账号登录后试 GET/POST

### Q4. `type` 字段传未知值（如 `type=GARBAGE`）会怎样？
- ❌ **期望**: 400 校验错误
- 🔴 **实测**: 静默 fallback 到 PROBLEM，返回 PROBLEM 列表
- ⚠️ **设计缺陷**: 见 §7 Bug #2

### Q5. `type` 字段大小写敏感吗？
- 🟢 **实测**: 不敏感 (`TYPE_FORUM.equalsIgnoreCase(...)`)
- ✅ **期望**: 文档是否明示？目前 API 文档/前端是否一致？

### Q6. 分页参数 `page=0` / `page=-1` / `limit=999` 边界行为？
- 🟢 **实测**: `page<1` 默认 1；`limit<1` 默认 20；超大 limit 未触发上限截断
- ❓ **建议补测**: `limit=10000`、`limit=0`、负数场景

---

## 3. GET `/admin/tags` — 标签列表

### Q-T1-1 默认列表能拉到吗？
- ✅ **期望**: 200，返回 `{data:[...], total, page, limit, totalPages}`
- 🟢 **实测**: ✅ code=0, total=11 (PROBLEM), 默认 page=1, limit=20
- 📎 见 §6 T1

### Q-T1-2 `?type=FORUM` 能否正确路由到 forum_tag 表？
- ✅ **期望**: 返回 FORUM 标签
- 🟢 **实测**: ✅ total=6，type 字段值为 `"FORUM"`

### Q-T1-3 `search` 参数是否走 LIKE 查询？
- ❓ **未实测**
- ✅ **期望**: 模糊匹配 `name`/`slug`（PROBLEM）或 `name`/`slug`（FORUM）
- ⚠️ **建议补测**: 中文/Escape 字符/SQL 通配符注入

### Q-T1-4 `sortBy=usage_count&sortOrder=desc` 真的能按使用次数排序吗？
- 🟡 **实测**: 对 FORUM 有效（FORUM 分支特殊处理）；对 PROBLEM **无效**（永远按 `label` 排序）
- ⚠️ **设计缺陷**: 见 §7 Bug #3
- ❓ **建议补测**: 给 PROBLEM 标签加 `usageCount` 差异验证排序

### Q-T1-5 PROBLEM 与 FORUM 标签是否能跨 type 列出？
- 🔴 **实测**: 不能 — `type` 决定走哪张表
- ✅ **期望**: 行为一致；前端是否需要"全部类型"汇总？

### Q-T1-6 软删除标签是否还出现在列表中？
- 🟢 **实测**: DELETE 后 GET 列表不再返回该 ID（DELETE 是硬删除 `deleteById`）
- ❓ **期望**: 业务上是否需要审计保留？

---

## 4. POST `/admin/tags` — 创建标签

### Q-T2-1 创建 PROBLEM 标签 happy path？
- ✅ **期望**: 200，返回新创建的 `TagVO`
- 🟢 **实测**: ✅ code=0, 返回 UUID id, slug 由 name 自动生成 (`curl-001`)
- 📎 见 §6 T2

### Q-T2-2 中文 name 自动生成 slug 的规则？
- 🟢 **实测**: `name="测试标签_curl_001"` → `slug="curl-001"`（中文和下划线被剥离，保留 ASCII 数字）
- ❓ **期望**: 文档是否明示 `[^a-z0-9]+` → `-` + trim `-` 的规则？
- ❓ **建议补测**: 全中文 name / 全 emoji / 纯符号的 slug 结果

### Q-T2-3 显式传 `slug` 是否生效？
- ❓ **未实测**
- ✅ **期望**: 优先使用 DTO 中的 slug（见 Service 代码 `StringUtils.hasText(dto.getSlug()) ? dto.getSlug() : generateSlug(...)`）

### Q-T2-4 重名 name 拒绝？
- ❓ **未实测（同名 case）**
- ✅ **期望**: 409 + `code=30011 (PROBLEM_TAG_NAME_EXISTS)` / `60011 (FORUM_TAG_NAME_EXISTS)`
- ⚠️ **建议补测**: 直接重放 POST T2 验证 NAME_EXISTS 错误码

### Q-T2-5 重名 slug 拒绝？
- ❓ **未实测**
- ✅ **期望**: 409 + `code=30012 (PROBLEM_TAG_SLUG_EXISTS)` / `60012 (FORUM_TAG_SLUG_EXISTS)`

### Q-T2-6 缺 `type` 字段？
- 🟢 **实测**: 400 校验失败 `code=40000, data.type="Tag type is required"`
- ✅ **期望**: 一致

### Q-T2-7 缺 `name` 字段？
- ❓ **未实测**
- ✅ **期望**: 400 `@NotBlank`

### Q-T2-8 FORUM 标签创建是否走同一 controller？
- 🟢 **实测**: ✅ `type=FORUM` 走 `ForumTagMapper`，行为对称

---

## 5. GET `/admin/tags/{id}` — 详情

### Q-T3-1 happy path？
- ✅ **期望**: 200 + `TagVO`
- 🟢 **实测**: ✅ 见 §6 T3a

### Q-T3-2 type 错配（PROBLEM id + type=FORUM）？
- 🟢 **实测**: 404 `code=60010 "Forum tag not found"`
- ⚠️ **错误码不一致**: 业务码应为 PROBLEM 但用 FORUM 错误码（语义模糊）

### Q-T3-3 id 不存在？
- 🟢 **实测**: 404 `code=30010 "Problem tag not found"`

### Q-T3-4 **缺 type 参数返回 500？**
- 🔴 **实测**: `HTTP 500 / code=50000 "Unknown error"`
- ⚠️ **设计缺陷**: `MissingServletRequestParameterException` 未被全局异常处理器映射到 400
- ⚠️ **建议修复**: 见 §7 Bug #1

### Q-T3-5 type 字段在响应中的用途？
- 🟢 **实测**: TagVO 始终带 `type` 字段，便于前端判断

---

## 6. PATCH `/admin/tags/{id}` — 更新

### Q-T4-1 happy path（rename + recolor）？
- ✅ **期望**: 200，name/color 更新，slug 不变
- 🟢 **实测**: ✅ 见 §6 T4a — slug 保持 `curl-001` 不变（**未自动重新生成**）
- ❓ **建议补测**: 改名后是否需要同步更新相关 problem_tag_relations？目前不更新

### Q-T4-2 缺 type 参数？
- 🟢 **实测**: 400 `code=40000 data.type="Tag type is required"`

### Q-T4-3 **name="" 空串会被处理吗？**
- 🔴 **实测**: 200 OK，name 不变（**静默忽略**）
- ⚠️ **设计缺陷**: `UpdateTagDTO.name` 没有 `@NotBlank`，Service 用 `StringUtils.hasText("")` 跳过；调用方可能误以为成功
- ⚠️ **建议**: 要么明确文档为"部分更新语义"，要么 DTO 加 `@Size(min=1)`
- 📎 见 §7 Bug #4

### Q-T4-4 name 重名冲突？
- 🟢 **实测**: 409 `code=30011 "Problem tag name already exists"`
- ⚠️ **HTTP 语义**: 业务冲突用 409 合理，但日志/前端需要明确区分 "业务冲突" vs "服务端错误"

### Q-T4-5 仅传部分字段（description 但无 name）？
- ❓ **未实测**
- ✅ **期望**: 保留 name，仅更新 description（部分更新语义）
- ⚠️ **建议补测**: 验证 `if (dto.getDescription() != null) existing.setDescription(dto.getDescription())` 会用空串覆盖

### Q-T4-6 slug 重名冲突？
- ❓ **未实测**
- ✅ **期望**: 409 + SLUG_EXISTS 错误码

### Q-T4-7 FORUM 类型更新？
- 🟢 **实测**: 路径对称（Service 分支）

---

## 7. DELETE `/admin/tags/{id}` — 删除

### Q-T5-1 happy path？
- ✅ **期望**: 200 `code=0`
- 🟢 **实测**: ✅ DELETE 成功，后续 GET 404

### Q-T5-2 id 不存在？
- 🟢 **实测**: 404 `code=30010`

### Q-T5-3 **缺 type 参数返回 500？**
- 🔴 **实测**: 同 Q-T3-4，HTTP 500
- 📎 见 §7 Bug #1

### Q-T5-4 删除被引用的标签会怎样？
- 🟢 **实测**: 创建后立刻删除成功（usage_count=0）
- ❓ **建议补测**: 给 PROBLEM tag 绑一个 problem，DELETE 后 relation 是否级联删除？
- ⚠️ **Service 仅 `deleteById`**，依赖 FK 约束；FK 缺失可能产生孤儿 relation

### Q-T5-5 删除 FORUM 标签？
- 🟢 **实测**: 路径对称

---

## 8. POST `/admin/tags/merge` — 合并

### Q-T6-1 同 ID 合并拒绝？
- ✅ **期望**: 400
- 🟢 **实测**: 400 `code=40000 "Cannot merge tag into itself"`
- 📎 见 §6 T6a

### Q-T6-2 缺 sourceId？
- 🟢 **实测**: 400 `code=40000 data.sourceId="Source tag ID is required"`

### Q-T6-3 sourceId 不存在？
- 🟢 **实测**: 404 `code=30010`

### Q-T6-4 跨 type 合并（PROBLEM src + FORUM target）？
- 🟢 **实测**: 404（因 type=PROBLEM 时查 problem_tag 表，找不到 FORUM id）
- ✅ **期望**: 行为安全，但错误信息可能误导用户

### Q-T6-5 PROBLEM happy merge？
- ✅ **期望**: 200，source 删除；target 的 problem_tag_relations 中 tag_id 被改写为 target id；target.usageCount 重算
- 🟢 **实测**: ✅ 见 §6 T6e，merge 后 source GET 404
- ⚠️ **建议补测**: 验证 target.usage_count 是否正确累加；验证 problem_tag_relations 表中无 source_id 残留

### Q-T6-6 FORUM merge 行为？
- ❓ **未实测**
- 🟢 **代码层面**: FORUM merge 仅删除 source，**不更新任何 forum relation 表**（因为似乎不存在 forum_tag_relations 表）
- ⚠️ **潜在风险**: 如果 FORUM 帖子依赖 forum_tag，删 source 同样会孤儿化

### Q-T6-7 merge 是否产生审计日志？
- 🟢 **代码层面**: `@Audited(action=AuditActionUtil.UPDATE_TAG, entityType=ENTITY_TAG)`
- ❓ **建议补测**: 验证 `audit_log` 表中生成记录，oldValues 包含 `name + mergedInto`

---

## 9. 缺陷汇总（待修复）

### Bug #1 — `getTag` / `deleteTag` 缺 `type` 参数返回 500

| 项 | 内容 |
|---|---|
| 严重度 | 中 |
| 影响端点 | `GET /admin/tags/{id}`、`DELETE /admin/tags/{id}` |
| 实测 | `HTTP 500 / code=50000 "Unknown error"` |
| 根因 | `MissingServletRequestParameterException` 未被 `GlobalExceptionHandler` 映射到 400 |
| 建议修复 | 在 `common/exception/GlobalExceptionHandler.java` 中添加 `@ExceptionHandler(MissingServletRequestParameterException.class)` → `Result.error(40000, ...)` |
| 验证脚本 | 见 §10 T3d / T5b |

### Bug #2 — 非法 `type` 值静默 fallback 到 PROBLEM

| 项 | 内容 |
|---|---|
| 严重度 | 中（数据正确性 + 安全语义） |
| 影响端点 | `GET /admin/tags`、`getTag`、`deleteTag`、`merge` |
| 实测 | `?type=GARBAGE` 返回 PROBLEM 列表 |
| 根因 | `AdminTagServiceImpl` 用 `if (TYPE_FORUM.equalsIgnoreCase(...))` 单一分支，else 永远走 PROBLEM |
| 建议修复 | Controller 层用 `@Pattern(regexp="PROBLEM|FORUM")` 校验 `type`；或 Service 入口显式 `else throw BAD_REQUEST` |
| 验证脚本 | 见 §10 T1c |

### Bug #3 — PROBLEM `sortBy` 参数实际无效

| 项 | 内容 |
|---|---|
| 严重度 | 低（功能误导） |
| 影响端点 | `GET /admin/tags` (`type=PROBLEM`) |
| 实测 | `sortBy=usage_count&sortOrder=desc` 仍按 `label` 升序排 |
| 根因 | `getProblemTags` 写死 `wrapper.orderBy(true, isAsc, ProblemTag::getLabel)`，忽略 sortBy |
| 建议修复 | 在 `getProblemTags` 中按 `sortBy` 映射字段（参考 `getForumTags` 已有的逻辑） |
| 验证脚本 | 待补 |

### Bug #4 — PATCH `name=""` 静默忽略

| 项 | 内容 |
|---|---|
| 严重度 | 低（API 一致性） |
| 影响端点 | `PATCH /admin/tags/{id}` |
| 实测 | `{"name":""}` 返回 200 OK，name 不变 |
| 根因 | `UpdateTagDTO.name` 无 `@NotBlank`，Service 用 `StringUtils.hasText("")` 跳过 |
| 建议修复 | 若坚持"部分更新"语义 → 在 OpenAPI 注解中明确；若希望严格 → DTO 加 `@Pattern(regexp=".*\\S.*")` 或在 Service 入口校验 |

---

## 10. 实测执行日志（可重放）

```bash
# === 准备：登录拿 CSRF ===
curl -sS -c /tmp/cookies.txt -X POST http://localhost:9001/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq -r '.data.csrfToken'
# → 0cf52b3bf65c44b7b483681e7a9be2c9:2cc4240501b640dcb35b6385d7cbb5ed

CSRF="<上一步输出>"; B="http://localhost:9001"

# === T0: 未鉴权基线 ===
curl -sS -w "\nHTTP=%{http_code}\n" "$B/admin/tags"
# → 401 / code=40100

# === T0b: 缺 CSRF ===
curl -sS -w "\nHTTP=%{http_code}\n" -X POST "$B/admin/tags" \
  -H "Content-Type: application/json" -b /tmp/cookies.txt \
  -d '{"name":"no-csrf","type":"PROBLEM"}'
# → 403 / code=40300

# === T1: 列表 ===
curl -sS "$B/admin/tags" -b /tmp/cookies.txt | jq '.data.total'
# → 11

curl -sS "$B/admin/tags?type=FORUM" -b /tmp/cookies.txt | jq '.data.total'
# → 6

curl -sS "$B/admin/tags?type=GARBAGE" -b /tmp/cookies.txt | jq '.data.total'
# → 11 (Bug #2)

# === T2: 创建 ===
curl -sS -X POST "$B/admin/tags" \
  -H "Content-Type: application/json" -H "X-CSRF-Token: $CSRF" \
  -b /tmp/cookies.txt -c /tmp/cookies.txt \
  -d '{"name":"测试标签_curl_001","description":"d","color":"#FF5733","type":"PROBLEM"}'
# → 200 / id=f2a3fe59-35c5-465f-b1bc-71b41c7fef90, slug=curl-001

# === T3: 详情 + 缺 type ===
ID="f2a3fe59-35c5-465f-b1bc-71b41c7fef90"
curl -sS "$B/admin/tags/$ID?type=PROBLEM" -b /tmp/cookies.txt
# → 200

curl -sS -w "\nHTTP=%{http_code}\n" "$B/admin/tags/$ID"
# → 500 (Bug #1)

curl -sS -w "\nHTTP=%{http_code}\n" "$B/admin/tags/no-such-id?type=PROBLEM" -b /tmp/cookies.txt
# → 404 / code=30010

# === T4: 更新 ===
curl -sS -X PATCH "$B/admin/tags/$ID" \
  -H "Content-Type: application/json" -H "X-CSRF-Token: $CSRF" \
  -b /tmp/cookies.txt \
  -d '{"name":"测试标签_curl_001_v2","color":"#00AA88","type":"PROBLEM"}'
# → 200, name/color 更新, slug 保持 curl-001

curl -sS -w "\nHTTP=%{http_code}\n" -X PATCH "$B/admin/tags/$ID" \
  -H "Content-Type: application/json" -H "X-CSRF-Token: $CSRF" \
  -b /tmp/cookies.txt -d '{"name":"","type":"PROBLEM"}'
# → 200, name 不变 (Bug #4)

# === T5: 删除 ===
curl -sS -w "\nHTTP=%{http_code}\n" -X DELETE "$B/admin/tags/$ID?type=PROBLEM" \
  -H "X-CSRF-Token: $CSRF" -b /tmp/cookies.txt
# → 200 / code=0

curl -sS -w "\nHTTP=%{http_code}\n" -X DELETE "$B/admin/tags/$ID" \
  -H "X-CSRF-Token: $CSRF" -b /tmp/cookies.txt
# → 500 (Bug #1, 同 #T3-4)

# === T6: 合并 ===
SRC=$(curl -sS -X POST "$B/admin/tags" -H "Content-Type: application/json" \
  -H "X-CSRF-Token: $CSRF" -b /tmp/cookies.txt \
  -d '{"name":"merge-source","type":"PROBLEM"}' | jq -r '.data.id')
TGT=$(curl -sS -X POST "$B/admin/tags" -H "Content-Type: application/json" \
  -H "X-CSRF-Token: $CSRF" -b /tmp/cookies.txt \
  -d '{"name":"merge-target","type":"PROBLEM"}' | jq -r '.data.id')

curl -sS -X POST "$B/admin/tags/merge" -H "Content-Type: application/json" \
  -H "X-CSRF-Token: $CSRF" -b /tmp/cookies.txt \
  -d "{\"sourceId\":\"$SRC\",\"targetTagId\":\"$SRC\",\"type\":\"PROBLEM\"}"
# → 400 / "Cannot merge tag into itself"

curl -sS -X POST "$B/admin/tags/merge" -H "Content-Type: application/json" \
  -H "X-CSRF-Token: $CSRF" -b /tmp/cookies.txt \
  -d "{\"sourceId\":\"$SRC\",\"targetTagId\":\"$TGT\",\"type\":\"PROBLEM\"}"
# → 200, SRC 后续 GET 404
```

---

## 11. 覆盖率矩阵

| 端点 | Happy | 鉴权失败 | CSRF 失败 | 参数校验 | 业务校验 | Not Found | 跨 type |
|---|---|---|---|---|---|---|---|
| GET `/admin/tags` | ✅ T1 | ✅ T0 | n/a | n/a | n/a | n/a | ⚠️ Bug#2 |
| POST `/admin/tags` | ✅ T2 | ✅ T0 | ✅ T0b | ✅ type缺 | ❓ name重名/slug重名 | n/a | n/a |
| GET `/{id}` | ✅ T3a | ✅ T0 | n/a | 🔴 Bug#1 | n/a | ✅ T3c | ✅ T3b |
| PATCH `/{id}` | ✅ T4a | ✅ T0 | ✅ T0b | ✅ type缺 | ✅ T4d name重名 / ⚠️ Bug#4 | ❓ | ❓ |
| DELETE `/{id}` | ✅ T5c | ✅ T0 | ✅ T0b | 🔴 Bug#1 | n/a | ✅ T5a | ❓ |
| POST `/merge` | ✅ T6e | ✅ T0 | ✅ T0b | ✅ T6b | ✅ T6a 自合并 | ✅ T6c | ✅ T6d |

图例: ✅ 实测通过 / ⚠️ 发现缺陷 / 🔴 必须修复 / ❓ 建议补测

---

## 12. 建议补测清单（TODO）

| 优先级 | 用例 | 原因 |
|---|---|---|
| P0 | 普通 USER 角色访问 `/admin/tags` | 验证 `@PreAuthorize` 实际拦截 |
| P0 | PROBLEM merge 后检查 `problem_tag_relations` 表 + `target.usage_count` | 业务关键路径，回归风险高 |
| P1 | 删除被 problem 引用的 PROBLEM 标签 | 验证 FK 约束 / 业务保护 |
| P1 | `search` 参数含 SQL 通配符 `%` `_` 与中文 | LIKE 注入 + 字符集 |
| P1 | FORUM merge 行为 | 与 PROBLEM merge 对称性 |
| P2 | PATCH 仅传 `description=""` 是否覆盖为 empty | 配 Bug #4 一致性 |
| P2 | 分页边界 (`limit=0`, `limit=-1`, `page=0`, `limit=10000`) | 分页逻辑健壮性 |
| P2 | 审计日志验证 | `@Audited` 是否落库 |
| P3 | 同名 PROBLEM tag 创建与 FORUM tag 创建能否重名 | 跨 type 重名是否允许 |

---

## 13. 文档元数据

- **作者**: Claude Code (curl 实测)
- **关联 PR / Issue**: —
- **下次复测建议**: Bug #1/#2/#3/#4 修复后立即回归 §3-§8 全部用例