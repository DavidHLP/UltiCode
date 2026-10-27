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
git clone --branch v1.0.4 https://github.com/DavidHLP/UltiCode.git
cd UltiCode
```

### 2. Choose the installation path before creating configuration

The current release is [v1.0.4 (2026-10-27)](https://github.com/DavidHLP/UltiCode/releases/tag/v1.0.4).
Normal installations should pin a release; contributors can use `main` in a separate checkout.

- **Deploy published images:** start with [release installation](docs/OPERATIONS.md#从已发布发行版安装). Download all assets, verify checksums and nine-image signature evidence, then prepare host TLS/Judge prerequisites and deployment approval.
- **Fresh local development:** open the [empty-volume installation guide](docs/DEVELOPMENT.md#干净卷首次安装仅本机-disposable-开发环境) while `.env` is still absent. That entry owns configuration generation, migration, backfill/parity, cutover and first startup. Do not run `init-env.sh` before it.
- **Existing configuration, databases or volumes:** preserve them and use the [upgrade and rollback entry](docs/OPERATIONS.md#升级与回滚入口). Never treat them as empty volumes, recreate configuration with `--force`, or only change a cutover marker.

Source development requires Docker Compose v2, mise-managed Zulu Java 17, Node.js 22.x (at least 22.22.2), pnpm 11.0.0, PM2, `curl`, `timeout` and `openssl`. Dev Containers prepare tooling and unit validation; they do not start the business stack. See [local development](docs/DEVELOPMENT.md#本地开发) for exact prerequisites, scopes, readiness and troubleshooting.

After completing the selected entry, local frontends are [Console](http://localhost:9002) and [Management](http://localhost:9003). Use the supported launcher rather than starting Maven/PM2 directly around its preparation and safety gates.

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
