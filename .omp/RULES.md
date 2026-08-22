# UltiCode project rules

These rules are project-level sticky guidance for `omp`.

## Authority

- The root and nearest nested `AGENTS.md` files are the source of truth for architecture, security invariants, contracts, workflows, and verification commands.
- Implementation, executable configuration, and tests are authoritative when documentation conflicts with code.
- Do not use this file to replace or duplicate an entire project guide.

## Required workflow

- Before editing, inspect the relevant implementation, configuration, tests, and nearest `AGENTS.md`.
- Preserve the backend flow `controller -> service -> mapper -> entity` and existing domain-module boundaries.
- Keep request/response contracts aligned across backend, shared types, and both frontends; preserve the existing `Result` envelope and field mappings.
- Validate trust-boundary inputs, use typed DTOs, and use parameterized database access.
- Add or update tests for changed behavior and important failure paths.
- Before completion, inspect the diff for correctness, security, concurrency/resource handling, error paths, compatibility, performance, coverage, unrelated changes, and documentation drift.
- Do not claim tests passed unless the relevant commands were actually run and passed.

## Security invariants

- Never commit, print, or hardcode credentials. Runtime secrets belong in `.env`, CI secrets, or the deployment secret store. JWT secrets must be at least 32 characters.
- Access and refresh tokens remain in HttpOnly cookies. Refresh tokens use the database-backed hash-only issue/rotate/revoke flow; never store plaintext refresh tokens or accept an access token as a refresh credential.
- OAuth state remains bound to an HttpOnly cookie and is consumed atomically from Redis.
- WebSocket authentication accepts only the `access_token` cookie, never query, URL, or client-controlled STOMP tokens.
- `/admin/**` and privileged methods require `ADMIN` or `SUPER_ADMIN`. Audit identity comes from the authenticated principal, not request data.
- Markdown and KaTeX HTML must pass through `packages/markdown-utils`; do not bypass DOMPurify or send unsanitized output to `v-html`.
- Base and production Compose configurations must not publish infrastructure or backend ports. Development exposure belongs only in `docker-compose.dev.yml` and must bind to loopback. Keep Nacos authentication enabled and its default account disabled.
- Do not add usable default users or passwords to migrations. Initial administrator provisioning remains opt-in.

## Database changes

- `init-db/migrations/` is the only migration source. Use `V{timestamp}__Description.sql`.
- Never edit an applied migration; add a later, backward-compatible migration.
- Do not bypass `V20260606130000__Secure_Refresh_Tokens_And_Lock_Seed_Accounts.sql` or reintroduce usable seed credentials.

## Verification

Use the supported wrapper when possible:

```bash
./scripts/dev/test.sh quick
./scripts/dev/test.sh full
./scripts/dev/test.sh integration
```

Use targeted checks from the nearest `AGENTS.md`. For Compose or migration changes, validate both Compose configurations and run `git diff --check`.

## Git and external actions

- Review `git diff` and `git diff --check` before completion.
- Use conventional commit subjects: `<type>: <description>`.
- Do not discard user changes or use destructive Git commands unless explicitly requested.
- Get explicit approval before pushing, merging, publishing, changing third-party resources, rotating remote credentials, or rewriting history.
- Do not edit generated or historical files without a task-specific reason.
