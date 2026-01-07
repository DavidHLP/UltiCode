import { Injectable, BadRequestException } from '@nestjs/common';
import { PrismaService } from '../../prisma.service';
import { CreateNotificationDto, NotificationTarget } from '../dto/notification.dto';
import { NotificationCategory } from '@prisma/client';

@Injectable()
export class AdminNotificationService {
  constructor(private prisma: PrismaService) {}

  async create(userId: string, dto: CreateNotificationDto) {
    if (dto.target === NotificationTarget.ALL) {
      return this.prisma.systemAnnouncement.create({
        data: {
          title: dto.title,
          content: dto.content,
          type: dto.type,
          created_by: userId,
        },
      });
    }

    if (dto.target === NotificationTarget.USERS) {
      if (!dto.userIds || dto.userIds.length === 0) {
        throw new BadRequestException('User IDs are required for unicast notifications');
      }

      // Verify users exist
      const users = await this.prisma.user.findMany({
        where: { id: { in: dto.userIds } },
        select: { id: true },
      });

      const validUserIds = users.map((u) => u.id);

      if (validUserIds.length === 0) {
         throw new BadRequestException('No valid users found');
      }

      // Create notifications in batch
      await this.prisma.notification.createMany({
        data: validUserIds.map((targetUserId) => ({
          user_id: targetUserId,
          type: dto.type,
          category: dto.category || NotificationCategory.SYSTEM,
          title: dto.title,
          body: dto.content,
        })),
      });

      return { count: validUserIds.length };
    }
  }

  async findAll() {
    return this.prisma.systemAnnouncement.findMany({
      orderBy: { created_at: 'desc' },
      include: {
        creator: {
          select: {
            id: true,
            username: true,
            avatar: true,
          },
        },
      },
    });
  }

  async delete(id: string) {
    return this.prisma.systemAnnouncement.delete({
      where: { id },
    });
  }
}
