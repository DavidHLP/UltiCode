---
phase: 14
reviewed: 2026-04-19T08:59:00Z
depth: standard
files_reviewed: 7
files_reviewed_list:
  - backend-spring/src/main/java/com/ulticode/modules/contest/scheduler/ContestScheduler.java
  - backend-spring/src/main/java/com/ulticode/modules/contest/service/RatingCalculationService.java
  - backend-spring/src/main/java/com/ulticode/modules/contest/service/impl/RatingCalculationServiceImpl.java
  - backend-spring/src/main/java/com/ulticode/modules/websocket/service/RealtimeService.java
  - backend-spring/src/main/java/com/ulticode/modules/queue/processor/JudgeWorkerProcessor.java
  - backend-spring/src/main/java/com/ulticode/modules/submission/service/impl/SubmissionServiceImpl.java
  - backend-spring/src/main/java/com/ulticode/modules/contest/entity/Contest.java
findings:
  critical: 1
  warning: 3
  info: 2
  total: 6
status: issues_found
---

# Phase 14: Code Review Report

**Reviewed:** 2026-04-19
**Depth:** standard
**Files Reviewed:** 7
**Status:** issues_found

## Summary

Phase 14 implements a contest engine with scheduling, real-time WebSocket updates, CF-style Elo rating, and judge queue integration. One critical bug was found in the rating calculation formula -- it does not reflect actual Codeforces Elo behavior. Several thread-safety and race condition issues were also identified.

---

## Critical Issues

### CR-01: Incorrect Codeforces Elo Rating Formula

**File:** `backend-spring/src/main/java/com/ulticode/modules/contest/service/impl/RatingCalculationServiceImpl.java:94-115`

**Issue:** The `calculateNewRating` method treats every participant's result as a perfect score (actual=1.0), then derives rating change from `k * (1 - avgExpected)`. This is fundamentally wrong.

Codeforces Elo works by comparing each participant against every other participant individually using their actual contest ranks, not an aggregate "did you win" boolean.

Current (incorrect) algorithm:
```java
double avgExpected = totalExpected / (participantCount - 1);
int change = (int) Math.round(k * (1.0 - avgExpected));  // Always assumes actual=1 (win)
```

This means:
- A user who places LAST (0 wins) gets the same rating gain formula as someone who places FIRST
- The actual rank placement is completely ignored
- Only the number of opponents and their ratings matter

**Fix:**

The correct CF rating change for participant `i` is:
```
actual_i = (n - rank_i + 1) / n   // where n = number of participants, rank starts at 1
expected_i = sum over j != i of 1/(1 + 10^((r_j - r_i)/400)) / (n-1)
change_i = k * (actual_i - expected_i)
```

Or if you want to use the pairwise CF approach, for each pair:
```java
// For each participant pair (i vs j):
// If rank_i < rank_j: actual_i beats actual_j (1.0 vs 0.0)
// expected = 1 / (1 + 10^((r_j - r_i)/400))
// change += k * (actual - expected)
```

**Recommended fix in `calculateNewRating`:**
```java
private int calculateNewRating(int myRating, List<ContestParticipant> allParticipants,
                                ContestParticipant me, int myRank) {
    double totalExpected = 0.0;

    for (ContestParticipant opponent : allParticipants) {
        if (opponent.getUserId().equals(me.getUserId())) continue;

        Optional<GlobalRanking> oppGr = globalRankingMapper.findByUserId(opponent.getUserId());
        if (oppGr.isEmpty()) continue;

        int oppRating = oppGr.get().getRating() != null ? oppGr.get().getRating() : 1500;
        double expected = 1.0 / (1.0 + Math.pow(10, (oppRating - myRating) / 400.0));
        totalExpected += expected;
    }

    int participantCount = allParticipants.size();
    double avgExpected = participantCount > 1 ? totalExpected / (participantCount - 1) : 0.5;

    // CF uses actual score based on rank: (n - rank + 1) / n
    double actual = (double) (participantCount - myRank + 1) / participantCount;

    int k = determineKFactor(myRating);
    int change = (int) Math.round(k * (actual - avgExpected));
    return Math.max(0, Math.min(3500, myRating + change));
}
```

Note: The method signature must change to accept `myRank` from the caller, which has it available from the sort order at line 41-49.

---

## Warnings

### WR-01: Race Condition in Contest Scheduler Transitions

**File:** `backend-spring/src/main/java/com/ulticode/modules/contest/scheduler/ContestScheduler.java:63-109`

**Issue:** The idempotency check in `transitionToRunning` and `transitionToFinished` reads the current status, but between the check and `updateById`, another thread could change the status. The DB update then silently overwrites that change without detecting the conflict.

```java
void transitionToRunning(Contest contest) {
    // Re-check: skip if already RUNNING (idempotent)
    if (RUNNING.name().equals(contest.getStatus())) {  // <-- TOCTOU: status could change here
        return;
    }
    contest.setStatus(RUNNING.name());
    contestMapper.updateById(contest);  // <-- Could overwrite a later transition to FINISHED
}
```

**Fix:** Use optimistic locking via a `version` field with `@Version` annotation, or use a conditional update:
```java
int updated = contestMapper.updateStatusIfCurrent(contest.getId(), "UPCOMING", "RUNNING");
if (updated == 0) {
    log.warn("Contest {} status transition race detected", contest.getId());
}
```

