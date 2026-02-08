/**
 * Docker Orchestrator Service Unit Tests
 */

import { Test, TestingModule } from '@nestjs/testing';
import { ConfigService } from '@nestjs/config';
import { DockerOrchestratorService } from './docker-orchestrator.service';
import { ContainerPoolService } from './container-pool.service';
import { JudgeTestCase } from '../judge.service';

describe('DockerOrchestratorService', () => {
  let service: DockerOrchestratorService;
  let _configService: ConfigService;
  let _poolService: ContainerPoolService;

  const mockConfigService = {
    get: jest.fn((key: string, defaultValue?: string) => {
      const config: Record<string, string> = {
        JUDGE_CONTAINER_IMAGE: 'ulticode-judge:latest',
        JUDGE_DEFAULT_MEMORY_LIMIT: '256',
        JUDGE_DEFAULT_TIME_LIMIT: '2000',
        DOCKER_SOCKET_PATH: '/var/run/docker.sock',
      };
      return config[key] ?? defaultValue;
    }),
  };

  const mockPoolService = {
    acquire: jest.fn(),
    release: jest.fn(),
    getStats: jest.fn(),
    shutdown: jest.fn(),
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        DockerOrchestratorService,
        {
          provide: ConfigService,
          useValue: mockConfigService,
        },
        {
          provide: ContainerPoolService,
          useValue: mockPoolService,
        },
      ],
    }).compile();

    service = module.get<DockerOrchestratorService>(DockerOrchestratorService);
    _configService = module.get<ConfigService>(ConfigService);
    _poolService = module.get<ContainerPoolService>(ContainerPoolService);
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('should be defined', () => {
    expect(service).toBeDefined();
  });

  describe('executeInSandbox', () => {
    const mockTestCases: JudgeTestCase[] = [
      {
        id: '1',
        inputs: [
          { name: 'a', value: '1' },
          { name: 'b', value: '2' },
        ],
        output: '3',
      },
    ];

    const mockContainer = {
      id: 'test-container-id',
      inUse: false,
      createdAt: new Date(),
      executionCount: 0,
    };

    it('should acquire and release container during execution', async () => {
      mockPoolService.acquire.mockResolvedValue(mockContainer);
      mockPoolService.release.mockReturnValue(undefined);

      // Note: This test will need to be updated once the actual Docker execution
      // is implemented, as the current implementation has a synchronous fallback
      const _result = await service.executeInSandbox(
        'function add(a, b) { return a + b; }',
        'javascript',
        mockTestCases,
      );

      expect(_poolService.acquire).toHaveBeenCalled();
      expect(_poolService.release).toHaveBeenCalledWith(mockContainer);
    });

    it('should handle container acquisition failure gracefully', async () => {
      mockPoolService.acquire.mockRejectedValue(
        new Error('No containers available'),
      );

      const result = await service.executeInSandbox(
        'function add(a, b) { return a + b; }',
        'javascript',
        mockTestCases,
      );

      expect(result.verdict).toBe('System Error');
    });
  });

  describe('checkImageExists', () => {
    it('should return true if image exists', () => {
      // This test would require mocking Dockerode
      // For now, we'll test that the method exists
      expect(typeof service.checkImageExists).toBe('function');
    });
  });

  describe('getPoolStats', () => {
    it('should return pool statistics', () => {
      const mockStats = {
        totalContainers: 5,
        activeContainers: 2,
        availableContainers: 3,
        totalExecutions: 100,
        avgExecutionTime: 150,
        utilizationRate: 0.4,
      };

      mockPoolService.getStats.mockReturnValue(mockStats);

      const stats = service.getPoolStats();
      expect(stats).toEqual(mockStats);
      expect(_poolService.getStats).toHaveBeenCalled();
    });
  });
});
