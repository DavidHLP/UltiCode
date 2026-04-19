# Phase 15-03 Summary: Fix globalRank AC-based Ranking

## Fix Summary

**Gap:** UAT Test #3 — `globalRank` returned null for `u-admin-001` because `findGlobalRankByUserId` queried `global_rankings` table (contest ratings), not AC submission counts.

**Root Cause:** SQL `@Select("SELECT global_rank FROM global_rankings WHERE user_id = #{userId}")` — user had no contest participation record, hence null.

**Fix Applied:** Replaced SQL with AC-count-based ranking query:
```sql
SELECT COUNT(*) + 1 FROM submissions s1
WHERE s1.user_id != #{userId}
AND s1.status = 'Accepted'
AND (SELECT COUNT(*) FROM submissions s2 WHERE s2.user_id = #{userId} AND s2.status = 'Accepted') <
(SELECT COUNT(*) FROM submissions s3 WHERE s3.user_id = s1.user_id AND s3.status = 'Accepted')
```
Rank = number of users with more accepted submissions + 1.

## Verification

```
GET /users/u-admin-001/stats → globalRank: 261 ✅ (previously null)
```

## Files Modified

- `backend-spring/src/main/java/com/ulticode/modules/submission/mapper/SubmissionMapper.java` — findGlobalRankByUserId SQL replaced

## Key Decision

Option A chosen: Rank by AC submissions. No schema change required, dynamic ranking.
