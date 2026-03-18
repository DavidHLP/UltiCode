import { Test, TestingModule } from '@nestjs/testing';
import { ReportService } from '../services/report.service';
import { PrismaService } from '../../prisma.service';
import { AuditService } from '../../admin/services/audit.service';
import {
  NotFoundException,
  ConflictException,
} from '@nestjs/common';
import { ReportCategory, ReportStatus } from '@prisma/client';

describe('ReportService', () => {
  let service: ReportService;
  let prisma: jest.Mocked<PrismaService>;
  let audit: jest.Mocked<AuditService>;

  const mockPrismaService = {
    report: {
      create: jest.fn(),
      findUnique: jest.fn(),
      findFirst: jest.fn(),
      findMany: jest.fn(),
      update: jest.fn(),
      count: jest.fn(),
    },
    moderationQueue: {
      findUnique: jest.fn(),
      create: jest.fn(),
      update: jest.fn(),
    },
    forumPost: {
      findUnique: jest.fn(),
      update: jest.fn(),
    },
    forumComment: {
      findUnique: jest.fn(),
      update: jest.fn(),
    },
    solution: {
      findUnique: jest.fn(),
      update: jest.fn(),
    },
    solutionComment: {
      findUnique: jest.fn(),
      update: jest.fn(),
    },
    problem: {
      findUnique: jest.fn(),
      update: jest.fn(),
    },
  };

  const mockAuditService = {
    log: jest.fn(),
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        ReportService,
        {
          provide: PrismaService,
          useValue: mockPrismaService,
        },
        {
          provide: AuditService,
          useValue: mockAuditService,
        },
      ],
    }).compile();

    service = module.get<ReportService>(ReportService);
    prisma = module.get(PrismaService);
    audit = module.get(AuditService);
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  describe('create', () => {
    const createDto = {
      entity_type: 'forum_post' as const,
      entity_id: 'post-1',
      category: ReportCategory.SPAM,
      reason: 'Spam content',
      evidence: 'Link to spam',
    };

    it('should create a report and queue entry successfully', async () => {
      const mockEntity = {
        id: 'post-1',
        author_id: 'author-1',
      };

      const mockQueue = {
        id: 'queue-1',
        entity_type: 'forum_post',
        entity_id: 'post-1',
        report_count: 1,
        priority: 3,
      };

      const mockReport = {
        id: 'report-1',
        reporter_id: 'user-1',
        ...createDto,
        status: ReportStatus.PENDING,
      };

      mockPrismaService.forumPost.findUnique.mockResolvedValue(mockEntity as any);
      mockPrismaService.report.findFirst.mockResolvedValue(null);
      mockPrismaService.moderationQueue.findUnique.mockResolvedValue(null);
      mockPrismaService.moderationQueue.create.mockResolvedValue(mockQueue as any);
      mockPrismaService.report.create.mockResolvedValue(mockReport as any);
      mockPrismaService.forumPost.update.mockResolvedValue({} as any);

      const result = await service.create('user-1', createDto);

      expect(result).toEqual(mockReport);
      expect(mockPrismaService.moderationQueue.create).toHaveBeenCalled();
      expect(mockPrismaService.forumPost.update).toHaveBeenCalledWith({
        where: { id: 'post-1' },
        data: {
          is_flagged: true,
          flagged_at: expect.any(Date),
          flagged_reason: 'Spam content',
        },
      });
      expect(mockAuditService.log).toHaveBeenCalled();
    });

    it('should increment report count on existing queue entry', async () => {
      const mockEntity = {
        id: 'post-1',
        author_id: 'author-1',
      };

      const existingQueue = {
        id: 'queue-1',
        entity_type: 'forum_post',
        entity_id: 'post-1',
        report_count: 1,
        priority: 2,
      };

      const mockReport = {
        id: 'report-1',
        reporter_id: 'user-1',
        ...createDto,
      };

      mockPrismaService.forumPost.findUnique.mockResolvedValue(mockEntity as any);
      mockPrismaService.report.findFirst.mockResolvedValue(null);
      mockPrismaService.moderationQueue.findUnique.mockResolvedValue(existingQueue as any);
      mockPrismaService.moderationQueue.update.mockResolvedValue({
        ...existingQueue,
        report_count: 2,
      } as any);
      mockPrismaService.report.create.mockResolvedValue(mockReport as any);
      mockPrismaService.forumPost.update.mockResolvedValue({} as any);

      await service.create('user-1', createDto);

      expect(mockPrismaService.moderationQueue.update).toHaveBeenCalledWith({
        where: { id: 'queue-1' },
        data: {
          report_count: { increment: 1 },
          priority: expect.any(Number),
        },
      });
    });

    it('should throw NotFoundException if entity not found', async () => {
      mockPrismaService.forumPost.findUnique.mockResolvedValue(null);

      await expect(service.create('user-1', createDto)).rejects.toThrow(
        NotFoundException,
      );
    });

    it('should throw ConflictException if user already reported', async () => {
      const mockEntity = {
        id: 'post-1',
        author_id: 'author-1',
      };

      mockPrismaService.forumPost.findUnique.mockResolvedValue(mockEntity as any);
      mockPrismaService.report.findFirst.mockResolvedValue({
        id: 'existing-report',
      } as any);

      await expect(service.create('user-1', createDto)).rejects.toThrow(
        ConflictException,
      );
    });
  });

  describe('findAll', () => {
    it('should return paginated reports', async () => {
      const mockReports = [
        {
          id: 'report-1',
          reporter_id: 'user-1',
          entity_type: 'forum_post',
          entity_id: 'post-1',
          category: ReportCategory.SPAM,
          status: ReportStatus.PENDING,
          reporter: { id: 'user-1', username: 'user1', name: 'User 1', avatar: null },
        },
      ];

      mockPrismaService.report.findMany.mockResolvedValue(mockReports as any);
      mockPrismaService.report.count.mockResolvedValue(1);

      const result = await service.findAll({ page: 1, limit: 20 });

      expect(result.data).toEqual(mockReports);
      expect(result.meta.total).toBe(1);
      expect(result.meta.page).toBe(1);
      expect(result.meta.limit).toBe(20);
    });

    it('should filter by status', async () => {
      mockPrismaService.report.findMany.mockResolvedValue([]);
      mockPrismaService.report.count.mockResolvedValue(0);

      await service.findAll({ page: 1, limit: 20, status: ReportStatus.PENDING });

      expect(mockPrismaService.report.findMany).toHaveBeenCalledWith(
        expect.objectContaining({
          where: expect.objectContaining({
            status: ReportStatus.PENDING,
          }),
        }),
      );
    });

    it('should filter by category', async () => {
      mockPrismaService.report.findMany.mockResolvedValue([]);
      mockPrismaService.report.count.mockResolvedValue(0);

      await service.findAll({
        page: 1,
        limit: 20,
        category: ReportCategory.SPAM,
      });

      expect(mockPrismaService.report.findMany).toHaveBeenCalledWith(
        expect.objectContaining({
          where: expect.objectContaining({
            category: ReportCategory.SPAM,
          }),
        }),
      );
    });

    it('should filter by entity_type', async () => {
      mockPrismaService.report.findMany.mockResolvedValue([]);
      mockPrismaService.report.count.mockResolvedValue(0);

      await service.findAll({ page: 1, limit: 20, entity_type: 'forum_post' });

      expect(mockPrismaService.report.findMany).toHaveBeenCalledWith(
        expect.objectContaining({
          where: expect.objectContaining({
            entity_type: 'forum_post',
          }),
        }),
      );
    });
  });

  describe('findOne', () => {
    it('should return a report by id', async () => {
      const mockReport = {
        id: 'report-1',
        reporter_id: 'user-1',
        entity_type: 'forum_post',
        entity_id: 'post-1',
        category: ReportCategory.SPAM,
        status: ReportStatus.PENDING,
        reporter: { id: 'user-1', username: 'user1', name: 'User 1', avatar: null },
      };

      mockPrismaService.report.findUnique.mockResolvedValue(mockReport as any);

      const result = await service.findOne('report-1');

      expect(result).toEqual(mockReport);
    });

    it('should throw NotFoundException if report not found', async () => {
      mockPrismaService.report.findUnique.mockResolvedValue(null);

      await expect(service.findOne('nonexistent')).rejects.toThrow(
        NotFoundException,
      );
    });
  });

  describe('updateStatus', () => {
    it('should update report status', async () => {
      const mockReport = {
        id: 'report-1',
        reporter_id: 'user-1',
        status: ReportStatus.PENDING,
        queue_id: 'queue-1',
        reporter: { id: 'user-1', username: 'user1' },
      };

      mockPrismaService.report.findUnique.mockResolvedValue(mockReport as any);
      mockPrismaService.report.update.mockResolvedValue({
        ...mockReport,
        status: ReportStatus.RESOLVED,
      } as any);

      const result = await service.updateStatus(
        'report-1',
        ReportStatus.RESOLVED,
        'admin-1',
      );

      expect(result.status).toBe(ReportStatus.RESOLVED);
      expect(mockAuditService.log).toHaveBeenCalled();
    });
  });

  describe('getReportsByEntity', () => {
    it('should return reports for an entity', async () => {
      const mockReports = [
        {
          id: 'report-1',
          entity_type: 'forum_post',
          entity_id: 'post-1',
          category: ReportCategory.SPAM,
          reporter: { id: 'user-1', username: 'user1', name: 'User 1', avatar: null },
        },
      ];

      mockPrismaService.report.findMany.mockResolvedValue(mockReports as any);

      const result = await service.getReportsByEntity('forum_post', 'post-1');

      expect(result).toEqual(mockReports);
      expect(mockPrismaService.report.findMany).toHaveBeenCalledWith({
        where: {
          entity_type: 'forum_post',
          entity_id: 'post-1',
        },
        orderBy: { created_at: 'desc' },
        include: {
          reporter: {
            select: { id: true, username: true, name: true, avatar: true },
          },
        },
      });
    });
  });
});
