import { Injectable, Logger } from '@nestjs/common';
import { PrismaService } from '../../prisma.service';
import { CacheService } from '../../cache/cache.service';
import { isFeatureEnabled } from '../../common/config/feature-flags.config';
import type {
  ContestProblem,
  ContestSubmission,
  ContestScoringRule,
} from '@prisma/client';

export interface ScoreCalculation {
  base_score: number;
  time_bonus: number;
  penalty: number;
  total_score: number;
}

export interface ParticipantScore {
  user_id: string;
  participant_id: string;
  total_score: number;
  total_time: number;
  problem_count: number;
  problems: Map<
    string,
    { score: number; time: number; solved: boolean; attempts: number }
  >;
}

export interface RankingEntry {
  rank: number | null;
  userId: string;
  username: string;
  avatar: string | null;
  score: number;
  time: number;
  penalty: number;
}

/** Cache key prefix for contest rankings */
const RANKING_CACHE_PREFIX = 'contest';
const RANKING_CACHE_TTL_SECONDS = 5;

@Injectable()
export class ScoringService {
  private readonly logger = new Logger(ScoringService.name);

  constructor(
    private readonly prisma: PrismaService,
    private readonly cacheService: CacheService,
  ) {}

  /**
   * Calculate score for a single submission
   * @param problem - The contest problem configuration
   * @param submission - The submission to calculate score for
   * @param rule - The scoring rule to apply
   * @param isFirstSolve - Whether this is the first solve for this problem
   * @returns Score calculation breakdown
   */
  calculateProblemScore(
    problem: Pick<
      ContestProblem,
      'base_score' | 'time_bonus' | 'score' | 'problem_id'
    >,
    submission: Pick<ContestSubmission, 'is_accepted' | 'time_from_start'>,
    rule: Pick<
      ContestScoringRule,
      | 'base_score_per_problem'
      | 'time_bonus_per_minute'
      | 'wrong_answer_penalty'
      | 'first_solve_bonus'
    >,
    isFirstSolve: boolean = false,
  ): ScoreCalculation {
    // Use problem-specific score if defined, otherwise use rule default
    const baseScore = problem.base_score ?? rule.base_score_per_problem;

    // Calculate time bonus (only if accepted)
    let timeBonus = 0;
    if (submission.is_accepted) {
      const timeSpent = submission.time_from_start; // in seconds
      const minutes = Math.floor(timeSpent / 60);
      const bonusPerMinute = problem.time_bonus ?? rule.time_bonus_per_minute;
      timeBonus = minutes * bonusPerMinute;
    }

    // Calculate penalty for wrong answers
    let penalty = 0;
    if (!submission.is_accepted) {
      penalty = rule.wrong_answer_penalty;
    }

    // First solve bonus
    const firstSolveBonus = isFirstSolve ? rule.first_solve_bonus : 0;

    const totalScore = submission.is_accepted
      ? baseScore + timeBonus + firstSolveBonus
      : 0;

    return {
      base_score: baseScore,
      time_bonus: timeBonus,
      penalty,
      total_score: totalScore,
    };
  }

