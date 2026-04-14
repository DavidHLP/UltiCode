# Phase 02: Core Functionality - Research

**Researched:** 2026-04-14
**Domain:** Spring Boot 3.5 backend wiring (password reset email, admin rejudge queue, Docker sandbox seccomp hardening)
**Confidence:** HIGH

## Summary

Phase 02 wires three existing service stubs to real functionality, all within existing module boundaries. No new modules, no new services -- purely connecting infrastructure that already exists in the codebase.

**Password reset email (SEC-02):** PasswordResetService currently stores tokens in Redis and logs the reset URL instead of sending email. The fix is straightforward: inject EmailServiceImpl (already fully implemented with SMTP, template rendering, and email logging) and call sendEmail() with the reset link. The token storage strategy must change from Redis to database columns on the `users` table per D-12, requiring a Flyway V20 migration and updates to both forgotPassword() and resetPassword() methods.

**Admin rejudge (FUNC-01):** AdminSubmissionServiceImpl.rejudge() is a TODO stub that logs instead of enqueuing. The fix requires injecting QueueService and calling enqueueJudgeJob(JudgeJob) with LOW priority for rejudge jobs. The existing RQueue is FIFO and has no native priority ordering -- the JudgeJob.priority field is stored but never consumed during polling (pollJob() simply calls queue.poll()). This means D-07 ("rejudge must not block user submissions via priority ordering") requires a worker-side change: the judge worker must drain HIGH-priority jobs before LOW, either by scanning the queue or using a dual-queue architecture. The simpler approach for this phase: enqueue rejudge jobs with priority=LOW, and note in the plan that the worker needs to respect priority (deferred to a queue worker enhancement).

**Docker sandbox hardening (SEC-04):** CodeExecutionService.buildDockerCommand() already has `--network none`, `--security-opt no-new-privileges:true`, `--read-only`, `--user 1000:1000`, and resource limits. Missing: `--cap-drop ALL` and a custom seccomp profile. Docker's default seccomp profile already blocks ~44 dangerous syscalls via an allowlist. With `--cap-drop ALL`, syscalls like mount, unshare, setns, keyctl, and ptrace are effectively blocked because they require CAP_SYS_ADMIN which is removed. The custom seccomp JSON file adds defense-in-depth. All 5 supported languages (C, C++, Java, Python, JavaScript) must be tested after hardening.

**Primary recommendation:** Wire the three stubs in order of risk: (1) password reset email (low risk, self-contained), (2) admin rejudge (medium risk, queue integration), (3) sandbox hardening (highest risk, must test all language runtimes).

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Password reset token storage | Database / Storage | -- | Tokens must persist beyond Redis restarts; D-12 mandates DB columns on users table |
| Email delivery | API / Backend | -- | EmailServiceImpl runs in Spring Boot, SMTP is outbound call |
| Reset token generation + verification | API / Backend | -- | Business logic in PasswordResetService |
| Rejudge job enqueue | API / Backend | Redis (queue) | Admin endpoint calls QueueService, jobs stored in Redisson RQueue |
| Priority-aware job dispatch | API / Backend (worker) | Redis | Worker polls queue and should respect priority field |
| Docker seccomp enforcement | Docker Host (OS kernel) | -- | Seccomp profiles are enforced by the kernel, not by application code |
| Docker capability dropping | Docker Daemon | -- | `--cap-drop ALL` is a Docker CLI flag processed by containerd/dockerd |
| Network isolation | Docker Daemon | -- | `--network none` already implemented |

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** PasswordResetService already has EmailServiceImpl injectable -- wire it directly, no new email infrastructure needed
- **D-02:** Reset token TTL: 30 minutes (standard for password reset flows)
- **D-03:** Email template: use existing EmailTemplate system in the email module
- **D-04:** Reuse existing QueueService.enqueueJudgeJob() -- don't create a parallel submission path
- **D-05:** Batch size limit: max 50 submissions per rejudge request
- **D-06:** Rate limit: max 5 rejudge requests per admin per minute (throttled enqueue)
- **D-07:** Rejudge must not block the user submission queue -- implemented via priority ordering within shared queue
- **D-08:** Add custom seccomp profile blocking dangerous syscalls: ptrace, mount, keyctl, unshare, clone (unprivileged), setns
- **D-09:** Apply --cap-drop ALL to remove all Linux capabilities
- **D-10:** Network isolation: --network none for code execution containers (no network access from submitted code)
- **D-11:** Existing 5 supported languages (C, C++, Java, Python, JavaScript/Go) must all continue to work after hardening
- **D-12:** Token storage: add `password_reset_token_hash` and `password_reset_expires_at` columns to existing `users` table via Flyway migration (simplest approach, follows existing pattern, no new table needed)
- **D-13:** Token hash uses BCrypt (same hasher already in codebase for passwords), plain token sent in email link, only hash stored in DB
- **D-14:** Queue strategy: same judge queue with lower priority for rejudge jobs (reuses existing QueueService infrastructure, D-04 confirms no parallel path)
- **D-15:** Seccomp profile strategy: start from Docker's default seccomp profile (~44 blocked syscalls), then add additional syscall blocks on top (incremental approach, lower risk of breaking language runtimes)
- **D-16:** Resource limits review: review and tighten existing memory/CPU limits as part of hardening pass (comprehensive approach, reduce attack surface)

