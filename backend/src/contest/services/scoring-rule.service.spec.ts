import { Test, TestingModule } from '@nestjs/testing';
import { ScoringRuleService } from './scoring-rule.service';
import { PrismaService } from '../../prisma.service';
import { NotFoundException, BadRequestException } from '@nestjs/common';

describe('ScoringRuleService', () => {
  let service: ScoringRuleService;
  let prisma: jest.Mocked<PrismaService>;

  const mockRule = {
    id: 'rule-1',
    name: 'Test Rule',
    description: 'Test Description',
    base_score_per_problem: 100,
    time_bonus_per_minute: 1,
    wrong_answer_penalty: 5,
    time_limit_penalty: 0,
    first_solve_bonus: 10,
    full_score_bonus: 0,
    is_default: false,
    is_active: true,
    created_at: new Date(),
    updated_at: new Date(),
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        ScoringRuleService,
        {
          provide: PrismaService,
          useValue: {
            contestScoringRule: {
              findMany: jest.fn(),
              findUnique: jest.fn(),
              findFirst: jest.fn(),
              create: jest.fn(),
              update: jest.fn(),
              updateMany: jest.fn(),
              delete: jest.fn(),
            },
            contest: {
              count: jest.fn(),
            },
          },
        },
      ],
    }).compile();

    service = module.get<ScoringRuleService>(ScoringRuleService);
    prisma = module.get(PrismaService);
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  describe('findAll', () => {
    it('should return only active rules by default', async () => {
      prisma.contestScoringRule.findMany.mockResolvedValue([mockRule]);

      const result = await service.findAll();

      expect(result).toEqual([mockRule]);
      expect(prisma.contestScoringRule.findMany).toHaveBeenCalledWith({
        where: { is_active: true },
        orderBy: [{ is_default: 'desc' }, { created_at: 'asc' }],
      });
    });

    it('should return all rules including inactive when flag is true', async () => {
      prisma.contestScoringRule.findMany.mockResolvedValue([mockRule]);

      const result = await service.findAll(true);

      expect(prisma.contestScoringRule.findMany).toHaveBeenCalledWith({
        where: undefined,
        orderBy: [{ is_default: 'desc' }, { created_at: 'asc' }],
      });
    });
  });

  describe('findOne', () => {
    it('should return a rule by id', async () => {
      prisma.contestScoringRule.findUnique.mockResolvedValue(mockRule);

      const result = await service.findOne('rule-1');

      expect(result).toEqual(mockRule);
    });

    it('should throw NotFoundException when rule not found', async () => {
      prisma.contestScoringRule.findUnique.mockResolvedValue(null);

      await expect(service.findOne('non-existent')).rejects.toThrow(
        NotFoundException,
      );
    });
  });

  describe('findDefault', () => {
    it('should return the default rule', async () => {
      prisma.contestScoringRule.findFirst.mockResolvedValue(mockRule);

      const result = await service.findDefault();

      expect(result).toEqual(mockRule);
    });

    it('should fallback to first active rule when no default', async () => {
      prisma.contestScoringRule.findFirst
        .mockResolvedValueOnce(null)
        .mockResolvedValueOnce(mockRule);

      const result = await service.findDefault();

      expect(result).toEqual(mockRule);
    });
  });

  describe('create', () => {
    it('should create a new rule', async () => {
      const createDto = {
        name: 'New Rule',
        base_score_per_problem: 100,
        time_bonus_per_minute: 1,
        wrong_answer_penalty: 5,
        first_solve_bonus: 10,
      };

      prisma.contestScoringRule.updateMany.mockResolvedValue({ count: 0 });
      prisma.contestScoringRule.create.mockResolvedValue({
        ...mockRule,
        ...createDto,
      });

      const result = await service.create(createDto as any);

      expect(result.name).toBe('New Rule');
    });

    it('should unset other defaults when creating a default rule', async () => {
      const createDto = {
        name: 'New Default',
        base_score_per_problem: 100,
        time_bonus_per_minute: 1,
        wrong_answer_penalty: 5,
        first_solve_bonus: 10,
        is_default: true,
      };

      prisma.contestScoringRule.updateMany.mockResolvedValue({ count: 1 });
      prisma.contestScoringRule.create.mockResolvedValue({
        ...mockRule,
        ...createDto,
      });

      await service.create(createDto as any);

      expect(prisma.contestScoringRule.updateMany).toHaveBeenCalledWith({
        where: { is_default: true },
        data: { is_default: false },
      });
    });
  });

  describe('remove', () => {
    it('should throw BadRequestException when trying to delete default rule', async () => {
      prisma.contestScoringRule.findUnique.mockResolvedValue({
        ...mockRule,
        is_default: true,
      });

      await expect(service.remove('rule-1')).rejects.toThrow(
        BadRequestException,
      );
    });

    it('should soft delete rule when it is used by contests', async () => {
      prisma.contestScoringRule.findUnique.mockResolvedValue(mockRule);
      prisma.contest.count.mockResolvedValue(5);
      prisma.contestScoringRule.update.mockResolvedValue({
        ...mockRule,
        is_active: false,
      });

      const result = await service.remove('rule-1');

      expect(prisma.contestScoringRule.update).toHaveBeenCalledWith({
        where: { id: 'rule-1' },
        data: { is_active: false },
      });
    });

    it('should hard delete rule when not used by contests', async () => {
      prisma.contestScoringRule.findUnique.mockResolvedValue(mockRule);
      prisma.contest.count.mockResolvedValue(0);
      prisma.contestScoringRule.delete.mockResolvedValue(mockRule);

      await service.remove('rule-1');

      expect(prisma.contestScoringRule.delete).toHaveBeenCalledWith({
        where: { id: 'rule-1' },
      });
    });
  });
});
