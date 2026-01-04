import { Module, Global } from '@nestjs/common';
import { PermissionService } from './services/permission.service';
import { AuditService } from './services/audit.service';
import { PermissionsGuard } from './guards/permissions.guard';
import { RolesGuard } from './guards/roles.guard';
import { AdminUserController } from './controllers/admin-user.controller';
import { PrismaService } from '../prisma.service';
import { UserModule } from '../user/user.module';

@Global()
@Module({
  imports: [UserModule],
  controllers: [AdminUserController],
  providers: [
    PermissionService,
    AuditService,
    PermissionsGuard,
    RolesGuard,
    PrismaService,
  ],
  exports: [PermissionService, AuditService, PermissionsGuard, RolesGuard],
})
export class AdminModule {}
