# 数据流、契约与事务

## 请求链样本

| 场景 | 当前链路 | 一致性边界 |
| --- | --- | --- |
| 登录/刷新 | Auth Controller → workflow → Auth account/session store → cookies | Auth 本地事务；refresh hash-only CAS |
| 管理员创建题目 | Admin Controller → App Problem provider → Problem service → local mapper/entity | App Problem Owner 本地事务 |
| 普通提交 | App request boundary → immutable facts snapshot → Submission intake → submission/judge outbox | Submission Owner 本地事务；事件异步 |
| 比赛提交 | Contest eligibility → Submission `submitContest` → created/judge outbox → Contest inbox | 资格同步校验；关联最终一致 |
| 判题结果 | Judge Stream → sandbox → Submission verdict/fence → result outbox | generation/attempt CAS；下游 Inbox |
| 通知投递 | App intent outbox → Notification Inbox → delivery ledger → SMTP/Redis relay | intent 本地事务；投递可重试 |
| 权限写入 | Admin account/version query → Auth `AuthorizationMutationService` delta | Auth 本地事务；direct row + CAS + audit/outbox + receipt |
| 公开代码运行 | App `InteractiveCodeRunner` → Judge `JudgeRunService` → Judge runtime `SandboxExecutor`；异步 preview 另走 `submit/poll/cancel` → `AsyncSandboxExecutor` | Judge 独立进程；只运行显式 public cases，缺 provider 映射 503；Judge0 默认关闭且外部证据未验证 |
| 搜索 | Owner event → Search worker → version ledger → MeiliSearch | 派生索引；旧事件按版本丢弃 |

## Contract 与 Dubbo

`services/api/` 当前包含 `auth-api`、`app-api`、`submission-api`、`notification-api` 和 `judge-api` 五个 provider-owned 模块。Contract 只能包含接口、DTO、错误码、事件和无状态元数据，不得依赖 Entity、Mapper 或实现模块。Submission mutation 已拆成 `SubmissionIntakePort`、`SubmissionVerdictWritePort`；权限 mutation 使用 Auth-owned 单条 delta；Judge preview 使用 `JudgeRunService`，其 App HTTP DTO 只在 App Adapter 侧映射。Contract artifacts 使用 reactor revision `2.0.0`；wire-incompatible 的 Submission read 方法使用 `1.1.0`，同主版本变更由 japicmp 门禁保护。

- 写调用自动 retry 为 0；query/execution 使用 `RpcPolicy` 的有界 timeout 与重试预算。
- Provider 验证签名、audience、deadline、jti/replay 和 actor；Dubbo attachment 不是信任边界。
- Provider 不同步调用第三个 Provider 完成同一命令；组合读优先使用本地 projection，临时实时读只做有界并行批量调用。
- 保持 `Result<T>` / `RpcResult` envelope 和既有字段映射；业务错误与 transport 错误分开映射。

## 事件可靠性

跨进程副作用使用本地事务内 Outbox、Redis Streams、Consumer Inbox、delivery ledger、lease 和 fence：

- Submission：`judge_outbox`、`submission_result_outbox`、`submission_created_outbox`，generation/attempt fence。
- Notification：Inbox、delivery ledger、stale lease reclaim、有限重试和幂等投递。
- Search：版本账本 `search:doc-version:{index}`，DELETE 使用 `D:T` tombstone，旧版本只 ACK 不覆盖新版本。
- Judge：Streams PEL、`0-0` group replay、bounded reclaim、DLQ、ACK-after-write。
- Audit：Auth/App 在本地业务事务写 audit outbox，Admin 通过 `Admin-Audit` inbox 按 event id 幂等落库。

事件必须带 `eventId`、`aggregateId`、`aggregateVersion`、`causationId`、`traceId`、`schemaVersion`；不支持的版本、恶意字段或非法版本在业务效果前 fail closed。ACK 只发生在持久化或明确 poison staging 成功后。


跨 Owner 不使用 SQL join、共享 Mapper 或跨 schema 写 grant。`users` 的 profile 垂直拆分遵循 expand → backfill → verify → cutover → contract；已应用 migration 不编辑。

### 完整 Data/Table matrix

缩写：**I**=Owner 内部直接 DB；**Q**=粗粒度 Query/批量 RPC 或本地物化投影；**C**=幂等 Command RPC；**E**=outbox/event；**R**=核验后退役。任何 Q/C 都不得返回 Entity、Mapper 或内部 Domain Model。

