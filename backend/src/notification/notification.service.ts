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

    // Fetch User Notifications
    // We fetch a bit more to handle merging with announcements
    const userNotifications = await this.prisma.notification.findMany({
      where,
      orderBy: { created_at: 'desc' },
      take: limit * 2, // Fetch double limit to ensure we have enough after merge
      skip: skip, // Basic skip, imperfect when merging
    });

    const totalUserNotifications = await this.prisma.notification.count({
      where,
    });
    let total = totalUserNotifications;
    let items = userNotifications.map((item) => this.mapNotification(item));

    // Fetch System Announcements if eligible
    // Only if filtering by SYSTEM or no category filter
    // And type filter matches available types or is unset
    if (
      (!query.category || query.category === 'SYSTEM') &&
      (!query.type || query.type === 'SYSTEM')
    ) {
      // Fetch recent announcements
      const announcements = await this.prisma.systemAnnouncement.findMany({
        orderBy: { created_at: 'desc' },
        take: 20, // Limit global announcements fetch
        include: {
          reads: {
            where: { user_id: userId },
          },
        },
      });

      const mappedAnnouncements = announcements
        .map((a) => {
          const readRecord = a.reads[0];
          const isRead = !!readRecord?.is_read;
          return {
            id: a.id,
            title: a.title,
            body: a.content,
            type: a.type.toLowerCase(),
            category: 'system',
            link: null,
            metadata: null,
            isRead,
            readAt: readRecord?.read_at || null,
            createdAt: a.created_at,
          };
        })
        .filter((a) => {
          if (query.unreadOnly && a.isRead) return false;
          if (query.type && a.type !== query.type.toLowerCase()) return false;
          return true;
        });

      total += mappedAnnouncements.length;
      items = [...items, ...mappedAnnouncements].sort(
        (a, b) => b.createdAt.getTime() - a.createdAt.getTime(),
      );

      // Apply pagination window to merged list
      // Since we used skip on userNotifications, we are effectively merging page N of user notifications
      // with ALL recent announcements. This is acceptable for now.
    }

    // Since we fetched user notifications with skip, we just need to slice if announcements pushed things out
    // But actually, merging simplified logic:
    // Ideally: Fetch Top N from (User U System).
    // Current: Fetch Top N User (Skipped) + All System -> Merge -> Slice.
    // This might show announcements on every page if we are not careful.
    // Correct simplified approach:
    // Just return the list. Client handles deduplication if needed.
    // Pagination with merged sources is hard.
    // Let's stick to the implementation above but limit the slice

    // items = items.slice(0, limit); // We don't want to slice off user notifications we already skipped to?
    // Actually, if we skipped user notifications, we shouldn't slice the beginning.
    // We strictly fetched the "current page" of user notifications.
    // We added announcements.
    // So we just return them.
    // This implies announcements "float" and don't push pagination down.
    // That's actually better for "sticky" announcements.

    return {
      items,
      total,
      page,
      limit,
      totalPages: Math.ceil(total / limit),
    };
  }

  async getUnreadCount(userId: string) {
    const userUnread = await this.prisma.notification.count({
      where: { user_id: userId, is_read: false },
    });

    const announcements = await this.prisma.systemAnnouncement.findMany({
      select: {
        id: true,
        reads: {
          where: { user_id: userId },
          select: { is_read: true },
        },
      },
      take: 20, // Check recent 20
    });

    const unreadAnnouncements = announcements.filter(
      (a) => !a.reads[0]?.is_read,
    ).length;

    return { count: userUnread + unreadAnnouncements };
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

    if (updated.count > 0) {
      const notification = await this.prisma.notification.findUnique({
        where: { id },
      });
      return this.mapNotification(notification!);
    }

    // Check system announcement
    const announcement = await this.prisma.systemAnnouncement.findUnique({
      where: { id },
    });

    if (announcement) {
      await this.prisma.systemAnnouncementRead.upsert({
        where: {
          user_id_announcement_id: {
            user_id: userId,
            announcement_id: id,
          },
        },
        update: {
          is_read: dto.isRead,
          read_at: dto.isRead ? new Date() : null,
        },
        create: {
          user_id: userId,
          announcement_id: id,
          is_read: dto.isRead,
          read_at: dto.isRead ? new Date() : null,
        },
      });

      return {
        id: announcement.id,
        title: announcement.title,
        body: announcement.content,
        type: announcement.type.toLowerCase(),
        category: 'system',
        link: null,
        metadata: null,
        isRead: dto.isRead,
        readAt: new Date(),
        createdAt: announcement.created_at,
      };
    }

    throw new NotFoundException('Notification not found');
  }

  async markAllRead(userId: string) {
    const updated = await this.prisma.notification.updateMany({
      where: { user_id: userId, is_read: false },
      data: { is_read: true, read_at: new Date() },
    });

    // Mark all active announcements as read
    const activeAnnouncements = await this.prisma.systemAnnouncement.findMany({
      select: { id: true },
    });

    const existingReads = await this.prisma.systemAnnouncementRead.findMany({
      where: { user_id: userId },
      select: { announcement_id: true },
    });

    const readIds = new Set(existingReads.map((r) => r.announcement_id));
    const toInsert = activeAnnouncements
      .filter((a) => !readIds.has(a.id))
      .map((a) => ({
        user_id: userId,
        announcement_id: a.id,
        is_read: true,
        read_at: new Date(),
      }));

    let announcementCount = 0;
    if (toInsert.length > 0) {
      const res = await this.prisma.systemAnnouncementRead.createMany({
        data: toInsert,
      });
      announcementCount = res.count;
    }

    // Also update existing reads if any were unread
    await this.prisma.systemAnnouncementRead.updateMany({
      where: { user_id: userId, is_read: false },
      data: { is_read: true, read_at: new Date() },
    });

    return { updated: updated.count + announcementCount };
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
