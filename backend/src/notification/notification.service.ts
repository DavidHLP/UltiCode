import { Injectable, NotFoundException } from '@nestjs/common';
import {
  NotificationCategory,
  NotificationType,
  Notification,
  NotificationPreference,
  Prisma,
} from '@prisma/client';
import { PrismaService } from '../prisma.service';
import {
  NotificationQueryDto,
  UpdateNotificationDto,
  UpdateNotificationPreferencesDto,
} from './dto';

export interface CreateNotificationPayload {
  userId: string;
  type: NotificationType;
  category: NotificationCategory;
  title: string;
  body: string;
  link?: string;
  metadata?: Prisma.JsonValue | null;
}

@Injectable()
export class NotificationService {
  constructor(private readonly prisma: PrismaService) {}

  private mapNotification(notification: Notification) {
    return {
      id: notification.id,
      title: notification.title,
      body: notification.body,
      type: notification.type.toLowerCase(),
      category: notification.category.toLowerCase(),
      link: notification.link,
      metadata: notification.metadata,
      isRead: notification.is_read,
      readAt: notification.read_at,
      createdAt: notification.created_at,
    };
  }

  private isCategoryEnabled(
    preference: NotificationPreference,
    category: NotificationCategory,
  ) {
    if (category === 'COMMUNICATION') return preference.communication;
    if (category === 'MARKETING') return preference.marketing;
    if (category === 'SECURITY') return true;
    if (category === 'SYSTEM') return preference.system;
    return true;
  }

  private async ensurePreferences(userId: string) {
    const existing = await this.prisma.notificationPreference.findUnique({
      where: { user_id: userId },
    });

    if (existing) return existing;

    return this.prisma.notificationPreference.create({
      data: {
        user_id: userId,
      },
    });
  }

  async list(userId: string, query: NotificationQueryDto) {
    const page = Number(query.page || 1);
    const limit = Number(query.limit || 20);
    const skip = (page - 1) * limit;

    const where = {
      user_id: userId,
      ...(query.unreadOnly ? { is_read: false } : {}),
      ...(query.category ? { category: query.category } : {}),
      ...(query.type ? { type: query.type } : {}),
    };

    const [items, total] = await Promise.all([
      this.prisma.notification.findMany({
        where,
        orderBy: { created_at: 'desc' },
        skip,
        take: limit,
      }),
      this.prisma.notification.count({ where }),
    ]);

    return {
      items: items.map((item) => this.mapNotification(item)),
      total,
      page,
      limit,
      totalPages: Math.ceil(total / limit),
    };
  }

  async getUnreadCount(userId: string) {
    const count = await this.prisma.notification.count({
      where: { user_id: userId, is_read: false },
    });
    return { count };
  }

  async updateNotification(
    userId: string,
    id: string,
    dto: UpdateNotificationDto,
  ) {
    const updated = await this.prisma.notification.updateMany({
      where: { id, user_id: userId },
      data: {
        is_read: dto.isRead,
        read_at: dto.isRead ? new Date() : null,
      },
    });

    if (updated.count === 0) {
      throw new NotFoundException('Notification not found');
    }

    const notification = await this.prisma.notification.findUnique({
      where: { id },
    });

    if (!notification) {
      throw new NotFoundException('Notification not found');
    }

    return this.mapNotification(notification);
  }

  async markAllRead(userId: string) {
    const updated = await this.prisma.notification.updateMany({
      where: { user_id: userId, is_read: false },
      data: { is_read: true, read_at: new Date() },
    });

    return { updated: updated.count };
  }

  async deleteNotification(userId: string, id: string) {
    const deleted = await this.prisma.notification.deleteMany({
      where: { id, user_id: userId },
    });

    if (deleted.count === 0) {
      throw new NotFoundException('Notification not found');
    }

    return { success: true };
  }

  async clearAll(userId: string) {
    const deleted = await this.prisma.notification.deleteMany({
      where: { user_id: userId },
    });

    return { deleted: deleted.count };
  }

  async getPreferences(userId: string) {
    return this.ensurePreferences(userId);
  }

  async updatePreferences(
    userId: string,
    dto: UpdateNotificationPreferencesDto,
  ) {
    await this.ensurePreferences(userId);

    const data: UpdateNotificationPreferencesDto = {
      ...(dto.communication !== undefined
        ? { communication: dto.communication }
        : {}),
      ...(dto.marketing !== undefined ? { marketing: dto.marketing } : {}),
      ...(dto.security !== undefined ? { security: true } : {}),
      ...(dto.system !== undefined ? { system: true } : {}),
    };

    return this.prisma.notificationPreference.update({
      where: { user_id: userId },
      data,
    });
  }

  async createNotification(payload: CreateNotificationPayload) {
    const preference = await this.ensurePreferences(payload.userId);

    if (!this.isCategoryEnabled(preference, payload.category)) {
      return null;
    }

    const notification = await this.prisma.notification.create({
      data: {
        user_id: payload.userId,
        type: payload.type,
        category: payload.category,
        title: payload.title,
        body: payload.body,
        link: payload.link,
        metadata: payload.metadata ?? undefined,
      },
    });

    return this.mapNotification(notification);
  }
}