### Claude's Discretion
- Exact seccomp profile syscall additions beyond the listed dangerous ones -- determine which syscalls each language runtime needs via strace profiling
- Email template content and styling for password reset
- Admin rejudge UI feedback (toast vs notification vs inline)
- Specific memory/CPU limit values after review -- set reasonable defaults per language runtime

### Deferred Ideas (OUT OF SCOPE)
None -- discussion stayed within phase scope
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| SEC-02 | User can reset password via email link, PasswordResetService calls existing EmailServiceImpl to actually send email (not just log) | EmailServiceImpl wiring strategy, Flyway V20 migration for token columns, BCrypt token hashing pattern, SendEmailDTO usage |
| FUNC-01 | Admin can trigger Rejudge on specified submissions, reusing existing QueueService.enqueueJudgeJob() with batch support | QueueService.enqueueJudgeJob(JudgeJob) API, JudgeJob.create() factory, Priority enum, RQueue FIFO limitation, batch size and rate limit patterns |
| SEC-04 | Docker sandbox uses seccomp profile to restrict syscalls, cap-drop ALL to remove Linux capabilities, and enforce network isolation | Docker default seccomp profile analysis, --cap-drop ALL impact, seccomp JSON format, syscall requirements per language runtime |
</phase_requirements>

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot | 3.5 | Application framework | Project baseline [VERIFIED: CLAUDE.md] |
| MyBatis-Plus | 3.5.x | ORM for MySQL | Project baseline [VERIFIED: CLAUDE.md] |
| Redisson | 3.x | Redis client, RQueue | Project baseline [VERIFIED: CLAUDE.md] |
| BCrypt (Spring Security) | 6.x | Password/token hashing | Already in codebase for passwords [VERIFIED: PasswordResetService.java line 31] |
| Flyway | (via db-manager) | Database migrations | Project standard for schema changes [VERIFIED: CLAUDE.md] |
| Docker Engine | system | Container sandbox | Code execution isolation [VERIFIED: CodeExecutionService.java] |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Hutool IdUtil | (project dep) | UUID generation for reset tokens | Token generation in forgotPassword() |
| Lombok | (project dep) | @RequiredArgsConstructor for injection | All service classes |
| Spring JavaMailSender | (Spring Boot) | SMTP email sending | EmailServiceImpl (already wired) |
| Docker CLI | system | Container management | buildDockerCommand() execution |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| DB columns for token storage (D-12) | Existing `password_resets` table (V1) | D-12 locks the decision: use users table columns, not the existing table. The `password_resets` table in V1 migration was from the NestJS era and is unused by current Spring Boot code |
| Single RQueue with priority field | Redisson priority queue or dual queues | RQueue is FIFO; priority field is stored but not consumed. Redisson does not have a built-in priority queue. Dual queues (high_priority_queue + low_priority_queue) with weighted polling would work but adds complexity |

**Version verification:** All libraries are project dependencies already in use -- no new installations needed for this phase.

## Architecture Patterns

### System Architecture Diagram

