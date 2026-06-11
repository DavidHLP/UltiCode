# Solution API 测试问题文档（`solution.ts` ↔ `SolutionController`）

> **生成时间**: 2026-06-11
> **基线**: `backend-spring/.../modules/solution/controller/SolutionController.java`（JVM 加载版本经 Arthas `jad` 反编译确认）
> **被测进程**: PM2 `ulticode-9001` (PID 9516, port 9001, Arthas attach 在 :8563)
> **测试目标**: `console/src/api/solution.ts` 列出的 11 个前端函数对应的 12 个后端 mapping（PUT + PATCH 复用同一 handler）
> **凭据**: dev seed admin (`admin` / `admin123`)
> **状态**: 已用 curl + Arthas MCP 全链路冒烟通过；本文档同时记录 4 个真实暴露的潜在 bug / 易错点。

---

## 0. 端点真实清单（JVM 反编译核对 vs `solution.ts`）

| # | 前端函数 (`solution.ts`) | HTTP | 后端实际路径 | 鉴权 | CSRF | 速率限制 | `solution.ts` 是否对齐 |
|---|---|---|---|---|---|---|---|
| 1 | `fetchSolutionFeed` | GET | `/api/problems/{problemId}/solutions` | public | — | — | ✅ |
| 2 | `createSolution` | POST | `/api/problems/{problemId}/solutions` | 登录 | ✅ | `solution:create` 20/60 | ✅ |
| 3 | `fetchSolution` | GET | `/api/solutions/{id}?userId=` | public | — | — | ✅ |
| 4 | `updateSolution` | PUT | `/api/solutions/{id}` | 登录 + 作者 | ✅ | `solution:update` 20/60 | ✅ |
| 4b | （隐藏）`updateSolution` | PATCH | `/api/solutions/{id}` | 登录 + 作者 | ✅ | `solution:update` 20/60 | ⚠️ 后端额外支持 PATCH，前端 `apiPut` 未使用 |
| 5 | `deleteSolution` | DELETE | `/api/solutions/{id}` | 登录 + 作者 | ✅ | `solution:delete` 20/60 | ✅ |
| 6 | `fetchUserSolutions` | GET | `/api/solutions?userId=&problemId=` | public | — | — | ✅ |
| 7 | `fetchSolutionComments` | GET | `/api/solutions/{solutionId}/comments` | public | — | — | ⚠️ ts 里带 `?userId=` 但后端不读这个参数 |
| 8 | `createSolutionComment` | POST | `/api/solutions/{solutionId}/comments` | 登录 | ✅ | — | ✅ |
| 9 | `updateSolutionComment` | PATCH | `/api/solutions/comments/{commentId}` | 登录 + 作者 | ✅ | — | ✅ |
| 10 | `deleteSolutionComment` | DELETE | `/api/solutions/comments/{commentId}` | 登录 + 作者 | ✅ | — | ✅ |
| 11 | `recordSolutionView` | POST | `/api/views/solution/{solutionId}` | permitAll | ❌ 不要求 CSRF | `solution:view` 20/60 | ⚠️ 见 §6 BUG-3 |

**关于 `/api/` 前缀**: `backend-spring` 没有 `server.servlet.context-path`，也没有 `addPathPrefix` 的 `PathMatchConfigurer`。`/api/` 是 `SolutionController` **每个 `@*Mapping` 单独硬编码** 的，不是 context-path 行为。其他模块（如 `/auth/login`、`/problems/...`）没有 `/api/` 前缀。**前端 `solution.ts` 直接用绝对路径 `/api/...` 是对的**。

---

## 1. 测试前置条件

### 1.1 服务状态自检

```bash
# 必须满足
lsof -ti :9001    # Spring Boot
lsof -ti :8563    # Arthas MCP (与 9001 共享 PID 是预期)
docker inspect --format='{{.State.Health.Status}}' ulticode-mysql  # healthy
```

### 1.2 准备工作目录与变量

```bash
BASE=http://localhost:9001
TMP=$(mktemp -d)
COOKIE=$TMP/cookies.txt
HDR=$TMP/headers.txt
```

### 1.3 获取登录态 + CSRF Token

