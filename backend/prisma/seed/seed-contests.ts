import type { PrismaClient, ContestType, ContestStatus } from '@prisma/client';
import contestsData from './data/contests.data';
import type { SeedProblemsResult } from './seed-problems';

export async function clearContests(prisma: PrismaClient): Promise<void> {
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
  deps: { problems: SeedProblemsResult }
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

  // Seed global rankings
  for (const gr of contestsData.global_rankings) {
    await prisma.globalRanking.create({
      data: {
        id: gr.id,
        user_id: gr.user_id,
        username: gr.username,
        global_rank: gr.global_rank,
        rating: gr.rating,
        max_rating: gr.max_rating,
        contests_attended: gr.contests_attended,
        avatar: null,
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
