import { Test, TestingModule } from '@nestjs/testing';
import { JudgeService } from './judge.service';

describe('JudgeService', () => {
  let service: JudgeService;

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [JudgeService],
    }).compile();

    service = module.get<JudgeService>(JudgeService);
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

    it('should judge javascript code and return Accepted result', () => {
      const code = 'function solution(a, b) { return a + b; }';

      const result = service.judge('javascript', code, testCases);

      expect(result.verdict).toBe('Accepted');
      expect(result.cases).toHaveLength(1);
      expect(result.cases[0].status).toBe('Accepted');
    });

    it('should judge typescript code and return Accepted result', () => {
      const code =
        'function solution(a: number, b: number): number { return a + b; }';

      const result = service.judge('typescript', code, testCases);

      expect(result.verdict).toBe('Accepted');
    });

    it('should return Compile Error for unsupported language', () => {
      const code = 'def solution(a, b): return a + b';

      const result = service.judge('python', code, testCases);

      expect(result.verdict).toBe('Compile Error');
      expect(result.compileError).toContain('not supported');
    });

    it('should return Wrong Answer for incorrect output', () => {
      const code = 'function solution(a, b) { return a - b; }';

      const result = service.judge('javascript', code, testCases);

      expect(result.verdict).toBe('Wrong Answer');
    });

    it('should handle array inputs and outputs', () => {
      const arrayTestCases = [
        {
          id: 'case-1',
          inputs: [{ name: 'nums', value: '[1, 2, 3]' }],
          output: '[1, 2, 3]',
        },
      ];

      const code = 'function solution(nums) { return nums; }';

      const result = service.judge('javascript', code, arrayTestCases);

      expect(result.verdict).toBe('Accepted');
    });

    it('should handle empty output test case', () => {
      const emptyTestCases = [
        {
          id: 'case-1',
          inputs: [{ name: 'x', value: '5' }],
          output: '',
        },
      ];

      const code = 'function solution(x) { return; }';

      const result = service.judge('javascript', code, emptyTestCases);

      expect(result.verdict).toBe('Accepted');
    });
  });
});
