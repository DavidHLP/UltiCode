import { Test, TestingModule } from '@nestjs/testing';
import { RatingService } from './rating.service';
import { PrismaService } from '../prisma.service';

describe('RatingService', () => {
  let service: RatingService;
  let prisma: jest.Mocked<PrismaService>;

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        RatingService,
        {
          provide: PrismaService,
          useValue: {
            contestRanking: {
              findMany: jest.fn(),
            },
            globalRanking: {
              findUnique: jest.fn(),
              findMany: jest.fn(),
              create: jest.fn(),
              update: jest.fn(),
            },
            user: {
              findUnique: jest.fn(),
            },
            $transaction: jest.fn((callback) => callback({})),
          },
        },
      ],
    }).compile();

    service = module.get<RatingService>(RatingService);
    prisma = module.get(PrismaService);
  });

  it('should be defined', () => {
    expect(service).toBeDefined();
  });

  describe('getRatingTitle', () => {
    it('should return LEGENDARY_GRANDMASTER for rating 3000', () => {
      expect(service.getRatingTitle(3000)).toBe('LEGENDARY_GRANDMASTER');
    });

    it('should return NEWBIE for rating 0', () => {
      expect(service.getRatingTitle(0)).toBe('NEWBIE');
    });

    it('should return PUPIL for rating 1200', () => {
      expect(service.getRatingTitle(1200)).toBe('PUPIL');
    });

    it('should return EXPERT for rating 1650', () => {
      expect(service.getRatingTitle(1650)).toBe('EXPERT');
    });
  });

  describe('getRatingColor', () => {
    it('should return correct color for NEWBIE', () => {
      expect(service.getRatingColor(0)).toBe('#808080');
    });

    it('should return correct color for PUPIL', () => {
      expect(service.getRatingColor(1200)).toBe('#008000');
    });

    it('should return correct color for EXPERT', () => {
      expect(service.getRatingColor(1650)).toBe('#0000FF');
    });
  });

  describe('calculateContestRatings', () => {
    it('should return empty array when no rankings', async () => {
      (prisma.contestRanking.findMany as jest.Mock).mockResolvedValue([]);

      const result = await service.calculateContestRatings('contest-123');

      expect(result).toEqual([]);
    });
  });

  describe('getUserRatingHistory', () => {
    it('should return user rating history', async () => {
      const mockRankings = [
        {
          contest_id: 'contest-1',
          rank: 5,
          rating_before: 1500,
          rating_after: 1550,
          rating_change: 50,
          contest: {
            title: 'Weekly Contest 1',
            start_time: new Date('2026-01-01'),
          },
        },
      ];

      (prisma.contestRanking.findMany as jest.Mock).mockResolvedValue(
        mockRankings as never,
      );

      const result = await service.getUserRatingHistory('user-123');

      expect(result).toHaveLength(1);
      expect(result[0].contestTitle).toBe('Weekly Contest 1');
    });
  });
});
