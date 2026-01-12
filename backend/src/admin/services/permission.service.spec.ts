import { Test, TestingModule } from '@nestjs/testing';
import { PermissionService } from './permission.service';
import { PrismaService } from '../../prisma.service';
import { PermissionAction, PermissionResource, UserRole } from '@prisma/client';

describe('PermissionService', () => {
  let service: PermissionService;
  let prisma: jest.Mocked<PrismaService>;

  const mockUser = {
    id: 'user-123',
    role: UserRole.USER,
    permissions: [],
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        PermissionService,
        {
          provide: PrismaService,
          useValue: {
            user: {
              findUnique: jest.fn().mockResolvedValue(mockUser),
              update: jest.fn().mockResolvedValue(mockUser),
            },
            rolePermission: {
              findMany: jest.fn().mockResolvedValue([]),
            },
            userPermission: {
              upsert: jest.fn().mockResolvedValue({}),
              deleteMany: jest.fn().mockResolvedValue({ count: 1 }),
            },
          },
        },
      ],
    }).compile();

    service = module.get<PermissionService>(PermissionService);
    prisma = module.get(PrismaService);
  });

  it('should be defined', () => {
    expect(service).toBeDefined();
  });

  describe('hasPermission', () => {
    it('should return false when user not found', async () => {
      prisma.user.findUnique.mockResolvedValue(null);

      const result = await service.hasPermission(
        'user-123',
        PermissionAction.READ,
        PermissionResource.PROBLEM,
      );

      expect(result).toBe(false);
    });

    it('should return true when user has role permission', async () => {
      prisma.user.findUnique.mockResolvedValue(mockUser);
      prisma.rolePermission.findMany.mockResolvedValue([
        {
          action: PermissionAction.READ,
          resource: PermissionResource.PROBLEM,
        },
      ] as never);

      const result = await service.hasPermission(
        'user-123',
        PermissionAction.READ,
        PermissionResource.PROBLEM,
      );

      expect(result).toBe(true);
    });

    it('should return true when user has direct permission', async () => {
      const userWithPerm = {
        ...mockUser,
        permissions: [
          {
            action: PermissionAction.READ,
            resource: PermissionResource.PROBLEM,
            expires_at: null,
          },
        ],
      };
      prisma.user.findUnique.mockResolvedValue(userWithPerm);
      prisma.rolePermission.findMany.mockResolvedValue([]);

      const result = await service.hasPermission(
        'user-123',
        PermissionAction.READ,
        PermissionResource.PROBLEM,
      );

      expect(result).toBe(true);
    });
  });

  describe('hasRole', () => {
    it('should return true when user has role', () => {
      const result = service.hasRole(mockUser as any, [UserRole.USER]);

      expect(result).toBe(true);
    });

    it('should return false when user does not have role', () => {
      const result = service.hasRole(mockUser as any, [UserRole.ADMIN]);

      expect(result).toBe(false);
    });
  });

  describe('getUserPermissions', () => {
    it('should return user permissions', async () => {
      prisma.user.findUnique.mockResolvedValue(mockUser);
      prisma.rolePermission.findMany.mockResolvedValue([
        {
          action: PermissionAction.READ,
          resource: PermissionResource.PROBLEM,
        },
      ] as never);

      const result = await service.getUserPermissions('user-123');

      expect(Array.isArray(result)).toBe(true);
    });

    it('should return empty array when user not found', async () => {
      prisma.user.findUnique.mockResolvedValue(null);

      const result = await service.getUserPermissions('user-123');

      expect(result).toEqual([]);
    });
  });

  describe('grantPermission', () => {
    it('should grant permission to user', async () => {
      prisma.userPermission.upsert.mockResolvedValue({} as never);

      await service.grantPermission(
        'user-123',
        PermissionAction.READ,
        PermissionResource.PROBLEM,
        'admin-123',
      );

      expect(prisma.userPermission.upsert).toHaveBeenCalled();
    });
  });

  describe('revokePermission', () => {
    it('should revoke permission from user', async () => {
      prisma.userPermission.deleteMany.mockResolvedValue({ count: 1 } as never);

      await service.revokePermission(
        'user-123',
        PermissionAction.READ,
        PermissionResource.PROBLEM,
      );

      expect(prisma.userPermission.deleteMany).toHaveBeenCalled();
    });
  });

  describe('updateUserRole', () => {
    it('should update user role', async () => {
      prisma.user.update.mockResolvedValue({} as never);

      await service.updateUserRole('user-123', UserRole.ADMIN, 'admin-123');

      expect(prisma.user.update).toHaveBeenCalledWith({
        where: { id: 'user-123' },
        data: {
          role: UserRole.ADMIN,
          updated_by: 'admin-123',
        },
      });
    });
  });
});