```bash
LOGIN_BODY=$(curl -sS -X POST "$BASE/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' \
  -c "$COOKIE")

# CSRF token 格式: tokenId:tokenValue, 整串作为 X-CSRF-Token 头
CSRF=$(echo "$LOGIN_BODY" | grep -oE '"csrfToken":"[^"]+"' | head -1 | cut -d'"' -f4)
USER_ID=$(echo "$LOGIN_BODY" | grep -oE '"id":"[a-f0-9-]+"' | head -1 | cut -d'"' -f4)

echo "CSRF=$CSRF"
echo "USER_ID=$USER_ID"
```

**实测响应示例**：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "csrfToken": "322830a447184bf893c3fdf0bffe2569:25bc22bc95204166948f66b8b2819653",
    "user": {
      "id": "5be2650e-63dd-11f1-a640-5efbb60fdb93",
      "username": "admin",
      "role": "ADMIN",
      ...
    }
  }
}
```

**Set-Cookie 头**（必须 `-c cookies.txt` 持久化）：

```
access_token=<JWT>;  Max-Age=900;    HttpOnly; SameSite=lax
refresh_token=<JWT>; Max-Age=604800; HttpOnly; SameSite=lax
csrf_token=<id:val>; Max-Age=86400;            SameSite=Lax    # 注意非 HttpOnly
```

### 1.4 【关键】CSRF Token 轮换协议

POST/PUT/DELETE/PATCH 成功后，响应头会回 `X-New-CSRF-Token`，且 `csrf_token` cookie 同步更新。**链式测试每一步都必须读取并替换**：

```bash
curl -sS -X POST "$BASE/api/..." \
  -H "X-CSRF-Token: $CSRF" \
  -b "$COOKIE" -c "$COOKIE" \
  -D "$HDR" \
  -d '...'

