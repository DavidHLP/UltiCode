import { Injectable, Logger, OnModuleInit } from '@nestjs/common';
import { SandboxFactory } from './sandbox/sandbox.factory';
import { SandboxServiceInterface } from './sandbox/sandbox.interface';

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

@Injectable()
export class JudgeService implements OnModuleInit {
  private readonly logger = new Logger(JudgeService.name);
  private sandbox: SandboxServiceInterface | null = null;

  constructor(private sandboxFactory: SandboxFactory) {}

  async onModuleInit(): Promise<void> {
    try {
      this.sandbox = await this.sandboxFactory.getSandbox();
      this.logger.log(`Judge service initialized with ${this.sandbox.getType()} sandbox`);
    } catch (error) {
      this.logger.error(`Failed to initialize sandbox: ${error}`);
    }
  }

  async judge(
    language: string,
    code: string,
    testCases: JudgeTestCase[],
  ): Promise<JudgeResult> {
    if (!this.sandbox) {
      try {
        this.sandbox = await this.sandboxFactory.getSandbox();
      } catch (error) {
        return {
          verdict: 'System Error',
          runtime: 0,
          memory: 0,
          cases: [],
          compileError: 'Sandbox service is not available',
        };
      }
    }

    const results: JudgeCaseResult[] = [];
    let verdict: JudgeStatus = 'Accepted';
    let totalRuntime = 0;
    let maxMemory = 0;

    for (let index = 0; index < testCases.length; index += 1) {
      const testCase = testCases[index];

      try {
        const result = await this.sandbox.execute(language, code, testCase);

        results.push(result);
        totalRuntime += result.time;
        if (result.memory > maxMemory) {
          maxMemory = result.memory;
        }

        if (result.status !== 'Accepted') {
          verdict = result.status;
          this.markPendingCases(results, testCases, index + 1);
          break;
        }
      } catch (error) {
        const message = error instanceof Error ? error.message : 'Unknown error';
        results.push({
          status: 'System Error',
          time: 0,
          memory: 0,
          output: '',
          expectedOutput: testCase.output ?? '',
          detail: message,
          inputs: testCase.inputs ?? [],
        });
        verdict = 'System Error';
        this.markPendingCases(results, testCases, index + 1);
        break;
      }
    }

    // Find compile error in results
    const compileError = results.find((r) => r.status === 'Compile Error')?.detail;

    return {
      verdict,
      runtime: totalRuntime,
      memory: maxMemory,
      cases: results,
      compileError,
    };
  }

  private markPendingCases(
    results: JudgeCaseResult[],
    testCases: JudgeTestCase[],
    startIndex: number,
  ): void {
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
