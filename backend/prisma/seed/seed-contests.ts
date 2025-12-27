import type {
  PrismaClient,
  ContestType,
  ContestStatus,
  ContestParticipantStatus,
  RatingTitle,
} from '@prisma/client';
import contestsData from './data/contests.data';
import usersData from './data/users.data';
import type { SeedProblemsResult } from './seed-problems';

export async function clearContests(prisma: PrismaClient): Promise<void> {
  // Clear in order of dependencies
  await prisma.contestSubmission.deleteMany();
  await prisma.contestProblemResult.deleteMany();
  await prisma.contestRanking.deleteMany();
  await prisma.contestParticipant.deleteMany();
  await prisma.virtualContestSession.deleteMany();
  await prisma.globalRanking.deleteMany();
  await prisma.contestProblem.deleteMany();
  await prisma.contest.deleteMany();
}

export interface SeedContestsResult {
  count: number;
  rankingsCount: number;
}

export async function seedContests(
  prisma: PrismaClient,
  deps: { problems: SeedProblemsResult },
): Promise<SeedContestsResult> {
  // Seed contests
  for (const c of contestsData.contests) {
    await prisma.contest.create({
      data: {
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
      },
    });
  }

  // Seed contest problems
  for (const cp of contestsData.contest_problems) {
    // Only add if problem exists
    if (deps.problems.problemIds.includes(cp.problem_id)) {
      await prisma.contestProblem.create({
        data: {
          id: cp.id,
          contest_id: cp.contest_id,
          problem_id: BigInt(cp.problem_id),
          problem_index: cp.problem_index,
          score: cp.score,
          solved_count: cp.solved_count,
          submission_count: cp.submission_count,
        },
      });
    }
  }

  /* eslint-disable @typescript-eslint/no-unsafe-assignment, @typescript-eslint/no-unsafe-member-access, @typescript-eslint/no-unsafe-argument, @typescript-eslint/no-unsafe-call */
  // Seed contest participants
  for (const p of contestsData.contest_participants as unknown as any[]) {
    await prisma.contestParticipant.create({
      data: {
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
      },
    });
  }

  // Seed contest rankings
  for (const r of contestsData.contest_rankings) {
    await prisma.contestRanking.create({
      data: {
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
      },
    });
  }

  // Seed global rankings
  for (const gr of contestsData.global_rankings) {
    const user = usersData.users.find((u) => u.id === gr.user_id);
    await prisma.globalRanking.create({
      data: {
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
      },
    });
  }

  return {
    count: contestsData.contests.length,
    rankingsCount: contestsData.global_rankings.length,
  };
}
