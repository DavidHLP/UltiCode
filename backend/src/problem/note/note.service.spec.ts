import { Test, TestingModule } from '@nestjs/testing';
import { ProblemNoteService } from './note.service';
import { PrismaService } from '../../prisma.service';

describe('ProblemNoteService', () => {
  let service: ProblemNoteService;
  let prisma: jest.Mocked<PrismaService>;

  const mockNote = {
    id: 'note-123',
    user_id: 'user-123',
    problem_id: 1,
    content: 'This is my note for this problem',
    created_at: new Date(),
    updated_at: new Date(),
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        ProblemNoteService,
        {
          provide: PrismaService,
          useValue: {
            problemNote: {
              upsert: jest.fn().mockResolvedValue(mockNote),
              findUnique: jest.fn().mockResolvedValue(mockNote),
            },
          },
        },
      ],
    }).compile();

    service = module.get<ProblemNoteService>(ProblemNoteService);
    prisma = module.get(PrismaService);
  });

  it('should be defined', () => {
    expect(service).toBeDefined();
  });

  describe('save', () => {
    it('should create or update a problem note', async () => {
      const result = await service.save('user-123', 1, 'My note content');

      expect(result).toEqual(mockNote);
      expect(prisma.problemNote.upsert).toHaveBeenCalledWith({
        where: {
          user_id_problem_id: {
            user_id: 'user-123',
            problem_id: 1,
          },
        },
        update: { content: 'My note content' },
        create: {
          user_id: 'user-123',
          problem_id: 1,
          content: 'My note content',
        },
      });
    });
  });

  describe('findByProblem', () => {
    it('should return a note for a user and problem', async () => {
      const result = await service.findByProblem('user-123', 1);

      expect(result).toEqual(mockNote);
      expect(prisma.problemNote.findUnique).toHaveBeenCalledWith({
        where: {
          user_id_problem_id: {
            user_id: 'user-123',
            problem_id: 1,
          },
        },
      });
    });
  });
});
