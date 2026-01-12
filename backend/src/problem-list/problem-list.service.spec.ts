import { Test, TestingModule } from '@nestjs/testing';
import { ProblemListService } from './problem-list.service';
import { Repository, DataSource } from 'typeorm';
import { ProblemList } from './problem-list.entity';
import { Problem } from '../problem/problem.entity';
import { ProblemListProblemRelation } from './problem-list-problem-relation.entity';
import { SubmissionService } from '../submission/submission.service';
import { PrismaService } from '../prisma.service';
import { BookmarkService } from '../bookmark/bookmark.service';
import { I18nService } from '../i18n/i18n.service';
import { NotFoundException, ForbiddenException } from '@nestjs/common';

describe('ProblemListService', () => {
  let service: ProblemListService;
  let listsRepository: jest.Mocked<Repository<ProblemList>>;
  let problemsRepository: jest.Mocked<Repository<Problem>>;
  let relationsRepository: jest.Mocked<Repository<ProblemListProblemRelation>>;
  let _submissionService: jest.Mocked<SubmissionService>;
  let _dataSource: jest.Mocked<DataSource>;
  let prisma: jest.Mocked<PrismaService>;
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
    status: 'todo',
    is_premium: false,
    has_solution: true,
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        ProblemListService,
        {
          provide: 'ProblemListRepository',
          useValue: {
            find: jest.fn().mockResolvedValue([]),
            findOne: jest.fn().mockResolvedValue(null),
            create: jest.fn().mockReturnValue(mockList),
            save: jest.fn().mockResolvedValue(mockList),
            remove: jest.fn().mockResolvedValue(mockList),
            update: jest.fn().mockResolvedValue(mockList),
            count: jest.fn().mockResolvedValue(0),
          },
        },
        {
          provide: 'ProblemRepository',
          useValue: {
            findBy: jest.fn().mockResolvedValue([]),
            findOne: jest.fn().mockResolvedValue(mockProblem),
          },
        },
        {
          provide: 'ProblemListProblemRelationRepository',
          useValue: {
            find: jest.fn().mockResolvedValue([]),
            findOne: jest.fn().mockResolvedValue(null),
            create: jest.fn().mockReturnValue({}),
            save: jest.fn().mockResolvedValue({}),
            delete: jest.fn().mockResolvedValue({}),
            count: jest.fn().mockResolvedValue(0),
            createQueryBuilder: jest.fn().mockReturnValue({
              select: jest.fn().mockReturnThis(),
              addSelect: jest.fn().mockReturnThis(),
              groupBy: jest.fn().mockReturnThis(),
              getRawMany: jest.fn().mockResolvedValue([]),
            }),
          },
        },
        {
          provide: SubmissionService,
          useValue: {
            getProblemStatusMap: jest.fn().mockResolvedValue(new Map()),
          },
        },
        {
          provide: DataSource,
          useValue: {
            transaction: jest.fn((callback) => callback({})),
          },
        },
        {
          provide: PrismaService,
          useValue: {
            bookmark: {
              findMany: jest.fn().mockResolvedValue([]),
              groupBy: jest.fn().mockResolvedValue([]),
              deleteMany: jest.fn().mockResolvedValue({ count: 0 }),
              count: jest.fn().mockResolvedValue(0),
            },
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
              sortOrder: 0,
            }),
            createFolder: jest.fn().mockResolvedValue({
              id: 'folder-123',
              name: 'Folder',
              sortOrder: 0,
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
    listsRepository = module.get('ProblemListRepository');
    problemsRepository = module.get('ProblemRepository');
    relationsRepository = module.get('ProblemListProblemRelationRepository');
    _submissionService = module.get(SubmissionService);
    _dataSource = module.get(DataSource);
    prisma = module.get(PrismaService);
    bookmarkService = module.get(BookmarkService);
    _i18nService = module.get(I18nService);
  });

  it('should be defined', () => {
    expect(service).toBeDefined();
  });

  describe('getUserProblemLists', () => {
    it('should return user problem lists', async () => {
      listsRepository.find.mockResolvedValue([mockList] as never);
      bookmarkService.getUserFolders.mockResolvedValue([
        { id: 'folder-123', name: 'Favorites', isDefault: true, sortOrder: 0 },
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
      listsRepository.find.mockResolvedValue([mockList] as never);

      const result = await service.findAll('en-US');

      expect(result.myLists).toEqual([]);
      expect(result.savedLists).toEqual([]);
      expect(result.featured).toBeDefined();
      expect(result.categories).toEqual([]);
    });
  });

  describe('getFeaturedLists', () => {
    it('should return featured lists', async () => {
      listsRepository.find.mockResolvedValue([mockList] as never);

      const result = await service.getFeaturedLists();

      expect(result).toBeDefined();
      expect(Array.isArray(result)).toBe(true);
    });
  });

  describe('getListOverview', () => {
    it('should return list overview with problems', async () => {
      listsRepository.findOne.mockResolvedValue(mockList as never);
      relationsRepository.find.mockResolvedValue([
        {
          problem_id: 1,
          sort_order: 0,
          problem: mockProblem,
          tagRelations: [],
        },
      ] as never);

      const result = await service.getListOverview('list-123', 'user-123');

      expect(result).toBeDefined();
      expect(result.list).toBeDefined();
      expect(result.problems).toBeDefined();
      expect(result.stats).toBeDefined();
    });
  });

  describe('createList', () => {
    it('should create a new list', async () => {
      listsRepository.create.mockReturnValue(mockList as never);
      listsRepository.save.mockResolvedValue(mockList as never);

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
      listsRepository.findOne.mockResolvedValue(mockList as never);
      listsRepository.save.mockResolvedValue({
        ...mockList,
        name: 'Updated List',
      } as never);

      const result = await service.updateList('list-123', 'user-123', {
        name: 'Updated List',
      });

      expect(result).toBeDefined();
      expect(listsRepository.save).toHaveBeenCalled();
    });

    it('should throw error if list not found', async () => {
      listsRepository.findOne.mockResolvedValue(null);

      await expect(
        service.updateList('list-123', 'user-123', { name: 'Updated' }),
      ).rejects.toThrow(NotFoundException);
    });

    it('should throw error if user is not author', async () => {
      listsRepository.findOne.mockResolvedValue({
        ...mockList,
        author_id: 'other-user',
      } as never);

      await expect(
        service.updateList('list-123', 'user-123', { name: 'Updated' }),
      ).rejects.toThrow(ForbiddenException);
    });
  });

  describe('deleteList', () => {
    it('should delete a list', async () => {
      listsRepository.findOne.mockResolvedValue(mockList as never);
      listsRepository.remove.mockResolvedValue(mockList as never);

      await service.deleteList('list-123', 'user-123');

      expect(listsRepository.remove).toHaveBeenCalled();
    });
  });

  describe('forkList', () => {
    it('should fork a list', async () => {
      listsRepository.findOne.mockResolvedValue(mockList as never);
      relationsRepository.find.mockResolvedValue([] as never);
      listsRepository.create.mockReturnValue({
        ...mockList,
        id: 'new-list-id',
      } as never);
      listsRepository.save.mockResolvedValue({
        ...mockList,
        id: 'new-list-id',
      } as never);

      const result = await service.forkList('list-123', 'user-123');

      expect(result).toBe('new-list-id');
    });
  });

  describe('addProblem', () => {
    it('should add problem to list', async () => {
      listsRepository.findOne.mockResolvedValue(mockList as never);
      problemsRepository.findOne.mockResolvedValue(mockProblem as never);
      relationsRepository.findOne.mockResolvedValue(null);
      relationsRepository.count.mockResolvedValue(0);
      relationsRepository.create.mockReturnValue({} as never);
      relationsRepository.save.mockResolvedValue({} as never);

      await service.addProblem('list-123', 'user-123', 1);

      expect(relationsRepository.save).toHaveBeenCalled();
    });
  });

  describe('removeProblem', () => {
    it('should remove problem from list', async () => {
      listsRepository.findOne.mockResolvedValue(mockList as never);
      relationsRepository.delete.mockResolvedValue({} as never);

      await service.removeProblem('list-123', 'user-123', 1);

      expect(relationsRepository.delete).toHaveBeenCalledWith({
        list_id: 'list-123',
        problem_id: 1,
      });
    });
  });

  describe('saveList', () => {
    it('should save list to bookmarks', async () => {
      listsRepository.findOne.mockResolvedValue(mockList as never);
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
        sortOrder: 0,
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
        sortOrder: 0,
      } as never);
      (prisma.bookmark.findMany as jest.Mock).mockResolvedValue([] as never);
      listsRepository.find.mockResolvedValue([] as never);

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
