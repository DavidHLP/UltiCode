import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { performance } from 'perf_hooks';
import * as vm from 'vm';
import ts from 'typescript';
import { DockerOrchestratorService } from './services/docker-orchestrator.service';

type JudgeStatus =
  | 'Accepted'
  | 'Wrong Answer'
  | 'Time Limit Exceeded'
  | 'Memory Limit Exceeded'
  | 'Output Limit Exceeded'
  | 'Runtime Error'
  | 'Compile Error'
  | 'Presentation Error'
  | 'System Error'
  | 'Judging'
  | 'Pending';

export interface JudgeInputField {
  id?: string;
  label?: string;
  name: string;
  value: string;
}

export interface JudgeTestCase {
  id: string;
  label?: string;
  inputs?: JudgeInputField[];
  output?: string;
}

export interface JudgeCaseResult {
  status: JudgeStatus;
  time: number;
  memory: number;
  output?: string;
  expectedOutput?: string;
  detail?: string;
  inputs?: JudgeInputField[];
}

export interface JudgeResult {
  verdict: JudgeStatus;
  runtime: number;
  memory: number;
  cases: JudgeCaseResult[];
  compileError?: string;
}

const TIME_LIMIT_MS = 2000;
const FLOAT_TOLERANCE = 1e-6;

@Injectable()
export class JudgeService {
  private readonly logger = new Logger(JudgeService.name);
  private readonly useDocker: boolean;

  constructor(
    private readonly configService: ConfigService,
    private readonly dockerOrchestrator: DockerOrchestratorService,
  ) {
    this.useDocker =
      this.configService.get<string>('JUDGE_CONTAINER_ENABLED') === 'true';
    if (this.useDocker) {
      this.logger.log('Docker container sandbox is ENABLED for code execution');
    } else {
      this.logger.error(
        'SECURITY WARNING: Docker container sandbox is DISABLED. ' +
          'Using unsafe vm module. DO NOT deploy to production without enabling Docker containers.',
      );
    }
  }

  async judge(
    language: string,
    code: string,
    testCases: JudgeTestCase[],
  ): Promise<JudgeResult> {
    // Use Docker sandbox if enabled, otherwise fall back to legacy vm module
    if (this.useDocker) {
      return this.judgeWithDocker(language, code, testCases);
    }
    // Legacy judge can remain sync for now, wrap in Promise for consistency
    return Promise.resolve(this.legacyJudge(language, code, testCases));
  }

