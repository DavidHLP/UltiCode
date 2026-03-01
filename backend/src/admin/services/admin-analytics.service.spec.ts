import { Test, TestingModule } from '@nestjs/testing';
import { AdminAnalyticsService } from './admin-analytics.service';
import { PrismaService } from '../../prisma.service';
import { AnalyticsReportType, AnalyticsPeriod } from '../dto/analytics.dto';

describe('AdminAnalyticsService', () => {
  let service: AdminAnalyticsService;
  let prisma: jest.Mocked<PrismaService>;

  const mockPrismaService = {
    $queryRaw: jest.fn(),
    user: {
      count: jest.fn(),
      findMany: jest.fn(),
    },
    problem: {
      findMany: jest.fn(),
      count: jest.fn(),
    },
    submission: {
      count: jest.fn(),
      findMany: jest.fn(),
    },
    contest: {
      findMany: jest.fn(),
      count: jest.fn(),
    },
    contestParticipation: {
      count: jest.fn(),
      findMany: jest.fn(),
    },
    subscription: {
      findMany: jest.fn(),
      count: jest.fn(),
      aggregate: jest.fn(),
    },
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        AdminAnalyticsService,
        {
          provide: PrismaService,
          useValue: mockPrismaService,
        },
      ],
    }).compile();

    service = module.get<AdminAnalyticsService>(AdminAnalyticsService);
    prisma = module.get(PrismaService);
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  describe('getDaysFromPeriod', () => {
    it('should return 1 for DAY period', () => {
      const result = (service as any).getDaysFromPeriod(
        AnalyticsPeriod.DAY,
        30,
      );
      expect(result).toBe(1);
    });

    it('should return 7 for WEEK period', () => {
      const result = (service as any).getDaysFromPeriod(
        AnalyticsPeriod.WEEK,
        30,
      );
      expect(result).toBe(7);
    });

    it('should return 30 for MONTH period', () => {
      const result = (service as any).getDaysFromPeriod(
        AnalyticsPeriod.MONTH,
        30,
      );
      expect(result).toBe(30);
    });

    it('should return 90 for QUARTER period', () => {
      const result = (service as any).getDaysFromPeriod(
        AnalyticsPeriod.QUARTER,
        30,
      );
      expect(result).toBe(90);
    });

    it('should return 365 for YEAR period', () => {
      const result = (service as any).getDaysFromPeriod(
        AnalyticsPeriod.YEAR,
        30,
      );
      expect(result).toBe(365);
    });

    it('should return default for unknown period', () => {
      const result = (service as any).getDaysFromPeriod(
        'unknown' as AnalyticsPeriod,
        42,
      );
      expect(result).toBe(42);
    });
  });

  describe('getDateRange', () => {
    it('should return correct start and end dates', () => {
      const result = (service as any).getDateRange(AnalyticsPeriod.WEEK, 7);

      expect(result).toHaveProperty('start');
      expect(result).toHaveProperty('end');
      expect(result.start instanceof Date).toBe(true);
      expect(result.end instanceof Date).toBe(true);
    });

    it('should use provided start and end dates', () => {
      const startDate = '2025-01-01';
      const endDate = '2025-01-31';

      const result = (service as any).getDateRange(
        AnalyticsPeriod.MONTH,
        30,
        startDate,
        endDate,
      );

      expect(result.start).toEqual(new Date(startDate));
      expect(result.end).toEqual(new Date(endDate));
    });

    it('should calculate correct date range from period', () => {
      const result = (service as any).getDateRange(AnalyticsPeriod.DAY, 30);

      const diffMs = result.end.getTime() - result.start.getTime();
      const diffDays = diffMs / (1000 * 60 * 60 * 24);

      expect(diffDays).toBeCloseTo(1, 0);
    });
  });

  describe('getReport', () => {
    it('should throw error for unknown report type', async () => {
      await expect(
        service.getReport({
          reportType: 'invalid' as AnalyticsReportType,
          period: AnalyticsPeriod.DAY,
        }),
      ).rejects.toThrow('Unknown report type');
    });
  });

  describe('service initialization', () => {
    it('should be defined', () => {
      expect(service).toBeDefined();
    });

    it('should have prisma service', () => {
      expect(prisma).toBeDefined();
    });
  });
});
