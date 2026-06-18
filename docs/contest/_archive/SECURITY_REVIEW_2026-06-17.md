> ⚠️ **历史证据（保留原样）**：当前权威定档见 **[REVIEW_V3.md](../REVIEW_V3.md)**（审查实际代码，裁决不建议合入）。本文档为 Security 审查历史证据，finding 在代码中的实际去向见 [V3 §7](../REVIEW_V3.md)。其中 CRIT-9/10（IDOR/appSecret）已在代码中用 UUID 方案修复，详见 V3 §5。

# Contest 修复计划 — Security 专项审查 (Retry)

**日期**: 2026-06-17
**覆盖攻击面**: 7 个 (IDOR / appSecret / WS / is_virtual / Migration / 数据泄漏 / 审计反作弊)

## 摘要

- 总 finding: 14
- 严重度分布: CRITICAL=2, HIGH=4, MEDIUM=5, LOW=2, INFO=1
- 前 3 关键 finding:
  1. F-SEC-01 (CRITICAL) — `X-Virtual-Session-Id` 完全依赖客户端控制,可伪造任意 userId+contestId 组合,造成横向越权提交 / 偷看他人虚拟题解
  2. F-SEC-02 (CRITICAL) — `HMAC-SHA256(appSecret, …)` 中的 `appSecret` 在项目里根本不存在,计划对"轮换/治理/多环境"零设计,提交 HMAC 退化为空密钥或被错误用 JWT secret 顶替
  3. F-SEC-03 (HIGH) — `is_virtual` 字段 API 层可写,无 DB CHECK constraint,攻击者把虚拟提交伪装成真实比赛记录,污染真榜 / 触发 Elo / 成就

## Finding 列表

### F-SEC-01 · CRITICAL · `X-Virtual-Session-Id` 客户端控制导致横向越权
- **位置**: 计划 2.2 (Phase 2 第 494-510 行);`ContestServiceImpl.submitContestProblem` / `findByContestIdAndUserIdAndVirtualSessionId`
- **证据**: 计划原文 `如果 header 有 X-Virtual-Session-Id: 查 virtual participant`;且 1.3 明确 `virtualSessionId` 用 `HMAC-SHA256(appSecret, contestId + ':' + userId)` —— **sessionId 是 (userId, contestId) 的纯函数**,无随机 salt,无 userId 绑定 nonce
- **关切**:
  1. **真正鉴权应当绑定 userId**: 计划 §1.3 说"真正鉴权仍然来自登录用户 + participant 归属校验",但 §2.2 的逻辑只说"查 virtual participant"。如果 mapper 接受任何 sessionId,则攻击者 A 用自己 sessionId(对应自己 userId)调用 /submit 没问题;但若 A 抓取到 B 的 sessionId(从他人 page source、聊天泄漏、BroadcastChannel、Redis 缓存)就能以 B 身份提交
  2. **若 mapper 改成 sessionId only 查**: 完全没校验 participant.userId == principal.userId;A 可遍历 contestId + 任意 userId 计算出 sessionId(只要 appSecret 泄漏过或从源码可推)
  3. **sessionId 计算确定性 + 无服务端 nonce** = IDOR 教科书案例
  4. **postMessage / WebSocket 透传 sessionId**: 计划 §6.1/§6.2 把 sessionId 走 WS join 校验,如果 WS handler 用同一 mapper,等价问题
- **修复**:
  1. mapper 强制 `WHERE virtual_session_id=? AND user_id=?`,userId 来自 `@AuthenticationPrincipal`,**绝不**接受 query/header 里的 userId
  2. sessionId 改为 `HMAC(appSecret, userId || contestId || startedAt || randomNonce)`,nonce 存 `participant` 行,二次校验
  3. WS join 同样按 (userId, sessionId) 双 key 校验,reject 任何缺一者
  4. 加回归测试:用户 A 拿 B 的 sessionId 提交 → 403

