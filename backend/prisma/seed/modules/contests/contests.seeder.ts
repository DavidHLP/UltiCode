import type {
  PrismaClient,
  ContestType,
  ContestStatus,
  ContestParticipantStatus,
  RatingTitle,
  VirtualContestStatus,
} from '@prisma/client';
import type {
  SeederMetadata,
  SeedModuleResult,
  TransactionClient,
} from '../../core/interfaces';
import { CONTEXT_KEYS } from '../../core/seed-context';
import { BaseSeeder, createSeederExport } from '../base/base.seeder';
import contestsData from '../../data/contests.data';
import usersData from '../../data/users.data';

/**
 * Contests seeder - creates contests with problems, participants, and rankings.
 *
 * Layer: L3 (depends on Problems, Users)
 */
export class ContestsSeeder extends BaseSeeder {
  readonly metadata: SeederMetadata = {
    name: 'Contests',
    version: '1.0.0',
    dependencies: ['Problems', 'Users'],
    priority: 0,
    description: 'Seed contests with problems, participants, and rankings',
  };

  async clear(tx?: TransactionClient): Promise<void> {
    const client = this.getClient(tx) as PrismaClient;
    // Clear in dependency order
    await client.contestSubmission.deleteMany();
    await client.contestProblemResult.deleteMany();
    await client.contestRanking.deleteMany();
    await client.contestParticipant.deleteMany();
    await client.virtualContestSession.deleteMany();
    await client.globalRanking.deleteMany();
    await client.contestProblem.deleteMany();
    await client.contest.deleteMany();
  }

