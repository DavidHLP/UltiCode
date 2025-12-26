import {
  CollectionTargetType,
  type PrismaClient,
} from '@prisma/client';
import problemListsData from './data/problem-lists.data';

export async function clearProblemLists(prisma: PrismaClient): Promise<void> {
  await prisma.collectionItem.deleteMany({
    where: { target_type: CollectionTargetType.PROBLEM_LIST },
  });
  await prisma.problemListProblemRelation.deleteMany();
  await prisma.problemList.deleteMany();
}

export interface SeedProblemListsResult {
  count: number;
}

export async function seedProblemLists(
  prisma: PrismaClient,
): Promise<SeedProblemListsResult> {
  // Seed lists
  for (const list of problemListsData.problem_lists) {
    await prisma.problemList.create({
      data: {
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
      },
    });
  }

  // Seed problem list relations
  for (const relation of problemListsData.problem_list_relations) {
    const sort_order =
      problemListsData.problem_list_relations.findIndex(
        (r) =>
          r.list_id === relation.list_id && r.problem_id === relation.problem_id,
      ) + 1;

    await prisma.problemListProblemRelation.create({
      data: {
        list_id: relation.list_id,
        problem_id: relation.problem_id,
        sort_order: sort_order,
      },
    });
  }

  // Seed user collections (replacing old categories)
  for (const category of problemListsData.user_problem_list_categories) {
    await prisma.collection.create({
      data: {
        id: category.id,
        user_id: category.user_id,
        name: category.name,
        sort_order: category.sort_order,
        is_default: false,
      },
    });
  }

  // Seed collection items (replacing old category items and favorites)
  // First, ensure default collections exist for users with favorites
  const usersWithFavorites = new Set(
    problemListsData.user_problem_list_favorites.map((f) => f.user_id),
  );
  const defaultCollections: Record<string, string> = {};

  for (const userId of usersWithFavorites) {
    const defaultCollection = await prisma.collection.create({
      data: {
        user_id: userId,
        name: 'Favorites',
        is_default: true,
      },
    });
    defaultCollections[userId] = defaultCollection.id;
  }

  // Add favorites to default collections
  for (const favorite of problemListsData.user_problem_list_favorites) {
    const collectionId = defaultCollections[favorite.user_id];
    if (collectionId) {
      await prisma.collectionItem.create({
        data: {
          collection_id: collectionId,
          target_type: CollectionTargetType.PROBLEM_LIST,
          target_id: favorite.list_id,
        },
      });
    }
  }

  // Add category items to collections
  for (const item of problemListsData.user_problem_list_category_items) {
    await prisma.collectionItem.create({
      data: {
        collection_id: item.category_id,
        target_type: CollectionTargetType.PROBLEM_LIST,
        target_id: item.list_id,
      },
    });
  }

  return {
    count: problemListsData.problem_lists.length,
  };
}
