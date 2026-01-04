import { Module, Global } from '@nestjs/common';
import { PermissionService } from './services/permission.service';
import { AuditService } from './services/audit.service';
import { AdminDashboardService } from './services/admin-dashboard.service';
import { PermissionsGuard } from './guards/permissions.guard';
import { RolesGuard } from './guards/roles.guard';
import { AdminUserController } from './controllers/admin-user.controller';
import { AdminDashboardController } from './controllers/admin-dashboard.controller';
import { AdminAuditController } from './controllers/admin-audit.controller';
import { AdminProblemController } from './controllers/admin-problem.controller';
import { AdminSolutionController } from './controllers/admin-solution.controller';
import { AdminContestController } from './controllers/admin-contest.controller';
import { AdminForumController } from './controllers/admin-forum.controller';
import { AdminSettingsController } from './controllers/admin-settings.controller';
import { AdminBulkController } from './controllers/admin-bulk.controller';
import { PrismaService } from '../prisma.service';
import { UserModule } from '../user/user.module';

@Global()
@Module({
  imports: [UserModule],
  controllers: [
    AdminUserController,
    AdminDashboardController,
    AdminAuditController,
    AdminProblemController,
    AdminSolutionController,
    AdminContestController,
    AdminForumController,
    AdminSettingsController,
    AdminBulkController,
  ],
  providers: [
    PermissionService,
    AuditService,
    AdminDashboardService,
    PermissionsGuard,
    RolesGuard,
    PrismaService,
  ],
  exports: [
    PermissionService,
    AuditService,
    AdminDashboardService,
    PermissionsGuard,
    RolesGuard,
  ],
})
export class AdminModule {}
