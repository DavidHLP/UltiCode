import { Test, TestingModule } from '@nestjs/testing';
import { AdminTagService } from './admin-tag.service';
import { PrismaService } from '../../prisma.service';

describe('AdminTagService', () => {
  let service: AdminTagService;
  let prisma: jest.Mocked<PrismaService>;

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        AdminTagService,
        {
          provide: PrismaService,
          useValue: {
            tag: {
              findMany: jest.fn().mockResolvedValue([]),
              findUnique: jest.fn().mockResolvedValue(null),
              create: jest.fn().mockResolvedValue({}),
              update: jest.fn().mockResolvedValue({}),
              delete: jest.fn().mockResolvedValue({}),
              count: jest.fn().mockResolvedValue(0),
            },
          },
        },
      ],
    }).compile();

    service = module.get<AdminTagService>(AdminTagService);
    prisma = module.get(PrismaService);
  });

  it('should be defined', () => {
    expect(service).toBeDefined();
  });
});
