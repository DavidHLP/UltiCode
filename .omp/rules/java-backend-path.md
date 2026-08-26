---
description: Java backend conventions and architectural boundaries.
globs:
- services/**/*.java
- services/**/*.yml
- services/**/*.yaml
- services/**/*.properties
priority: 100
---

# Java backend rules

- MUST read `services/AGENTS.md` and the nearest service/module guide before editing.
- MUST compile on Java 17 without preview features or later-JDK APIs.
- MUST preserve `controller -> service -> mapper -> entity` and owner service boundaries (`auth`, `admin`, `app`, `submission`, `notification`, `search`, `judge`, `platform`, `api`).
- MUST use constructor injection for new dependencies; do not add field injection.
- MUST validate DTOs at HTTP boundaries and preserve the existing `Result` envelope and field mappings.
- MUST use parameterized database access and keep MyBatis/MyBatis-Plus mappings aligned with entities and migrations.
- MUST handle nullability deliberately: return empty collections instead of `null`, and do not call `Optional.get()` without a proven presence check.
- MUST use `java.time`; money, exact scores, and identifiers MUST NOT use floating point.
- MUST catch the narrowest exception, preserve causes, restore interruption, close resources with try-with-resources, and never swallow failures or catch `Throwable`.
- MUST log through the established SLF4J boundary without secrets, tokens, cookies, authorization headers, personal data, or full sensitive payloads.
- MUST validate untrusted input at the first typed boundary. Authorization MUST use the authenticated principal and target resource, never request-supplied identity.
- MUST avoid concatenating untrusted input into SQL, shell commands, templates, expressions, class names, paths, or URLs. File paths MUST remain under the owned root; URL fetches MUST validate scheme, redirects, and resolved addresses.
- MUST fail closed for security-sensitive fallback behavior and add a malicious-input regression test for every security fix.
- MUST define query cardinality, deterministic pagination, bounded batch input, affected-row behavior, and transaction ownership for persistence changes.
- MUST NOT hold database transactions across remote calls, blocking waits, sandbox execution, or message delivery.
- MUST preserve the existing mapper/persistence abstraction and module dependency direction; do not add speculative interfaces, pass-through services, or parallel architectures.
- MUST define idempotency and bounded retry behavior for duplicate-prone or externally visible operations.
- MUST keep shared mutable state and executors bounded, owned, observable, and shut down with their lifecycle.
- MUST use JUnit 5 tests that are deterministic, isolated, assertion-based, and free of real network services, unseeded randomness, arbitrary sleeps, developer credentials, or mutable external state.
- MUST add denied-path tests for security/authorization changes and cover boundary, common, contract, and error paths where relevant.
- SHOULD prefer readable control flow over clever streams or speculative abstractions.
- MUST inspect the relevant callers, configuration, tests, and diff before completion.
- MUST route cross-service calls via Dubbo RPC contracts in `services/api/` and preserve schema isolation per owner.
- MUST register every new `@Audited` and `@CheckBan` site in `AuditPolicy` and verify with `AuditPolicyCoverageTest`.