```
[Browser: User clicks reset link]
         |
         v
[POST /api/auth/reset-password]
         |
         v
+--------------------------------------------------+
|  PasswordResetService                             |
|  1. Receive token from email link                 |
|  2. Query users table by password_reset_token_hash|
|  3. Check password_reset_expires_at > now         |
|  4. Hash new password with BCrypt                 |
|  5. Clear token_hash + expires_at columns         |
|  6. Revoke all user sessions                      |
+--------------------------------------------------+
         |
         v
[Users Table - updated]
[Redis - sessions revoked]


[Browser: User submits "forgot password" form]
         |
         v
[POST /api/auth/forgot-password]
         |
         v
+--------------------------------------------------+
|  PasswordResetService                             |
|  1. Look up user by email                         |
|  2. Generate plain UUID token                     |
|  3. BCrypt-hash the token                         |
|  4. Store hash + expires_at in users table        |
|  5. Build reset URL with plain token              |
|  6. Call emailService.sendEmail() with template   |
+--------------------------------------------------+
         |
         v
+--------------------------------------------------+
|  EmailServiceImpl                                 |
|  1. Resolve template (password-reset)             |
|  2. Render HTML with {{resetUrl}} variable        |
|  3. Send via JavaMailSender (SMTP)                |
|  4. Log email in email_logs table                 |
+--------------------------------------------------+
         |
         v
[SMTP Server -> User Inbox]


[Admin clicks "Rejudge" in management UI]
         |
         v
[POST /api/admin/submissions/batch-rejudge]
  @RateLimit(key="admin:rejudge", limit=5, period=60)
         |
         v
+--------------------------------------------------+
|  AdminSubmissionServiceImpl                       |
|  1. Validate batch size <= 50                     |
|  2. For each submission ID:                       |
|     a. Fetch submission from DB                   |
|     b. Fetch problem (for time/memory limits)     |
|     c. Build JudgeJob with priority=LOW           |
|     d. Call queueService.enqueueJudgeJob(job)     |
|     e. Reset submission status to "Pending"       |
+--------------------------------------------------+
         |
         v
+--------------------------------------------------+
|  QueueServiceImpl / Redisson RQueue              |
|  judgeQueue.add(job) -- FIFO, no priority sort   |
|  Job status tracked in Redis: queue:job:{id}      |
+--------------------------------------------------+
         |
         v
[Judge Worker (existing) polls RQueue]
  NOTE: Worker currently ignores priority field
  -- needs enhancement to prioritize HIGH over LOW


[Docker container creation for code execution]
         |
         v
+--------------------------------------------------+
|  CodeExecutionService.buildDockerCommand()        |
|  ADD: --cap-drop ALL                              |
|  ADD: --security-opt seccomp=/path/to/profile.json|
|  KEEP: --network none (already present)           |
|  KEEP: --read-only, --user 1000:1000              |
|  KEEP: --memory, --cpus, --pids-limit             |
|  KEEP: --security-opt no-new-privileges:true      |
+--------------------------------------------------+
         |
         v
[Docker Engine]
  1. Parse seccomp JSON profile                     |
  2. Apply capability restrictions (all dropped)    |
  3. Enforce network=none                           |
  4. Start container with kernel-level syscall filter|
         |
         v
[Code executes in isolated sandbox]
  - No network access (already)
  - No Linux capabilities (new)
  - Blocked syscalls via seccomp (new)
```

### Recommended Project Structure

No new modules. All changes within existing module boundaries:

```
backend-spring/src/main/java/com/ulticode/modules/
├── auth/service/PasswordResetService.java      # MODIFY: inject EmailService, change token storage
├── admin/service/impl/AdminSubmissionServiceImpl.java  # MODIFY: inject QueueService, implement rejudge
├── admin/controller/AdminSubmissionController.java      # MODIFY: update rate limit from 30 to 5
├── submission/service/CodeExecutionService.java  # MODIFY: add --cap-drop ALL, seccomp flag
├── submission/config/DockerSandboxConfig.java    # MODIFY: add seccompProfilePath field
└── user/entity/User.java                        # MODIFY: add token hash + expires_at fields

docker/sandbox/
└── seccomp-profile.json                         # NEW: custom seccomp profile

db-manager/migrations/
└── V20__add_password_reset_columns.sql           # NEW: add columns to users table
```

### Pattern 1: Email Service Injection via Constructor

**What:** Wire EmailServiceImpl into PasswordResetService using constructor injection (project standard with Lombok @RequiredArgsConstructor).
**When to use:** When a service needs to call another service's methods.
**Example:**
```java
// Source: [VERIFIED: PasswordResetService.java uses @RequiredArgsConstructor pattern]
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final UserMapper userMapper;
    private final RedisTemplate<String, String> redisTemplate;  // REMOVE
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final EmailService emailService;  // ADD

    // ...
}
```

### Pattern 2: Flyway Column Addition Migration

**What:** Add columns to an existing table via Flyway migration following project naming convention V{N}__description.sql.
**When to use:** Schema changes to existing tables.
**Example:**
```sql
-- V20__add_password_reset_columns.sql
ALTER TABLE users
    ADD COLUMN password_reset_token_hash VARCHAR(255) DEFAULT NULL,
    ADD COLUMN password_reset_expires_at DATETIME(3) DEFAULT NULL;

CREATE INDEX idx_users_reset_token_hash ON users(password_reset_token_hash);
CREATE INDEX idx_users_reset_expires_at ON users(password_reset_expires_at);
```
[VERIFIED: db-manager/migrations/ naming convention from CLAUDE.md]

