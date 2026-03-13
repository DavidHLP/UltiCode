import {
  Injectable,
  Logger,
  OnModuleInit,
  OnModuleDestroy,
  Inject,
  forwardRef,
} from '@nestjs/common';
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
import { SandboxMonitoringService } from './sandbox-monitoring.service';

/** Result from running a container */
interface ContainerResult {
  stdout: string;
  stderr: string;
  exitCode: number;
}

/**
 * Docker-based sandbox service for secure code execution.
 *
 * This service provides isolated code execution using Docker containers.
 * Each code submission runs in a fresh container with strict resource limits
 * (CPU, memory, time) to prevent malicious code from affecting the host system.
 *
 * ## Architecture
 * - Uses the official Docker API via dockerode
 * - Containers run with restricted permissions (no network, limited resources)
 * - Code and test cases are mounted as temporary files
 * - Supports 7 languages: JavaScript, TypeScript, Python, Java, C++, Go, Rust
 *
 * ## Resource Limits
 * - Time: 5-15 seconds per test case (language dependent)
 * - Memory: 256-512MB per container
 * - CPU: 1 core limit
 *
 * @see {@link SandboxServiceInterface} for the public API
 * @see {@link LANGUAGE_CONFIGS} for supported languages
 */
@Injectable()
export class DockerSandboxService
  implements SandboxServiceInterface, OnModuleInit, OnModuleDestroy
{
  private readonly logger = new Logger(DockerSandboxService.name);
  private docker: Docker;
  private containerImage: string;
  private readonly tempDir = '/tmp/ulti-sandbox';
  private activeContainers = new Map<string, Docker.Container>();

  constructor(
    private configService: ConfigService,
    @Inject(forwardRef(() => SandboxMonitoringService))
    private monitoringService: SandboxMonitoringService,
  ) {
    this.docker = new Docker({
      socketPath: this.configService.get<string>(
        'DOCKER_SOCKET',
        '/var/run/docker.sock',
      ),
    });
    this.containerImage = this.configService.get<string>(
      'JUDGE_IMAGE',
      'ulti-judge:latest',
    );
  }

  /**
   * Initializes the sandbox on module load.
   *
   * Creates the temp directory, verifies Docker connectivity,
   * and checks that the judge image is available.
   */
  async onModuleInit(): Promise<void> {
    try {
      // Ensure temp directory exists
      await fs.mkdir(this.tempDir, { recursive: true });
      this.logger.log('Docker sandbox service initialized');

      // Check Docker availability
      await this.docker.ping();
      this.logger.log('Docker daemon is available');

      // Check if image exists, build if not
      const images = await this.docker.listImages({
        filters: JSON.stringify({ reference: [this.containerImage] }),
      });
      if (images.length === 0) {
        this.logger.warn(
          `Docker image ${this.containerImage} not found. Please build it first.`,
        );
      }
    } catch (error) {
      this.logger.error(`Failed to initialize Docker sandbox: ${error}`);
    }
  }

  /**
   * Cleans up resources when the module is destroyed.
   *
   * Stops and removes any active containers to prevent resource leaks.
   */
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

  /**
   * Executes code against a test case in an isolated Docker container.
   *
   * This method:
   * 1. Validates the language configuration
   * 2. Creates temporary files for code and input
   * 3. Spawns a Docker container with resource limits
   * 4. Captures the output and measures resource usage
   * 5. Cleans up resources after execution
   *
   * @param language - The programming language (e.g., 'python', 'javascript')
   * @param code - The source code to execute
   * @param testCase - Test case with input and expected output
   * @param config - Optional resource limit overrides
   * @returns The execution result with status, output, time, and memory
   */
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

    // Start monitoring log
    await this.monitoringService.startExecution({
      executionId,
      language: langConfig.id,
    });

    try {
      // Create working directory
      await fs.mkdir(path.join(workDir, 'code'), { recursive: true });
      await fs.mkdir(path.join(workDir, 'input'), { recursive: true });
      await fs.mkdir(path.join(workDir, 'output'), { recursive: true });

      // Write code file
      const codeFile = path.join(
        workDir,
        'code',
        `solution${langConfig.extension}`,
      );
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

      const startTime = Date.now();
      const result = await this.runContainer(
        workDir,
        langConfig.runCommand,
        langConfig.extension,
        timeLimit,
        memoryLimit,
        executionId,
      );
      const executionTime = Date.now() - startTime;

      // Parse result
      const judgeResult = this.parseResult(result, testCase, timeLimit);

      // Complete monitoring log
      const monitoringStatus = this.mapToMonitoringStatus(judgeResult.status);
      await this.monitoringService.completeExecution(executionId, {
        status: monitoringStatus,
        timeMs: judgeResult.time || executionTime,
        memoryBytes: judgeResult.memory * 1024 * 1024, // Convert MB to bytes
        exitCode: result.exitCode,
        errorMessage: judgeResult.detail,
      });

      return judgeResult;
    } catch (error) {
      this.logger.error(`Execution failed for ${executionId}: ${error}`);

      // Record error in monitoring
      await this.monitoringService.recordError(
        executionId,
        error instanceof Error ? error : String(error),
      );

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

  private mapToMonitoringStatus(
    status: JudgeCaseResult['status'],
  ):
    | 'COMPLETED'
    | 'TIMEOUT'
    | 'MEMORY_EXCEEDED'
    | 'RUNTIME_ERROR'
    | 'COMPILE_ERROR'
    | 'SYSTEM_ERROR' {
    const statusMap: Partial<
      Record<
        JudgeCaseResult['status'],
        | 'COMPLETED'
        | 'TIMEOUT'
        | 'MEMORY_EXCEEDED'
        | 'RUNTIME_ERROR'
        | 'COMPILE_ERROR'
        | 'SYSTEM_ERROR'
      >
    > = {
      Accepted: 'COMPLETED',
      'Wrong Answer': 'COMPLETED',
      'Time Limit Exceeded': 'TIMEOUT',
      'Memory Limit Exceeded': 'MEMORY_EXCEEDED',
      'Output Limit Exceeded': 'MEMORY_EXCEEDED',
      'Runtime Error': 'RUNTIME_ERROR',
      'Compile Error': 'COMPILE_ERROR',
      'Presentation Error': 'COMPLETED',
      'System Error': 'SYSTEM_ERROR',
      Judging: 'COMPLETED',
      Pending: 'COMPLETED',
    };
    return statusMap[status] ?? 'SYSTEM_ERROR';
  }

  private async runContainer(
    workDir: string,
    runCommand: string,
    extension: string,
    timeLimit: number,
    memoryLimit: string,
    executionId: string,
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
        `CODE_FILE=/sandbox/code/solution${extension}`,
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

    // Update execution log with container ID
    await this.monitoringService.updateContainerId(
      executionId,
      container.id.substring(0, 12),
    );

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

  private waitForContainer(
    container: Docker.Container,
    timeoutMs: number,
  ): Promise<ContainerResult> {
    return new Promise((resolve, reject) => {
      const timeout = setTimeout(() => {
        container.stop().catch(() => {
          // Ignore stop errors
        });
        resolve({
          stdout: '',
          stderr: 'Execution timed out',
          exitCode: 137,
        });
      }, timeoutMs);

      container.wait((err, data) => {
        clearTimeout(timeout);
        if (err) {
          reject(err instanceof Error ? err : new Error(String(err)));
          return;
        }

        container
          .logs({
            stdout: true,
            stderr: true,
          })
          .then((logs) => {
            // Docker uses multiplexed stream format: 8-byte header + payload
            // Header: 1 byte stream type (1=stdout, 2=stderr) + 3 bytes padding + 4 bytes size
            // We need to strip these headers to get clean output
            const logString = this.parseDockerLogs(logs);

            resolve({
              stdout: logString,
              stderr: '',
              exitCode: data?.StatusCode ?? 0,
            });
          })
          .catch((error) => {
            reject(error instanceof Error ? error : new Error(String(error)));
          });
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
      const keysA = Object.keys(a);
      const keysB = Object.keys(b);
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

  /**
   * Parse Docker multiplexed log stream.
   *
   * Docker logs use a multiplexed stream format where each frame has:
   * - 1 byte: stream type (1=stdout, 2=stderr)
   * - 3 bytes: padding (zeros)
   * - 4 bytes: payload size (big-endian uint32)
   * - N bytes: actual payload
   *
   * @param logs - Raw log buffer from Docker
   * @returns Clean string with headers stripped
   */
  private parseDockerLogs(logs: Buffer): string {
    const result: string[] = [];
    let offset = 0;

    while (offset < logs.length) {
      // Need at least 8 bytes for the header
      if (offset + 8 > logs.length) {
        break;
      }

      // Read header
      const streamType = logs[offset];
      // Skip 3 padding bytes
      const size = logs.readUInt32BE(offset + 4);

      // Move past header
      offset += 8;

      // Read payload
      if (offset + size > logs.length) {
        // Incomplete frame, take what we have
        result.push(logs.toString('utf-8', offset));
        break;
      }

      if (size > 0) {
        const payload = logs.toString('utf-8', offset, offset + size);
        result.push(payload);
      }

      offset += size;
    }

    return result.join('');
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