  async seed(tx?: TransactionClient): Promise<SeedModuleResult> {
    const startTime = Date.now();
    const client = this.getClient(tx) as PrismaClient;
    const details: Record<string, number> = {};

    // Get problem IDs from context
    const problemIds = this.get<number[]>(CONTEXT_KEYS.PROBLEM_IDS) || [];
    const problemIdSet = new Set(problemIds);

    // 1. Seed contests
    const contestData = contestsData.contests.map((c) => ({
      id: c.id,
      title: c.title,
      slug: c.slug,
      contest_type: c.contest_type as ContestType,
      start_time: new Date(c.start_time),
      duration_minutes: c.duration_minutes,
      status: c.status as ContestStatus,
      registered_count: c.registered_count,
      participant_count: c.participant_count,
      is_rated: c.is_rated,
      description: c.description ?? null,
      cover_image: c.cover_image ?? null,
    }));

    const contestResult = await client.contest.createMany({
      data: contestData,
      skipDuplicates: true,
    });
    details.contests = contestResult.count;

    // Store contest IDs in context
    this.set(CONTEXT_KEYS.CONTEST_IDS, contestData.map((c) => c.id));

    // 2. Seed contest problems (filter by existing problem IDs)
    const contestProblemData = contestsData.contest_problems
      .filter((cp) => problemIdSet.has(cp.problem_id))
      .map((cp) => ({
        id: cp.id,
        contest_id: cp.contest_id,
        problem_id: BigInt(cp.problem_id),
        problem_index: cp.problem_index,
        score: cp.score,
        solved_count: cp.solved_count,
        submission_count: cp.submission_count,
      }));

    const contestProblemResult = await client.contestProblem.createMany({
      data: contestProblemData,
      skipDuplicates: true,
    });
    details.contestProblems = contestProblemResult.count;

    // 3. Seed contest participants
    type ParticipantData = {
      id: string;
      contest_id: string;
      user_id: string;
      status: string;
      registered_at: string;
      started_at?: string;
      finished_at?: string;
      final_rank?: number;
      total_score?: number;
      total_penalty?: number;
    };
    const participantData = (contestsData.contest_participants as unknown as ParticipantData[]).map((p) => ({
      id: p.id,
      contest_id: p.contest_id,
      user_id: p.user_id,
      status: p.status as ContestParticipantStatus,
      registered_at: new Date(p.registered_at),
      started_at: p.started_at ? new Date(p.started_at) : null,
      finished_at: p.finished_at ? new Date(p.finished_at) : null,
      final_rank: p.final_rank ?? null,
      total_score: p.total_score ?? 0,
      total_penalty: p.total_penalty ?? 0,
      is_virtual: false,
    }));

    const participantResult = await client.contestParticipant.createMany({
      data: participantData,
      skipDuplicates: true,
    });
    details.participants = participantResult.count;

    // 4. Seed contest rankings
    const rankingData = contestsData.contest_rankings.map((r) => ({
      id: r.id,
      contest_id: r.contest_id,
      user_id: r.user_id,
      rank: r.rank,
      total_score: r.total_score,
      total_penalty: r.total_penalty,
      solved_count: r.solved_count,
      rating_before: r.rating_before,
      rating_after: r.rating_after,
      rating_change: r.rating_change,
      is_virtual: r.is_virtual,
    }));

    const rankingResult = await client.contestRanking.createMany({
      data: rankingData,
      skipDuplicates: true,
    });
    details.rankings = rankingResult.count;

    // 5. Seed global rankings
    const globalRankingData = contestsData.global_rankings.map((gr) => {
      const user = usersData.users.find((u) => u.id === gr.user_id);
      return {
        id: gr.id,
        user_id: gr.user_id,
        username: gr.username,
        global_rank: gr.global_rank,
        rating: gr.rating,
        max_rating: gr.max_rating,
        rating_title: gr.rating_title as RatingTitle,
        max_rating_title: gr.max_rating_title as RatingTitle,
        contests_attended: gr.contests_attended,
        contests_rated: gr.contests_rated,
        avatar: user?.avatar || null,
        country: gr.country,
        badge: gr.badge,
      };
    });

    const globalRankingResult = await client.globalRanking.createMany({
      data: globalRankingData,
      skipDuplicates: true,
    });
    details.globalRankings = globalRankingResult.count;

    // 6. Seed virtual contest sessions if they exist
    type VirtualSessionData = {
      id: string;
      contest_id: string;
      user_id: string;
      status: string;
      started_at?: string;
      ends_at?: string;
      finished_at?: string;
      total_score?: number;
      total_penalty?: number;
    };
    const virtualSessions = (contestsData as unknown as { virtual_contest_sessions?: VirtualSessionData[] }).virtual_contest_sessions;
    if (virtualSessions) {
      const vcsData = virtualSessions.map((vcs) => ({
        id: vcs.id,
        contest_id: vcs.contest_id,
        user_id: vcs.user_id,
        status: vcs.status as VirtualContestStatus,
        started_at: vcs.started_at ? new Date(vcs.started_at) : null,
        ends_at: vcs.ends_at ? new Date(vcs.ends_at) : null,
        finished_at: vcs.finished_at ? new Date(vcs.finished_at) : null,
        total_score: vcs.total_score ?? 0,
        total_penalty: vcs.total_penalty ?? 0,
      }));

      const vcsResult = await client.virtualContestSession.createMany({
        data: vcsData,
        skipDuplicates: true,
      });
      details.virtualSessions = vcsResult.count;
    }

    // 7. Seed contest problem results if they exist
    type ProblemResultData = {
      contest_id: string;
      contest_problem_id: string;
      user_id: string;
      participant_id: string;
      ranking_id?: string;
      is_solved: boolean;
      score: number;
      attempts: number;
      first_solve_time?: number;
      penalty_time: number;
    };
    const problemResults = (contestsData as unknown as { contest_problem_results?: ProblemResultData[] }).contest_problem_results;
    if (problemResults) {
      const cprData = problemResults.map((cpr) => ({
        contest_id: cpr.contest_id,
        contest_problem_id: cpr.contest_problem_id,
        user_id: cpr.user_id,
        participant_id: cpr.participant_id,
        ranking_id: cpr.ranking_id ?? null,
        is_solved: cpr.is_solved,
        score: cpr.score,
        attempts: cpr.attempts,
        first_solve_time: cpr.first_solve_time ?? null,
        penalty_time: cpr.penalty_time,
      }));

      const cprResult = await client.contestProblemResult.createMany({
        data: cprData,
        skipDuplicates: true,
      });
      details.problemResults = cprResult.count;
    }

    const totalCount = Object.values(details).reduce((sum, n) => sum + n, 0);
    return this.createResult(totalCount, startTime, details);
  }
}

export const createContestsSeeder = createSeederExport(ContestsSeeder);
