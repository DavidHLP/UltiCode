import * as bcrypt from 'bcrypt';
import type { SeedEnvironment } from '../core/interfaces';

/**
 * Salt rounds by environment
 * - Development/Test: Low rounds for speed
 * - Production: Standard security rounds
 */
const SALT_ROUNDS: Record<SeedEnvironment, number> = {
  development: 4,
  test: 4,
  production: 12,
};

/**
 * Default password used for seeded users
 */
export const DEFAULT_PASSWORD = 'password123';

/**
 * PasswordHasher provides efficient password hashing for seed operations.
 *
 * Features:
 * - Environment-aware salt rounds (low for dev/test, high for prod)
 * - Hash caching for repeated passwords
 * - Async-only API (no synchronous blocking)
 */
export class PasswordHasher {
  private environment: SeedEnvironment;
  private cache: Map<string, string> = new Map();
  private saltRounds: number;

  constructor(environment: SeedEnvironment = 'development') {
    this.environment = environment;
    this.saltRounds = SALT_ROUNDS[environment];
  }

  /**
   * Get the salt rounds for the current environment
   */
  getSaltRounds(): number {
    return this.saltRounds;
  }

  /**
   * Override salt rounds (useful for testing)
   */
  setSaltRounds(rounds: number): void {
    this.saltRounds = rounds;
    this.cache.clear(); // Clear cache when salt rounds change
  }

  /**
   * Hash a password asynchronously.
   * Uses cache for repeated passwords.
   *
   * @param password - Plain text password
   * @returns Hashed password
   */
  async hash(password: string): Promise<string> {
    // Check cache first
    const cached = this.cache.get(password);
    if (cached) {
      return cached;
    }

    // Generate new hash
    const hash = await bcrypt.hash(password, this.saltRounds);

    // Cache the result
    this.cache.set(password, hash);

    return hash;
  }

  /**
   * Get the default password hash.
   * Pre-computes and caches the default password hash.
   *
   * @returns Hashed default password
   */
  async getDefaultHash(): Promise<string> {
    return this.hash(DEFAULT_PASSWORD);
  }

  /**
   * Check if a password is already hashed (bcrypt format)
   *
   * @param password - Password to check
   * @returns true if already hashed
   */
  isHashed(password: string): boolean {
    return (
      password.startsWith('$2a$') ||
      password.startsWith('$2b$') ||
      password.startsWith('$2y$')
    );
  }

  /**
   * Ensure a password is hashed.
   * Returns as-is if already hashed, otherwise hashes it.
   *
   * @param password - Password (plain or hashed)
   * @returns Hashed password
   */
  async ensureHashed(password: string): Promise<string> {
    if (this.isHashed(password)) {
      return password;
    }
    return this.hash(password);
  }

  /**
   * Pre-compute hashes for a list of passwords.
   * Useful for warming up the cache before batch operations.
   *
   * @param passwords - List of passwords to pre-hash
   */
  async warmup(passwords: string[]): Promise<void> {
    const unique = [...new Set(passwords)];
    await Promise.all(unique.map(p => this.hash(p)));
  }

  /**
   * Clear the hash cache
   */
  clearCache(): void {
    this.cache.clear();
  }

  /**
   * Get cache statistics
   */
  getCacheStats(): { size: number; passwords: string[] } {
    return {
      size: this.cache.size,
      passwords: [...this.cache.keys()],
    };
  }
}

/**
 * Create a password hasher for the given environment
 */
export function createPasswordHasher(
  environment: SeedEnvironment = 'development',
): PasswordHasher {
  return new PasswordHasher(environment);
}

/**
 * Pre-compute a hash for the default password.
 * Use this when you need a one-off hash outside of the PasswordHasher class.
 *
 * @param environment - Target environment
 * @returns Promise resolving to the hashed password
 */
export async function hashDefaultPassword(
  environment: SeedEnvironment = 'development',
): Promise<string> {
  const hasher = new PasswordHasher(environment);
  return hasher.getDefaultHash();
}
