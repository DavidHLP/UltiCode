import type { PrismaClient, Prisma } from '@prisma/client';

/**
 * Environment for seed execution
 */
export type SeedEnvironment = 'development' | 'test' | 'production';

/**
 * Fixture size configuration
 */
export type FixtureType = 'minimal' | 'standard' | 'full';

/**
 * Metadata for a seeder module
 */
export interface SeederMetadata {
  /** Unique identifier for the seeder */
  name: string;
  /** Semantic version (for migration tracking) */
  version: string;
  /** Names of seeders that must run before this one */
  dependencies: string[];
  /** Priority within the same dependency layer (lower = earlier) */
  priority: number;
  /** Description of what this seeder does */
  description?: string;
}

/**
 * Result from executing a single seeder module
 */
export interface SeedModuleResult {
  /** Seeder name */
  name: string;
  /** Number of records created */
  count: number;
  /** Execution time in milliseconds */
  duration: number;
  /** Any errors that occurred (non-fatal) */
  errors: string[];
  /** Additional details (e.g., breakdown by table) */
  details?: Record<string, number>;
}

/**
 * Overall result from a seed run
 */
export interface SeedResult {
  /** Environment used */
  environment: SeedEnvironment;
  /** Fixture type used */
  fixture: FixtureType;
  /** Total records created across all modules */
  totalRecords: number;
  /** Total execution time in milliseconds */
  totalDuration: number;
  /** Results per module */
  modules: SeedModuleResult[];
  /** Whether the seed was successful */
  success: boolean;
  /** Any fatal error message */
  error?: string;
}

/**
 * Options for running the seed
 */
export interface SeedRunnerOptions {
  /** Target environment (affects module selection and config) */
  environment: SeedEnvironment;
  /** Fixture size to use */
  fixture: FixtureType;
  /** Enable parallel execution within dependency layers */
  parallel: boolean;
  /** Dry run (log but don't execute) */
  dryRun: boolean;
  /** Verbose logging */
  verbose: boolean;
  /** Clear existing data before seeding */
  clear: boolean;
  /** Specific modules to run (empty = all) */
  modules?: string[];
  /** Transaction timeout per module in milliseconds */
  transactionTimeout?: number;
}

/**
 * Prisma transaction client type alias
 */
export type TransactionClient = Prisma.TransactionClient;

/**
 * Options for batch insert operations
 */
export interface BatchInsertOptions {
  /** Number of records per batch (default: 100) */
  batchSize?: number;
  /** Skip records that would cause unique constraint violations */
  skipDuplicates?: boolean;
  /** Error handling strategy */
  onError?: 'throw' | 'continue' | 'skip-batch';
  /** Progress callback for batch operations */
  onProgress?: (progress: BatchProgress) => void;
}

/**
 * Progress information for batch operations
 */
export interface BatchProgress {
  /** Current batch number (1-indexed) */
  batch: number;
  /** Total number of batches */
  totalBatches: number;
  /** Records processed so far */
  processed: number;
  /** Total records to process */
  total: number;
  /** Percentage complete (0-100) */
  percentage: number;
}

/**
 * Result from a batch insert operation
 */
export interface BatchInsertResult {
  /** Total records attempted */
  total: number;
  /** Records successfully inserted */
  inserted: number;
  /** Records skipped (duplicates) */
  skipped: number;
  /** Records that failed */
  failed: number;
  /** Error messages for failed records */
  errors: string[];
  /** Execution time in milliseconds */
  duration: number;
}

/**
 * Prisma delegate interface for batch operations
 * Works with any Prisma model delegate that supports createMany
 */
export interface PrismaDelegate<T = unknown> {
  createMany(args: {
    data: T[];
    skipDuplicates?: boolean;
  }): Promise<{ count: number }>;
  deleteMany(args?: object): Promise<{ count: number }>;
}

/**
 * Base interface for all seeder modules
 */
export interface ISeeder {
  /** Seeder metadata */
  readonly metadata: SeederMetadata;

  /** Clear all data managed by this seeder */
  clear(tx?: TransactionClient): Promise<void>;

  /** Seed data */
  seed(tx?: TransactionClient): Promise<SeedModuleResult>;
}

/**
 * Environment-specific configuration
 */
export interface EnvironmentConfig {
  /** Environment name */
  environment: SeedEnvironment;
  /** Default fixture to use */
  defaultFixture: FixtureType;
  /** Batch size for inserts */
  batchSize: number;
  /** Enable parallel execution */
  parallel: boolean;
  /** Allow clearing existing data */
  allowClear: boolean;
  /** bcrypt salt rounds */
  saltRounds: number;
  /** Transaction timeout in milliseconds */
  transactionTimeout: number;
  /** Modules allowed to run in this environment */
  allowedModules: string[] | 'all';
}

/**
 * Fixture configuration
 */
export interface FixtureConfig {
  /** Fixture name */
  name: FixtureType;
  /** Description */
  description: string;
  /** User count */
  users: number;
  /** Problem count */
  problems: number;
  /** Forum post count */
  forumPosts: number;
  /** Contest count */
  contests: number;
}
