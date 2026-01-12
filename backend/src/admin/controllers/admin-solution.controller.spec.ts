import { Test, TestingModule } from '@nestjs/testing';
import { AdminSolutionController } from './admin-solution.controller';
import { PrismaService } from '../../prisma.service';
import { JwtService } from '@nestjs/jwt';
import { Reflector, ModuleRef } from '@nestjs/core';
import { PermissionsGuard } from '../guards/permissions.guard';
import { RolesGuard } from '../guards/roles.guard';
import { AuthGuard } from '../../auth/auth.guard';
describe('AdminSolutionController', () => {
  let controller: AdminSolutionController;
  let prisma: jest.Mocked<PrismaService>;

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      controllers: [AdminSolutionController],
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
          provide: PrismaService,
          useValue: {
            solution: {
              findMany: jest.fn().mockResolvedValue([]),
              findUnique: jest.fn().mockResolvedValue(null),
              update: jest.fn().mockResolvedValue({}),
              delete: jest.fn().mockResolvedValue({}),
            },
          },
        },
      ],
    })
      .overrideGuard(AuthGuard)
      .useValue({ canActivate: jest.fn(() => true) })
      .compile();

    controller = module.get<AdminSolutionController>(AdminSolutionController);
    prisma = module.get(PrismaService);
  });

  it('should be defined', () => {
    expect(controller).toBeDefined();
  });
});