### F-SEC-02 · CRITICAL · `appSecret` 在项目里不存在,HMAC 治理真空
- **位置**: 计划 1.3 (Phase 1 第 419-431 行)
- **证据**: 计划原文 `HMAC-SHA256(appSecret, contestId + ':' + userId)`;但项目当前 `application.yml` / `FeatureFlagsProperties` / `JwtProperties` 等配置中**没有任何 `appSecret` 字段**(F-SEC-02 假定:无代码搜索到 `appSecret` 引用,需要 review 时确认)
- **关切**:
  1. **空密钥退化**: 若代码走 `MessageDigest.getInstance("HmacSHA256")` 配 null,直接 NPE;若走 "if appSecret is null use empty string",任何人都可预测 sessionId
  2. **借 JWT secret 当 appSecret**: 开发者顺手复用 `jwt.secret`,导致 JWT secret 一旦轮换,**所有未结束的虚拟 session 全部断链**,用户体验崩;且 attack surface 扩大(破坏 JWT 等同破坏虚拟 session 完整性)
  3. **多环境一致性**: dev / staging / prod 共用 secret?各自随机?如果用 .env,需要确保 prod 启动校验 secret ≥ 32 字节(与 JWT secret 治理同档)
  4. **轮换方案缺失**: 计划 0 字节谈轮换;一旦轮换,所有老 session 失效,**用户开着的虚拟赛突然无法提交/无法 finish**
  5. **泄露后果**: appSecret 泄漏 = 任何攻击者可对任意 (userId, contestId) 计算 sessionId,**IDOR + 永久伪造**
- **修复**:
  1. 在 `FeatureFlagsProperties` / 新建 `VirtualContestProperties` 显式定义 `appSecret`,从 `VC_HMAC_SECRET` 环境变量注入,默认 null,启动时 fail-fast 校验长度 ≥ 32
  2. 单独 secret,**禁止**复用 JWT secret
  3. 写"双 secret 轮换"方案:新 secret 生效后老 secret 仍可校验 7 天(`valid_from` / `valid_to` 列),期间同时接受两个
  4. multi-env 表格化文档:`dev` / `staging` / `prod` 各自 secret 来源(CI secret / Nacos / .env)

### F-SEC-03 · HIGH · `is_virtual` 字段 API 层可写,无 DB CHECK,虚拟/真实可互转
- **位置**: 计划 Phase 1 / Phase 2;DB 计划 1.2 提到 `UNIQUE KEY uk_contest_user_virtual(contest_id, user_id, is_virtual)` 但**没有** `CHECK (is_virtual IN (0,1))` / `BEFORE UPDATE` trigger
- **证据**: 计划 §2.2 "查 virtual participant";但没说"创建 participant 时 is_virtual 来自哪里";§9 风险章节没提"API 是否能改 is_virtual"
- **关切**:
  1. 如果 mapper 用 `@Param("isVirtual")` 注入,`/admin/contest/participant/update` 类接口(若存在)能改 is_virtual,导致**虚拟 AC 变真实 AC**,触发 Elo / 成就
  2. 没 CHECK 约束: dev/seed 误插入 is_virtual=2 / -1,业务代码里三元判断 `is_virtual == 1` 直接绕过滤
  3. 没 trigger: 物理删除/恢复时(若项目用 `is_deleted`)cross FK 错位;`update is_virtual set 0` 没审计
  4. **反向攻击**: 用户 POST `is_virtual=false` 强制真实参赛,绕过"虚拟赛不参与真榜"的产品决策 = **产品级越权**
  5. **伪造 contest_status=RUNNING**: 若 FINISHED 比赛想开虚拟,选手伪造 `participant.is_virtual=false` + `contest.status=RUNNING`,等于在任意比赛里"开挂"实时提交
- **修复**:
  1. DTO 序列化时 `@JsonIgnore` is_virtual;Controller 层 `@Column(updatable=false)` 或 mapper XML 显式 `<if test="isVirtual != null">`,写路径**完全禁止**修改
  2. 加 DB constraint:
     ```sql
     ALTER TABLE contest_participants
       ADD CONSTRAINT chk_is_virtual CHECK (is_virtual IN (0,1)),
       ADD CONSTRAINT chk_virtual_pair CHECK (
         (is_virtual=0 AND virtual_session_id IS NULL) OR
         (is_virtual=1 AND virtual_session_id IS NOT NULL)
       );
     ```
  3. 加 `BEFORE UPDATE` trigger 拦截 `is_virtual` 字段变更
  4. 加 admin 后台**专用**修改入口,带 `@PreAuthorize("hasRole('SUPER_ADMIN')")` + `@Audited` + 写审计日志

