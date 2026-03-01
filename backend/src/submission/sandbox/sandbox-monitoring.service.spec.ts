import { Test, TestingModule } from '@nestjs/testing';
import {
  SandboxMonitoringService,
  MetricsSummary,
} from './sandbox-monitoring.service';
import { PrismaService } from '../../prisma.service';
import { ExecutionStatus } from '@prisma/client';

describe('SandboxMonitoringService', () => {
  let service: SandboxMonitoringService;
  let prisma: any;

  const mockPrismaService = {
    sandboxExecutionLog: {
      create: jest.fn(),
      update: jest.fn(),
      findMany: jest.fn(),
      count: jest.fn(),
      deleteMany: jest.fn(),
    },
    sandboxMetrics: {
      findUnique: jest.fn(),
      create: jest.fn(),
      update: jest.fn(),
      findMany: jest.fn(),
    },
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        SandboxMonitoringService,
        {
          provide: PrismaService,
          useValue: mockPrismaService,
        },
      ],
    }).compile();

    service = module.get<SandboxMonitoringService>(SandboxMonitoringService);
    prisma = module.get(PrismaService);
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  describe('startExecution', () => {
    it('should create a new execution log entry with RUNNING status', async () => {
      const mockLog = {
        id: '1',
        execution_id: 'test-exec-id',
        submission_id: 'sub-1',
        language: 'javascript',
        status: 'RUNNING' as ExecutionStatus,
        container_id: 'container-123',
        started_at: new Date(),
        completed_at: null,
        time_ms: null,
        memory_bytes: null,
        exit_code: null,
        error_message: null,
      };

      prisma.sandboxExecutionLog.create.mockResolvedValue(mockLog);

      const result = await service.startExecution({
        executionId: 'test-exec-id',
        submissionId: 'sub-1',
        language: 'javascript',
        containerId: 'container-123',
      });

      expect(result).toEqual(mockLog);
      expect(prisma.sandboxExecutionLog.create).toHaveBeenCalledWith({
        data: {
          execution_id: 'test-exec-id',
          submission_id: 'sub-1',
          language: 'javascript',
          status: 'RUNNING',
          container_id: 'container-123',
        },
      });
    });

    it('should create execution log without optional fields', async () => {
      const mockLog = {
        id: '1',
        execution_id: 'test-exec-id',
        submission_id: null,
        language: 'python',
        status: 'RUNNING' as ExecutionStatus,
        container_id: null,
        started_at: new Date(),
        completed_at: null,
        time_ms: null,
        memory_bytes: null,
        exit_code: null,
        error_message: null,
      };

      prisma.sandboxExecutionLog.create.mockResolvedValue(mockLog);

      const result = await service.startExecution({
        executionId: 'test-exec-id',
        language: 'python',
      });

      expect(result).toEqual(mockLog);
      expect(prisma.sandboxExecutionLog.create).toHaveBeenCalledWith({
        data: {
          execution_id: 'test-exec-id',
          submission_id: undefined,
          language: 'python',
          status: 'RUNNING',
          container_id: undefined,
        },
      });
    });
  });

  describe('recordError', () => {
    it('should record error from Error object', async () => {
      prisma.sandboxExecutionLog.update.mockResolvedValue({});
      prisma.sandboxMetrics.findUnique.mockResolvedValue(null);
      prisma.sandboxMetrics.create.mockResolvedValue({});

      const error = new Error('Test error message');
      await service.recordError('exec-id', error, 1);

      expect(prisma.sandboxExecutionLog.update).toHaveBeenCalledWith({
        where: { execution_id: 'exec-id' },
        data: {
          status: 'SYSTEM_ERROR',
          error_message: 'Test error message',
          exit_code: 1,
          completed_at: expect.any(Date),
        },
      });
    });

    it('should record error from string', async () => {
      prisma.sandboxExecutionLog.update.mockResolvedValue({});
      prisma.sandboxMetrics.findUnique.mockResolvedValue(null);
      prisma.sandboxMetrics.create.mockResolvedValue({});

      await service.recordError('exec-id', 'String error message', 2);

      expect(prisma.sandboxExecutionLog.update).toHaveBeenCalledWith({
        where: { execution_id: 'exec-id' },
        data: {
          status: 'SYSTEM_ERROR',
          error_message: 'String error message',
          exit_code: 2,
          completed_at: expect.any(Date),
        },
      });
    });
  });

  describe('updateContainerId', () => {
    it('should update container ID for execution', async () => {
      prisma.sandboxExecutionLog.update.mockResolvedValue({});

      await service.updateContainerId('exec-id', 'container-456');

      expect(prisma.sandboxExecutionLog.update).toHaveBeenCalledWith({
        where: { execution_id: 'exec-id' },
        data: { container_id: 'container-456' },
      });
    });

    it('should not throw if update fails', async () => {
      prisma.sandboxExecutionLog.update.mockRejectedValue(
        new Error('DB error'),
      );

      // Should not throw
      await expect(
        service.updateContainerId('exec-id', 'container-456'),
      ).resolves.toBeUndefined();
    });
  });

  describe('getMetricsSummary', () => {
    it('should return empty metrics when no data', async () => {
      prisma.sandboxMetrics.findMany.mockResolvedValue([]);

      const startDate = new Date('2024-01-01');
      const endDate = new Date('2024-01-31');

      const result = await service.getMetricsSummary(startDate, endDate);

      expect(result).toEqual<MetricsSummary>({
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
      });
    });

    it('should aggregate metrics from multiple days', async () => {
      const mockMetrics = [
        {
          date: new Date('2024-01-01'),
          total_executions: 100,
          successful: 80,
          timeouts: 5,
          memory_exceeded: 3,
          runtime_errors: 7,
          compile_errors: 3,
          system_errors: 2,
          avg_time_ms: 150,
          max_time_ms: 500,
          avg_memory_bytes: 1024000,
          max_memory_bytes: 2048000,
        },
        {
          date: new Date('2024-01-02'),
          total_executions: 100,
          successful: 90,
          timeouts: 3,
          memory_exceeded: 2,
          runtime_errors: 3,
          compile_errors: 1,
          system_errors: 1,
          avg_time_ms: 120,
          max_time_ms: 600,
          avg_memory_bytes: 512000,
          max_memory_bytes: 3072000,
        },
      ];

      prisma.sandboxMetrics.findMany.mockResolvedValue(mockMetrics);

      const result = await service.getMetricsSummary(
        new Date('2024-01-01'),
        new Date('2024-01-02'),
      );

      expect(result.totalExecutions).toBe(200);
      expect(result.successful).toBe(170);
      expect(result.timeouts).toBe(8);
      expect(result.maxTimeMs).toBe(600);
      expect(result.maxMemoryBytes).toBe(3072000);
      expect(result.successRate).toBe(85); // 170/200 * 100
    });
  });

  describe('getExecutionLogs', () => {
    it('should return paginated logs without filters', async () => {
      const mockLogs = [
        {
          id: '1',
          execution_id: 'exec-1',
          language: 'javascript',
          status: 'COMPLETED',
        },
        {
          id: '2',
          execution_id: 'exec-2',
          language: 'python',
          status: 'COMPLETED',
        },
      ];

      prisma.sandboxExecutionLog.findMany.mockResolvedValue(mockLogs);
      prisma.sandboxExecutionLog.count.mockResolvedValue(2);

      const result = await service.getExecutionLogs({});

      expect(result.logs).toEqual(mockLogs);
      expect(result.total).toBe(2);
      expect(prisma.sandboxExecutionLog.findMany).toHaveBeenCalledWith({
        where: {},
        orderBy: { started_at: 'desc' },
        take: 50,
        skip: 0,
      });
    });

    it('should filter logs by language', async () => {
      const mockLogs = [
        {
          id: '1',
          execution_id: 'exec-1',
          language: 'javascript',
          status: 'COMPLETED',
        },
      ];

      prisma.sandboxExecutionLog.findMany.mockResolvedValue(mockLogs);
      prisma.sandboxExecutionLog.count.mockResolvedValue(1);

      await service.getExecutionLogs({ language: 'javascript' });

      expect(prisma.sandboxExecutionLog.findMany).toHaveBeenCalledWith({
        where: { language: 'javascript' },
        orderBy: { started_at: 'desc' },
        take: 50,
        skip: 0,
      });
    });

    it('should filter logs by status', async () => {
      prisma.sandboxExecutionLog.findMany.mockResolvedValue([]);
      prisma.sandboxExecutionLog.count.mockResolvedValue(0);

      await service.getExecutionLogs({ status: 'TIMEOUT' });

      expect(prisma.sandboxExecutionLog.findMany).toHaveBeenCalledWith({
        where: { status: 'TIMEOUT' },
        orderBy: { started_at: 'desc' },
        take: 50,
        skip: 0,
      });
    });

    it('should apply pagination', async () => {
      prisma.sandboxExecutionLog.findMany.mockResolvedValue([]);
      prisma.sandboxExecutionLog.count.mockResolvedValue(100);

      await service.getExecutionLogs({ limit: 10, offset: 20 });

      expect(prisma.sandboxExecutionLog.findMany).toHaveBeenCalledWith({
        where: {},
        orderBy: { started_at: 'desc' },
        take: 10,
        skip: 20,
      });
    });
  });

  describe('getLanguageMetrics', () => {
    it('should return language-specific metrics', async () => {
      const mockLogs = [
        { language: 'javascript', time_ms: 100, memory_bytes: 1024000 },
        { language: 'javascript', time_ms: 200, memory_bytes: 2048000 },
        { language: 'python', time_ms: 150, memory_bytes: 1536000 },
      ];

      prisma.sandboxExecutionLog.findMany.mockResolvedValue(mockLogs);

      const result = await service.getLanguageMetrics();

      expect(result).toHaveLength(2);

      const jsMetrics = result.find((m) => m.language === 'javascript');
      expect(jsMetrics).toBeDefined();
      expect(jsMetrics?.count).toBe(2);
      expect(jsMetrics?.avgTimeMs).toBe(150); // (100 + 200) / 2
      expect(jsMetrics?.avgMemoryBytes).toBe(1536000); // (1024000 + 2048000) / 2

      const pyMetrics = result.find((m) => m.language === 'python');
      expect(pyMetrics).toBeDefined();
      expect(pyMetrics?.count).toBe(1);
    });

    it('should return empty array when no completed logs', async () => {
      prisma.sandboxExecutionLog.findMany.mockResolvedValue([]);

      const result = await service.getLanguageMetrics();

      expect(result).toEqual([]);
    });
  });

  describe('cleanupOldLogs', () => {
    it('should delete logs older than specified days', async () => {
      prisma.sandboxExecutionLog.deleteMany.mockResolvedValue({ count: 50 });

      const result = await service.cleanupOldLogs(30);

      expect(result).toBe(50);
      expect(prisma.sandboxExecutionLog.deleteMany).toHaveBeenCalledWith({
        where: {
          started_at: { lt: expect.any(Date) },
        },
      });
    });

    it('should use default 30 days if not specified', async () => {
      prisma.sandboxExecutionLog.deleteMany.mockResolvedValue({ count: 0 });

      await service.cleanupOldLogs();

      const callArg = prisma.sandboxExecutionLog.deleteMany.mock.calls[0][0];
      const cutoffDate = callArg.where.started_at.lt as Date;
      const expectedCutoff = new Date();
      expectedCutoff.setDate(expectedCutoff.getDate() - 30);

      // Allow 1 second tolerance for test execution time
      expect(
        Math.abs(cutoffDate.getTime() - expectedCutoff.getTime()),
      ).toBeLessThan(1000);
    });
  });
});
