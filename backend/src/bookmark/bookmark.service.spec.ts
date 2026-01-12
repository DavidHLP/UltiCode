import { Test, TestingModule } from '@nestjs/testing';
import { BookmarkService } from './bookmark.service';
import { PrismaService } from '../prisma.service';
import { ForbiddenException } from '@nestjs/common';
import { BookmarkType } from '@prisma/client';

describe('BookmarkService', () => {
  let service: BookmarkService;
  let prisma: jest.Mocked<PrismaService>;

  const mockFolder = {
    id: 'folder-123',
    user_id: 'user-123',
    name: 'Favorites',
    description: null,
    icon: null,
    color: null,
    is_default: true,
    sort_order: 0,
    created_at: new Date(),
    updated_at: new Date(),
    _count: { bookmarks: 2 },
  };

  const mockBookmark = {
    id: 'bookmark-123',
    folder_id: 'folder-123',
    target_type: BookmarkType.FORUM_POST,
    target_id: 'post-123',
    note: null,
    sort_order: 0,
    created_at: new Date(),
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        BookmarkService,
        {
          provide: PrismaService,
          useValue: {
            bookmarkFolder: {
              findFirst: jest.fn(),
              findMany: jest.fn(),
              create: jest.fn(),
              update: jest.fn(),
              delete: jest.fn(),
              aggregate: jest.fn(),
            },
            bookmark: {
              findUnique: jest.fn(),
              findFirst: jest.fn(),
              findMany: jest.fn(),
              create: jest.fn(),
              upsert: jest.fn(),
              update: jest.fn(),
              delete: jest.fn(),
              deleteMany: jest.fn(),
              groupBy: jest.fn(),
              count: jest.fn(),
            },
            forumPost: {
              findMany: jest.fn(),
            },
            problem: {
              findMany: jest.fn(),
            },
            problemList: {
              findMany: jest.fn(),
            },
            solution: {
              findMany: jest.fn(),
            },
            $transaction: jest.fn((callback) => callback({})),
          },
        },
      ],
    }).compile();

    service = module.get<BookmarkService>(BookmarkService);
    prisma = module.get(PrismaService);
  });

  it('should be defined', () => {
    expect(service).toBeDefined();
  });

  describe('ensureDefaultFolder', () => {
    it('should return existing default folder', async () => {
      (prisma.bookmarkFolder.findFirst as jest.Mock).mockResolvedValue({
        id: 'folder-123',
        name: 'Favorites',
      } as never);

      const result = await service.ensureDefaultFolder('user-123');

      expect(result).toEqual({ id: 'folder-123', name: 'Favorites' });
    });

    it('should create default folder if not exists', async () => {
      (prisma.bookmarkFolder.findFirst as jest.Mock).mockResolvedValue(null);
      (prisma.bookmarkFolder.create as jest.Mock).mockResolvedValue({
        id: 'folder-123',
        name: 'Favorites',
      } as never);

      const result = await service.ensureDefaultFolder('user-123');

      expect(result).toEqual({ id: 'folder-123', name: 'Favorites' });
      expect(prisma.bookmarkFolder.create).toHaveBeenCalled();
    });
  });

  describe('quickFavorite', () => {
    it('should add item to favorites if not exists', async () => {
      const mockTx = {
        bookmarkFolder: {
          findFirst: jest.fn().mockResolvedValue(mockFolder),
        },
        bookmark: {
          findUnique: jest.fn().mockResolvedValue(null),
          create: jest.fn().mockResolvedValue(mockBookmark),
        },
      };

      (prisma.$transaction as jest.Mock).mockImplementation((callback) =>
        callback(mockTx as never),
      );

      const result = await service.quickFavorite(
        'user-123',
        BookmarkType.FORUM_POST,
        'post-123',
      );

      expect(result).toBe(true);
    });

    it('should remove item from favorites if exists', async () => {
      const mockTx = {
        bookmarkFolder: {
          findFirst: jest.fn().mockResolvedValue(mockFolder),
        },
        bookmark: {
          findUnique: jest.fn().mockResolvedValue(mockBookmark),
          delete: jest.fn().mockResolvedValue({}),
        },
      };

      (prisma.$transaction as jest.Mock).mockImplementation((callback) =>
        callback(mockTx as never),
      );

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
      (prisma.bookmarkFolder.findMany as jest.Mock).mockResolvedValue([
        mockFolder,
      ] as never);

      const result = await service.getUserFolders('user-123');

      expect(result).toHaveLength(1);
      expect(result[0].name).toBe('Favorites');
    });
  });

  describe('createFolder', () => {
    it('should create a new folder', async () => {
      (prisma.bookmarkFolder.aggregate as jest.Mock).mockResolvedValue({
        _max: { sort_order: 0 },
      } as never);
      (prisma.bookmarkFolder.create as jest.Mock).mockResolvedValue(
        mockFolder as never,
      );

      const result = await service.createFolder('user-123', {
        name: 'My Folder',
        description: 'My custom folder',
      });

      expect(result).toBeDefined();
      expect(result.name).toBe('Favorites');
      expect(prisma.bookmarkFolder.create).toHaveBeenCalled();
    });
  });

  describe('deleteFolder', () => {
    it('should delete a folder', async () => {
      (prisma.bookmarkFolder.findFirst as jest.Mock).mockResolvedValue({
        ...mockFolder,
        is_default: false,
      } as never);
      (prisma.bookmarkFolder.delete as jest.Mock).mockResolvedValue(
        {} as never,
      );

      await service.deleteFolder('user-123', 'folder-123');

      expect(prisma.bookmarkFolder.delete).toHaveBeenCalledWith({
        where: { id: 'folder-123' },
      });
    });

    it('should throw error when deleting default folder', async () => {
      (prisma.bookmarkFolder.findFirst as jest.Mock).mockResolvedValue({
        ...mockFolder,
        is_default: true,
      } as never);

      await expect(
        service.deleteFolder('user-123', 'folder-123'),
      ).rejects.toThrow(ForbiddenException);
    });
  });

  describe('getFavoriteCount', () => {
    it('should return favorite count', async () => {
      (prisma.bookmark.count as jest.Mock).mockResolvedValue(5);

      const result = await service.getFavoriteCount(
        BookmarkType.FORUM_POST,
        'post-123',
      );

      expect(result).toBe(5);
    });
  });

  describe('getFavoriteCountsBatch', () => {
    it('should return favorite counts for multiple items', async () => {
      (prisma.bookmark.groupBy as jest.Mock).mockResolvedValue([
        { target_id: 'post-1', _count: { target_id: 5 } },
        { target_id: 'post-2', _count: { target_id: 3 } },
      ] as never);

      const result = await service.getFavoriteCountsBatch(
        BookmarkType.FORUM_POST,
        ['post-1', 'post-2'],
      );

      expect(result.get('post-1')).toBe(5);
      expect(result.get('post-2')).toBe(3);
    });
  });
});