### F-SEC-04 · HIGH · WS join 鉴权 race + 旁观/公开房间泄漏
- **位置**: 计划 §6.1 (Phase 6 第 885-898 行)
- **证据**: 计划原文 `是真实 participant OR 是当前虚拟 participant OR 比赛允许公开旁观`;`handleJoinContest` 检查列表
- **关切**:
  1. **race**: 用户在 virtual STARTED → 比赛 auto-finish 之间 join WS,`participant.status` 短暂 FINISHED,handler 拒绝,但 user 已收到 race 期间 WS 帧吗?计划没说"先入队,后校验"
  2. **未鉴权 join 公开房间**: "比赛允许公开旁观"没定义 — 任何公开/未设 flag 的比赛都开放旁观 = 旁观者能 subscribe `/topic/contest/{id}/submission-result` 看他人判题结果(包括失败堆栈摘要、错误用例)
  3. **session 过期后还在收消息**: 计划 §3.3 finish 推 WS,但用户 sessionStorage 还活着,前端 reconnect 后又被后端 `validatesession` 拒绝,但此时已收到泄露的 ranking frame
  4. **广播大小/速率**: 排行榜 N=5000 用户,每 1-2s 推一次 = 1MB/次,无 diff 推送 = 客户端 OOM
  5. **公告 privilege escalation**: 计划没明说"只有 admin 能推 announcement",如果 announcement 走的是 participant 角色,任何参赛者能广播
- **修复**:
  1. 旁观权限显式定义为 `contest.spectatorEnabled=true` 字段(默认 false),DDB 显式
  2. WS handler 顺序: authentication → 查 participant → 查 visibility/spectator flag → 才 subscribe;任一失败 deny
  3. session expire 后发 `force-disconnect` 帧,前端清 Pinia + 跳回详情页
  4. ranking 用 diff 推送(只推 changed rows)+ 限速(每 1s 一次)
  5. announcement 推权限校验 `@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")`

### F-SEC-05 · HIGH · HMAC deterministic sessionId = 预测 / 共享 / 持久 IDOR
- **位置**: 计划 1.3 第 419-431 行
- **证据**: 计划原文 `HMAC-SHA256(appSecret, contestId + ':' + userId)`
- **关切**:
  1. **跨设备 IDOR**: 用户在手机开虚拟 session,sessionId = HMAC(secret, contestId+userId);同一用户电脑打开页面,**sessionId 一致**,**覆盖**已有 startedAt(若没有保护)— A 设备开, B 设备 join, B 把 A 的进度当自己的
  2. **跨比赛稳定性**: 同 userId + 不同 contestId → 不同 sessionId,可枚举;若 secret 长期不变,**可离线生成任意 (userId, contestId) 的合法 sessionId**,等 appSecret 泄漏立即生效
  3. **持久化攻击**: 数据库已经存在 startedAt > current 的虚拟 participant,攻击者 A 用自己的 sessionId 去 join,**触发后端 finish A 的老 session**?需要确认 mapper 是否含 startedAt 比对
  4. **Replay 攻击**: sessionId 不变 = 攻击者截获一次合法提交,可**重复提交**到 contest(proxy 抓包),如果服务端没 nonce 校验
- **修复**:
  1. sessionId 加入 `randomNonce` / `startedAt` 盐值,**每次 start 重新生成**,通过 (userId, contestId) 索引查 participant
  2. 用 `startedAt` 做 server-authoritative 兜底:即使 sessionId 撞库,以 DB 里 `startedAt + duration` 为准
  3. submit 接口加 idempotency-key / 单次性 nonce,避免 replay
  4. 公开攻击面:在 production 部署后,提供 `/admin/debug/virtual-session-info?userId=X&contestId=Y` 类工具(若存在)需严格 `@PreAuthorize`;HMAC 可让**任何拥有 secret 的 admin**伪造任意 sessionId — 锁白名单 admin

