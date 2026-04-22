# Phase 46: Sandbox Hardening - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-22
**Phase:** 46-sandbox-hardening
**Areas discussed:** Flag ordering, seccomp mounting, per-language limits, tmpfs size, namespace isolation test

---

## Flag Ordering Bug (SAND-01)

| Option | Description | Selected |
|--------|-------------|----------|
| Fix --read-only after --tmpfs | Current code has --read-only before --tmpfs causing mount failure | ✓ |
| Keep as-is | Not viable — bug is documented | |

**User's choice:** Fix --read-only after --tmpfs
**Notes:** [auto] Selected recommended approach — flag ordering must be corrected per SAND-01

---

## Seccomp Profile Mounting (SAND-02)

| Option | Description | Selected |
|--------|-------------|----------|
| Volume mount seccomp profile | Add $(pwd)/docker/sandbox:/seccomp-profile:ro volume so container can access profile | ✓ |
| Keep path as-is | Not viable — container cannot access host path without volume mount | |

**User's choice:** Volume mount seccomp profile into container
**Notes:** [auto] Selected recommended approach — seccomp profile must be volume-mounted per SAND-02

---

## Per-Language Resource Limits (SAND-03)

| Option | Description | Selected |
|--------|-------------|----------|
| Distinct limits per language | Java 10s/256m, Python 5s/128m, C/C++ 5s/128m, Go 8s/256m, Rust 8s/256m, JS 3s/64m | ✓ |
| Single global limit | All languages share same limits — simpler but less secure | |

**User's choice:** Distinct limits per language
**Notes:** [auto] Selected recommended limits from D-04

---

## Tmpfs Size (SAND-04)

| Option | Description | Selected |
|--------|-------------|----------|
| size=64m (already correct) | /tmp mounted as tmpfs with size=64m — already in code | ✓ |
| Different size | Not evaluated — current implementation already correct | |

**User's choice:** size=64m (already correct in code)
**Notes:** [auto] No change needed — tmpfs size already enforces 64m limit

---

## Namespace Isolation Test (SAND-05)

| Option | Description | Selected |
|--------|-------------|----------|
| Integration test with pid/network checks | Verify process invisible in host ns, network truly isolated | ✓ |
| Skip test | Not viable — SAND-05 explicitly requires verification | |

**User's choice:** Integration test validating namespace isolation
**Notes:** [auto] Selected recommended approach — integration test per SAND-05

---

## Claude's Discretion

- Configuration approach (application.yml vs hardcoded) — delegated to planner to determine best Spring Boot pattern

## Deferred Ideas

None — all SAND requirements discussed and resolved within phase scope.

