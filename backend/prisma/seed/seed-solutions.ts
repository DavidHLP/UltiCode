// prisma/seed/seed-solutions.ts
import { PrismaClient } from '@prisma/client';
import solutionsData from './data/solutions.data';

/**
 * Clear all solution-related data
 */
export async function clearSolutions(prisma: PrismaClient): Promise<void> {
  // Delete in order of dependencies (child tables first)
  await prisma.solutionVote.deleteMany();
  await prisma.solutionComment.deleteMany();
  await prisma.solution.deleteMany();
}

/**
 * Seed solutions, comments, and votes
 */
export async function seedSolutions(prisma: PrismaClient): Promise<{
  solutionsCount: number;
  commentsCount: number;
  votesCount: number;
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
      likes: sol.likes,
      dislikes: sol.dislikes,
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
      likes: comment.likes,
    })),
  });

  // 3. Seed Votes
  const votes = await prisma.solutionVote.createMany({
    data: solutionsData.votes.map((vote) => ({
      solution_id: vote.solution_id,
      user_id: vote.user_id,
      vote_type: vote.vote_type,
    })),
  });

  return {
    solutionsCount: solutions.count,
    commentsCount: comments.count,
    votesCount: votes.count,
  };
}
