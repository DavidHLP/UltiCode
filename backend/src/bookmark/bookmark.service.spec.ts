import { Test, TestingModule } from '@nestjs/testing';
import { BookmarkService } from './bookmark.service';
import { PrismaService } from '../prisma.service';
import { ForbiddenException } from '@nestjs/common';
import { BookmarkType } from '@prisma/client';
import { BookmarkFolderService } from './services/bookmark-folder.service';
import { BookmarkCrudService } from './services/bookmark-crud.service';
import { BookmarkQueryService } from './services/bookmark-query.service';

describe('BookmarkService', () => {
  let service: BookmarkService;
  let folderService: jest.Mocked<BookmarkFolderService>;
  let crudService: jest.Mocked<BookmarkCrudService>;
  let queryService: jest.Mocked<BookmarkQueryService>;

  const mockFolder = {
    id: 'folder-123',
    user_id: 'user-123',
    name: 'Favorites',
    description: null,
    icon: null,
    color: null,
    isDefault: true,
    itemCount: 2,
    sortOrder: 0,
    createdAt: new Date(),
    updatedAt: new Date(),
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        BookmarkService,
        {
          provide: PrismaService,
          useValue: {},
        },
        {
          provide: BookmarkFolderService,
          useValue: {
            ensureDefaultFolder: jest.fn(),
            getUserFolders: jest.fn(),
            getFolderWithBookmarks: jest.fn(),
            createFolder: jest.fn(),
            updateFolder: jest.fn(),
            deleteFolder: jest.fn(),
            reorderFolders: jest.fn(),
            validateFolderOwnership: jest.fn(),
          },
        },
        {
          provide: BookmarkCrudService,
          useValue: {
            quickFavorite: jest.fn(),
            isInDefaultFolder: jest.fn(),
            addBookmark: jest.fn(),
            removeBookmark: jest.fn(),
            removeBookmarkByTarget: jest.fn(),
            updateBookmark: jest.fn(),
          },
        },
        {
          provide: BookmarkQueryService,
          useValue: {
            getFolderWithHydratedItems: jest.fn(),
            getBookmarkFolders: jest.fn(),
            getBookmarkStatusBatch: jest.fn(),
            getFavoriteCount: jest.fn(),
            getFavoriteCountsBatch: jest.fn(),
          },
        },
      ],
    }).compile();

    service = module.get<BookmarkService>(BookmarkService);
    folderService = module.get(BookmarkFolderService);
    crudService = module.get(BookmarkCrudService);
    queryService = module.get(BookmarkQueryService);
  });

  it('should be defined', () => {
    expect(service).toBeDefined();
  });

  describe('ensureDefaultFolder', () => {
    it('should return existing default folder', async () => {
      folderService.ensureDefaultFolder.mockResolvedValue({
        id: 'folder-123',
        name: 'Favorites',
      });

      const result = await service.ensureDefaultFolder('user-123');

      expect(result).toEqual({ id: 'folder-123', name: 'Favorites' });
    });

    it('should create default folder if not exists', async () => {
      folderService.ensureDefaultFolder.mockResolvedValue({
        id: 'folder-123',
        name: 'Favorites',
      });

      const result = await service.ensureDefaultFolder('user-123');

      expect(result).toEqual({ id: 'folder-123', name: 'Favorites' });
    });
  });

  describe('quickFavorite', () => {
    it('should add item to favorites if not exists', async () => {
      crudService.quickFavorite.mockResolvedValue(true);

      const result = await service.quickFavorite(
        'user-123',
        BookmarkType.FORUM_POST,
        'post-123',
      );

      expect(result).toBe(true);
    });

    it('should remove item from favorites if exists', async () => {
      crudService.quickFavorite.mockResolvedValue(false);

      const result = await service.quickFavorite(
        'user-123',
        BookmarkType.FORUM_POST,
        'post-123',
      );

      expect(result).toBe(false);
    });
  });

  describe('getUserFolders', () => {
    it('should return all user folders', async () => {
      folderService.getUserFolders.mockResolvedValue([mockFolder]);

      const result = await service.getUserFolders('user-123');

      expect(result).toHaveLength(1);
      expect(result[0].name).toBe('Favorites');
    });
  });

  describe('createFolder', () => {
    it('should create a new folder', async () => {
      folderService.createFolder.mockResolvedValue(mockFolder);

      const result = await service.createFolder('user-123', {
        name: 'My Folder',
        description: 'My custom folder',
      });

      expect(result).toBeDefined();
      expect(result.name).toBe('Favorites');
    });
  });

  describe('deleteFolder', () => {
    it('should delete a folder', async () => {
      folderService.deleteFolder.mockResolvedValue(undefined);

      await service.deleteFolder('user-123', 'folder-123');

      expect(folderService.deleteFolder).toHaveBeenCalledWith(
        'user-123',
        'folder-123',
      );
    });

    it('should throw error when deleting default folder', async () => {
      folderService.deleteFolder.mockRejectedValue(
        new ForbiddenException('Cannot delete the default folder'),
      );

      await expect(
        service.deleteFolder('user-123', 'folder-123'),
      ).rejects.toThrow(ForbiddenException);
    });
  });

  describe('getFavoriteCount', () => {
    it('should return favorite count', async () => {
      queryService.getFavoriteCount.mockResolvedValue(5);

      const result = await service.getFavoriteCount(
        BookmarkType.FORUM_POST,
        'post-123',
      );

      expect(result).toBe(5);
    });
  });

  describe('getFavoriteCountsBatch', () => {
    it('should return favorite counts for multiple items', async () => {
      const mockCounts = new Map([
        ['post-1', 5],
        ['post-2', 3],
      ]);
      queryService.getFavoriteCountsBatch.mockResolvedValue(mockCounts);

      const result = await service.getFavoriteCountsBatch(
        BookmarkType.FORUM_POST,
        ['post-1', 'post-2'],
      );

      expect(result.get('post-1')).toBe(5);
      expect(result.get('post-2')).toBe(3);
    });
  });
});
