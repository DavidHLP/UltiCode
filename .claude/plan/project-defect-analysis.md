# UltiCode 项目缺陷分析计划

**生成日期**: 2026-04-12
**分析范围**: backend-spring / console / management / recommendation
**总缺陷数**: 112 (5 CRITICAL / 20 HIGH / 45 MEDIUM / 42 LOW)

---

## 一、全局缺陷统计

| 模块 | CRITICAL | HIGH | MEDIUM | LOW | 合计 |
|------|----------|------|--------|-----|------|
| backend-spring | 1 | 5 | 14 | 8 | **28** |
| console | 3 | 7 | 10 | 7 | **27** |
| management | 0 | 3 | 8 | 16 | **27** |
| recommendation | 1 | 5 | 13 | 11 | **30** |
| **合计** | **5** | **20** | **45** | **42** | **112** |

---

## 二、CRITICAL 级缺陷（5 个，必须立即修复）

### C-B01: Redis 反序列化使用 LaissezFaireSubTypeValidator
- **模块**: backend-spring
- **文件**: `common/config/RedisConfig.java:38-42`
- **问题**: 允许所有 Java 类型从 Redis 反序列化，存在远程代码执行攻击向量（gadget chain）
- **修复**: 使用限制性 PolymorphicTypeValidator，仅白名单应用内的 DTO/entity 包

### C-C01: forum.ts 未定义变量 url 导致运行时崩溃
- **模块**: console
- **文件**: `api/forum.ts:100`
- **问题**: `fetchForumThread` 引用未定义的 `url` 变量，用户查看任何论坛帖子时抛出 ReferenceError
- **修复**: 将 `url` 替换为正确的字符串字面量 `` `/forum/posts/${postId}` ``

### C-C02: DOMPurify FORBID_CONTENT 配置无效，XSS 防护失效
- **模块**: console
- **文件**: `utils/sanitize.ts:100`
- **问题**: `FORBID_CONTENT` 不是有效的 DOMPurify 配置键，协议黑名单（javascript:, data: 等）完全不生效
- **修复**: 改用 `DOMPurify.addHook('uponSanitizeAttribute', ...)` 或 HOOKS 配置

### C-C03: getTokenFromCookie 读取 httpOnly Cookie（死代码）
- **模块**: console
- **文件**: `lib/socket.ts:137-148`
- **问题**: 尝试从 document.cookie 读取 httpOnly cookie，永远返回 null。contest WebSocket 发送空 Bearer token
- **修复**: 删除 `getTokenFromCookie()`，统一依赖 httpOnly cookie + SockJS withCredentials

### C-R01: 推荐策略启动时加载 Redis 数据后永不过期
- **模块**: recommendation
- **文件**: `recommend-provider/.../config/RecommendationEngineConfig.java:68-86`
- **问题**: 所有 recall 策略在 Bean 创建时一次性加载数据，之后持有过期快照。Spark 离线任务更新 Redis 后需重启才生效
- **修复**: 策略注入 store 引用，每次 recall 时加载数据（带短 TTL 缓存），或实现定时刷新机制

---

## 三、HIGH 级缺陷（20 个）

### 安全类 (7)

| ID | 模块 | 文件 | 描述 |
|----|------|------|------|
| H-B01 | backend-spring | `security/csrf/CsrfInterceptor.java:42-46` | 未认证请求绕过 CSRF 检查，但 Spring Security CSRF 已禁用 |
| H-B02 | backend-spring | `security/jwt/JwtAuthenticationFilter.java:40-88` | JWT 过滤器不检查 TokenBlacklistService，注销后的 token 仍有效 |
| H-B03 | backend-spring | `common/filter/XssFilter.java:69-72` | XSS 过滤器污染 Authorization 和 X-CSRF-Token 头 |
| H-B04 | backend-spring | `modules/auth/service/OAuth.java:247-259` | OAuth 用户无密码创建，后续密码登录触发 NPE |
| H-B05 | backend-spring | `resources/application.yml:47` | JWT Secret 空字符串默认值可能绕过启动验证 |
| H-B06 | console | `components/ui/chart/ChartSingleTooltip.vue:41-42` | innerHTML 使用未经净化的数据（XSS 向量） |
| H-B07 | recommendation | `recommend-web/.../RecommendController.java` | REST 和 Dubbo 端点完全无认证，可查询任意用户推荐 |

### 架构类 (5)

| ID | 模块 | 文件 | 描述 |
|----|------|------|------|
| H-A01 | backend-spring | `auth/util/JwtUtils.java` + `security/jwt/JwtTokenProvider.java` | 重复 JWT 基础设施，JwtUtils 默认过期时间 7 天 vs 配置 15 分钟 |
| H-A02 | console | `lib/socket.ts` + `composables/contest/useContestSocket.ts` | 重复 Socket 管理器 + 冲突的认证策略 |
| H-A03 | console | `router/index.ts` | 缺少 404 catch-all 路由 |
| H-A04 | console | `components/common/loading/ErrorBoundary.vue` + 所有视图 | ErrorBoundary 存在但从未在任何视图使用 |
| H-A05 | management | `stores/admin/moderation.ts:72-75` | 审核模块共用单一分页状态，导航后分页被污染 |

