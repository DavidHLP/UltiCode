import type { PrismaClient } from '@prisma/client';
import type {
  SeederMetadata,
  SeedModuleResult,
  TransactionClient,
} from '../../core/interfaces';
import { CONTEXT_KEYS } from '../../core/seed-context';
import { BaseSeeder, createSeederExport } from '../base/base.seeder';
import problemsData from '../../data/problems.data';

/**
 * ProblemTags seeder - creates problem tag reference data.
 *
 * Layer: L0 (no dependencies)
 *
 * Stores in context:
 * - PROBLEM_TAG_IDS: Array of created tag IDs
 */
export class ProblemTagsSeeder extends BaseSeeder {
  readonly metadata: SeederMetadata = {
    name: 'ProblemTags',
    version: '1.0.0',
    dependencies: [],
    priority: 1,
    description: 'Seed problem tag reference data',
  };

  async clear(tx?: TransactionClient): Promise<void> {
    const client = this.getClient(tx) as PrismaClient;
    // Tag relations are cleared by Problems seeder
    await client.problemTag.deleteMany();
  }

  async seed(tx?: TransactionClient): Promise<SeedModuleResult> {
    const startTime = Date.now();
    const client = this.getClient(tx) as PrismaClient;

    const tagData = problemsData.problem_tags.map((tag) => ({
      id: tag.id,
      label: tag.label,
    }));

    const result = await this.batchCreate(
      client.problemTag as unknown as Parameters<typeof this.batchCreate>[0],
      tagData,
      { skipDuplicates: true },
    );

    // Store tag IDs in context
    this.set(CONTEXT_KEYS.PROBLEM_TAG_IDS, tagData.map((t) => t.id));

    return this.createResult(result.inserted, startTime, {
      tags: result.inserted,
    });
  }
}

export const createProblemTagsSeeder = createSeederExport(ProblemTagsSeeder);
