---
description: "Shell changes"
globs:
  - "**/*.sh"
---

# Shell changes

- Preserve the intended interpreter. Quote expansions, use arrays for arguments and avoid evaluating untrusted strings as shell code.
- Handle expected failures explicitly; use strict mode where compatible with the script. Preserve meaningful non-zero exits for failed validation.
- Bound retries and polling, clean up owned temporary resources, and validate destructive targets. Never print secrets or enable tracing around credentials.
- Run the changed script's syntax check and a focused behavioral check when logic changes; use ShellCheck when available. Preserve entry-point permissions.
