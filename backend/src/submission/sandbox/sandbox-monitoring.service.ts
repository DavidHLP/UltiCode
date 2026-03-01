import { Injectable, Logger } from '@nestjs/common';
import { PrismaService } from '../../prisma.service';
import { SandboxExecutionLog, ExecutionStatus } from '@prisma/client';

export interface ExecutionLogData {
  executionId: string;
  submissionId?: string;
  language: string;
  status: ExecutionStatus;
  timeMs?: number;
  memoryBytes?: number;
  exitCode?: number;
  errorMessage?: string;
  containerId?: string;
}

export interface MetricsSummary {
  totalExecutions: number;
  successful: number;
  timeouts: number;
  memoryExceeded: number;
  runtimeErrors: number;
  compileErrors: number;
  systemErrors: number;
  avgTimeMs: number;
  maxTimeMs: number;
  avgMemoryBytes: number;
  maxMemoryBytes: number;
  successRate: number;
}

@Injectable()
export class SandboxMonitoringService {
  private readonly logger = new Logger(SandboxMonitoringService.name);

  constructor(private readonly prisma: PrismaService) {}

  /**
   * Start a new execution log entry
   */
  async startExecution(data: {
    executionId: string;
    submissionId?: string;
    language: string;
    containerId?: string;
  }): Promise<SandboxExecutionLog> {
    return this.prisma.sandboxExecutionLog.create({
      data: {
        execution_id: data.executionId,
        submission_id: data.submissionId,
        language: data.language,
        status: 'RUNNING',
        container_id: data.containerId,
      },
    });
  }

  /**
   * Complete an execution log entry with results
   */
  async completeExecution(
    executionId: string,
    result: {
      status: ExecutionStatus;
      timeMs?: number;
      memoryBytes?: number;
      exitCode?: number;
      errorMessage?: string;
    },
  ): Promise<SandboxExecutionLog | null> {
    const log = await this.prisma.sandboxExecutionLog.update({
      where: { execution_id: executionId },
      data: {
        status: result.status,
        time_ms: result.timeMs,
        memory_bytes: result.memoryBytes,
        exit_code: result.exitCode,
        error_message: result.errorMessage,
        completed_at: new Date(),
      },
    });

    // Update daily metrics
    await this.updateDailyMetrics(
      result.status,
      result.timeMs,
      result.memoryBytes,
    );

    return log;
  }

  /**
   * Record an error during execution
   */
  async recordError(
    executionId: string,
    error: Error | string,
    exitCode?: number,
  ): Promise<void> {
    const errorMessage = error instanceof Error ? error.message : error;

    await this.prisma.sandboxExecutionLog.update({
      where: { execution_id: executionId },
      data: {
        status: 'SYSTEM_ERROR',
        error_message: errorMessage,
        exit_code: exitCode,
        completed_at: new Date(),
      },
    });

    await this.updateDailyMetrics('SYSTEM_ERROR');
  }

  /**
   * Update the container ID for an execution
   */
  async updateContainerId(
    executionId: string,
    containerId: string,
  ): Promise<void> {
    await this.prisma.sandboxExecutionLog
      .update({
        where: { execution_id: executionId },
        data: { container_id: containerId },
      })
      .catch(() => {
        // Ignore if update fails
      });
  }

