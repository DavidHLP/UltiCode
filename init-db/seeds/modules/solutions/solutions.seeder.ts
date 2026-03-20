import type { PrismaClient, EdgeOperationType } from '@prisma/client';
import type {
  SeederMetadata,
  SeedModuleResult,
  TransactionClient,
} from '../../core/interfaces';
import { BaseSeeder, createSeederExport } from '../base/base.seeder';
import solutionsData from '../../data/solutions.data';

/**
 * Solutions seeder - creates solutions and solution comments.
 *
 * Layer: L3 (depends on Problems, Users)
 */
export class SolutionsSeeder extends BaseSeeder {
  readonly metadata: SeederMetadata = {
    name: 'Solutions',
    version: '1.0.0',
    dependencies: ['Problems', 'Users'],
    priority: 1,
    description: 'Seed solutions and comments',
  };

  async clear(tx?: TransactionClient): Promise<void> {
    const client = this.getClient(tx) as PrismaClient;
    // Clear edge operations for solutions
    await client.edgeOperation.deleteMany({
      where: {
        target_type: 'SOLUTION',
        operation_type: { in: ['VOTE_UP', 'VOTE_DOWN'] as EdgeOperationType[] },
      },
    });
    await client.solutionComment.deleteMany();
    await client.solution.deleteMany();
  }

  async seed(tx?: TransactionClient): Promise<SeedModuleResult> {
    const startTime = Date.now();
    const client = this.getClient(tx) as PrismaClient;
    const details: Record<string, number> = {};

    // 1. Seed solutions
    const solutionData = solutionsData.solutions.map((sol) => ({
      id: sol.id,
      problem_id: BigInt(sol.problem_id),
      user_id: sol.user_id,
      title: sol.title,
      content: sol.content,
      summary: sol.summary,
      language: sol.language,
      tags: sol.tags,
    }));

    const solutionResult = await client.solution.createMany({
      data: solutionData,
      skipDuplicates: true,
    });
    details.solutions = solutionResult.count;

    // 2. Seed comments
    const commentData = solutionsData.comments.map((comment) => ({
      id: comment.id,
      solution_id: comment.solution_id,
      parent_id: comment.parent_id,
      user_id: comment.user_id,
      content: comment.content,
    }));

    const commentResult = await client.solutionComment.createMany({
      data: commentData,
      skipDuplicates: true,
    });
    details.comments = commentResult.count;

    const totalCount = Object.values(details).reduce((sum, n) => sum + n, 0);
    return this.createResult(totalCount, startTime, details);
  }
}

export const createSolutionsSeeder = createSeederExport(SolutionsSeeder);
