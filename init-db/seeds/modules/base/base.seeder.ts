import type { PrismaClient } from '@prisma/client';
import type {
  SeederMetadata,
  SeedModuleResult,
  TransactionClient,
  BatchInsertOptions,
  BatchInsertResult,
  PrismaDelegate,
} from '../../core/interfaces';
import type { SeedContext } from '../../core/seed-context';
import { batchInsert } from '../../utils/batch-insert';

/**
 * Abstract base class for all seeder modules.
 *
 * Provides:
 * - Standard metadata structure
 * - Abstract clear/seed methods
 * - Helper methods for batch inserts
 * - Context access for cross-module data sharing
 * - Result creation utilities
 *
 * Subclasses must implement:
 * - metadata getter
 * - clear() method
 * - seed() method
 */
export abstract class BaseSeeder {
  protected prisma: PrismaClient;
  protected context: SeedContext;

  constructor(prisma: PrismaClient, context: SeedContext) {
    this.prisma = prisma;
    this.context = context;
  }

  /**
   * Metadata for this seeder
   */
  abstract readonly metadata: SeederMetadata;

  /**
   * Clear all data managed by this seeder.
   * Called before seeding to ensure a clean state.
   *
   * @param tx - Optional transaction client
   */
  abstract clear(tx?: TransactionClient): Promise<void>;

  /**
   * Seed data into the database.
   *
   * @param tx - Optional transaction client
   * @returns Result with counts and timing
   */
  abstract seed(tx?: TransactionClient): Promise<SeedModuleResult>;

  // ============ Helper Methods ============

  /**
   * Perform a batch insert using the batch insert utility.
   *
   * @param delegate - Prisma model delegate
   * @param data - Array of records to insert
   * @param options - Batch insert options
   * @returns Number of records inserted
   */
  protected async batchCreate<T extends object>(
    delegate: PrismaDelegate<T>,
    data: T[],
    options: BatchInsertOptions = {},
  ): Promise<BatchInsertResult> {
    return batchInsert(delegate, data, {
      batchSize: 100,
      skipDuplicates: true,
      ...options,
    });
  }

  /**
   * Get a value from the context, throwing if not found.
   * Use this for required dependencies from other seeders.
   *
   * @param key - Context key
   * @returns Typed value
   */
  protected getOrThrow<T>(key: string): T {
    return this.context.getOrThrow<T>(key);
  }

  /**
   * Get a value from the context, returning undefined if not found.
   *
   * @param key - Context key
   * @returns Typed value or undefined
   */
  protected get<T>(key: string): T | undefined {
    return this.context.get<T>(key);
  }

  /**
   * Set a value in the context for other seeders to use.
   *
   * @param key - Context key
   * @param value - Value to store
   */
  protected set<T>(key: string, value: T): void {
    this.context.set(key, value);
  }

  /**
   * Get the Prisma client (or transaction client if provided)
   */
  protected getClient(tx?: TransactionClient): PrismaClient | TransactionClient {
    return tx || this.prisma;
  }

  /**
   * Create a standard result object
   *
   * @param count - Number of records created
   * @param startTime - Start timestamp for duration calculation
   * @param details - Optional breakdown of counts
   * @param errors - Optional list of non-fatal errors
   */
  protected createResult(
    count: number,
    startTime: number,
    details?: Record<string, number>,
    errors: string[] = [],
  ): SeedModuleResult {
    return {
      name: this.metadata.name,
      count,
      duration: Date.now() - startTime,
      errors,
      details,
    };
  }

  /**
   * Log a debug message (if verbose logging is enabled)
   */
  protected log(message: string): void {
    if (process.env.SEED_VERBOSE === 'true') {
      console.log(`  [${this.metadata.name}] ${message}`);
    }
  }
}

/**
 * Type helper for creating seeder factories
 */
export type SeederFactory = (
  prisma: PrismaClient,
  context: SeedContext,
) => BaseSeeder;

/**
 * Create an index file export helper
 */
export function createSeederExport(SeederClass: new (
  prisma: PrismaClient,
  context: SeedContext,
) => BaseSeeder): SeederFactory {
  return (prisma, context) => new SeederClass(prisma, context);
}