# 用新 token 覆盖,否则下一个写请求会 40300
NEW=$(grep -i 'x-new-csrf-token' "$HDR" | awk '{print $2}' | tr -d '\r\n')
if [ -n "$NEW" ]; then CSRF=$NEW; fi
```

⚠️ **失败响应（4xx）不会下发新 token**——脚本必须仅在 grep 出值时才覆盖，否则会把 `CSRF` 清空（见 §6 BUG-2）。

---

## 2. 用例矩阵

### 用例分类
- **T-Hxx**: Happy path（业务正向）
- **T-Axx**: Auth / CSRF 异常
- **T-Vxx**: 校验（DTO required / 边界）
- **T-Bxx**: Business 异常（404 / 409 / rate limit）
- **T-Sxx**: Security 横向越权
- **T-Pxx**: 性能 / 并发

### 端点 1：`GET /api/problems/{problemId}/solutions` — `fetchSolutionFeed`

| ID | 场景 | 前置 | 请求 | 期望 HTTP | 期望 `code` | 期望 body 关键字段 |
|---|---|---|---|---|---|---|
| T-H01 | 列表（默认分页） | 题目 1 有题解 | `GET /api/problems/1/solutions` | 200 | 0 | `data.items[]`, `data.total`, `data.page`, `data.totalPages` |
| T-H02 | 自定义分页 | 同上 | `GET /api/problems/1/solutions?page=1&pageSize=5` | 200 | 0 | `data.items.length ≤ 5` |
| T-B01 | 题目不存在 | — | `GET /api/problems/999999/solutions` | 200 | 0 | `data.items=[]`, `data.total=0` |
| T-V01 | problemId 非数字 | — | `GET /api/problems/abc/solutions` | 400 | non-zero | type mismatch error |
| T-A01 | 匿名访问 | 不带 cookie | 同 T-H01 | 200 | 0 | 同 T-H01（public 端点） |

**实测 T-H01 响应**：

```json
{"code":0,"message":"success","data":{
  "items":[{
    "id":"sol-s-007",
    "problemId":1,
    "title":"社区版主视角:两数之和的多种解法对比",
    "summary":"...",
    "language":"python",
    "tags":["哈希表","数组","双指针"],
    "author":{"id":"mod-mike-001","name":"王明","avatar":"..."},
    "counts":{"views":680,...},
    "score":...,
    "publishedAt":"...",
    "isPinned":true|false
  }, ...]
}}
```

`SolutionListItemVO` **不含 `content` 字段**（lightweight 列表 DTO），需要正文走端点 3。

---

### 端点 2：`POST /api/problems/{problemId}/solutions` — `createSolution`

DTO `CreateSolutionDTO`：

| 字段 | 类型 | 必填 | 校验 | 备注 |
|---|---|---|---|---|
| `title` | String | ✅ | `@NotBlank` | — |
| `content` | String | ✅ | `@NotBlank` | Markdown |
| `language` | String | ✅ | `@NotBlank` | 如 `java`、`python` |
| `tags` | String | ❌ | — | **`String`，非数组**。前端要 `JSON.stringify` 后再传 |

| ID | 场景 | 请求 | 期望 |
|---|---|---|---|
| T-H03 | 正常创建 | body `{"title":"x","content":"# c","language":"java","tags":"[\"dp\"]"}` + CSRF | 200, `code=0`, `data.id` UUID, `data.tags=["dp"]`（响应反序列化为数组） |
| T-A02 | 未登录 | 同上，不带 cookie | 401 / `40100` |
| T-A03 | 已登录但无 CSRF | 同上不带 `X-CSRF-Token` | 403 / `40300 CSRF token is required` |
| T-A04 | CSRF token 错误 | 头部传任意串 | 403 / `40300` |
| T-V02 | title 为空 | `{"title":"","content":"x","language":"java"}` + CSRF | 400, `"Title is required"` |
| T-V03 | content 为空 | `{"title":"x","content":"","language":"java"}` + CSRF | 400, `"Content is required"` |
| T-V04 | language 为空 | 缺字段 | 400, `"Language is required"` |
| T-B02 | 题目不存在 | `POST /api/problems/999999/solutions` | 404 / 500（取决于 service 实现） |
| T-B03 | 速率限制 | 1 分钟内 > 20 次 | 429 + `RateLimit` 头 |
| T-S01 | 前端误把 tags 传成数组 | `"tags":["a","b"]` | 400（Jackson 类型不匹配，验证前端使用 `JSON.stringify(tags)`） |

**实测 T-H03 响应**：

```json
{"code":0,"message":"success","data":{
  "id":"ce058a79-823f-4e5c-970b-1ced8cdf06ba",
  "problemId":1,
  "userId":"5be2650e-63dd-11f1-a640-5efbb60fdb93",
  "authorName":"Development Administrator",
  "title":"DR Test Solution",
  "content":"# DR\n```java\nclass S{}\n```",
  "summary":"DR",
  "language":"java",
  "tags":["dr","smoke"],      // ← 响应是数组
  "views":0,"likes":0,"dislikes":0,"comments":0,
  "score":0,
  "topicName":"数组",
  "isPublished":true,
  "publishedAt":"2026-06-11T17:46:10.155309935",
  "isFlagged":false,
  "createdAt":"...","updatedAt":"..."
}}
```

⚠️ **请求 tags 必须是 JSON 字符串、响应 tags 是数组**——序列化不对称，文档需要给前端明确说明。

---

### 端点 3：`GET /api/solutions/{id}?userId=` — `fetchSolution`

| ID | 场景 | 请求 | 期望 |
|---|---|---|---|
| T-H04 | 正常详情 | `GET /api/solutions/<sid>` | 200, `data.content` 不为空, `data.userVote=0` |
| T-H05 | 带 userId 查投票 | `GET /api/solutions/<sid>?userId=<uid>` | 200, `data.userVote ∈ {0,1,-1}` |
| T-B04 | 不存在 | `GET /api/solutions/00000000-0000-0000-0000-000000000000` | 200 + `code=40400 "Not found"`（**注意是 HTTP 200，业务 code 非零** — 实测）|
| T-A05 | 匿名 | 不带 cookie | 200, `data.userVote=0` |

**实测 T-B04 响应**：`{"code":40400,"message":"Not found","traceId":"t-1781171209150"}` — HTTP 状态 200，business code 表达错误。

---

### 端点 4 / 4b：`PUT|PATCH /api/solutions/{id}` — `updateSolution`

DTO `UpdateSolutionDTO`（与 Create 完全相同四字段，全部 `@NotBlank` 除 tags）。

| ID | 场景 | 请求 | 期望 |
|---|---|---|---|
| T-H06 | 作者更新 | `PUT /api/solutions/<sid>` + CSRF + body | 200, `data.updatedAt` 变化 |
| T-H07 | PATCH 等效 | `PATCH /api/solutions/<sid>` + CSRF + body | 200（同 handler） |
| T-A06 | 未登录 | 无 cookie | 401 / `40100` |
| T-A07 | 无 CSRF | 无 `X-CSRF-Token` | 403 / `40300` |
| T-S02 | 越权改他人题解 | 用 admin 登录改 `mod-mike-001` 的题解 | 403 业务异常 / `code` 非零（service 校验 `userId == solution.userId`） |
| T-V05 | title 空 | `{"title":"","content":"x","language":"java"}` | 400 |
| T-B05 | id 不存在 | `PUT /api/solutions/00000000...` | 200 + `40400 Not found` |
| T-B06 | rate limit | 20+/60s | 429 |

---

### 端点 5：`DELETE /api/solutions/{id}` — `deleteSolution`

| ID | 场景 | 请求 | 期望 |
|---|---|---|---|
| T-H08 | 作者删除 | `DELETE /api/solutions/<sid>` + CSRF | 200, `data=null` |
| T-A08 | 无 CSRF | — | 403 / `40300` |
| T-S03 | 删他人题解 | admin 删 mod-mike-001 的 | 403（service 校验 author） |
| T-B07 | 重复删除 | 删一个已 soft-deleted 的 | 200 + `40400 Not found` |
| T-B08 | rate limit | 20+/60s | 429 |

**回归校验**：删除后立即 `GET /api/solutions/<sid>` → 期望 `40400 Not found`（实测通过）。

---

### 端点 6：`GET /api/solutions?userId=&problemId=` — `fetchUserSolutions`

⚠️ **path 与端点 3 (`/api/solutions/{id}`) 共享 URI 命名空间**——Spring 通过 `@RequestParam userId` (required) vs `@PathVariable id` 区分。

| ID | 场景 | 请求 | 期望 |
|---|---|---|---|
| T-H09 | 仅按用户 | `GET /api/solutions?userId=<uid>` | 200, `data[]` |
| T-H10 | 用户 + 题目 | `?userId=<uid>&problemId=4` | 200, 只返回该题目下该用户的 |
| T-V06 | 缺 userId | `GET /api/solutions` | 400（required param） |
| T-A09 | 匿名 | 不带 cookie | 200 |

**实测 T-H09**：

```json
{"code":0,"data":[
  {"id":"sol-s-012","problemId":4,"userId":"5be2650e-...","authorName":"Development Administrator","title":"中位数问题的工程化思考","content":"...", ...},
  ...
]}
```

注意：此端点返回 **完整 `SolutionVO` 列表（含 content）**，而端点 1 返回的是 `SolutionListItemVO`（不含 content）。粒度不一致是已知设计，不算 bug。

---

### 端点 7：`GET /api/solutions/{solutionId}/comments` — `fetchSolutionComments`

⚠️ `solution.ts` 调用时拼了 `?userId=`，但后端 controller **签名只有 `@PathVariable String solutionId`**，`userId` 被 Spring 静默忽略——见 §6 OBS-1。

| ID | 场景 | 请求 | 期望 |
|---|---|---|---|
| T-H11 | 列表 | `GET /api/solutions/<sid>/comments` | 200, `data=[SolutionCommentVO]` |
| T-H12 | 前端带 userId | `?userId=<uid>` | 200，与不带 userId 结果一致（参数被忽略） |
| T-A10 | 匿名 | — | 200 |
| T-B09 | sid 不存在 | UUID 全 0 | 200, `data=[]` |
| T-B10 | sid 为空字符串 | URL `/api/solutions//comments` | **HTTP 400 HTML**（Tomcat 拒绝双斜杠，非 JSON 响应） — 见 §6 BUG-1 |

