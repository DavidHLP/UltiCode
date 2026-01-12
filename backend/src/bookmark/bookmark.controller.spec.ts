import { Test, TestingModule } from '@nestjs/testing';
import { BookmarkController } from './bookmark.controller';
import { BookmarkService } from './bookmark.service';
import { BookmarkType } from '@prisma/client';

describe('BookmarkController', () => {
  let controller: BookmarkController;
  let bookmarkService: jest.Mocked<BookmarkService>;

  const mockReq = {
    user: { id: 'user-123' },
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      controllers: [BookmarkController],
      providers: [
        {
          provide: BookmarkService,
          useValue: {
            quickFavorite: jest.fn(),
            getUserFolders: jest.fn(),
            getFolderWithBookmarks: jest.fn(),
            createFolder: jest.fn(),
            updateFolder: jest.fn(),
            deleteFolder: jest.fn(),
            addBookmark: jest.fn(),
            removeBookmark: jest.fn(),
            removeBookmarkByTarget: jest.fn(),
            updateBookmark: jest.fn(),
            getBookmarkFolders: jest.fn(),
            reorderFolders: jest.fn(),
          },
        },
      ],
    }).compile();

    controller = module.get<BookmarkController>(BookmarkController);
    bookmarkService = module.get(BookmarkService);
  });

  it('should be defined', () => {
    expect(controller).toBeDefined();
  });

  describe('quickFavorite', () => {
    it('should toggle favorite status', async () => {
      bookmarkService.quickFavorite.mockResolvedValue(true);

      const result = await controller.quickFavorite(mockReq as any, {
        targetType: BookmarkType.FORUM_POST,
        targetId: 'post-123',
      });

      expect(result).toEqual({ isSaved: true });
      expect(bookmarkService.quickFavorite).toHaveBeenCalledWith(
        'user-123',
        BookmarkType.FORUM_POST,
        'post-123',
      );
    });
  });

  describe('getUserFolders', () => {
    it('should return user folders', async () => {
      const mockFolders = [
        {
          id: 'folder-123',
          name: 'Favorites',
          isDefault: true,
          itemCount: 5,
        },
      ];

      bookmarkService.getUserFolders.mockResolvedValue(mockFolders as never);

      const result = await controller.getUserFolders(mockReq as any);

      expect(result).toEqual(mockFolders);
      expect(bookmarkService.getUserFolders).toHaveBeenCalledWith('user-123');
    });
  });

  describe('getFolder', () => {
    it('should return folder with bookmarks', async () => {
      const mockFolder = {
        id: 'folder-123',
        name: 'Favorites',
        items: [],
      };

      bookmarkService.getFolderWithBookmarks.mockResolvedValue(
        mockFolder as never,
      );

      const result = await controller.getFolder(mockReq as any, 'folder-123');

      expect(result).toEqual(mockFolder);
    });
  });

  describe('createFolder', () => {
    it('should create a new folder', async () => {
      const mockFolder = {
        id: 'folder-456',
        name: 'My Folder',
        itemCount: 0,
      };

      bookmarkService.createFolder.mockResolvedValue(mockFolder as never);

      const result = await controller.createFolder(mockReq as any, {
        name: 'My Folder',
      });

      expect(result).toEqual(mockFolder);
    });
  });

  describe('deleteFolder', () => {
    it('should delete a folder', async () => {
      bookmarkService.deleteFolder.mockResolvedValue(undefined);

      const result = await controller.deleteFolder(
        mockReq as any,
        'folder-123',
      );

      expect(result).toEqual({ success: true });
    });
  });

  describe('addBookmark', () => {
    it('should add a bookmark to folder', async () => {
      const mockBookmark = {
        id: 'bookmark-123',
        targetId: 'post-123',
        targetType: BookmarkType.FORUM_POST,
      };

      bookmarkService.addBookmark.mockResolvedValue(mockBookmark as never);

      const result = await controller.addBookmark(
        mockReq as any,
        'folder-123',
        {
          targetType: BookmarkType.FORUM_POST,
          targetId: 'post-123',
        },
      );

      expect(result).toEqual(mockBookmark);
    });
  });
});
