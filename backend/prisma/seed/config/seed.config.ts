import type {
  SeedEnvironment,
  FixtureType,
  EnvironmentConfig,
  FixtureConfig,
} from '../core/interfaces';

/**
 * Environment configurations
 */
export const ENVIRONMENT_CONFIGS: Record<SeedEnvironment, EnvironmentConfig> = {
  development: {
    environment: 'development',
    defaultFixture: 'standard',
    batchSize: 100,
    parallel: true,
    allowClear: true,
    saltRounds: 4,
    transactionTimeout: 120000, // 2 minutes
    allowedModules: 'all',
  },
  test: {
    environment: 'test',
    defaultFixture: 'minimal',
    batchSize: 50,
    parallel: false, // Sequential for predictable test results
    allowClear: true,
    saltRounds: 4,
    transactionTimeout: 60000, // 1 minute
    allowedModules: 'all',
  },
  production: {
    environment: 'production',
    defaultFixture: 'standard',
    batchSize: 100,
    parallel: false, // Sequential for safety
    allowClear: false, // Never auto-clear in production
    saltRounds: 12,
    transactionTimeout: 300000, // 5 minutes
    allowedModules: ['Permissions', 'SubmissionStatuses'], // Only reference data
  },
};

/**
 * Fixture configurations
 */
export const FIXTURE_CONFIGS: Record<FixtureType, FixtureConfig> = {
  minimal: {
    name: 'minimal',
    description: 'Minimal data for quick tests',
    users: 3,
    problems: 2,
    forumPosts: 1,
    contests: 0,
  },
  standard: {
    name: 'standard',
    description: 'Standard development dataset',
    users: 20,
    problems: 8,
    forumPosts: 10,
    contests: 2,
  },
  full: {
    name: 'full',
    description: 'Full dataset for integration testing',
    users: 50,
    problems: 20,
    forumPosts: 30,
    contests: 5,
  },
};

/**
 * Get environment configuration
 */
export function getEnvironmentConfig(
  env?: SeedEnvironment,
): EnvironmentConfig {
  const environment = env || getEnvironment();
  return ENVIRONMENT_CONFIGS[environment];
}

/**
 * Get fixture configuration
 */
export function getFixtureConfig(fixture?: FixtureType): FixtureConfig {
  const fixtureName = fixture || getFixture();
  return FIXTURE_CONFIGS[fixtureName];
}

/**
 * Determine current environment from env vars
 */
export function getEnvironment(): SeedEnvironment {
  const env = process.env.SEED_ENV || process.env.NODE_ENV;
  if (env === 'test' || env === 'testing') return 'test';
  if (env === 'production' || env === 'prod') return 'production';
  return 'development';
}

/**
 * Determine current fixture from env vars
 */
export function getFixture(): FixtureType {
  const fixture = process.env.SEED_FIXTURE;
  if (fixture === 'minimal' || fixture === 'full') return fixture;

  // Default based on environment
  const env = getEnvironment();
  return ENVIRONMENT_CONFIGS[env].defaultFixture;
}

/**
 * Check if a module is allowed in current environment
 */
export function isModuleAllowed(moduleName: string, env?: SeedEnvironment): boolean {
  const config = getEnvironmentConfig(env);
  if (config.allowedModules === 'all') return true;
  return config.allowedModules.includes(moduleName);
}
