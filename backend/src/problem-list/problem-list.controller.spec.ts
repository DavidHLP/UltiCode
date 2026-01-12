import { Test, TestingModule } from '@nestjs/testing';
import { ProblemListController } from './problem-list.controller';
import { ProblemListService } from './problem-list.service';

describe('ProblemListController', () => {
  let controller: ProblemListController;
  let problemListService: jest.Mocked<ProblemListService>;

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      controllers: [ProblemListController],
      providers: [
        {
          provide: ProblemListService,
          useValue: {
            findAll: jest.fn(),
            getUserProblemLists: jest.fn(),
            getListOverview: jest.fn(),
            createList: jest.fn(),
            updateList: jest.fn(),
            deleteList: jest.fn(),
            forkList: jest.fn(),
            addProblem: jest.fn(),
            removeProblem: jest.fn(),
            batchAddProblemToLists: jest.fn(),
            batchRemoveProblemFromLists: jest.fn(),
            getUserListsForProblem: jest.fn(),
            saveList: jest.fn(),
            unsaveList: jest.fn(),
            moveListToCategory: jest.fn(),
            createCategory: jest.fn(),
            updateCategory: jest.fn(),
            deleteCategory: jest.fn(),
          },
        },
      ],
    }).compile();

    controller = module.get<ProblemListController>(ProblemListController);
    problemListService = module.get(ProblemListService);
  });

  it('should be defined', () => {
    expect(controller).toBeDefined();
  });

  describe('getOverview', () => {
    it('should return user lists when userId provided', async () => {
      const mockResponse = {
        myLists: [],
        savedLists: [],
        featured: [],
        categories: [],
      };

      problemListService.getUserProblemLists.mockResolvedValue(
        mockResponse as never,
      );

      const result = await controller.getOverview('user-123', undefined);

      expect(result).toEqual(mockResponse);
    });

    it('should return all lists when no userId provided', async () => {
      const mockResponse = {
        myLists: [],
        savedLists: [],
        featured: [],
        categories: [],
      };

      problemListService.findAll.mockResolvedValue(mockResponse as never);

      const result = await controller.getOverview(undefined, undefined);

      expect(result).toEqual(mockResponse);
    });
  });

  describe('getListOverview', () => {
    it('should return list overview', async () => {
      const mockResponse = {
        list: { id: 'list-123', name: 'My List' },
        problems: [],
        stats: null,
      };

      problemListService.getListOverview.mockResolvedValue(
        mockResponse as never,
      );

      const result = await controller.getListOverview(
        'list-123',
        'user-123',
        undefined,
      );

      expect(result).toEqual(mockResponse);
    });
  });

  describe('createList', () => {
    it('should create a new list', async () => {
      const mockList = { id: 'list-123', name: 'My List' };

      problemListService.createList.mockResolvedValue(mockList as never);

      const result = await controller.createList('user-123', {
        name: 'My List',
        description: 'Test',
      });

      expect(result).toEqual(mockList);
    });
  });

  describe('updateList', () => {
    it('should update a list', async () => {
      const mockList = { id: 'list-123', name: 'Updated List' };

      problemListService.updateList.mockResolvedValue(mockList as never);

      const result = await controller.updateList('list-123', 'user-123', {
        name: 'Updated List',
      });

      expect(result).toEqual(mockList);
    });
  });

  describe('deleteList', () => {
    it('should delete a list', async () => {
      problemListService.deleteList.mockResolvedValue(undefined);

      await controller.deleteList('list-123', 'user-123');

      expect(problemListService.deleteList).toHaveBeenCalledWith(
        'list-123',
        'user-123',
      );
    });
  });

  describe('forkList', () => {
    it('should fork a list', async () => {
      problemListService.forkList.mockResolvedValue('new-list-id');

      const result = await controller.forkList('list-123', 'user-123');

      expect(result).toEqual({ id: 'new-list-id' });
    });
  });

  describe('addProblem', () => {
    it('should add problem to list', async () => {
      problemListService.addProblem.mockResolvedValue(undefined);

      await controller.addProblem('list-123', 'user-123', { problemId: 1 });

      expect(problemListService.addProblem).toHaveBeenCalledWith(
        'list-123',
        'user-123',
        1,
      );
    });
  });

  describe('removeProblem', () => {
    it('should remove problem from list', async () => {
      problemListService.removeProblem.mockResolvedValue(undefined);

      await controller.removeProblem('list-123', 1, 'user-123');

      expect(problemListService.removeProblem).toHaveBeenCalledWith(
        'list-123',
        'user-123',
        1,
      );
    });
  });

  describe('batchAddProblemToLists', () => {
    it('should batch add problem to lists', async () => {
      problemListService.batchAddProblemToLists.mockResolvedValue(undefined);

      await controller.batchAddProblemToLists(1, 'user-123', {
        listIds: ['list-1', 'list-2'],
      });

      expect(problemListService.batchAddProblemToLists).toHaveBeenCalledWith(
        'user-123',
        1,
        ['list-1', 'list-2'],
      );
    });
  });

  describe('batchRemoveProblemFromLists', () => {
    it('should batch remove problem from lists', async () => {
      problemListService.batchRemoveProblemFromLists.mockResolvedValue(
        undefined,
      );

      await controller.batchRemoveProblemFromLists(1, 'user-123', {
        listIds: ['list-1', 'list-2'],
      });

      expect(
        problemListService.batchRemoveProblemFromLists,
      ).toHaveBeenCalledWith('user-123', 1, ['list-1', 'list-2']);
    });
  });

  describe('getUserListsForProblem', () => {
    it('should return user lists for problem', async () => {
      const mockLists = [];

      problemListService.getUserListsForProblem.mockResolvedValue(
        mockLists as never,
      );

      const result = await controller.getUserListsForProblem(1, 'user-123');

      expect(result).toEqual(mockLists);
    });
  });

  describe('saveList', () => {
    it('should save list', async () => {
      problemListService.saveList.mockResolvedValue(undefined);

      await controller.saveList('list-123', 'user-123', {
        categoryId: 'folder-123',
      });

      expect(problemListService.saveList).toHaveBeenCalledWith(
        'user-123',
        'list-123',
        'folder-123',
      );
    });
  });

  describe('unsaveList', () => {
    it('should unsave list', async () => {
      problemListService.unsaveList.mockResolvedValue(undefined);

      await controller.unsaveList('list-123', 'user-123');

      expect(problemListService.unsaveList).toHaveBeenCalledWith(
        'user-123',
        'list-123',
      );
    });
  });

  describe('moveListToCategory', () => {
    it('should move list to category', async () => {
      problemListService.moveListToCategory.mockResolvedValue(undefined);

      await controller.moveListToCategory('list-123', 'user-123', {
        categoryId: 'folder-123',
      });

      expect(problemListService.moveListToCategory).toHaveBeenCalledWith(
        'user-123',
        'list-123',
        'folder-123',
      );
    });
  });

  describe('createCategory', () => {
    it('should create a category', async () => {
      const mockCategory = {
        id: 'folder-123',
        name: 'My Category',
        sortOrder: 0,
      };

      problemListService.createCategory.mockResolvedValue(
        mockCategory as never,
      );

      const result = await controller.createCategory('user-123', {
        name: 'My Category',
      });

      expect(result).toEqual(mockCategory);
    });
  });

  describe('updateCategory', () => {
    it('should update a category', async () => {
      const mockCategory = {
        id: 'folder-123',
        name: 'Updated Category',
        sortOrder: 0,
      };

      problemListService.updateCategory.mockResolvedValue(
        mockCategory as never,
      );

      const result = await controller.updateCategory('folder-123', 'user-123', {
        name: 'Updated Category',
      });

      expect(result).toEqual(mockCategory);
    });
  });

  describe('deleteCategory', () => {
    it('should delete a category', async () => {
      problemListService.deleteCategory.mockResolvedValue(undefined);

      await controller.deleteCategory('folder-123', 'user-123');

      expect(problemListService.deleteCategory).toHaveBeenCalledWith(
        'folder-123',
        'user-123',
      );
    });
  });
});
