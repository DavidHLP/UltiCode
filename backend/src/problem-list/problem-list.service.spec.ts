import { Test, TestingModule } from '@nestjs/testing';
import { ProblemListService } from './problem-list.service';
import { PrismaService } from '../prisma.service';
import { SubmissionService } from '../submission/submission.service';
import { BookmarkService } from '../bookmark/bookmark.service';
import { I18nService } from '../i18n/i18n.service';
import { ProblemListCrudService } from './services/problem-list-crud.service';
import { ProblemListRelationService } from './services/problem-list-relation.service';
import { ProblemListBookmarkService } from './services/problem-list-bookmark.service';
import { ProblemListCategoryService } from './services/problem-list-category.service';
import { ProblemListStatsService } from './services/problem-list-stats.service';
import { v4 as uuidv4 } from 'uuid';

jest.mock('uuid');
(uuidv4 as jest.Mock).mockReturnValue('new-list-id');

describe('ProblemListService', () => {
  let service: ProblemListService;
  let prisma: jest.Mocked<PrismaService>;
  let crudService: jest.Mocked<ProblemListCrudService>;
  let relationService: jest.Mocked<ProblemListRelationService>;
  let bookmarkService: jest.Mocked<ProblemListBookmarkService>;
  let categoryService: jest.Mocked<ProblemListCategoryService>;

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

  const mockListSummary = {
    id: 'list-123',
    name: 'My List',
    description: 'Test list',
    authorId: 'user-123',
    isPublic: true,
    isFeatured: false,
    createdAt: mockList.created_at,
    updatedAt: mockList.updated_at,
    problemCount: 0,
    favoritesCount: 0,
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
    bookmarkFolder: {
      findMany: jest.fn(),
    },
    $transaction: jest.fn(),
  };

  const mockSubmissionService = {
    getProblemStatusMap: jest.fn().mockResolvedValue(new Map()),
  };

  const mockI18nService = {
    getBatchTranslations: jest.fn().mockResolvedValue(new Map()),
    applyTranslations: jest.fn().mockReturnValue(mockProblem),
    translateEntities: jest
      .fn()
      .mockImplementation((_entityType, entities, _locale) =>
        Promise.resolve(entities),
      ),
    translateEntity: jest
      .fn()
      .mockImplementation((_entityType, entity, _locale) =>
        Promise.resolve(entity),
      ),
  };

  const mockBookmarkService = {
    getUserFolders: jest.fn().mockResolvedValue([]),
    ensureDefaultFolder: jest.fn().mockResolvedValue({ id: 'folder-123' }),
    addBookmark: jest.fn().mockResolvedValue({}),
    getBookmarkFolders: jest.fn().mockResolvedValue([]),
    updateFolder: jest.fn().mockResolvedValue({
      id: 'folder-123',
      name: 'Folder',
      sortOrder: 0,
    }),
    createFolder: jest.fn().mockResolvedValue({
      id: 'folder-123',
      name: 'Folder',
      sortOrder: 0,
    }),
    deleteFolder: jest.fn().mockResolvedValue(undefined),
    removeBookmarkByTarget: jest.fn().mockResolvedValue(undefined),
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
          useValue: mockSubmissionService,
        },
        {
          provide: BookmarkService,
          useValue: mockBookmarkService,
        },
        {
          provide: I18nService,
          useValue: mockI18nService,
        },
        {
          provide: ProblemListStatsService,
          useValue: {
            buildProblemCountMap: jest.fn().mockResolvedValue(new Map()),
            buildFavoritesCountMap: jest.fn().mockResolvedValue(new Map()),
            mapList: jest.fn().mockReturnValue(mockListSummary),
            buildStatsFromProblems: jest.fn().mockReturnValue({
              listId: 'list-123',
              totalCount: 0,
              solvedCount: 0,
              attemptedCount: 0,
              todoCount: 0,
              progress: 0,
            }),
            getStats: jest.fn().mockResolvedValue([]),
            enrichListWithCounts: jest.fn().mockResolvedValue(mockListSummary),
          },
        },
        {
          provide: ProblemListCrudService,
          useValue: {
            createList: jest.fn().mockResolvedValue(mockListSummary),
            updateList: jest.fn().mockResolvedValue(mockListSummary),
            deleteList: jest.fn().mockResolvedValue(undefined),
            forkList: jest.fn().mockResolvedValue('new-list-id'),
            getListById: jest.fn().mockResolvedValue(mockListSummary),
            getListsByUserId: jest.fn().mockResolvedValue([mockListSummary]),
            getFeaturedLists: jest.fn().mockResolvedValue([mockListSummary]),
            getDefaultList: jest.fn().mockResolvedValue(mockListSummary),
          },
        },
        {
          provide: ProblemListRelationService,
          useValue: {
            addProblem: jest.fn().mockResolvedValue(undefined),
            removeProblem: jest.fn().mockResolvedValue(undefined),
            batchAddProblemToLists: jest.fn().mockResolvedValue(undefined),
            batchRemoveProblemFromLists: jest.fn().mockResolvedValue(undefined),
            getUserListsForProblem: jest.fn().mockResolvedValue([]),
            getProblemListIds: jest.fn().mockResolvedValue([]),
            getProblemsByListId: jest.fn().mockResolvedValue([]),
          },
        },
        {
          provide: ProblemListBookmarkService,
          useValue: {
            saveList: jest.fn().mockResolvedValue(undefined),
            unsaveList: jest.fn().mockResolvedValue(undefined),
            isListSaved: jest.fn().mockResolvedValue(true),
          },
        },
        {
          provide: ProblemListCategoryService,
          useValue: {
            getCategories: jest.fn().mockResolvedValue([]),
            createCategory: jest.fn().mockResolvedValue({
              id: 'folder-123',
              name: 'My Category',
              sortOrder: 0,
              lists: [],
            }),
            updateCategory: jest.fn().mockResolvedValue({
              id: 'folder-123',
              name: 'Updated Category',
              sortOrder: 0,
              lists: [],
            }),
            deleteCategory: jest.fn().mockResolvedValue(undefined),
            moveListToCategory: jest.fn().mockResolvedValue(undefined),
          },
        },
      ],
    }).compile();

    service = module.get<ProblemListService>(ProblemListService);
    prisma = module.get(PrismaService);
    crudService = module.get(ProblemListCrudService);
    relationService = module.get(ProblemListRelationService);
    bookmarkService = module.get(ProblemListBookmarkService);
    categoryService = module.get(ProblemListCategoryService);
  });

  it('should be defined', () => {
    expect(service).toBeDefined();
  });

  describe('getUserProblemLists', () => {
    it('should return user problem lists', async () => {
      (prisma.problemList.findMany as jest.Mock).mockResolvedValue([mockList]);
      (prisma.bookmark.findMany as jest.Mock).mockResolvedValue([]);
      categoryService.getCategories.mockResolvedValue([]);

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
      crudService.getFeaturedLists.mockResolvedValue([mockListSummary]);

      const result = await service.findAll('en-US');

      expect(result.myLists).toEqual([]);
      expect(result.savedLists).toEqual([]);
      expect(result.featured).toBeDefined();
      expect(result.categories).toEqual([]);
    });
  });

  describe('getFeaturedLists', () => {
    it('should return featured lists', async () => {
      crudService.getFeaturedLists.mockResolvedValue([mockListSummary]);

      const result = await service.getFeaturedLists();

      expect(result).toBeDefined();
      expect(Array.isArray(result)).toBe(true);
    });
  });

  describe('getListOverview', () => {
    it('should return list overview with problems', async () => {
      crudService.getListById.mockResolvedValue(mockListSummary);
      relationService.getProblemsByListId.mockResolvedValue([
        mockProblem,
      ] as never);
      (prisma.bookmark.findMany as jest.Mock).mockResolvedValue([]);
      (prisma.bookmarkFolder.findMany as jest.Mock).mockResolvedValue([]);

      const result = await service.getListOverview('list-123', 'user-123');

      expect(result).toBeDefined();
      expect(result.list).toBeDefined();
      expect(result.problems).toBeDefined();
      expect(result.stats).toBeDefined();
    });

    it('should return list overview without user', async () => {
      crudService.getListById.mockResolvedValue(mockListSummary);
      relationService.getProblemsByListId.mockResolvedValue([
        mockProblem,
      ] as never);

      const result = await service.getListOverview('list-123');

      expect(result).toBeDefined();
      expect(result.list).toBeDefined();
      expect(result.problems).toBeDefined();
      expect(result.stats).toBeDefined();
      expect(result.viewer).toBeUndefined();
    });
  });

  describe('createList', () => {
    it('should create a new list', async () => {
      crudService.createList.mockResolvedValue(mockListSummary);

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
      crudService.updateList.mockResolvedValue(mockListSummary);

      const result = await service.updateList('list-123', 'user-123', {
        name: 'Updated List',
      });

      expect(result).toBeDefined();
      expect(crudService.updateList).toHaveBeenCalled();
    });
  });

  describe('deleteList', () => {
    it('should delete a list', async () => {
      crudService.deleteList.mockResolvedValue(undefined);

      await service.deleteList('list-123', 'user-123');

      expect(crudService.deleteList).toHaveBeenCalled();
    });
  });

  describe('forkList', () => {
    it('should fork a list', async () => {
      crudService.forkList.mockResolvedValue('new-list-id');

      const result = await service.forkList('list-123', 'user-123');

      expect(result).toBe('new-list-id');
    });
  });

  describe('addProblem', () => {
    it('should add problem to list', async () => {
      relationService.addProblem.mockResolvedValue(undefined);

      await service.addProblem('list-123', 'user-123', 1);

      expect(relationService.addProblem).toHaveBeenCalledWith(
        'list-123',
        'user-123',
        1,
      );
    });
  });

  describe('removeProblem', () => {
    it('should remove problem from list', async () => {
      relationService.removeProblem.mockResolvedValue(undefined);

      await service.removeProblem('list-123', 'user-123', 1);

      expect(relationService.removeProblem).toHaveBeenCalledWith(
        'list-123',
        'user-123',
        1,
      );
    });
  });

  describe('saveList', () => {
    it('should save list to bookmarks', async () => {
      bookmarkService.saveList.mockResolvedValue(undefined);

      await service.saveList('user-123', 'list-123');

      expect(bookmarkService.saveList).toHaveBeenCalled();
    });
  });

  describe('unsaveList', () => {
    it('should unsave list', async () => {
      bookmarkService.unsaveList.mockResolvedValue(undefined);

      await service.unsaveList('user-123', 'list-123');

      expect(bookmarkService.unsaveList).toHaveBeenCalled();
    });
  });

  describe('createCategory', () => {
    it('should create a category', async () => {
      categoryService.createCategory.mockResolvedValue({
        id: 'folder-123',
        name: 'My Category',
        sortOrder: 0,
        lists: [],
      });

      const result = await service.createCategory('user-123', {
        name: 'My Category',
      });

      expect(result).toBeDefined();
      expect(result.name).toBe('My Category');
    });
  });

  describe('updateCategory', () => {
    it('should update a category', async () => {
      categoryService.updateCategory.mockResolvedValue({
        id: 'folder-123',
        name: 'Updated Category',
        sortOrder: 0,
        lists: [],
      });

      const result = await service.updateCategory('folder-123', 'user-123', {
        name: 'Updated Category',
      });

      expect(result).toBeDefined();
      expect(result.name).toBe('Updated Category');
    });
  });

  describe('deleteCategory', () => {
    it('should delete a category', async () => {
      categoryService.deleteCategory.mockResolvedValue(undefined);

      await service.deleteCategory('folder-123', 'user-123');

      expect(categoryService.deleteCategory).toHaveBeenCalledWith(
        'folder-123',
        'user-123',
      );
    });
  });
});
