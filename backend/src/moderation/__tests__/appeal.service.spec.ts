import { Test, TestingModule } from '@nestjs/testing'
import { AppealService } from '../services/appeal.service'
import { PrismaService } from '../../prisma.service'
import { AuditService } from '../../admin/services/audit.service'
import {
  NotFoundException,
  BadRequestException,
  ForbiddenException,
} from '@nestjs/common'
import { AppealStatus, ModerationStatus, ModerationActionType, ReportCategory } from '@prisma/client'

describe('AppealService', () => {
  let service: AppealService
  let prisma: jest.Mocked<PrismaService>
  let audit: jest.Mocked<AuditService>

  const mockPrismaService = {
    appeal: {
      create: jest.fn(),
      findUnique: jest.fn(),
      findFirst: jest.fn(),
      findMany: jest.fn(),
      update: jest.fn(),
      count: jest.fn(),
      groupBy: jest.fn(),
    },
    moderationQueue: {
      findUnique: jest.fn(),
      update: jest.fn(),
    },
    moderationAction: {
      create: jest.fn(),
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
  }

  const mockAuditService = {
    log: jest.fn(),
  }

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        AppealService,
        {
          provide: PrismaService,
          useValue: mockPrismaService,
        },
        {
          provide: AuditService,
          useValue: mockAuditService,
        },
      ],
    }).compile()

    service = module.get<AppealService>(AppealService)
    prisma = module.get(PrismaService)
    audit = module.get(AuditService)
  })

  afterEach(() => {
    jest.clearAllMocks()
  })

  describe('create', () => {
    const createDto = {
      queue_id: 'queue-1',
      reason: 'I believe this was a mistake',
      evidence: 'Additional context',
    }

    it('should create an appeal successfully', async () => {
      const mockQueue = {
        id: 'queue-1',
        author_id: 'user-1',
        entity_type: 'forum_post',
        entity_id: 'post-1',
      }

      const mockAppeal = {
        id: 'appeal-1',
        queue_id: 'queue-1',
        appellant_id: 'user-1',
        reason: createDto.reason,
        evidence: createDto.evidence,
      }

      mockPrismaService.moderationQueue.findUnique.mockResolvedValue(mockQueue as any)
      mockPrismaService.appeal.findFirst.mockResolvedValue(null)
      mockPrismaService.appeal.create.mockResolvedValue(mockAppeal as any)
      mockPrismaService.moderationQueue.update.mockResolvedValue({} as any)
      mockPrismaService.moderationAction.create.mockResolvedValue({} as any)

      const result = await service.create('user-1', createDto)

      expect(result).toEqual(mockAppeal)
      expect(mockPrismaService.moderationQueue.update).toHaveBeenCalledWith({
        where: { id: 'queue-1' },
        data: { status: ModerationStatus.APPEAL_PENDING },
      })
      expect(mockAuditService.log).toHaveBeenCalled()
    })

    it('should throw NotFoundException if queue item not found', async () => {
      mockPrismaService.moderationQueue.findUnique.mockResolvedValue(null)

      await expect(service.create('user-1', createDto)).rejects.toThrow(
        NotFoundException,
      )
    })

    it('should throw ForbiddenException if user is not the author', async () => {
      const mockQueue = {
        id: 'queue-1',
        author_id: 'other-user',
      }

      mockPrismaService.moderationQueue.findUnique.mockResolvedValue(mockQueue as any)

      await expect(service.create('user-1', createDto)).rejects.toThrow(
        ForbiddenException,
      )
    })

    it('should throw BadRequestException if appeal already pending', async () => {
      const mockQueue = {
        id: 'queue-1',
        author_id: 'user-1',
      }

      mockPrismaService.moderationQueue.findUnique.mockResolvedValue(mockQueue as any)
      mockPrismaService.appeal.findFirst.mockResolvedValue({ id: 'existing-appeal' } as any)

      await expect(service.create('user-1', createDto)).rejects.toThrow(
        BadRequestException,
      )
    })
  })

  describe('findAll', () => {
    it('should return paginated appeals', async () => {
      const mockAppeals = [
        {
          id: 'appeal-1',
          queue_id: 'queue-1',
          appellant_id: 'user-1',
          status: AppealStatus.PENDING,
          appellant: { id: 'user-1', username: 'user1', name: 'User 1', avatar: null },
        },
      ]

      mockPrismaService.appeal.findMany.mockResolvedValue(mockAppeals as any)
      mockPrismaService.appeal.count.mockResolvedValue(1)

      const result = await service.findAll({ page: 1, limit: 20 })

      expect(result.data).toEqual(mockAppeals)
      expect(result.meta.total).toBe(1)
      expect(result.meta.page).toBe(1)
      expect(result.meta.limit).toBe(20)
    })

    it('should filter by status', async () => {
      mockPrismaService.appeal.findMany.mockResolvedValue([])
      mockPrismaService.appeal.count.mockResolvedValue(0)

      await service.findAll({ page: 1, limit: 20, status: AppealStatus.PENDING })

      expect(mockPrismaService.appeal.findMany).toHaveBeenCalledWith(
        expect.objectContaining({
          where: expect.objectContaining({
            status: AppealStatus.PENDING,
          }),
        }),
      )
    })

    it('should filter by queue_id', async () => {
      mockPrismaService.appeal.findMany.mockResolvedValue([])
      mockPrismaService.appeal.count.mockResolvedValue(0)

      await service.findAll({ page: 1, limit: 20, queue_id: 'queue-1' })

      expect(mockPrismaService.appeal.findMany).toHaveBeenCalledWith(
        expect.objectContaining({
          where: expect.objectContaining({
            queue_id: 'queue-1',
          }),
        }),
      )
    })

    it('should filter by appellant_id', async () => {
      mockPrismaService.appeal.findMany.mockResolvedValue([])
      mockPrismaService.appeal.count.mockResolvedValue(0)

      await service.findAll({ page: 1, limit: 20, appellant_id: 'user-1' })

      expect(mockPrismaService.appeal.findMany).toHaveBeenCalledWith(
        expect.objectContaining({
          where: expect.objectContaining({
            appellant_id: 'user-1',
          }),
        }),
      )
    })
  })

  describe('findOne', () => {
    it('should return an appeal by id', async () => {
      const mockAppeal = {
        id: 'appeal-1',
        queue_id: 'queue-1',
        appellant_id: 'user-1',
        status: AppealStatus.PENDING,
        appellant: { id: 'user-1', username: 'user1', name: 'User 1', avatar: null },
        queue: {
          entity_type: 'forum_post',
          entity_id: 'post-1',
          author: { id: 'user-1', username: 'user1', name: 'User 1' },
          actions: [],
        },
        reviewed_by: null,
      }

      mockPrismaService.appeal.findUnique.mockResolvedValue(mockAppeal as any)

      const result = await service.findOne('appeal-1')

      expect(result).toEqual(mockAppeal)
    })

    it('should throw NotFoundException if appeal not found', async () => {
      mockPrismaService.appeal.findUnique.mockResolvedValue(null)

      await expect(service.findOne('nonexistent')).rejects.toThrow(NotFoundException)
    })
  })

  describe('review', () => {
    const mockAppeal = {
      id: 'appeal-1',
      queue_id: 'queue-1',
      status: AppealStatus.PENDING,
      queue: {
        entity_type: 'forum_post',
        entity_id: 'post-1',
      },
    }

    it('should approve an appeal and restore content', async () => {
      const reviewDto = {
        status: AppealStatus.APPROVED,
        response: 'Appeal approved',
      }

      mockPrismaService.appeal.findUnique.mockResolvedValue(mockAppeal as any)
      mockPrismaService.appeal.update.mockResolvedValue({
        ...mockAppeal,
        status: AppealStatus.APPROVED,
        reviewed_by_id: 'admin-1',
        response: reviewDto.response,
      } as any)
      mockPrismaService.moderationQueue.update.mockResolvedValue({} as any)
      mockPrismaService.moderationAction.create.mockResolvedValue({} as any)
      mockPrismaService.forumPost.update.mockResolvedValue({} as any)

      const result = await service.review('appeal-1', 'admin-1', reviewDto)

      expect(result.status).toBe(AppealStatus.APPROVED)
      expect(mockPrismaService.moderationAction.create).toHaveBeenCalledWith({
        data: expect.objectContaining({
          action: ModerationActionType.APPEAL_APPROVED,
        }),
      })
      expect(mockPrismaService.forumPost.update).toHaveBeenCalled()
      expect(mockAuditService.log).toHaveBeenCalled()
    })

    it('should reject an appeal', async () => {
      const reviewDto = {
        status: AppealStatus.REJECTED,
        response: 'Appeal rejected',
      }

      mockPrismaService.appeal.findUnique.mockResolvedValue(mockAppeal as any)
      mockPrismaService.appeal.update.mockResolvedValue({
        ...mockAppeal,
        status: AppealStatus.REJECTED,
        reviewed_by_id: 'admin-1',
        response: reviewDto.response,
      } as any)
      mockPrismaService.moderationQueue.update.mockResolvedValue({} as any)
      mockPrismaService.moderationAction.create.mockResolvedValue({} as any)

      const result = await service.review('appeal-1', 'admin-1', reviewDto)

      expect(result.status).toBe(AppealStatus.REJECTED)
      expect(mockPrismaService.moderationAction.create).toHaveBeenCalledWith({
        data: expect.objectContaining({
          action: ModerationActionType.APPEAL_REJECTED,
        }),
      })
    })

    it('should throw BadRequestException if appeal already reviewed', async () => {
      const reviewedAppeal = {
        ...mockAppeal,
        status: AppealStatus.APPROVED,
      }

      mockPrismaService.appeal.findUnique.mockResolvedValue(reviewedAppeal as any)

      await expect(
        service.review('appeal-1', 'admin-1', {
          status: AppealStatus.APPROVED,
          response: 'test',
        }),
      ).rejects.toThrow(BadRequestException)
    })
  })

  describe('getAppealsByUser', () => {
    it('should return appeals for a user', async () => {
      const mockAppeals = [
        {
          id: 'appeal-1',
          queue_id: 'queue-1',
          appellant_id: 'user-1',
          queue: {
            entity_type: 'forum_post',
            entity_id: 'post-1',
            resolution: null,
          },
        },
      ]

      mockPrismaService.appeal.findMany.mockResolvedValue(mockAppeals as any)

      const result = await service.getAppealsByUser('user-1')

      expect(result).toEqual(mockAppeals)
      expect(mockPrismaService.appeal.findMany).toHaveBeenCalledWith({
        where: { appellant_id: 'user-1' },
        orderBy: { created_at: 'desc' },
        include: {
          queue: {
            select: {
              entity_type: true,
              entity_id: true,
              resolution: true,
            },
          },
        },
      })
    })
  })

  describe('getStats', () => {
    it('should return appeal statistics', async () => {
      mockPrismaService.appeal.groupBy.mockResolvedValue([
        { status: AppealStatus.PENDING, _count: 5 },
        { status: AppealStatus.APPROVED, _count: 10 },
        { status: AppealStatus.REJECTED, _count: 3 },
      ])
      mockPrismaService.appeal.count.mockResolvedValueOnce(18).mockResolvedValueOnce(5)

      const result = await service.getStats()

      expect(result.total).toBe(18)
      expect(result.pending).toBe(5)
      expect(result.byStatus[AppealStatus.PENDING]).toBe(5)
      expect(result.byStatus[AppealStatus.APPROVED]).toBe(10)
      expect(result.byStatus[AppealStatus.REJECTED]).toBe(3)
    })
  })
})
