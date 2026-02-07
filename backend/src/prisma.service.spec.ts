import { Test, TestingModule } from '@nestjs/testing';
import { PrismaService } from './prisma.service';
import { ConfigService } from '@nestjs/config';

describe('PrismaService', () => {
  let service: PrismaService;

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        PrismaService,
        {
          provide: ConfigService,
          useValue: {
            get: jest.fn((key: string) => {
              if (key === 'DATABASE_URL') {
                return 'mysql://test:test@localhost:3306/test';
              }
              return undefined;
            }),
          },
        },
      ],
    }).compile();

    service = module.get<PrismaService>(PrismaService);
  });

  it('should be defined', () => {
    expect(service).toBeDefined();
  });

  describe('onModuleInit', () => {
    it('should call $connect on module init', async () => {
      const spy = jest.spyOn(service, '$connect').mockResolvedValue(undefined);

      await service.onModuleInit();

      expect(spy).toHaveBeenCalled();
    });
  });

  describe('onModuleDestroy', () => {
    it('should call $disconnect on module destroy', async () => {
      const spy = jest
        .spyOn(service, '$disconnect')
        .mockResolvedValue(undefined);

      await service.onModuleDestroy();

      expect(spy).toHaveBeenCalled();
    });
  });
});