  /**
   * Judge using Docker container sandbox (secure)
   * This is the recommended method for production use.
   */
  private async judgeWithDocker(
    language: string,
    code: string,
    testCases: JudgeTestCase[],
  ): Promise<JudgeResult> {
    try {
      return await this.dockerOrchestrator.executeInSandbox(
        code,
        language,
        testCases,
        TIME_LIMIT_MS,
      );
    } catch (error) {
      this.logger.error(
        `Docker execution failed: ${error instanceof Error ? error.message : String(error)}`,
        error instanceof Error ? error.stack : undefined,
      );
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
          detail: error instanceof Error ? error.message : String(error),
        })),
      };
    }
  }

  /**
   * Judge using legacy vm module (INSECURE - for backward compatibility only)
   * This method should only be used when JUDGE_CONTAINER_ENABLED=false
   *
   * @deprecated Use judgeWithDocker() for production
   */
  private legacyJudge(
    language: string,
    code: string,
    testCases: JudgeTestCase[],
  ): JudgeResult {
    const normalizedLanguage = this.normalizeLanguage(language);
    if (!normalizedLanguage) {
      return {
        verdict: 'Compile Error',
        runtime: 0,
        memory: 0,
        cases: [],
        compileError: `Language ${language} is not supported.`,
      };
    }

    const entryName = this.detectEntryFunctionName(code);
    if (!entryName) {
      return {
        verdict: 'Compile Error',
        runtime: 0,
        memory: 0,
        cases: [],
        compileError: 'Unable to detect the entry function name.',
      };
    }

    let compiledCode = code;
    if (normalizedLanguage === 'typescript') {
      compiledCode = ts.transpileModule(code, {
        compilerOptions: {
          target: ts.ScriptTarget.ES2020,
          module: ts.ModuleKind.CommonJS,
          strict: false,
        },
      }).outputText;
    }

    const context = vm.createContext({
      console: {
        log: () => undefined,
        error: () => undefined,
        warn: () => undefined,
      },
    });
    context.globalThis = context;

    const instrumentedCode = `${compiledCode}\n;globalThis.__entry = typeof ${entryName} !== 'undefined' ? ${entryName} : undefined;`;

    try {
      const setupScript = new vm.Script(instrumentedCode, {
        filename: 'submission.js',
      });
      setupScript.runInContext(context, { timeout: TIME_LIMIT_MS });
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      return {
        verdict: 'Compile Error',
        runtime: 0,
        memory: 0,
        cases: [],
        compileError: message,
      };
    }

    if (typeof context.__entry !== 'function') {
      return {
        verdict: 'Compile Error',
        runtime: 0,
        memory: 0,
        cases: [],
        compileError: `Entry function "${entryName}" was not found.`,
      };
    }

    const invokeScript = new vm.Script('__entry(...__args)', {
      filename: 'invoke.js',
    });

    const results: JudgeCaseResult[] = [];
    let verdict: JudgeStatus = 'Accepted';
    let totalRuntime = 0;
    let maxMemory = 0;

    for (let index = 0; index < testCases.length; index += 1) {
      const testCase = testCases[index];
      const inputs = testCase.inputs ?? [];
      const args = inputs.map((input) => this.parseValue(input.value));

      context.__args = args;
      const start = performance.now();
      try {
        const outputValue = invokeScript.runInContext(context, {
          timeout: TIME_LIMIT_MS,
        }) as unknown;
        const elapsed = Math.max(1, Math.round(performance.now() - start));
        const memory = this.currentMemoryMb();
        const expectedOutput = testCase.output ?? '';
        const expectedValue = this.parseValue(expectedOutput);
        const outputText = this.formatValue(outputValue);

        const isAccepted =
          expectedOutput === ''
            ? true
            : this.deepEqual(outputValue, expectedValue);

        const status: JudgeStatus = isAccepted ? 'Accepted' : 'Wrong Answer';

        results.push({
          status,
          time: elapsed,
          memory,
          output: outputText,
          expectedOutput,
          inputs,
        });

        totalRuntime += elapsed;
        if (memory > maxMemory) {
          maxMemory = memory;
        }

        if (!isAccepted) {
          verdict = status;
          this.markPendingCases(results, testCases, index + 1);
          break;
        }
      } catch (error) {
        const elapsed = Math.max(1, Math.round(performance.now() - start));
        const memory = this.currentMemoryMb();
        const message = error instanceof Error ? error.message : String(error);
        const status: JudgeStatus = message.includes(
          'Script execution timed out',
        )
          ? 'Time Limit Exceeded'
          : 'Runtime Error';
        results.push({
          status,
          time: elapsed,
          memory,
          output: '',
          expectedOutput: testCase.output ?? '',
          detail: message,
          inputs,
        });
        totalRuntime += elapsed;
        if (memory > maxMemory) {
          maxMemory = memory;
        }
        verdict = status;
        this.markPendingCases(results, testCases, index + 1);
        break;
      }
    }

    return {
      verdict,
      runtime: totalRuntime,
      memory: maxMemory,
      cases: results,
    };
  }

  private normalizeLanguage(language: string): string | null {
    const normalized = (language || '').toLowerCase();
    if (normalized === 'javascript' || normalized === 'js') {
      return 'javascript';
    }
    if (normalized === 'typescript' || normalized === 'ts') {
      return 'typescript';
    }
    return null;
  }

  private detectEntryFunctionName(code: string): string | null {
    const patterns = [
      /(?:export\s+default\s+|export\s+)?function\s+([A-Za-z_$][\w$]*)\s*\(/,
      /\b(?:const|let|var)\s+([A-Za-z_$][\w$]*)\s*=\s*function\s*\(/,
      /\b(?:const|let|var)\s+([A-Za-z_$][\w$]*)\s*=\s*(?:async\s*)?\(?[\w\s,]*\)?\s*=>/,
    ];

    for (const pattern of patterns) {
      const match = code.match(pattern);
      if (match?.[1]) {
        return match[1];
      }
    }
    return null;
  }

  private parseValue(rawValue: string): unknown {
    const trimmed = (rawValue ?? '').trim();
    if (!trimmed) {
      return '';
    }
    try {
      return JSON.parse(trimmed);
    } catch {
      return trimmed;
    }
  }

  private formatValue(value: unknown): string {
    if (value === null) return 'null';
    if (value === undefined) return 'undefined';
    if (typeof value === 'string') return value;
    if (typeof value === 'number' || typeof value === 'boolean') {
      return String(value);
    }
    try {
      return JSON.stringify(value);
    } catch {
      return Object.prototype.toString.call(value) as string;
    }
  }

  private deepEqual(a: unknown, b: unknown): boolean {
    if (typeof a === 'number' && typeof b === 'number') {
      return Math.abs(a - b) <= FLOAT_TOLERANCE;
    }
    if (Array.isArray(a) && Array.isArray(b)) {
      if (a.length !== b.length) return false;
      for (let i = 0; i < a.length; i += 1) {
        if (!this.deepEqual(a[i], b[i])) return false;
      }
      return true;
    }
    if (
      a &&
      b &&
      typeof a === 'object' &&
      typeof b === 'object' &&
      !Array.isArray(a) &&
      !Array.isArray(b)
    ) {
      const keysA = Object.keys(a as Record<string, unknown>);
      const keysB = Object.keys(b as Record<string, unknown>);
      if (keysA.length !== keysB.length) return false;
      for (const key of keysA) {
        if (
          !this.deepEqual(
            (a as Record<string, unknown>)[key],
            (b as Record<string, unknown>)[key],
          )
        ) {
          return false;
        }
      }
      return true;
    }
    return a === b;
  }

  private currentMemoryMb(): number {
    return Math.round((process.memoryUsage().heapUsed / 1024 / 1024) * 10) / 10;
  }

  private markPendingCases(
    results: JudgeCaseResult[],
    testCases: JudgeTestCase[],
    startIndex: number,
  ) {
    for (let index = startIndex; index < testCases.length; index += 1) {
      const pendingCase = testCases[index];
      results.push({
        status: 'Pending',
        time: 0,
        memory: 0,
        output: '',
        expectedOutput: pendingCase.output ?? '',
        inputs: pendingCase.inputs ?? [],
      });
    }
  }
}