### F-SEC-06 · HIGH · slug 唯一约束 dedup 阶段 race / 锁
- **位置**: 计划 1.1 (Phase 1 第 378-394 行)
- **证据**: `保留最早创建的一条. 其余 slug 改为 slug-duplicate-{id后缀}`;但 dedup UPDATE 没显式加 `FOR UPDATE` / `SELECT ... LOCK IN SHARE MODE`
- **关切**:
  1. **dev/staging/prod 并发 dedup**: 多台 dev 机器同时跑 migration → UPDATE 互相覆盖 / 死锁
  2. **read-replica 漂移**: migration 在 master 跑,replica 还没同步;前端读 replica 时还能看到重复 slug
  3. **admin 后台创建 race**: 两 admin 同一秒创建同 slug contest,dedup 后一个的 slug 被改名为 `slug-duplicate-xxx`,**数据完整性可接受但 URL 不可预测**;用户体验问题,但不是安全问题
  4. **回滚窗口**: 加完 unique index,发现 prod 实际有漏 dedup 的脏数据,回滚 migration 是否有 rollback SQL?计划没写
  5. **lock_wait_timeout**: dedup UPDATE 在大表(`contest_participants`)上,如果 online 期间有人在 INSERT,UPDATE 等锁,**用户 502 / 请求超时**
- **修复**:
  1. dedup 阶段显式 `START TRANSACTION` + `SELECT ... FOR UPDATE` + 分批 `LIMIT 1000`
  2. 准备 `V2026xxxx__Rollback_xxx.sql` 反向脚本(migration 文件夹注释里写)
  3. 在 pre-migration 钩子检测 `INFORMATION_SCHEMA.STATISTICS` 副本延迟,或强制走 master
  4. admin 后台 slug 输入时**实时校验**(乐观锁),dedup 只做兜底

### F-SEC-07 · MEDIUM · `penaltyPerWrong` / `tieBreaker` admin 改值可被实时作弊
- **位置**: 计划 Phase 4 (整章)
- **证据**: `applyJudgeResult` 改用 contest 配置;但 admin 后台改 `penaltyPerWrong` / `scoringMode` 的窗口期呢?计划没明说"比赛开始后锁定"
- **关切**:
  1. **比赛进行中改 scoringMode**: ICPC → IOI 切换,排名瞬间反转,影响所有已 AC 选手的 final_rank
  2. **比赛进行中改 penaltyPerWrong**: 600 → 0,所有罚时归零,部分选手排名飞升;**没有审计 / 没有锁定**
  3. **回放攻击**: 比赛结束前 admin 改 `isRated=true → false`,跳过 Elo,赛后改回 true,**审计可查但没有主动告警**
  4. **tieBreaker 改了**: rank 重算,若 race 期间 ranking 已 cache 推给客户端,**客户端看到的排名 = 旧规则的最终态,新规则的实际态**
- **修复**:
  1. 比赛状态 = RUNNING 时,scoring 相关字段全部 `updateable=false` (DTO / mapper 双重锁)
  2. FINISHED 后允许改,但**需要重新触发 final_rank 重算** + 推 WS ranking invalidate
  3. 所有 admin 改 scoring 字段写 `@Audited` 审计日志(actor / before / after / contestId / time)
  4. 改 isRated 触发自动告警(项目已有的 `monitoring` 模块)

### F-SEC-08 · MEDIUM · 虚拟榜 endpoint 暴露其他 user 真实信息
- **位置**: 计划 5.2 (Phase 5 第 803-823 行)
- **证据**: `GET /contest/{contestId}/virtual/ranking`;`仍然不能泄露不该泄露的用户敏感字段`
- **关切**:
  1. **数据泄漏面**: 虚拟榜虽然隔离了 is_virtual,但若返回 userId + username + avatar + real-rating,**任何登录用户能枚举出"谁在补打这场"**
  2. **跨比赛信息**: 同一用户可能在 100 场比赛里开虚拟,某比赛观察者能列全该 user 参与的虚拟场
  3. **admin 后台 admin 视角**: 计划没明说"admin 能否列出所有虚拟 session";如果能,**这是用户行为画像泄漏** —— admin 可能滥用:看某用户在哪场虚拟 AC
  4. **公开 / 私有**: "如果产品希望公开" — 计划留口子,产品后置决定,但**安全策略应当默认私有**
