import type { PrismaClient } from '@prisma/client';
import type {
  SeederMetadata,
  SeedModuleResult,
  TransactionClient,
} from '../../core/interfaces';
import { BaseSeeder, createSeederExport } from '../base/base.seeder';
import { SUBMISSION_STATUS_DEFINITIONS } from '../../../../src/submission/submission-statuses';

/**
 * SubmissionStatuses seeder - creates submission status lookup table.
 *
 * Layer: L0 (no dependencies)
 *
 * This is a reference data seeder that uses the application's
 * SUBMISSION_STATUS_DEFINITIONS constant for consistency.
 */
export class SubmissionStatusesSeeder extends BaseSeeder {
  readonly metadata: SeederMetadata = {
    name: 'SubmissionStatuses',
    version: '1.0.0',
    dependencies: [],
    priority: 0,
    description: 'Seed submission status reference data',
  };

  async clear(tx?: TransactionClient): Promise<void> {
    const client = this.getClient(tx) as PrismaClient;
    try {
      await client.submissionStatus.deleteMany();
    } catch (error) {
      const err = error as { code?: string };
      if (err?.code === 'P2021') {
        // Table doesn't exist, skip
        return;
      }
      throw error;
    }
  }

  async seed(tx?: TransactionClient): Promise<SeedModuleResult> {
    const startTime = Date.now();
    const client = this.getClient(tx) as PrismaClient;

    try {
      const data = SUBMISSION_STATUS_DEFINITIONS.map((def) => ({ ...def }));

      const result = await client.submissionStatus.createMany({
        data,
        skipDuplicates: true,
      });

      return this.createResult(result.count, startTime);
    } catch (error) {
      const err = error as { code?: string };
      if (err?.code === 'P2021') {
        // Table doesn't exist
        return this.createResult(0, startTime, undefined, ['Table missing']);
      }
      throw error;
    }
  }
}

export const createSubmissionStatusesSeeder = createSeederExport(SubmissionStatusesSeeder);
