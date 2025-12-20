import { Injectable } from '@nestjs/common';
import { PrismaService } from '../prisma.service';
import { FavoriteTargetType } from '@prisma/client';

@Injectable()
export class FavoriteService {
  constructor(private prisma: PrismaService) {}

  async toggle(
    userId: string,
    targetType: FavoriteTargetType,
    targetId: string,
  ) {
    const existing = await this.prisma.favorite.findUnique({
      where: {
        user_id_target_type_target_id: {
          user_id: userId,
          target_type: targetType,
          target_id: targetId,
        },
      },
    });

    if (existing) {
      await this.prisma.favorite.delete({ where: { id: existing.id } });
      return { isFavorited: false };
    } else {
      await this.prisma.favorite.create({
        data: {
          user_id: userId,
          target_type: targetType,
          target_id: targetId,
        },
      });
      return { isFavorited: true };
    }
  }

  async isFavorited(
    userId: string,
    targetType: FavoriteTargetType,
    targetId: string,
  ) {
    const favorite = await this.prisma.favorite.findUnique({
      where: {
        user_id_target_type_target_id: {
          user_id: userId,
          target_type: targetType,
          target_id: targetId,
        },
      },
    });
    return !!favorite;
  }
}
