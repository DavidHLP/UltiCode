# Phase 7: Code Quality & Dependencies - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-16
**Phase:** 07-code-quality-dependencies
**Mode:** auto (all decisions auto-selected with recommended defaults)

---

## Exception Handling Precision

| Option | Description | Selected |
|--------|-------------|----------|
| Replace with specific types | Analyze try blocks, use narrow catch (IOException, SQLException, etc.) | ✓ |
| Multi-catch where needed | Use `catch (A \| B e)` for related exceptions | ✓ |
| Allow broad catch with comment | Keep `catch(Exception e)` only with explanatory comment | ✓ |
| Never catch Throwable | Let JVM errors propagate | ✓ |

**Auto-selected:** All four strategies — layered approach based on context

---

## AdminAnalyticsServiceImpl Splitting Strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Split by domain | User analytics, Content analytics, Performance report | ✓ |
| Keep facade pattern | AdminAnalyticsServiceImpl delegates to new services | ✓ |
| Target <300 lines each | Clear single responsibility per service | ✓ |

**Auto-selected:** Domain-based splitting with facade pattern for backward compatibility

---

## Frontend Debug Logging

| Option | Description | Selected |
|--------|-------------|----------|
| Remove all console.log/warn | Keep console.error for genuine error logging | ✓ |
| Preserve DEV-guarded logs | Keep if wrapped in import.meta.env.DEV check | ✓ |

**Auto-selected:** Remove production console.log/warn, keep console.error and DEV-guarded code

---

## Dependency Hygiene

| Option | Description | Selected |
|--------|-------------|----------|
| Replace SNAPSHOT with stable | Update pom.xml versions | ✓ |
| Untrack management/.env | Add to .gitignore, git rm --cached | ✓ |

**Auto-selected:** Standard dependency hygiene

---

## Claude's Discretion

- Exact exception types per catch block
- Service splitting implementation order
- DEV-guarded console.log evaluation

## Deferred Ideas

None
