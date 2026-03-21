import type { PrismaClient, ReportCategory } from '@prisma/client';
import type {
  SeederMetadata,
  SeedModuleResult,
  TransactionClient,
} from '../../core/interfaces';
import { BaseSeeder, createSeederExport } from '../base/base.seeder';
import MODERATION_QUEUE_DATA from '../../data/moderation-queue.data';

/**
 * Moderation Queue seeder - creates moderation queue items for testing.
 *
 * Layer: L3 (depends on Users and content entities)
 *
 * This seeder creates entries in the moderation_queue table to test
 * the moderation system functionality.
 */
export class ModerationQueueSeeder extends BaseSeeder {
  readonly metadata: SeederMetadata = {
    name: 'ModerationQueue',
    version: '1.0.0',
    dependencies: ['Users', 'Problems', 'Forum', 'Solutions'],
    priority: 0,
    description: 'Seed moderation queue data for testing moderation system',
  };

  async clear(tx?: TransactionClient): Promise<void> {
    const client = this.getClient(tx) as PrismaClient;
    await client.moderationQueue.deleteMany();
  }

  async seed(tx?: TransactionClient): Promise<SeedModuleResult> {
    const startTime = Date.now();
    const client = this.getClient(tx) as PrismaClient;
    const details: Record<string, number> = {
      PENDING: 0,
      UNDER_REVIEW: 0,
      RESOLVED: 0,
      DISMISSED: 0,
      APPEAL_PENDING: 0,
    };

    // Insert each moderation queue item
    for (const item of MODERATION_QUEUE_DATA) {
      await client.moderationQueue.create({
        data: {
          id: item.id,
          entity_type: item.entity_type,
          entity_id: item.entity_id,
          author_id: item.author_id,
          priority: item.priority,
          status: item.status,
          report_count: item.report_count,
          primary_category: item.primary_category as ReportCategory,
          assigned_to_id: item.assigned_to_id ?? null,
        },
      });

      // Count by status
      if (item.status in details) {
        details[item.status]++;
      }
    }

    const totalCount = MODERATION_QUEUE_DATA.length;
    return this.createResult(totalCount, startTime, details);
  }
}

export const createModerationQueueSeeder = createSeederExport(ModerationQueueSeeder);
