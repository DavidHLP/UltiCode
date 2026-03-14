import { Test, TestingModule } from '@nestjs/testing';
import { AntiCheatService } from './anticheat.service';
import { PrismaService } from '../../prisma.service';

describe('AntiCheatService', () => {
  let service: AntiCheatService;
  let prisma: jest.Mocked<PrismaService>;

  const mockPrismaService = {
    contestSubmission: {
      findMany: jest.fn(),
      count: jest.fn(),
    },
    contest: {
      findUnique: jest.fn(),
    },
    submission: {
      findMany: jest.fn(),
    },
    contestParticipant: {
      findMany: jest.fn(),
      count: jest.fn(),
    },
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        AntiCheatService,
        {
          provide: PrismaService,
          useValue: mockPrismaService,
        },
      ],
    }).compile();

    service = module.get<AntiCheatService>(AntiCheatService);
    prisma = module.get(PrismaService);
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  describe('calculateSimilarity', () => {
    it('should return 1.0 for identical code', () => {
      const code = `
function solve(a, b) {
  return a + b;
}
      `.trim();

      const result = service.calculateSimilarity(code, code);

      expect(result).toBe(1.0);
    });

    it('should return 0.0 for completely different code', () => {
      const code1 = 'function add(a, b) { return a + b; }';
      const code2 = 'const multiply = (x, y) => x * y;';

      const result = service.calculateSimilarity(code1, code2);

      // Very different code should have low similarity
      expect(result).toBeLessThan(0.3);
    });

    it('should detect structural similarity with different variable names', () => {
      const code1 = `
function solve(n, arr) {
  let sum = 0;
  for (let i = 0; i < n; i++) {
    sum += arr[i];
  }
  return sum;
}
      `.trim();

      const code2 = `
function calculate(count, data) {
  let total = 0;
  for (let j = 0; j < count; j++) {
    total += data[j];
  }
  return total;
}
      `.trim();

      const result = service.calculateSimilarity(code1, code2);

      // Structural similarity should be detected (some overlap in structure patterns)
      // Note: n-gram similarity won't be very high for different variable names
      // but should still detect some similarity due to shared structure
      expect(result).toBeGreaterThan(0.2);
    });

    it('should handle empty code', () => {
      const result = service.calculateSimilarity('', '');

      expect(result).toBe(0);
    });

    it('should handle one empty code string', () => {
      const result = service.calculateSimilarity('some code', '');

      expect(result).toBe(0);
    });

    it('should normalize whitespace', () => {
      const code1 = 'function test() { return 1; }';
      const code2 = 'function  test()  {  return  1;  }';

      const result = service.calculateSimilarity(code1, code2);

      expect(result).toBe(1.0);
    });

    it('should be case-sensitive by default', () => {
      const code1 = 'function Solve() {}';
      const code2 = 'function solve() {}';

      const result = service.calculateSimilarity(code1, code2);

      // Should not be identical due to case difference
      expect(result).toBeLessThan(1.0);
    });
  });

  describe('detectSimilarity', () => {
    it('should return empty array when no submissions exist', async () => {
      prisma.contestSubmission.findMany.mockResolvedValue([]);

      const result = await service.detectSimilarity('contest-1');

      expect(result).toEqual([]);
      expect(prisma.contestSubmission.findMany).toHaveBeenCalledWith({
        where: { contest_id: 'contest-1' },
        include: {
          submission: {
            select: {
              id: true,
              code: true,
              user_id: true,
              language: true,
            },
          },
          contestProblem: {
            select: {
              problem_index: true,
            },
          },
          participant: {
            select: {
              user_id: true,
            },
          },
        },
      });
    });

    it('should detect suspicious pairs above threshold', async () => {
      const identicalCode = 'function solve() { return 42; }';
      const differentCode = 'function different() { return 0; }';

      prisma.contestSubmission.findMany.mockResolvedValue([
        {
          id: 'sub-1',
          contest_id: 'contest-1',
          submission_id: 's1',
          contest_problem_id: 'cp-1',
          participant_id: 'p1',
          submission: {
            id: 's1',
            code: identicalCode,
            user_id: 'user-1',
            language: 'javascript',
          },
          contestProblem: { problem_index: 'A' },
          participant: { user_id: 'user-1' },
        },
        {
          id: 'sub-2',
          contest_id: 'contest-1',
          submission_id: 's2',
          contest_problem_id: 'cp-1',
          participant_id: 'p2',
          submission: {
            id: 's2',
            code: identicalCode,
            user_id: 'user-2',
            language: 'javascript',
          },
          contestProblem: { problem_index: 'A' },
          participant: { user_id: 'user-2' },
        },
        {
          id: 'sub-3',
          contest_id: 'contest-1',
          submission_id: 's3',
          contest_problem_id: 'cp-1',
          participant_id: 'p3',
          submission: {
            id: 's3',
            code: differentCode,
            user_id: 'user-3',
            language: 'javascript',
          },
          contestProblem: { problem_index: 'A' },
          participant: { user_id: 'user-3' },
        },
      ] as any);

      const result = await service.detectSimilarity('contest-1', 0.8);

      expect(result.length).toBeGreaterThan(0);
      // The identical pair should be detected
      const identicalPair = result.find(
        (p) =>
          (p.user1_id === 'user-1' && p.user2_id === 'user-2') ||
          (p.user1_id === 'user-2' && p.user2_id === 'user-1'),
      );
      expect(identicalPair).toBeDefined();
      expect(identicalPair?.similarity).toBe(1.0);
      expect(identicalPair?.problem_index).toBe('A');
    });

    it('should filter out pairs below threshold', async () => {
      const code1 = 'function a() { return 1; }';
      const code2 = 'function b() { return 2; }';

      prisma.contestSubmission.findMany.mockResolvedValue([
        {
          id: 'sub-1',
          contest_id: 'contest-1',
          submission_id: 's1',
          contest_problem_id: 'cp-1',
          participant_id: 'p1',
          submission: {
            id: 's1',
            code: code1,
            user_id: 'user-1',
            language: 'javascript',
          },
          contestProblem: { problem_index: 'A' },
          participant: { user_id: 'user-1' },
        },
        {
          id: 'sub-2',
          contest_id: 'contest-1',
          submission_id: 's2',
          contest_problem_id: 'cp-1',
          participant_id: 'p2',
          submission: {
            id: 's2',
            code: code2,
            user_id: 'user-2',
            language: 'javascript',
          },
          contestProblem: { problem_index: 'A' },
          participant: { user_id: 'user-2' },
        },
      ] as any);

      const result = await service.detectSimilarity('contest-1', 0.95);

      // These codes should not be 95% similar
      expect(result.length).toBe(0);
    });

    it('should only compare submissions for the same problem', async () => {
      prisma.contestSubmission.findMany.mockResolvedValue([
        {
          id: 'sub-1',
          contest_id: 'contest-1',
          submission_id: 's1',
          contest_problem_id: 'cp-1',
          participant_id: 'p1',
          submission: {
            id: 's1',
            code: 'same code',
            user_id: 'user-1',
            language: 'javascript',
          },
          contestProblem: { problem_index: 'A' },
          participant: { user_id: 'user-1' },
        },
        {
          id: 'sub-2',
          contest_id: 'contest-1',
          submission_id: 's2',
          contest_problem_id: 'cp-2', // Different problem
          participant_id: 'p2',
          submission: {
            id: 's2',
            code: 'same code',
            user_id: 'user-2',
            language: 'javascript',
          },
          contestProblem: { problem_index: 'B' },
          participant: { user_id: 'user-2' },
        },
      ] as any);

      const result = await service.detectSimilarity('contest-1', 0.8);

      // Should not compare across different problems
      expect(result.length).toBe(0);
    });
  });

  describe('checkTimeAnomaly', () => {
    it('should return empty array when no submissions exist', async () => {
      prisma.contestSubmission.findMany.mockResolvedValue([]);

      const result = await service.checkTimeAnomaly('contest-1');

      expect(result).toEqual([]);
    });

    it('should detect too-fast submissions', async () => {
      const baseTime = new Date('2024-01-01T10:00:00Z');
      const fastSubmissionTime = new Date('2024-01-01T10:00:30Z'); // 30 seconds later

      prisma.contestSubmission.findMany.mockResolvedValue([
        {
          id: 'sub-1',
          contest_id: 'contest-1',
          submission_id: 's1',
          contest_problem_id: 'cp-1',
          participant_id: 'p1',
          submitted_at: fastSubmissionTime,
          time_from_start: 30,
          submission: {
            id: 's1',
            code: 'function solve() { return complexAlgorithm(); }',
            user_id: 'user-1',
            language: 'javascript',
          },
          contestProblem: { problem_index: 'A' },
          participant: { user_id: 'user-1' },
        },
      ] as any);

      // Mock contest start time
      prisma.contest.findUnique.mockResolvedValue({
        id: 'contest-1',
        start_time: baseTime,
      } as any);

      const result = await service.checkTimeAnomaly('contest-1', {
        minTime: 60, // 60 seconds minimum
      });

      expect(result.length).toBeGreaterThan(0);
      expect(result[0].user_id).toBe('user-1');
      expect(result[0].time_from_start).toBe(30);
    });

    it('should not flag normal submissions', async () => {
      const baseTime = new Date('2024-01-01T10:00:00Z');
      const normalSubmissionTime = new Date('2024-01-01T10:05:00Z'); // 5 minutes later

      prisma.contestSubmission.findMany.mockResolvedValue([
        {
          id: 'sub-1',
          contest_id: 'contest-1',
          submission_id: 's1',
          contest_problem_id: 'cp-1',
          participant_id: 'p1',
          submitted_at: normalSubmissionTime,
          time_from_start: 300,
          submission: {
            id: 's1',
            code: 'function solve() { return answer; }',
            user_id: 'user-1',
            language: 'javascript',
          },
          contestProblem: { problem_index: 'A' },
          participant: { user_id: 'user-1' },
        },
      ] as any);

      prisma.contest.findUnique.mockResolvedValue({
        id: 'contest-1',
        start_time: baseTime,
      } as any);

      const result = await service.checkTimeAnomaly('contest-1', {
        minTime: 60,
      });

      expect(result.length).toBe(0);
    });
  });

  describe('generateReport', () => {
    it('should generate a comprehensive report', async () => {
      prisma.contest.findUnique.mockResolvedValue({
        id: 'contest-1',
        title: 'Test Contest',
        start_time: new Date('2024-01-01T10:00:00Z'),
        end_time: new Date('2024-01-01T12:00:00Z'),
      } as any);

      prisma.contestSubmission.findMany.mockResolvedValue([
        {
          id: 'sub-1',
          contest_id: 'contest-1',
          submission_id: 's1',
          contest_problem_id: 'cp-1',
          participant_id: 'p1',
          submitted_at: new Date('2024-01-01T10:01:00Z'),
          time_from_start: 60,
          is_accepted: true,
          submission: {
            id: 's1',
            code: 'function solve() { return 1; }',
            user_id: 'user-1',
            language: 'javascript',
          },
          contestProblem: { problem_index: 'A' },
          participant: { user_id: 'user-1' },
        },
        {
          id: 'sub-2',
          contest_id: 'contest-1',
          submission_id: 's2',
          contest_problem_id: 'cp-1',
          participant_id: 'p2',
          submitted_at: new Date('2024-01-01T10:01:30Z'),
          time_from_start: 90,
          is_accepted: true,
          submission: {
            id: 's2',
            code: 'function solve() { return 1; }',
            user_id: 'user-2',
            language: 'javascript',
          },
          contestProblem: { problem_index: 'A' },
          participant: { user_id: 'user-2' },
        },
      ] as any);

      prisma.contestSubmission.count.mockResolvedValue(2);
      prisma.contestParticipant.count.mockResolvedValue(2);

      const result = await service.generateReport('contest-1');

      expect(result).toHaveProperty('contest_id', 'contest-1');
      expect(result).toHaveProperty('generated_at');
      expect(result).toHaveProperty('similarity_pairs');
      expect(result).toHaveProperty('time_anomalies');
      expect(result).toHaveProperty('summary');
      expect(result.summary).toHaveProperty('total_submissions');
      expect(result.summary).toHaveProperty('suspicious_pairs_count');
      expect(result.summary).toHaveProperty('time_anomalies_count');
    });

    it('should return null when contest not found', async () => {
      prisma.contest.findUnique.mockResolvedValue(null);

      const result = await service.generateReport('non-existent');

      expect(result).toBeNull();
    });
  });
});
