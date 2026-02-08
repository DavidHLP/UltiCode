/**
 * Container Pool Service
 *
 * Manages a pool of Docker containers for efficient code execution.
 * Containers are reused to avoid the overhead of creating new containers
 * for each execution.
 *
 * Pool behavior:
 * - Acquire: Get an available container or create a new one
 * - Release: Return container to pool for reuse
 * - Prune: Remove idle containers to free resources
 */

import { Injectable, Logger, OnModuleDestroy } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import Docker, { ContainerCreateOptions } from 'dockerode';
import {
  ManagedContainer,
  ContainerPoolStats,
  ContainerConfig,
} from '../dto/docker-judge.dto';

@Injectable()
export class ContainerPoolService implements OnModuleDestroy {
  private readonly logger = new Logger(ContainerPoolService.name);
  private readonly docker: Docker;
  private readonly config: ContainerConfig;
  private readonly poolSize: number;
  private readonly maxContainers: number;
  private readonly pruneInterval: number;

  private containers: Map<string, ManagedContainer> = new Map();
  private isShuttingDown = false;
  private pruneTimer?: NodeJS.Timeout;

  // Statistics
  private totalExecutions = 0;
  private totalExecutionTime = 0;

  constructor(private readonly configService: ConfigService) {
    this.docker = new Docker({
      socketPath:
        this.configService.get('DOCKER_SOCKET_PATH') || '/var/run/docker.sock',
    });

    this.poolSize = parseInt(
      this.configService.get('JUDGE_CONTAINER_POOL_SIZE') || '5',
      10,
    );
    this.maxContainers = parseInt(
      this.configService.get('JUDGE_CONTAINER_MAX_CONTAINERS') || '10',
      10,
    );

    this.config = {
      image:
        this.configService.get('JUDGE_CONTAINER_IMAGE') ||
        'ulticode-judge:latest',
      memoryLimit: parseInt(
        this.configService.get('JUDGE_DEFAULT_MEMORY_LIMIT') || '256',
        10,
      ),
      cpuLimit: 0.5,
      pidsLimit: 50,
      timeout: parseInt(
        this.configService.get('JUDGE_DEFAULT_TIME_LIMIT') || '2000',
        10,
      ),
      networkDisabled: true,
      seccompProfile: undefined,
    };

    this.pruneInterval = 60000; // Prune every minute

    // Start periodic pruning
    this.startPruneTimer();
  }

  /**
   * Acquire a container from the pool
   * Creates a new container if none available
   */
  async acquire(): Promise<ManagedContainer> {
    if (this.isShuttingDown) {
      throw new Error('Service is shutting down, cannot acquire containers');
    }

    // Find available container
    for (const [id, container] of this.containers.entries()) {
      if (!container.inUse) {
        container.inUse = true;
        container.lastUsedAt = new Date();
        container.executionCount++;

        this.logger.debug(`Acquired existing container ${id}`);
        return container;
      }
    }

    // No available container, create new one
    if (this.containers.size >= this.maxContainers) {
      throw new Error(
        `Maximum container limit (${this.maxContainers}) reached. Please try again later.`,
      );
    }

    const newContainer = await this.createContainer();
    this.containers.set(newContainer.id, newContainer);

    this.logger.debug(
      `Created new container ${newContainer.id}. Pool size: ${this.containers.size}`,
    );

    return newContainer;
  }

  /**
   * Release a container back to the pool
   */
  release(container: ManagedContainer): void {
    const managed = this.containers.get(container.id);
    if (!managed) {
      this.logger.warn(
        `Attempted to release unknown container ${container.id}`,
      );
      return;
    }

    managed.inUse = false;
    this.logger.debug(`Released container ${container.id}`);
  }

  /**
   * Create a new container
   */
  private async createContainer(): Promise<ManagedContainer> {
    const createOptions: ContainerCreateOptions = {
      Image: this.config.image,
      Cmd: ['node', 'dist/index.js'],
      AttachStdout: false,
      AttachStderr: false,
      Tty: false,
      OpenStdin: false,
      HostConfig: {
        NetworkMode: 'none',
        ReadonlyRootfs: true,
        Memory: this.config.memoryLimit * 1024 * 1024,
        NanoCpus: this.config.cpuLimit * 1e9,
        PidsLimit: this.config.pidsLimit,
        SecurityOpt: ['no-new-privileges'],
        // Temporary filesystems for workspace
        Tmpfs: {
          '/tmp': 'rw,noexec,nosuid,size=100m',
          '/workspace': 'rw,noexec,nosuid,size=100m',
        },
      },
      Env: [`NODE_ENV=production`, `PORT=3000`],
      Labels: {
        'ulticode-judge': 'true',
        'created-at': new Date().toISOString(),
      },
    };

    try {
      // Pull image if not exists
      await this.ensureImageExists();

      // Create container
      const container = await this.docker.createContainer(createOptions);
      await container.start();

      const managed: ManagedContainer = {
        id: container.id,
        container,
        inUse: true,
        createdAt: new Date(),
        lastUsedAt: new Date(),
        executionCount: 0,
      };

      return managed;
    } catch (error) {
      this.logger.error(`Failed to create container: ${error}`);
      throw new Error(
        `Container creation failed: ${error instanceof Error ? error.message : String(error)}`,
      );
    }
  }