  /**
   * Update daily aggregated metrics
   */
  private async updateDailyMetrics(
    status: ExecutionStatus,
    timeMs?: number,
    memoryBytes?: number,
  ): Promise<void> {
    const today = new Date();
    today.setHours(0, 0, 0, 0);

    try {
      // Upsert metrics for today
      const existing = await this.prisma.sandboxMetrics.findUnique({
        where: { date: today },
      });

      if (existing) {
        // Calculate new averages
        const newTotal = existing.total_executions + 1;
        const newAvgTime =
          timeMs !== undefined
            ? (existing.avg_time_ms ?? 0 * existing.total_executions + timeMs) /
              newTotal
            : existing.avg_time_ms;
        const newMaxTime =
          timeMs !== undefined
            ? Math.max(existing.max_time_ms ?? 0, timeMs)
            : existing.max_time_ms;
        const newAvgMemory =
          memoryBytes !== undefined
            ? ((existing.avg_memory_bytes ?? 0) * existing.total_executions +
                memoryBytes) /
              newTotal
            : existing.avg_memory_bytes;
        const newMaxMemory =
          memoryBytes !== undefined
            ? Math.max(existing.max_memory_bytes ?? 0, memoryBytes)
            : existing.max_memory_bytes;

        await this.prisma.sandboxMetrics.update({
          where: { date: today },
          data: {
            total_executions: newTotal,
            successful: existing.successful + (status === 'COMPLETED' ? 1 : 0),
            timeouts: existing.timeouts + (status === 'TIMEOUT' ? 1 : 0),
            memory_exceeded:
              existing.memory_exceeded + (status === 'MEMORY_EXCEEDED' ? 1 : 0),
            runtime_errors:
              existing.runtime_errors + (status === 'RUNTIME_ERROR' ? 1 : 0),
            compile_errors:
              existing.compile_errors + (status === 'COMPILE_ERROR' ? 1 : 0),
            system_errors:
              existing.system_errors + (status === 'SYSTEM_ERROR' ? 1 : 0),
            avg_time_ms: newAvgTime,
            max_time_ms: newMaxTime,
            avg_memory_bytes: newAvgMemory,
            max_memory_bytes: newMaxMemory,
          },
        });
      } else {
        await this.prisma.sandboxMetrics.create({
          data: {
            date: today,
            total_executions: 1,
            successful: status === 'COMPLETED' ? 1 : 0,
            timeouts: status === 'TIMEOUT' ? 1 : 0,
            memory_exceeded: status === 'MEMORY_EXCEEDED' ? 1 : 0,
            runtime_errors: status === 'RUNTIME_ERROR' ? 1 : 0,
            compile_errors: status === 'COMPILE_ERROR' ? 1 : 0,
            system_errors: status === 'SYSTEM_ERROR' ? 1 : 0,
            avg_time_ms: timeMs,
            max_time_ms: timeMs,
            avg_memory_bytes: memoryBytes,
            max_memory_bytes: memoryBytes,
          },
        });
      }
    } catch (error) {
      this.logger.error(`Failed to update daily metrics: ${error}`);
    }
  }

  /**
   * Get metrics summary for a date range
   */
  async getMetricsSummary(
    startDate: Date,
    endDate: Date,
  ): Promise<MetricsSummary> {
    const metrics = await this.prisma.sandboxMetrics.findMany({
      where: {
        date: {
          gte: startDate,
          lte: endDate,
        },
      },
    });

    if (metrics.length === 0) {
      return {
        totalExecutions: 0,
        successful: 0,
        timeouts: 0,
        memoryExceeded: 0,
        runtimeErrors: 0,
        compileErrors: 0,
        systemErrors: 0,
        avgTimeMs: 0,
        maxTimeMs: 0,
        avgMemoryBytes: 0,
        maxMemoryBytes: 0,
        successRate: 0,
      };
    }

    const totals = metrics.reduce(
      (acc, m) => ({
        totalExecutions: acc.totalExecutions + m.total_executions,
        successful: acc.successful + m.successful,
        timeouts: acc.timeouts + m.timeouts,
        memoryExceeded: acc.memoryExceeded + m.memory_exceeded,
        runtimeErrors: acc.runtimeErrors + m.runtime_errors,
        compileErrors: acc.compileErrors + m.compile_errors,
        systemErrors: acc.systemErrors + m.system_errors,
        maxTimeMs: Math.max(acc.maxTimeMs, m.max_time_ms ?? 0),
        maxMemoryBytes: Math.max(acc.maxMemoryBytes, m.max_memory_bytes ?? 0),
      }),
      {
        totalExecutions: 0,
        successful: 0,
        timeouts: 0,
        memoryExceeded: 0,
        runtimeErrors: 0,
        compileErrors: 0,
        systemErrors: 0,
        maxTimeMs: 0,
        maxMemoryBytes: 0,
      },
    );

    // Calculate weighted averages
    const totalExecs = metrics.reduce((sum, m) => sum + m.total_executions, 0);
    const avgTimeMs =
      metrics.reduce(
        (sum, m) => sum + (m.avg_time_ms ?? 0) * m.total_executions,
        0,
      ) / totalExecs;
    const avgMemoryBytes =
      metrics.reduce(
        (sum, m) => sum + (m.avg_memory_bytes ?? 0) * m.total_executions,
        0,
      ) / totalExecs;

    return {
      ...totals,
      avgTimeMs: Math.round(avgTimeMs),
      avgMemoryBytes: Math.round(avgMemoryBytes),
      successRate:
        totals.totalExecutions > 0
          ? Math.round((totals.successful / totals.totalExecutions) * 100)
          : 0,
    };
  }

