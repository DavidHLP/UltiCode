/**
 * Container Pool Service Unit Tests
 */

import { Test, TestingModule } from '@nestjs/testing';
import { ConfigService } from '@nestjs/config';
import { ContainerPoolService } from './container-pool.service';

describe('ContainerPoolService', () => {
  let service: ContainerPoolService;
  let configService: ConfigService;

  const mockConfigService = {
    get: jest.fn((key: string, defaultValue?: string) => {
      const config: Record<string, string> = {
        JUDGE_CONTAINER_IMAGE: 'ulticode-judge:latest',
        JUDGE_CONTAINER_POOL_SIZE: '3',
        JUDGE_CONTAINER_MAX_CONTAINERS: '5',
        JUDGE_DEFAULT_MEMORY_LIMIT: '256',
        JUDGE_DEFAULT_TIME_LIMIT: '2000',
        DOCKER_SOCKET_PATH: '/var/run/docker.sock',
      };
      return config[key] ?? defaultValue;
    }),
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        ContainerPoolService,
        {
          provide: ConfigService,
          useValue: mockConfigService,
        },
      ],
    }).compile();

    service = module.get<ContainerPoolService>(ContainerPoolService);
    configService = module.get<ConfigService>(ConfigService);
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('should be defined', () => {
    expect(service).toBeDefined();
  });

  describe('getStats', () => {
    it('should return initial pool statistics', () => {
      const stats = service.getStats();

      expect(stats).toHaveProperty('totalContainers');
      expect(stats).toHaveProperty('activeContainers');
      expect(stats).toHaveProperty('availableContainers');
      expect(stats).toHaveProperty('totalExecutions');
      expect(stats).toHaveProperty('avgExecutionTime');
      expect(stats).toHaveProperty('utilizationRate');

      // Initial state should have no containers
      expect(stats.totalContainers).toBe(0);
      expect(stats.activeContainers).toBe(0);
    });
  });

  describe('configuration', () => {
    it('should use configuration from ConfigService', () => {
      expect(configService.get).toHaveBeenCalledWith('JUDGE_CONTAINER_IMAGE');
      expect(configService.get).toHaveBeenCalledWith(
        'JUDGE_CONTAINER_POOL_SIZE',
      );
      expect(configService.get).toHaveBeenCalledWith(
        'JUDGE_CONTAINER_MAX_CONTAINERS',
      );
    });
  });

  describe('shutdown', () => {
    it('should shutdown gracefully', async () => {
      await expect(service.shutdown()).resolves.not.toThrow();
    });
  });

  describe('acquire', () => {
    it('should create a container when none available', async () => {
      // This test requires Docker to be running
      // If Docker is available, it will create a container
      // If not, it will throw an error (which is expected in non-Docker environments)
      try {
        const container = await service.acquire();
        // If we got here, Docker is working
        expect(container).toBeDefined();
        expect(container.id).toBeTruthy();

        // Clean up
        await service.shutdown();
      } catch (error) {
        // Expected when Docker is not available
        const isDockerError =
          (error as Error).message.includes('docker') ||
          (error as Error).message.includes('ENOTNO');
        if (!isDockerError) {
          throw error;
        }
      }
    });
  });
});
