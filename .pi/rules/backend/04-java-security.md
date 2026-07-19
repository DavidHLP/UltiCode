---
kind: rules
paths:
  - 'backend-spring/src/**/*.java'
  - 'backend-spring/src/main/resources/**/*.{yml,yaml,properties}'
summary: 'Java security rules: auth, validation, secrets.'
triggers:
  - 'security'
  - 'auth'
  - 'validation'
  - 'secrets java'
---
# Java secure-coding rules

- Validate untrusted data at the first typed boundary and reject invalid values before business or persistence logic. Internal callers do not make request data trusted.
- Authorization **MUST** be checked against the authenticated principal and target resource; never accept actor, owner, role, or permission identity from request data.
- SQL, shell commands, templates, expressions, class names, file paths, and URLs **MUST NOT** be built by concatenating untrusted input.
- URL fetches **MUST** validate scheme, destination, redirects, and resolved addresses against the intended allowlist to prevent SSRF.
- File operations **MUST** normalize the path, enforce containment under the owned root, and account for symlink traversal before reading or writing.
- Do not deserialize untrusted native Java objects or enable permissive polymorphic JSON typing. Bind to narrow DTOs and validate them.
- Use platform cryptography and `SecureRandom`; custom encryption, hardcoded keys/IVs, weak hashes for credentials, and predictable tokens are forbidden.
- Regexes applied to untrusted or large input **MUST** have bounded input and avoid catastrophic backtracking patterns.
- Error responses **MUST NOT** expose stack traces, SQL, filesystem paths, secrets, or internal topology.
- Sensitive user data **MUST** be minimized and masked in responses, administrative views, exports, and diagnostics according to the caller's authorization.
- Costly or externally visible actions such as email, messaging, provisioning, and submissions **MUST** define replay/idempotency behavior plus appropriate rate or quota limits.
- User-generated content and interaction endpoints **MUST** consider spam/abuse controls, bounded payloads, and the established moderation/sanitization path.
- Security-sensitive fallback behavior **MUST** fail closed. Do not silently disable authentication, authorization, validation, sandboxing, or secret checks.
- Secrets **MUST** come from approved runtime configuration and must not appear in source, defaults, fixtures, migrations, logs, or exception messages.
- Every security fix **MUST** include a regression test proving the rejected or escaped malicious case, not only the valid path.