- **修复**:
  1. 虚拟榜返回字段白名单:`userId` + `username` (PII:email/avatar 屏蔽);按 `participant.final_rank` 排序
  2. admin 后台"虚拟 session 列表"接口:仅 SUPER_ADMIN 可调,且写审计
  3. 默认虚拟榜对其他参赛者**不可见**,只允许本人看自己的 + 公开榜(若产品决定)
  4. 加 rate limit:`/contest/{id}/virtual/ranking` 30 req/min/user

### F-SEC-09 · MEDIUM · virtual / real 真实提交记录 query 共享 mapper → 历史 AC 泄漏
- **位置**: 计划 2.4 (Phase 2 第 519-538 行)
- **证据**: 计划原文 `真实赛: contest_id + contest_problem_id + user_id + participant.is_virtual=false`;若 mapper 实现是 `LEFT JOIN contest_participants` 而 LEFT JOIN 拉了所有 participant
- **关切**:
  1. 攻击者 A 查自己虚拟 session 题目状态时,若 mapper JOIN 拉了真实 participant,**能间接看到 A 真实 AC 记录**(返回数组里包含虚拟 + 真实)
  2. /contest/{id}/problems (计划 §6 数据泄漏面) 是否过滤虚拟数据?**计划没明说**
  3. 题目状态查询 `/contest/{contestId}/problems/{problemId}/status` 是基于 `submission` 还是 `contest_submission`?若前者,虚拟提交写到 `submissions` 公共表,**会污染普通 problems 页的 AC 状态**
- **修复**:
  1. SQL 强制 `AND cp.is_virtual = ?` 显式参数,绝不依赖前端过滤
  2. 虚拟提交写到独立 `virtual_submissions` 表 或 `contest_submissions` 表,**禁止**写 `submissions` 全局表
  3. 加 IT 测试:虚拟 AC 后,/problems/{id} 状态不变

### F-SEC-10 · MEDIUM · Flyway 迁移期间 admin / 用户操作无锁,数据漂移
- **位置**: 计划 1.1, 1.2 (Phase 1 整章)
- **证据**: dedup UPDATE + 加 unique index;线上跑时无 DDL 锁说明
- **关切**:
  1. **pt-online-schema-change 不在项目内**: 加 unique index 是 `ALTER TABLE` = metadata lock,InnoDB 表 `ALTER TABLE ... ADD UNIQUE KEY` 默认是 in-place 但有 online ddl 短暂 exclusive;并发 INSERT 会等
  2. **lock_wait_timeout 默认 50s**: 大表 `contest_submissions` 加 index 时(若计划未来加),50s 阻塞会导致用户提交失败
  3. **dev seed 冲突**: 项目 `linked-list-special` seed 改了 status = FINISHED,**同时迁移改了 schema**,两 migration 顺序:若 seed 在 dedup 前跑,seed 写入的虚拟 session 落进 dedup 范围,可能误删
  4. **ROLLBACK 缺失**: Flyway 默认无 rollback,**任何 schema 错改 = 不可逆**(除了手动写反向 SQL)
- **修复**:
  1. 迁移期设 `innodb_lock_wait_timeout=10` 缩短影响
  2. 用 `ALGORITHM=INPLACE, LOCK=NONE` 显式声明(若 MySQL 8.0+)
  3. 准备每条迁移的 reverse SQL,放在 `init-db/rollback/` 目录(可选)
  4. seed 和 schema 迁移分文件,seed 用 `R__` repeatable 或显式顺序

### F-SEC-11 · MEDIUM · WebSocket 鉴权 → 队列,用户可冒充他人
- **位置**: 计划 6.1, 6.2 (Phase 6 整章)
- **证据**: `/user/queue/notification` + `/user/queue/contest-status`;若 Spring `@MessageMapping` 的 principal 从 STOMP header 拿
- **关切**:
  1. **STOMP user destination 错配**: 计划 §6.1 明确写过"WebSocket 鉴权只接受 access_token cookie,禁止 query token / URL token / 客户端 STOMP token" — 若 STOMP CONNECT 头里也允许 token,被劫持
  2. **logout 后没 disconnect**: 用户登出,WS 不断,**老订阅继续收到排名 / 判题结果**,session 复用时 leak
  3. **replay of submissions across user boundaries**: 计划 §3.3 推 `/user/queue/contest-status`,如果 user-destination 错误解析成 broadcast,全房间用户能收到他人的"虚拟赛已结束"