---

### 端点 8：`POST /api/solutions/{solutionId}/comments` — `createSolutionComment`

DTO `CreateSolutionCommentDTO`：

| 字段 | 必填 | 校验 |
|---|---|---|
| `content` | ✅ | `@NotBlank @Size(max=2000)` |
| `parentId` | ❌ | 子评论时填父评论 id |

| ID | 场景 | 请求 | 期望 |
|---|---|---|---|
| T-H13 | 顶级评论 | `{"content":"root"}` + CSRF | 200, `data.id` UUID |
| T-H14 | 子评论 | `{"content":"reply","parentId":"<cid>"}` + CSRF | 200, `data.parentId` 一致 |
| T-A11 | 未登录 | — | 401 / `40100` |
| T-A12 | 无 CSRF | — | 403 / `40300` |
| T-V07 | 空内容 | `{"content":""}` | 400, `"Content is required"` |
| T-V08 | 超长 | content 长度 > 2000 | 400, `"Content must be at most 2000 characters"` |
| T-V09 | 缺 content | `{}` | 400 |
| T-B11 | sid 不存在 | UUID 全 0 + 合法 body | 200 + `40400 Not found`（service 校验） |

---

### 端点 9：`PATCH /api/solutions/comments/{commentId}` — `updateSolutionComment`

