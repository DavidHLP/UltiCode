import { Test, TestingModule } from '@nestjs/testing';
import { UserController } from './user.controller';
import { UserService } from './user.service';
import { User } from './user.entity';
import { BadRequestException } from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import { Reflector, ModuleRef } from '@nestjs/core';
import { AuthGuard } from '../auth/auth.guard';

describe('UserController', () => {
  let controller: UserController;
  let service: jest.Mocked<UserService>;

  const mockUser = {
    id: 'user-123',
    username: 'testuser',
    email: 'test@example.com',
    name: 'Test User',
    role: 'USER',
  } as User;

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      controllers: [UserController],
      providers: [
        {
          provide: UserService,
          useValue: {
            findAll: jest.fn(),
            getProfileWithRank: jest.fn(),
            update: jest.fn(),
            getUserStats: jest.fn(),
          },
        },
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
      ],
    })
      .overrideGuard(AuthGuard)
      .useValue({ canActivate: jest.fn(() => true) })
      .compile();

    controller = module.get<UserController>(UserController);
    service = module.get(UserService);
  });

  it('should be defined', () => {
    expect(controller).toBeDefined();
  });

  describe('findAll', () => {
    it('should return array of users', async () => {
      service.findAll.mockResolvedValue([mockUser]);

      const result = await controller.findAll();

      expect(result).toEqual([mockUser]);
      expect(service.findAll).toHaveBeenCalled();
    });
  });

  describe('findOne', () => {
    it('should return user with rank', async () => {
      const userWithRank = { ...mockUser, rank: 42 };
      service.getProfileWithRank.mockResolvedValue(userWithRank);

      const result = await controller.findOne('user-123');

      expect(result).toEqual(userWithRank);
      expect(service.getProfileWithRank).toHaveBeenCalledWith('user-123');
    });

    it('should return null when user not found', async () => {
      service.getProfileWithRank.mockResolvedValue(null);

      const result = await controller.findOne('non-existent');

      expect(result).toBeNull();
    });
  });

  describe('updateProfile', () => {
    it('should update user profile when authenticated user matches', async () => {
      const updateData = { name: 'Updated Name' };
      const updatedUser = { ...mockUser, name: 'Updated Name' };
      const req = { user: { id: 'user-123' } };

      service.update.mockResolvedValue(updatedUser);

      const result = await controller.updateProfile(
        'user-123',
        updateData,
        req as any,
      );

      expect(result).toEqual(updatedUser);
      expect(service.update).toHaveBeenCalledWith('user-123', updateData);
    });

    it('should throw BadRequestException when user tries to update another user', async () => {
      const updateData = { name: 'Updated Name' };
      const req = { user: { id: 'different-user-id' } };

      await expect(
        controller.updateProfile('user-123', updateData, req as any),
      ).rejects.toThrow(BadRequestException);

      await expect(
        controller.updateProfile('user-123', updateData, req as any),
      ).rejects.toThrow('You can only update your own profile');
    });
  });

  describe('getUserStats', () => {
    it('should return user statistics', async () => {
      const mockStats = {
        stats: {
          Easy: { count: 5, total: 10 },
          Medium: { count: 3, total: 20 },
          Hard: { count: 1, total: 15 },
        },
        streak: 7,
        totalSolved: 9,
        heatmap: [
          { date: '2026-01-01', level: 1 },
          { date: '2026-01-02', level: 2 },
        ],
      };

      service.getUserStats.mockResolvedValue(mockStats);

      const result = await controller.getUserStats('user-123');

      expect(result).toEqual(mockStats);
      expect(service.getUserStats).toHaveBeenCalledWith('user-123');
    });
  });
});
