import { Test, TestingModule } from '@nestjs/testing';
import { NotificationService } from './notification.service';
import { PrismaService } from '../prisma.service';

describe('NotificationService', () => {
  let service: NotificationService;
  let prisma: jest.Mocked<PrismaService>;

  const mockNotification = {
    id: 'notif-123',
    user_id: 'user-123',
    type: 'INFO',
    category: 'SYSTEM',
    title: 'Test Notification',
    body: 'This is a test notification',
    link: null,
    metadata: null,
    is_read: false,
    read_at: null,
    created_at: new Date(),
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        NotificationService,
        {
          provide: PrismaService,
          useValue: {
            notification: {
              findMany: jest.fn(),
              findUnique: jest.fn(),
              count: jest.fn(),
              updateMany: jest.fn(),
              deleteMany: jest.fn(),
              create: jest.fn(),
            },
            systemAnnouncement: {
              findMany: jest.fn(),
              findUnique: jest.fn(),
            },
            systemAnnouncementRead: {
              upsert: jest.fn(),
              findMany: jest.fn(),
              updateMany: jest.fn(),
              createMany: jest.fn(),
            },
            notificationPreference: {
              findUnique: jest.fn(),
              create: jest.fn(),
              update: jest.fn(),
            },
          },
        },
      ],
    }).compile();

    service = module.get<NotificationService>(NotificationService);
    prisma = module.get(PrismaService);
  });

  it('should be defined', () => {
    expect(service).toBeDefined();
  });

  describe('list', () => {
    it('should return paginated notifications', async () => {
      prisma.notification.findMany.mockResolvedValue([
        mockNotification,
      ] as never);
      prisma.notification.count.mockResolvedValue(1);
      prisma.systemAnnouncement.findMany.mockResolvedValue([]);

      const result = await service.list('user-123', {});

      expect(result).toHaveProperty('items');
      expect(result).toHaveProperty('total');
      expect(result.items).toHaveLength(1);
    });
  });

  describe('getUnreadCount', () => {
    it('should return unread count', async () => {
      prisma.notification.count.mockResolvedValue(3);
      prisma.systemAnnouncement.findMany.mockResolvedValue([]);

      const result = await service.getUnreadCount('user-123');

      expect(result).toEqual({ count: 3 });
    });
  });

  describe('markAllRead', () => {
    it('should mark all notifications as read', async () => {
      prisma.notification.updateMany.mockResolvedValue({ count: 5 } as never);
      prisma.systemAnnouncement.findMany.mockResolvedValue([]);
      prisma.systemAnnouncementRead.findMany.mockResolvedValue([]);

      const result = await service.markAllRead('user-123');

      expect(result).toEqual({ updated: 5 });
      expect(prisma.notification.updateMany).toHaveBeenCalled();
    });
  });

  describe('createNotification', () => {
    it('should create a new notification', async () => {
      prisma.notificationPreference.findUnique.mockResolvedValue({
        communication: true,
        marketing: false,
        system: true,
        security: true,
      } as never);
      prisma.notification.create.mockResolvedValue(mockNotification as never);

      const result = await service.createNotification({
        userId: 'user-123',
        type: 'INFO',
        category: 'SYSTEM',
        title: 'New Notification',
        body: 'Notification content',
      });

      expect(result).toBeDefined();
      expect(prisma.notification.create).toHaveBeenCalled();
    });
  });

  describe('updateNotification', () => {
    it('should update a notification', async () => {
      prisma.notification.updateMany.mockResolvedValue({ count: 1 } as never);
      prisma.notification.findUnique.mockResolvedValue({
        ...mockNotification,
        is_read: true,
      } as never);

      const result = await service.updateNotification('user-123', 'notif-123', {
        isRead: true,
      });

      expect(result).toBeDefined();
      expect(result.isRead).toBe(true);
    });
  });

  describe('getPreferences', () => {
    it('should return user preferences', async () => {
      const mockPreferences = {
        user_id: 'user-123',
        communication: true,
        marketing: false,
        system: true,
        security: true,
      };

      prisma.notificationPreference.findUnique.mockResolvedValue(
        mockPreferences as never,
      );

      const result = await service.getPreferences('user-123');

      expect(result).toEqual(mockPreferences);
    });
  });

  describe('updatePreferences', () => {
    it('should update user preferences', async () => {
      const mockPreferences = {
        user_id: 'user-123',
        communication: false,
        marketing: false,
        system: true,
        security: true,
      };

      prisma.notificationPreference.findUnique.mockResolvedValue(
        mockPreferences as never,
      );
      prisma.notificationPreference.update.mockResolvedValue(
        mockPreferences as never,
      );

      const result = await service.updatePreferences('user-123', {
        communication: false,
      });

      expect(result).toBeDefined();
      expect(prisma.notificationPreference.update).toHaveBeenCalled();
    });
  });
});
