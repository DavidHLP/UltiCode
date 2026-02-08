import { Test, TestingModule } from '@nestjs/testing';
import { JudgeService } from './judge.service';
import { ConfigService } from '@nestjs/config';
import { DockerOrchestratorService } from './services/docker-orchestrator.service';

describe('JudgeService', () => {
  let service: JudgeService;

  const mockConfigService = {
    get: jest.fn((key: string) => {
      if (key === 'JUDGE_CONTAINER_ENABLED') {
        return 'false'; // Default to legacy vm for these tests
      }
      return undefined;
    }),
  };

  const mockDockerOrchestrator = {
    executeInSandbox: jest.fn(),
    checkImageExists: jest.fn(),
    getPoolStats: jest.fn(),
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        JudgeService,
        {
          provide: ConfigService,
          useValue: mockConfigService,
        },
        {
          provide: DockerOrchestratorService,
          useValue: mockDockerOrchestrator,
        },
      ],
    }).compile();

    service = module.get<JudgeService>(JudgeService);
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('should be defined', () => {
    expect(service).toBeDefined();
  });

  describe('judge', () => {
    const testCases = [
      {
        id: 'case-1',
        label: 'Test Case 1',
        inputs: [
          { name: 'a', value: '1' },
          { name: 'b', value: '2' },
        ],
        output: '3',
      },
    ];

    it('should judge javascript code and return Accepted result', async () => {
      const code = 'function solution(a, b) { return a + b; }';

      const result = await service.judge('javascript', code, testCases);

      expect(result.verdict).toBe('Accepted');
      expect(result.cases).toHaveLength(1);
      expect(result.cases[0].status).toBe('Accepted');
    });

    it('should judge typescript code and return Accepted result', async () => {
      const code =
        'function solution(a: number, b: number): number { return a + b; }';

      const result = await service.judge('typescript', code, testCases);

      expect(result.verdict).toBe('Accepted');
    });

    it('should return Compile Error for unsupported language', async () => {
      const code = 'def solution(a, b): return a + b';

      const result = await service.judge('python', code, testCases);

      expect(result.verdict).toBe('Compile Error');
      expect(result.compileError).toContain('not supported');
    });

    it('should return Wrong Answer for incorrect output', async () => {
      const code = 'function solution(a, b) { return a - b; }';

      const result = await service.judge('javascript', code, testCases);

      expect(result.verdict).toBe('Wrong Answer');
    });

    it('should handle array inputs and outputs', async () => {
      const arrayTestCases = [
        {
          id: 'case-1',
          inputs: [{ name: 'nums', value: '[1, 2, 3]' }],
          output: '[1, 2, 3]',
        },
      ];

      const code = 'function solution(nums) { return nums; }';

      const result = await service.judge('javascript', code, arrayTestCases);

      expect(result.verdict).toBe('Accepted');
    });

    it('should handle empty output test case', async () => {
      const emptyTestCases = [
        {
          id: 'case-1',
          inputs: [{ name: 'x', value: '5' }],
          output: '',
        },
      ];

      const code = 'function solution(x) { return; }';

      const result = await service.judge('javascript', code, emptyTestCases);

      expect(result.verdict).toBe('Accepted');
    });
  });
});
