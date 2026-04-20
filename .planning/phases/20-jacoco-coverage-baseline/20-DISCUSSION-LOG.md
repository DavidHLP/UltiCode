# Phase 20: JaCoCo Coverage Baseline - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-20
**Phase:** 20-jacoco-coverage-baseline
**Areas discussed:** Coverage thresholds, Exclusion patterns, Enforcement mechanism

---

## Coverage Thresholds

| Option | Description | Selected |
|--------|-------------|----------|
| 50% line / 40% branch | From ROADMAP.md success criteria | ✓ |
| 60% line / 50% branch | Higher bar | |
| 40% line / 30% branch | Lower bar | |

**User's choice:** 50% line / 40% branch (per ROADMAP)
**Notes:** Auto-mode — recommended default selected.

---

## Exclusion Patterns

| Option | Description | Selected |
|--------|-------------|----------|
| Generated mappers, entities, DTOs, config | Per ROADMAP success criteria | ✓ |
| All non-source files | Overly broad | |
| Only generated mappers | May miss entity/DTO bloat | |

**User's choice:** Generated mappers, entities, DTOs, config classes excluded
**Notes:** Auto-mode — standard JaCoCo exclusion practice.

---

## Enforcement Mechanism

| Option | Description | Selected |
|--------|-------------|----------|
| `mvn verify` fails on threshold | Standard Maven gate pattern | ✓ |
| Warning only | Not effective enforcement | |
| Separate coverage goal | More complex | |

**User's choice:** `mvn verify` fails when coverage below threshold
**Notes:** Auto-mode — failsafe approach ensures CI catches regressions.

---

## Enforcement at Verify Phase

| Option | Description | Selected |
|--------|-------------|----------|
| Verify phase | Allows ITs in failsafe | ✓ |
| Test phase only | ITs excluded | |

**User's choice:** Verify phase (surefire + failsafe)
**Notes:** Auto-mode — standard Maven pattern.

---

## Claude's Discretion

JaCoCo agent jvm arguments, output file format, and specific exclude patterns resolved to standard Maven defaults.

## Deferred Ideas

None.
