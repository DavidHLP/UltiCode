<div align="center">

# UltiCode

**Open-source online judge platform · From problem practice to contest judging**

**Java 17 · Spring Boot · Vue 3 · TypeScript · Docker**

**English** · [简体中文](README.zh-CN.md)

[Quick Start](#quick-start) · [Screenshots](#screenshots) · [Documentation](docs/index.md) · [Contributing](#contributing)

[MIT License](LICENSE) · [Report an issue](https://github.com/DavidHLP/UltiCode/issues) · [CI workflow](https://github.com/DavidHLP/UltiCode/actions/workflows/ci.yml)

</div>

---

## Overview

UltiCode is a full-stack online judge platform providing a problem library, contests, online judging, editorials, community, and an admin console.

## Core Capabilities

| Area | Capability |
| --- | --- |
| Practice | Problem library, contests, code submissions, and judging records |
| Learning and community | Editorials, forum, achievements, and user profile |
| Platform administration | Problem and contest management, moderation, audit, notifications, and operational queries |
| Judging | A standalone Judge worker consumes Submissions from Redis Streams and runs code in a Docker sandbox |

## Screenshots

The screenshots below are the design and regression reference screenshots maintained in this repository; see the [screenshot index](assets/screenshots/README.md) for more pages.

| Console · Problem detail | Management · Dashboard |
| :---: | :---: |
| ![Console dark-theme problem detail](assets/screenshots/problem-detail-dark.png) | ![Management dark-theme dashboard](assets/screenshots/admin-dashboard-dark.png) |

## Tech Stack

| Layer | Technology |
| --- | --- |
| Backend | Java 17, Spring Boot, Maven, MyBatis-Plus, Flyway, Dubbo |
| Frontend | Vue 3, TypeScript, Vite, pnpm workspace |
| Infrastructure | Docker Compose, MySQL, Redis, Nacos, MeiliSearch |

## Quick Start

### 1. Get the project

```bash
git clone https://github.com/DavidHLP/UltiCode.git
cd UltiCode
```

### 2. Prepare the environment

Prerequisites: Docker Compose v2, mise, Node.js `^20.19.0 || >=22.12.0`, pnpm 10+, PM2, plus `curl`, `timeout`, and `openssl`. Backend startup runs through the repository's `services/mvnw` and the mise-managed Zulu Java 17.

Contributors can also use Dev Containers / Codespaces directly; creating the container only installs dependencies and then runs the unit gate by default — it does not start the full stack:

```bash
./ulticode doctor --json
```

```bash
./scripts/dev/init-env.sh
```

### 3. Finish the database preparation

> **Required reading for the first start**
>
> `init-env.sh` generates `SUBMISSION_CUTOVER_COMPLETE` as `false`. Both `dev-lite` and `dev-full` require `APP_SUBMISSION_ROUTING_MODE=remote` and a completed Submission cutover marker; the startup scripts refuse to run when either is missing.
>
> Read [Database migrations](docs/OPERATIONS.md#数据库迁移与-owner-收敛) first, and complete migration and verification according to the authorized cutover/backfill runbook. Do not skip the checks by only changing the marker.

### 4. Start the development environment

Once the preparation above is done, choose a mode:

| Mode | Search | Frontends |
| --- | --- | --- |
| `dev-lite` | Database-backed search; no Search worker | Not started by default |
| `dev-full` | Indexed search with the Search worker | Both frontends started |

```bash
./scripts/dev/up.sh --mode dev-lite
```

`dev-lite` uses database search, does not start the Search worker, and does not start either frontend by default; run the following when you need the browser UI:

```bash
./scripts/dev/up.sh --frontend-only
```

`dev-full` enables indexed search, the Search worker, and both frontends, still behind the same cutover gate:

```bash
./scripts/dev/up.sh --mode dev-full
```

Once the frontends are running: [Console](http://localhost:9002) · [Management](http://localhost:9003). See [Local development](docs/DEVELOPMENT.md#本地开发) for scopes, logs, and troubleshooting entry points.

<details>
<summary>Optional Core pilot and rollback notes</summary>

The Core convergence pilot uses an explicit scope and keeps Judge as a separate process:

```bash
./scripts/dev/up.sh --scope core
./scripts/dev/test.sh core
```

The Core parent listens on `9108` with readiness at `/api/v1/core/health/ready`; that profile currently serves owner assembly and boundary verification and has not yet replaced the default distributed topology.

This version no longer supports `legacy-rollback`; `up.sh` fails closed for that legacy mode and for unknown modes. Production rollback can only use the last complete release descriptor retained and verified by the deployer — the current binaries cannot restore the old implementation (see [Deployment, release, and rollback](docs/OPERATIONS.md#部署发布与回滚)).

</details>

## Project Structure

```text
UltiCode/
├── apps/
│   ├── console/       # Console (user application)
│   └── management/    # Management (admin application)
├── packages/          # Shared frontend packages
├── services/          # Backend services, API contracts, and workers
├── init-db/           # Database initialization and Flyway migrations
├── docker/            # Infrastructure and judging sandbox
├── scripts/           # Development, verification, and operations entry points
└── docs/              # Architecture, development, and deployment documents
```

The default topology is **distributed**. Auth, Admin, App, Submission, and Notification each own their data and write boundaries; Judge runs judging, and Search maintains derived indexes. See the [architecture overview](docs/ARCHITECTURE.md).

## Documentation

Long-form documentation is currently maintained in Chinese.

- [Documentation index](docs/index.md): the six core sources for product, architecture, development, operations, and API/reference.
- [Product and domain](docs/PRODUCT.md): product positioning, roles, capabilities, and boundaries.
- [Architecture](docs/ARCHITECTURE.md): service boundaries, Owner/Worker topology, data flow, contracts, and security boundaries.
- [Development and testing](docs/DEVELOPMENT.md): local startup, verification, configuration, rules, and troubleshooting.
- [Operations](docs/OPERATIONS.md): deployment, release, rollback, migrations, backup, monitoring, and incident response.
- [API and authentication reference](docs/REFERENCE.md): browser flows, API entry points, and internal contracts.

Implementation, configuration, migration scripts, tests, and executable runbooks are the source of truth for behavior; this repository has no production environment, and production deployment, real traffic, and external credentials are the deployer's responsibility.

## Contributing

Report issues and discuss improvements through [Issues](https://github.com/DavidHLP/UltiCode/issues), or open a Pull Request.

1. When reporting an issue, include reproduction steps, expected vs. actual behavior, and sanitized environment details.
2. Read [`AGENTS.md`](AGENTS.md) and the relevant directory guides before starting; discuss the scope in an Issue first for larger changes.
3. Add tests for behavior changes and run the matching checks from the [development guide](docs/DEVELOPMENT.md).
4. Explain the reason, verification method, and known limitations in the Pull Request.

Before submitting, run `git diff --check` and the `./scripts/dev/test.sh` gate matching the surface you touched. Never commit `.env`, credentials, private keys, or generated runtime artifacts.

## License

Released under the [MIT License](LICENSE).
