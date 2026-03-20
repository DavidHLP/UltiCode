import type { PrismaClient } from '@prisma/client';
import type {
  SeederMetadata,
  SeedModuleResult,
  TransactionClient,
} from '../../core/interfaces';
import { BaseSeeder, createSeederExport } from '../base/base.seeder';
import notificationsData from '../../data/notifications.data';

/**
 * Notifications seeder - creates notification records for users.
 *
 * Layer: L3 (depends on Users)
 *
 * Creates realistic notifications including:
 * - System announcements
 * - Submission results
 * - Contest updates
 * - Social interactions
 */
export class NotificationsSeeder extends BaseSeeder {
  readonly metadata: SeederMetadata = {
    name: 'Notifications',
    version: '1.0.0',
    dependencies: ['Users'],
    priority: 30,
    description: 'Seed notification records with realistic content',
  };

  async clear(tx?: TransactionClient): Promise<void> {
    const client = this.getClient(tx) as PrismaClient;
    await client.notification.deleteMany();
  }

  async seed(tx?: TransactionClient): Promise<SeedModuleResult> {
    const startTime = Date.now();
    const client = this.getClient(tx) as PrismaClient;

    // Prepare notification data for batch insert
    const notificationData = notificationsData.map((n) => ({
      id: n.id,
      user_id: n.user_id,
      type: n.type,
      category: n.category,
      title: n.title,
      body: n.body,
      link: n.link,
      metadata: n.metadata,
      is_read: n.is_read,
      read_at: n.read_at,
      created_at: n.created_at,
    }));

    // Use batch insert
    const result = await this.batchCreate(
      client.notification as unknown as Parameters<typeof this.batchCreate>[0],
      notificationData,
      {
        batchSize: 50,
        skipDuplicates: true,
      },
    );

    return this.createResult(
      result.inserted,
      startTime,
      {
        inserted: result.inserted,
        skipped: result.skipped,
      },
      result.errors,
    );
  }
}

/**
 * Factory export for registration
 */
export const createNotificationsSeeder = createSeederExport(NotificationsSeeder);