### 性能类 (6)

| ID | 模块 | 文件 | 描述 |
|----|------|------|------|
| H-P01 | console | `stores/contest.ts:316,357` | `$reset()` 不清理 timerHandles，setInterval 内存泄漏 |
| H-P02 | console | `composables/useSearch.ts:38` | debounce timer 组件卸载不清理 |
| H-P03 | console | `composables/useSWR.ts:72` | fetch 组件卸载不 abort；stale 标志永不重置 |
| H-P04 | recommendation | `recommend-provider/.../store/RedisRecommendationStore.java:100-103` | 每次请求反序列化整个用户-题目矩阵 |
| H-P05 | recommendation | `recommend-spark/.../SimilarityJob.scala:125-158` | O(n^2) cross join，MinHash 未实现 |
| H-P06 | recommendation | `recommend-feature/.../FeatureStore.java:28-80` | ConcurrentHashMap 无上限，内存无限增长 |

### 代码质量类 (2)

| ID | 模块 | 文件 | 描述 |
|----|------|------|------|
| H-Q01 | backend-spring | `user/service/impl/UserServiceImpl.java:282` | BeanUtils.copyProperties 可能泄露 password hash |
| H-Q02 | management | `views/settings/SettingsView.vue:61` | SMTP 密码以明文存储在 Vue 组件状态中（DevTools 可见） |

---

## 四、MEDIUM 级缺陷精选（45 个，按优先级排列）

### 4.1 安全 (10)

- **B-S06**: OAuth 回调 open redirect（`AuthController.java:146,160`）
- **B-S07**: 比赛注册 TOCTOU 竞态条件（`ContestServiceImpl.java:314-353`）
- **B-S09**: X-Real-IP 限速绕过风险（`RateLimitAspect.java:80-83`）
- **C-M04**: CSP `unsafe-inline` 允许样式注入（`console/nginx.conf:54`）
- **C-M02**: VITE_TEST_USERNAME/PASSWORD 可能泄露到生产包（`console/.env.example`）
- **M-S01**: 缺少路由权限守卫（account/billing）（`management/router/index.ts:305-316`）
- **M-S02**: DOMPurify 缓存无上限（`management/chart/utils.ts:7`）
- **M-S03**: 角色值字符串无枚举约束（management 多处）
- **R-03**: RecommendRequest.size 无 @Max 上限（`RecommendRequest.java:36`）
- **R-05**: SIMILAR 场景忽略 sourceProblemId（`RecommendServiceImpl.java:112-128`）

### 4.2 N+1 查询 (3)

- **B-Q02**: ModerationServiceImpl.toQueueVO() 每条记录 3 次用户查询
- **B-Q03**: ContestServiceImpl.toVO() 每条比赛单独查参与状态
- **B-Q04**: SubmissionServiceImpl.toVO() 每条提交单独查用户和题目

### 4.3 性能 (7)

- **C-M07**: DescriptionMarkdown 双重 DOMPurify 净化
- **B-Q05**: Email 模板注入风险（简单字符串替换无转义）
- **R-06**: Dubbo 缓存键忽略 targetTags/includeSolved 等参数
- **R-10**: Spark 任务 write 后冗余 .count()
- **R-20**: 离线评估将全量数据加载到 JVM 堆
- **M-P01**: highlight.js 全量打包（190+ 语言）
- **M-P02**: useDataTable composable 初始化时可能触发重复请求

### 4.4 架构/代码质量 (15)

- **B-A02**: Cookie 设置代码重复（AuthServiceImpl + OAuthService）
- **B-A03**: 服务层手动检查角色而非使用 @PreAuthorize
- **B-D01**: 多个 @RequestBody 缺少 @Valid 注解
- **B-D03**: 密码重置未校验新密码强度
- **B-C01**: JWT Cookie Secure 默认 false
- **B-C02**: 默认数据库凭据 ulticode/ulticode
- **B-D02**: OAuth 异常使用 RuntimeException 而非 BusinessException
- **C-M06**: window.location.href 代替 Vue Router
- **C-M08**: 多处空 catch {} 吞掉错误
- **C-M09**: useContestSocket onConnectionStatus 回调双重注册
- **C-M10**: joinContest promise 总是 resolve
- **R-13**: 重复 Scenario 枚举（api + core）
- **R-14**: 重复 RecommendItem 类（api + core）
- **R-23**: REST API 无分页支持
- **R-30**: FreshnessScore 语义冲突

### 4.5 表单/状态管理 (6)

