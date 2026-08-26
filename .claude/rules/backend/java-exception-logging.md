---
paths:
  - "services/**/src/**/*.java"
kind: rules
summary: 'Exception handling and logging rules for backend Java.'
---

# Java exception and logging rules

- Catch the narrowest exception that can be handled. Catching `Exception` is limited to explicit process or protocol boundaries that translate, clean up, or fail closed.
- Exceptions **MUST NOT** implement routine branching inside core domain logic. At an input-decoding or compatibility boundary, catching the parser's documented exception to reject malformed input or select an explicitly supported alternate representation/fallback is allowed; do not duplicate parser grammar with hand-written prechecks or hide unexpected failures.
- Do not catch `Throwable`, swallow an exception, return success after failure, or replace a failure with an unrelated default value.
- Preserve the original cause when translating exceptions. Error messages **MUST** add actionable context without leaking secrets or internal-only data.
- `InterruptedException` handling **MUST** restore the interrupt flag with `Thread.currentThread().interrupt()` unless the method immediately rethrows it.
- Close `AutoCloseable` resources with try-with-resources. Cleanup failures must not hide the primary failure.
- Do not `return`, `throw`, or otherwise replace control flow from `finally`. A transaction that catches an exception must rethrow it or explicitly mark rollback when the operation cannot commit safely.
- Use the project's established `BusinessException` and `ErrorCode` enum patterns for expected domain failures; do not invent ad hoc response structures or catch-all exception wrappers.
- Use SLF4J parameter placeholders instead of string concatenation. Pass the throwable as the final argument when a stack trace is required.
- Guard expensive debug/trace argument construction with the matching level check; simple placeholder logging needs no manual guard.
- Log at one ownership boundary. Do not log and rethrow the same failure at every layer.
- `ERROR` is for failed operations requiring attention, `WARN` for recoverable abnormal conditions, `INFO` for durable lifecycle/audit signals, and `DEBUG` for diagnostic detail.
- Never log passwords, tokens, cookies, authorization headers, secret configuration, full sensitive payloads, or personal data.
- `System.out`, `System.err`, and `printStackTrace()` are forbidden in application code.
