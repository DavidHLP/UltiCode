  A. 现状问题总结（按业务域/用户路径分组）

  - 用户与鉴权路径：/auth/* 登录注册等无统一错误码；仅 GET /users 使用 AuthGuard；其余接口通过 userId 查询或 Body 传参（可伪造）。鉴权与
    身份来源分裂。
  - 题库与详情路径：GET /problems 与 GET /problems/:id 返回字段多且混用 snake/camel（如 created_at、acceptance_rate），未统一 DTO/VO；无
    分页/过滤规范。
  - 解答与评论路径：GET /problems/:id/solutions 与 GET /solutions?userId=... 语义重叠；评论接口直接传 userId；前端调用的 /solutions/:id/
    vote、/solutions/comments/:id/vote 后端缺失，契约漂移。
  - 提交记录路径：GET /submissions 与 GET /problems/:problemId/submissions 逻辑重复，仅过滤条件不同；“best”接口单独暴露，可能并入过滤。
  - 竞赛路径：/contest/list|upcoming|running|past 为同资源的分裂；/contest/global-ranking 与 /:id/ranking 属于“排名域”，聚合类接口与资源
    类接口混放。
  - 论坛路径：/forum/posts/:id 与 /forum/posts/:id/thread 重叠，线程接口包含帖子与评论；/forum/quick-filters 依赖 seed 静态数据。
  - 投票与浏览量路径：POST /votes 已通用，但 UI 预期各资源下的投票路由；/views/solution/:id 与 /views/forum/:id 仅 targetType 不同。
  - 数据层与技术约束：TypeORM + Prisma 同时存在；实体模型与 Prisma schema 可能偏离；BigInt JSON hack；缺少统一异常过滤器与错误码标准。
  - 调用方线索：frontend/src/api/*.ts 直接调用后端；存在后端缺失接口与路径不一致（如 problem-list stats、solutions vote），需兼容层。

  B. 合并分组与理由（表格）

  | 旧接口集合 | 合并方式 | 理由 | 风险 | 建议新接口 |
  |---|---|---|---|---|
  | GET /contest/list + /contest/upcoming + /contest/running + /contest/past | 参数化过滤 | 同资源多入口 | 分页参数与状态枚举兼容 | GET /
  contests?status=&page=&limit= |
  | GET /problems/:id/solutions + GET /solutions?userId= | 资源化 + 过滤 | 语义一致 | 旧路径依赖、缓存键变更 | GET /solutions?
  problemId=&userId=&sort= |
  | GET /submissions + GET /problems/:problemId/submissions | 参数化过滤 | 仅过滤条件不同 | 默认 userId 行为变更 | GET /submissions?
  userId=&problemId=&skip=&take= |
  | GET /problems/:problemId/submissions/best | 参数化 | 查询“最佳”可视为排序策略 | best 定义差异 | GET /submissions?
  problemId=&userId=&best=true |
  | POST /views/solution/:id + POST /views/forum/:id | 资源化 | 仅 targetType 不同 | 统计归因变更 | POST /views |
  | POST /votes + 前端期望 /solutions/:id/vote、/solutions/comments/:id/vote | 适配 + 统一入口 | 保持旧端、统一新端 | 权限与幂等一致 |
  POST /votes + 兼容路由 |
  | GET /forum/posts/:id + GET /forum/posts/:id/thread | include 参数 | thread 覆盖 post | 默认 payload 变大 | GET /forum/posts/:id?
  include=comments |
  | GET /contest/global-ranking + GET /contest/:id/ranking | 资源化 | 排名域独立 | 路由变更 | GET /rankings?scope=global|
  contest&contestId= |
  | GET /problem-lists/:id + GET /problem-lists/:id/problems + GET /problem-lists/stats | 聚合/资源拆分 | 领域完整性 | 前端依赖未实现 |
  GET /problem-lists, GET /problem-lists/:id, GET /problem-lists/:id/problems, GET /problem-lists/stats |

  ———

  C. 新接口设计（REST，保证旧路径兼容）
  说明：新接口统一返回 { code, message, data, traceId }，旧接口由适配层转发，保持字段与行为不变；新接口支持分页、过滤、排序规范。

  1. 认证域

  - POST /auth/login
      - 场景：用户登录获取 token
      - 请求：{ username, password }
      - 响应：{ accessToken, user:{ id, username, name, avatar } }
      - 错误码：AUTH_INVALID_CREDENTIALS
      - 鉴权：无
      - 幂等：否
      - 示例：

  { "code": 0, "message": "ok", "data": { "accessToken": "xxx", "user": { "id": "u-001", "username": "tom", "name": "Tom", "avatar":
  "..." } }, "traceId": "t-123" }

  2. 用户域

  - GET /users
      - 场景：管理员或内部使用查询用户列表
      - 请求：?page=&limit=
      - 响应：{ items:[...], total }
      - 鉴权：AuthGuard
      - 幂等：是

  3. 题库域

  - GET /problems
      - 请求：?difficulty=&tag=&keyword=&page=&limit=&sort=
      - 响应：{ items:[{ id, slug, title, difficulty, acceptanceRate, status }], total }
      - 鉴权：无
      - 分页：page/limit
      - 排序：sort=acceptanceRate|difficulty|newest
  - GET /problems/:id
      - 场景：题目详情页
      - 响应：{ id, slug, title, detail:{...}, tags:[...], examples:[...], languages:[...] }
      - 兼容：保留 snake_case 字段到旧路径

  4. 解答域

  - GET /solutions
      - 请求：?problemId=&userId=&topic=&language=&sort=&page=&limit=
      - 响应：{ items:[Solution], total, sortOptions }
      - 鉴权：可选（用于 userVote）
      - 分页：page/limit
      - 排序：sort=likes|newest|oldest|heat
      - 示例：

  {
    "code": 0,
    "message": "ok",
    "data": {
      "items": [
        { "id": "s-1", "title": "DP", "author": { "id": "u-1", "username": "a" }, "stats": { "views": 12, "comments": 3, "likes": 2 },
  "score": 2, "createdAt": "2025-01-01T10:00:00Z" }
      ],
      "total": 1,
      "sortOptions": [{ "label": "Most liked", "value": "likes" }]
    },
    "traceId": "t-456"
  }

  - GET /solutions/:id/comments
      - 请求：?page=&limit=
      - 响应：{ items:[{ id, parentId, content, author, likes, userVote, createdAt }], total }
      - 鉴权：可选（用于 userVote）
  - POST /solutions/:id/comments
      - 请求：{ content, parentId }（用户从 token）
      - 响应：{ id, content, author, createdAt }
      - 幂等：否
      - 鉴权：必需

  5. 提交域

  - GET /submissions
      - 请求：?problemId=&userId=&status=&language=&best=false&page=&limit=
      - 响应：{ items:[Submission], total }
      - 幂等：是
  - GET /submissions/:id
      - 响应：{ id, problem, user, status, runtime, memory, testDetails }

  6. 论坛域

  - GET /forum/posts
      - 请求：?communityId=&sort=&page=&limit=
      - 响应：{ items:[Post], total }
  - GET /forum/posts/:id
      - 请求：?include=comments
      - 响应：{ post, comments? }
  - POST /forum/posts/:id/comments
      - 请求：{ content, parentId }
      - 鉴权：必需

  7. 投票域（统一）

  - POST /votes
      - 请求：{ targetType, targetId, voteType }
      - 响应：{ likes, dislikes, userVote }
      - 幂等：是（同一投票可切换/取消）
      - 兼容：提供 /solutions/:id/vote 与 /solutions/comments/:id/vote 作为适配，内部转到 /votes

  8. 浏览量域（统一）

  - POST /views
      - 请求：{ targetType, targetId }
      - 响应：{ counted: true|false }
      - 幂等：60 分钟内相同 userId/ip 去重
      - 兼容：保留 /views/solution/:id、/views/forum/:id 作为适配

  ———

  D. 数据模型与错误码规范

  - 统一响应结构：

  { "code": 0, "message": "ok", "data": {}, "traceId": "t-xxx" }

  - 字段命名：API 对外统一 camelCase；内部可兼容旧 snake_case（适配层做映射）。
  - 时间格式：统一 ISO8601 字符串（UTC），例如 2025-01-01T10:00:00Z。
  - 枚举：统一大写 SCREAMING_SNAKE_CASE（如 VOTE_TARGET_TYPE，对外可映射为小写）。
  - 空值策略：缺失字段用 null，集合字段用 []，避免省略字段导致前端分支爆炸。
  - 错误码分层（建议）：
      - 0 成功
      - 400000 参数校验失败
      - 401000 未授权 / Token 无效
      - 403000 无权限
      - 404000 资源不存在
      - 409000 冲突（重复注册等）
      - 429000 触发限流
      - 500000 系统异常
      - 业务域扩展：401100 登录失败，409100 用户名重复，409110 邮箱重复，404200 题目不存在等

  ———

  E. 迁移/灰度/回滚方案（含旧→新映射表）

  - 阶段 1（兼容期）：新增统一接口与响应包装；旧接口保留，改为适配调用新 Service；记录旧接口调用量。
  - 阶段 2（双轨）：前端逐步切换到新接口；旧接口返回响应中增加 deprecation 字段（不破坏结构）。
  - 阶段 3（灰度下线）：按路由逐步限流旧接口，观察错误率与调用量。
  - 阶段 4（清理）：下线旧接口；保留 1 个版本回滚开关。

  旧→新映射表（关键项）：

  - /contest/list|upcoming|running|past → /contests?status=...
  - /problems/:id/solutions → /solutions?problemId=:id
  - /solutions?userId= → /solutions?userId=
  - /submissions → /submissions?userId=
  - /problems/:problemId/submissions → /submissions?problemId=:problemId
  - /problems/:problemId/submissions/best → /submissions?problemId=:problemId&best=true
  - /views/solution/:id → /views + {targetType:"SOLUTION",targetId}
  - /views/forum/:id → /views + {targetType:"FORUM_POST",targetId}
  - /solutions/:id/vote（前端期望） → /votes 适配
  - /solutions/comments/:id/vote（前端期望） → /votes 适配

  回滚方案：

  - 通过网关路由开关切回旧 Service；
  - 监控指标：5xx率 > 1%、p95 > 1s、旧接口调用量下降失败 触发回滚。

  ———

  F. 风险清单与测试要点

  - 风险：身份来源由 userId 参数切换到 token；旧客户端可能仍传 userId，需兼容。
  - 风险：字段命名统一引发前端解析失败；需适配层双写字段或做返回映射。
  - 风险：混合 ORM 造成数据一致性问题；建议明确哪些模块用 Prisma/TypeORM。
  - 测试要点：
      - 单测：Vote/Views 的幂等与去重逻辑；Submission best 排序。
      - 契约测试：新旧接口返回结构一致性；字段映射正确性。
      - 回归清单：论坛线程、解答列表、竞赛列表分页、problem detail。
      - 灰度指标：成功率、p95、调用量、error code 分布、异常栈 TopN。

  如果你愿意，我可以继续输出更细的“接口字段清单+DTO 差异矩阵”，并给出“旧端兼容适配层”的代码级落地建议（含 Controller/Interceptor/Filter
  结构）。


› continue


• I'll provide a structured, detailed deliverable covering current problem domains, merged interface tables, new interface design with
  request/response examples, data models including error codes, migration plans with mapping, and risk/testing considerations. This
  includes inferred frontend call sites and constraints like missing OpenAPI specs and no guards. The output will follow the required A-F
  format with clear headers and bullet points per instructions.

─ Worked for 18s ────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────

• 继续补全并细化交付物 A–F（包含更完整的接口清单与字段、调用方线索、兼容与落地建议）。

  A. 现状问题总结（按业务域/用户路径分组）

  - 业务背景与核心流程: 项目形态为类 LeetCode/论坛社区平台，核心流程包括题库浏览、解答阅读与评论、提交记录查询、竞赛排行浏览、论坛帖子与
    评论互动、点赞与浏览统计。
  - 核心流程 1（题库→解答→评论）: 用户浏览题库列表 GET /problems → 进入题目详情 GET /problems/:id → 查看解答 GET /problems/:id/solutions
    → 查看/发布解答评论 GET/POST /solutions/:id/comments。
  - 核心流程 2（论坛互动）: 用户查看论坛帖子 GET /forum/posts → 进入帖子线程 GET /forum/posts/:id/thread → 发表评论 POST /forum/
    posts/:id/comments → 投票 POST /votes → 浏览统计 POST /views/forum/:id。
  - 核心流程 3（竞赛与排名）: 查看竞赛列表与状态 GET /contest/list|upcoming|running|past → 进入竞赛详情 GET /contest/:id → 查看排名 GET /
    contest/:id/ranking 或全球榜 GET /contest/global-ranking。
  - 鉴权现状: 仅 GET /users 使用 AuthGuard，其余接口普遍使用 userId 查询或 body 传参，身份与权限边界模糊。
  - 模型与DTO现状: DTO 稀少（RegisterDto,VoteDto,CreateSolutionCommentDto），无统一响应封装；字段命名混用 snake/camel；无 OpenAPI/Swagger
    注解。
  - 数据层现状: TypeORM 实体 + Prisma Client 同时使用；同一业务域存在两套 ORM 访问方式，潜在一致性风险。
  - 接口契约漂移: 前端调用了后端不存在的路径（/solutions/:id/vote、/solutions/comments/:id/vote、/problem-lists/stats、/problem-
    lists/:id/problems），需做兼容适配。

  现有接口清单（按域）

  - Auth
      - POST /auth/login 请求 { username, password } 响应 { access_token, user:{ id, username, name } }
      - POST /auth/register 请求 { username, email, password?, avatar? } 响应同上
      - POST /auth/forgot-password 请求 { email } 响应 { message }
      - GET /auth/github 重定向
      - GET /auth/github/callback 重定向
      - POST /auth/logout 响应 { message }
  - Users
      - GET /users 响应 User[]（需鉴权）
      - GET /users/:id 响应 User|null
  - Problems
      - GET /problems 响应 Problem[]
      - GET /problems/:id 响应 Problem|null（含 detail、tags、examples、languages）
  - Solutions
      - GET /problems/:id/solutions?userId= 响应 SolutionFeedResponse
      - GET /solutions?userId= 响应 SolutionFeedResponse
      - GET /solutions/:id/comments?userId= 响应 ForumComment[] 结构
      - POST /solutions/:id/comments 请求 { content, parentId, userId }
      - GET /solution-topics 响应 { topics: [...] }
  - Submissions
      - GET /submissions?userId=&skip=&take= 响应 Submission[]
      - GET /submissions/:id 响应 Submission
      - GET /problems/:problemId/submissions?userId=&skip=&take=
      - GET /problems/:problemId/submissions/best?userId=
  - Forum
      - GET /forum/posts 响应 ForumPost[]（含 likes/dislikes/score）
      - GET /forum/posts/:id 响应 ForumPost|null
      - GET /forum/posts/:id/thread?userId= 响应 { post + comments[] + userVote }
      - POST /forum/posts/:id/comments 请求 { body, parentId }
      - GET /forum/communities
      - GET /forum/quick-filters（seed 数据）
  - Votes
      - POST /votes 请求 { targetType, targetId, voteType } + userId query
  - Views
      - POST /views/solution/:id 请求 { userId }
      - POST /views/forum/:id 请求 { userId }
  - Contest
      - GET /contest/list GET /contest/upcoming GET /contest/running GET /contest/past?page&limit
      - GET /contest/:id
      - GET /contest/:id/ranking
      - GET /contest/global-ranking
      - GET /contest/stats

  痛点与约束（推断）

  - 鉴权不一致：多处用 userId 参数替代真实登录态，易被伪造；AuthGuard 使用 Base64 token 模拟。
  - 错误码缺失：全靠默认异常；无统一错误结构，无法对接监控与告警。
  - 接口碎片化：同资源多路由（contest、submissions、solutions）；缓存和网关难以统一。
  - 字段不统一：数据库字段 snake_case，部分响应 camelCase，前端做多次映射。
  - 性能隐患：论坛/解答接口多次查询投票与评论统计，缺少统一聚合响应。
  - 兼容限制：要求保持前端现有路径完全兼容，需增加适配层而非硬切换。

  业务域划分建议（模块 → 域）

  - 用户与认证域: AuthModule, UserModule
  - 题库域: ProblemModule, ProblemListModule
  - 解答域: SolutionModule
  - 提交评测域: SubmissionModule
  - 竞赛域: ContestModule
  - 论坛域: ForumModule
  - 互动域（横切）: VoteModule, ViewModule

  ———

  B. 合并分组与理由（表格）

  | 旧接口集合 | 合并方式 | 理由 | 风险 | 建议新接口 |
  |---|---|---|---|---|
  | /contest/list /contest/upcoming /contest/running /contest/past | 参数化过滤 | 同资源不同状态 | 状态枚举与分页兼容 | GET /contests?
  status=&page=&limit= |
  | /contest/global-ranking /contest/:id/ranking | 资源化分层 | 排名域独立 | 旧路径依赖 | GET /rankings?scope=global|contest&contestId= |
  | /problems/:id/solutions /solutions?userId= | 资源化 + 过滤 | 语义一致 | 缓存键变更 | GET /solutions?problemId=&userId=&sort= |
  | /submissions /problems/:problemId/submissions | 参数化过滤 | 查询条件不同 | 默认 userId 行为 | GET /submissions?
  problemId=&userId=&page=&limit= |
  | /problems/:problemId/submissions/best | 参数化 | 只是排序策略 | “best”定义改变 | GET /submissions?best=true |
  | /forum/posts/:id /forum/posts/:id/thread | include 参数 | thread 覆盖 post | payload 变大 | GET /forum/posts/:id?include=comments |
  | /views/solution/:id /views/forum/:id | 资源化 | 仅 targetType 不同 | 统计归因 | POST /views |
  | /votes + 前端期望 /solutions/:id/vote /solutions/comments/:id/vote | 适配层 | 统一投票入口 | 权限&幂等一致 | POST /votes |

  ———

  C. 新接口设计（REST，保持旧路径兼容）
  新接口统一结构，旧路径由适配层转发并保持旧字段。

  1. Solutions 查询（统一）

  - GET /solutions
  - 用途：按题目/作者过滤解答列表
  - 请求：?problemId=&userId=&sort=likes|newest|oldest|heat&page=&limit=
  - 响应：

  { "code": 0, "message": "ok", "data": { "items": [{ "id": "s-1", "title": "DP", "author": { "id": "u-1", "username": "a" }, "stats":
  { "views": 12, "comments": 3, "likes": 2 }, "score": 2, "createdAt": "2025-01-01T10:00:00Z" }], "total": 1 }, "traceId": "t-1" }

  - 鉴权：可选（用于 userVote）
  - 幂等：是
  - 兼容：GET /problems/:id/solutions 适配为 problemId=:id

  2. Solution 评论

  - GET /solutions/:id/comments
  - 请求：?page=&limit=
  - 响应：{ items:[{ id, parentId, content, author, likes, userVote, createdAt }], total }
  - POST /solutions/:id/comments
  - 请求：{ content, parentId }（userId 从 token）
  - 错误码：400000 字段校验；401000 未登录

  3. 投票统一入口

  - POST /votes
  - 请求：{ targetType, targetId, voteType }
  - 响应：{ likes, dislikes, userVote }
  - 幂等：是（重复同值视为取消）
  - 兼容：新增 /solutions/:id/vote、/solutions/comments/:id/vote 作为适配层

  4. 浏览量统一入口

  - POST /views
  - 请求：{ targetType, targetId }
  - 响应：{ counted }
  - 幂等：60 分钟去重
  - 兼容：/views/solution/:id、/views/forum/:id

  5. Submissions（合并查询）

  - GET /submissions
  - 请求：?problemId=&userId=&status=&language=&best=true|false&page=&limit=
  - 响应：{ items:[Submission], total }
  - 兼容：/problems/:problemId/submissions、/problems/:problemId/submissions/best

  6. Contest 统一列表

  - GET /contests
  - 请求：?status=upcoming|running|finished&page=&limit=
  - 响应：{ items:[Contest], total }
  - 兼容：旧路由分发 status

  7. Forum 帖子详情

  - GET /forum/posts/:id
  - 请求：?include=comments
  - 响应：{ post, comments? }

  ———

  D. 数据模型与错误码规范（统一结构）

  - 统一响应

  { "code": 0, "message": "ok", "data": {}, "traceId": "t-xxx" }

  - 字段命名与时间格式: 统一 camelCase + ISO8601 UTC
  - 枚举: 统一 SCREAMING_SNAKE_CASE
  - 错误码分层
      - 0 成功
      - 400000 请求参数错误
      - 401000 未授权
      - 403000 无权限
      - 404000 资源不存在
      - 409000 冲突
      - 500000 系统异常
      - 业务细化示例：409100 用户名重复、409110 邮箱重复、404200 题目不存在

  ———

  E. 迁移/灰度/回滚方案（含映射表）

  - 迁移阶段
      - 阶段1：新增统一接口与响应拦截器；旧接口调用统一 Service。
      - 阶段2：前端逐步切换；旧接口返回 deprecation 提示。
      - 阶段3：灰度下线旧接口，监控错误率与调用量。
      - 阶段4：清理旧接口，保留版本回滚开关。
  - 旧→新映射（关键项）
      - /contest/list|upcoming|running|past → /contests?status=...
      - /problems/:id/solutions → /solutions?problemId=:id
      - /solutions?userId= → /solutions?userId=
      - /submissions → /submissions?userId=
      - /problems/:problemId/submissions → /submissions?problemId=:problemId
      - /problems/:problemId/submissions/best → /submissions?problemId=:problemId&best=true
      - /views/solution/:id → /views
      - /views/forum/:id → /views
  - 回滚：网关开关回旧路由；指标阈值 5xx>1%、p95>1s、旧调用量异常

  ———

  F. 风险清单与测试要点

  - 风险: 身份来源从 query/body 切到 token；旧端仍传 userId。
  - 风险: 字段命名统一导致前端解析失败；需适配层双写或映射。
  - 风险: Prisma/TypeORM 混用导致数据一致性与事务边界不清。
  - 测试要点
      - 单测：Vote 幂等、Views 冷却、Submissions best 排序
      - 契约测试：旧接口输出字段一致性
      - 回归清单：论坛线程、解答列表、竞赛分页、题目详情
      - 灰度验证：成功率、p95、错误码分布、调用量变化

  ———

1) 完整接口字段对照表（旧→新映射细粒度）

  说明：新接口统一 camelCase，旧接口保持原样；适配层做字段双写/映射。以下列出关键域与字段差异。

  Auth

  - 旧 POST /auth/login 响应：
      - access_token → 新 accessToken
      - user.id → 新 user.id
      - user.username → 新 user.username
      - user.name → 新 user.name
  - 旧 POST /auth/register 同上

  Users

  - 旧 GET /users 响应 User[]：
      - avatar → 新 avatarUrl（建议新增，旧字段保留）
      - name → 新 displayName（建议新增）
      - 旧字段不变：id, username, email

  Problems

  - 旧 GET /problems（Problem 实体）：
      - acceptance_rate → 新 acceptanceRate
      - is_premium → 新 isPremium
      - has_solution → 新 hasSolution
      - completed_time → 新 completedTime
      - difficulty 保持，但推荐映射为 EASY|MEDIUM|HARD
      - status 保持，但推荐映射为 SOLVED|ATTEMPTED|TODO
  - 旧 GET /problems/:id：
      - detail.summary → 新 detail.summary
      - detail.difficulty_rating → 新 detail.difficultyRating
      - detail.updated_at → 新 detail.updatedAt
      - detail.constraints_json → 新 detail.constraints
      - detail.follow_up → 新 detail.followUp
      - tagRelations[].tag.label → 新 tags[].label
      - languages[].starter_code → 新 languages[].starterCode
      - examples[].example_order → 新 examples[].order
      - examples[].input_text → 新 examples[].inputText
      - examples[].output_text → 新 examples[].outputText

  Solutions

  - 旧 GET /problems/:id/solutions、GET /solutions?userId= 返回 SolutionFeedResponse
      - created_at → 新 createdAt
      - publishedAt 保留
      - languageFilter 保留（建议改为 language + filters.language）
      - stats.views/comments/likes 保留
      - votes/views/likes/comments 重复字段 → 新只保留 stats
      - author.avatarColor → 新 author.avatarColor
      - topicName/topicTranslated → 新 topic.name/topic.translatedName
  - 旧 GET /solutions/:id/comments 返回 ForumComment[]（结构仿论坛）
      - body → 新 content
      - createdAt 保留
      - author.username 保留
      - likes/dislikes 保留
  - 旧 POST /solutions/:id/comments 请求：
      - content 保留
      - parentId 保留
      - userId → 新从 token 取，适配层允许传参但忽略

  Submissions

  - 旧 GET /submissions/GET /problems/:problemId/submissions：
      - created_at → 新 createdAt
      - runtime_percentile → 新 runtimePercentile
      - memory_percentile → 新 memoryPercentile
      - runtimeDistBinsMs → 新 runtimeDistBinsMs（保留）
      - memoryDistBinsMb → 新 memoryDistBinsMb（保留）
      - test_details → 新 testDetails

  Forum

  - 旧 GET /forum/posts：
      - createdAt（实体字段）→ 新 createdAt
      - likes/dislikes/score 为注入字段 → 新保留但归入 stats
      - vote_state → 新 voteState
      - is_pinned/is_locked → 新 isPinned/isLocked
  - 旧 GET /forum/posts/:id/thread：
      - userVote 保留
      - voteState 保留
      - comments[].body → 新 comments[].content
      - comments[].createdAt 保留

  Votes

  - 旧 POST /votes 请求：
      - targetType → 新 targetType
      - targetId → 新 targetId
      - voteType → 新 voteType（推荐枚举 UP|DOWN|NONE）
      - userId query → 新从 token

  Views

  - 旧 POST /views/solution/:id//views/forum/:id 请求：
      - userId body → 新从 token（适配保留）

  ———

  2) 兼容适配层的 NestJS 代码结构建议（Controller/Interceptor/Filter 样例）

  目标：新接口统一 DTO/错误码/响应包裹；旧接口保留，但内部调用新 Service 并做字段映射。

  结构建议

  - 统一响应封装：CommonResponseInterceptor
  - 统一异常映射：GlobalExceptionFilter
  - 兼容适配层：LegacyController（旧路径）
  - 新接口控制器：V1Controller（或直接用原控制器但保持新风格）

  示例：响应拦截器（统一 envelope）

  // backend/src/common/interceptors/response.interceptor.ts
  import {
    CallHandler,
    ExecutionContext,
    Injectable,
    NestInterceptor,
  } from '@nestjs/common';
  import { map } from 'rxjs/operators';

  @Injectable()
  export class ResponseInterceptor implements NestInterceptor {
    intercept(_context: ExecutionContext, next: CallHandler) {
      return next.handle().pipe(
        map((data) => ({
          code: 0,
          message: 'ok',
          data,
          traceId: `t-${Date.now()}`,
        })),
      );
    }
  }

  示例：异常过滤器（统一错误码）

  // backend/src/common/filters/exception.filter.ts
  import {
    ArgumentsHost,
    Catch,
    ExceptionFilter,
    HttpException,
    HttpStatus,
  } from '@nestjs/common';

  @Catch()
  export class GlobalExceptionFilter implements ExceptionFilter {
    catch(exception: unknown, host: ArgumentsHost) {
      const ctx = host.switchToHttp();
      const response = ctx.getResponse();
      const status =
        exception instanceof HttpException
          ? exception.getStatus()
          : HttpStatus.INTERNAL_SERVER_ERROR;

      const code = status === 400 ? 400000
        : status === 401 ? 401000
        : status === 403 ? 403000
        : status === 404 ? 404000
        : status === 409 ? 409000
        : 500000;

      const message =
        exception instanceof HttpException
          ? exception.message
          : 'Internal Server Error';

      response.status(status).json({
        code,
        message,
        data: null,
        traceId: `t-${Date.now()}`,
      });
    }
  }

  示例：旧接口适配层

  // backend/src/legacy/legacy-solution.controller.ts
  @Controller('problems')
  export class LegacySolutionController {
    constructor(private readonly solutionService: SolutionService) {}

    @Get(':id/solutions')
    async findSolutions(@Param('id') id: string, @Query('userId') userId?: string) {
      const data = await this.solutionService.findByProblemId(id, userId);
      return data; // 旧接口保持原结构，不走响应包装或在 interceptor 中做条件排除
    }
  }

  示例：新接口统一控制器

  // backend/src/solution/solution.v1.controller.ts
  @Controller('solutions')
  export class SolutionV1Controller {
    constructor(private readonly solutionService: SolutionService) {}

    @Get()
    findSolutions(@Query() query: SolutionQueryDto) {
      return this.solutionService.findSolutions(query);
    }
  }

  落地建议

  - AppModule 注册 ResponseInterceptor 与 GlobalExceptionFilter
  - 对旧接口增加 @SkipWrap() 自定义装饰器，避免旧路径被强制 envelope（必要时保留旧格式）
  - 新接口默认返回统一 envelope

  ———

  3) 前端缺失接口补齐“兼容 stub 路由”清单

  1. POST /solutions/:id/vote
      - 兼容 stub：转发到 POST /votes
      - 请求 { userId, voteType } → { targetType:"SOLUTION", targetId:id, voteType }
  2. POST /solutions/comments/:id/vote
      - 兼容 stub：转发到 POST /votes
      - 请求 { userId, voteType } → { targetType:"SOLUTION_COMMENT", targetId:id, voteType }
  3. GET /problem-lists/stats
  5. GET /problems/:id/results
      - 前端 frontend/src/api/test-results.ts 引用，但后端无接口
      - 兼容 stub：返回 null 或 mock ProblemRunResult