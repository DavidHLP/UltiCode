# Phase 14: Contest Engine - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-18
**Phase:** 14-contest-engine
**Areas discussed:** Contest Scheduler, Rating Calculation, Real-Time Ranking, Submission WebSocket

---

## Contest Scheduler

| Option | Description | Selected |
|--------|-------------|----------|
| Fixed-rate @Scheduled polling | Run every N seconds, check all contests | ✓ |
| Event-driven (ApplicationEvent) | Trigger on contest creation/update | Not selected |
| External scheduler (Quartz) | Separate scheduler service | Not selected |

**User's choice:** (--auto mode: recommended defaults selected)
**Notes:** Fixed-rate polling is simpler and sufficient for contest timing precision requirements. Skip if already in target state ensures idempotency.

---

## Rating Calculation

| Option | Description | Selected |
|--------|-------------|----------|
| Codeforces-style Elo variant | Standard CF rating formula with K-factor and expected score | ✓ |
| Glicko-2 | More complex, period-based rating | Not selected |
| TrueSkill | Bayesian, team-based | Not selected |

**User's choice:** (--auto mode: recommended defaults selected)
**Notes:** CF Elo is well-documented, appropriate for individual competitive programming contests. Default K-factor of 32 for new players, lower for established players.

---

## Real-Time Ranking Updates

| Option | Description | Selected |
|--------|-------------|----------|
| Throttled (max 1/sec per contest) | RealtimeService existing throttle | ✓ |
| Per-submission push (no throttle) | Every submission triggers ranking update | Not selected |
| Hybrid (during contest throttled, end full recalc) | Combined approach | Not selected |

**User's choice:** (--auto mode: recommended defaults selected)
**Notes:** Existing RealtimeService throttle infrastructure used — pendingRankingUpdates map + @Scheduled flush already built.

---

## Submission WebSocket

| Option | Description | Selected |
|--------|-------------|----------|
| User-specific destination /user/{userId}/submission | Phase 12 pattern | ✓ |
| Contest room broadcast | Send to all contest participants | Not selected |
| Both user + room | Dual delivery | Not selected |

**User's choice:** (--auto mode: recommended defaults selected)
**Notes:** Submission result is user-specific — only the submitting user needs to know their own verdict. Contest room ranking updates handle the rest.

---

## Claude's Discretion

Listed in CONTEXT.md — specific formula coefficients (K-factor schedule), penalty formula details, and scheduler pagination strategy delegated to planner/researcher.

---

## Deferred Ideas

- Contest freeze time (during last hour, rankings locked) — belongs in future phase
- Problem difficulty weight in rating calculation — future enhancement

---

*Phase: 14-contest-engine*
*Discussion log: 2026-04-18*
