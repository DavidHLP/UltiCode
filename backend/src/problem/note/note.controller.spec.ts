import { Test, TestingModule } from '@nestjs/testing';
import { ProblemNoteController } from './note.controller';
import { ProblemNoteService } from './note.service';
import { SaveNoteDto } from './dto/save-note.dto';
import { JwtService } from '@nestjs/jwt';
import { Reflector, ModuleRef } from '@nestjs/core';
import { AuthGuard } from '../../auth/auth.guard';

describe('ProblemNoteController', () => {
  let controller: ProblemNoteController;
  let noteService: jest.Mocked<ProblemNoteService>;

  const mockNote = {
    id: 'note-123',
    user_id: 'user-123',
    problem_id: BigInt(1),
    content: 'This is my note',
    created_at: new Date(),
    updated_at: new Date(),
  };

  const mockReq = {
    user: { id: 'user-123' },
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      controllers: [ProblemNoteController],
      providers: [
        {
          provide: ProblemNoteService,
          useValue: {
            save: jest.fn(),
            findByProblem: jest.fn(),
          },
        },
        {
          provide: JwtService,
          useValue: {
            sign: jest.fn(),
            verify: jest.fn(),
          },
        },
        {
          provide: Reflector,
          useValue: {
            get: jest.fn(),
            getAll: jest.fn(),
          },
        },
        {
          provide: ModuleRef,
          useValue: {
            get: jest.fn(),
          },
        },
      ],
    })
      .overrideGuard(AuthGuard)
      .useValue({ canActivate: jest.fn(() => true) })
      .compile();

    controller = module.get<ProblemNoteController>(ProblemNoteController);
    noteService = module.get(ProblemNoteService);
  });

  it('should be defined', () => {
    expect(controller).toBeDefined();
  });

  describe('save', () => {
    it('should save a problem note', async () => {
      const saveNoteDto: SaveNoteDto = {
        content: 'This is my note',
      };

      noteService.save.mockResolvedValue(mockNote);

      const result = await controller.save(1, saveNoteDto, mockReq as any);

      expect(result).toEqual(mockNote);
      expect(noteService.save).toHaveBeenCalledWith(
        'user-123',
        1,
        'This is my note',
      );
    });
  });

  describe('findOne', () => {
    it('should return a problem note', async () => {
      noteService.findByProblem.mockResolvedValue(mockNote);

      const result = await controller.findOne(1, mockReq as any);

      expect(result).toEqual(mockNote);
      expect(noteService.findByProblem).toHaveBeenCalledWith('user-123', 1);
    });
  });
});
