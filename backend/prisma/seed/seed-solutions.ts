// prisma/seed/seed-solutions.ts
import { PrismaClient } from '@prisma/client';
import solutionsData from './data/solutions.data';

/**
 * Clear all solution-related data
 */
export async function clearSolutions(prisma: PrismaClient): Promise<void> {
  // Delete in order of dependencies (child tables first)
  // Delete in order of dependencies (child tables first)
  await prisma.vote.deleteMany({ where: { target_type: 'SOLUTION' } });
  await prisma.solutionComment.deleteMany();
  await prisma.solution.deleteMany();
}

/**
 * Seed solutions and comments
 */
export async function seedSolutions(prisma: PrismaClient): Promise<{
  solutionsCount: number;
  commentsCount: number;
}> {
  // 1. Seed Solutions
  const solutions = await prisma.solution.createMany({
    data: solutionsData.solutions.map((sol) => ({
      id: sol.id,
      problem_id: BigInt(sol.problem_id),
      user_id: sol.user_id,
      title: sol.title,
      content: sol.content,
      summary: sol.summary,
      language: sol.language,
      tags: sol.tags,
      views: sol.views,

    })),
  });

  // 2. Seed Comments
  const comments = await prisma.solutionComment.createMany({
    data: solutionsData.comments.map((comment) => ({
      id: comment.id,
      solution_id: comment.solution_id,
      parent_id: comment.parent_id,
      user_id: comment.user_id,
      content: comment.content,

    })),
  });

  return {
    solutionsCount: solutions.count,
    commentsCount: comments.count,
  };
}
