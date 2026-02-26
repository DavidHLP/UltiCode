import { apiGet } from '@/utils/request'

export interface SystemInfo {
  uptime: number
  nodeVersion: string
  platform: string
  hostname: string
  env: string
  pid: number
  version: string
}

export interface MemoryUsage {
  rss: number
  heapTotal: number
  heapUsed: number
  external: number
}

export interface ResourceUsage {
  memory: MemoryUsage
  cpuUser: number
  cpuSystem: number
  freeMem: number
  totalMem: number
  loadAverage: number[]
}

export interface DatabaseStats {
  activeConnections: number
  maxConnections: number
  queryCount: number
  slowQueries: number
  lastQueryTime: string | null
}

export interface QueueStats {
  name: string
  waiting: number
  active: number
  completed: number
  failed: number
  delayed: number
  paused: boolean
}

export interface RedisStats {
  connected: boolean
  version?: string
  usedMemory?: number
  totalKeys?: number
  uptime?: number
  connectedClients?: number
}

export interface HealthCheckResult {
  service: string
  status: 'healthy' | 'unhealthy' | 'degraded'
  message?: string
  latency?: number
  details?: Record<string, unknown>
}

export interface SystemHealth {
  status: 'healthy' | 'unhealthy' | 'degraded'
  checks: HealthCheckResult[]
  timestamp: string
}

export const monitoringApi = {
  async getSystemInfo(): Promise<SystemInfo> {
    return apiGet<SystemInfo>('/admin/monitoring/system')
  },

  async getResourceUsage(): Promise<ResourceUsage> {
    return apiGet<ResourceUsage>('/admin/monitoring/resources')
  },

  async getDatabaseStats(): Promise<DatabaseStats> {
    return apiGet<DatabaseStats>('/admin/monitoring/database')
  },

  async getQueueStats(): Promise<QueueStats[]> {
    return apiGet<QueueStats[]>('/admin/monitoring/queues')
  },

  async getRedisStats(): Promise<RedisStats> {
    return apiGet<RedisStats>('/admin/monitoring/redis')
  },

  async getHealth(): Promise<SystemHealth> {
    return apiGet<SystemHealth>('/admin/monitoring/health')
  },

  async getAllStats(): Promise<{
    system: SystemInfo
    resources: ResourceUsage
    database: DatabaseStats
    queues: QueueStats[]
    redis: RedisStats
    health: SystemHealth
  }> {
    const [system, resources, database, queues, redis, health] = await Promise.all([
      this.getSystemInfo(),
      this.getResourceUsage(),
      this.getDatabaseStats(),
      this.getQueueStats(),
      this.getRedisStats(),
      this.getHealth(),
    ])
    return { system, resources, database, queues, redis, health }
  },
}