| Data/Table | Current Owner / 当前调用方 | Target Owner | Consumer | Access Method |
|---|---|---|---|---|
| `DailyRecommendation` | 仅 migration，生产 Java 未见映射 | App（R 候选） | App | 核数据后 R，否则 I |
| `achievements` | achievement；submission/solution/follow 触发或读取 | App | App 内部 | I/E |
| `appeals` | moderation R/W | Admin | App 用户入口 | Gateway 直达 Admin HTTP；I |
| `audit_logs` | Admin mapper；各 Owner audit event sink | Admin | 各服务、Admin 查询 | 生产者 E，Admin I/Q |
| `audit_outbox` | Auth/App/Admin 请求事务内的 owner-local audit outbox | Auth/App/Admin（各自 schema） | Admin | 各 Owner I；Admin 通过事件 inbox 消费 |
| `collection_items` | bookmark R/W；edgeoperations 读 | App | App | I |
| `collections` | bookmark folder/service | App | App | I |
| `consumer_inbox` | 集成事件暂存；Admin-Audit 与 Notification 各自持有本地 inbox | Admin/Notification（各自 schema） | Admin、Notification、App（过渡） | 各 Owner I；按 group 消费 |
| `contest_analytics` | 仅 migration，当前实时 projection 计算 | App（R 候选） | Admin analytics | R 或 App I + Admin Q/E |
| `contest_announcements` | contest 读；admin 直接写 | App | Admin、WebSocket | Admin C/Q；App I/E |
| `contest_participants` | contest R/W；admin analytics 读 | App | Admin | App I；Admin Q/投影 |
| `contest_problem_results` | contest adjudication/lifecycle | App | App | I |
| `contest_problems` | contest R/W；admin 直接写 | App | Admin | App I；Admin C/Q |
| `contest_rankings` | 仅 migration；当前排名由 participant/cache 计算 | App（R 候选） | App/Admin | R 或明确为 App projection |
| `contest_scoring_rules` | contest ScoringRuleService | App | Admin | App I；Admin C/Q |
| `contest_submissions` | contest/submission association | App | App | I；由 SubmissionCreated event/inbox 幂等写 |
| `contests` | contest 与 admin 多方写/读 | App | Admin | App I；Admin C/Q/E |
| `edge_operations` | vote/edgeoperations；多内容域使用 | App/Engagement | App 内容模块、Admin | I；统一 Engagement port |
| `first_solve_records` | contest adjudication | App | App | I |
| `forum_comments` | forum；admin/moderation 直接写 | App | Admin | App I；Admin C/Q |
| `forum_communities` | forum；admin 读 | App | Admin | I/Q |
| `forum_community_links` | 仅 migration | App（R 候选） | App | 核数据后 R/I |
| `forum_community_members` | forum membership | App | App | I |
| `forum_community_permissions` | 仅 migration；不是 Auth RBAC | App（R 候选） | App | 核数据后 R/I |
| `forum_community_rules` | 仅 migration | App（R 候选） | App | 核数据后 R/I |
| `forum_community_tags` | 仅 migration | App（R 候选） | App | 核数据后 R/I |
| `forum_post_tag_relations` | 仅 migration，当前 mapper 未见关系 SQL | App（R 候选） | App | 核数据后 R/I |
| `forum_posts` | forum；admin/moderation 直接写，search 读 | App | Admin/Search | App I；Admin C/Q；Search E |
| `forum_tags` | forum；admin tag 管理 | App | Admin | App I；Admin C/Q |
| `forum_users` | forum 的身份投影 | App | Forum | Auth Account event → App I |
| `global_rankings` | contest rating/ranking facts；display name/avatar read from App `user_profiles` | App | Admin/App | I；身份显示只走 App profile projection |
| `judge_outbox` | Submission 写，queue dispatcher/reaper 更新 | Submission | Submission/Judge worker | 与 submission 同 Owner DB I；不跨服务 SQL |
| `moderation_actions` | moderation | Admin | Admin | I |
| `moderation_queue` | moderation，引用多种 App 内容 | Admin | App 内容 Owner | Admin I；App C/Q/E |
| `notification_command_receipt` | Notification 命令回执（幂等重放） | Notification | Notification | I |
| `notification_delivery_ledger` | notification dispatcher/reaper | Notification | Admin 运维读 | Notification I；Admin Q |
| `notification_preferences` | notification | Notification | Notification | I |
| `notifications` | notification | Notification | Admin、WebSocket | Notification I；Admin Q/E |
| `oauth_provider_identities` | Auth OAuth workflow/mapper；provider + provider_user_id 的账号绑定 | Auth | Auth | I；唯一约束保证同一 provider identity 只绑定一个账号 |
| `password_resets` | 仅 migration；实际 hash 存 `users.password_reset_*` | Auth（R 候选） | Auth | 核数据后 R；保留 hash-only 流程 |
| `problem_details` | problem；admin 直接读写 | App | Admin | App I；Admin C/Q |
| `problem_examples` | problem/admin；judge fallback 读 | App | Judge worker、Admin | App I；versioned case snapshot/Q |
| `problem_languages` | problem/admin；submission 读 facts | App | Admin/Judge | App I；C/Q |
| `problem_list_bookmarks` | problemlist | App | App/Admin | I/Q |
| `problem_list_categories` | problemlist | App | App | I |
| `problem_list_problem_relations` | problemlist；admin 读 | App | Admin | I/Q |
| `problem_lists` | problemlist；admin 管理 | App | Admin | App I；Admin C/Q |
| `problem_notes` | problem note | App | App | I；先修 schema drift |
| `problem_tag_relations` | problem；admin/user/solution 读 | App | Admin/App modules | I/C/Q |
| `problem_tags` | problem；admin 管理 | App | Admin | App I；Admin C/Q |
| `problem_versions` | problem version/snapshot | App | Admin | App I；Admin C/Q |
| `problems` | problem；admin/mod/search/contest/submission 多方读写 | App | Admin、Contest、Submission、Search | App I；Admin C/Q；App 内 port/E |
| `refresh_tokens` | refresh token service | Auth | Auth only | I，禁止其他服务读 |
| `reports` | moderation，普通用户可创建 | Admin | App 用户/Admin | Gateway 直达 Admin HTTP；I |
| `role_permissions` | permission；admin projection 直读 | Auth | Admin、Auth | Auth I；Admin C/Q |
| `solution_comments` | solution；admin/moderation 直接写 | App | Admin | App I；Admin C/Q |
| `solution_topics` | solution reference | App | Admin/App | App I；Admin C/Q |
| `solutions` | solution；admin/mod/search/problem/interaction 使用 | App | Admin/Search | App I；Admin C/Q；Search E |
| `submission_statuses` | 仅 migration；代码用 `SubmissionStatusCatalog` | Submission API（纯 contract/catalog） | App、Submission | `SubmissionStatus` enum 在 common；catalog 由 `backend-submission-api` 唯一实现 |
| `submissions` | submission；admin/problem/contest 直读 | Submission | Admin、Contest/Problem | Submission I；Admin Q；结果 E |
| `submission_result_outbox` | submission result dispatcher/worker 写 | Submission | Submission/Judge worker | Submission I；不跨服务 SQL |
| `submission_created_outbox` | contest intake association event | Submission | App-Contest inbox | Submission I；App 仅消费事件写 `contest_submissions` |
| `subscriptions` | subscription；admin analytics | App | Admin | App I；Admin Q/C |
| `system_announcement_reads` | 仅 migration | Admin（R/启用候选） | App 用户 | 启用则 Admin I + HTTP/Q/E |
| `system_announcements` | 仅 migration | Admin（R/启用候选） | App 用户 | Admin I；App Q/E projection |
| `system_settings` | admin store | Admin | App/Auth（只需部分） | Admin I；versioned E/cache，避免热路径 RPC |
| `test_cases` | admin test-case service；judge 读 | App/Problem-Judge | Admin/Judge | App I；Admin C/Q；case snapshot |
| `translations` | i18n service，polymorphic entity reference | App/I18n | Admin | App I；Admin C/Q |
| `user_achievements` | achievement | App | App | I/E |
| `user_bans` | moderation 治理记录 | Admin | Auth/App | Admin I；Auth ban C + status E |
| `user_follows` | follow | App | App | I |
| `user_permissions` | permission；admin 管理 | Auth | Admin | Auth I；Admin C/Q |
| `user_warnings` | moderation | Admin | App 用户 | Admin I；通知 E/Q |
| `users` | Auth/User/Admin/Moderation 多方写的混合表 | 迁移态 Auth；目标 Auth account + App `user_profiles` | App/Admin | Auth I；JWT/Q/C/E；App 不再写旧行 |
| `views` | 仅 migration；与 edge operations 语义重叠 | App（R 候选） | App | 核数据后合并/R |
| `virtual_contest_sessions` | 仅 migration；活动态已在 participants | App（R 候选） | App | 核历史数据后合并/R |
| `email_templates` | email | Notification | Admin、Auth（不共享业务模板） | Notification I；Admin C/Q；Auth 自有安全模板 |
| `email_logs` | email intake | Notification | Admin | Notification I；Admin Q |
| `backups` | Backup Entity/Mapper/Service CRUD | Admin/Ops | Admin | I（owner 已迁移至 backend-admin） |


