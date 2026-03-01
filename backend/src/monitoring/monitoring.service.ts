import { Injectable, Logger } from '@nestjs/common';
import { InjectQueue } from '@nestjs/bullmq';
import type { Queue } from 'bullmq';
import type { Cache } from 'cache-manager';
import { CACHE_MANAGER } from '@nestjs/cache-manager';
import { Inject } from '@nestjs/common';
import { PrismaService } from '../prisma.service';
import * as os from 'os';
import { ConfigService } from '@nestjs/config';
import {
  SystemInfoDto,
  ResourceUsageDto,
  DatabaseStatsDto,
  QueueStatsDto,
  RedisStatsDto,
  SystemHealthDto,
  HealthCheckResultDto,
} from './dto/system-info.dto';

@Injectable()
export class MonitoringService {
  private readonly logger = new Logger(MonitoringService.name);
  private queryCount = 0;
  private slowQueryCount = 0;
  private lastQueryTime: Date | null = null;

  constructor(
    private prisma: PrismaService,
    private configService: ConfigService,
    @Inject(CACHE_MANAGER) private cacheManager: Cache,
    @InjectQueue('judge_queue') private judgeQueue: Queue,
  ) {}

  getSystemInfo(): SystemInfoDto {
    return {
      uptime: process.uptime(),
      nodeVersion: process.version,
      platform: process.platform,
      hostname: os.hostname(),
      env: this.configService.get('NODE_ENV', 'development'),
      pid: process.pid,
      version: this.configService.get('npm_package_version', '1.0.0'),
    };
  }

  getResourceUsage(): ResourceUsageDto {
    const memoryUsage = process.memoryUsage();
    const cpuUsage = process.cpuUsage();

    return {
      memory: {
        rss: memoryUsage.rss,
        heapTotal: memoryUsage.heapTotal,
        heapUsed: memoryUsage.heapUsed,
        external: memoryUsage.external,
      },
      cpuUser: cpuUsage.user,
      cpuSystem: cpuUsage.system,
      freeMem: os.freemem(),
      totalMem: os.totalmem(),
      loadAverage: os.loadavg(),
    };
  }

  async getDatabaseStats(): Promise<DatabaseStatsDto> {
    try {
      // Get connection count from Prisma
      const result = await this.prisma.$queryRaw<Array<{ count: bigint }>>`
        SELECT count(*) as count
        FROM information_schema.processlist
        WHERE db = DATABASE()
      `;

      const activeConnections = Number(result[0]?.[0]?.count ?? 0);

      return {
        activeConnections,
        maxConnections: 100, // Default MySQL max connections
        queryCount: this.queryCount,
        slowQueries: this.slowQueryCount,
        lastQueryTime: this.lastQueryTime,
      };
    } catch (error) {
      this.logger.error('Failed to get database stats', error);
      return {
        activeConnections: 0,
        maxConnections: 100,
        queryCount: this.queryCount,
        slowQueries: this.slowQueryCount,
        lastQueryTime: this.lastQueryTime,
      };
    }
  }

  async getQueueStats(): Promise<QueueStatsDto[]> {
    const queues: QueueStatsDto[] = [];

    try {
      const [waiting, active, completed, failed, delayed] = await Promise.all([
        this.judgeQueue.getWaitingCount(),
        this.judgeQueue.getActiveCount(),
        this.judgeQueue.getCompletedCount(),
        this.judgeQueue.getFailedCount(),
        this.judgeQueue.getDelayedCount(),
      ]);

      const isPaused = await this.judgeQueue.isPaused();

      queues.push({
        name: 'judge_queue',
        waiting,
        active,
        completed,
        failed,
        delayed,
        paused: isPaused,
      });
    } catch (error) {
      this.logger.error('Failed to get queue stats', error);
      queues.push({
        name: 'judge_queue',
        waiting: 0,
        active: 0,
        completed: 0,
        failed: 0,
        delayed: 0,
        paused: false,
      });
    }

    return queues;
  }

  async getRedisStats(): Promise<RedisStatsDto> {
    try {
      // Try to get Redis info through cache manager
      const _startTime = Date.now();

      // Simple ping test
      await this.cacheManager.get('__health_check__');

      // If we get here, Redis is connected
      // Try to get more info if available
      let version: string | undefined;
      let usedMemory: number | undefined;
      const totalKeys = 0;
      let uptime: number | undefined;
      let connectedClients: number | undefined;

      // Note: Detailed Redis stats require direct Redis client access
      // For now, we return basic connectivity status

      return {
        connected: true,
        version,
        usedMemory,
        totalKeys,
        uptime,
        connectedClients,
      };
    } catch (error) {
      this.logger.error('Failed to get Redis stats', error);
      return {
        connected: false,
      };
    }
  }

