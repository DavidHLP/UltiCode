import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { SandboxServiceInterface, SandboxType } from './sandbox.interface';
import { DockerSandboxService } from './docker-sandbox.service';
import { VmSandboxService } from './vm-sandbox.service';

@Injectable()
export class SandboxFactory {
  private readonly logger = new Logger(SandboxFactory.name);
  private primarySandbox: SandboxServiceInterface;
  private fallbackSandbox: SandboxServiceInterface;
  private preferredType: SandboxType;

  constructor(
    private configService: ConfigService,
    private dockerSandbox: DockerSandboxService,
    private vmSandbox: VmSandboxService,
  ) {
    this.preferredType = this.configService.get<SandboxType>(
      'SANDBOX_TYPE',
      'docker',
    );
    this.primarySandbox =
      this.preferredType === 'docker' ? dockerSandbox : vmSandbox;
    this.fallbackSandbox =
      this.preferredType === 'docker' ? vmSandbox : dockerSandbox;
  }

  /**
   * Get the appropriate sandbox service based on configuration and availability.
   * Falls back to VM sandbox if Docker is not available.
   */
  async getSandbox(): Promise<SandboxServiceInterface> {
    // Check if primary sandbox is healthy
    const isHealthy = await this.primarySandbox.isHealthy();

    if (isHealthy) {
      if (this.primarySandbox.getType() === 'vm') {
        this.logger.warn(
          'Using VM sandbox - this is NOT secure for production! Use Docker sandbox instead.',
        );
      }
      return this.primarySandbox;
    }

    // Fallback to alternative sandbox
    this.logger.warn(
      `Primary sandbox (${this.primarySandbox.getType()}) is not available, falling back to ${this.fallbackSandbox.getType()}`,
    );

    const fallbackHealthy = await this.fallbackSandbox.isHealthy();
    if (fallbackHealthy) {
      if (this.fallbackSandbox.getType() === 'vm') {
        this.logger.warn(
          'Using VM sandbox - this is NOT secure for production! Use Docker sandbox instead.',
        );
      }
      return this.fallbackSandbox;
    }

    // If neither is available, throw error
    throw new Error('No sandbox service is available');
  }

  /**
   * Get a specific sandbox type regardless of availability.
   */
  getSandboxByType(type: SandboxType): SandboxServiceInterface {
    if (type === 'docker') {
      return this.dockerSandbox;
    }
    return this.vmSandbox;
  }

  /**
   * Check which sandbox types are available.
   */
  async checkAvailability(): Promise<{ docker: boolean; vm: boolean }> {
    const [dockerHealthy, vmHealthy] = await Promise.all([
      this.dockerSandbox.isHealthy(),
      this.vmSandbox.isHealthy(),
    ]);

    return {
      docker: dockerHealthy,
      vm: vmHealthy,
    };
  }
}
