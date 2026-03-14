/**
 * Feature flags for gradual rollout of new contest system
 * Set via environment variables
 */
export const FEATURE_FLAGS = {
  /**
   * Enable new contest scoring system (point-based instead of Elo)
   */
  USE_NEW_CONTEST_SYSTEM: process.env.FEATURE_NEW_CONTEST === 'true',

  /**
   * Enable real-time ranking updates via WebSocket
   */
  ENABLE_REALTIME_RANKING: process.env.FEATURE_REALTIME_RANKING !== 'false',

  /**
   * Enable first-solve notifications
   */
  ENABLE_FIRST_SOLVE_NOTIFICATIONS:
    process.env.FEATURE_FIRST_SOLVE !== 'false',

  /**
   * Enable anti-cheat detection
   */
  ENABLE_ANTICHEAT: process.env.FEATURE_ANTICHEAT === 'true',

  /**
   * Enable contest analytics generation
   */
  ENABLE_CONTEST_ANALYTICS: process.env.FEATURE_CONTEST_ANALYTICS !== 'false',
} as const;

export type FeatureFlag = keyof typeof FEATURE_FLAGS;

/**
 * Check if a feature flag is enabled
 */
export function isFeatureEnabled(flag: FeatureFlag): boolean {
  return FEATURE_FLAGS[flag] ?? false;
}