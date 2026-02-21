import { Injectable, Logger, OnModuleInit, OnModuleDestroy } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import Docker from 'dockerode';
import { promises as fs } from 'fs';
import * as path from 'path';
import { v4 as uuidv4 } from 'uuid';
import {
  SandboxServiceInterface,
  SandboxType,
  SandboxConfig,
  LANGUAGE_CONFIGS,
} from './sandbox.interface';
import { JudgeTestCase, JudgeCaseResult } from '../judge.service';

interface ContainerResult {
  stdout: string;
  stderr: string;
  exitCode: number;
}

@Injectable()
export class DockerSandboxService implements SandboxServiceInterface, OnModuleInit, OnModuleDestroy {
  private readonly logger = new Logger(DockerSandboxService.name);
  private docker: Docker;
  private containerImage: string;
  private readonly tempDir = '/tmp/ulti-sandbox';
  private activeContainers = new Map<string, Docker.Container>();

  constructor(private configService: ConfigService) {
    this.docker = new Docker({
      socketPath: this.configService.get<string>('DOCKER_SOCKET', '/var/run/docker.sock'),
    });
    this.containerImage = this.configService.get<string>('JUDGE_IMAGE', 'ulti-judge:latest');
  }

  async onModuleInit(): Promise<void> {
    try {
      // Ensure temp directory exists
      await fs.mkdir(this.tempDir, { recursive: true });
      this.logger.log('Docker sandbox service initialized');

      // Check Docker availability
      await this.docker.ping();
      this.logger.log('Docker daemon is available');

      // Check if image exists, build if not
      const images = await this.docker.listImages({ filters: JSON.stringify({ reference: [this.containerImage] }) });
      if (images.length === 0) {
        this.logger.warn(`Docker image ${this.containerImage} not found. Please build it first.`);
      }
    } catch (error) {
      this.logger.error(`Failed to initialize Docker sandbox: ${error}`);
    }
  }

  async onModuleDestroy(): Promise<void> {
    // Clean up any active containers
    for (const [id, container] of this.activeContainers) {
      try {
        await container.stop();
        await container.remove();
        this.logger.debug(`Cleaned up container ${id}`);
      } catch (error) {
        this.logger.warn(`Failed to clean up container ${id}: ${error}`);
      }
    }
  }

  async execute(
    language: string,
    code: string,
    testCase: JudgeTestCase,
    config?: Partial<SandboxConfig>,
  ): Promise<JudgeCaseResult> {
    const langConfig = LANGUAGE_CONFIGS[language.toLowerCase()];
    if (!langConfig) {
      return {
        status: 'Compile Error',
        time: 0,
        memory: 0,
        output: '',
        expectedOutput: testCase.output ?? '',
        detail: `Language ${language} is not supported.`,
        inputs: testCase.inputs ?? [],
      };
    }

    const executionId = uuidv4();
    const workDir = path.join(this.tempDir, executionId);

    try {
      // Create working directory
      await fs.mkdir(path.join(workDir, 'code'), { recursive: true });
      await fs.mkdir(path.join(workDir, 'input'), { recursive: true });
      await fs.mkdir(path.join(workDir, 'output'), { recursive: true });

      // Write code file
      const codeFile = path.join(workDir, 'code', `solution${langConfig.extension}`);
      await fs.writeFile(codeFile, code);

      // Write input file
      const inputFile = path.join(workDir, 'input', 'args.json');
      const inputData = {
        args: (testCase.inputs ?? []).map((input) => input.value),
      };
      await fs.writeFile(inputFile, JSON.stringify(inputData));

      // Run in container
      const timeLimit = config?.timeLimit ?? langConfig.timeLimit;
      const memoryLimit = config?.memoryLimit ?? langConfig.memoryLimit;

      const result = await this.runContainer(
        workDir,
        langConfig.runCommand,
        timeLimit,
        memoryLimit,
      );

      // Parse result
      return this.parseResult(result, testCase, timeLimit);
    } catch (error) {
      this.logger.error(`Execution failed for ${executionId}: ${error}`);
      return {
        status: 'System Error',
        time: 0,
        memory: 0,
        output: '',
        expectedOutput: testCase.output ?? '',
        detail: error instanceof Error ? error.message : 'Unknown error',
        inputs: testCase.inputs ?? [],
      };
    } finally {
      // Cleanup work directory
      try {
        await fs.rm(workDir, { recursive: true, force: true });
      } catch (error) {
        this.logger.warn(`Failed to cleanup ${workDir}: ${error}`);
      }
    }
  }