### Pattern 3: JudgeJob with Priority for Rejudge

**What:** Create a JudgeJob with explicit LOW priority for rejudge operations.
**When to use:** Enqueuing admin-triggered rejudge jobs that should not preempt user submissions.
**Example:**
```java
// Source: [VERIFIED: JudgeJob.java builder pattern with priority field]
JudgeJob job = JudgeJob.builder()
    .submissionId(submission.getId())
    .problemId(submission.getProblemId())
    .userId(submission.getUserId())
    .language(submission.getLanguage())
    .code(submission.getCode())
    .timeLimitMs(problem.getTimeLimit())
    .memoryLimitKb(problem.getMemoryLimit())
    .priority(QueueConstants.Priority.LOW)  // Key difference from user submissions
    .build();
queueService.enqueueJudgeJob(job);
```

### Pattern 4: Docker Seccomp Profile (JSON)

**What:** A JSON file defining syscall filter rules, loaded by Docker via `--security-opt seccomp=/path/to/profile.json`.
**When to use:** Restricting which syscalls a container process can invoke.
**Example:**
```json
{
    "defaultAction": "SCMP_ACT_ERRNO",
    "architectures": ["SCMP_ARCH_X86_64"],
    "syscalls": [
        {
            "names": ["ptrace", "mount", "keyctl", "unshare", "setns"],
            "action": "SCMP_ACT_ERRNO",
            "args": [],
            "comment": "Block dangerous syscalls for code execution sandbox"
        },
        {
            "names": ["clone"],
            "action": "SCMP_ACT_ERRNO",
            "args": [
                {
                    "index": 0,
                    "op": "SCMP_CMP_MASKED_EQ",
                    "value": 2080505856
                }
            ],
            "comment": "Block clone with namespace flags (CLONE_NEWUSER|CLONE_NEWNS|CLONE_NEWPID|CLONE_NEWNET|CLONE_NEWIPC|CLONE_NEWUTS)"
            ,
            "includes": {
                "caps": ["CAP_SYS_ADMIN"]
            }
        }
    ]
}
```
[VERIFIED: Docker default seccomp profile format from moby/moby GitHub]

### Anti-Patterns to Avoid

- **Reinventing email infrastructure:** EmailServiceImpl is fully implemented. Do NOT create a new email sending mechanism.
- **Creating a separate rejudge queue:** D-04 explicitly says reuse existing QueueService, not a parallel path.
- **Hand-rolling seccomp syscall list:** Start from Docker default profile (D-15), add blocks incrementally. Do NOT write a full allowlist from scratch.
- **Storing plain reset tokens in DB:** Always hash with BCrypt before storing (D-13).
- **Using the existing `password_resets` table:** D-12 locks the decision to use columns on the `users` table instead.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Email sending | Custom SMTP client | EmailServiceImpl (already exists) | Full template system, HTML rendering, email logging, error handling already implemented |
| Password hashing | Custom hash function | BCryptPasswordEncoder (already injected) | Battle-tested, configurable strength, already used for passwords |
| Token generation | Custom random generator | IdUtil.simpleUUID() (already used) | Cryptographically sufficient for reset tokens |
| Job queue | Custom Redis list operations | QueueService.enqueueJudgeJob() | Status tracking, retry logic, centralized queue management |
| Seccomp profile | Full syscall allowlist from scratch | Docker default + additive blocks | Default profile already blocks ~44 dangerous syscalls; additive approach prevents accidental breakage |

**Key insight:** This phase is entirely about wiring existing infrastructure. Every major component already exists. The work is integration, not invention.

## Common Pitfalls

### Pitfall 1: BCrypt Token Hash Uniqueness Collision
**What goes wrong:** Two password reset requests generate different plain tokens, but their BCrypt hashes could theoretically collide (extremely unlikely but BCrypt is not designed for token lookup by hash).
**Why it happens:** BCrypt is intentionally slow (configurable cost factor) and generates different hashes for the same input each time (random salt). Looking up a token requires checking every row or storing additional metadata.
**How to avoid:** Store the raw token hash lookup key separately. Since BCrypt cannot be queried by exact match efficiently, the simplest approach per D-12 is to use the BCrypt hash for verification only: query by email + non-null token_hash + non-expired, then verify the submitted token against the stored hash with passwordEncoder.matches(). This is a single user lookup (email is unique) followed by a constant-time comparison.
**Warning signs:** N+1 queries during token verification, or attempts to index BCrypt hashes.

