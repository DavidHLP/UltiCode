import { Injectable } from '@nestjs/common';
import { PrismaService } from '../prisma.service';
import { RatingTitle } from '@prisma/client';

// Codeforces-style rating thresholds
export const RATING_THRESHOLDS: Record<RatingTitle, number> = {
  NEWBIE: 0,
  PUPIL: 1200,
  SPECIALIST: 1400,
  EXPERT: 1600,
  CANDIDATE_MASTER: 1900,
  MASTER: 2100,
  INTERNATIONAL_MASTER: 2300,
  GRANDMASTER: 2400,
  INTERNATIONAL_GRANDMASTER: 2600,
  LEGENDARY_GRANDMASTER: 2900,
};

// Rating colors for frontend display
export const RATING_COLORS: Record<RatingTitle, string> = {
  NEWBIE: '#808080',
  PUPIL: '#008000',
  SPECIALIST: '#03A89E',
  EXPERT: '#0000FF',
  CANDIDATE_MASTER: '#AA00AA',
  MASTER: '#FF8C00',
  INTERNATIONAL_MASTER: '#FF8C00',
  GRANDMASTER: '#FF0000',
  INTERNATIONAL_GRANDMASTER: '#FF0000',
  LEGENDARY_GRANDMASTER: '#FF0000',
};

interface RatingChange {
  userId: string;
  oldRating: number;
  newRating: number;
  ratingChange: number;
  rank: number;
  expectedRank: number;
}

@Injectable()
export class RatingService {
  constructor(private prisma: PrismaService) {}

  /**
   * Get the rating title for a given rating value
   */
  getRatingTitle(rating: number): RatingTitle {
    if (rating >= RATING_THRESHOLDS.LEGENDARY_GRANDMASTER) {
      return 'LEGENDARY_GRANDMASTER';
    }
    if (rating >= RATING_THRESHOLDS.INTERNATIONAL_GRANDMASTER) {
      return 'INTERNATIONAL_GRANDMASTER';
    }
    if (rating >= RATING_THRESHOLDS.GRANDMASTER) {
      return 'GRANDMASTER';
    }
    if (rating >= RATING_THRESHOLDS.INTERNATIONAL_MASTER) {
      return 'INTERNATIONAL_MASTER';
    }
    if (rating >= RATING_THRESHOLDS.MASTER) {
      return 'MASTER';
    }
    if (rating >= RATING_THRESHOLDS.CANDIDATE_MASTER) {
      return 'CANDIDATE_MASTER';
    }
    if (rating >= RATING_THRESHOLDS.EXPERT) {
      return 'EXPERT';
    }
    if (rating >= RATING_THRESHOLDS.SPECIALIST) {
      return 'SPECIALIST';
    }
    if (rating >= RATING_THRESHOLDS.PUPIL) {
      return 'PUPIL';
    }
    return 'NEWBIE';
  }

  /**
   * Get the color for a rating title
   */
  getRatingColor(rating: number): string {
    const title = this.getRatingTitle(rating);
    return RATING_COLORS[title];
  }

  /**
   * Calculate expected rank based on rating (simplified Elo)
   * Higher rating = lower expected rank
   */
  private getExpectedRank(
    rating: number,
    participants: { rating: number }[],
  ): number {
    let expectedRank = 1;
    for (const p of participants) {
      // Probability that p beats the current user
      const prob = 1 / (1 + Math.pow(10, (rating - p.rating) / 400));
      expectedRank += prob;
    }
    return expectedRank;
  }

  /**
   * Get K-factor based on rating and contests attended
   * Lower rated and newer players have higher K-factor (bigger swings)
   */
  private getKFactor(rating: number, contestsAttended: number): number {
    // New players (first 10 contests) have higher volatility
    if (contestsAttended < 10) {
      return 800;
    }

    // K-factor decreases as rating increases
    if (rating < 1400) {
      return 400;
    }
    if (rating < 1900) {
      return 350;
    }
    if (rating < 2400) {
      return 300;
    }
    return 250;
  }

