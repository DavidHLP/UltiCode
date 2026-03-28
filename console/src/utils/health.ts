import { apiGet } from "./request";

/**
 * Backend health check utilities
 */

let healthCheckCache: {
  isHealthy: boolean | null;
  timestamp: number;
} | null = null;

const CACHE_TTL = 30000; // 30 seconds

/**
 * Check if backend service is healthy
 *
 * Uses cached result for 30 seconds to avoid excessive requests.
 *
 * @returns true if backend is healthy, false otherwise
 */
export async function checkBackendHealth(): Promise<boolean> {
  // Return cached result if fresh
  if (healthCheckCache) {
    const age = Date.now() - healthCheckCache.timestamp;
    if (age < CACHE_TTL && healthCheckCache.isHealthy !== null) {
      return healthCheckCache.isHealthy;
    }
  }

  try {
    // Call backend health endpoint (public endpoint, no auth required)
    await apiGet<{ status: string }>("/actuator/health", {
      skipErrorHandler: true,
    });
    healthCheckCache = { isHealthy: true, timestamp: Date.now() };
    return true;
  } catch {
    healthCheckCache = { isHealthy: false, timestamp: Date.now() };
    return false;
  }
}

/**
 * Clear health check cache
 * Call this if you need to force a fresh health check
 */
export function clearHealthCheckCache(): void {
  healthCheckCache = null;
}