### WR-02: Throttle Flush Can Drop Updates

**File:** `backend-spring/src/main/java/com/ulticode/modules/websocket/service/RealtimeService.java:167-193`

**Issue:** In `flushPendingRankings`, there is a window between `pendingRankingUpdates.clear()` (line 170) and the re-mark at line 191 where any new `markDirty()` calls will be cleared on the NEXT flush, not the current one.

```java
Set<String> dirty = Set.copyOf(pendingRankingUpdates.keySet());
pendingRankingUpdates.clear();  // <-- Clears all, including ones just added

for (String contestId : dirty) {
    // ...
    if (elapsed >= RANKING_THROTTLE_MS) {
        // ...
    } else {
        pendingRankingUpdates.putIfAbsent(contestId, true);  // Re-mark for next cycle
    }
}
```

If `markDirty("contest-A")` is called between `clear()` and the re-mark loop for "contest-A", it will NOT be re-marked (because `putIfAbsent` will overwrite with `true`, which was already the value). Wait -- `putIfAbsent` won't overwrite if the key exists, so this is actually safe in this specific case.

However, the bigger issue: if `elapsed >= RANKING_THROTTLE_MS` (line 176) sends the update but then an exception occurs before `lastRankingPushTime.put()` (line 188), the pending entry is already cleared and the contest will not be re-marked. On next scheduled flush, there will be no entry for this contest.

**Fix:** Consider a per-contest atomic operation:
```java
public void markDirty(String contestId) {
    pendingRankingUpdates.compute(contestId, (k, v) -> Boolean.TRUE);
}

@Scheduled(fixedRate = 1000)
public void flushPendingRankings() {
    Map<String, Boolean> toFlush = new HashMap<>();
    pendingRankingUpdates.forEach((contestId, dirty) -> {
        Long lastPush = lastRankingPushTime.get(contestId);
        long elapsed = System.currentTimeMillis() - (lastPush != null ? lastPush : 0);
        if (elapsed >= RANKING_THROTTLE_MS) {
            toFlush.put(contestId, dirty);
        }
    });

    for (Map.Entry<String, Boolean> entry : toFlush.entrySet()) {
        String contestId = entry.getKey();
        pendingRankingUpdates.remove(contestId);
        // ... emit and update lastRankingPushTime
    }
    // Entries not in toFlush remain for next cycle
}
```

### WR-03: Rating Calculation Misses Users Without GlobalRanking

**File:** `backend-spring/src/main/java/com/ulticode/modules/contest/service/impl/RatingCalculationServiceImpl.java:60-66`

**Issue:** Per D-11, users without a `global_ranking` record are skipped. However, the rank assignment at lines 51-56 includes ALL participants, including those without global records. This creates an inconsistency -- two users could have the same final rank if one was skipped.

```java
for (int i = 0; i < participants.size(); i++) {
    ContestParticipant p = participants.get(i);
    p.setFinalRank(i + 1);  // Skipped users still get ranks assigned
    participantMapper.updateById(p);
}
```

**Fix:** Either:
1. Only assign final_rank to participants that will be rated, or
2. Document that final_rank represents contest rank (regardless of rating eligibility)

---

## Info

### IN-01: Typo in Variable Name

**File:** `backend-spring/src/main/java/com/ulticode/modules/contest/service/impl/RatingCalculationServiceImpl.java:61`

```java
String oderId = participant.getUserId();  // "oderId" should be "userId"
```

### IN-02: Broad Catch in JudgeWorkerProcessor.onFailure

**File:** `backend-spring/src/main/java/com/ulticode/modules/queue/processor/JudgeWorkerProcessor.java:203-221`

**Issue:** The `onFailure` method catches `InterruptedException` and logs a warning, but the thread's interrupt status is not fully restored before the method returns. The `log.error` for exhausted retries also uses the `error` parameter twice in formatted output.

```java
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();  // Good
    log.warn("Retry sleep interrupted for job {}", job.getId());
}  // Method exits here -- callers may not know the thread was interrupted
```

**Fix:** Consider propagating the interrupt or ensuring callers handle it. The current handling is acceptable for fire-and-forget retry scenarios, but worth documenting.

---

## Findings by Category

| Category | Critical | Warning | Info |
|----------|----------|---------|------|
| Correctness (rating formula) | 1 | 0 | 0 |
| Thread safety | 0 | 2 | 0 |
| Race conditions | 0 | 1 | 0 |
| Code quality | 0 | 0 | 2 |
| **Total** | 1 | 3 | 2 |

---

## Reviewed Files Summary

| File | Key Concerns |
|------|-------------|
| `ContestScheduler.java` | Race condition in status transitions (WR-01) |
| `RatingCalculationServiceImpl.java` | Incorrect Elo formula (CR-01), skipped user inconsistency (WR-03), typo (IN-01) |
| `RealtimeService.java` | Throttle flush can drop updates (WR-02) |
| `JudgeWorkerProcessor.java` | Broad exception handling (IN-02) |
| `SubmissionServiceImpl.java` | No issues found |
| `Contest.java` | No issues found |
| `RatingCalculationService.java` | Interface only, no issues |

---

_Reviewed: 2026-04-19T08:59:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
