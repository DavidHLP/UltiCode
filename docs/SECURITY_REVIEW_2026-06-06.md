# UltiCode Security Review and Remediation

- Review date: 2026-06-06
- Remediation date: 2026-06-06
- Scope: Spring backend, Vue console, Vue management client, database migrations,
  CI/CD, and deployment configuration
- Repository status: all code-addressable findings remediated
- Operational status: credential rotation and Git history cleanup still require
  repository-owner execution

## Executive Summary

The review identified two critical, three high, and three medium findings. The
repository now contains fixes for every finding, including database-backed refresh
token rotation, privileged endpoint authorization, browser-bound OAuth state,
cookie-only WebSocket authentication, locked seed accounts, internal infrastructure
networks, Nacos authentication, patched frontend dependencies, and CI security gates.

The committed database and JWT values must still be treated as compromised because
deleting them from the current tree does not remove them from Git history. Production
is not considered fully remediated until the credential rotation and coordinated
history rewrite in `docs/SECURITY_REMEDIATION_RUNBOOK_2026-06-06.md` are completed.

## Remediation Status

### Critical: Credentials Committed to Git

Status: repository fixed; operational action required.

- Removed `backend-spring/start-backend.sh`,
  `backend-spring/ecosystem.config.cjs`, and the raw database dump.
- Removed insecure database and JWT defaults from runtime configuration.
- Added required secret environment variables and Gitleaks scanning in CI.
- Added a runbook for credential rotation, session invalidation, and history cleanup.

Remaining action: rotate every exposed value before deployment, then rewrite and
force-push Git history during an approved maintenance window.

### Critical: Migrations Install Accounts With Public Passwords

Status: fixed.

- Added `V20260606130000__Secure_Refresh_Tokens_And_Lock_Seed_Accounts.sql`.
- The migration disables and bans every documented seed account and replaces its
  password hash with an unusable value.
- Added an opt-in, one-time `AdminBootstrapRunner` that requires a strong password,
  refuses overwrite, and exits after creating the first `SUPER_ADMIN`.
- Disabled the default Nacos account and added
  `scripts/security/bootstrap-nacos-user.sh` for explicit administrator provisioning.

### High: Refresh Tokens Were Not Reliably Validated or Revoked

Status: fixed.

- Password and OAuth login now use the same database-backed issuance path.
- Refresh requires a JWT with `type=refresh`.
- Only SHA-256 token hashes are stored; the plaintext database column is removed.
- Rotation atomically revokes the presented token before issuing its replacement.
- Logout revokes the presented refresh token before clearing cookies.
- Tests cover access-token rejection, hash-only storage, rotation, and replay denial.

### High: Missing Authorization on Privileged Operations

Status: fixed.

- `/admin/**` now requires `ADMIN` or `SUPER_ADMIN`.
- Email, subscription, dashboard, achievement mutation, and i18n bulk-write
  operations have method-level role checks.
- i18n audit identities are derived from the authenticated principal rather than
  request data.
- Authorization tests cover `USER`, `MODERATOR`, `ADMIN`, and `SUPER_ADMIN`.

### High: Production Infrastructure Was Exposed

Status: fixed in deployment configuration.

- MySQL, Redis, Nacos, and the backend no longer publish public host ports.
- Infrastructure services use an internal Docker network.
- Production frontend ports bind to loopback for an external TLS gateway.
- Nacos authentication and unique identity values are required.
- Redis, database, JWT, and Nacos credentials no longer have production fallbacks.
- A separate `docker-compose.dev.yml` provides loopback-only development ports.

Operational verification is still required in the target environment to confirm that
the external firewall and TLS gateway expose only intended services.

### Medium: OAuth State Was Not Bound to the Initiating Browser

Status: fixed.

- Provider-specific state is stored in an HttpOnly, SameSite cookie.
- Callback state is compared against the browser cookie in constant time.
- Redis state is atomically consumed and the browser cookie is cleared.
- A regression test rejects state from a different browser session.

### Medium: WebSocket Tokens Could Leak Through Logs

Status: fixed.

- WebSocket authentication accepts only the HttpOnly `access_token` cookie.
- Query-string, bearer-header, and client-supplied STOMP token paths were removed.
- Handshake logs no longer contain full URIs, token fragments, or attributes.
- Frontend Nginx access logs are disabled to prevent query-string retention.

### Medium: Vulnerable Frontend Dependencies

Status: fixed.

- Replaced `markdown-it-katex` with maintained `@mdit/plugin-katex`.
- Pinned patched `dompurify`, `lodash-es`, and `protocol-buffers-schema` versions.
- Added malicious Markdown and KaTeX regression tests to both applications.
- Added production dependency audit gates to CI.

## Verification

The following checks passed on 2026-06-06:

- Backend Maven suite: 605 tests, 0 failures.
- Targeted backend security suite: 67 tests, 0 failures.
- Shared auth package: 18 tests plus type-check.
- Console: 205 tests, type-check, production build, and production dependency audit.
- Management: 228 tests, type-check, production build, and production dependency
  audit.
- Console and management audits: no known production vulnerabilities.
- Gitleaks current-tree scan: no leaks found.
- Development and production Docker Compose rendering with required variables.
- Nacos bootstrap script shell syntax validation.
- Flyway 10.17 migration and validation against a fresh MySQL 9.1 database.
- Database assertions: zero enabled documented seed accounts, no plaintext refresh
  token column, and final migration version `20260606130000`.
- `git diff --check`.

Flyway warns that MySQL 9.1 is newer than the database version officially tested by
Flyway 10.17. The migrations completed successfully, but the Flyway version should be
upgraded separately after compatibility testing.

The OWASP Java dependency database scan was not completed during the original review,
so this report does not claim that Java dependencies are vulnerability-free.

## Required Operational Closure

Complete these steps before declaring production remediation complete:

1. Rotate database, Redis, JWT, OAuth, SMTP, payment, and Nacos credentials.
2. Deploy the new JWT secret to invalidate all previously issued sessions.
3. Provision the Nacos administrator and first application administrator.
4. Verify public network reachability from outside the deployment network.
5. Rewrite Git history and coordinate collaborator re-clones.
6. Run a full-history Gitleaks scan after the rewrite.

Detailed commands and safety constraints are in
`docs/SECURITY_REMEDIATION_RUNBOOK_2026-06-06.md`.
