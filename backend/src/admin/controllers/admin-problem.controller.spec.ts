import { Test, TestingModule } from '@nestjs/testing';
import { AdminProblemController } from './admin-problem.controller';

// Mock JSDOM to avoid ESM import issues in Jest
jest.mock('jsdom', () => ({
  JSDOM: jest.fn().mockImplementation(() => ({
    window: {
      document: {
        createTextNode: jest.fn(),
      },
    },
  })),
}));
import { PrismaService } from '../../prisma.service';
import { AuditService } from '../services/audit.service';
import { PermissionService } from '../services/permission.service';
import { ProblemVersionService } from '../services/problem-version.service';
import { JwtService } from '@nestjs/jwt';
import { Reflector, ModuleRef } from '@nestjs/core';
import { PermissionsGuard } from '../guards/permissions.guard';
import { RolesGuard } from '../guards/roles.guard';
import { AuthGuard } from '../../auth/auth.guard';
import { CsrfGuard } from '../../auth/csrf.guard';
import { CsrfService } from '../../auth/csrf.service';
describe('AdminProblemController', () => {
  let controller: AdminProblemController;
  let _prisma: jest.Mocked<PrismaService>;

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      controllers: [AdminProblemController],
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
          provide: PrismaService,
          useValue: {
            problem: {
              findMany: jest.fn().mockResolvedValue([]),
              findUnique: jest.fn().mockResolvedValue(null),
              create: jest.fn().mockResolvedValue({}),
              update: jest.fn().mockResolvedValue({}),
              delete: jest.fn().mockResolvedValue({}),
            },
          },
        },
        {
          provide: ProblemVersionService,
          useValue: {
            createVersion: jest.fn().mockResolvedValue({}),
            getVersionHistory: jest.fn().mockResolvedValue([]),
            getVersion: jest.fn().mockResolvedValue(null),
            restoreVersion: jest.fn().mockResolvedValue({}),
            compareVersions: jest.fn().mockResolvedValue({}),
          },
        },
      ],
    })
      .overrideGuard(AuthGuard)
      .useValue({ canActivate: jest.fn(() => true) })
      .compile();

    controller = module.get<AdminProblemController>(AdminProblemController);
    _prisma = module.get(PrismaService);
  });

  it('should be defined', () => {
    expect(controller).toBeDefined();
  });
});
