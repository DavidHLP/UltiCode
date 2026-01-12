import { Test, TestingModule } from '@nestjs/testing';
import { AdminTagService } from './admin-tag.service';
import { PrismaService } from '../../prisma.service';
import { AuditService } from './audit.service';

describe('AdminTagService', () => {
  let service: AdminTagService;
  let _prisma: jest.Mocked<PrismaService>;

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
        {
          provide: AuditService,
          useValue: {
            log: jest.fn().mockResolvedValue({}),
            getAuditLogs: jest.fn().mockResolvedValue({ logs: [], total: 0 }),
            getAuditStats: jest.fn().mockResolvedValue({
              totalActions: 0,
              actionsByEntity: [],
              actionsByPerformer: [],
              topPerformers: [],
            }),
          },
        },
      ],
    }).compile();

    service = module.get<AdminTagService>(AdminTagService);
    _prisma = module.get(PrismaService);
  });

  it('should be defined', () => {
    expect(service).toBeDefined();
  });
});
