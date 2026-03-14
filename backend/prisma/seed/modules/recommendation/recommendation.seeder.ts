import type { PrismaClient, Difficulty } from '@prisma/client';
import type {
  SeederMetadata,
  SeedModuleResult,
  TransactionClient,
} from '../../core/interfaces';
import { BaseSeeder, createSeederExport } from '../base/base.seeder';
import {
  REC_PROBLEMS,
  REC_PROBLEM_TAG_RELATIONS,
} from '../../data/recommendation-problems.data';
import { REC_SUBMISSIONS } from '../../data/recommendation-submissions.data';

/**
 * Recommendation seeder - creates problems and submissions for testing the recommendation algorithm.
 *
 * Layer: L2+ (depends on ProblemTags, Users)
 *
 * Creates:
 * - 38 new problems across various tags and difficulties
 * - 60+ submissions with different user behavior patterns
 *
 * User Learning Patterns:
 * - YUKI (Beginner): Few Easy ACs, Medium attempts fail
 * - ALEX (Balanced): Tries various tags, medium success rate
 * - CHEN (Advanced): Many Medium ACs, attempts Hard
 * - MAX (Weak Point): Strong Array, weak DP
 * - SARA (Biased): Strong String, weak Tree
 * - LILY (Challenger): Many Medium ACs, ready for Hard
 * - DAVID (All-rounder): AC records across all tags
 */
export class RecommendationSeeder extends BaseSeeder {
  readonly metadata: SeederMetadata = {
    name: 'Recommendation',
    version: '1.0.0',
    dependencies: ['ProblemTags', 'Users'],
    priority: 0,
    description:
      'Seed problems and submissions for recommendation algorithm testing',
  };

  async clear(tx?: TransactionClient): Promise<void> {
    const client = this.getClient(tx) as PrismaClient;

    // Clear submissions first (they reference problems)
    // Only delete submissions for recommendation problems
    const recProblemIds = REC_PROBLEMS.map((p) => BigInt(p.id));
    await client.submission.deleteMany({
      where: {
        problem_id: { in: recProblemIds },
      },
    });

    // Clear problem tag relations
    await client.problemTagRelation.deleteMany({
      where: {
        problem_id: { in: recProblemIds },
      },
    });

    // Clear problems
    await client.problem.deleteMany({
      where: {
        id: { in: recProblemIds },
      },
    });
  }

  async seed(tx?: TransactionClient): Promise<SeedModuleResult> {
    const startTime = Date.now();
    const client = this.getClient(tx) as PrismaClient;
    const details: Record<string, number> = {};

    // 1. Seed problems
    const problemData = REC_PROBLEMS.map((p) => ({
      id: BigInt(p.id),
      slug: p.slug,
      title: p.title,
      difficulty: p.difficulty as Difficulty,
      acceptance_rate: p.acceptance_rate,
      is_premium: p.is_premium,
      has_solution: p.has_solution,
    }));

    const problemResult = await client.problem.createMany({
      data: problemData,
      skipDuplicates: true,
    });
    details.problems = problemResult.count;

    // 2. Seed tag relations
    const tagRelationData = REC_PROBLEM_TAG_RELATIONS.map((rel) => ({
      problem_id: BigInt(rel.problem_id),
      tag_id: rel.tag_id,
    }));

    const tagRelResult = await client.problemTagRelation.createMany({
      data: tagRelationData,
      skipDuplicates: true,
    });
    details.tagRelations = tagRelResult.count;

    // 3. Seed submissions (sequential due to `connect` syntax)
    let inserted = 0;
    const errors: string[] = [];

    for (const sub of REC_SUBMISSIONS) {
      try {
        await client.submission.create({ data: sub });
        inserted++;
      } catch (error) {
        const msg = error instanceof Error ? error.message : String(error);
        errors.push(`Failed to insert submission: ${msg}`);
      }
    }
    details.submissions = inserted;

    const totalCount = Object.values(details).reduce((sum, n) => sum + n, 0);
    return this.createResult(totalCount, startTime, details, errors);
  }
}

export const createRecommendationSeeder =
  createSeederExport(RecommendationSeeder);
