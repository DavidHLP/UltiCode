import { Test, TestingModule } from '@nestjs/testing';
import { NotificationController } from './notification.controller';
import { NotificationService } from './notification.service';
import { JwtService } from '@nestjs/jwt';
import { Reflector, ModuleRef } from '@nestjs/core';
import { AuthGuard } from '../auth/auth.guard';

describe('NotificationController', () => {
  let controller: NotificationController;
  let notificationService: jest.Mocked<NotificationService>;

  const mockReq = {
    user: { id: 'user-123' },
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      controllers: [NotificationController],
      providers: [
        {
          provide: JwtService,
          useValue: {
            sign: jest.fn(),
            verify: jest.fn(),
          },
        },
        {
          provide: Reflector,
          useValue: {
            get: jest.fn(),
            getAll: jest.fn(),
          },
        },
        {
          provide: ModuleRef,
          useValue: {
            get: jest.fn(),
          },
        },
        {
          provide: NotificationService,
          useValue: {
            list: jest.fn(),
            getUnreadCount: jest.fn(),
            getPreferences: jest.fn(),
            updatePreferences: jest.fn(),
            markAllRead: jest.fn(),
            clearAll: jest.fn(),
            updateNotification: jest.fn(),
            deleteNotification: jest.fn(),
          },
        },
      ],
    })
      .overrideGuard(AuthGuard)
      .useValue({ canActivate: jest.fn(() => true) })
      .compile();

    controller = module.get<NotificationController>(NotificationController);
    notificationService = module.get(NotificationService);
  });

  it('should be defined', () => {
    expect(controller).toBeDefined();
  });

  describe('list', () => {
    it('should return paginated notifications', async () => {
      const mockResponse = {
        items: [
          {
            id: 'notif-123',
            title: 'Test Notification',
            body: 'Test content',
            isRead: false,
          },
        ],
        total: 1,
        page: 1,
        limit: 20,
        totalPages: 1,
      };

      notificationService.list.mockResolvedValue(mockResponse as never);

      const result = await controller.list(mockReq as any, {});

      expect(result).toEqual(mockResponse);
      expect(notificationService.list).toHaveBeenCalledWith('user-123', {});
    });
  });

  describe('getUnreadCount', () => {
    it('should return unread count', async () => {
      notificationService.getUnreadCount.mockResolvedValue({ count: 5 });

      const result = await controller.getUnreadCount(mockReq as any);

      expect(result).toEqual({ count: 5 });
      expect(notificationService.getUnreadCount).toHaveBeenCalledWith(
        'user-123',
      );
    });
  });

  describe('getPreferences', () => {
    it('should return user preferences', async () => {
      const mockPreferences = {
        user_id: 'user-123',
        communication: true,
        marketing: false,
        system: true,
      };

      notificationService.getPreferences.mockResolvedValue(
        mockPreferences as never,
      );

      const result = await controller.getPreferences(mockReq as any);

      expect(result).toEqual(mockPreferences);
    });
  });

  describe('updatePreferences', () => {
    it('should update user preferences', async () => {
      const mockPreferences = {
        communication: false,
        marketing: false,
        system: true,
      };

      notificationService.updatePreferences.mockResolvedValue(
        mockPreferences as never,
      );

      const result = await controller.updatePreferences(mockReq as any, {
        communication: false,
      });

      expect(result).toEqual(mockPreferences);
    });
  });

  describe('markAllRead', () => {
    it('should mark all notifications as read', async () => {
      notificationService.markAllRead.mockResolvedValue({ updated: 10 });

      const result = await controller.markAllRead(mockReq as any);

      expect(result).toEqual({ updated: 10 });
    });
  });

  describe('clearAll', () => {
    it('should clear all notifications', async () => {
      notificationService.clearAll.mockResolvedValue({ deleted: 5 });

      const result = await controller.clearAll(mockReq as any);

      expect(result).toEqual({ deleted: 5 });
    });
  });
});