  /**
   * Ensure the Docker image exists locally
   */
  private async ensureImageExists(): Promise<void> {
    try {
      await this.docker.getImage(this.config.image).inspect();
      this.logger.debug(`Image ${this.config.image} already exists locally`);
    } catch {
      this.logger.log(`Pulling image ${this.config.image}...`);
      await new Promise<void>((resolve, reject) => {
        void this.docker.pull(
          this.config.image,
          (err: Error, stream: NodeJS.ReadableStream) => {
            if (err) {
              reject(err);
              return;
            }

            this.docker.modem.followProgress(stream, (err) => {
              if (err) {
                reject(err);
              } else {
                resolve();
              }
            });
          },
        );
      });
      this.logger.log(`Image ${this.config.image} pulled successfully`);
    }
  }

  /**
   * Start periodic pruning of idle containers
   */
  private startPruneTimer(): void {
    this.pruneTimer = setInterval(() => {
      if (!this.isShuttingDown) {
        this.pruneIdleContainers().catch((error) => {
          this.logger.error('Error during container pruning:', error);
        });
      }
    }, this.pruneInterval);

    this.pruneTimer.unref(); // Don't block shutdown
  }

  /**
   * Remove idle containers to free resources
   * Keeps minimum pool size available
   */
  private async pruneIdleContainers(): Promise<void> {
    const idleThreshold = 5 * 60 * 1000; // 5 minutes
    const now = Date.now();
    const toDelete: string[] = [];

    for (const [id, container] of this.containers.entries()) {
      // Skip in-use containers
      if (container.inUse) {
        continue;
      }

      // Skip if we're at minimum pool size
      if (this.containers.size - toDelete.length <= this.poolSize) {
        continue;
      }

      // Check if container has been idle
      const idleTime =
        now -
        (container.lastUsedAt?.getTime() || container.createdAt.getTime());
      if (idleTime > idleThreshold) {
        toDelete.push(id);
      }
    }

    if (toDelete.length > 0) {
      this.logger.debug(`Pruning ${toDelete.length} idle containers`);
      await this.deleteContainers(toDelete);
    }
  }

  /**
   * Delete containers by ID
   */
  private async deleteContainers(containerIds: string[]): Promise<void> {
    for (const id of containerIds) {
      try {
        const managed = this.containers.get(id);
        if (managed) {
          await managed.container.stop();
          await managed.container.remove();
          this.containers.delete(id);
          this.logger.debug(`Deleted container ${id}`);
        }
      } catch (error) {
        this.logger.warn(`Failed to delete container ${id}: ${error}`);
      }
    }
  }

  /**
   * Get pool statistics
   */
  getStats(): ContainerPoolStats {
    const activeContainers = Array.from(this.containers.values()).filter(
      (c) => c.inUse,
    ).length;

    return {
      totalContainers: this.containers.size,
      activeContainers,
      availableContainers: this.containers.size - activeContainers,
      totalExecutions: this.totalExecutions,
      avgExecutionTime:
        this.totalExecutions > 0
          ? this.totalExecutionTime / this.totalExecutions
          : 0,
      utilizationRate:
        this.containers.size > 0 ? activeContainers / this.containers.size : 0,
    };
  }

  /**
   * Clean shutdown - remove all containers
   */
  async shutdown(): Promise<void> {
    this.isShuttingDown = true;

    if (this.pruneTimer) {
      clearInterval(this.pruneTimer);
    }

    this.logger.log('Shutting down container pool...');
    const containerIds = Array.from(this.containers.keys());
    await this.deleteContainers(containerIds);
    this.logger.log(
      `Removed ${containerIds.length} containers during shutdown`,
    );
  }

  /**
   * Clean up on module destroy
   */
  async onModuleDestroy(): Promise<void> {
    await this.shutdown();
  }
}