  private async runContainer(
    workDir: string,
    runCommand: string,
    timeLimit: number,
    memoryLimit: string,
  ): Promise<ContainerResult> {
    const containerName = `judge-${path.basename(workDir)}`;

    // Create container with resource limits
    const container = await this.docker.createContainer({
      Image: this.containerImage,
      name: containerName,
      Cmd: ['bash', '-c', runCommand],
      Env: [
        `TIME_LIMIT_MS=${timeLimit}`,
        `MEMORY_LIMIT=${memoryLimit}`,
        `INPUT_FILE=/sandbox/input/args.json`,
        `CODE_FILE=/sandbox/code/solution.*`,
      ],
      HostConfig: {
        Binds: [
          `${workDir}/code:/sandbox/code:ro`,
          `${workDir}/input:/sandbox/input:ro`,
          `${workDir}/output:/sandbox/output:rw`,
        ],
        Memory: this.parseMemory(memoryLimit),
        CpuQuota: 100000, // 1 CPU
        CpuPeriod: 100000,
        PidsLimit: 64,
        NetworkMode: 'none', // No network access
        ReadonlyRootfs: false,
        SecurityOpt: ['no-new-privileges'],
      },
      StopTimeout: Math.ceil(timeLimit / 1000) + 5,
    });

    this.activeContainers.set(container.id, container);

    try {
      // Start container
      await container.start();

      // Wait for completion with timeout
      const timeoutMs = timeLimit + 5000;
      const result = await this.waitForContainer(container, timeoutMs);

      return result;
    } finally {
      // Remove container
      try {
        await container.remove({ force: true });
        this.activeContainers.delete(container.id);
      } catch (error) {
        this.logger.warn(`Failed to remove container: ${error}`);
      }
    }
  }

  private async waitForContainer(
    container: Docker.Container,
    timeoutMs: number,
  ): Promise<ContainerResult> {
    return new Promise((resolve, reject) => {
      const timeout = setTimeout(async () => {
        try {
          await container.stop();
        } catch {
          // Ignore stop errors
        }
        resolve({
          stdout: '',
          stderr: 'Execution timed out',
          exitCode: 137,
        });
      }, timeoutMs);

      container.wait(async (err, data) => {
        clearTimeout(timeout);
        if (err) {
          reject(err);
          return;
        }

        try {
          const logs = await container.logs({
            stdout: true,
            stderr: true,
          });
          const logString = logs.toString('utf-8').replace(/^\x00+/gm, '');

          resolve({
            stdout: logString,
            stderr: '',
            exitCode: data?.StatusCode ?? 0,
          });
        } catch (error) {
          reject(error);
        }
      });
    });
  }