- **修复**:
  1. WebSocket handshake 仍走 `access_token` cookie,STOMP CONNECT 头禁止 token
  2. logout 接口同步 `WebSocketSession.close()`,前端 `beforeunload` 触发 leave
  3. 单元测试:用户 A subscribe `/user/queue/...`,用户 B 触发推送,验证 A 收不到

### F-SEC-12 · LOW · 反作弊钩子(开卷 / 刷题 / 账号多开)未设计
- **位置**: 全文,§4.7 提了"虚拟不触发成就 / Elo / 真首杀",但**没有反作弊**
- **证据**: 整章未提
- **关切**:
  1. **多开账号**: 同一 IP / device 多个 user 报同一场,提交时间间隔<1s 几乎肯定是协同
  2. **外部 LLM 协助**: 虚拟赛本意是"练习复盘",但用户在虚拟赛跑外面 LLM 完成,**全 AC 也无意义**,系统识别不到
  3. **开卷**: 比赛进行中用户查自己历史 AC 代码,问题难度降低;**系统没禁止**
  4. **刷题**: 用户 1000 场虚拟赛全 AC,成就系统没拦(计划 §4.7 提了虚拟不成就,但**没提恶意刷题检测**)
- **修复**:
  1. 加 `AntiCheatService` 入口,在 `submitContestProblem` 调用
  2. 短期:同 IP N 个 user 同场比赛同时段提交 → 标记 + 管理员后台
  3. 长期:typing DNA / keystroke dynamics(超出本次 plan 范围,但留扩展点)

### F-SEC-13 · LOW · 虚拟赛结束无审计 / log retention 不清
- **位置**: 全文
- **证据**: 计划没提"虚拟赛 AC 写不写审计日志" / "log retention"
- **关切**:
  1. **无审计**: 比赛反作弊追溯需要"用户何时开虚拟、AC 顺序、失败回放",计划没说要存
  2. **log retention**: 项目是否有日志保留策略?6 个月?GDPR 合规?虚拟 session 数据含判题失败堆栈(可能含代码片段)
  3. **Admin 改 scoring 模式**: 整章没提"中途改 scoring 写不写审计"
- **修复**:
  1. 虚拟赛 AC 写 `audit_log` 表(actor=system, action=VIRTUAL_SUBMISSION, payload=submission_id+score)
  2. log retention 文档化(在 `docs/CONTRIBUTING.md` 或新 `docs/PRIVACY.md`)
  3. admin 改 scoring 字段加 `@Audited` 注解,记录 before/after + actor

### F-SEC-14 · INFO · 计划本身偏功能性,安全章节 0 字节
- **位置**: 整文档
- **证据**: 全文搜索 security / 安全,只在 §8.1 提了"Markdown / KaTeX sanitization" 和 "admin 写接口必须 @PreAuthorize + CSRF"
- **关切**:
  1. 计划没列 "STRIDE threat model" / "OWASP coverage"
  2. 关键的 race condition (HMAC 确定性、participant 唯一性) 没被识别为风险
  3. 测试章节 §7 没列 security test:IDOR / privilege escalation / spoofing
  4. 整个 plan **应当** 加一节"安全约束 / 不可妥协项"
- **修复**:
  1. 加 §12 "Security Requirements" 章节,列 OWASP API Top 10 逐条对应
  2. 测试章节 §7.5 加 "Security test cases" 子节
  3. 评审前必须经过 security review checklist(本文档即用作 checklist)

## 验证建议(交付前必跑)

1. `F-SEC-01 / 02 / 03` 必须先修复并加 IT,再合并 Phase 1-3
2. `F-SEC-04` 在 Phase 6 merge 前必跑 `WS authn fuzz test`
3. `F-SEC-05` 加 regression test:同一 userId + 多次 start virtual → sessionId 不同 / participant 行唯一
4. `F-SEC-08 / 09` 加 privacy regression test:虚拟 AC 不可见普通 problems 页
5. `F-SEC-11` 加 multi-user WS broadcast 测试
6. `F-SEC-14` 整改文档结构,所有 PR 必经过 security review agent
