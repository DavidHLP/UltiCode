import { PrismaClient, ViewTargetType } from '@prisma/client';
import viewData from './data/view.data';

export async function clearViews(prisma: PrismaClient): Promise<void> {
  await prisma.view.deleteMany();
}

export async function seedViews(
  prisma: PrismaClient,
): Promise<{ viewsCount: number }> {
  // If no data, return 0
  if (viewData.views.length === 0) {
    return { viewsCount: 0 };
  }

  const views = await prisma.view.createMany({
    data: viewData.views.map((view) => ({
      target_id: view.target_id,
      target_type: view.target_type as ViewTargetType,
      user_id: view.user_id,
      ip: view.ip,
    })),
  });

  return {
    viewsCount: views.count,
  };
}
