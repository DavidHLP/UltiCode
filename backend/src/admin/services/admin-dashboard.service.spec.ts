import { Test, TestingModule } from '@nestjs/testing';
import { AdminDashboardService } from './admin-dashboard.service';
import { PrismaService } from '../../prisma.service';

describe('AdminDashboardService', () => {
  let service: AdminDashboardService;
  let prisma: jest.Mocked<PrismaService>;

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        AdminDashboardService,
        {
          provide: PrismaService,
          useValue: {
            user: {
              count: jest.fn().mockResolvedValue(100),
              groupBy: jest.fn().mockResolvedValue([]),
            },
            problem: {
              count: jest.fn().mockResolvedValue(50),
              groupBy: jest.fn().mockResolvedValue([]),
            },
            contest: {
              count: jest.fn().mockResolvedValue(10),
              groupBy: jest.fn().mockResolvedValue([]),
            },
            submission: {
              count: jest.fn().mockResolvedValue(500),
              groupBy: jest.fn().mockResolvedValue([]),
            },
            solution: {
              count: jest.fn().mockResolvedValue(30),
              groupBy: jest.fn().mockResolvedValue([]),
            },
            forumPost: {
              count: jest.fn().mockResolvedValue(20),
              groupBy: jest.fn().mockResolvedValue([]),
            },
            forumComment: {
              count: jest.fn().mockResolvedValue(50),
            },
            forumCommunity: {
              count: jest.fn().mockResolvedValue(5),
            },
          } as any,
        },
      ],
    }).compile();

    service = module.get<AdminDashboardService>(AdminDashboardService);
    prisma = module.get(PrismaService);
  });

  it('should be defined', () => {
    expect(service).toBeDefined();
  });

  describe('getDashboardStats', () => {
    it('should return dashboard statistics', async () => {
      prisma.user.count.mockResolvedValue(100);
      prisma.problem.count.mockResolvedValue(50);
      prisma.contest.count.mockResolvedValue(10);
      prisma.submission.count.mockResolvedValue(500);
      prisma.solution.count.mockResolvedValue(30);
      prisma.forumPost.count.mockResolvedValue(20);
      prisma.forumComment.count.mockResolvedValue(50);
      prisma.forumCommunity.count.mockResolvedValue(5);

      const result = await service.getDashboardStats();

      expect(result).toBeDefined();
      expect(result.users).toBeDefined();
      expect(result.problems).toBeDefined();
      expect(result.contests).toBeDefined();
      expect(result.submissions).toBeDefined();
      expect(result.solutions).toBeDefined();
      expect(result.forum).toBeDefined();
      expect(result.system).toBeDefined();
    });
  });

  describe('getChartStats', () => {
    it('should return chart statistics', async () => {
      prisma.user.groupBy.mockResolvedValue([]);

      const result = await service.getChartStats({
        period: 'daily' as any,
        metric: 'users' as any,
        days: 7,
      });

      expect(result).toBeDefined();
      expect(result.metric).toBeDefined();
      expect(result.period).toBeDefined();
      expect(result.data).toBeDefined();
    });
  });
});
