import { Injectable } from '@nestjs/common';
import { PrismaService } from '../../prisma.service';
import { JudgeService, JudgeTestCase } from '../judge.service';

@Injectable()
export class SubmissionExecutionService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly judgeService: JudgeService,
  ) {}

  async run(
    problemId: number,
    data: {
      language: string;
      code: string;
      testCases?: JudgeTestCase[];
    },
    userId?: string,
  ) {
    const testCases =
      data.testCases && data.testCases.length > 0
        ? this.normalizeTestCases(data.testCases)
        : await this.buildTestCasesFromExamples(problemId);
    const judgeResult = await this.judgeService.judge(
      data.language,
      data.code,
      testCases,
    );

    const runId = `run-${problemId}-${Date.now()}`;
    const cases = judgeResult.cases.map((detail, index) => ({
      id: `${runId}-case-${index + 1}`,
      runId,
      submissionTestId: `${runId}-test-${index + 1}`,
      testCaseId: testCases[index]?.id ?? `${problemId}-${index + 1}`,
      caseLabel: testCases[index]?.label ?? `Case ${index + 1}`,
      status: detail.status,
      runtime: `${detail.time} ms`,
      memory: `${detail.memory} MB`,
      detail: detail.detail,
      output: detail.output,
      expectedOutput: detail.expectedOutput,
      inputs: detail.inputs ?? [],
    }));

    return {
      id: runId,
      submissionId: runId,
      problemId,
      userId: userId ?? 'anonymous',
      verdict: judgeResult.compileError ? 'Compile Error' : judgeResult.verdict,
      runtime: `${judgeResult.runtime} ms`,
      memory: `${judgeResult.memory} MB`,
      cases,
      passed_cases: cases.filter((item) => item.status === 'Accepted').length,
      total_cases: cases.length,
      error_message: judgeResult.compileError ?? null,
    };
  }

  normalizeTestCases(testCases: JudgeTestCase[]): JudgeTestCase[] {
    return testCases.map((testCase, index) => {
      const caseId = testCase.id || `case-${index + 1}`;
      const inputs = Array.isArray(testCase.inputs) ? testCase.inputs : [];
      return {
        id: caseId,
        label: testCase.label || `Case ${index + 1}`,
        inputs: inputs.map((input, inputIndex) => ({
          id: input.id ?? `${caseId}-input-${inputIndex}`,
          name: input.name,
          value: input.value,
          label: input.label ?? input.name,
        })),
        output: testCase.output ?? '',
      };
    });
  }

  private async buildTestCasesFromExamples(
    problemId: number,
  ): Promise<JudgeTestCase[]> {
    const examples = await this.prisma.problemExample.findMany({
      where: { problem_id: problemId },
      orderBy: { example_order: 'asc' },
    });

    return examples.map((example, index) => {
      const inputs = Array.isArray(example.inputs)
        ? (example.inputs as { name: string; value: string }[])
        : [];
      return {
        id: example.id,
        label: `Case ${index + 1}`,
        inputs: inputs.map((input, inputIndex) => ({
          id: `${example.id}-input-${inputIndex}`,
          name: input.name,
          value: input.value,
          label: input.name,
        })),
        output: example.output_text,
      };
    });
  }
}
