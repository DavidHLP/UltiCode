# Phase 6: Admin Functionality & Performance - Discussion Log (Assumptions Mode)

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions captured in CONTEXT.md — this log preserves the analysis.

**Date:** 2026-04-16
**Phase:** 06-admin-functionality-performance
**Mode:** auto (assumptions-based)
**Areas analyzed:** Audit Trail, Admin Stubs, Moderation Metrics, Performance Optimization, Batch Execution

## Assumptions Presented

### Audit Trail User Resolution
| Assumption | Confidence | Evidence |
|------------|-----------|----------|
| Use @CurrentUser annotation to replace hardcoded "system" | Confident | @CurrentUser exists in common/annotation, used in 10+ controllers including ModerationController, ContestServiceImpl |

### Admin TODO Stub Implementation
| Assumption | Confidence | Evidence |
|------------|-----------|----------|
| Extend existing MyBatis-Plus mappers with new query methods | Confident | All admin services follow mapper pattern, stubs are in modules that already have mappers |
| Forum communities query from existing forum tables | Likely | AdminForumCommunityVO already defined, need to identify source table |
| Problem count via COUNT on problem_list_item table | Confident | ProblemList entity has list_id relationship, standard COUNT pattern |

### Moderation Average Resolution Time
| Assumption | Confidence | Evidence |
|------------|-----------|----------|
| SQL AVG(TIMESTAMPDIFF) on moderation_queue | Confident | moderation_queue has created_at and resolved_at fields, status='RESOLVED' filter |

### Performance Optimization
| Assumption | Confidence | Evidence |
|------------|-----------|----------|
| MyBatis-Plus selectMaps + GROUP BY for aggregation | Confident | Established pattern in codebase, LambdaQueryWrapper supports groupBy |
| Replace entity loading with aggregate queries | Confident | AdminAnalyticsServiceImpl currently loads full entity lists (User, Submission, Contest) |

### Batch Test Case Execution
| Assumption | Confidence | Evidence |
|------------|-----------|----------|
| Single container with multi-case JSON input | Likely | Current buildDockerCommand constructs per-language commands, need unified batch entrypoint |
| Per-case timeout within batch timeout | Likely | sandboxConfig.timeout() exists, need per-case division |

## Corrections Made

No corrections — all assumptions auto-confirmed (auto mode).

## Auto-Resolved

All 5 areas auto-selected with recommended defaults:
- Audit Trail: @CurrentUser annotation (Confident)
- Admin Stubs: Extend existing mappers (Confident/Likely)
- Moderation Time: SQL AVG aggregation (Confident)
- Performance: MyBatis-Plus selectMaps (Confident)
- Batch Execution: Single container with JSON array input (Likely)

## External Research

No external research needed — codebase provides sufficient evidence for all decisions.

---

*Discussion log generated: 2026-04-16*