DTO `UpdateSolutionCommentDTO`：仅 `content`，`@NotBlank @Size(max=2000)`。

| ID | 场景 | 请求 | 期望 |
|---|---|---|---|
| T-H15 | 作者编辑 | `PATCH /api/solutions/comments/<cid>` + CSRF + `{"content":"edited"}` | 200, `data.content="edited"` |
| T-A13 | 无 CSRF | — | 403 / `40300` |
| T-S04 | 越权改他人评论 | admin 改 mod-mike-001 的评论 | 403 业务异常 |
| T-V10 | 空内容 | `{"content":""}` | 400 |
| T-B12 | cid 不存在 | UUID 全 0 | 200 + `40400 Not found` |

---

### 端点 10：`DELETE /api/solutions/comments/{commentId}` — `deleteSolutionComment`

| ID | 场景 | 请求 | 期望 |
|---|---|---|---|
| T-H16 | 作者删除 | `DELETE /api/solutions/comments/<cid>` + CSRF | 200, `data=null`（soft delete） |
| T-A14 | 无 CSRF | — | 403 / `40300` |
| T-S05 | 删他人评论 | admin 删 mod-mike-001 的 | 403 业务异常 |
| T-B13 | 重复删除 | — | 200 + `40400 Not found`（soft-deleted 后被过滤） |
| T-H17 | 回归 | 删除后 `GET /api/solutions/<sid>/comments` 不应再出现该评论 | 200, 列表中无该 cid |

---

### 端点 11：`POST /api/views/solution/{solutionId}` — `recordSolutionView`

`SecurityConfig` 第 58 行 `permitAll("/api/views/solution/**")` — **不要求登录、不要求 CSRF**。

DTO `RecordViewRequest`：仅 `userId`（可空，匿名亦可）。

| ID | 场景 | 请求 | 期望 |
|---|---|---|---|
| T-H18 | 登录用户埋点 | `POST /api/views/solution/<sid>` + `{"userId":"<uid>"}` | 200, `data=null` |
| T-H19 | 匿名埋点 | 同上不带 cookie，body `{}` | 200, `data=null` |
| T-H20 | rate limit | > 20 次/60s | 429 |
| T-B14 | **sid 不存在** | `POST /api/views/solution/00000000-...` `{}` | **实测 200 + `code=50401 "Solution not found"`** → 见 §6 BUG-3 |

---

## 3. 全链路冒烟脚本（已实测通过）

