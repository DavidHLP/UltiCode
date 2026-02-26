import { Module, Global } from '@nestjs/common';
import { DockerSandboxService } from './docker-sandbox.service';
import { VmSandboxService } from './vm-sandbox.service';
import { SandboxFactory } from './sandbox.factory';

@Global()
@Module({
  providers: [DockerSandboxService, VmSandboxService, SandboxFactory],
  exports: [DockerSandboxService, VmSandboxService, SandboxFactory],
})
export class SandboxModule {}
