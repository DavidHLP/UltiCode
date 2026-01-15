import { Test, TestingModule } from '@nestjs/testing';
import { AdminDashboardController } from './admin-dashboard.controller';
import { AdminDashboardService } from '../services/admin-dashboard.service';
import { PermissionService } from '../services/permission.service';
import { JwtService } from '@nestjs/jwt';
import { Reflector, ModuleRef } from '@nestjs/core';
import { PermissionsGuard } from '../guards/permissions.guard';
import { RolesGuard } from '../guards/roles.guard';
import { AuthGuard } from '../../auth/auth.guard';
import { CsrfGuard } from '../../auth/csrf.guard';
import { CsrfService } from '../../auth/csrf.service';
import { ChartPeriod, ChartMetric } from '../dto/dashboard.dto';
describe('AdminDashboardController', () => {
  let controller: AdminDashboardController;
  let dashboardService: jest.Mocked<AdminDashboardService>;

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      controllers: [AdminDashboardController],
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
          provide: CsrfService,
          useValue: {
            generateCsrfToken: jest.fn(),
            validateCsrfToken: jest.fn(),
            revokeCsrfToken: jest.fn(),
          },
        },
        {
          provide: AdminDashboardService,
          useValue: {
            getDashboardStats: jest.fn(),
            getChartStats: jest.fn(),
          },
        },
      ],
    })
      .overrideGuard(AuthGuard)
      .useValue({ canActivate: jest.fn(() => true) })
      .compile();

    controller = module.get<AdminDashboardController>(AdminDashboardController);
    dashboardService = module.get(AdminDashboardService);
  });

  it('should be defined', () => {
    expect(controller).toBeDefined();
  });

  describe('getStats', () => {
    it('should return dashboard stats', async () => {
      const mockStats = {
        users: { total: 100, active: 80 },
        problems: { total: 50 },
      };

      dashboardService.getDashboardStats.mockResolvedValue(mockStats as never);

      const result = await controller.getStats();

      expect(result).toEqual(mockStats);
    });
  });

  describe('getChartStats', () => {
    it('should return chart stats', async () => {
      const mockChartData = {
        metric: 'users',
        period: 'daily',
        data: [],
      };

      dashboardService.getChartStats.mockResolvedValue(mockChartData as never);

      const result = await controller.getChartStats({
        period: ChartPeriod.DAY,
        metric: ChartMetric.USERS,
        days: 7,
      });

      expect(result).toEqual(mockChartData);
    });
  });
});
