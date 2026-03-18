import { Test, TestingModule } from '@nestjs/testing';
import { ModerationQueueService } from '../services/queue.service';
import { PrismaService } from '../../prisma.service';
import { ModerationActionService } from '../services/action.service';
import { AuditService } from '../../admin/services/audit.service';
import {
  ModerationStatus,
  ReportCategory,
  ModerationActionType,
} from '@prisma/client';
import { BadRequestException, NotFoundException } from '@nestjs/common';

describe('ModerationQueueService', () => {
  let service: ModerationQueueService;
  let prisma: jest.Mocked<PrismaService>;
  let actionService: jest.Mocked<ModerationActionService>;
  let auditService: jest.Mocked<AuditService>;

  const mockPrismaService = {
    moderationQueue: {
      findMany: jest.fn(),
      findUnique: jest.fn(),
      findFirst: jest.fn(),
      create: jest.fn(),
      update: jest.fn(),
      count: jest.fn(),
      groupBy: jest.fn(),
    },
    moderationAction: {
      create: jest.fn(),
      findMany: jest.fn(),
    },
    report: {
      updateMany: jest.fn(),
    },
    forumPost: {
      findUnique: jest.fn(),
    },
    forumComment: {
      findUnique: jest.fn(),
    },
    solution: {
      findUnique: jest.fn(),
    },
    solutionComment: {
      findUnique: jest.fn(),
    },
    problem: {
      findUnique: jest.fn(),
    },
    $transaction: jest.fn((cb) => cb(mockPrismaService)),
  };

  const mockActionService = {
    performAction: jest.fn(),
    getActionsByQueue: jest.fn(),
  };

  const mockAuditService = {
    log: jest.fn(),
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        ModerationQueueService,
        {
          provide: PrismaService,
          useValue: mockPrismaService,
        },
        {
          provide: ModerationActionService,
          useValue: mockActionService,
        },
        {
          provide: AuditService,
          useValue: mockAuditService,
        },
      ],
    }).compile();

    service = module.get<ModerationQueueService>(ModerationQueueService);
    prisma = module.get(PrismaService);
    actionService = module.get(ModerationActionService);
    auditService = module.get(AuditService);
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  describe('findAll', () => {
    it('should return paginated queue items', async () => {
      const mockItems = [
        {
          id: '1',
          entity_type: 'forum_post',
          entity_id: 'post-1',
          status: ModerationStatus.PENDING,
          priority: 5,
          primary_category: ReportCategory.SPAM,
          report_count: 3,
          created_at: new Date(),
          updated_at: new Date(),
        },
      ];

      mockPrismaService.moderationQueue.findMany.mockResolvedValue(
        mockItems as any,
      );
      mockPrismaService.moderationQueue.count.mockResolvedValue(1);
      mockPrismaService.forumPost.findUnique.mockResolvedValue({
        title: 'Test Post',
        excerpt: 'Test excerpt',
      } as any);

      const result = await service.findAll({ page: 1, limit: 20 });

      expect(result.data).toHaveLength(1);
      expect(result.meta.total).toBe(1);
      expect(result.meta.page).toBe(1);
      expect(result.meta.limit).toBe(20);
    });

    it('should filter by status', async () => {
      mockPrismaService.moderationQueue.findMany.mockResolvedValue([]);
      mockPrismaService.moderationQueue.count.mockResolvedValue(0);

      await service.findAll({
        page: 1,
        limit: 20,
        status: ModerationStatus.PENDING,
      });

      expect(mockPrismaService.moderationQueue.findMany).toHaveBeenCalledWith(
        expect.objectContaining({
          where: expect.objectContaining({
            status: ModerationStatus.PENDING,
          }),
        }),
      );
    });

    it('should filter by entity type', async () => {
      mockPrismaService.moderationQueue.findMany.mockResolvedValue([]);
      mockPrismaService.moderationQueue.count.mockResolvedValue(0);

      await service.findAll({ page: 1, limit: 20, entity_type: 'problem' });

      expect(mockPrismaService.moderationQueue.findMany).toHaveBeenCalledWith(
        expect.objectContaining({
          where: expect.objectContaining({
            entity_type: 'problem',
          }),
        }),
      );
    });

    it('should filter by primary category', async () => {
      mockPrismaService.moderationQueue.findMany.mockResolvedValue([]);
      mockPrismaService.moderationQueue.count.mockResolvedValue(0);

      await service.findAll({
        page: 1,
        limit: 20,
        primary_category: ReportCategory.SPAM,
      });

      expect(mockPrismaService.moderationQueue.findMany).toHaveBeenCalledWith(
        expect.objectContaining({
          where: expect.objectContaining({
            primary_category: ReportCategory.SPAM,
          }),
        }),
      );
    });
  });

  describe('findOne', () => {
    it('should return a queue item by id', async () => {
      const mockItem = {
        id: '1',
        entity_type: 'forum_post',
        entity_id: 'post-1',
        status: ModerationStatus.PENDING,
        priority: 5,
        primary_category: ReportCategory.SPAM,
        report_count: 3,
        created_at: new Date(),
        updated_at: new Date(),
      };

      mockPrismaService.moderationQueue.findUnique.mockResolvedValue(
        mockItem as any,
      );
      mockPrismaService.forumPost.findUnique.mockResolvedValue({
        title: 'Test Post',
        content: 'Test content',
      } as any);

      const result = await service.findOne('1');

      expect(result.id).toBe('1');
      expect(result.entity_type).toBe('forum_post');
    });

    it('should throw NotFoundException if item not found', async () => {
      mockPrismaService.moderationQueue.findUnique.mockResolvedValue(null);

      await expect(service.findOne('nonexistent')).rejects.toThrow(
        NotFoundException,
      );
    });
  });

  describe('getStats', () => {
    it('should return moderation statistics', async () => {
      mockPrismaService.moderationQueue.groupBy.mockResolvedValue([
        { status: ModerationStatus.PENDING, _count: { id: 5 } },
        { status: ModerationStatus.RESOLVED, _count: { id: 10 } },
      ]);

      const result = await service.getStats();

      expect(result).toBeDefined();
    });
  });

  describe('claim', () => {
    it('should claim an unassigned queue item', async () => {
      const mockItem = {
        id: '1',
        entity_type: 'forum_post',
        entity_id: 'post-1',
        status: ModerationStatus.PENDING,
        priority: 5,
        primary_category: ReportCategory.SPAM,
        report_count: 3,
        assigned_to_id: null,
        created_at: new Date(),
        updated_at: new Date(),
      };

      const claimedItem = {
        ...mockItem,
        status: ModerationStatus.UNDER_REVIEW,
        assigned_to_id: 'user-1',
      };

      mockPrismaService.moderationQueue.findUnique.mockResolvedValue(
        mockItem as any,
      );
      mockPrismaService.moderationQueue.update.mockResolvedValue(
        claimedItem as any,
      );
      mockPrismaService.forumPost.findUnique.mockResolvedValue({
        title: 'Test Post',
        content: 'Test content',
      } as any);

      const result = await service.claim('1', 'user-1');

      expect(result.assigned_to_id).toBe('user-1');
      expect(result.status).toBe(ModerationStatus.UNDER_REVIEW);
    });

    it('should throw BadRequestException if already assigned', async () => {
      const mockItem = {
        id: '1',
        entity_type: 'forum_post',
        entity_id: 'post-1',
        assigned_to_id: 'other-user',
      };

      mockPrismaService.moderationQueue.findUnique.mockResolvedValue(
        mockItem as any,
      );
      mockPrismaService.forumPost.findUnique.mockResolvedValue({
        title: 'Test Post',
        content: 'Test content',
      } as any);

      await expect(service.claim('1', 'user-1')).rejects.toThrow(
        BadRequestException,
      );
    });
  });

  describe('performAction', () => {
    it('should resolve a queue item', async () => {
      const mockItem = {
        id: '1',
        entity_type: 'forum_post',
        entity_id: 'post-1',
        author_id: 'author-1',
        status: ModerationStatus.PENDING,
        priority: 5,
        primary_category: ReportCategory.SPAM,
        report_count: 3,
        created_at: new Date(),
        updated_at: new Date(),
      };

      mockPrismaService.moderationQueue.findUnique.mockResolvedValue(mockItem);
      mockPrismaService.moderationQueue.update.mockResolvedValue({
        ...mockItem,
        status: ModerationStatus.RESOLVED,
      } as any);
      mockActionService.performAction.mockResolvedValue({
        id: 'action-1',
        action: ModerationActionType.RESOLVED,
      } as any);
      mockPrismaService.forumPost.findUnique.mockResolvedValue({
        title: 'Test Post',
        excerpt: 'Test excerpt',
      } as any);

      const result = await service.performAction(
        '1',
        ModerationActionType.RESOLVED,
        'admin-1',
      );

      expect(mockActionService.performAction).toHaveBeenCalled();
      expect(mockPrismaService.moderationQueue.update).toHaveBeenCalled();
    });

    it('should throw NotFoundException if item not found', async () => {
      mockPrismaService.moderationQueue.findUnique.mockResolvedValue(null);

      await expect(
        service.performAction('1', ModerationActionType.RESOLVED, 'user-1'),
      ).rejects.toThrow(NotFoundException);
    });
  });
});
