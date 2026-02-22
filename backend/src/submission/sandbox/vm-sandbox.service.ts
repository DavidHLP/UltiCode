import { Injectable, Logger } from '@nestjs/common';
import {
  SandboxServiceInterface,
  SandboxType,
  SandboxConfig,
  LANGUAGE_CONFIGS,
} from './sandbox.interface';
import { JudgeTestCase, JudgeCaseResult } from '../judge.service';
import { performance } from 'perf_hooks';
import * as vm from 'vm';
import ts from 'typescript';

/**
 * VM-based sandbox for development and testing.
 * WARNING: This is NOT secure for production use.
 * Use DockerSandboxService for production.
 */
@Injectable()
export class VmSandboxService implements SandboxServiceInterface {
  private readonly logger = new Logger(VmSandboxService.name);
  private readonly timeLimitMs = 2000;

  async execute(
    language: string,
    code: string,
    testCase: JudgeTestCase,
    config?: Partial<SandboxConfig>,
  ): Promise<JudgeCaseResult> {
    const normalizedLanguage = this.normalizeLanguage(language);
    if (!normalizedLanguage) {
      return {
        status: 'Compile Error',
        time: 0,
        memory: 0,
        output: '',
        expectedOutput: testCase.output ?? '',
        detail: `Language ${language} is not supported in VM sandbox.`,
        inputs: testCase.inputs ?? [],
      };
    }

    const timeLimit = config?.timeLimit ?? this.timeLimitMs;
    const inputs = testCase.inputs ?? [];
    const expectedOutput = testCase.output ?? '';

    return this.executeInVM(
      code,
      normalizedLanguage,
      inputs,
      expectedOutput,
      timeLimit,
    );
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

  private executeInVM(
    code: string,
    language: string,
    inputs: { name: string; value: string }[],
    expectedOutput: string,
    timeLimit: number,
  ): JudgeCaseResult {
    const entryName = this.detectEntryFunctionName(code);
    if (!entryName) {
      return {
        status: 'Compile Error',
        time: 0,
        memory: 0,
        output: '',
        expectedOutput,
        detail: 'Unable to detect the entry function name.',
        inputs,
      };
    }

    let compiledCode = code;
    if (language === 'typescript') {
      try {
        compiledCode = ts.transpileModule(code, {
          compilerOptions: {
            target: ts.ScriptTarget.ES2020,
            module: ts.ModuleKind.CommonJS,
            strict: false,
          },
        }).outputText;
      } catch (error) {
        return {
          status: 'Compile Error',
          time: 0,
          memory: 0,
          output: '',
          expectedOutput,
          detail:
            error instanceof Error
              ? error.message
              : 'TypeScript compilation failed',
          inputs,
        };
      }
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
      setupScript.runInContext(context, { timeout: timeLimit });
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      return {
        status: 'Compile Error',
        time: 0,
        memory: 0,
        output: '',
        expectedOutput,
        detail: message,
        inputs,
      };
    }

    if (typeof context.__entry !== 'function') {
      return {
        status: 'Compile Error',
        time: 0,
        memory: 0,
        output: '',
        expectedOutput,
        detail: `Entry function "${entryName}" was not found.`,
        inputs,
      };
    }

    const args = inputs.map((input) => this.parseValue(input.value));
    const invokeScript = new vm.Script('__entry(...__args)', {
      filename: 'invoke.js',
    });

    const start = performance.now();
    try {
      context.__args = args;
      const outputValue = invokeScript.runInContext(context, {
        timeout: timeLimit,
      }) as unknown;

      const elapsed = Math.max(1, Math.round(performance.now() - start));
      const memory = this.currentMemoryMb();
      const outputText = this.formatValue(outputValue);
      const expectedValue = this.parseValue(expectedOutput);

      const isAccepted =
        expectedOutput === ''
          ? true
          : this.deepEqual(outputValue, expectedValue);

      return {
        status: isAccepted ? 'Accepted' : 'Wrong Answer',
        time: elapsed,
        memory,
        output: outputText,
        expectedOutput,
        inputs,
      };
    } catch (error) {
      const elapsed = Math.max(1, Math.round(performance.now() - start));
      const memory = this.currentMemoryMb();
      const message = error instanceof Error ? error.message : String(error);
      const status: JudgeCaseResult['status'] = message.includes(
        'Script execution timed out',
      )
        ? 'Time Limit Exceeded'
        : 'Runtime Error';

      return {
        status,
        time: elapsed,
        memory,
        output: '',
        expectedOutput,
        detail: message,
        inputs,
      };
    }
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

  private deepEqual(a: unknown, b: unknown, tolerance = 1e-6): boolean {
    if (typeof a === 'number' && typeof b === 'number') {
      return Math.abs(a - b) <= tolerance;
    }
    if (Array.isArray(a) && Array.isArray(b)) {
      if (a.length !== b.length) return false;
      for (let i = 0; i < a.length; i++) {
        if (!this.deepEqual(a[i], b[i], tolerance)) return false;
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
            tolerance,
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

  async isHealthy(): Promise<boolean> {
    return true;
  }

  getType(): SandboxType {
    return 'vm';
  }
}
