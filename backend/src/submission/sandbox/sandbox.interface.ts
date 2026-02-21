import { JudgeTestCase, JudgeCaseResult } from '../judge.service';

export interface ExecutionResult {
  status: 'Success' | 'Time Limit Exceeded' | 'Memory Limit Exceeded' | 'Runtime Error' | 'Compile Error';
  output?: string;
  error?: string;
  time: number;
  memory: number;
}

export interface SandboxConfig {
  timeLimit: number;
  memoryLimit: string;
  cpuLimit: string;
}

export const DEFAULT_SANDBOX_CONFIG: SandboxConfig = {
  timeLimit: 5000,
  memoryLimit: '256m',
  cpuLimit: '1',
};

export interface LanguageConfig {
  id: string;
  name: string;
  extension: string;
  timeLimit: number;
  memoryLimit: string;
  compileCommand?: string;
  runCommand: string;
}

export const LANGUAGE_CONFIGS: Record<string, LanguageConfig> = {
  javascript: {
    id: 'javascript',
    name: 'JavaScript',
    extension: '.js',
    timeLimit: 5000,
    memoryLimit: '256m',
    runCommand: 'node /sandbox/runners/run-javascript.js',
  },
  typescript: {
    id: 'typescript',
    name: 'TypeScript',
    extension: '.ts',
    timeLimit: 5000,
    memoryLimit: '256m',
    runCommand: 'node /sandbox/runners/run-javascript.js',
  },
  python: {
    id: 'python',
    name: 'Python',
    extension: '.py',
    timeLimit: 10000,
    memoryLimit: '256m',
    runCommand: 'python3 /sandbox/runners/run-python.py',
  },
  java: {
    id: 'java',
    name: 'Java',
    extension: '.java',
    timeLimit: 15000,
    memoryLimit: '512m',
    runCommand: 'bash /sandbox/runners/run-java.sh',
  },
  cpp: {
    id: 'cpp',
    name: 'C++',
    extension: '.cpp',
    timeLimit: 10000,
    memoryLimit: '256m',
    runCommand: 'bash /sandbox/runners/run-cpp.sh',
  },
  go: {
    id: 'go',
    name: 'Go',
    extension: '.go',
    timeLimit: 10000,
    memoryLimit: '256m',
    runCommand: 'bash /sandbox/runners/run-go.sh',
  },
};

export type SandboxType = 'docker' | 'vm';

/**
 * Abstract interface for code execution sandboxes.
 * Implementations must provide secure, isolated code execution.
 */
export interface SandboxServiceInterface {
  /**
   * Execute code in the sandbox and return the result.
   * @param language The programming language
   * @param code The source code to execute
   * @param testCase The test case with inputs and expected output
   * @param config Optional sandbox configuration overrides
   */
  execute(
    language: string,
    code: string,
    testCase: JudgeTestCase,
    config?: Partial<SandboxConfig>,
  ): Promise<JudgeCaseResult>;

  /**
   * Check if the sandbox is available and healthy.
   */
  isHealthy(): Promise<boolean>;

  /**
   * Get the type of this sandbox implementation.
   */
  getType(): SandboxType;

  /**
   * Clean up any resources used by the sandbox.
   */
  cleanup?(): Promise<void>;
}
