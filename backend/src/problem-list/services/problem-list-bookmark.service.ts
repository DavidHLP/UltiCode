import {
  Injectable,
  NotFoundException,
  ForbiddenException,
} from '@nestjs/common';
import { BookmarkType } from '@prisma/client';
import { PrismaService } from '../../prisma.service';
import { BookmarkService } from '../../bookmark/bookmark.service';

const problemListTargetType = BookmarkType.PROBLEM_LIST;

@Injectable()
export class ProblemListBookmarkService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly bookmarkService: BookmarkService,
  ) {}

  /**
   * Save a problem list to user's bookmarks
   */
  async saveList(
    userId: string,
    listId: string,
    collectionId?: string,
  ): Promise<void> {
    const list = await this.prisma.problemList.findUnique({
      where: { id: listId },
    });
    if (!list) {
      throw new NotFoundException('List not found');
    }
    if (!list.is_public && list.author_id !== userId) {
      throw new ForbiddenException('This list is private');
    }

    if (collectionId) {
      await this.bookmarkService.addBookmark(userId, collectionId, {
        targetId: listId,
        targetType: problemListTargetType,
      });
    } else {
      const defaultFolder =
        await this.bookmarkService.ensureDefaultFolder(userId);
      await this.bookmarkService.addBookmark(userId, defaultFolder.id, {
        targetId: listId,
        targetType: problemListTargetType,
      });
    }
  }

  /**
   * Unsave a problem list from user's bookmarks
   */
  async unsaveList(userId: string, listId: string): Promise<void> {
    await this.prisma.bookmark.deleteMany({
      where: {
        target_type: problemListTargetType,
        target_id: listId,
        folder: { user_id: userId },
      },
    });
  }

  /**
   * Check if a list is saved by user
   */
  async isListSaved(userId: string, listId: string): Promise<boolean> {
    const count = await this.prisma.bookmark.count({
      where: {
        target_type: problemListTargetType,
        target_id: listId,
        folder: { user_id: userId },
      },
    });
    return count > 0;
  }
}
