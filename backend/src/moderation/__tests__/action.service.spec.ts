import { Test, TestingModule } from '@nestjs/testing';
import { ModerationActionService } from '../services/action.service';
import { PrismaService } from '../../prisma.service';
import { AuditService } from '../../admin/services/audit.service';
import { ModerationActionType, ReportCategory } from '@prisma/client';

describe('ModerationActionService', () => {
  let service: ModerationActionService;
  let prisma: jest.Mocked<PrismaService>;
  let audit: jest.Mocked<AuditService>;

  const mockPrismaService = {
    moderationAction: {
      create: jest.fn(),
      findMany: jest.fn(),
    },
    userWarning: {
      create: jest.fn(),
    },
    userBan: {
      create: jest.fn(),
    },
    user: {
      update: jest.fn(),
    },
    forumPost: {
      update: jest.fn(),
    },
    forumComment: {
      update: jest.fn(),
    },
    solution: {
      update: jest.fn(),
    },
    solutionComment: {
      update: jest.fn(),
    },
    problem: {
      update: jest.fn(),
    },
    $transaction: jest.fn((cb) => cb(mockPrismaService)),
  };

  const mockAuditService = {
    log: jest.fn(),
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        ModerationActionService,
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

    service = module.get<ModerationActionService>(ModerationActionService);
    prisma = module.get(PrismaService);
    audit = module.get(AuditService);
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  describe('performAction', () => {
    const mockQueue = {
      id: 'queue-1',
      entity_type: 'forum_post',
      entity_id: 'post-1',
      author_id: 'user-1',
      primary_category: ReportCategory.SPAM,
    };

    it('should create a DELETED action and soft delete the entity', async () => {
      mockPrismaService.moderationAction.create.mockResolvedValue({
        id: 'action-1',
        queue_id: 'queue-1',
        action: ModerationActionType.DELETED,
        performed_by_id: 'admin-1',
      } as any);

      await service.performAction(
        mockQueue as any,
        ModerationActionType.DELETED,
        'admin-1',
        'Deleted for spam',
      );

      expect(mockPrismaService.moderationAction.create).toHaveBeenCalledWith({
        data: {
          queue_id: 'queue-1',
          action: ModerationActionType.DELETED,
          performed_by_id: 'admin-1',
          note: 'Deleted for spam',
          duration_days: undefined,
        },
      });

      expect(mockPrismaService.forumPost.update).toHaveBeenCalledWith({
        where: { id: 'post-1' },
        data: {
          is_deleted: true,
          deleted_at: expect.any(Date),
          deleted_by: 'admin-1',
        },
      });

      expect(mockAuditService.log).toHaveBeenCalled();
    });

    it('should create a HIDDEN action and hide the entity', async () => {
      mockPrismaService.moderationAction.create.mockResolvedValue({
        id: 'action-1',
        queue_id: 'queue-1',
        action: ModerationActionType.HIDDEN,
        performed_by_id: 'admin-1',
      } as any);

      await service.performAction(
        mockQueue as any,
        ModerationActionType.HIDDEN,
        'admin-1',
      );

      expect(mockPrismaService.forumPost.update).toHaveBeenCalledWith({
        where: { id: 'post-1' },
        data: {
          is_deleted: true,
          deleted_at: expect.any(Date),
        },
      });
    });

    it('should create a RESTORED action and restore the entity', async () => {
      mockPrismaService.moderationAction.create.mockResolvedValue({
        id: 'action-1',
        queue_id: 'queue-1',
        action: ModerationActionType.RESTORED,
        performed_by_id: 'admin-1',
      } as any);

      await service.performAction(
        mockQueue as any,
        ModerationActionType.RESTORED,
        'admin-1',
      );

      expect(mockPrismaService.forumPost.update).toHaveBeenCalledWith({
        where: { id: 'post-1' },
        data: {
          is_deleted: false,
          deleted_at: null,
          deleted_by: null,
        },
      });
    });

    it('should create a WARNED action and warn the user', async () => {
      mockPrismaService.moderationAction.create.mockResolvedValue({
        id: 'action-1',
        queue_id: 'queue-1',
        action: ModerationActionType.WARNED,
        performed_by_id: 'admin-1',
      } as any);

      mockPrismaService.userWarning.create.mockResolvedValue({} as any);

      await service.performAction(
        mockQueue as any,
        ModerationActionType.WARNED,
        'admin-1',
        'Warning for spam content',
      );

      expect(mockPrismaService.userWarning.create).toHaveBeenCalledWith({
        data: {
          user_id: 'user-1',
          queue_id: 'queue-1',
          action_id: 'action-1',
          reason: 'Warning for spam content',
          category: ReportCategory.SPAM,
        },
      });
    });

    it('should create a TEMP_BANNED action and ban the user temporarily', async () => {
      mockPrismaService.moderationAction.create.mockResolvedValue({
        id: 'action-1',
        queue_id: 'queue-1',
        action: ModerationActionType.TEMP_BANNED,
        performed_by_id: 'admin-1',
      } as any);

      mockPrismaService.userBan.create.mockResolvedValue({} as any);
      mockPrismaService.user.update.mockResolvedValue({} as any);

      await service.performAction(
        mockQueue as any,
        ModerationActionType.TEMP_BANNED,
        'admin-1',
        'Temporary ban',
        14,
      );

      expect(mockPrismaService.userBan.create).toHaveBeenCalledWith({
        data: expect.objectContaining({
          user_id: 'user-1',
          is_permanent: false,
          reason: 'Temporary ban',
          category: ReportCategory.SPAM,
          queue_id: 'queue-1',
          action_id: 'action-1',
          banned_by_id: 'admin-1',
        }),
      });

      expect(mockPrismaService.user.update).toHaveBeenCalledWith({
        where: { id: 'user-1' },
        data: {
          is_banned: true,
          banned_until: expect.any(Date),
          banned_reason: 'Temporary ban',
        },
      });
    });

    it('should create a PERM_BANNED action and ban the user permanently', async () => {
      mockPrismaService.moderationAction.create.mockResolvedValue({
        id: 'action-1',
        queue_id: 'queue-1',
        action: ModerationActionType.PERM_BANNED,
        performed_by_id: 'admin-1',
      } as any);

      mockPrismaService.userBan.create.mockResolvedValue({} as any);
      mockPrismaService.user.update.mockResolvedValue({} as any);

      await service.performAction(
        mockQueue as any,
        ModerationActionType.PERM_BANNED,
        'admin-1',
        'Permanent ban',
      );

      expect(mockPrismaService.userBan.create).toHaveBeenCalledWith({
        data: expect.objectContaining({
          user_id: 'user-1',
          is_permanent: true,
          reason: 'Permanent ban',
          category: ReportCategory.SPAM,
          queue_id: 'queue-1',
          action_id: 'action-1',
          banned_by_id: 'admin-1',
          ends_at: null,
        }),
      });

      expect(mockPrismaService.user.update).toHaveBeenCalledWith({
        where: { id: 'user-1' },
        data: {
          is_banned: true,
          banned_until: null,
          banned_reason: 'Permanent ban',
        },
      });
    });

    it('should create a DISMISSED action and unflag the entity', async () => {
      mockPrismaService.moderationAction.create.mockResolvedValue({
        id: 'action-1',
        queue_id: 'queue-1',
        action: ModerationActionType.DISMISSED,
        performed_by_id: 'admin-1',
      } as any);

      await service.performAction(
        mockQueue as any,
        ModerationActionType.DISMISSED,
        'admin-1',
      );

      expect(mockPrismaService.forumPost.update).toHaveBeenCalledWith({
        where: { id: 'post-1' },
        data: {
          is_flagged: false,
          flagged_at: null,
          flagged_reason: null,
        },
      });
    });
  });

  describe('getActionsByQueue', () => {
    it('should return actions for a queue item', async () => {
      const mockActions = [
        {
          id: 'action-1',
          queue_id: 'queue-1',
          action: ModerationActionType.DELETED,
          performed_by_id: 'admin-1',
          performed_by: {
            id: 'admin-1',
            username: 'admin',
            name: 'Admin',
            avatar: null,
          },
        },
      ];

      mockPrismaService.moderationAction.findMany.mockResolvedValue(
        mockActions as any,
      );

      const result = await service.getActionsByQueue('queue-1');

      expect(result).toEqual(mockActions);
      expect(mockPrismaService.moderationAction.findMany).toHaveBeenCalledWith({
        where: { queue_id: 'queue-1' },
        orderBy: { created_at: 'desc' },
        include: {
          performed_by: {
            select: { id: true, username: true, name: true, avatar: true },
          },
        },
      });
    });
  });

  describe('getActionsByUser', () => {
    it('should return actions performed by a user', async () => {
      const mockActions = [
        {
          id: 'action-1',
          queue_id: 'queue-1',
          action: ModerationActionType.DELETED,
          performed_by_id: 'admin-1',
        },
      ];

      mockPrismaService.moderationAction.findMany.mockResolvedValue(
        mockActions as any,
      );

      const result = await service.getActionsByUser('admin-1');

      expect(result).toEqual(mockActions);
      expect(mockPrismaService.moderationAction.findMany).toHaveBeenCalledWith({
        where: { performed_by_id: 'admin-1' },
        orderBy: { created_at: 'desc' },
        take: 50,
      });
    });
  });
});
