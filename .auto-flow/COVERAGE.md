# Coverage

| Requirement | Task | Evidence |
| --- | --- | --- |
| Solarized light semantic allocation | TASK-001 | PASS — CSS mapping + browser computed-token audit |
| Solarized dark semantic allocation | TASK-001 | PASS — CSS mapping + browser computed-token audit |
| Canonical 16-color palette | TASK-001 | PASS — official values locked by regression test |
| Complete localhost:9002 visual regression | TASK-001 | PASS — type checks, build, focused/full-scope tests, browser audit |
| Public Design ownership in packages/design-system | TASK-002 | PASS — package export, README contract, token interface, package-level test |
| Accessible Light/Dark semantic mappings | TASK-002 | PASS — contrast assertions for text, controls, primary action and status marks |
| Shared chart and status semantics | TASK-002, TASK-003 | PASS — stable 8-series chart adapter, semantic status surfaces and terminal badges |
| Console and Management public consumption | TASK-002, TASK-003 | PASS — public import, shared variants and production CSS assertions |
| Code, Markdown, controls, and feedback states | TASK-003 | PASS — component consumer contract plus focused app tests |
| Dual-app visual and build validation | TASK-003 | PASS — type checks, tests, builds and Light/Dark browser audits |
| Renderer runtime palette bridge and CSS variable resolution | TASK-004 | PASS — public palette/helper, no-DOM fallback test and package contract |
| First-party runtime color literal boundary | TASK-004, TASK-005, TASK-006, TASK-007 | PASS — scanner rejects non-canonical literals and records external/vendor/data exceptions |
| Console semantic consumers and ECharts | TASK-005 | PASS — Console type-check/test/build and computed-token chart assertions |
| Console Monaco and Markdown | TASK-005 | PASS — Monaco semantic mapping test and Markdown token scan |
| Console UserStats/layout header/glow consumers | TASK-005 | PASS — tokenized shadow/glow and no legacy HSL wrapper |
| Console Sidebar/ProblemListAnalytics/Markdown legacy HSL wrappers | TASK-005 | PASS — valid computed CSS declarations and semantic-token assertions |
| Console external rating/community/tag data exceptions | TASK-005 | PASS — explicit scanner exception boundary; canonical fallback remains token-based |
| Console Landing CSS/WebGL/theme | TASK-007 | PASS — Landing tests plus Light/Dark/Reduced Motion browser evidence |
| Management semantic consumers and ECharts/SVG | TASK-006 | PASS — Management type-check/test/i18n/build and chart/editor assertions |
| Management ChartContainer SVG selector seam | TASK-006 | PASS — no old HEX attribute selectors; token-driven structural rule |
| Management problem-list editor controls and local variable hierarchy | TASK-006 | PASS — Light/Dark computed token and button/switch checks |
| Public placeholder/PWA metadata | TASK-005, TASK-006 | PASS — canonical static asset/config scan |
| Landing renderer defaults (fog, particles, MSDF, clear color) | TASK-007 | PASS — canonical bridge defaults and no visible black/white/random fallback |
| Landing alpha-0 transparent render-target sentinel | TASK-007 | PASS — documented non-visible renderer exception |
| Third-party Google OAuth/vendor bundle/shader math exceptions | TASK-004, TASK-005, TASK-007 | PASS — scanner allowlist and ownership documentation |
| State/icon/text redundancy and accessibility | TASK-008 | PASS — contrast, keyboard, 200% zoom, reduced motion and color-vision smoke audit |
| Required screenshot route matrix and no-console-error regression | TASK-008 | PASS — Light/Dark screenshots and browser console-error audit |
| Final review, rollback boundary, and local delivery authority | TASK-009 | PASS — formal review, diff check, complete handoff without commit/push |
| Console header/nav/search/notification shared baseline | TASK-010 | PASS — semantic surfaces, rounded geometry, popover shadow, keyboard focus and Light/Dark browser smoke verified |
| Shared outline/secondary hover feedback in both Solarized modes | TASK-010 | PASS — border-control/primary hover markers plus resolved-token regression assertions |
| Console top-level nav active parity across nested routes | TASK-011 | PASS — /problemset, /forum and /contest path-segment matcher; 16-test consumer contract and type-check |
| Console/Management language menu public baseline and emoji-free locale markers | TASK-012 | PASS — shared Solarized surfaces, ZH/EN semantic markers, browser smoke, contracts, type-checks and builds |
| Notification Delivery candidate ownership and intent seam | NOTIFY-001 | PASS — App sole Owner and no-fourth-service boundary confirmed; sealed intent/channel/port contract matrix, idempotency and payload-redaction assertions pass; focused Notification/Inbox/Outbox suite 90 tests pass |
| Durable notification publication and inbox idempotency | NOTIFY-002 | PASS — Notification/consumer/dispatcher focused suite 51 tests; InboxConsumer/transaction/outbox/ledger integration suites 17 tests with MySQL Testcontainers; Submission result outbox tests 7; diff check and changed-file review pass |
| Delivery ledger reclaim, fencing and bounded retry | NOTIFY-003 | PASS — real MySQL ledger IT (stale claim reclaim, concurrent claim single winner, owner fencing, terminal states, reaper releasing durable CLAIMED) and real Redis bridge IT (staging/group-ACK, duplicate eventId dedup, unavailable tolerance); 63-test focused suite and 103-test Notification/Inbox/Outbox suite pass |
| Notification channel failure isolation | NOTIFY-003 | PASS — SMTP/WebSocket/in_app adapter failure-isolation and sensitive-payload contract tests pass; durable retry logs/persists class-only reasons; no token/cookie/password/hidden testcase in intent, ledger or logs |
| Independent App Notification worker role | NOTIFY-004 | PASS — ulticode.notification.worker.enabled gate + api/worker profiles; bridge/reaper ConditionalOnProperty 3-state contract test, reaper metric/failure-containment test, ledger-lag query + real MySQL IT, App boot test; lease/CAS multi-replica safety preserved with no second writer |
| Shared baseline and App Contest migration compatibility | CONTEST-011 | PASS — conditional DDL converges fields/indexes/FK across shared full and App lightweight baselines; both Flyway runs, replay, field assertions, rollback guard, validate and diff check pass |
| Notification phase terminal review and no backend-notification claim | NOTIFY-005 | PASS — `test.sh quick` passes after Contest migration repair; App Notification worker remains App-owned and no backend-notification was created |