  private parseResult(
    result: ContainerResult,
    testCase: JudgeTestCase,
    timeLimit: number,
  ): JudgeCaseResult {
    const expectedOutput = testCase.output ?? '';
    const inputs = testCase.inputs ?? [];

    // Handle timeout
    if (result.exitCode === 137 || result.stderr.includes('timed out')) {
      return {
        status: 'Time Limit Exceeded',
        time: timeLimit,
        memory: 0,
        output: '',
        expectedOutput,
        detail: 'Execution timed out',
        inputs,
      };
    }

    // Try to parse JSON output from runner
    try {
      const outputLines = result.stdout.trim().split('\n');
      const jsonLine = outputLines.find((line) => {
        try {
          JSON.parse(line);
          return true;
        } catch {
          return false;
        }
      });

      if (jsonLine) {
        const parsed = JSON.parse(jsonLine);

        if (parsed.status === 'Success') {
          const isAccepted = this.compareOutput(parsed.output, expectedOutput);
          return {
            status: isAccepted ? 'Accepted' : 'Wrong Answer',
            time: parsed.time ?? 0,
            memory: parsed.memory ?? 0,
            output: parsed.output ?? '',
            expectedOutput,
            inputs,
          };
        }

        return {
          status: this.mapStatus(parsed.status),
          time: parsed.time ?? 0,
          memory: parsed.memory ?? 0,
          output: '',
          expectedOutput,
          detail: parsed.error,
          inputs,
        };
      }
    } catch {
      // Fall through to raw output handling
    }

    // Handle non-JSON output (for compiled languages that output directly)
    const output = result.stdout.trim();
    const isAccepted = this.compareOutput(output, expectedOutput);

    return {
      status: isAccepted ? 'Accepted' : 'Wrong Answer',
      time: 0,
      memory: 0,
      output,
      expectedOutput,
      inputs,
    };
  }

  private compareOutput(actual: string, expected: string): boolean {
    const normalize = (str: string) =>
      str
        .trim()
        .replace(/\s+/g, ' ')
        .replace(/,\s*]/g, ']')
        .replace(/,\s*}/g, '}');

    const normalizedActual = normalize(actual);
    const normalizedExpected = normalize(expected);

    if (normalizedActual === normalizedExpected) {
      return true;
    }

    // Try JSON comparison for numeric tolerance
    try {
      const actualJson = JSON.parse(normalizedActual);
      const expectedJson = JSON.parse(normalizedExpected);
      return this.deepEqual(actualJson, expectedJson);
    } catch {
      return false;
    }
  }

  private deepEqual(a: unknown, b: unknown, tolerance = 1e-6): boolean {
    if (typeof a === 'number' && typeof b === 'number') {
      return Math.abs(a - b) <= tolerance;
    }
    if (Array.isArray(a) && Array.isArray(b)) {
      if (a.length !== b.length) return false;
      return a.every((item, i) => this.deepEqual(item, b[i], tolerance));
    }
    if (a && b && typeof a === 'object' && typeof b === 'object') {
      const keysA = Object.keys(a as object);
      const keysB = Object.keys(b as object);
      if (keysA.length !== keysB.length) return false;
      return keysA.every((key) =>
        this.deepEqual(
          (a as Record<string, unknown>)[key],
          (b as Record<string, unknown>)[key],
          tolerance,
        ),
      );
    }
    return a === b;
  }

  private mapStatus(status: string): JudgeCaseResult['status'] {
    const statusMap: Record<string, JudgeCaseResult['status']> = {
      Success: 'Accepted',
      'Time Limit Exceeded': 'Time Limit Exceeded',
      'Memory Limit Exceeded': 'Memory Limit Exceeded',
      'Runtime Error': 'Runtime Error',
      'Compile Error': 'Compile Error',
    };
    return statusMap[status] ?? 'Runtime Error';
  }

  private parseMemory(memoryStr: string): number {
    const match = memoryStr.match(/^(\d+)([kmg]?)$/i);
    if (!match) return 256 * 1024 * 1024;

    const value = parseInt(match[1], 10);
    const unit = (match[2] || 'm').toLowerCase();

    switch (unit) {
      case 'k':
        return value * 1024;
      case 'm':
        return value * 1024 * 1024;
      case 'g':
        return value * 1024 * 1024 * 1024;
      default:
        return value;
    }
  }

  async isHealthy(): Promise<boolean> {
    try {
      await this.docker.ping();
      return true;
    } catch {
      return false;
    }
  }

  getType(): SandboxType {
    return 'docker';
  }
}
