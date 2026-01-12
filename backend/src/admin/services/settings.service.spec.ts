import { Test, TestingModule } from '@nestjs/testing';
import { AdminSettingsService } from './settings.service';
import { PrismaService } from '../../prisma.service';

describe('AdminSettingsService', () => {
  let service: AdminSettingsService;
  let prisma: jest.Mocked<PrismaService>;

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        AdminSettingsService,
        {
          provide: PrismaService,
          useValue: {
            systemSetting: {
              findMany: jest.fn().mockResolvedValue([]),
              findUnique: jest.fn().mockResolvedValue(null),
              upsert: jest.fn().mockResolvedValue({}),
            },
          },
        },
      ],
    }).compile();

    service = module.get<AdminSettingsService>(AdminSettingsService);
    prisma = module.get(PrismaService);
  });

  it('should be defined', () => {
    expect(service).toBeDefined();
  });
});
