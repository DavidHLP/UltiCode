import { Test, TestingModule } from '@nestjs/testing';
import { ProblemListService } from './problem-list.service';
import { PrismaService } from '../prisma.service';
import { SubmissionService } from '../submission/submission.service';
import { BookmarkService } from '../bookmark/bookmark.service';
import { I18nService } from '../i18n/i18n.service';
import { NotFoundException, ForbiddenException } from '@nestjs/common';
import { v4 as uuidv4 } from 'uuid';

jest.mock('uuid');
(uuidv4 as jest.Mock).mockReturnValue('new-list-id');

describe('ProblemListService', () => {
  let service: ProblemListService;
  let prisma: jest.Mocked<PrismaService>;
  let _submissionService: jest.Mocked<SubmissionService>;
  let bookmarkService: jest.Mocked<BookmarkService>;
  let _i18nService: jest.Mocked<I18nService>;

  const mockList = {
    id: 'list-123',
    name: 'My List',
    description: 'Test list',
    author_id: 'user-123',
    is_public: true,
    is_featured: false,
    created_at: new Date(),
    updated_at: new Date(),
  };

  const mockProblem = {
    id: 1,
    slug: 'two-sum',
    title: 'Two Sum',
    difficulty: 'Easy',
    acceptance_rate: 0.45,
    is_premium: false,
    has_solution: true,
  };

  const mockPrismaService = {
    problemList: {
      findMany: jest.fn(),
      findUnique: jest.fn(),
      findFirst: jest.fn(),
      create: jest.fn(),
      update: jest.fn(),
      delete: jest.fn(),
      count: jest.fn(),
    },
    problem: {
      findUnique: jest.fn(),
      findMany: jest.fn(),
    },
    problemListProblemRelation: {
      findMany: jest.fn(),
      findUnique: jest.fn(),
      create: jest.fn(),
      createMany: jest.fn(),
      delete: jest.fn(),
      deleteMany: jest.fn(),
      groupBy: jest.fn().mockResolvedValue([]),
      count: jest.fn(),
    },
    problemTagRelation: {
      findMany: jest.fn(),
    },
    bookmark: {
      deleteMany: jest.fn(),
      findMany: jest.fn(),
      count: jest.fn(),
      groupBy: jest.fn().mockResolvedValue([]),
    },
    $transaction: jest.fn(),
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        ProblemListService,
        {
          provide: PrismaService,
          useValue: mockPrismaService,
        },
        {
          provide: SubmissionService,
          useValue: {
            getProblemStatusMap: jest.fn().mockResolvedValue(new Map()),
          },
        },
        {
          provide: BookmarkService,
          useValue: {
            getUserFolders: jest.fn().mockResolvedValue([]),
            ensureDefaultFolder: jest
              .fn()
              .mockResolvedValue({ id: 'folder-123' }),
            addBookmark: jest.fn().mockResolvedValue({}),
            getBookmarkFolders: jest.fn().mockResolvedValue([]),
            updateFolder: jest.fn().mockResolvedValue({
              id: 'folder-123',
              name: 'Folder',
              sort_order: 0,
            }),
            createFolder: jest.fn().mockResolvedValue({
              id: 'folder-123',
              name: 'Folder',
              sort_order: 0,
            }),
            deleteFolder: jest.fn().mockResolvedValue(undefined),
            removeBookmarkByTarget: jest.fn().mockResolvedValue(undefined),
          },
        },
        {
          provide: I18nService,
          useValue: {
            getBatchTranslations: jest.fn().mockResolvedValue(new Map()),
            applyTranslations: jest.fn().mockReturnValue(mockProblem),
          },
        },
      ],
    }).compile();

    service = module.get<ProblemListService>(ProblemListService);
    prisma = module.get(PrismaService);
    _submissionService = module.get(SubmissionService);
    bookmarkService = module.get(BookmarkService);
    _i18nService = module.get(I18nService);
  });

  it('should be defined', () => {
    expect(service).toBeDefined();
  });

  describe('getUserProblemLists', () => {
    it('should return user problem lists', async () => {
      (prisma.problemList.findMany as jest.Mock).mockResolvedValue([mockList]);
      (prisma.bookmark.findMany as jest.Mock).mockResolvedValue([]);
      bookmarkService.getUserFolders.mockResolvedValue([
        {
          id: 'folder-123',
          name: 'Favorites',
          is_default: true,
          sort_order: 0,
        },
      ] as never);

      const result = await service.getUserProblemLists('user-123');

      expect(result).toBeDefined();
      expect(result.myLists).toBeDefined();
      expect(result.savedLists).toBeDefined();
      expect(result.featured).toBeDefined();
      expect(result.categories).toBeDefined();
    });
  });

  describe('findAll', () => {
    it('should return featured lists for anonymous users', async () => {
      (prisma.problemList.findMany as jest.Mock).mockResolvedValue([mockList]);

      const result = await service.findAll('en-US');

      expect(result.myLists).toEqual([]);
      expect(result.savedLists).toEqual([]);
      expect(result.featured).toBeDefined();
      expect(result.categories).toEqual([]);
    });
  });

  describe('getFeaturedLists', () => {
    it('should return featured lists', async () => {
      (prisma.problemList.findMany as jest.Mock).mockResolvedValue([mockList]);

      const result = await service.getFeaturedLists();

      expect(result).toBeDefined();
      expect(Array.isArray(result)).toBe(true);
    });
  });

  describe('getListOverview', () => {
    it('should return list overview with problems', async () => {
      (prisma.problemList.findUnique as jest.Mock).mockResolvedValue(mockList);
      (
        prisma.problemListProblemRelation.findMany as jest.Mock
      ).mockResolvedValue([
        {
          problem_id: 1,
          sort_order: 0,
          problem: mockProblem,
        },
      ] as never);
      (prisma.problemTagRelation.findMany as jest.Mock).mockResolvedValue([]);

      const result = await service.getListOverview('list-123', 'user-123');

      expect(result).toBeDefined();
      expect(result.list).toBeDefined();
      expect(result.problems).toBeDefined();
      expect(result.stats).toBeDefined();
    });
  });

  describe('createList', () => {
    it('should create a new list', async () => {
      (prisma.problemList.create as jest.Mock).mockResolvedValue(mockList);

      const result = await service.createList('user-123', {
        name: 'My List',
        description: 'Test description',
        isPublic: true,
      });

      expect(result).toBeDefined();
      expect(result.name).toBe('My List');
    });
  });

  describe('updateList', () => {
    it('should update a list', async () => {
      (prisma.problemList.findUnique as jest.Mock).mockResolvedValue(mockList);
      (prisma.problemList.update as jest.Mock).mockResolvedValue({
        ...mockList,
        name: 'Updated List',
      });

      const result = await service.updateList('list-123', 'user-123', {
        name: 'Updated List',
      });

      expect(result).toBeDefined();
      expect(prisma.problemList.update).toHaveBeenCalled();
    });

    it('should throw error if list not found', async () => {
      (prisma.problemList.findUnique as jest.Mock).mockResolvedValue(null);

      await expect(
        service.updateList('list-123', 'user-123', { name: 'Updated' }),
      ).rejects.toThrow(NotFoundException);
    });

    it('should throw error if user is not author', async () => {
      (prisma.problemList.findUnique as jest.Mock).mockResolvedValue({
        ...mockList,
        author_id: 'other-user',
      });

      await expect(
        service.updateList('list-123', 'user-123', { name: 'Updated' }),
      ).rejects.toThrow(ForbiddenException);
    });
  });

  describe('deleteList', () => {
    it('should delete a list', async () => {
      (prisma.problemList.findUnique as jest.Mock).mockResolvedValue(mockList);
      (prisma.problemList.delete as jest.Mock).mockResolvedValue(mockList);

      await service.deleteList('list-123', 'user-123');

      expect(prisma.problemList.delete).toHaveBeenCalled();
    });
  });

  describe('forkList', () => {
    it('should fork a list', async () => {
      (prisma.problemList.findUnique as jest.Mock).mockResolvedValue(mockList);
      (
        prisma.problemListProblemRelation.findMany as jest.Mock
      ).mockResolvedValue([]);
      (prisma.$transaction as jest.Mock).mockImplementation((callback) =>
        callback({
          problemList: {
            create: jest
              .fn()
              .mockResolvedValue({ ...mockList, id: 'new-list-id' }),
          },
          problemListProblemRelation: {
            createMany: jest.fn().mockResolvedValue({ count: 1 }),
          },
        }),
      );

      const result = await service.forkList('list-123', 'user-123');

      expect(result).toBe('new-list-id');
    });
  });

  describe('addProblem', () => {
    it('should add problem to list', async () => {
      (prisma.problemList.findUnique as jest.Mock).mockResolvedValue(mockList);
      (prisma.problem.findUnique as jest.Mock).mockResolvedValue(mockProblem);
      (
        prisma.problemListProblemRelation.findUnique as jest.Mock
      ).mockResolvedValue(null);
      (prisma.problemListProblemRelation.count as jest.Mock).mockResolvedValue(
        0,
      );
      (prisma.problemListProblemRelation.create as jest.Mock).mockResolvedValue(
        {},
      );

      await service.addProblem('list-123', 'user-123', 1);

      expect(prisma.problemListProblemRelation.create).toHaveBeenCalled();
    });
  });

  describe('removeProblem', () => {
    it('should remove problem from list', async () => {
      (prisma.problemList.findUnique as jest.Mock).mockResolvedValue(mockList);
      (prisma.problemListProblemRelation.delete as jest.Mock).mockResolvedValue(
        {},
      );

      await service.removeProblem('list-123', 'user-123', 1);

      expect(prisma.problemListProblemRelation.delete).toHaveBeenCalledWith({
        where: {
          list_id_problem_id: {
            list_id: 'list-123',
            problem_id: BigInt(1),
          },
        },
      });
    });
  });

  describe('saveList', () => {
    it('should save list to bookmarks', async () => {
      (prisma.problemList.findUnique as jest.Mock).mockResolvedValue(mockList);
      bookmarkService.ensureDefaultFolder.mockResolvedValue({
        id: 'folder-123',
      } as never);
      bookmarkService.addBookmark.mockResolvedValue({} as never);

      await service.saveList('user-123', 'list-123');

      expect(bookmarkService.addBookmark).toHaveBeenCalled();
    });
  });

  describe('unsaveList', () => {
    it('should unsave list', async () => {
      (prisma.bookmark.deleteMany as jest.Mock).mockResolvedValue({
        count: 1,
      } as never);

      await service.unsaveList('user-123', 'list-123');

      expect(prisma.bookmark.deleteMany).toHaveBeenCalled();
    });
  });

  describe('createCategory', () => {
    it('should create a category', async () => {
      bookmarkService.createFolder.mockResolvedValue({
        id: 'folder-123',
        name: 'My Category',
        sort_order: 0,
      } as never);

      const result = await service.createCategory('user-123', {
        name: 'My Category',
      });

      expect(result).toBeDefined();
      expect(result.name).toBe('My Category');
    });
  });

  describe('updateCategory', () => {
    it('should update a category', async () => {
      bookmarkService.updateFolder.mockResolvedValue({
        id: 'folder-123',
        name: 'Updated Category',
        sort_order: 0,
      } as never);
      (
        prisma.problemListProblemRelation.findMany as jest.Mock
      ).mockResolvedValue([]);
      (prisma.problemList.findMany as jest.Mock).mockResolvedValue([]);
      (prisma.bookmark.findMany as jest.Mock).mockResolvedValue([]);
      (
        prisma.problemListProblemRelation.groupBy as jest.Mock
      ).mockResolvedValue([]);

      const result = await service.updateCategory('folder-123', 'user-123', {
        name: 'Updated Category',
      });

      expect(result).toBeDefined();
      expect(result.name).toBe('Updated Category');
    });
  });

  describe('deleteCategory', () => {
    it('should delete a category', async () => {
      bookmarkService.deleteFolder.mockResolvedValue(undefined);

      await service.deleteCategory('folder-123', 'user-123');

      expect(bookmarkService.deleteFolder).toHaveBeenCalledWith(
        'user-123',
        'folder-123',
      );
    });
  });
});
