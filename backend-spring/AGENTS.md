# Backend guide

This file supplements [`../AGENTS.md`](../AGENTS.md) for `backend-spring/`.

## Boundaries

- Java 17, Spring Boot 3.2.5, MyBatis-Plus, and MapStruct are configured in `pom.xml`.
- Keep domain work under `src/main/java/com/ulticode/modules/<module>/` using the existing controller, service, mapper, entity, DTO, projection, and consumer-owned port seams.
- Use annotation-based MyBatis mappers; the backend does not use XML mappers.
- Read-side projections own entity-to-VO shaping and cross-mapper enrichment. Cross-module dependencies should use consumer-owned ports rather than reaching through another module's internals.

## Required patterns

- Return the existing `Result<T>` and `PageResult<T>` types; raise domain failures through `BusinessException` and `ErrorCode`.
- Validate controller inputs. Privileged operations require method-level `@PreAuthorize` in addition to route rules.
- Add every new `@Audited` or `@CheckBan` site to `common.audit.AuditPolicy`; `AuditPolicyCoverageTest` enforces the catalog.
- Use transactional conditional updates for single-use or race-sensitive state.
- Do not use `Map.of` when a value may be null.
- When constructor dependencies change, update Mockito `@InjectMocks` fixtures with corresponding mocks.
- Push-port adapters must treat missing or disconnected subscriptions as a no-op.
- Judge execution must fail closed; never silently substitute `problem_examples` for unavailable hidden cases.
- Vote state belongs to `VoteService`. A caller that owns denormalized counters must persist the values returned by `voteService.vote()` and must not independently recount through `EdgeOperationMapper`.

## Verification

Run from this directory:

```bash
./mvnw compile -B
./mvnw test -B
./mvnw -Dtest='*IT' test -B
./mvnw verify -B
```

Run integration tests when database, concurrency, security, queue, sandbox, or cross-module behavior changes. `verify` runs unit tests and the configured JaCoCo checks; it does not select `*IT.java`.
