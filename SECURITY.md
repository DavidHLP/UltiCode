# Security policy

[v1.0.4](https://github.com/DavidHLP/UltiCode/releases/tag/v1.0.4) was published on
2026-10-27. Security reports for this release and current `main` are welcome;
include the exact commit or immutable image digest. Older releases may require an
upgrade before a fix can be evaluated. Fixes may land on `main` and a later
release; no LTS/backport window, response-time SLA or production security
certification is promised. The Agent remains experimental and disabled by default.

## Private reporting

Use [Security → Report a vulnerability](https://github.com/DavidHLP/UltiCode/security).
GitHub private vulnerability reporting is enabled for this repository. Do not
publish exploit details, credentials, personal data or production submissions in
an issue, pull request or discussion. No dedicated security email or response-time
SLA is advertised here.

Include the affected component and revision, impact, prerequisites, and a minimal
reproduction using synthetic data in a disposable environment. Describe any
mitigation already applied. Coordinate disclosure with the maintainer before
making the report public. Do not access other users' data or test against a
third-party deployment without its operator's permission.

The project maintainer is [DavidHLP](https://github.com/DavidHLP). Operational
hardening, backup, migration and sandbox boundaries are documented in
[Operations](docs/OPERATIONS.md) and [AGENTS.md](AGENTS.md).