  /**
   * Get execution logs with filtering
   */
  async getExecutionLogs(options: {
    submissionId?: string;
    language?: string;
    status?: ExecutionStatus;
    startDate?: Date;
    endDate?: Date;
    limit?: number;
    offset?: number;
  }): Promise<{ logs: SandboxExecutionLog[]; total: number }> {
    const where: Record<string, unknown> = {};

    if (options.submissionId) {
      where.submission_id = options.submissionId;
    }
    if (options.language) {
      where.language = options.language;
    }
    if (options.status) {
      where.status = options.status;
    }
    if (options.startDate || options.endDate) {
      where.started_at = {};
      if (options.startDate) {
        (where.started_at as Record<string, unknown>).gte = options.startDate;
      }
      if (options.endDate) {
        (where.started_at as Record<string, unknown>).lte = options.endDate;
      }
    }

    const [logs, total] = await Promise.all([
      this.prisma.sandboxExecutionLog.findMany({
        where,
        orderBy: { started_at: 'desc' },
        take: options.limit ?? 50,
        skip: options.offset ?? 0,
      }),
      this.prisma.sandboxExecutionLog.count({ where }),
    ]);

    return { logs, total };
  }

  /**
   * Get language-specific metrics
   */
  async getLanguageMetrics(): Promise<
    Array<{
      language: string;
      count: number;
      avgTimeMs: number;
      avgMemoryBytes: number;
    }>
  > {
    const logs = await this.prisma.sandboxExecutionLog.findMany({
      where: { status: 'COMPLETED' },
      select: {
        language: true,
        time_ms: true,
        memory_bytes: true,
      },
    });

    const languageMap = new Map<
      string,
      { count: number; totalTime: number; totalMemory: number }
    >();

    for (const log of logs) {
      const existing = languageMap.get(log.language) || {
        count: 0,
        totalTime: 0,
        totalMemory: 0,
      };
      existing.count++;
      existing.totalTime += log.time_ms ?? 0;
      existing.totalMemory += log.memory_bytes ?? 0;
      languageMap.set(log.language, existing);
    }

    return Array.from(languageMap.entries()).map(([language, data]) => ({
      language,
      count: data.count,
      avgTimeMs: Math.round(data.totalTime / data.count),
      avgMemoryBytes: Math.round(data.totalMemory / data.count),
    }));
  }

  /**
   * Clean up old execution logs (retention policy)
   */
  async cleanupOldLogs(olderThanDays: number = 30): Promise<number> {
    const cutoff = new Date();
    cutoff.setDate(cutoff.getDate() - olderThanDays);

    const result = await this.prisma.sandboxExecutionLog.deleteMany({
      where: {
        started_at: { lt: cutoff },
      },
    });

    this.logger.log(
      `Cleaned up ${result.count} execution logs older than ${olderThanDays} days`,
    );
    return result.count;
  }
}
