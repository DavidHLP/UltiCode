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
| Submission aggregate and judge/result outboxes have one Owner/writer | SPLIT-003, SPLIT-004 | PARTIAL — expand grant/manifest PASS；slice-2 本地写路径+IT 4/4 PASS；slice-3 本地 outbox 消费者+IT 4/4 PASS；slice-4 cutover runbook（真实 MySQL 全链路）+ provider local 模式（IT 3/3）就绪；唯一 writer 生效需 SPLIT-004 读路径迁移后启用 remote+local |
| Submission + judge outbox and verdict fence + result outbox stay locally strong-consistent | SPLIT-003 | PARTIAL — slice-2 IT 验证单事务+fence CAS+result outbox 必写；slice-3 IT 验证 judge outbox → Streams enqueue、result outbox → stream:integration XADD；slice-4 IT 验证 provider local 模式直写 submission schema + fence acquireLease/renewLease/stale 拒绝；cutover 后 crash-window 证据仍待 |
| Judge remains an independent stateless sandbox worker | SPLIT-002, SPLIT-004 | PARTIAL — dependency tree excludes `backend-app-web`, runtime has no HTTP/business tables, Streams atomic enqueue and bounded PEL/DLQ tests pass; full owner-consumer proof remains SPLIT-004 |
| Judge Streams enqueue/retry failure safety | SPLIT-002 | PASS — Lua `SET NX + XADD` rollback path, bounded delivery attempts, `judge:{judge-stream}:dlq`, ACK ordering, metric/config and focused runtime tests |
| App/Admin/Contest/Notification consumers use typed contract/event/inbox, not cross-service SQL | SPLIT-001, SPLIT-004 | PARTIAL — SPLIT-001 shared envelope/owner/redaction contract tests and app-api ArchUnit pass; runtime caller/inbox proof remains SPLIT-004 |
| Search source aggregates remain App/Auth-owned; Search worker owns MeiliSearch writes | DEC-011, SEARCH-001, SEARCH-002 | PENDING — owner matrix, event producer and worker-only dependency evidence |
| Problem/User/Forum/Solution changes publish safe SearchDocumentChanged events | SEARCH-001 | PENDING — four writer-path tests and sensitive-field redaction regressions |
| Search worker is no HTTP/no business DB and is at-least-once/idempotent | SEARCH-002 | PENDING — Redis Streams + MeiliSearch integration and boot/config checks |
| Four index backfills, delete/replay, watermark and existing `/search` compatibility | SEARCH-003 | PENDING — backfill/dual-read/replay and controller/projection contract evidence |
| No new broker, no speculative Contest ranking/Moderation/Notification physical split | SPLIT-001, SPLIT-005 | PASS — explicit out-of-scope and DEC-011 alternatives; final gate still pending |
| Final route, DB grant, Compose, security, concurrency and rollback gate | SPLIT-005 | PENDING — focused/module/integration/full verification and formal review |
| Submission aggregate and judge/result outboxes have one Owner/writer | SPLIT-003, SPLIT-004 | PARTIAL — expand slice 1: target-state submission schema + flyway-submission.conf + submission_rw grant + manifest/docs synced; writer cutover, backfill and grant revocation remain |
| Submission + judge outbox and verdict fence + result outbox stay locally strong-consistent | SPLIT-003 | PENDING — MySQL transaction/crash-window/fence integration tests remain after writer migration |