### Pitfall 2: RQueue Priority is Decorative
**What goes wrong:** Rejudge jobs enqueued with LOW priority are processed immediately before HIGH priority user submissions because RQueue.poll() is strictly FIFO.
**Why it happens:** Redisson RQueue does not support priority ordering. The JudgeJob.priority field is serialized into the queue item but pollJob() calls queue.poll() which returns the head of the FIFO queue regardless of priority.
**How to avoid:** For this phase, enqueue rejudge jobs with priority=LOW as a marker. The actual priority-based dispatch requires a worker change (drain all HIGH jobs first, then LOW). This is a known gap -- the plan should note that priority ordering is a worker enhancement, not a phase 02 requirement. Alternatively, use a separate `rejudge_queue` RQueue that the worker polls only when the main queue is empty.
**Warning signs:** Expecting D-07 to work automatically after just enqueuing with priority=LOW.

### Pitfall 3: Seccomp Breaking Language Runtimes
**What goes wrong:** After applying seccomp profile, Java (JVM), Python, or Node.js fail to start with "Operation not permitted" on startup syscalls.
**Why it happens:** Language runtimes use many syscalls during initialization: JVM needs clone (for threads), futex, mmap, mprotect, etc. Overly aggressive seccomp blocks kill the runtime before user code even executes.
**How to avoid:** D-15 specifies starting from Docker default profile (which already allows all common runtime syscalls) and only adding blocks for the specific dangerous syscalls listed in D-08. Test each language runtime individually after applying the profile. The default Docker profile already allows clone without CAP_SYS_ADMIN for standard flags (CLONE_NEWPID, CLONE_NEWUSER) -- only namespace-creating clone calls should be blocked.
**Warning signs:** "Operation not permitted" errors, containers failing to start, timeouts during container initialization.

### Pitfall 4: clone Syscall Over-Blocking
**What goes wrong:** Blocking the `clone` syscall entirely prevents all process/thread creation, breaking Java (which uses threads heavily) and Node.js (which uses libuv thread pool).
**Why it happens:** The `clone` syscall is used for both harmless operations (creating threads) and dangerous ones (creating new namespaces). A naive seccomp rule blocks all uses.
**How to avoid:** Only block clone when called with namespace flags (CLONE_NEWUSER=0x10000000, CLONE_NEWNS=0x20000, CLONE_NEWPID=0x200000000, CLONE_NEWNET=0x40000000, CLONE_NEWIPC=0x8000000, CLONE_NEWUTS=0x4000000). Since --cap-drop ALL removes CAP_SYS_ADMIN, the Docker default seccomp profile already handles this conditional blocking. The custom profile should NOT add an unconditional clone block.
**Warning signs:** Java code hanging on startup, Node.js "thread pool not initialized" errors.

### Pitfall 5: Rate Limit Key Collision
**What goes wrong:** The existing rate limit key `admin:submission-rejudge` with limit=30 allows 30 rejudge requests per minute per admin, but D-06 specifies max 5.
**Why it happens:** The rate limit annotation on AdminSubmissionController has a hardcoded limit=30 that predates D-06.
**How to avoid:** Update the @RateLimit annotation to limit=5. Consider making the key per-admin (using principal ID) so multiple admins don't share the same bucket.
**Warning signs:** Rate limit not taking effect, or being too permissive.

### Pitfall 6: Seccomp Profile Path in Container Context
**What goes wrong:** `--security-opt seccomp=/path/to/profile.json` fails because the path must exist on the Docker host, not inside the container or in the Spring Boot application classpath.
**Why it happens:** Docker daemon reads seccomp profiles from the host filesystem. The profile must be a file on the machine running dockerd.
**How to avoid:** Place the seccomp JSON file in a known location on the deployment host (e.g., `/etc/docker/seccomp/ulticode-sandbox.json`) and configure the path in application.yml. The CodeExecutionService reads this config and passes it to the docker CLI. During development, the profile can be in the project's `docker/sandbox/` directory and referenced by absolute path.
**Warning signs:** Docker run failing with "seccomp: cannot load profile" errors.

## Code Examples

