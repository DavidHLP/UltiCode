# Phase 12: Judge Worker - Context

**Gathered:** 2026-04-18
**Status:** Ready for planning

<domain>
## Phase Boundary

Implement the Judge Worker that consumes jobs from the Redis `judge_queue`, executes all test cases via the existing CodeExecutionService Docker sandbox, writes verdicts to Submission entities, and pushes results via WebSocket. Fix the language support mismatch (13 accepted → 5 supported). Add actual memory measurement to replace hardcoded "0KB".

**Scope:**
- Judge Worker: polling consumer for Redis judge_queue → CodeExecutionService → write verdict
- Language fix: restrict SubmissionServiceImpl validation to the 5 sandbox-supported languages
- Memory measurement: capture actual memory usage via cgroup v2 inside Docker container
- Result notification: trigger existing WebSocket `submission_result` event after judging

**Out of scope:**
- WebSocket infrastructure changes (already complete)
- Contest-specific judging logic (Phase 13-14)
- Additional language support (Go, Rust, etc. — deferred to v2)
- Frontend submission UI changes (already driven by problem_languages per problem)

</domain>

<decisions>
## Implementation Decisions

### Worker Architecture
- **D-01:** Use `@Scheduled(fixedDelay)` polling loop — aligns with existing `QueueConfig.pollInterval` (1000ms default)
- **D-02:** Implement as `JudgeWorkerProcessor` implementing existing `JobProcessor<JudgeJob>` interface
- **D-03:** Single-threaded polling with configurable concurrency via `QueueConfig.maxConcurrentJobs` (default 10)
- **D-04:** Worker lifecycle managed by Spring `@Component` + `@ConditionalOnProperty("queue.judge.enabled")` for easy disable in CI

### Language Fix
- **D-05:** Restrict `SubmissionServiceImpl` validation to match `CodeExecutionService`'s 5 supported languages: `javascript, python, java, c, cpp`
- **D-06:** Frontend language dropdown is already correct — driven by `problem_languages` table per problem, not hardcoded list

### Memory Measurement
- **D-07:** Use cgroup v2 memory stats — read `/sys/fs/cgroup/memory.current` inside container after execution completes
- **D-08:** Add memory capture to the per-test-case wrapper scripts (each language wrapper reports peak memory)
- **D-09:** Return memory as numeric MB value (not string "0KB") — aligns with `Submission.memory` (Double) and `TestCaseDetail.memory` (Double)

### Test Case Execution
- **D-10:** Reuse existing `CodeExecutionService.executeBatch()` for multi-test-case problems — already implements compile-once, run-many pattern
- **D-11:** Map batch results to verdict: all pass → Accepted, first fail determines verdict (WA/TLE/MLE/RE)

### Result Notification
- **D-12:** After writing verdict to Submission entity, push `submission_result` event via existing `SimpMessagingTemplate` to `/user/{userId}/queue/submission`
- **D-13:** Payload matches existing frontend `SubmissionResultPayload`: `{ submissionId, problemId, problemSlug, status, runtime, memory }`

### Retry & Error Handling
- **D-14:** Use existing `JudgeJob.maxRetries` (3) with exponential backoff (2s → 4s → 8s)
- **D-15:** On permanent failure (all retries exhausted), mark submission as "System Error" with error detail in notes
- **D-16:** Compile errors are NOT retried — immediate "Compile Error" verdict

### Verdict Logic
- **D-17:** Verdict priority: RE > MLE > TLE > WA > PE > Accepted — first failing test case determines final verdict
- **D-18:** Runtime = max across all test cases; Memory = max across all test cases
- **D-19:** Status transitions: Pending → Judging → [final verdict]

### Claude's Discretion
- Exact `@Scheduled` parameters (initial delay, fixed delay tuning)
- Logger levels and structured log format for worker events
- Whether to use `@Async` for the actual execution within the worker
- Unit test structure and mock boundaries

### Folded Todos
None — no pending todos matched this phase.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Judge Queue Infrastructure
- `backend-spring/src/main/java/com/ulticode/modules/queue/constants/QueueConstants.java` — Queue name, priority enum, job status enum
- `backend-spring/src/main/java/com/ulticode/modules/queue/config/QueueConfig.java` — Redisson queue beans, poll interval, max concurrent jobs
- `backend-spring/src/main/java/com/ulticode/modules/queue/service/QueueService.java` — Queue interface (enqueue, poll, updateStatus, retry)
- `backend-spring/src/main/java/com/ulticode/modules/queue/service/impl/QueueServiceImpl.java` — Full Redisson queue implementation
- `backend-spring/src/main/java/com/ulticode/modules/queue/job/JudgeJob.java` — Job data class with fields and factory method
- `backend-spring/src/main/java/com/ulticode/modules/queue/job/JobProcessor.java` — Generic processor interface to implement

