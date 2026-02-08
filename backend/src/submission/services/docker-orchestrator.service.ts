/**
 * Docker Orchestrator Service
 *
 * Manages Docker container lifecycle for secure code execution.
 * This service replaces the unsafe vm module with containerized isolation.
 *
 * Architecture:
 * 1. Acquires container from pool (or creates new one)
 * 2. Executes code in isolated container
 * 3. Returns result and releases container back to pool
 */

import { Injectable, Logger, OnModuleDestroy } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import Docker from 'dockerode';
import {
  DockerExecuteRequest,
  DockerExecuteResponse,
  ContainerConfig,
  ExecutionMetrics,
  ManagedContainer,
} from '../dto/docker-judge.dto';
import { JudgeResult, JudgeTestCase } from '../judge.service';
import { ContainerPoolService } from './container-pool.service';

@Injectable()
export class DockerOrchestratorService implements OnModuleDestroy {
  private readonly logger = new Logger(DockerOrchestratorService.name);
  private readonly docker: Docker;
  private readonly config: ContainerConfig;
  private readonly containerUrl: string;
  private isShuttingDown = false;

  constructor(
    private readonly configService: ConfigService,
    private readonly poolService: ContainerPoolService,
  ) {
    this.docker = new Docker({
      socketPath:
        this.configService.get('DOCKER_SOCKET_PATH') || '/var/run/docker.sock',
    });

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

    this.containerUrl = `http://localhost:3000`;
  }

  /**
   * Execute code in a Docker container with full isolation
   */
  async executeInSandbox(
    code: string,
    language: string,
    testCases: JudgeTestCase[],
    timeLimit?: number,
    memoryLimit?: number,
  ): Promise<JudgeResult> {
    if (this.isShuttingDown) {
      throw new Error('Service is shutting down, cannot accept new executions');
    }

    const startTime = Date.now();
    const metrics: ExecutionMetrics = {
      containerId: '',
      acquireTime: 0,
      executionTime: 0,
      releaseTime: 0,
      totalTime: 0,
      peakMemory: 0,
      success: false,
    };

    let container: ManagedContainer | undefined;
    try {
      // Acquire container from pool
      const acquireStart = Date.now();
      container = await this.poolService.acquire();
      metrics.containerId = container.id;
      metrics.acquireTime = Date.now() - acquireStart;

      this.logger.debug(`Acquired container ${container.id} for execution`);

      // Prepare execution request
      const request: DockerExecuteRequest = {
        code,
        language,
        testCases,
        timeLimit: timeLimit || this.config.timeout,
        memoryLimit: memoryLimit || this.config.memoryLimit,
      };

      // Execute code in container
      const execStart = Date.now();
      const response = await this.executeInContainer(container.id, request);
      metrics.executionTime = Date.now() - execStart;
      metrics.success = response.success;

      if (!response.success || !response.result) {
        throw new Error(response.error || 'Execution failed');
      }

      metrics.peakMemory = response.result.memory;

      return response.result;
    } catch (error) {
      metrics.success = false;
      metrics.error = error instanceof Error ? error.message : String(error);

      this.logger.error(
        `Container execution failed: ${metrics.error}`,
        error instanceof Error ? error.stack : undefined,
      );

      // Return system error result
      return {
        verdict: 'System Error',
        runtime: 0,
        memory: 0,
        cases: testCases.map((tc) => ({
          status: 'System Error',
          time: 0,
          memory: 0,
          output: '',
          expectedOutput: tc.output ?? '',
          inputs: tc.inputs ?? [],
          detail: metrics.error,
        })),
      };
    } finally {
      // Release container back to pool
      if (container) {
        const releaseStart = Date.now();
        try {
          this.poolService.release(container);
          metrics.releaseTime = Date.now() - releaseStart;
          metrics.totalTime = Date.now() - startTime;

          this.logger.debug(
            `Released container ${container.id}. ` +
              `Total time: ${metrics.totalTime}ms ` +
              `(acquire: ${metrics.acquireTime}ms, ` +
              `execute: ${metrics.executionTime}ms, ` +
              `release: ${metrics.releaseTime}ms)`,
          );
        } catch (error) {
          this.logger.warn(
            `Failed to release container ${container.id}: ${error}`,
          );
        }
      }
    }
  }

