import { Injectable, NotFoundException, Logger } from '@nestjs/common';
import { PrismaService } from '../prisma.service';
import {
  CreateTestCaseDto,
  UpdateTestCaseDto,
  BulkImportTestCasesDto,
  TestCaseQueryDto,
} from './dto/create-test-case.dto';
import { Prisma } from '@prisma/client';

@Injectable()
export class TestCaseService {
  private readonly logger = new Logger(TestCaseService.name);

  constructor(private prisma: PrismaService) {}

  async create(problemId: bigint, dto: CreateTestCaseDto) {
    // Get max test_order for the problem
    const maxOrder = await this.prisma.testCase.aggregate({
      where: { problem_id: problemId },
      _max: { test_order: true },
    });

    const testOrder = dto.test_order ?? (maxOrder._max.test_order ?? -1) + 1;

    return this.prisma.testCase.create({
      data: {
        problem_id: problemId,
        is_sample: dto.is_sample ?? false,
        is_hidden: dto.is_hidden ?? true,
        test_order: testOrder,
        input_text: dto.input_text,
        output_text: dto.output_text,
        explanation: dto.explanation,
        constraints: dto.constraints as Prisma.InputJsonValue,
      },
    });
  }

  async findAll(problemId: bigint, query: TestCaseQueryDto) {
    const page = query.page ?? 1;
    const limit = query.limit ?? 20;

    const where: Prisma.TestCaseWhereInput = {
      problem_id: problemId,
    };

    if (query.is_sample !== undefined) {
      where.is_sample = query.is_sample;
    }

    if (query.is_hidden !== undefined) {
      where.is_hidden = query.is_hidden;
    }

    const [total, items] = await Promise.all([
      this.prisma.testCase.count({ where }),
      this.prisma.testCase.findMany({
        where,
        orderBy: { test_order: 'asc' },
        skip: (page - 1) * limit,
        take: limit,
      }),
    ]);

    return {
      total,
      page,
      limit,
      items,
    };
  }

  async findOne(problemId: bigint, testCaseId: string) {
    const testCase = await this.prisma.testCase.findFirst({
      where: {
        id: testCaseId,
        problem_id: problemId,
      },
    });

    if (!testCase) {
      throw new NotFoundException(`Test case ${testCaseId} not found`);
    }

    return testCase;
  }

  async update(problemId: bigint, testCaseId: string, dto: UpdateTestCaseDto) {
    await this.findOne(problemId, testCaseId);

    return this.prisma.testCase.update({
      where: { id: testCaseId },
      data: {
        is_sample: dto.is_sample,
        is_hidden: dto.is_hidden,
        test_order: dto.test_order,
        input_text: dto.input_text,
        output_text: dto.output_text,
        explanation: dto.explanation,
        constraints: dto.constraints as Prisma.InputJsonValue,
      },
    });
  }

  async remove(problemId: bigint, testCaseId: string) {
    await this.findOne(problemId, testCaseId);

    await this.prisma.testCase.delete({
      where: { id: testCaseId },
    });

    return { success: true };
  }

  async bulkImport(problemId: bigint, dto: BulkImportTestCasesDto) {
    if (dto.replace_existing) {
      await this.prisma.testCase.deleteMany({
        where: { problem_id: problemId },
      });
    }

    // Get max test_order
    const maxOrder = await this.prisma.testCase.aggregate({
      where: { problem_id: problemId },
      _max: { test_order: true },
    });

    let currentOrder = (maxOrder._max.test_order ?? -1) + 1;

    const created = await this.prisma.testCase.createMany({
      data: dto.test_cases.map((tc) => ({
        problem_id: problemId,
        is_sample: tc.is_sample ?? false,
        is_hidden: tc.is_hidden ?? true,
        test_order: currentOrder++,
        input_text: tc.input_text,
        output_text: tc.output_text,
        explanation: tc.explanation,
      })),
    });

    this.logger.log(
      `Bulk imported ${created.count} test cases for problem ${problemId}`,
    );

    return { count: created.count };
  }

  async export(problemId: bigint) {
    const testCases = await this.prisma.testCase.findMany({
      where: { problem_id: problemId },
      orderBy: { test_order: 'asc' },
      select: {
        id: true,
        is_sample: true,
        is_hidden: true,
        test_order: true,
        input_text: true,
        output_text: true,
        explanation: true,
        constraints: true,
      },
    });

    return testCases;
  }

  async reorder(problemId: bigint, testCaseIds: string[]) {
    // Verify all test cases belong to the problem
    const existing = await this.prisma.testCase.findMany({
      where: {
        id: { in: testCaseIds },
        problem_id: problemId,
      },
      select: { id: true },
    });

    if (existing.length !== testCaseIds.length) {
      throw new NotFoundException('Some test cases not found');
    }

    // Update order in transaction
    await this.prisma.$transaction(
      testCaseIds.map((id, index) =>
        this.prisma.testCase.update({
          where: { id },
          data: { test_order: index },
        }),
      ),
    );

    return { success: true };
  }

  async getTestCasesForJudging(problemId: bigint, includeHidden = true) {
    const where: Prisma.TestCaseWhereInput = {
      problem_id: problemId,
    };

    if (!includeHidden) {
      where.is_hidden = false;
    }

    return this.prisma.testCase.findMany({
      where,
      orderBy: { test_order: 'asc' },
      select: {
        id: true,
        input_text: true,
        output_text: true,
        is_sample: true,
      },
    });
  }
}