### Code Execution (Sandbox)
- `backend-spring/src/main/java/com/ulticode/modules/submission/service/CodeExecutionService.java` — Docker sandbox execution (618 lines), batch mode, language wrappers
- `backend-spring/src/main/java/com/ulticode/modules/submission/config/DockerSandboxConfig.java` — Sandbox config properties

### Submission Module
- `backend-spring/src/main/java/com/ulticode/modules/submission/entity/Submission.java` — Entity with status, runtime, memory, testDetails fields
- `backend-spring/src/main/java/com/ulticode/modules/submission/service/impl/SubmissionServiceImpl.java` — submit(), updateSubmissionResult(), language validation (13 languages — TO FIX)
- `backend-spring/src/main/java/com/ulticode/modules/submission/service/SubmissionService.java` — Service interface
- `backend-spring/src/main/java/com/ulticode/modules/submission/mapper/SubmissionMapper.java` — MyBatis mapper

### WebSocket (Result Push)
- `backend-spring/src/main/java/com/ulticode/modules/websocket/constants/WebSocketConstants.java` — EVENT_SUBMISSION_RESULT, queue destinations
- `backend-spring/src/main/java/com/ulticode/modules/websocket/config/WebSocketConfig.java` — STOMP/SockJS broker config
- `backend-spring/src/main/java/com/ulticode/modules/websocket/notification/NotificationWebSocketHandler.java` — SimpMessagingTemplate usage pattern

### Frontend (Already Correct)
- `console/src/lib/socket.ts` — NotificationEvent.SUBMISSION_RESULT, SubmissionResultPayload type
- `console/src/composables/useSocket.ts` — onSubmissionResult() listener
- `console/src/views/problems/submissions/SubmissionsView.vue` — WebSocket subscription for result refresh
- `console/src/api/submission.ts` — API client (no changes needed)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **QueueService**: Full Redisson queue implementation with enqueue/poll/status tracking — Judge Worker just needs to consume from it
- **CodeExecutionService.executeBatch()**: Already implements compile-once, run-many with per-case timeout, JSON result parsing — Judge Worker calls this
- **JudgeJob**: Complete data class with all needed fields (submissionId, problemId, language, code, timeLimit, memoryLimit)
- **JobProcessor<T>**: Generic interface ready for JudgeWorkerProcessor to implement
- **SubmissionServiceImpl.updateSubmissionResult()**: Already exists — updates status, runtime, memory, testDetails
- **WebSocket infrastructure**: SimpMessagingTemplate, submission_result event, frontend listener — all wired, just needs backend trigger

### Established Patterns
- **Queue pattern**: `QueueService.enqueueJudgeJob()` in SubmissionServiceImpl.submit() — enqueue on submit, consumer processes async
- **Docker sandbox security**: `--network none --cap-drop ALL --read-only --memory 256m --pids-limit 128 --seccomp` — keep this for judge execution
- **Batch execution**: JSON array of test cases via stdin → JSON array of results via stdout — CodeExecutionService already handles this
- **Status tracking**: QueueConfig.enableStatusTracking with TTL on `queue:job:{jobId}` Redis keys

### Integration Points
- **Submit flow**: `SubmissionServiceImpl.submit()` → `queueService.enqueueJudgeJob()` — already enqueues, just no consumer
- **Result write**: `SubmissionServiceImpl.updateSubmissionResult()` — called by worker after judging
- **WebSocket push**: `SimpMessagingTemplate.convertAndSendToUser()` — used by existing handlers, same pattern for submission_result
- **Language validation**: `SubmissionServiceImpl.submit()` line ~83 — hardcoded list of 13, needs reduction to 5

### Critical Gaps
- **No JobProcessor<JudgeJob> implementation** — the entire worker is missing
- **Memory always "0KB"** — hardcoded in CodeExecutionService.emptyResult() and buildCaseResult()
- **Language validation accepts 8 unsupported languages** — typescript, go, rust, csharp, php, ruby, swift, kotlin

</code_context>

<specifics>
## Specific Ideas

No specific requirements — standard LeetCode-style judge worker implementation.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 12-judge-worker*
*Context gathered: 2026-04-18*
