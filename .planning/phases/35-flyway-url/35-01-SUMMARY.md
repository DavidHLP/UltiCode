---
phase: "35"
plan: "01"
name: "fix-flyway-url"
subsystem: "ci"
tags:
  - "flyway"
  - "ci"
  - "deps"
key-files:
  created: []
  modified:
    - ".github/workflows/ci.yml"
---

## Summary

修复 CI workflow 中 Flyway 下载 URL 返回 404 的问题。将 Flyway 从 11.3.4 降级到 10.17.0。

## Commits

| Task | Description |
|------|-------------|
| 01 | Update Flyway version 11.3.4 → 10.17.0 in CI workflow |

## Deviations

None — plan executed as specified.

## Self-Check

**PASSED**

- `grep "10.17.0" .github/workflows/ci.yml` returns 2 matches (URL and FLYWAY_DIR)
- CI workflow `migrate-validate` job now uses working Flyway download URL
