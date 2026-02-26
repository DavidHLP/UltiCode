import { Test, TestingModule } from '@nestjs/testing';
import { JudgeService } from './judge.service';
import { SandboxFactory } from './sandbox/sandbox.factory';
import { SandboxServiceInterface } from './sandbox/sandbox.interface';
import { JudgeTestCase, JudgeCaseResult } from './judge.service';

describe('JudgeService', () => {
  let service: JudgeService;
  let mockSandbox: jest.Mocked<SandboxServiceInterface>;
  let mockSandboxFactory: jest.Mocked<SandboxFactory>;

  beforeEach(async () => {
    mockSandbox = {
      execute: jest.fn(),
      isHealthy: jest.fn().mockResolvedValue(true),
      getType: jest.fn().mockReturnValue('vm'),
    };

    mockSandboxFactory = {
      getSandbox: jest.fn().mockResolvedValue(mockSandbox),
      getSandboxByType: jest.fn().mockReturnValue(mockSandbox),
      checkAvailability: jest
        .fn()
        .mockResolvedValue({ vm: true, docker: false }),
    } as unknown as jest.Mocked<SandboxFactory>;

    const module: TestingModule = await Test.createTestingModule({
      providers: [
        JudgeService,
        {
          provide: SandboxFactory,
          useValue: mockSandboxFactory,
        },
      ],
    }).compile();

    service = module.get<JudgeService>(JudgeService);
    await service.onModuleInit();
  });

  it('should be defined', () => {
    expect(service).toBeDefined();
  });

  describe('judge', () => {
    const createTestCase = (
      inputs: { name: string; value: string }[],
      output: string,
      id = 'case-1',
    ): JudgeTestCase => ({
      id,
      inputs,
      output,
    });

    const createResult = (
      status: JudgeCaseResult['status'],
      overrides?: Partial<JudgeCaseResult>,
    ): JudgeCaseResult => ({
      status,
      time: 10,
      memory: 5,
      output: 'result',
      expectedOutput: 'expected',
      inputs: [],
      ...overrides,
    });

    it('should judge javascript code and return Accepted result', async () => {
      mockSandbox.execute.mockResolvedValueOnce(
        createResult('Accepted', { output: '3', expectedOutput: '3' }),
      );

      const code = 'function solution(a, b) { return a + b; }';
      const testCases = [
        createTestCase(
          [
            { name: 'a', value: '1' },
            { name: 'b', value: '2' },
          ],
          '3',
        ),
      ];

      const result = await service.judge('javascript', code, testCases);

      expect(result.verdict).toBe('Accepted');
      expect(result.cases).toHaveLength(1);
      expect(result.cases[0].status).toBe('Accepted');
    });

    it('should judge typescript code and return Accepted result', async () => {
      mockSandbox.execute.mockResolvedValueOnce(
        createResult('Accepted', { output: '3', expectedOutput: '3' }),
      );

      const code =
        'function solution(a: number, b: number): number { return a + b; }';
      const testCases = [
        createTestCase(
          [
            { name: 'a', value: '1' },
            { name: 'b', value: '2' },
          ],
          '3',
        ),
      ];

      const result = await service.judge('typescript', code, testCases);

      expect(result.verdict).toBe('Accepted');
    });

    it('should return Compile Error for unsupported language', async () => {
      mockSandbox.execute.mockResolvedValueOnce(
        createResult('Compile Error', {
          detail: 'Language python is not supported in VM sandbox.',
          output: '',
        }),
      );

      const code = 'def solution(a, b): return a + b';
      const testCases = [
        createTestCase(
          [
            { name: 'a', value: '1' },
            { name: 'b', value: '2' },
          ],
          '3',
        ),
      ];

      const result = await service.judge('python', code, testCases);

      expect(result.verdict).toBe('Compile Error');
      expect(result.compileError).toContain('not supported');
    });

    it('should return Wrong Answer for incorrect output', async () => {
      mockSandbox.execute.mockResolvedValueOnce(
        createResult('Wrong Answer', { output: '-1', expectedOutput: '3' }),
      );

      const code = 'function solution(a, b) { return a - b; }';
      const testCases = [
        createTestCase(
          [
            { name: 'a', value: '1' },
            { name: 'b', value: '2' },
          ],
          '3',
        ),
      ];

      const result = await service.judge('javascript', code, testCases);

      expect(result.verdict).toBe('Wrong Answer');
    });

    it('should handle array inputs and outputs', async () => {
      mockSandbox.execute.mockResolvedValueOnce(
        createResult('Accepted', {
          output: '[1,2,3]',
          expectedOutput: '[1,2,3]',
        }),
      );

      const arrayTestCases = [
        createTestCase([{ name: 'nums', value: '[1, 2, 3]' }], '[1, 2, 3]'),
      ];
      const code = 'function solution(nums) { return nums; }';

      const result = await service.judge('javascript', code, arrayTestCases);

      expect(result.verdict).toBe('Accepted');
    });

    it('should handle empty output test case', async () => {
      mockSandbox.execute.mockResolvedValueOnce(
        createResult('Accepted', { output: 'undefined', expectedOutput: '' }),
      );

      const emptyTestCases = [createTestCase([{ name: 'x', value: '5' }], '')];
      const code = 'function solution(x) { return; }';

      const result = await service.judge('javascript', code, emptyTestCases);

      expect(result.verdict).toBe('Accepted');
    });

    it('should stop on first wrong answer and mark remaining as Pending', async () => {
      mockSandbox.execute
        .mockResolvedValueOnce(
          createResult('Accepted', { output: '5', expectedOutput: '5' }),
        )
        .mockResolvedValueOnce(
          createResult('Wrong Answer', { output: '5', expectedOutput: '10' }),
        );

      const testCases = [
        createTestCase(
          [
            { name: 'a', value: '2' },
            { name: 'b', value: '3' },
          ],
          '5',
          '1',
        ),
        createTestCase(
          [
            { name: 'a', value: '5' },
            { name: 'b', value: '5' },
          ],
          '10',
          '2',
        ),
        createTestCase(
          [
            { name: 'a', value: '1' },
            { name: 'b', value: '1' },
          ],
          '2',
          '3',
        ),
      ];

      const result = await service.judge('javascript', 'code', testCases);

      expect(result.verdict).toBe('Wrong Answer');
      expect(result.cases).toHaveLength(3);
      expect(result.cases[2].status).toBe('Pending');
      expect(mockSandbox.execute).toHaveBeenCalledTimes(2);
    });

    it('should handle time limit exceeded', async () => {
      mockSandbox.execute.mockResolvedValueOnce(
        createResult('Time Limit Exceeded', {
          time: 5000,
          detail: 'Execution timed out',
        }),
      );

      const testCases = [createTestCase([], '5')];

      const result = await service.judge(
        'javascript',
        'while(true){}',
        testCases,
      );

      expect(result.verdict).toBe('Time Limit Exceeded');
    });

    it('should handle runtime error', async () => {
      mockSandbox.execute.mockResolvedValueOnce(
        createResult('Runtime Error', {
          detail: 'TypeError: Cannot read property of undefined',
        }),
      );

      const testCases = [createTestCase([], '5')];

      const result = await service.judge('javascript', 'code', testCases);

      expect(result.verdict).toBe('Runtime Error');
    });

    it('should aggregate total runtime and max memory', async () => {
      mockSandbox.execute
        .mockResolvedValueOnce(
          createResult('Accepted', { time: 100, memory: 10 }),
        )
        .mockResolvedValueOnce(
          createResult('Accepted', { time: 50, memory: 20 }),
        )
        .mockResolvedValueOnce(
          createResult('Accepted', { time: 75, memory: 15 }),
        );

      const testCases = [
        createTestCase([], '1', '1'),
        createTestCase([], '2', '2'),
        createTestCase([], '3', '3'),
      ];

      const result = await service.judge('javascript', 'code', testCases);

      expect(result.runtime).toBe(225);
      expect(result.memory).toBe(20);
    });
  });
});
