import { Test, TestingModule } from '@nestjs/testing';
import { AdminCommentService } from './admin-comment.service';
import { PrismaService } from '../../prisma.service';
import { AuditService } from './audit.service';

describe('AdminCommentService', () => {
  let service: AdminCommentService;
  let _prisma: jest.Mocked<PrismaService>;

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        AdminCommentService,
        {
          provide: PrismaService,
          useValue: {
            forumComment: {
              findMany: jest.fn().mockResolvedValue([]),
              findUnique: jest.fn().mockResolvedValue(null),
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

    service = module.get<AdminCommentService>(AdminCommentService);
    _prisma = module.get(PrismaService);
  });

  it('should be defined', () => {
    expect(service).toBeDefined();
  });
});
