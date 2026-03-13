import type { PrismaClient } from '@prisma/client';
import type {
  SeederMetadata,
  SeedModuleResult,
  TransactionClient,
} from '../../core/interfaces';
import { BaseSeeder, createSeederExport } from '../base/base.seeder';
import FLAGGED_PROBLEM_DATA from '../../data/flagged-problems.data';

// Valid flag status values for counting (lowercase keys for details object)
const VALID_FLAG_STATUSES = [
  'pending',
  'reviewed',
  'resolved',
  'dismissed',
] as const;

/**
 * Flagged Problems seeder - updates existing problems with flag information.
 *
 * Layer: L3 (depends on Problems and Users)
 *
 * This seeder does NOT create new problems, but updates existing ones
 * with flag-related fields for moderation queue testing.
 */
export class FlaggedProblemsSeeder extends BaseSeeder {
  readonly metadata: SeederMetadata = {
    name: 'FlaggedProblems',
    version: '1.0.0',
    dependencies: ['Problems', 'Users'],
    priority: 0,
    description: 'Seed flagged problem data for moderation queue testing',
  };

  async clear(tx?: TransactionClient): Promise<void> {
    const client = this.getClient(tx) as PrismaClient;
    // Reset all flag-related fields on problems that were flagged
    const flaggedProblemIds = FLAGGED_PROBLEM_DATA.map((p) =>
      BigInt(p.problem_id),
    );

    await client.problem.updateMany({
      where: {
        id: { in: flaggedProblemIds },
      },
      data: {
        is_flagged: false,
        flag_reason: null,
        flag_status: null,
        flag_reported_by: null,
        flag_reported_at: null,
        flag_notes: null,
        flag_reviewed_by: null,
        flag_reviewed_at: null,
      },
    });
  }

  async seed(tx?: TransactionClient): Promise<SeedModuleResult> {
    const startTime = Date.now();
    const client = this.getClient(tx) as PrismaClient;
    const details: Record<string, number> = {
      pending: 0,
      reviewed: 0,
      resolved: 0,
      dismissed: 0,
    };

    // Update each problem with flag data
    for (const flagData of FLAGGED_PROBLEM_DATA) {
      await client.problem.update({
        where: { id: BigInt(flagData.problem_id) },
        data: {
          is_flagged: true,
          flag_reason: flagData.flag_reason,
          flag_status: flagData.flag_status,
          flag_reported_by: flagData.flag_reported_by,
          flag_reported_at: flagData.flag_reported_at,
          flag_notes: flagData.flag_notes ?? null,
          flag_reviewed_by: flagData.flag_reviewed_by ?? null,
          flag_reviewed_at: flagData.flag_reviewed_at ?? null,
        },
      });

      // Count by status
      const status = flagData.flag_status.toLowerCase();
      if (status in details) {
        details[status]++;
      }
    }

    const totalCount = FLAGGED_PROBLEM_DATA.length;
    return this.createResult(totalCount, startTime, details);
  }
}

export const createFlaggedProblemsSeeder = createSeederExport(
  FlaggedProblemsSeeder,
);
