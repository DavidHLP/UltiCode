import type { PrismaClient } from '@prisma/client';
import problemListsData from './data/problem-lists.data';

export async function clearProblemLists(prisma: PrismaClient): Promise<void> {
  await prisma.userProblemListSave.deleteMany();
  await prisma.userProblemListCategory.deleteMany();
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

  // Seed user saves
  for (const save of problemListsData.user_problem_list_saves) {
    await prisma.userProblemListSave.create({
      data: {
        user_id: save.user_id,
        list_id: save.list_id,
        category_id: null,
      },
    });
  }

  return {
    count: problemListsData.problem_lists.length,
  };
}