  /**
   * Update ranking for all participants in a contest
   * This method recalculates all scores based on submissions and updates rankings
   * @param contestId - The contest ID to update rankings for
   */
  async updateContestRanking(contestId: string): Promise<void> {
    if (!isFeatureEnabled('USE_NEW_CONTEST_SYSTEM')) {
      this.logger.debug('New scoring system disabled, skipping ranking update');
      return;
    }

    // Get contest with scoring rule
    const contest = await this.prisma.contest.findUnique({
      where: { id: contestId },
      include: {
        scoring_rule: true,
        problems: true,
      },
    });

    if (!contest) {
      this.logger.warn(`Contest ${contestId} not found`);
      return;
    }

    if (!contest.scoring_rule) {
      this.logger.warn(`Contest ${contestId} has no scoring rule`);
      return;
    }

    // Get all submissions for this contest, ordered by time
    const submissions = await this.prisma.contestSubmission.findMany({
      where: { contest_id: contestId },
      orderBy: { submitted_at: 'asc' },
    });

    // Get all participants
    const participants = await this.prisma.contestParticipant.findMany({
      where: { contest_id: contestId },
    });

    // Track first solves per problem (problemId -> participantId)
    const firstSolves = new Map<string, string>();

    // Build a map of problem ID to problem config
    const problemMap = new Map<string, (typeof contest.problems)[0]>();
    for (const problem of contest.problems) {
      problemMap.set(problem.id, problem);
    }

    // Calculate scores per participant
    const participantScores = new Map<string, ParticipantScore>();

    // Initialize all participants
    for (const participant of participants) {
      participantScores.set(participant.id, {
        user_id: participant.user_id,
        participant_id: participant.id,
        total_score: 0,
        total_time: 0,
        problem_count: 0,
        problems: new Map(),
      });
    }

    // Process submissions in order
    for (const submission of submissions) {
      const participantId = submission.participant_id;
      const problemId = submission.contest_problem_id;

      // Get participant score tracker
      const participant = participantScores.get(participantId);
      if (!participant) continue;

      // Get problem configuration
      const problem = problemMap.get(problemId);
      if (!problem) continue;

      // Check if already solved - skip if so (only count best attempt)
      const existingResult = participant.problems.get(problemId);
      if (existingResult?.solved) continue;

      // Check if first solve for this problem
      const isFirstSolve =
        submission.is_accepted && !firstSolves.has(problemId);

      if (isFirstSolve) {
        firstSolves.set(problemId, participantId);
      }

      // Calculate score for this submission
      const score = this.calculateProblemScore(
        problem,
        submission,
        contest.scoring_rule,
        isFirstSolve,
      );

      // Track attempts
      const currentAttempts = existingResult?.attempts ?? 0;

      // Update problem result
      participant.problems.set(problemId, {
        score: score.total_score,
        time: submission.time_from_start,
        solved: submission.is_accepted,
        attempts: currentAttempts + 1,
      });

      // Update totals if accepted
      if (submission.is_accepted) {
        participant.total_score += score.total_score;
        participant.total_time += submission.time_from_start;
        participant.problem_count += 1;
      }
    }

    // Sort participants by score (desc), then by time (asc), then by problems solved (desc)
    const sortedParticipants = Array.from(participantScores.values()).sort(
      (a, b) => {
        // Higher score first
        if (b.total_score !== a.total_score) {
          return b.total_score - a.total_score;
        }
        // Less time is better
        if (a.total_time !== b.total_time) {
          return a.total_time - b.total_time;
        }
        // More problems solved is better
        return b.problem_count - a.problem_count;
      },
    );

    // Update rankings in database using transaction
    await this.prisma.$transaction(async (tx) => {
      for (let i = 0; i < sortedParticipants.length; i++) {
        const participant = sortedParticipants[i];
        const rank = i + 1;

        await tx.contestParticipant.update({
          where: { id: participant.participant_id },
          data: {
            total_score: participant.total_score,
            total_time: participant.total_time,
            final_rank: rank,
          },
        });
      }
    });

    // Invalidate cache after successful update
    await this.invalidateRankingCache(contestId);

    this.logger.log(
      `Updated rankings for contest ${contestId}: ${sortedParticipants.length} participants`,
    );
  }

  /**
   * Build cache key for contest ranking
   * @param contestId - The contest ID
   * @returns Cache key string
   */
  private buildRankingCacheKey(contestId: string): string {
    return `${RANKING_CACHE_PREFIX}:${contestId}:ranking`;
  }

  /**
   * Invalidate ranking cache for a contest
   * Call this when rankings are updated to ensure fresh data on next request
   * @param contestId - The contest ID to invalidate cache for
   */
  async invalidateRankingCache(contestId: string): Promise<void> {
    const cacheKey = this.buildRankingCacheKey(contestId);
    await this.cacheService.del(cacheKey);
    this.logger.debug(`Invalidated ranking cache for contest ${contestId}`);
  }

  /**
   * Get ranking snapshot for a contest with caching
   * Results are cached for 5 seconds during contests to reduce database load
   * @param contestId - The contest ID
   * @param limit - Maximum number of entries to return (default: 100)
   * @returns Array of ranking entries
   */
  async getRankingSnapshot(
    contestId: string,
    limit: number = 100,
  ): Promise<RankingEntry[]> {
    const cacheKey = this.buildRankingCacheKey(contestId);

    // Try to get from cache first
    const cached = await this.cacheService.get<RankingEntry[]>(cacheKey);
    if (cached) {
      this.logger.debug(
        `Cache hit for ranking snapshot of contest ${contestId}`,
      );
      return cached.slice(0, limit);
    }

    // Fetch from database if not cached
    const participants = await this.prisma.contestParticipant.findMany({
      where: {
        contest_id: contestId,
        final_rank: { not: null },
      },
      orderBy: [{ final_rank: 'asc' }],
      take: limit,
      include: {
        user: {
          select: { id: true, username: true, avatar: true },
        },
      },
    });

    const rankings: RankingEntry[] = participants.map((p) => ({
      rank: p.final_rank,
      userId: p.user_id,
      username: p.user.username,
      avatar: p.user.avatar,
      score: p.total_score,
      time: p.total_time,
      penalty: p.total_penalty,
    }));

    // Cache the results with TTL
    await this.cacheService.set(cacheKey, rankings, RANKING_CACHE_TTL_SECONDS);
    this.logger.debug(
      `Cached ranking snapshot for contest ${contestId} (TTL: ${RANKING_CACHE_TTL_SECONDS}s)`,
    );

    return rankings;
  }
}
