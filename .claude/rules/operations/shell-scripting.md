---
paths:
  - "*.sh"
  - "scripts/**/*.sh"
  - "docker/**/*.sh"
  - "init-db/**/*.sh"
kind: rules
summary: 'Shell scripting standards for repo scripts.'
---

# Bash and shell-script rules

- New Bash scripts **MUST** use `#!/usr/bin/env bash`. Preserve another interpreter only when the script intentionally targets POSIX `sh` or an existing runtime contract.
- Default to `set -euo pipefail`. Omitting `-e` is allowed for probe/diagnostic scripts only when expected failures are captured and checked explicitly.
- Quote parameter expansions and command substitutions unless intentional splitting/globbing is documented. Prefer arrays for argument lists.
- Use `read -r`, `mapfile`, or structured tools; do not parse `ls` output or split filenames on whitespace.
- `eval` and dynamically assembled shell command strings are forbidden. Pass arguments as arrays and validate closed option sets.
- Resolve the repository root from `${BASH_SOURCE[0]}` rather than assuming the caller's working directory.
- Functions **SHOULD** declare locals with `local`; environment/configuration names use uppercase, ordinary locals lowercase.
- Temporary files/directories **MUST** use `mktemp` and a `trap` cleanup. Validate paths before `rm`, use `--` where supported, and never rely on an unchecked destructive glob.
- Retry and polling loops **MUST** have a timeout or bounded attempt count, diagnostic output, and non-zero failure status.
- Check required external commands early and emit actionable errors. Use `jq`, Compose, or another structured parser instead of regex for JSON/YAML.
- Never echo secrets, enable `set -x` around credentials, embed passwords in command arguments, or source an untrusted environment file.
- A `shellcheck disable` comment **MUST** be adjacent to the line and justified by the surrounding code.
- Changed scripts **MUST** pass `bash -n`; run ShellCheck when available and preserve executable permissions for entry-point scripts.