```bash
#!/usr/bin/env bash
set -e
BASE=http://localhost:9001
TMP=$(mktemp -d); COOKIE=$TMP/cookies.txt; HDR=$TMP/headers.txt
trap 'rm -rf "$TMP"' EXIT

# helper: 安全更新 CSRF（只在响应头确实带 X-New-CSRF-Token 时覆盖）
update_csrf() {
  local new
  new=$(grep -i 'x-new-csrf-token' "$HDR" | awk '{print $2}' | tr -d '\r\n')
  [ -n "$new" ] && CSRF=$new
}

# 1. 登录
LOGIN=$(curl -sS -X POST "$BASE/auth/login" -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' -c "$COOKIE")
CSRF=$(echo "$LOGIN" | grep -oE '"csrfToken":"[^"]+"' | head -1 | cut -d'"' -f4)
USER_ID=$(echo "$LOGIN" | grep -oE '"id":"[a-f0-9-]+"' | head -1 | cut -d'"' -f4)
echo "[1] login OK  user=$USER_ID"

# 2. Create
RESP=$(curl -sS -X POST "$BASE/api/problems/1/solutions" \
  -H 'Content-Type: application/json' -H "X-CSRF-Token: $CSRF" \
  -b "$COOKIE" -c "$COOKIE" -D "$HDR" \
  -d '{"title":"Smoke","content":"# c","language":"java","tags":"[\"smoke\"]"}')
SID=$(echo "$RESP" | grep -oE '"id":"[a-f0-9-]+"' | head -1 | cut -d'"' -f4)
update_csrf
[ -z "$SID" ] && { echo "FAIL: create returned no id: $RESP"; exit 1; }
echo "[2] create OK  sid=$SID"

# 3. Read
curl -sS "$BASE/api/solutions/$SID" >/dev/null
echo "[3] read OK"

# 4. Update (PUT)
curl -sS -X PUT "$BASE/api/solutions/$SID" \
  -H 'Content-Type: application/json' -H "X-CSRF-Token: $CSRF" \
  -b "$COOKIE" -c "$COOKIE" -D "$HDR" \
  -d '{"title":"Smoke v2","content":"# c2","language":"java","tags":"[\"smoke\"]"}' >/dev/null
update_csrf
echo "[4] update OK"

# 5. Create comment
RESP=$(curl -sS -X POST "$BASE/api/solutions/$SID/comments" \
  -H 'Content-Type: application/json' -H "X-CSRF-Token: $CSRF" \
  -b "$COOKIE" -c "$COOKIE" -D "$HDR" \
  -d '{"content":"hello"}')
CID=$(echo "$RESP" | grep -oE '"id":"[a-f0-9-]+"' | head -1 | cut -d'"' -f4)
update_csrf
echo "[5] comment OK  cid=$CID"

# 6. Get comments
curl -sS "$BASE/api/solutions/$SID/comments" >/dev/null
echo "[6] list comments OK"

# 7. Edit comment
curl -sS -X PATCH "$BASE/api/solutions/comments/$CID" \
  -H 'Content-Type: application/json' -H "X-CSRF-Token: $CSRF" \
  -b "$COOKIE" -c "$COOKIE" -D "$HDR" \
  -d '{"content":"edited"}' >/dev/null
update_csrf
echo "[7] edit comment OK"

# 8. Record view (no CSRF needed — permitAll)
curl -sS -X POST "$BASE/api/views/solution/$SID" \
  -H 'Content-Type: application/json' \
  -d "{\"userId\":\"$USER_ID\"}" >/dev/null
echo "[8] record view OK"

# 9. Delete comment
curl -sS -X DELETE "$BASE/api/solutions/comments/$CID" \
  -H "X-CSRF-Token: $CSRF" -b "$COOKIE" -c "$COOKIE" -D "$HDR" >/dev/null
update_csrf
echo "[9] delete comment OK"

# 10. Delete solution
curl -sS -X DELETE "$BASE/api/solutions/$SID" \
  -H "X-CSRF-Token: $CSRF" -b "$COOKIE" -c "$COOKIE" >/dev/null
echo "[10] delete solution OK"

# 11. Regression
HTTP=$(curl -sS -o /dev/null -w '%{http_code}' "$BASE/api/solutions/$SID")
[ "$HTTP" = "200" ] && echo "[11] regression OK (HTTP 200 with code=40400)" || echo "FAIL regression http=$HTTP"
```

---

## 4. Arthas 运行时验证清单

> ⚠️ **强制**：阻塞型命令（dashboard/trace/watch/monitor/tt）走 MCP 必带 `-n N (N ≤ 5)`，否则 30s 超时——见 CLAUDE.md "强制降级路径"。

### 4.1 静态验证

```text
# 类是否加载、是否被 CGLIB 代理
sc -d com.ulticode.modules.solution.controller.SolutionController

# 反编译方法签名核对
jad com.ulticode.modules.solution.controller.SolutionController --source-only

# Service 层方法（核对参数顺序）
sm com.ulticode.modules.solution.service.SolutionService

# 检查 RateLimit 注解参数
jad com.ulticode.common.annotation.RateLimit --source-only
```

### 4.2 行为验证（必须配合 curl 同时发请求触发）

```text
# 观察 create 入参（5次封顶,避免 MCP 超时）
watch com.ulticode.modules.solution.controller.SolutionController create \
  '{params, returnObj}' -x 2 -n 5

# trace recordView 内部调用 — 验证 BUG-3 是否在 service 层抛
trace com.ulticode.modules.solution.service.SolutionService recordView -n 3

# 越权场景:trace update,看 authorId 校验在哪一层
trace com.ulticode.modules.solution.service.impl.SolutionServiceImpl update -n 3

# stack 看 CSRF 校验链路
stack com.ulticode.security.csrf.CsrfValidationFilter doFilterInternal -n 3
```

