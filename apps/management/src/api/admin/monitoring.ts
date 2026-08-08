import { apiGet } from '@/utils/request'

/**
 * Monitoring API types — aligned with backend VOs in
 * services/app/app-web/src/main/java/com/ulticode/modules/monitoring/dto/
 *
 * Last reconciled: 2026-06-08 (fix/monitoring-issues).
 * If you change backend DTOs, update this file in the same PR.
 */

export interface SystemInfo {
  /** Seconds since JVM started */
  uptime: number
  /** JDK version (e.g. "17.0.2") */
  javaVersion: string
  /** OS name from java.os.name */
  platform: string
  /** Best-effort hostname; "unknown" if resolution fails */
  hostname: string
  /** Active Spring profile */
  env: string
  /** Process ID; -1 if the JVM refused to disclose it */
  pid: number
  /** Application version from app.version */
  version: string
}

export interface MemoryInfo {
  /** Bytes of heap currently in use */
  heapUsed: number
  /** Maximum heap bytes (-Xmx) */
  heapMax: number
  /** Bytes of non-heap memory (Metaspace + CodeCache) */
  nonHeapUsed: number
}

export interface CpuInfo {
  /** Process CPU load fraction in [0, 1] */
  processCpuLoad: number
  /** System-wide CPU load fraction in [0, 1] */
  systemCpuLoad: number
  /** Number of processors available to the JVM */
  availableProcessors: number
}

export interface ResourceUsage {
  memory: MemoryInfo
  cpu: CpuInfo
  /** Number of live JVM threads */
  threadCount: number
}

export interface DatabaseStats {
  /** Current Threads_connected from MySQL */
  activeConnections: number
  /** max_connections setting */
  maxConnections: number
  /** Cumulative SQL executions since process start */
  queryCount: number
  /** Cumulative slow-query executions (cost > app.monitoring.slow-query-ms) */
  slowQueries: number
  /** "healthy" | "unhealthy" */
  status: string
}

export interface QueueStats {
  name: string
  /** Jobs waiting to be picked up */
  waiting: number
  /** Jobs currently being processed */
  active: number
  /** Jobs completed since process start */
  completed: number
  /** Jobs that failed since process start */
  failed: number
  /** Jobs scheduled with a delay */
  delayed: number
}

export interface RedisStats {
  /** True if INFO responded */
  connected: boolean
  /** Server version, e.g. "7.4.8" */
  version?: string
  /** used_memory in bytes */
  usedMemory?: number
  /** Currently connected client count */
  connectedClients?: number
  /** Total keys across all logical DBs */
  totalKeys?: number
  /** Seconds since Redis server started */
  uptimeInSeconds?: number
}

export interface HealthCheckResult {
  /** Service name: "database" | "redis" | "queues" */
  service: string
  /** "healthy" | "degraded" | "unhealthy" */
  status: 'healthy' | 'degraded' | 'unhealthy'
  /** Probe latency in ms */
  latency?: number
  /** Human-readable detail (e.g. "Database responding normally") */
  message?: string
}

export interface SystemHealth {
  /** Aggregated: unhealthy > degraded > healthy */
  status: 'healthy' | 'degraded' | 'unhealthy'
  checks: HealthCheckResult[]
  /** ISO-8601 instant when the check ran */
  timestamp: string
}

interface ApiAllStats {
  system: SystemInfo
  resources: ResourceUsage
  database: DatabaseStats
  queues: QueueStats[]
  redis: RedisStats
  health: SystemHealth
}

export const monitoringApi = {
  async getSystemInfo(): Promise<SystemInfo> {
    return apiGet<SystemInfo>('/monitoring/system')
  },

  async getResourceUsage(): Promise<ResourceUsage> {
    return apiGet<ResourceUsage>('/monitoring/resources')
  },

  async getDatabaseStats(): Promise<DatabaseStats> {
    return apiGet<DatabaseStats>('/monitoring/database')
  },

  async getQueueStats(): Promise<QueueStats[]> {
    return apiGet<QueueStats[]>('/monitoring/queues')
  },

  async getRedisStats(): Promise<RedisStats> {
    return apiGet<RedisStats>('/monitoring/redis')
  },

  async getHealth(): Promise<SystemHealth> {
    return apiGet<SystemHealth>('/monitoring/health')
  },

  /**
   * Fetch all six monitoring endpoints in parallel.
   *
   * Each individual endpoint is wrapped in a generic `safe()` helper so
   * a single failing endpoint (e.g. Redis down) does not blank out the
   * entire dashboard. On failure we log a warning and substitute a
   * sensible default for that field. `Promise.all` waits for all six
   * slots to resolve, but each slot independently degrades.
   */
  async getAllStats(): Promise<ApiAllStats> {
    const safe = async <T>(label: string, p: Promise<T>, fallback: T): Promise<T> => {
      try {
        return await p
      } catch (e) {
        console.warn(`[monitoringApi] ${label} failed:`, e)
        return fallback
      }
    }
    const [system, resources, database, queues, redis, health] = await Promise.all([
      safe('getSystemInfo', this.getSystemInfo(), {
        uptime: 0,
        javaVersion: 'unknown',
        platform: 'unknown',
        hostname: 'unknown',
        env: 'unknown',
        pid: -1,
        version: 'unknown',
      }),
      safe('getResourceUsage', this.getResourceUsage(), {
        memory: { heapUsed: 0, heapMax: 0, nonHeapUsed: 0 },
        cpu: { processCpuLoad: 0, systemCpuLoad: 0, availableProcessors: 0 },
        threadCount: 0,
      }),
      safe('getDatabaseStats', this.getDatabaseStats(), {
        activeConnections: 0,
        maxConnections: 0,
        queryCount: 0,
        slowQueries: 0,
        status: 'unhealthy',
      }),
      safe('getQueueStats', this.getQueueStats(), []),
      safe('getRedisStats', this.getRedisStats(), { connected: false }),
      safe('getHealth', this.getHealth(), {
        status: 'unhealthy',
        checks: [],
        timestamp: new Date().toISOString(),
      }),
    ])
    return { system, resources, database, queues, redis, health }
  },
}