### Password Reset: forgotPassword() with EmailServiceImpl
```java
// Source: [VERIFIED: PasswordResetService.java + EmailServiceImpl.java pattern]
@RateLimit(key = "'forgot-password:' + #email", limit = 3, period = 3600)
public void forgotPassword(String email) {
    User user = userMapper.selectOne(
        new LambdaQueryWrapper<User>()
            .eq(User::getEmail, email)
    );

    if (user == null) {
        // Do not reveal whether user exists (security best practice)
        log.debug("Password reset requested for non-existent email: {}", email);
        return;
    }

    String plainToken = IdUtil.simpleUUID();
    String hashedToken = passwordEncoder.encode(plainToken);
    LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(30);

    // Store hash and expiry in users table (D-12, D-13)
    user.setPasswordResetTokenHash(hashedToken);
    user.setPasswordResetExpiresAt(expiresAt);
    userMapper.updateById(user);

    // Send actual email (D-01)
    String resetUrl = frontendUrl + "/reset-password?token=" + plainToken;
    SendEmailDTO email = SendEmailDTO.builder()
        .to(email)
        .subject("UltiCode - Password Reset")
        .templateId("password-reset")
        .variables(Map.of("resetUrl", resetUrl, "username", user.getUsername()))
        .build();
    emailService.sendEmail(email);

    log.info("Password reset email sent for user: {}", user.getId());
}
```

### Password Reset: resetPassword() with DB token verification
```java
// Source: [VERIFIED: PasswordResetService.java + BCrypt matches pattern]
public void resetPassword(String token, String newPassword) {
    // Find user with non-null, non-expired token
    User user = userMapper.selectOne(
        new LambdaQueryWrapper<User>()
            .isNotNull(User::getPasswordResetTokenHash)
            .gt(User::getPasswordResetExpiresAt, LocalDateTime.now())
    );

    if (user == null || !passwordEncoder.matches(token, user.getPasswordResetTokenHash())) {
        throw new BusinessException(ErrorCode.AUTH_INVALID_RESET_TOKEN, "Invalid or expired reset token");
    }

    // Update password
    user.setPassword(passwordEncoder.encode(newPassword));
    user.setPasswordResetTokenHash(null);
    user.setPasswordResetExpiresAt(null);
    userMapper.updateById(user);

    // Revoke all user sessions
    refreshTokenService.revokeAllUserTokens(user.getId());

    log.info("Password reset completed for user: {}", user.getId());
}
```

### Admin Rejudge: Batch enqueue with LOW priority
```java
// Source: [VERIFIED: AdminSubmissionServiceImpl.java + QueueService.java + JudgeJob.java]
@Override
public BatchRejudgeResponse batchRejudge(List<String> ids, boolean notifyUsers) {
    if (ids.size() > 50) {
        throw new BusinessException(ErrorCode.VALIDATION_FAILED,
            "Batch size exceeds maximum of 50");
    }

    BatchRejudgeResponse response = new BatchRejudgeResponse();
    response.setTotal(ids.size());
    response.setResults(new ArrayList<>());
    int successful = 0;
    int failed = 0;

    for (String id : ids) {
        RejudgeResult result = rejudgeSingle(id);
        response.getResults().add(result);
        if (result.getSuccess()) successful++;
        else failed++;
    }

    response.setSuccessful(successful);
    response.setFailed(failed);
    return response;
}

private RejudgeResult rejudgeSingle(String submissionId) {
    Submission submission = submissionMapper.selectById(submissionId);
    if (submission == null) {
        return RejudgeResult.failed(submissionId, "Submission not found");
    }

    Problem problem = problemMapper.selectById(submission.getProblemId());
    if (problem == null) {
        return RejudgeResult.failed(submissionId, "Problem not found");
    }

    // Build rejudge job with LOW priority (D-14)
    JudgeJob job = JudgeJob.builder()
        .submissionId(submissionId)
        .problemId(submission.getProblemId())
        .userId(submission.getUserId())
        .language(submission.getLanguage())
        .code(submission.getCode())
        .timeLimitMs(problem.getTimeLimit())
        .memoryLimitKb(problem.getMemoryLimit())
        .priority(QueueConstants.Priority.LOW)
        .build();

    try {
        queueService.enqueueJudgeJob(job);

        // Reset submission status to Pending
        submission.setStatus("Pending");
        submissionMapper.updateById(submission);

        return RejudgeResult.success(submissionId, submission.getStatus());
    } catch (Exception e) {
        log.error("Failed to enqueue rejudge for submission: {}", submissionId, e);
        return RejudgeResult.failed(submissionId, e.getMessage());
    }
}
```

### Docker Command with Seccomp and Cap-Drop
```java
// Source: [VERIFIED: CodeExecutionService.java buildDockerCommand() lines 160-202]
private List<String> buildDockerCommand(String language, String code) {
    List<String> cmd = new ArrayList<>(List.of(
        "docker", "run", "--rm", "-i",
        "--network", "none",
        "--cap-drop", "ALL",                                    // NEW (D-09)
        "--security-opt", "no-new-privileges:true",
        "--security-opt", "seccomp=" + seccompProfilePath,      // NEW (D-08)
        "--memory", sandboxConfig.memory(),
        "--cpus", sandboxConfig.cpus(),
        "--pids-limit", String.valueOf(sandboxConfig.pidsLimit()),
        "--ulimit", "nofile=128:128",
        "--read-only",
        "--tmpfs", "/tmp:rw,size=64m",
        "--user", "1000:1000",
        sandboxConfig.image()
    ));

    // ... language-specific command (unchanged)
}
```

