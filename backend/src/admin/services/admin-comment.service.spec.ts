import { Test, TestingModule } from '@nestjs/testing';
import { AdminCommentService } from './admin-comment.service';
import { PrismaService } from '../../prisma.service';

describe('AdminCommentService', () => {
  let service: AdminCommentService;
  let prisma: jest.Mocked<PrismaService>;

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
      ],
    }).compile();

    service = module.get<AdminCommentService>(AdminCommentService);
    prisma = module.get(PrismaService);
  });

  it('should be defined', () => {
    expect(service).toBeDefined();
  });
});
