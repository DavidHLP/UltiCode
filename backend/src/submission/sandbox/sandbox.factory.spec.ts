import { Test, TestingModule } from '@nestjs/testing';
import { ConfigService } from '@nestjs/config';
import { SandboxFactory } from './sandbox.factory';
import { DockerSandboxService } from './docker-sandbox.service';
import { VmSandboxService } from './vm-sandbox.service';

describe('SandboxFactory', () => {
  let factory: SandboxFactory;
  let dockerSandbox: DockerSandboxService;
  let vmSandbox: VmSandboxService;

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        SandboxFactory,
        {
          provide: ConfigService,
          useValue: {
            get: jest.fn().mockReturnValue('vm'),
          },
        },
        {
          provide: DockerSandboxService,
          useValue: {
            isHealthy: jest.fn().mockResolvedValue(false),
            getType: jest.fn().mockReturnValue('docker'),
          },
        },
        {
          provide: VmSandboxService,
          useValue: {
            isHealthy: jest.fn().mockResolvedValue(true),
            getType: jest.fn().mockReturnValue('vm'),
            execute: jest.fn(),
          },
        },
      ],
    }).compile();

    factory = module.get<SandboxFactory>(SandboxFactory);
    dockerSandbox = module.get<DockerSandboxService>(DockerSandboxService);
    vmSandbox = module.get<VmSandboxService>(VmSandboxService);
  });

  it('should be defined', () => {
    expect(factory).toBeDefined();
  });

  describe('getSandbox', () => {
    it('should return VM sandbox when Docker is not healthy', async () => {
      const sandbox = await factory.getSandbox();
      expect(sandbox.getType()).toBe('vm');
    });

    it('should fallback to VM when Docker fails health check', async () => {
      jest.spyOn(dockerSandbox, 'isHealthy').mockResolvedValue(false);
      jest.spyOn(vmSandbox, 'isHealthy').mockResolvedValue(true);

      const sandbox = await factory.getSandbox();
      expect(sandbox.getType()).toBe('vm');
    });
  });

  describe('getSandboxByType', () => {
    it('should return Docker sandbox when requested', () => {
      const sandbox = factory.getSandboxByType('docker');
      expect(sandbox.getType()).toBe('docker');
    });

    it('should return VM sandbox when requested', () => {
      const sandbox = factory.getSandboxByType('vm');
      expect(sandbox.getType()).toBe('vm');
    });
  });

  describe('checkAvailability', () => {
    it('should return availability status for both sandboxes', async () => {
      jest.spyOn(dockerSandbox, 'isHealthy').mockResolvedValue(false);
      jest.spyOn(vmSandbox, 'isHealthy').mockResolvedValue(true);

      const availability = await factory.checkAvailability();

      expect(availability.docker).toBe(false);
      expect(availability.vm).toBe(true);
    });
  });
});