  /**
   * Execute code in a specific container via HTTP
   */
  private async executeInContainer(
    containerId: string,
    request: DockerExecuteRequest,
  ): Promise<DockerExecuteResponse> {
    const container = this.docker.getContainer(containerId);

    // Wait for container to be running
    await this.waitForContainer(container);

    // Execute command to run curl inside container
    const exec = await container.exec({
      Cmd: [
        'node',
        '-e',
        `const http = require('http');\n` +
          `const data = JSON.stringify(${JSON.stringify(request)});\n\n` +
          `const options = {\n` +
          `  hostname: 'localhost',\n` +
          `  port: 3000,\n` +
          `  path: '/execute',\n` +
          `  method: 'POST',\n` +
          `  headers: {\n` +
          `    'Content-Type': 'application/json',\n` +
          `    'Content-Length': data.length\n` +
          `  }\n` +
          `};\n\n` +
          `const req = http.request(options, (res) => {\n` +
          `  let body = '';\n` +
          `  res.on('data', (chunk) => body += chunk);\n` +
          `  res.on('end', () => console.log(body));\n` +
          `});\n\n` +
          `req.on('error', (e) => { console.error(JSON.stringify({ success: false, error: e.message })); });\n` +
          `req.write(data);\n` +
          `req.end();`,
      ],
      AttachStdout: true,
      AttachStderr: true,
    });

    const stream = await exec.start({ Detach: false });
    const chunks: Buffer[] = [];

    return new Promise((resolve, reject) => {
      stream.on('data', (chunk: Buffer) => chunks.push(chunk));
      stream.on('error', reject);
      stream.on('end', () => {
        try {
          const output = Buffer.concat(chunks).toString('utf8').trim();
          // Handle both JSON response and error output
          const lines = output
            .split('\n')
            .filter((line) => line.trim().length > 0);
          const lastLine = lines[lines.length - 1] || output;
          const response = JSON.parse(lastLine) as DockerExecuteResponse;
          resolve(response);
        } catch (_error) {
          const rawOutput = Buffer.concat(chunks).toString('utf8').trim();
          reject(new Error(`Failed to parse container response: ${rawOutput}`));
        }
      });

      // Set timeout for execution
      setTimeout(() => {
        stream.destroy();
        reject(
          new Error(
            `Container execution timeout after ${this.config.timeout}ms`,
          ),
        );
      }, this.config.timeout + 1000); // Add buffer for HTTP overhead
    });
  }

  /**
   * Wait for container to be running and healthy
   */
  private async waitForContainer(container: Docker.Container): Promise<void> {
    const maxAttempts = 10;
    const delay = 100;

    for (let i = 0; i < maxAttempts; i++) {
      try {
        const info = await container.inspect();
        if (info.State.Running) {
          // Give extra time for the HTTP server to start
          await new Promise((resolve) => setTimeout(resolve, 100));
          return;
        }
      } catch {
        // Container might not be fully created yet
      }
      await new Promise((resolve) => setTimeout(resolve, delay));
    }

    throw new Error('Container failed to start within expected time');
  }

  /**
   * Check if the judge container image exists
   */
  async checkImageExists(): Promise<boolean> {
    try {
      const images = await this.docker.listImages({
        filters: JSON.stringify({
          reference: [this.config.image],
        }),
      });
      return images.length > 0;
    } catch (error) {
      this.logger.error(`Failed to check image existence: ${error}`);
      return false;
    }
  }

  /**
   * Get container pool statistics
   */
  getPoolStats() {
    return this.poolService.getStats();
  }

  /**
   * Clean shutdown
   */
  async onModuleDestroy() {
    this.isShuttingDown = true;
    this.logger.log('Shutting down Docker Orchestrator Service...');
    await this.poolService.shutdown();
  }
}
