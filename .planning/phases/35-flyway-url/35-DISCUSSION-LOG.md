# Phase 35: Flyway URL 修复 - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-22
**Phase:** 35-flyway-url
**Areas discussed:** Flyway 版本选择, URL 来源

---

## Flyway 版本选择

| Option | Description | Selected |
|--------|-------------|----------|
| 11.3.4 (当前) | 已有 wrapper script，但 URL 返回 404 | |
| 10.17.0 | 10.x LTS，URL 已验证 200 | ✓ |
| 11.20.3 | 11.x 最新，URL 需验证 | |
| 12.x | 最新版本，可能有过早风险 | |

**User's choice:** Flyway 10.17.0
**Notes:** 10.x LTS 版本，企业级稳定，Maven Central URL 已验证可用

---

## URL 来源

| Option | Description | Selected |
|--------|-------------|----------|
| Maven Central | https://repo1.maven.org/maven2/... | ✓ |
| Redgate 官方 | https://download.red-gate.com/... | ✗ (返回 404) |
| GitHub Raw | github.com/redgate/flyway/raw/main/... | ✗ (返回 404) |

**User's choice:** Maven Central
**Notes:** 唯一返回 200 的官方来源

---

## Claude's Discretion

- Flyway wrapper script 保持不变（JRE 检测 workaround 有效）
- db-manager CLI 调用方式保持不变

---

*Phase: 35-flyway-url*
*Context gathered: 2026-04-22*
