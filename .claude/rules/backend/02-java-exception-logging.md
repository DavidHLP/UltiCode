---
paths:
  - "backend-spring/src/**/*.java"
---

# Java exception and logging rules

- Catch the narrowest exception that can be handled. Catching `Exception` is limited to explicit process or protocol boundaries that translate, clean up, or fail closed.
- Do not catch `Throwable`, swallow an exception, return success after failure, or replace a failure with an unrelated default value.
- Preserve the original cause when translating exceptions. Error messages **MUST** add actionable context without leaking secrets or internal-only data.
- `InterruptedException` handling **MUST** restore the interrupt flag with `Thread.currentThread().interrupt()` unless the method immediately rethrows it.
- Close `AutoCloseable` resources with try-with-resources. Cleanup failures must not hide the primary failure.
- Use the project's established business-error and global-handler patterns for expected domain failures; do not create ad hoc response bodies in lower layers.
- Use SLF4J parameter placeholders instead of string concatenation. Pass the throwable as the final argument when a stack trace is required.
- Log at one ownership boundary. Do not log and rethrow the same failure at every layer.
- `ERROR` is for failed operations requiring attention, `WARN` for recoverable abnormal conditions, `INFO` for durable lifecycle/audit signals, and `DEBUG` for diagnostic detail.
- Never log passwords, tokens, cookies, authorization headers, secret configuration, full sensitive payloads, or personal data.
- `System.out`, `System.err`, and `printStackTrace()` are forbidden in application code. Harness command output is allowed only when it is part of the documented protocol.
