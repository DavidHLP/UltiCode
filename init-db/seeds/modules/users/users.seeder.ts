import type { PrismaClient } from '@prisma/client';
import type {
  SeederMetadata,
  SeedModuleResult,
  TransactionClient,
} from '../../core/interfaces';
import type { SeedContext } from '../../core/seed-context';
import { CONTEXT_KEYS } from '../../core/seed-context';
import { BaseSeeder, createSeederExport } from '../base/base.seeder';
import { PasswordHasher } from '../../utils/password-hasher';
import usersData, { USER_IDS } from '../../data/users.data';

/**
 * Users seeder - creates user accounts.
 *
 * Layer: L1 (depends on nothing, depended on by most other seeders)
 *
 * Stores in context:
 * - USER_IDS: Array of created user IDs
 * - USER_MAP: Map of username -> userId
 * - DEFAULT_PASSWORD_HASH: Pre-computed hash for default password
 */
export class UsersSeeder extends BaseSeeder {
  readonly metadata: SeederMetadata = {
    name: 'Users',
    version: '1.0.0',
    dependencies: [],
    priority: 0,
    description: 'Seed user accounts',
  };

  async clear(tx?: TransactionClient): Promise<void> {
    const client = this.getClient(tx) as PrismaClient;
    await client.user.deleteMany();
  }

  async seed(tx?: TransactionClient): Promise<SeedModuleResult> {
    const startTime = Date.now();
    const client = this.getClient(tx) as PrismaClient;

    // Create password hasher with environment-appropriate salt rounds
    const hasher = new PasswordHasher(this.context.environment);

    // Pre-compute the default password hash once
    const defaultHash = await hasher.getDefaultHash();
    this.set(CONTEXT_KEYS.DEFAULT_PASSWORD_HASH, defaultHash);

    // Prepare user data for batch insert
    const userData = await Promise.all(
      usersData.users.map(async (u) => {
        // Determine password hash
        let password: string | null = null;
        if (u.password) {
          if (hasher.isHashed(u.password)) {
            // Already hashed (from data file)
            password = u.password;
          } else {
            // Use the cached default hash since all users use the same password
            password = defaultHash;
          }
        }

        return {
          id: u.id,
          username: u.username,
          name: u.name ?? null,
          email: u.email ?? null,
          avatar: u.avatar ?? null,
          password,
          bio: (u as Record<string, unknown>).bio as string ?? null,
          company: (u as Record<string, unknown>).company as string ?? null,
          github: (u as Record<string, unknown>).github as string ?? null,
          location: (u as Record<string, unknown>).location as string ?? null,
          twitter: (u as Record<string, unknown>).twitter as string ?? null,
          website: (u as Record<string, unknown>).website as string ?? null,
          preferred_language: (u as Record<string, unknown>).preferred_language as string ?? null,
          last_login_at: (u as Record<string, unknown>).last_login_at as Date ?? null,
        };
      }),
    );

    // Use batch insert instead of sequential creates
    const result = await this.batchCreate(
      client.user as unknown as Parameters<typeof this.batchCreate>[0],
      userData,
      {
        batchSize: 50, // Users are small, can use larger batches
        skipDuplicates: true,
      },
    );

    // Store user IDs in context for dependent seeders
    const userIds = userData.map(u => u.id);
    this.set(CONTEXT_KEYS.USER_IDS, userIds);

    // Create username -> id map for quick lookups
    const userMap = new Map<string, string>();
    for (const u of userData) {
      userMap.set(u.username, u.id);
    }
    this.set(CONTEXT_KEYS.USER_MAP, userMap);

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
export const createUsersSeeder = createSeederExport(UsersSeeder);

/**
 * Re-export user IDs for reference
 */
export { USER_IDS };