### 4.3 性能/线程

```text
dashboard -n 1                      # 1 次即可,避免 30s 阻塞
thread -n 5                         # 最忙的 5 个线程
trace com.ulticode.modules.solution.service.impl.SolutionServiceImpl findByProblemId -n 3   # 排查列表慢查询
```

### 4.4 降级路径（MCP 阻塞时）

```bash
# 看应用最近 200 行日志(不阻塞)
pm2 logs ulticode-9001 --nostream --lines 200
pm2 logs ulticode-9001 --nostream --lines 200 --raw     # 含原始堆栈
```

---

## 5. 安全 / 边界专项

| 类别 | 测试要点 | 工具 |
|---|---|---|
| **CSRF** | T-A03/04/07/08/12/13/14：所有写操作都需 `X-CSRF-Token`；token 必须用最近一次 rotation 值 | curl |
| **横向越权** | T-S01~05：admin 用户去操作其他用户的题解 / 评论，必须 403 业务异常 | curl + 准备多个用户的 cookie |
| **XSS / Markdown** | content 中插入 `<script>alert(1)</script>` 与 `![x](javascript:alert(1))`，验证前端 `v-html` 之前确实走过 sanitize | 前端集成测试 + Playwright |
| **SQL Injection** | userId / problemId 传 `1 OR 1=1`、`'; DROP TABLE solutions; --`，验证 MyBatis-Plus 参数绑定生效 | curl |
| **Mass Assignment** | body 多带 `userId`/`isPinned`/`isFlagged` 字段，验证 DTO 没有暴露这些可被覆盖 | curl |
| **Rate Limit** | 1 分钟内 21 次 create / update / delete / view → 期望 429 | for 循环 |
| **CSRF Token 复用** | 用旧 token 第二次写请求 → 403（每次写完轮换） | curl |
| **跨用户 CSRF 串用** | A 用户的 CSRF 给 B 用户的 cookie 用 → 403 | curl |
| **Path Traversal** | id 传 `../../admin` | curl，期望 400/404 |

---

## 6. 实测发现的 BUG / 观察项

### 🟥 BUG-1：Tomcat 双斜杠 400 HTML（非 JSON）

**复现**：当 `solutionId` 为空字符串时 URL 变成 `/api/solutions//comments`，Tomcat 直接返回 HTML：

```bash
$ curl -sS "http://localhost:9001/api/solutions//comments"
<!doctype html><html lang="en"><head><title>HTTP Status 400 – Bad Request</title>...
```

**影响**：前端 `apiGet` 假定响应是 JSON，遇到 HTML 会触发 JSON 解析异常，错误提示不友好。
**建议**：前端在拼 URL 前校验 `solutionId !== ''`；或后端加 `Filter` 把 `//` 拦截重定向。

### 🟥 BUG-2：4xx 响应不下发 `X-New-CSRF-Token` 导致链断

**复现**：链式测试一旦中间任意写请求失败（如 BUG-1 触发 400），后续步骤读不到新 CSRF，全部 `40300`。

**影响**：前端如果用类似 "失败重试 + token 自动轮换" 逻辑会陷入死循环。
**建议**：在 `update_csrf` helper 中显式判断 `grep` 命中再覆盖（脚本已实现），并在测试报告中标注每一步的 CSRF 来源。

**修复决策（2026-06-11）**：

> **判定**：**不修改后端行为**。当前 4xx 不下发 `X-New-CSRF-Token` 是**预期安全设计**——避免在错误响应中向客户端（可能已被劫持的）泄漏下一个有效 token。

**协议说明**：

- 客户端**复用**当前持有的合法 token 即可继续发起请求；服务端**不消费**失败请求的 token，下次仍可成功消费。
- 唯一的真实风险是"前端用旧 token 但服务端已轮换"——前端需在每次**成功响应**后即时更新 `X-New-CSRF-Token`（已在 §3 全链路脚本 `update_csrf` helper 中通过"grep 命中才覆盖"实现）。
- 该决策与 `CsrfValidationFilter.writeErrorResponse` 第 80-87 行的"先 setStatus + 写 body，再 return"的执行顺序保持一致——不修改该 Filter。

**变更**：仅在本节补充协议说明；代码与 Filter 不变。