## Submission / Search microservice extraction

| Requirement | Task | Evidence |
| --- | --- | --- |
| Submission/Search shared envelope, owner values, event versions and sensitive-payload contract | SPLIT-001 | PASS — `IntegrationEventEnvelopeContract` is reused by both event contracts; nested map/list redaction and top-level compatibility tests pass; app-api ArchUnit passes |
| Submission has a real network seam and deep existing port | SPLIT-001, SPLIT-002 | PASS for runtime seam — graph/source inventory, independent Submission provider, App local/remote single-writer routing and boot/route tests; storage ownership remains SPLIT-003 |
| Submission aggregate and judge/result outboxes have one Owner/writer | SPLIT-003, SPLIT-004 | PARTIAL — submission/judge/result/created outboxes now have one backend-submission writer; local/remote routing, real-MySQL cutover+rollback, App grant revocation, contest command/inbox, focused suites, and owner-grant 6/6 pass; compatibility adapters remain intentionally subject to the SPLIT-005 runtime retirement gate |
| Submission + judge outbox and verdict fence + result outbox stay locally strong-consistent | SPLIT-003 | PASS for the implemented writer path — real-MySQL writer 5/5, cutover 3/3, dispatcher MySQL+Redis 5/5, focused contract/boot/provider suites pass; crash-window behavior remains represented by outbox/PEL replay and final gate review |
| Judge remains an independent stateless sandbox worker | SPLIT-002, SPLIT-004 | PARTIAL — dependency tree excludes `backend-app-web`, runtime has no HTTP/business tables, Streams atomic enqueue and bounded PEL/DLQ tests pass; final services-wide owner/runtime gate remains SPLIT-005 |
| Judge Streams enqueue/retry failure safety | SPLIT-002 | PASS — Lua `SET NX + XADD` rollback path, bounded delivery attempts, `judge:{judge-stream}:dlq`, ACK ordering, metric/config and focused runtime tests |
| App/Admin/Contest/Notification consumers use typed contract/event/inbox, not cross-service SQL | SPLIT-001, SPLIT-004 | PARTIAL — shared contracts/ArchUnit, App remote read routing, Admin read-group seam, and Contest SubmissionCreated inbox are covered; judged-before-association retries; ranking/moderation/Notification physical ownership remains explicitly out of scope |
| Search source aggregates remain App/Auth-owned; Search worker owns MeiliSearch writes | DEC-011, SEARCH-001, SEARCH-002 | PASS — source writers publish typed events; backend-search owns the allowlisted MeiliSearch write path; SEARCH-003 backfill/version/tombstone/replay and E2E-stub evidence complete |
| Problem/User/Forum/Solution changes publish safe SearchDocumentChanged events | SEARCH-001 | PARTIAL — App 三源（Problem/Forum/Solution）全部 owner writer 已发布（slice-a：service/admin provider/import/moderation/publish 语义 + Forum wiring 测试 + 发布矩阵）；User 源 slice-b：auth outbox（V20260816170000）+ Auth 三写路径 hook + Auth dispatcher（属性门控 XADD）+ App profile 写路径 hook；用户文档 id/username + name/avatar；Auth 事件链路 IT 待 SEARCH-002 消费方验证 |
| Search worker is no HTTP/no business DB and is at-least-once/idempotent | SEARCH-002 | PARTIAL — services/search/ 模块已建（无 web/无业务表，PEL claim/reclaim + XACK-after-write + DLQ + allowlist + metrics），单测 7/7 + boot 契约 + compose 双 PASS；真实 Redis+Meili E2E 受无外网阻塞（gap），SEARCH-003 backfill/watermark 未完成 |
| Four index backfills, delete/replay, watermark and existing `/search` compatibility | SEARCH-003 | PASS — 四切片全 done（a4be5ac58/f5c5e058e/bd2c42291/d09411d19）；backfill 一致性（枚举谓词与 Q-read 一致、watermark/版本/tombstone、重跑幂等）、/search 兼容回归、唯一写者静态扫描、E2E IT（真实 Redis+Meili stub）+ 可观测计数；真实 Meili 镜像 E2E 为外部 gap |
| No new broker, no speculative Contest ranking/Moderation/Notification physical split | SPLIT-001, SPLIT-005 | PASS — explicit out-of-scope and DEC-011 alternatives; final gate still pending |
| Final route, DB grant, Compose, security, concurrency and rollback gate | SPLIT-005, SPLIT-005-env-quick, SPLIT-005-env-sandbox, SPLIT-005-retirement-authority | PENDING — quick isolation is PASS; sandbox namespace focused IT reports 6 explicit skips but the services-wide IT run is blocked in Testcontainers MySQL startup and real image coverage remains unavailable; compatibility/grant retirement remains blocked pending release authority |
| Official quick remains runnable with stale persistent MySQL credentials | SPLIT-005-env-quick | PASS — root probe detected existing `ulticode-mysql` ERROR 1045; local `mysql:8.0` disposable container on loopback port 40526 migrated 85 versions and official quick passed; persistent container/volume untouched |
| Sandbox namespace IT reports missing-image prerequisites honestly | SPLIT-005-env-sandbox | PARTIAL — focused reactor IT passes with 6/6 explicit skips and unchanged namespace assertions; services-wide `*IT` could not finish because Testcontainers MySQL `waitUntilContainerStarted` remained blocked in the Docker environment, so real sandbox coverage is still external |
| Submission aggregate and judge/result outboxes have one Owner/writer | SPLIT-003, SPLIT-004 | PARTIAL — target schema/grants, real-MySQL cutover checksum, App grant revocation, remote+local writer routing, and contest event handoff are evidenced; compatibility retirement remains the SPLIT-005 gate |
| Submission + judge outbox and verdict fence + result outbox stay locally strong-consistent | SPLIT-003 | PASS for transaction/fence/outbox and dispatcher evidence; isolated services `test`/`verify` pass; final SPLIT-005 remains open for environment and compatibility retirement gates |
