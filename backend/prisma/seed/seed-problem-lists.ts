import type { PrismaClient } from '@prisma/client';
import problemListsData from './data/problem-lists.data';

export async function clearProblemLists(prisma: PrismaClient): Promise<void> {
  await prisma.problemList.deleteMany();
  await prisma.problemListGroup.deleteMany();
}

export interface SeedProblemListsResult {
  count: number;
}

export async function seedProblemLists(
  prisma: PrismaClient,
): Promise<SeedProblemListsResult> {
  // Seed groups
  for (const group of problemListsData.problem_list_groups) {
    await prisma.problemListGroup.create({
      data: {
        id: group.id,
        name: group.name,
        sort_order: group.sort_order,
      },
    });
  }

  // Seed lists
  for (const list of problemListsData.problem_lists) {
    await prisma.problemList.create({
      data: {
        id: list.id,
        group_id: list.group_id,
        name: list.name,
        description: list.description ?? null,
        author_id: list.author_id,
        is_public: list.is_public,
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

  return {
    count: problemListsData.problem_lists.length,
  };
}
