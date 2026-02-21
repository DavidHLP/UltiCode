import { Test, TestingModule } from '@nestjs/testing';
import { ConfigService } from '@nestjs/config';
import { VmSandboxService } from './vm-sandbox.service';
import { JudgeTestCase } from '../judge.service';

describe('VmSandboxService', () => {
  let service: VmSandboxService;

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        VmSandboxService,
        {
          provide: ConfigService,
          useValue: {
            get: jest.fn(),
          },
        },
      ],
    }).compile();

    service = module.get<VmSandboxService>(VmSandboxService);
  });

  it('should be defined', () => {
    expect(service).toBeDefined();
  });

  it('should return vm as sandbox type', async () => {
    expect(service.getType()).toBe('vm');
  });

  it('should be healthy', async () => {
    const healthy = await service.isHealthy();
    expect(healthy).toBe(true);
  });

  describe('execute', () => {
    const createTestCase = (
      inputs: { name: string; value: string }[],
      output: string,
    ): JudgeTestCase => ({
      id: 'test-1',
      inputs,
      output,
    });

    it('should execute simple JavaScript addition', async () => {
      const code = `
        function add(a, b) {
          return a + b;
        }
      `;
      const testCase = createTestCase(
        [
          { name: 'a', value: '2' },
          { name: 'b', value: '3' },
        ],
        '5',
      );

      const result = await service.execute('javascript', code, testCase);

      expect(result.status).toBe('Accepted');
      expect(result.output).toBe('5');
    });

    it('should execute JavaScript array reversal', async () => {
      const code = `
        function reverse(arr) {
          return arr.slice().reverse();
        }
      `;
      const testCase = createTestCase([{ name: 'arr', value: '[1,2,3]' }], '[3,2,1]');

      const result = await service.execute('javascript', code, testCase);

      expect(result.status).toBe('Accepted');
      expect(result.output).toBe('[3,2,1]');
    });

    it('should handle TypeScript code', async () => {
      const code = `
        function greet(name: string): string {
          return 'Hello, ' + name;
        }
      `;
      const testCase = createTestCase([{ name: 'name', value: '"World"' }], 'Hello, World');

      const result = await service.execute('typescript', code, testCase);

      expect(result.status).toBe('Accepted');
      expect(result.output).toBe('Hello, World');
    });

    it('should return Wrong Answer for incorrect output', async () => {
      const code = `
        function add(a, b) {
          return a + b;
        }
      `;
      const testCase = createTestCase(
        [
          { name: 'a', value: '2' },
          { name: 'b', value: '3' },
        ],
        '10',
      );

      const result = await service.execute('javascript', code, testCase);

      expect(result.status).toBe('Wrong Answer');
      expect(result.output).toBe('5');
      expect(result.expectedOutput).toBe('10');
    });

    it('should return Compile Error for unsupported language', async () => {
      const code = 'print("hello")';
      const testCase = createTestCase([], '');

      const result = await service.execute('python', code, testCase);

      expect(result.status).toBe('Compile Error');
      expect(result.detail).toContain('not supported');
    });

    it('should return Compile Error for code without entry function', async () => {
      const code = 'const x = 42;';
      const testCase = createTestCase([], '');

      const result = await service.execute('javascript', code, testCase);

      expect(result.status).toBe('Compile Error');
      expect(result.detail).toContain('entry function');
    });

    it('should return Runtime Error for code that throws', async () => {
      const code = `
        function crash() {
          throw new Error('Intentional error');
        }
      `;
      const testCase = createTestCase([], '');

      const result = await service.execute('javascript', code, testCase);

      expect(result.status).toBe('Runtime Error');
      expect(result.detail).toContain('Intentional error');
    });

    it('should handle floating point comparison with tolerance', async () => {
      const code = `
        function divide(a, b) {
          return a / b;
        }
      `;
      const testCase = createTestCase(
        [
          { name: 'a', value: '1' },
          { name: 'b', value: '3' },
        ],
        '0.3333333333333333',
      );

      const result = await service.execute('javascript', code, testCase);

      expect(result.status).toBe('Accepted');
    });

    it('should handle nested object comparison', async () => {
      const code = `
        function transform(obj) {
          return { result: obj.value * 2 };
        }
      `;
      const testCase = createTestCase(
        [{ name: 'obj', value: '{"value": 5}' }],
        '{"result":10}',
      );

      const result = await service.execute('javascript', code, testCase);

      expect(result.status).toBe('Accepted');
    });
  });
});
