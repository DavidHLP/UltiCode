import { describe, expect, it } from "vitest";
import {
  contestDetailSchema,
  globalRankingEntrySchema,
} from "@/api/contest.schema";

const baseContestDetail = {
  id: "contest-finished-002",
  slug: "beginner-contest-vol-1",
  title: "Beginner Contest Vol. 1",
  status: "FINISHED",
  startTime: "2026-06-01T10:00:00",
  endTime: "2026-06-01T12:00:00",
  duration: 120,
  contestType: "IOI",
  scoringMode: "SCORE",
};

describe("contestDetailSchema", () => {
  it("accepts contests with no tie breaker", () => {
    const parsed = contestDetailSchema.parse({
      ...baseContestDetail,
      tieBreaker: "NONE",
    });

    expect(parsed.tieBreaker).toBe("NONE");
  });

  it("accepts all database-backed tie breaker values", () => {
    for (const tieBreaker of [
      "LAST_SOLVE_TIME",
      "TOTAL_TIME",
      "TOTAL_ATTEMPTS",
      "NONE",
    ]) {
      expect(() =>
        contestDetailSchema.parse({
          ...baseContestDetail,
          tieBreaker,
        }),
      ).not.toThrow();
    }
  });
});

describe("globalRankingEntrySchema", () => {
  it("normalizes the backend ContestRankingVO score to rating", () => {
    const parsed = globalRankingEntrySchema.parse({
      rank: 1,
      userId: "user-carol-003",
      username: "carol_wu",
      name: null,
      avatar: null,
      country: "CN",
      score: 1620,
      maxRating: 1620,
      ratingTitle: "SPECIALIST",
      maxRatingTitle: "SPECIALIST",
      contestsAttended: 2,
      badge: "🏆",
    });

    expect(parsed.rating).toBe(1620);
    expect("score" in parsed).toBe(false);
  });
});
