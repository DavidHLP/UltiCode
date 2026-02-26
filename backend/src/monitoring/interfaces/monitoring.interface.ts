export interface SystemInfo {
  uptime: number;
  nodeVersion: string;
  platform: string;
  hostname: string;
  env: string;
  pid: number;
}

export interface MemoryUsage {
  rss: number;
  heapTotal: number;
  heapUsed: number;
  external: number;
  arrayBuffers: number;
}

export interface CpuUsage {
  user: number;
  system: number;
}

export interface ResourceUsage {
  memory: MemoryUsage;
  cpu: CpuUsage;
  freeMem: number;
  totalMem: number;
  loadAverage: number[];
}

export interface DatabaseStats {
  activeConnections: number;
  maxConnections: number;
  queryCount: number;
  slowQueries: number;
  lastQueryTime: Date | null;
}

export interface QueueStats {
  name: string;
  waiting: number;
  active: number;
  completed: number;
  failed: number;
  delayed: number;
  paused: boolean;
}

export interface RedisStats {
  connected: boolean;
  version?: string;
  usedMemory?: number;
  totalKeys?: number;
  uptime?: number;
  connectedClients?: number;
}

export interface HealthCheckResult {
  service: string;
  status: 'healthy' | 'unhealthy' | 'degraded';
  message?: string;
  latency?: number;
  details?: Record<string, unknown>;
}

export interface SystemHealth {
  status: 'healthy' | 'unhealthy' | 'degraded';
  checks: HealthCheckResult[];
  timestamp: Date;
}
