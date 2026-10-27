# Contributing

Use [issues](https://github.com/DavidHLP/UltiCode/issues) for reproducible bugs and
scoped proposals. Include the revision, expected and observed behavior, and a
minimal reproduction with synthetic data. Follow [SECURITY.md](SECURITY.md) for
vulnerabilities; never attach secrets or real user submissions to public reports.

Read [AGENTS.md](AGENTS.md), the nearest component guide, and the
[documentation map](docs/index.md) before changing code. Start from a branch,
preserve unrelated changes, and keep pull requests focused. Explain the concrete
behavior change and list checks as passed, failed, skipped or not run. Link the
issue when one exists; an issue is not required for a small repair.

Use the supported local setup and relevant checks in
[Development](docs/DEVELOPMENT.md). Browser acceptance requires disposable real
services; mocks and existing sample verdicts are not end-to-end evidence. Do not
run tests against production data or make paid model calls without permission.

Keep applied migrations immutable; add a forward migration when needed. Preserve
authentication, owner isolation, sandbox limits, backup/cutover and artifact
verification gates. Release tags are immutable: changed image digests need new
service versions. Publishing, deployment and merging are separate maintainer
actions, not consequences of a passing test or draft pull request.

Contributions are provided under the repository's [MIT license](LICENSE). Keep
third-party attribution and submit only material you have permission to share.
Be respectful and follow the [community expectations](CODE_OF_CONDUCT.md).
