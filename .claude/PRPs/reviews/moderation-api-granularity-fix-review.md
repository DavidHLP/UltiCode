# Local Code Review: Moderation API Granularity Fix

**Reviewed**: 2026-05-21
**Branch**: feat/moderation-api-granularity-fix
**Decision**: APPROVE

## Summary
Well-structured strategy pattern refactor that cleanly replaces the monolithic switch with sealed interface handlers. DTO enum typing is correct. Frontend changes are minimal and aligned with backend. No security vulnerabilities found.

## Findings

### CRITICAL
None

### HIGH
None

### MEDIUM

1. **String-typed status constants in handlers** — `DeleteHideHandler.java:13`, `RestoreDismissHandler.java:13`, `WarnHandler.java:13`, `BanHandler.java:27`, `AppealHandler.java:23-26`
   - Handlers use hardcoded string literals like `"RESOLVED"` and `"APPEAL_PENDING"` for `item.setStatus()`. The backend already has `ModerationActionType` and `ModerationStatus` enums. Using raw strings risks typos and drift from enum values.
   - **FIXED**: Replaced all string literals with `ModerationStatus.RESOLVED.name()` and `ModerationStatus.APPEAL_PENDING.name()`. Updated imports from `ModerationActionType` to `ModerationStatus` where appropriate.

2. **BanHandler no-arg constructor default** — `BanHandler.java:12-14`
   - The no-arg constructor sets `isPermanent = false`, but `BanHandler` is only instantiated via `ModerationActionHandler.from()` which always passes `true` or `false` explicitly. The no-arg constructor is never called in practice. It exists because the `sealed permits` clause requires a public constructor, but it could mislead future callers.
   - **FIXED**: Removed the no-arg constructor. Java sealed permits only requires that permitted classes are accessible to the sealed interface — a single public constructor with the required parameter is sufficient.

3. **AppealHandler no-arg constructor default** — `AppealHandler.java:12-14`
   - Same issue as BanHandler. The no-arg constructor defaults to `APPEAL_PENDING`, but `from()` always passes the specific type. Unused in practice.
   - **FIXED**: Removed the no-arg constructor. Only the `AppealHandler(ModerationActionType actionType)` constructor remains.

### LOW

1. **ReportDialog.vue evidence field sends `undefined` when empty** — `ReportDialog.vue:70`
   - `evidence: evidence.value || undefined` — this works correctly (empty string becomes undefined, omitted from JSON payload), but the pattern is slightly unusual. A more explicit approach would be a conditional spread: `...(evidence.value ? { evidence: evidence.value } : {})`. Not a bug, just a style preference.

2. **ModerationServiceImpl.java line count** — 723 lines
   - The file is within the 800-line threshold but approaching it. The strategy pattern refactor already helped by extracting handler logic. Future additions (more handlers, more entity types) should consider further extraction.

3. **Unused import in ModerationServiceImpl.java** — `ModerationActionType` import at line 26
   - The import is now used (by `dto.getAction()` and the strategy pattern), so this is fine. Just noting it was added as part of the refactor.

## Validation Results

| Check | Result |
|---|---|
| Backend compile | Pass |
| Console type-check | Pass (no moderation-related errors) |
| Management type-check | Pass |
| Console lint | Pass |
| Management lint | Pass |
| Backend tests | Pass (12/12 ModerationDtoAlignmentTest) |

## Files Reviewed

| File | Change Type | Lines |
|---|---|---|
| `ModerationActionHandler.java` | Added | 47 |
| `DeleteHideHandler.java` | Added | 16 |
| `RestoreDismissHandler.java` | Added | 16 |
| `WarnHandler.java` | Added | 16 |
| `BanHandler.java` | Added | 26 |
| `AppealHandler.java` | Added | 32 |
| `ModerationServiceImpl.java` | Modified | 723 |
| `PerformModerationActionDTO.java` | Modified | 31 |
| `BatchModerationActionDTO.java` | Modified | 41 |
| `ReportDialog.vue` | Modified | 148 |
| `moderation.ts` | Modified | 449 |

## Architectural Assessment

The sealed interface + permits pattern is an excellent choice for this domain:
- **Exhaustiveness**: Java compiler enforces all `ModerationActionType` values are handled in `from()`
- **Extensibility**: New action types require a new handler class (which the permits clause forces you to add)
- **Testability**: Each handler can be unit-tested independently
- **ActionContext record**: Clean delegation pattern that avoids exposing Service internals publicly while allowing package-private access

The DTO enum migration (`String` -> `ModerationActionType`) eliminates the previous `Set.of(...)` validation and makes the API contract type-safe at the DTO level. Jackson will serialize the enum as its string name, maintaining API compatibility.

Ghost type cleanup in `moderation.ts` is correct — all removed types (`UserWarning`, `UserBan`, `CreateUserBanDto`, `RevokeBanDto`, `QueryUserWarningsParams`, `QueryUserBansParams`) had zero references outside the definition file.