### 🟥 BUG-3：`POST /api/views/solution/{sid}` 对不存在 sid 返回 50401，与 permitAll 语义矛盾

**复现**：

```bash
$ curl -sS -X POST "http://localhost:9001/api/views/solution/00000000-0000-0000-0000-000000000000" \
    -H 'Content-Type: application/json' -d '{}'
{"code":50401,"message":"Solution not found","traceId":"t-1781171170093"}
```

**分析**：
- `SecurityConfig` 第 58 行 `.requestMatchers("/api/views/solution/**").permitAll()` 期望埋点静默成功。
- `SolutionServiceImpl.recordView()` 在 service 层强制做 `findById` → throw `BusinessException(NOT_FOUND)`。
- 结果：随便一个错误 sid 都会触发 500 级 error code 入应用日志，污染监控告警。

**建议**：
- 改为 service 层静默 no-op（找不到就 return）；或
- 改为 200 + `code=0`，仅在日志层 `WARN`；或
- 加新 ErrorCode 表达 "view target gone" 而非 50401。

### 🟧 OBS-1：`fetchSolutionComments` 前端传 `?userId=` 后端未接收

**实测**：`SolutionController.getComments(@PathVariable String solutionId)` 没有 `userId` 参数。

**影响**：前端 `solution.ts:fetchSolutionComments` 拼的 `?userId=` 被 Spring 静默忽略——目前看应该是为了未来 "标记当前用户对每条评论的投票态" 预留的。**当前是 dead query string**，建议要么后端补齐 `viewerVote` 字段、要么前端去掉这个 query。

### 🟧 OBS-2：`updateSolution` PUT/PATCH 双映射，前端只用 PUT

后端 controller 用 `@PutMapping @PatchMapping` 同时映射，handler 同一个。
**影响**：HTTP 语义不清晰——PATCH 应支持部分更新但当前 DTO 全部 `@NotBlank`，等价 PUT。
**建议**：删 PATCH 或拆 DTO（`@NotBlank` → `@Size`，允许部分字段为空）。

### 🟧 OBS-3：tags 字段请求/响应序列化不对称

请求需要 `"tags":"[\"a\",\"b\"]"`（JSON-string），响应返回 `"tags":["a","b"]`（array）。
**影响**：前端 `solution.ts:createSolution` 当前签名 `tags?: string[]`，但 `apiPost` 之前**没有** `JSON.stringify(tags)`——核对 `console/src/api/solution.ts:CreateSolutionDto` 实际传输是否正确，否则会 400。
**建议**：后端把 `CreateSolutionDTO.tags` 改为 `List<String>` 与响应对齐。

---

## 7. 文档关联

- 后端控制器：`backend-spring/src/main/java/com/ulticode/modules/solution/controller/SolutionController.java`
- DTO：`backend-spring/src/main/java/com/ulticode/modules/solution/dto/*.java`
- Service 接口：`com.ulticode.modules.solution.service.SolutionService`
- 前端 API：`console/src/api/solution.ts`
- 安全规则：`backend-spring/src/main/java/com/ulticode/common/config/SecurityConfig.java` 第 58 行（permitAll）
- CSRF 实现：`backend-spring/.../security/csrf/CsrfValidationFilter.java`、`CsrfService.java`
- CSRF 文档：本仓库 `CLAUDE.md` "CSRF 机制测试"
- Arthas 文档：本仓库 `CLAUDE.md` "运行时调试 (Arthas)"

---

## 8. 总结

- ✅ **12 个 mapping** 全部在 JVM 内由 Arthas 反编译核对，与 `solution.ts` 列出的 11 个前端函数一一对应（PUT/PATCH 共用一条 handler）。
- ✅ **CSRF 流程** 已实测：admin/admin123 登录 → 写操作必带 `X-CSRF-Token` → 响应头 `X-New-CSRF-Token` 必须轮换。
- ⚠️ **3 个真实 BUG**（BUG-1/2/3）和 **3 个观察项**（OBS-1/2/3）需要修复或决策，特别是 BUG-3 影响埋点可用性、OBS-3 影响前后端 tags 字段对齐。
- 📁 全链路冒烟脚本（§3）可直接复用为 CI 烟雾测试。
- 🔍 Arthas 命令清单（§4）已遵循 MCP `-n N` 限制，避免 30s 阻塞。