## 事务边界

必须保持强一致且只在单一 Owner 内：refresh rotation、账号 ban/password、permission grant/revoke、Contest participant/count、Problem aggregate satellites、Submission + judge outbox、verdict fence + result outbox、moderation queue claim/decision、vote ledger invariant。应最终一致：Judge queue、SMTP、WebSocket、cache、对象存储、audit、notification、achievement、Search index、ranking projection 和跨 Owner moderation side effects。

DB 与 Redis/SMTP/WebSocket/对象存储不由 `@Transactional` 组合；使用 outbox/inbox/lease/fence/补偿。跨服务同步调用只做权威校验或一个 Owner command，不使用 Seata。

## 数据迁移与回滚

Owner migration 顺序固定为 `shared → auth → admin → app → notification → submission → post-owner controls`。`owner-migrate` 和 `owner-migration-manifest.sh` 负责 schema/location、账号、依赖、checksum、lease 和 secret-free reports。备份/恢复覆盖 `ulticode` control schema 与五个 Owner；rollback 通过上一已验证 artifact 与 `skip_migrations=true`，不做 schema downgrade。

详见 [`../operations/database-migrations.md`](../operations/database-migrations.md) 和 [`../../services/docs/CONTRACT_COMPAT_GATE.md`](../../services/docs/CONTRACT_COMPAT_GATE.md)。
