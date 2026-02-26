import { Test, TestingModule } from '@nestjs/testing';
import { ConfigService } from '@nestjs/config';
import { SearchService } from './search.service';
import { PrismaService } from '../prisma.service';
import { SearchIndex } from './dto/search-query.dto';

describe('SearchService', () => {
  let service: SearchService;
  let prisma: PrismaService;

  const mockPrismaService = {
    problem: {
      findMany: jest.fn().mockResolvedValue([]),
      count: jest.fn().mockResolvedValue(0),
    },
    user: {
      findMany: jest.fn().mockResolvedValue([]),
      count: jest.fn().mockResolvedValue(0),
    },
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        SearchService,
        {
          provide: ConfigService,
          useValue: {
            get: jest.fn().mockReturnValue(undefined), // No MeiliSearch configured
          },
        },
        {
          provide: PrismaService,
          useValue: mockPrismaService,
        },
      ],
    }).compile();

    service = module.get<SearchService>(SearchService);
    prisma = module.get<PrismaService>(PrismaService);
  });

  it('should be defined', () => {
    expect(service).toBeDefined();
  });

  describe('search (database fallback)', () => {
    it('should search problems', async () => {
      mockPrismaService.problem.findMany.mockResolvedValueOnce([
        {
          id: BigInt(1),
          title: 'Two Sum',
          slug: 'two-sum',
          detail: { summary: 'Find two numbers that add up to target' },
          tagRelations: [],
        },
      ]);
      mockPrismaService.problem.count.mockResolvedValueOnce(1);

      const result = await service.search({
        query: 'sum',
        page: 1,
        limit: 20,
      });

      expect(result.query).toBe('sum');
      expect(result.total).toBe(1);
      expect(result.results).toHaveLength(1);
      expect(result.results[0].type).toBe(SearchIndex.PROBLEMS);
    });

    it('should search users', async () => {
      mockPrismaService.problem.findMany.mockResolvedValue([]);
      mockPrismaService.problem.count.mockResolvedValue(0);
      mockPrismaService.user.findMany.mockResolvedValueOnce([
        {
          id: 'user-1',
          username: 'johndoe',
          name: 'John Doe',
        },
      ]);
      mockPrismaService.user.count.mockResolvedValueOnce(1);

      const result = await service.search({
        query: 'john',
        page: 1,
        limit: 20,
      });

      expect(result.total).toBe(1);
      expect(result.results).toHaveLength(1);
      expect(result.results[0].type).toBe(SearchIndex.USERS);
    });

    it('should filter by index type', async () => {
      mockPrismaService.problem.findMany.mockResolvedValue([]);
      mockPrismaService.problem.count.mockResolvedValue(0);
      mockPrismaService.user.findMany.mockClear();
      mockPrismaService.user.count.mockClear();

      const result = await service.search({
        query: 'test',
        index: SearchIndex.PROBLEMS,
        page: 1,
        limit: 20,
      });

      expect(mockPrismaService.problem.findMany).toHaveBeenCalled();
      expect(mockPrismaService.user.findMany).not.toHaveBeenCalled();
    });

    it('should handle pagination', async () => {
      mockPrismaService.problem.findMany.mockResolvedValue([]);
      mockPrismaService.problem.count.mockResolvedValue(0);

      await service.search({
        query: 'test',
        page: 2,
        limit: 10,
      });

      expect(mockPrismaService.problem.findMany).toHaveBeenCalledWith(
        expect.objectContaining({
          skip: 10,
          take: 10,
        }),
      );
    });
  });
});