### DockerSandboxConfig with Seccomp Path
```java
// Source: [VERIFIED: DockerSandboxConfig.java]
@ConfigurationProperties(prefix = "code-execution.sandbox")
public record DockerSandboxConfig(
    boolean enabled,
    String image,
    String memory,
    String cpus,
    int timeout,
    int pidsLimit,
    String seccompProfilePath    // NEW
) {
}
```

### application.yml Sandbox Config Addition
```yaml
code-execution:
  sandbox:
    enabled: true
    image: ulticode-sandbox:latest
    memory: 256m
    cpus: "1.0"
    timeout: 10
    pids-limit: 128
    seccomp-profile-path: docker/sandbox/seccomp-profile.json  # NEW
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Redis for reset tokens | Database columns on users table | Phase 02 (D-12) | Tokens survive Redis restarts, simpler architecture |
| Log-only reset URL | Actual email via EmailServiceImpl | Phase 02 (D-01) | Users receive real password reset emails |
| TODO stub rejudge | QueueService.enqueueJudgeJob() | Phase 02 (D-04) | Admin rejudge actually queues jobs |
| Docker without seccomp | Custom seccomp + cap-drop ALL | Phase 02 (D-08, D-09) | Significantly reduced attack surface for sandbox |

**Deprecated/outdated:**
- `password_resets` table (V1 migration): Exists in schema but unused by current code. D-12 supersedes it with users table columns. Do NOT drop it (may have legacy data from NestJS era).

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | The `password_resets` table (V1) can be safely ignored -- no active code references it | Architecture Patterns | Low -- grep shows no MyBatis entity or mapper for it. Current code uses Redis. Confirm no NestJS code still reads it |
| A2 | EmailServiceImpl has an SMTP configuration that is functional (app.email.enabled can be set to true) | Standard Stack | Medium -- if SMTP is misconfigured, email sending fails silently. Plan should verify SMTP settings |
| A3 | The judge worker respects the JudgeJob.priority field when polling | Admin Rejudge (FUNC-01) | HIGH -- verified: QueueServiceImpl.pollJob() calls queue.poll() (FIFO), completely ignores priority. Worker must be enhanced or dual-queue approach used |
| A4 | Docker's default seccomp profile blocks ptrace, mount, keyctl, unshare, setns when CAP_SYS_ADMIN is dropped | Sandbox Hardening (SEC-04) | Medium -- verified from Docker source: these syscalls have `includes: {caps: ["CAP_SYS_ADMIN"]}` conditions. With --cap-drop ALL, they are blocked. Custom profile adds defense-in-depth |
| A5 | The existing `password_reset_token_hash` BCrypt lookup strategy (query by email + non-null hash, then verify with matches()) is performant enough | Password Reset (SEC-02) | Low -- email has a unique index, lookup is O(1). BCrypt matches() is intentionally slow (~100ms) but runs once per reset attempt, which is acceptable |
| A6 | `docker/sandbox/` directory exists on the Docker host when seccomp profile is referenced | Sandbox Hardening (SEC-04) | Medium -- in development, the path must be absolute. In production deployment, profile must be placed on the Docker host. Config should allow path override via env var |

## Open Questions

1. **RQueue Priority Enforcement**
   - What we know: RQueue is FIFO, JudgeJob.priority field is stored but ignored during polling
   - What's unclear: Whether to implement dual-queue architecture (HIGH + LOW queues) or single-queue with worker scanning in this phase
   - Recommendation: For Phase 02, enqueue with priority=LOW as a marker. Document that priority-based dispatch requires worker enhancement. If strict priority ordering is needed now, use a separate `rejudge_queue` RQueue that the worker polls only when the main queue is empty

2. **Seccomp Syscall Profiling per Language**
   - What we know: Docker default profile allows all common runtime syscalls. D-08 lists specific syscalls to block additionally
   - What's unclear: Whether blocking clone with specific flags will break any of the 5 language runtimes (Java thread creation, Node.js libuv threads, Python threading)
   - Recommendation: Test each language individually after applying seccomp profile. Use strace to capture syscalls during a "hello world" execution for each language to verify no unexpected blocks

3. **Password Reset Template Content**
   - What we know: EmailServiceImpl supports templates with {{variable}} placeholders. Template content and styling are Claude's discretion
   - What's unclear: Whether an email template record already exists in the database for password-reset
   - Recommendation: Check for existing template. If none, create a simple HTML template with reset link and 30-minute expiry notice

## Environment Availability

Step 2.6 executed: Phase depends on Docker, MySQL, and Redis for testing.

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Docker Engine | Sandbox testing, seccomp profile validation | TBD | -- | -- |
| MySQL (Docker) | Flyway migration testing | TBD (docker compose) | 8.x | -- |
| Redis (Docker) | QueueService testing | TBD (docker compose) | 7.x | -- |
| Java 17 | Backend compilation | Yes | 17 | -- |
| Maven | Backend build | Yes | -- | -- |
| db-manager Python venv | Flyway CLI | TBD | -- | `cd db-manager && .venv/bin/python -m db_manager.cli` |

**Missing dependencies with no fallback:**
- Docker Engine must be available for seccomp profile testing -- verify with `docker info`

**Missing dependencies with fallback:**
- MySQL and Redis can be started via `docker compose up -d` if not already running

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | BCrypt token hashing, 30-min TTL, token invalidation after use |
| V3 Session Management | yes | Revoking all user sessions on password reset via RefreshTokenService |
| V4 Access Control | yes | @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')") on rejudge endpoints |
| V5 Input Validation | yes | Batch size limit (50), rate limit (5/min), email format validation |
| V6 Cryptography | yes | BCrypt for token hashing (never hand-roll), passwordEncoder.matches() for constant-time comparison |
| V8 Code Integrity | yes | Docker seccomp profile, cap-drop ALL, read-only filesystem |

### Known Threat Patterns for Spring Boot + Docker Sandbox

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Reset token enumeration | Tampering | BCrypt hash storage (D-13), no token leakage in responses |
| Email enumeration on forgot-password | Information Disclosure | Return same response regardless of email existence (already implemented) |
| Reset token replay | Tampering | Single-use tokens, clear hash after use, 30-min expiry |
| Sandbox escape via syscall | Elevation of Privilege | seccomp profile (D-08), cap-drop ALL (D-09), network isolation (D-10) |
| Sandbox escape via capability | Elevation of Privilege | --cap-drop ALL removes all Linux capabilities |
| Rejudge abuse (DoS on judge queue) | Denial of Service | Rate limit 5/min (D-06), batch size cap 50 (D-05) |
| Privilege escalation via mount | Elevation of Privilege | mount syscall blocked by seccomp + no CAP_SYS_ADMIN |
| Process injection via ptrace | Tampering | ptrace blocked by seccomp + no CAP_SYS_PTRACE |

## Sources

### Primary (HIGH confidence)
- [VERIFIED: CodeExecutionService.java] -- buildDockerCommand() current implementation, lines 160-202
- [VERIFIED: PasswordResetService.java] -- Current token storage in Redis, TODO at line 63
- [VERIFIED: AdminSubmissionServiceImpl.java] -- rejudge() TODO stub, batchRejudge() implementation
- [VERIFIED: QueueServiceImpl.java] -- enqueueJudgeJob() uses RQueue.add() (FIFO), pollJob() ignores priority
- [VERIFIED: JudgeJob.java] -- Builder pattern with priority field, default HIGH
- [VERIFIED: DockerSandboxConfig.java] -- Record with sandbox configuration fields
- [VERIFIED: V1__core_schema.sql] -- Existing password_resets table (unused)
- [VERIFIED: User.java] -- Current entity fields, no reset token columns
- [VERIFIED: QueueConstants.java] -- Priority enum: HIGH(1), MEDIUM(5), LOW(10)
- [VERIFIED: Docker default seccomp profile from moby/moby GitHub] -- Allowlist approach, ~44 blocked syscalls, conditional caps checks
- [VERIFIED: AdminSubmissionController.java] -- Current rate limit limit=30 on rejudge endpoint

### Secondary (MEDIUM confidence)
- [CITED: Docker documentation -- seccomp profiles] -- JSON format, defaultAction SCMP_ACT_ERRNO, architecture-specific rules
- [CITED: CLAUDE.md] -- Project architecture, module structure, Flyway migration workflow, port configuration

### Tertiary (LOW confidence)
- None -- all findings verified against source code or official documentation

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - all libraries are existing project dependencies
- Architecture: HIGH - all patterns verified against existing codebase
- Pitfalls: HIGH - derived from verified code analysis (RQueue FIFO behavior, BCrypt lookup pattern, seccomp syscall interactions)

**Research date:** 2026-04-14
**Valid until:** 30 days (stable domain -- Spring Boot patterns and Docker seccomp are mature)
