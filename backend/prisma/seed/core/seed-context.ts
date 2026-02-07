import type {
  SeedEnvironment,
  FixtureType,
  SeedModuleResult,
} from './interfaces';

/**
 * Module progress status
 */
export type ModuleStatus = 'pending' | 'running' | 'completed' | 'failed' | 'skipped';

/**
 * Module progress tracking
 */
export interface ModuleProgress {
  name: string;
  status: ModuleStatus;
  startTime?: number;
  endTime?: number;
  result?: SeedModuleResult;
  error?: string;
}

/**
 * SeedContext provides shared state across seeder modules.
 *
 * - Stores cross-module data (e.g., created user IDs for foreign keys)
 * - Tracks execution progress
 * - Holds environment and fixture configuration
 */
export class SeedContext {
  private data: Map<string, unknown> = new Map();
  private progress: Map<string, ModuleProgress> = new Map();

  constructor(
    public readonly environment: SeedEnvironment,
    public readonly fixture: FixtureType,
  ) {}

  /**
   * Get a typed value from the context
   * Returns undefined if not found
   */
  get<T>(key: string): T | undefined {
    return this.data.get(key) as T | undefined;
  }

  /**
   * Get a typed value from the context, throw if not found
   */
  getOrThrow<T>(key: string): T {
    const value = this.data.get(key);
    if (value === undefined) {
      throw new Error(`SeedContext: Key '${key}' not found. Ensure the dependency module ran first.`);
    }
    return value as T;
  }

  /**
   * Set a typed value in the context
   */
  set<T>(key: string, value: T): void {
    this.data.set(key, value);
  }

  /**
   * Check if a key exists in the context
   */
  has(key: string): boolean {
    return this.data.has(key);
  }

  /**
   * Delete a key from the context
   */
  delete(key: string): boolean {
    return this.data.delete(key);
  }

  /**
   * Clear all stored data (not progress)
   */
  clearData(): void {
    this.data.clear();
  }

  // ============ Progress Tracking ============

  /**
   * Mark a module as pending
   */
  markPending(name: string): void {
    this.progress.set(name, { name, status: 'pending' });
  }

  /**
   * Mark a module as running
   */
  markRunning(name: string): void {
    const existing = this.progress.get(name) || { name, status: 'pending' };
    this.progress.set(name, {
      ...existing,
      status: 'running',
      startTime: Date.now(),
    });
  }

  /**
   * Mark a module as completed
   */
  markCompleted(name: string, result: SeedModuleResult): void {
    const existing = this.progress.get(name);
    this.progress.set(name, {
      name,
      status: 'completed',
      startTime: existing?.startTime,
      endTime: Date.now(),
      result,
    });
  }

  /**
   * Mark a module as failed
   */
  markFailed(name: string, error: string): void {
    const existing = this.progress.get(name);
    this.progress.set(name, {
      name,
      status: 'failed',
      startTime: existing?.startTime,
      endTime: Date.now(),
      error,
    });
  }

  /**
   * Mark a module as skipped
   */
  markSkipped(name: string): void {
    this.progress.set(name, { name, status: 'skipped' });
  }

  /**
   * Get module progress
   */
  getProgress(name: string): ModuleProgress | undefined {
    return this.progress.get(name);
  }

  /**
   * Get all module progress entries
   */
  getAllProgress(): ModuleProgress[] {
    return Array.from(this.progress.values());
  }

  /**
   * Get modules by status
   */
  getModulesByStatus(status: ModuleStatus): string[] {
    return Array.from(this.progress.entries())
      .filter(([, p]) => p.status === status)
      .map(([name]) => name);
  }

  /**
   * Get pending modules
   */
  getPendingModules(): string[] {
    return this.getModulesByStatus('pending');
  }

  /**
   * Get completed modules
   */
  getCompletedModules(): string[] {
    return this.getModulesByStatus('completed');
  }

  /**
   * Get failed modules
   */
  getFailedModules(): string[] {
    return this.getModulesByStatus('failed');
  }

  /**
   * Check if a module is completed
   */
  isCompleted(name: string): boolean {
    return this.progress.get(name)?.status === 'completed';
  }

  /**
   * Check if all dependencies are completed
   */
  areDependenciesCompleted(dependencies: string[]): boolean {
    return dependencies.every(dep => this.isCompleted(dep));
  }

  /**
   * Reset progress tracking
   */
  resetProgress(): void {
    this.progress.clear();
  }

  /**
   * Get total execution time for completed modules
   */
  getTotalDuration(): number {
    let total = 0;
    for (const p of this.progress.values()) {
      if (p.result?.duration) {
        total += p.result.duration;
      }
    }
    return total;
  }

  /**
   * Get total records created
   */
  getTotalRecords(): number {
    let total = 0;
    for (const p of this.progress.values()) {
      if (p.result?.count) {
        total += p.result.count;
      }
    }
    return total;
  }
}

// ============ Well-known Context Keys ============

/**
 * Standard keys for cross-module data sharing
 */
export const CONTEXT_KEYS = {
  /** Array of user IDs created by UsersSeeder */
  USER_IDS: 'userIds',
  /** Map of username -> userId */
  USER_MAP: 'userMap',
  /** Array of problem IDs created by ProblemsSeeder */
  PROBLEM_IDS: 'problemIds',
  /** Map of problem slug -> problemId */
  PROBLEM_MAP: 'problemMap',
  /** Array of problem tag IDs */
  PROBLEM_TAG_IDS: 'problemTagIds',
  /** Array of forum community IDs */
  FORUM_COMMUNITY_IDS: 'forumCommunityIds',
  /** Array of forum post IDs */
  FORUM_POST_IDS: 'forumPostIds',
  /** Array of contest IDs */
  CONTEST_IDS: 'contestIds',
  /** Pre-computed password hash for default password */
  DEFAULT_PASSWORD_HASH: 'defaultPasswordHash',
} as const;
