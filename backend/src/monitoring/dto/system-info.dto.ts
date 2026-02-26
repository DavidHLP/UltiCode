import { ApiProperty } from '@nestjs/swagger';

export class SystemInfoDto {
  @ApiProperty({ description: 'Process uptime in seconds' })
  uptime: number;

  @ApiProperty({ description: 'Node.js version' })
  nodeVersion: string;

  @ApiProperty({ description: 'Operating system platform' })
  platform: string;

  @ApiProperty({ description: 'Server hostname' })
  hostname: string;

  @ApiProperty({ description: 'Environment (development, production, etc.)' })
  env: string;

  @ApiProperty({ description: 'Process ID' })
  pid: number;

  @ApiProperty({ description: 'Application version' })
  version: string;
}

export class MemoryUsageDto {
  @ApiProperty({ description: 'Resident Set Size in bytes' })
  rss: number;

  @ApiProperty({ description: 'Total heap memory in bytes' })
  heapTotal: number;

  @ApiProperty({ description: 'Used heap memory in bytes' })
  heapUsed: number;

  @ApiProperty({ description: 'External memory in bytes' })
  external: number;
}

export class ResourceUsageDto {
  @ApiProperty({ type: MemoryUsageDto })
  memory: MemoryUsageDto;

  @ApiProperty({ description: 'CPU user time in microseconds' })
  cpuUser: number;

  @ApiProperty({ description: 'CPU system time in microseconds' })
  cpuSystem: number;

  @ApiProperty({ description: 'Free system memory in bytes' })
  freeMem: number;

  @ApiProperty({ description: 'Total system memory in bytes' })
  totalMem: number;

  @ApiProperty({ description: 'System load average' })
  loadAverage: number[];
}

export class DatabaseStatsDto {
  @ApiProperty({ description: 'Number of active database connections' })
  activeConnections: number;

  @ApiProperty({ description: 'Maximum database connections' })
  maxConnections: number;

  @ApiProperty({ description: 'Total query count (approximate)' })
  queryCount: number;

  @ApiProperty({ description: 'Number of slow queries detected' })
  slowQueries: number;

  @ApiProperty({ description: 'Time of last query', nullable: true })
  lastQueryTime: Date | null;
}

export class QueueStatsDto {
  @ApiProperty({ description: 'Queue name' })
  name: string;

  @ApiProperty({ description: 'Number of waiting jobs' })
  waiting: number;

  @ApiProperty({ description: 'Number of active jobs' })
  active: number;

  @ApiProperty({ description: 'Number of completed jobs' })
  completed: number;

  @ApiProperty({ description: 'Number of failed jobs' })
  failed: number;

  @ApiProperty({ description: 'Number of delayed jobs' })
  delayed: number;

  @ApiProperty({ description: 'Whether the queue is paused' })
  paused: boolean;
}

export class RedisStatsDto {
  @ApiProperty({ description: 'Whether Redis is connected' })
  connected: boolean;

  @ApiProperty({ description: 'Redis version', required: false })
  version?: string;

  @ApiProperty({ description: 'Used memory in bytes', required: false })
  usedMemory?: number;

  @ApiProperty({ description: 'Total number of keys', required: false })
  totalKeys?: number;

  @ApiProperty({ description: 'Redis uptime in seconds', required: false })
  uptime?: number;

  @ApiProperty({ description: 'Number of connected clients', required: false })
  connectedClients?: number;
}

export class HealthCheckResultDto {
  @ApiProperty({ description: 'Service name' })
  service: string;

  @ApiProperty({
    description: 'Health status',
    enum: ['healthy', 'unhealthy', 'degraded'],
  })
  status: 'healthy' | 'unhealthy' | 'degraded';

  @ApiProperty({ description: 'Status message', required: false })
  message?: string;

  @ApiProperty({ description: 'Response latency in ms', required: false })
  latency?: number;

  @ApiProperty({ description: 'Additional details', required: false })
  details?: Record<string, unknown>;
}

export class SystemHealthDto {
  @ApiProperty({
    description: 'Overall system health status',
    enum: ['healthy', 'unhealthy', 'degraded'],
  })
  status: 'healthy' | 'unhealthy' | 'degraded';

  @ApiProperty({ type: [HealthCheckResultDto] })
  checks: HealthCheckResultDto[];

  @ApiProperty({ description: 'Timestamp of health check' })
  timestamp: Date;
}
