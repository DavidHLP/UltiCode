import { Test, TestingModule } from '@nestjs/testing';
import { AdminSettingsController } from './admin-settings.controller';
import { AdminSettingsService } from '../services/settings.service';
import { AuditService } from '../services/audit.service';
import { PermissionService } from '../services/permission.service';
import { JwtService } from '@nestjs/jwt';
import { Reflector, ModuleRef } from '@nestjs/core';
import { PermissionsGuard } from '../guards/permissions.guard';
import { RolesGuard } from '../guards/roles.guard';
import { AuthGuard } from '../../auth/auth.guard';
import { CsrfGuard } from '../../auth/csrf.guard';
import { CsrfService } from '../../auth/csrf.service';
describe('AdminSettingsController', () => {
  let controller: AdminSettingsController;
  let _settingsService: jest.Mocked<AdminSettingsService>;

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      controllers: [AdminSettingsController],
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
          provide: PermissionService,
          useValue: {
            hasPermission: jest.fn().mockResolvedValue(true),
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
          provide: CsrfGuard,
          useValue: {
            canActivate: jest.fn(() => true),
          },
        },
        {
          provide: AdminSettingsService,
          useValue: {
            getSettings: jest.fn(),
            updateSettings: jest.fn(),
          },
        },
        {
          provide: AuditService,
          useValue: {
            log: jest.fn(),
          },
        },
        {
          provide: CsrfService,
          useValue: {
            generateCsrfToken: jest.fn().mockResolvedValue('mock-csrf-token'),
            validateCsrfToken: jest.fn().mockResolvedValue(true),
            revokeCsrfToken: jest.fn(),
          },
        },
      ],
    })
      .overrideGuard(AuthGuard)
      .useValue({ canActivate: jest.fn(() => true) })
      .compile();

    controller = module.get<AdminSettingsController>(AdminSettingsController);
    _settingsService = module.get(AdminSettingsService);
  });

  it('should be defined', () => {
    expect(controller).toBeDefined();
  });
});
