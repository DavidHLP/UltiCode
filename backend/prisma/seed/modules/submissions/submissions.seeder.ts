import type { PrismaClient } from '@prisma/client';
import type {
  SeederMetadata,
  SeedModuleResult,
  TransactionClient,
} from '../../core/interfaces';
import { BaseSeeder, createSeederExport } from '../base/base.seeder';
import { SUBMISSIONS } from '../../data/submissions.data';

/**
 * Submissions seeder - creates problem submissions.
 *
 * Layer: L4 (depends on Problems, Users)
 *
 * Note: This seeder uses sequential inserts because the data file
 * uses Prisma's `connect` syntax for relations which requires
 * individual create calls.
 */
export class SubmissionsSeeder extends BaseSeeder {
  readonly metadata: SeederMetadata = {
    name: 'Submissions',
    version: '1.0.0',
    dependencies: ['Problems', 'Users', 'SubmissionStatuses'],
    priority: 0,
    description: 'Seed problem submissions',
  };

  async clear(tx?: TransactionClient): Promise<void> {
    const client = this.getClient(tx) as PrismaClient;
    // Only delete submissions created by this seeder (IDs starting with 'sub-')
    // Don't delete recommendation submissions (IDs starting with 'rec-')
    await client.submission.deleteMany({
      where: {
        id: { startsWith: 'sub-' },
      },
    });
  }

  async seed(tx?: TransactionClient): Promise<SeedModuleResult> {
    const startTime = Date.now();
    const client = this.getClient(tx) as PrismaClient;

    // Use sequential inserts because data uses `connect` syntax
    let inserted = 0;
    const errors: string[] = [];

    for (const sub of SUBMISSIONS) {
      try {
        await client.submission.create({ data: sub });
        inserted++;
      } catch (error) {
        const msg = error instanceof Error ? error.message : String(error);
        errors.push(`Failed to insert submission ${sub.id}: ${msg}`);
      }
    }

    return this.createResult(
      inserted,
      startTime,
      { submissions: inserted },
      errors,
    );
  }
}

export const createSubmissionsSeeder = createSeederExport(SubmissionsSeeder);