  /**
   * Calculate rating changes for all participants in a contest
   * Uses Codeforces-style Elo rating system
   */
  async calculateContestRatings(contestId: string): Promise<RatingChange[]> {
    // Get all contest rankings ordered by rank
    const rankings = await this.prisma.contestRanking.findMany({
      where: {
        contest_id: contestId,
        is_virtual: false, // Only calculate for real participants
      },
      orderBy: { rank: 'asc' },
      include: {
        user: {
          include: {
            globalRanking: true,
          },
        },
      },
    });

    if (rankings.length === 0) {
      return [];
    }

    // Build participant list with current ratings
    const participants = rankings.map((r) => ({
      userId: r.user_id,
      rank: r.rank,
      rating: r.user.globalRanking?.rating ?? 1500,
      contestsAttended: r.user.globalRanking?.contests_attended ?? 0,
    }));

    const ratingChanges: RatingChange[] = [];

    // Calculate rating change for each participant
    for (const participant of participants) {
      const expectedRank = this.getExpectedRank(
        participant.rating,
        participants.filter((p) => p.userId !== participant.userId),
      );

      const kFactor = this.getKFactor(
        participant.rating,
        participant.contestsAttended,
      );

      // Performance-based rating calculation
      // Based on difference between expected and actual rank
      const rankDiff = expectedRank - participant.rank;
      const ratingChange = Math.round(
        (kFactor * rankDiff) / Math.sqrt(participants.length),
      );

      // Apply rating floor (can't go below 0)
      const newRating = Math.max(0, participant.rating + ratingChange);

      ratingChanges.push({
        userId: participant.userId,
        oldRating: participant.rating,
        newRating,
        ratingChange: newRating - participant.rating,
        rank: participant.rank,
        expectedRank: Math.round(expectedRank),
      });
    }

    return ratingChanges;
  }

  /**
   * Apply calculated rating changes to the database
   */
  async applyContestRatings(
    contestId: string,
    ratingChanges: RatingChange[],
  ): Promise<void> {
    await this.prisma.$transaction(async (tx) => {
      for (const change of ratingChanges) {
        const newTitle = this.getRatingTitle(change.newRating);

        // Update contest ranking
        await tx.contestRanking.updateMany({
          where: {
            contest_id: contestId,
            user_id: change.userId,
          },
          data: {
            rating_before: change.oldRating,
            rating_after: change.newRating,
            rating_change: change.ratingChange,
          },
        });

        // Get current global ranking
        const globalRanking = await tx.globalRanking.findUnique({
          where: { user_id: change.userId },
        });

        if (globalRanking) {
          // Update existing global ranking
          const maxRating = Math.max(
            globalRanking.max_rating,
            change.newRating,
          );
          const maxTitle = this.getRatingTitle(maxRating);

          await tx.globalRanking.update({
            where: { user_id: change.userId },
            data: {
              rating: change.newRating,
              rating_title: newTitle,
              max_rating: maxRating,
              max_rating_title: maxTitle,
              contests_attended: { increment: 1 },
              contests_rated: { increment: 1 },
              last_contest_id: contestId,
            },
          });
        } else {
          // Create new global ranking
          const user = await tx.user.findUnique({
            where: { id: change.userId },
          });

          await tx.globalRanking.create({
            data: {
              id: `gr-${change.userId}`,
              user_id: change.userId,
              username: user?.username ?? 'Unknown',
              global_rank: 99999, // Will be updated by recalculateGlobalRankings
              rating: change.newRating,
              max_rating: change.newRating,
              rating_title: newTitle,
              max_rating_title: newTitle,
              contests_attended: 1,
              contests_rated: 1,
              avatar: user?.avatar,
              last_contest_id: contestId,
            },
          });
        }
      }
    });
  }

  /**
   * Recalculate global rankings based on current ratings
   */
  async recalculateGlobalRankings(): Promise<void> {
    // Get all global rankings ordered by rating (desc)
    const rankings = await this.prisma.globalRanking.findMany({
      orderBy: { rating: 'desc' },
    });

    // Update ranks
    await this.prisma.$transaction(async (tx) => {
      for (let i = 0; i < rankings.length; i++) {
        await tx.globalRanking.update({
          where: { id: rankings[i].id },
          data: { global_rank: i + 1 },
        });
      }
    });
  }

  /**
   * Get user's rating history (all contest participations)
   */
  async getUserRatingHistory(userId: string): Promise<
    {
      contestId: string;
      contestTitle: string;
      date: Date;
      rank: number;
      ratingBefore: number;
      ratingAfter: number;
      ratingChange: number;
    }[]
  > {
    const rankings = await this.prisma.contestRanking.findMany({
      where: {
        user_id: userId,
        is_virtual: false,
      },
      include: {
        contest: true,
      },
      orderBy: {
        contest: {
          start_time: 'asc',
        },
      },
    });

    return rankings.map((r) => ({
      contestId: r.contest_id,
      contestTitle: r.contest.title,
      date: r.contest.start_time,
      rank: r.rank,
      ratingBefore: r.rating_before,
      ratingAfter: r.rating_after,
      ratingChange: r.rating_change,
    }));
  }
}
