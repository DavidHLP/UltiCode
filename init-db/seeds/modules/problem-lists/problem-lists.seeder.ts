import type { PrismaClient, BookmarkType } from '@prisma/client';
import type {
  SeederMetadata,
  SeedModuleResult,
  TransactionClient,
} from '../../core/interfaces';
import { BaseSeeder, createSeederExport } from '../base/base.seeder';
import problemListsData from '../../data/problem-lists.data';

/**
 * ProblemLists seeder - creates problem lists with relations and bookmarks.
 *
 * Layer: L3 (depends on Problems, Users)
 */
export class ProblemListsSeeder extends BaseSeeder {
  readonly metadata: SeederMetadata = {
    name: 'ProblemLists',
    version: '1.0.0',
    dependencies: ['Problems', 'Users'],
    priority: 2,
    description: 'Seed problem lists with relations and bookmarks',
  };

  async clear(tx?: TransactionClient): Promise<void> {
    const client = this.getClient(tx) as PrismaClient;
    await client.bookmark.deleteMany({
      where: { target_type: 'PROBLEM_LIST' as BookmarkType },
    });
    await client.problemListProblemRelation.deleteMany();
    await client.problemList.deleteMany();
  }

  async seed(tx?: TransactionClient): Promise<SeedModuleResult> {
    const startTime = Date.now();
    const client = this.getClient(tx) as PrismaClient;
    const details: Record<string, number> = {};

    // 1. Seed problem lists
    const listData = problemListsData.problem_lists.map((list) => ({
      id: list.id,
      name: list.name,
      description: list.description ?? null,
      author_id: list.author_id,
      is_public: list.is_public,
      is_featured: list.is_featured,
      banner_tag: list.banner_tag ?? null,
      banner_icon: list.banner_icon ?? null,
      banner_theme: list.banner_theme ?? null,
      banner_order: list.banner_order ?? 0,
      created_at: new Date(list.created_at),
      updated_at: new Date(list.updated_at),
    }));

    const listResult = await client.problemList.createMany({
      data: listData,
      skipDuplicates: true,
    });
    details.lists = listResult.count;

    // 2. Seed problem list relations with calculated sort_order
    const relationData = problemListsData.problem_list_relations.map((rel, index) => {
      const sortOrder =
        problemListsData.problem_list_relations.findIndex(
          (r) => r.list_id === rel.list_id && r.problem_id === rel.problem_id,
        ) + 1;

      return {
        list_id: rel.list_id,
        problem_id: rel.problem_id,
        sort_order: sortOrder,
      };
    });

    const relationResult = await client.problemListProblemRelation.createMany({
      data: relationData,
      skipDuplicates: true,
    });
    details.relations = relationResult.count;

    // 3. Seed bookmark folders
    const folderData = problemListsData.user_problem_list_categories.map((cat) => ({
      id: cat.id,
      user_id: cat.user_id,
      name: cat.name,
      sort_order: cat.sort_order,
      is_default: false,
    }));

    const folderResult = await client.bookmarkFolder.createMany({
      data: folderData,
      skipDuplicates: true,
    });
    details.folders = folderResult.count;

    // 4. Create default folders for users with favorites
    const usersWithFavorites = [
      ...new Set(problemListsData.user_problem_list_favorites.map((f) => f.user_id)),
    ];
    const defaultFolders: Record<string, string> = {};

    for (const userId of usersWithFavorites) {
      const defaultFolder = await client.bookmarkFolder.create({
        data: {
          user_id: userId,
          name: 'Favorites',
          is_default: true,
        },
      });
      defaultFolders[userId] = defaultFolder.id;
    }
    details.defaultFolders = usersWithFavorites.length;

    // 5. Seed bookmarks for favorites
    const favoriteBookmarks = problemListsData.user_problem_list_favorites
      .filter((fav) => defaultFolders[fav.user_id])
      .map((fav) => ({
        folder_id: defaultFolders[fav.user_id],
        target_type: 'PROBLEM_LIST' as BookmarkType,
        target_id: fav.list_id,
      }));

    if (favoriteBookmarks.length > 0) {
      const favResult = await client.bookmark.createMany({
        data: favoriteBookmarks,
        skipDuplicates: true,
      });
      details.favoriteBookmarks = favResult.count;
    }

    // 6. Seed bookmarks for category items
    const categoryBookmarks = problemListsData.user_problem_list_category_items.map(
      (item) => ({
        folder_id: item.category_id,
        target_type: 'PROBLEM_LIST' as BookmarkType,
        target_id: item.list_id,
      }),
    );

    if (categoryBookmarks.length > 0) {
      const catResult = await client.bookmark.createMany({
        data: categoryBookmarks,
        skipDuplicates: true,
      });
      details.categoryBookmarks = catResult.count;
    }

    const totalCount = Object.values(details).reduce((sum, n) => sum + n, 0);
    return this.createResult(totalCount, startTime, details);
  }
}

export const createProblemListsSeeder = createSeederExport(ProblemListsSeeder);
