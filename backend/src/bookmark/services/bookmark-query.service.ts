import { Injectable } from '@nestjs/common';
import { PrismaService } from '../../prisma.service';
import { BookmarkType } from '@prisma/client';
import {
  BookmarkFolderService,
  BookmarkFolderWithItems,
  BookmarkDetail,
} from './bookmark-folder.service';

interface RawForumPost {
  id: string;
  title: string;
  community: {
    name: string;
    slug: string;
  };
  author: {
    username: string;
    avatar: string | null;
  };
}

interface RawProblem {
  id: bigint;
  title: string;
  slug: string;
  difficulty: string;
}

interface RawProblemList {
  id: string;
  name: string;
  description: string | null;
}

interface RawSolution {
  id: string;
  title: string;
  problem: {
    title: string;
    slug: string;
  };
  author: {
    username: string;
  };
}

@Injectable()
export class BookmarkQueryService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly folderService: BookmarkFolderService,
  ) {}

  async hydrateBookmarkItems(
    folderId: string,
    userId: string,
  ): Promise<BookmarkDetail[]> {
    const folder = await this.prisma.bookmarkFolder.findFirst({
      where: { id: folderId, user_id: userId },
      include: {
        bookmarks: {
          orderBy: [{ sort_order: 'asc' }, { created_at: 'desc' }],
        },
      },
    });

    if (!folder) {
      return [];
    }

    const forumPostIds = folder.bookmarks
      .filter((b) => b.target_type === BookmarkType.FORUM_POST)
      .map((b) => b.target_id);
    const problemIds = folder.bookmarks
      .filter((b) => b.target_type === BookmarkType.PROBLEM)
      .map((b) => b.target_id);
    const problemListIds = folder.bookmarks
      .filter((b) => b.target_type === BookmarkType.PROBLEM_LIST)
      .map((b) => b.target_id);
    const solutionIds = folder.bookmarks
      .filter((b) => b.target_type === BookmarkType.SOLUTION)
      .map((b) => b.target_id);

    let forumPostsMap = new Map<string, RawForumPost>();
    if (forumPostIds.length > 0) {
      const posts = await this.prisma.forumPost.findMany({
        where: { id: { in: forumPostIds } },
        select: {
          id: true,
          title: true,
          community: { select: { name: true, slug: true } },
          author: { select: { username: true, avatar: true } },
        },
      });
      forumPostsMap = new Map(posts.map((p) => [p.id, p]));
    }

    let problemsMap = new Map<string, RawProblem>();
    if (problemIds.length > 0) {
      const problemIdsBigInt = problemIds.map((id) => BigInt(id));
      const problems = await this.prisma.problem.findMany({
        where: { id: { in: problemIdsBigInt } },
        select: { id: true, title: true, slug: true, difficulty: true },
      });
      problemsMap = new Map(problems.map((p) => [p.id.toString(), p]));
    }

    let problemListsMap = new Map<string, RawProblemList>();
    if (problemListIds.length > 0) {
      const lists = await this.prisma.problemList.findMany({
        where: { id: { in: problemListIds } },
        select: { id: true, name: true, description: true },
      });
      problemListsMap = new Map(lists.map((l) => [l.id, l]));
    }

    let solutionsMap = new Map<string, RawSolution>();
    if (solutionIds.length > 0) {
      const solutions = await this.prisma.solution.findMany({
        where: { id: { in: solutionIds } },
        select: {
          id: true,
          title: true,
          problem: { select: { title: true, slug: true } },
          author: { select: { username: true } },
        },
      });
      solutionsMap = new Map(solutions.map((s) => [s.id, s]));
    }

    return folder.bookmarks.map((item) => {
      let title: string | undefined = undefined;
      let metadata: Record<string, unknown> | undefined = undefined;

      if (item.target_type === BookmarkType.FORUM_POST) {
        const post = forumPostsMap.get(item.target_id);
        if (post) {
          title = post.title;
          metadata = {
            communityName: post.community.name,
            communitySlug: post.community.slug,
            authorName: post.author.username,
            authorAvatar: post.author.avatar,
          };
        }
      } else if (item.target_type === BookmarkType.PROBLEM) {
        const problem = problemsMap.get(item.target_id);
        if (problem) {
          title = problem.title;
          metadata = {
            slug: problem.slug,
            difficulty: problem.difficulty,
          };
        }
      } else if (item.target_type === BookmarkType.PROBLEM_LIST) {
        const list = problemListsMap.get(item.target_id);
        if (list) {
          title = list.name;
          metadata = {
            description: list.description,
          };
        }
      } else if (item.target_type === BookmarkType.SOLUTION) {
        const solution = solutionsMap.get(item.target_id);
        if (solution) {
          title = solution.title;
          metadata = {
            problemTitle: solution.problem.title,
            problemSlug: solution.problem.slug,
            authorName: solution.author.username,
          };
        }
      }

      return {
        id: item.id,
        targetId: item.target_id,
        targetType: item.target_type,
        sortOrder: item.sort_order,
        note: item.note,
        createdAt: item.created_at,
        title,
        metadata,
      };
    });
  }

  async getBookmarkFolders(
    userId: string,
    targetType: BookmarkType,
    targetId: string,
  ): Promise<string[]> {
    const items = await this.prisma.bookmark.findMany({
      where: {
        target_type: targetType,
        target_id: targetId,
        folder: { user_id: userId },
      },
      select: { folder_id: true },
    });

    return items.map((i) => i.folder_id);
  }

  async getBookmarkStatusBatch(
    userId: string,
    targetType: BookmarkType,
    targetIds: string[],
  ): Promise<Map<string, boolean>> {
    const defaultFolder = await this.prisma.bookmarkFolder.findFirst({
      where: { user_id: userId, is_default: true },
      select: { id: true },
    });

    if (!defaultFolder) return new Map();

    const items = await this.prisma.bookmark.findMany({
      where: {
        folder_id: defaultFolder.id,
        target_type: targetType,
        target_id: { in: targetIds },
      },
      select: { target_id: true },
    });

    const result = new Map<string, boolean>();
    items.forEach((item) => result.set(item.target_id, true));
    return result;
  }

  async getFavoriteCount(
    targetType: BookmarkType,
    targetId: string,
  ): Promise<number> {
    return this.prisma.bookmark.count({
      where: {
        target_type: targetType,
        target_id: targetId,
        folder: { is_default: true },
      },
    });
  }

  async getFavoriteCountsBatch(
    targetType: BookmarkType,
    targetIds: string[],
  ): Promise<Map<string, number>> {
    const results = await this.prisma.bookmark.groupBy({
      by: ['target_id'],
      where: {
        target_type: targetType,
        target_id: { in: targetIds },
        folder: { is_default: true },
      },
      _count: {
        target_id: true,
      },
    });

    const counts = new Map<string, number>();
    results.forEach((r) => {
      counts.set(r.target_id, r._count.target_id);
    });
    return counts;
  }

  async getFolderWithHydratedItems(
    userId: string,
    folderId: string,
  ): Promise<BookmarkFolderWithItems> {
    const folder = await this.folderService.getFolderWithBookmarks(
      userId,
      folderId,
    );

    const hydratedItems = await this.hydrateBookmarkItems(folderId, userId);

    return {
      ...folder,
      items: hydratedItems,
    };
  }
}
