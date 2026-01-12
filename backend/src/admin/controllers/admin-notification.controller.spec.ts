import { Test, TestingModule } from '@nestjs/testing';
import { AdminNotificationController } from './admin-notification.controller';
import { AdminNotificationService } from '../services/admin-notification.service';
import { JwtService } from '@nestjs/jwt';
import { Reflector, ModuleRef } from '@nestjs/core';
import { PermissionsGuard } from '../guards/permissions.guard';
import { RolesGuard } from '../guards/roles.guard';
import { AuthGuard } from '../../auth/auth.guard';
describe('AdminNotificationController', () => {
  let controller: AdminNotificationController;
  let adminNotificationService: jest.Mocked<AdminNotificationService>;

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      controllers: [AdminNotificationController],
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
          provide: PermissionsGuard,
          useValue: {
            canActivate: jest.fn(() => true),
          },
        },
        {
          provide: RolesGuard,
          useValue: {
            canActivate: jest.fn(() => true),
          },
        },
        {
          provide: AdminNotificationService,
          useValue: {
            findMany: jest.fn(),
            findOne: jest.fn(),
            create: jest.fn(),
            update: jest.fn(),
            delete: jest.fn(),
          },
        },
      ],
    })
      .overrideGuard(AuthGuard)
      .useValue({ canActivate: jest.fn(() => true) })
      .compile();

    controller = module.get<AdminNotificationController>(
      AdminNotificationController,
    );
    adminNotificationService = module.get(AdminNotificationService);
  });

  it('should be defined', () => {
    expect(controller).toBeDefined();
  });
});
