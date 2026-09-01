# Codex command rules

Codex `.rules` files are command-execution policies, not replacements for the
project's path-scoped engineering guidance. The converted command-enforceable
subset of `.omp` and `.claude` is split into focused files:

- `git-safety.rules`: prompts before remote or destructive Git operations and
  broad staging.
- `secrets-safety.rules`: blocks direct `.env` display and prompts before
  secret-sensitive or destructive shell commands.
- `infrastructure-safety.rules`: prompts before destructive Docker, Flyway, or
  host-resource operations.
- `external-publish.rules`: prompts before publishing, merging, or deployment
  mutations outside the repository.

The Java, Vue, MySQL, testing, contract, and path-specific workflow rules stay
in `AGENTS.md` and the nearest nested guides because Codex command rules do not
support file globs or natural-language coding constraints.

Project-local rules load only when the `.codex/` layer is trusted. Restart
Codex after changing them. Validate representative commands with:

```bash
codex execpolicy check --pretty --rules .codex/rules/git-safety.rules -- git push origin main
codex execpolicy check --pretty --rules .codex/rules/secrets-safety.rules -- cat .env
codex execpolicy check --pretty --rules .codex/rules/infrastructure-safety.rules -- docker compose down -v
codex execpolicy check --pretty --rules .codex/rules/external-publish.rules -- docker push registry.example/app:tag
```