- **M-FH01**: UserCreateDialog 无客户端验证
- **M-FH02**: UserResetPasswordDialog 无密码强度校验
- **M-AR02**: 401 处理器并发重定向竞态
- **M-SM01**: Problems store 单一 loading 标志
- **M-AI01**: 视图组件无 AbortController 清理
- **M-FH03**: 问题导入文件无大小/类型验证

### 4.6 配置 (4)

- **R-09**: Spark 参数解析无数组边界检查
- **R-15**: Redis 端口默认 26379（Sentinel 端口）
- **R-24**: Dubbo registry check:false 隐藏 Nacos 不可用
- **M-AR01**: 会话过期阈值硬编码 5 分钟

---

## 五、实施计划

### Sprint 1: 紧急修复（CRITICAL + HIGH 安全类）

**目标**: 消除所有 CRITICAL 和 HIGH 安全缺陷

| 步骤 | 任务 | 文件 |
|------|------|------|
| 1.1 | Redis 反序列化安全加固 | `RedisConfig.java` |
| 1.2 | 修复 forum.ts 未定义变量 | `api/forum.ts` |
| 1.3 | 修复 DOMPurify 配置 | `utils/sanitize.ts` |
| 1.4 | 删除死代码 getTokenFromCookie | `lib/socket.ts` |
| 1.5 | XSS 过滤器排除安全头 | `XssFilter.java` |
| 1.6 | 修复 OAuth 用户密码空值 | `OAuthService.java` |
| 1.7 | JWT Secret 去除空默认值 | `application.yml` |
| 1.8 | 推荐策略数据刷新机制 | `RecommendationEngineConfig.java` |
| 1.9 | 推荐服务添加认证 | `recommend-web/pom.xml`, `RecommendController.java` |
| 1.10 | SMTP 密码脱敏 | `SettingsView.vue` |

### Sprint 2: 高优先级修复（HIGH 架构 + 性能）

| 步骤 | 任务 | 文件 |
|------|------|------|
| 2.1 | 删除 JwtUtils，统一 JwtTokenProvider | `JwtUtils.java` |
| 2.2 | 统一 WebSocket 管理器 | `lib/socket.ts`, `useContestSocket.ts` |
| 2.3 | 添加 404 路由 + 启用 ErrorBoundary | `router/index.ts`, `App.vue` |
| 2.4 | 修复审核模块分页状态共享 | `stores/admin/moderation.ts` |
| 2.5 | 修复 Contest Store timer 泄漏 | `stores/contest.ts` |
| 2.6 | 添加 useSearch/useSWR 清理逻辑 | `composables/useSearch.ts`, `useSWR.ts` |
| 2.7 | 推荐服务用户矩阵改为按用户 Redis key | `RedisRecommendationStore.java` |
| 2.8 | FeatureStore 添加大小上限 | `FeatureStore.java` |

### Sprint 3: 中优先级修复（MEDIUM）

| 步骤 | 任务 |
|------|------|
| 3.1 | CSRF 保护加固（未认证请求） |
| 3.2 | Token 黑名单检查集成到 JWT 过滤器 |
| 3.3 | N+1 查询批量优化（Moderation, Contest, Submission） |
| 3.4 | @Valid 注解补全 + 密码重置验证 |
| 3.5 | BeanUtils 替换为显式字段映射 |
| 3.6 | Console/Mgmt 表单验证补全 |
| 3.7 | 生产环境 console.log 清理 |
| 3.8 | Cookie 设置代码去重 |
| 3.9 | 推荐服务 Dubbo 缓存键修复 + 分页 |
| 3.10 | 推荐服务配置修复（Redis port, check, namespace） |
| 3.11 | Spark MinHash LSH 实现 |
| 3.12 | Spark 参数边界检查 + 冗余 .count() |
| 3.13 | JWT Cookie Secure 生产环境默认 true |

### Sprint 4: 代码质量优化（LOW）

- 移除默认数据库凭据
- 备份目录权限加固
- 大组件拆分（ProblemListsView 1356 行、ProblemsListView 1224 行等）
- 推荐模块重复枚举/类合并
- 空文件清理（recommendation/1h7xZM）
- run-evaluation.sh 修复
- Accessibility 改进（ARIA 标签）
- highlight.js 按需导入

---

## 六、风险与缓解

| 风险 | 缓解措施 |
|------|----------|
| Redis 反序列化修复可能影响已有缓存数据 | 先清空缓存再部署，或在读取时增加兼容性处理 |
| WebSocket 统一重构影响实时通知功能 | 灰度发布，先测试通知再测试比赛 Socket |
| 推荐策略刷新机制增加 Redis 负载 | 使用本地 Caffeine 缓存 + 短 TTL 减少 Redis 调用 |
| N+1 查询优化可能改变排序行为 | 逐个模块优化，每次优化后运行集成测试 |
| Spark MinHash 实现需要调参 | 先在离线评估中验证召回率，再上线 |
