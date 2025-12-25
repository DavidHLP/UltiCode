import {
  EdgeOperationTargetType,
  EdgeOperationType,
  type PrismaClient,
} from '@prisma/client';
import problemListsData from './data/problem-lists.data';

export async function clearProblemLists(prisma: PrismaClient): Promise<void> {
  await prisma.userProblemListCategoryItem.deleteMany();
  await prisma.userProblemListCategory.deleteMany();
  await prisma.problemListProblemRelation.deleteMany();
  await prisma.problemList.deleteMany();
  await prisma.edgeOperation.deleteMany({
    where: { target_type: EdgeOperationTargetType.PROBLEM_LIST },
  });
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

  // Seed user categories
  for (const category of problemListsData.user_problem_list_categories) {
    await prisma.userProblemListCategory.create({
      data: {
        id: category.id,
        user_id: category.user_id,
        name: category.name,
        sort_order: category.sort_order,
      },
    });
  }

  // Seed user favorites (edge operations)
  for (const favorite of problemListsData.user_problem_list_favorites) {
    await prisma.edgeOperation.create({
      data: {
        operator_id: favorite.user_id,
        target_type: EdgeOperationTargetType.PROBLEM_LIST,
        target_id: favorite.list_id,
        operation_type: EdgeOperationType.FAVORITE,
      },
    });
  }

  // Seed category items
  for (const item of problemListsData.user_problem_list_category_items) {
    await prisma.userProblemListCategoryItem.create({
      data: {
        user_id: item.user_id,
        list_id: item.list_id,
        category_id: item.category_id,
      },
    });
  }

  return {
    count: problemListsData.problem_lists.length,
  };
}
