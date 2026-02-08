/**
 * End-to-End Tests for Judge Service with Docker Integration
 */

import { Test, TestingModule } from '@nestjs/testing';
import { ConfigService } from '@nestjs/config';
import { JudgeService } from '../src/submission/judge.service';
import { DockerOrchestratorService } from '../src/submission/services/docker-orchestrator.service';
import { ContainerPoolService } from '../src/submission/services/container-pool.service';
import { JudgeTestCase } from '../src/submission/judge.service';

describe('Judge Service E2E Tests', () => {
  let judgeService: JudgeService;
  let dockerOrchestrator: DockerOrchestratorService;
  let containerPool: ContainerPoolService;

  beforeAll(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        JudgeService,
        DockerOrchestratorService,
        ContainerPoolService,
        {
          provide: ConfigService,
          useValue: {
            get: jest.fn((key: string) => {
              const config: Record<string, string> = {
                JUDGE_CONTAINER_ENABLED: 'true',
                JUDGE_CONTAINER_IMAGE: 'ulticode-judge:latest',
                JUDGE_CONTAINER_POOL_SIZE: '2',
                JUDGE_CONTAINER_MAX_CONTAINERS: '3',
                JUDGE_DEFAULT_MEMORY_LIMIT: '256',
                JUDGE_DEFAULT_TIME_LIMIT: '2000',
                DOCKER_SOCKET_PATH: '/var/run/docker.sock',
              };
              return config[key];
            }),
          },
        },
      ],
    }).compile();

    judgeService = module.get<JudgeService>(JudgeService);
    dockerOrchestrator = module.get<DockerOrchestratorService>(
      DockerOrchestratorService,
    );
    containerPool = module.get<ContainerPoolService>(ContainerPoolService);
  });

  afterAll(async () => {
    await containerPool.shutdown();
  });

  describe('Simple JavaScript Execution', () => {
    it('should execute simple addition function', async () => {
      const code = 'function add(a, b) { return a + b; }';
      const testCases: JudgeTestCase[] = [
        {
          id: '1',
          inputs: [
            { name: 'a', value: '1' },
            { name: 'b', value: '2' },
          ],
          output: '3',
        },
        {
          id: '2',
          inputs: [
            { name: 'a', value: '-5' },
            { name: 'b', value: '10' },
          ],
          output: '5',
        },
      ];

      const result = await judgeService.judge('javascript', code, testCases);

      expect(result.verdict).toBe('Accepted');
      expect(result.cases).toHaveLength(2);
      expect(result.cases[0].status).toBe('Accepted');
      expect(result.cases[1].status).toBe('Accepted');
    });

    it('should detect wrong answer', async () => {
      const code = 'function add(a, b) { return a - b; }';
      const testCases: JudgeTestCase[] = [
        {
          id: '1',
          inputs: [
            { name: 'a', value: '5' },
            { name: 'b', value: '3' },
          ],
          output: '8',
        },
      ];

      const result = await judgeService.judge('javascript', code, testCases);

      expect(result.verdict).toBe('Wrong Answer');
      expect(result.cases[0].status).toBe('Wrong Answer');
    });
  });

  describe('TypeScript Execution', () => {
    it('should execute TypeScript code', async () => {
      const code =
        'function greet(name: string): string { return `Hello, ${name}!`; }';
      const testCases: JudgeTestCase[] = [
        {
          id: '1',
          inputs: [{ name: 'name', value: '"World"' }],
          output: '"Hello, World!"',
        },
      ];

      const result = await judgeService.judge('typescript', code, testCases);

      expect(result.verdict).toBe('Accepted');
    });
  });

  describe('Array and Object Handling', () => {
    it('should handle array inputs and outputs', async () => {
      const code =
        'function sumArray(arr) { return arr.reduce((a, b) => a + b, 0); }';
      const testCases: JudgeTestCase[] = [
        {
          id: '1',
          inputs: [{ name: 'arr', value: '[1, 2, 3, 4, 5]' }],
          output: '15',
        },
      ];

      const result = await judgeService.judge('javascript', code, testCases);

      expect(result.verdict).toBe('Accepted');
    });

    it('should handle nested objects', async () => {
      const code = 'function getDeep(obj) { return obj.a.b.c; }';
      const testCases: JudgeTestCase[] = [
        {
          id: '1',
          inputs: [{ name: 'obj', value: '{"a": {"b": {"c": 42}}}' }],
          output: '42',
        },
      ];

      const result = await judgeService.judge('javascript', code, testCases);

      expect(result.verdict).toBe('Accepted');
    });
  });

  describe('Error Handling', () => {
    it('should handle runtime errors', async () => {
      const code = 'function divide(a, b) { return a / b; }';
      const testCases: JudgeTestCase[] = [
        {
          id: '1',
          inputs: [
            { name: 'a', value: '10' },
            { name: 'b', value: '0' },
          ],
          output: 'Infinity',
        },
      ];

      const result = await judgeService.judge('javascript', code, testCases);

      // Division by zero returns Infinity in JavaScript, which is valid
      expect(result.verdict).toBe('Accepted');
    });

    it('should detect compile errors', async () => {
      const code = 'function broken( { return missing closing brace;';
      const testCases: JudgeTestCase[] = [
        {
          id: '1',
          inputs: [],
          output: '',
        },
      ];

      const result = await judgeService.judge('javascript', code, testCases);

      expect(result.verdict).toBe('Compile Error');
      expect(result.compileError).toBeTruthy();
    });
  });

  describe('Multiple Test Cases', () => {
    it('should stop on first failure', async () => {
      const code = 'function multiply(a, b) { return a * b; }';
      const testCases: JudgeTestCase[] = [
        {
          id: '1',
          inputs: [
            { name: 'a', value: '2' },
            { name: 'b', value: '3' },
          ],
          output: '6',
        },
        {
          id: '2',
          inputs: [
            { name: 'a', value: '4' },
            { name: 'b', value: '5' },
          ],
          output: '20', // Wrong, should be 21
        },
        {
          id: '3',
          inputs: [
            { name: 'a', value: '1' },
            { name: 'b', value: '1' },
          ],
          output: '1',
        },
      ];

      const result = await judgeService.judge('javascript', code, testCases);

      expect(result.verdict).toBe('Wrong Answer');
      expect(result.cases).toHaveLength(3);
      expect(result.cases[0].status).toBe('Accepted');
      expect(result.cases[1].status).toBe('Wrong Answer');
      expect(result.cases[2].status).toBe('Pending');
    });
  });

  describe('Container Pool Statistics', () => {
    it('should provide pool statistics', () => {
      const stats = dockerOrchestrator.getPoolStats();

      expect(stats).toHaveProperty('totalContainers');
      expect(stats).toHaveProperty('activeContainers');
      expect(stats).toHaveProperty('availableContainers');
      expect(stats).toHaveProperty('utilizationRate');
    });
  });
});
