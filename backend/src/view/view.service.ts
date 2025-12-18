import { Injectable } from '@nestjs/common';
import { PrismaService } from '../prisma.service';
import { ViewTargetType } from '@prisma/client';

@Injectable()
export class ViewService {
  constructor(private readonly prisma: PrismaService) {}

  private readonly COOLDOWN_MINUTES = 60;

  async recordView(
    targetType: ViewTargetType,
    targetId: string,
    userId?: string,
    ip?: string,
  ): Promise<{ counted: boolean }> {
    if (!userId && !ip) return { counted: false };

    const cutoff = new Date(Date.now() - this.COOLDOWN_MINUTES * 60 * 1000);

    // Filter condition for existing view
    const existingView = await this.prisma.view.findFirst({
      where: {
        target_id: targetId,
        target_type: targetType,
        OR: [
          userId ? { user_id: userId } : undefined,
          ip ? { ip: ip } : undefined,
        ].filter((condition): condition is NonNullable<typeof condition> =>
          Boolean(condition),
        ),
        viewed_at: {
          gt: cutoff,
        },
      },
    });

    if (existingView) {
      return { counted: false };
    }

    // Use transaction to ensure consistency
    await this.prisma.$transaction(async (tx) => {
      // 1. Create View Record
      await tx.view.create({
        data: {
          target_id: targetId,
          target_type: targetType,
          user_id: userId,
          ip: ip,
        },
      });

      // 2. Increment Counter
      if (targetType === ViewTargetType.SOLUTION) {
        await tx.solution.update({
          where: { id: targetId },
          data: { views: { increment: 1 } },
        });
      } else if (targetType === ViewTargetType.FORUM_POST) {
        await tx.forumPost.update({
          where: { id: targetId },
          data: { views: { increment: 1 } },
        });
      }
    });

    return { counted: true };
  }
}
