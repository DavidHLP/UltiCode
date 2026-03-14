/**
 * Recommendation system interfaces matching the Java DTOs
 */

/**
 * Recommendation scenario types
 */
export enum RecommendScenario {
  DAILY = 'DAILY',
  SIMILAR = 'SIMILAR',
  WEAK_POINT = 'WEAK_POINT',
  CHALLENGE = 'CHALLENGE',
}

/**
 * Request DTO for recommendation service
 */
export interface RecommendRequest {
  /** User identifier (required) */
  userId: string;
  /** Number of recommendations to return (default: 10) */
  size?: number;
  /** Recommendation scenario (default: DAILY) */
  scenario?: RecommendScenario;
  /** Source problem ID for SIMILAR scenario */
  sourceProblemId?: number;
  /** Target tags for filtering recommendations */
  targetTags?: string[];
  /** Whether to include already solved problems (default: false) */
  includeSolved?: boolean;
}

/**
 * Single recommended problem item
 */
export interface RecommendItem {
  /** Unique identifier of the problem */
  problemId: number;
  /** URL-friendly slug for the problem */
  slug: string;
  /** Display title of the problem */
  title: string;
  /** Difficulty level (e.g., "Easy", "Medium", "Hard") */
  difficulty: string;
  /** Recommendation score (0.0 to 1.0, higher is better) */
  score: number;
  /** Tags associated with the problem */
  tags: string[];
  /** Human-readable reason for why this problem was recommended */
  reason: string;
}

/**
 * Result DTO containing recommendation items
 */
export interface RecommendResult {
  /** List of recommended items */
  items: RecommendItem[];
  /** Total count of available recommendations */
  totalCount: number;
  /** The scenario used for generating recommendations */
  scenario: RecommendScenario;
  /** Timestamp when recommendations were generated */
  generatedAt: string;
}

/**
 * Generic response wrapper for recommendation service
 */
export interface RecommendResponse<T> {
  /** Indicates whether the request was successful */
  success: boolean;
  /** Response code (0 for success, non-zero for errors) */
  code: number;
  /** Human-readable message describing the result */
  message: string;
  /** The response data payload */
  data: T | null;
}

/**
 * Nacos service instance
 */
export interface NacosServiceInstance {
  instanceId: string;
  ip: string;
  port: number;
  serviceName: string;
  healthy: boolean;
  enabled: boolean;
  weight: number;
  metadata: Record<string, string>;
}

/**
 * Recommendation service configuration
 */
export interface RecommendationConfig {
  /** Whether recommendation service is enabled */
  enabled: boolean;
  /** Nacos server address */
  nacosServerAddr: string;
  /** Nacos namespace */
  nacosNamespace: string;
  /** Nacos group */
  nacosGroup: string;
  /** Service name to discover */
  serviceName: string;
  /** Request timeout in milliseconds */
  timeout: number;
  /** Fallback direct URL (bypasses Nacos discovery) */
  fallbackUrl?: string;
}
