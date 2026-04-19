# Phase 18: Collections Seed (V25) - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-19
**Phase:** 18-collections-seed
**Areas discussed:** Collection Distribution, Icon Strategy, Color Strategy, User Ownership, target_type Mix

---

## [--auto] Auto-Selected Decisions

| Area | Decision | Options Considered |
|------|----------|-------------------|
| Collection Distribution | Mixed: Difficulty(3) + Tag(20) + Company(15) + Contest(5) + Featured(3) ≈ 50 | Single category vs mixed |
| Icon Strategy | Semantic Lucide icons per category | Generic icons vs semantic |
| Color Strategy | Tailwind semantic colors per category | Random vs semantic |
| User Ownership | Admin pool (user-alex primary) | Single admin vs multi-user |
| target_type Mix | PROBLEM_LIST primary + PROBLEM for difficulty tiers | PROBLEM_LIST only vs mixed |
| Icon/Color Storage | Lucide name string + Tailwind color name string | Lookup tables vs direct strings |
| FK Validation | V15 problem_lists.id references | V15 lists only |
| Naming Language | Chinese names | Chinese vs bilingual |

**Notes:** Phase 18 is a data seed phase with well-defined schema. Gray areas are primarily content strategy decisions (what categories, which icons/colors). Technical decisions (schema, FK, seed format) follow established V8/V15 patterns. Auto mode selected sensible defaults.

## Deferred Ideas

- Collection comments/likes (future engagement)
- Personal private collections (V8 has these)
- Collection sharing/collaboration
- Auto-generated similar problems
- Contest-related collections (deferred CONTEST-03)