  async getHealthCheck(): Promise<SystemHealthDto> {
    const checks: HealthCheckResultDto[] = [];
    let hasUnhealthy = false;
    let hasDegraded = false;

    // Check database
    const dbCheck = await this.checkDatabase();
    checks.push(dbCheck);
    if (dbCheck.status === 'unhealthy') hasUnhealthy = true;
    else if (dbCheck.status === 'degraded') hasDegraded = true;

    // Check Redis
    const redisCheck = await this.checkRedis();
    checks.push(redisCheck);
    if (redisCheck.status === 'unhealthy') hasDegraded = true;
    else if (redisCheck.status === 'degraded') hasDegraded = true;

    // Check queue
    const queueCheck = await this.checkQueue();
    checks.push(queueCheck);
    if (queueCheck.status === 'unhealthy') hasDegraded = true;
    else if (queueCheck.status === 'degraded') hasDegraded = true;

    // Check disk space (basic check)
    const diskCheck = this.checkDiskSpace();
    checks.push(diskCheck);
    if (diskCheck.status === 'unhealthy') hasDegraded = true;
    else if (diskCheck.status === 'degraded') hasDegraded = true;

    let overallStatus: 'healthy' | 'unhealthy' | 'degraded' = 'healthy';
    if (hasUnhealthy) overallStatus = 'unhealthy';
    else if (hasDegraded) overallStatus = 'degraded';

    return {
      status: overallStatus,
      checks,
      timestamp: new Date(),
    };
  }

  private async checkDatabase(): Promise<HealthCheckResultDto> {
    const start = Date.now();
    try {
      await this.prisma.$queryRaw`SELECT 1`;
      const latency = Date.now() - start;

      return {
        service: 'database',
        status: latency < 100 ? 'healthy' : 'degraded',
        latency,
        message:
          latency < 100 ? 'Database responding normally' : 'Database slow',
      };
    } catch (error) {
      return {
        service: 'database',
        status: 'unhealthy',
        latency: Date.now() - start,
        message: 'Database connection failed',
        details: {
          error: error instanceof Error ? error.message : 'Unknown error',
        },
      };
    }
  }

  private async checkRedis(): Promise<HealthCheckResultDto> {
    const start = Date.now();
    try {
      await this.cacheManager.get('__health_check__');
      const latency = Date.now() - start;

      return {
        service: 'redis',
        status: latency < 50 ? 'healthy' : 'degraded',
        latency,
        message: latency < 50 ? 'Redis responding normally' : 'Redis slow',
      };
    } catch (error) {
      return {
        service: 'redis',
        status: 'unhealthy',
        latency: Date.now() - start,
        message: 'Redis connection failed',
        details: {
          error: error instanceof Error ? error.message : 'Unknown error',
        },
      };
    }
  }

  private async checkQueue(): Promise<HealthCheckResultDto> {
    try {
      const isPaused = await this.judgeQueue.isPaused();
      const failedCount = await this.judgeQueue.getFailedCount();

      if (isPaused) {
        return {
          service: 'queue',
          status: 'degraded',
          message: 'Judge queue is paused',
        };
      }

      if (failedCount > 100) {
        return {
          service: 'queue',
          status: 'degraded',
          message: `High number of failed jobs: ${failedCount}`,
          details: { failedCount },
        };
      }

      return {
        service: 'queue',
        status: 'healthy',
        message: 'Queue operating normally',
        details: { failedCount },
      };
    } catch (error) {
      return {
        service: 'queue',
        status: 'unhealthy',
        message: 'Queue connection failed',
        details: {
          error: error instanceof Error ? error.message : 'Unknown error',
        },
      };
    }
  }

  private checkDiskSpace(): HealthCheckResultDto {
    // Basic memory-based check (Node.js doesn't have native disk space API)
    const memoryUsagePercent =
      (process.memoryUsage().heapUsed / process.memoryUsage().heapTotal) * 100;

    if (memoryUsagePercent > 90) {
      return {
        service: 'memory',
        status: 'unhealthy',
        message: `Memory usage critical: ${memoryUsagePercent.toFixed(1)}%`,
        details: { memoryUsagePercent },
      };
    }

    if (memoryUsagePercent > 75) {
      return {
        service: 'memory',
        status: 'degraded',
        message: `Memory usage high: ${memoryUsagePercent.toFixed(1)}%`,
        details: { memoryUsagePercent },
      };
    }

    return {
      service: 'memory',
      status: 'healthy',
      message: `Memory usage normal: ${memoryUsagePercent.toFixed(1)}%`,
      details: { memoryUsagePercent },
    };
  }

  // Track query for stats
  recordQuery(duration: number, isSlow = false): void {
    this.queryCount++;
    this.lastQueryTime = new Date();
    if (isSlow) {
      this.slowQueryCount++;
    }
  }
}
