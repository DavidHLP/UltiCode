import { Module, Global } from '@nestjs/common';
import { DockerSandboxService } from './docker-sandbox.service';
import { VmSandboxService } from './vm-sandbox.service';
import { SandboxFactory } from './sandbox.factory';
import { SandboxMonitoringService } from './sandbox-monitoring.service';
import { PrismaService } from '../../prisma.service';

@Global()
@Module({
  providers: [
    PrismaService,
    SandboxMonitoringService,
    DockerSandboxService,
    VmSandboxService,
    SandboxFactory,
  ],
  exports: [
    SandboxMonitoringService,
    DockerSandboxService,
    VmSandboxService,
    SandboxFactory,
  ],
})
export class SandboxModule {}
