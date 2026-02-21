import {
  Controller,
  Get,
  Post,
  Put,
  Delete,
  Body,
  Param,
  Query,
  ParseIntPipe,
} from '@nestjs/common';
import { TestCaseService } from './test-case.service';
import {
  CreateTestCaseDto,
  UpdateTestCaseDto,
  BulkImportTestCasesDto,
  TestCaseQueryDto,
} from './dto/create-test-case.dto';

@Controller('admin/problems/:problemId/test-cases')
export class TestCaseController {
  constructor(private readonly testCaseService: TestCaseService) {}

  @Post()
  create(
    @Param('problemId', ParseIntPipe) problemId: number,
    @Body() dto: CreateTestCaseDto,
  ) {
    return this.testCaseService.create(BigInt(problemId), dto);
  }

  @Get()
  findAll(
    @Param('problemId', ParseIntPipe) problemId: number,
    @Query() query: TestCaseQueryDto,
  ) {
    return this.testCaseService.findAll(BigInt(problemId), query);
  }

  @Get('export')
  export(@Param('problemId', ParseIntPipe) problemId: number) {
    return this.testCaseService.export(BigInt(problemId));
  }

  @Get(':testCaseId')
  findOne(
    @Param('problemId', ParseIntPipe) problemId: number,
    @Param('testCaseId') testCaseId: string,
  ) {
    return this.testCaseService.findOne(BigInt(problemId), testCaseId);
  }

  @Put(':testCaseId')
  update(
    @Param('problemId', ParseIntPipe) problemId: number,
    @Param('testCaseId') testCaseId: string,
    @Body() dto: UpdateTestCaseDto,
  ) {
    return this.testCaseService.update(BigInt(problemId), testCaseId, dto);
  }

  @Delete(':testCaseId')
  remove(
    @Param('problemId', ParseIntPipe) problemId: number,
    @Param('testCaseId') testCaseId: string,
  ) {
    return this.testCaseService.remove(BigInt(problemId), testCaseId);
  }

  @Post('bulk')
  bulkImport(
    @Param('problemId', ParseIntPipe) problemId: number,
    @Body() dto: BulkImportTestCasesDto,
  ) {
    return this.testCaseService.bulkImport(BigInt(problemId), dto);
  }

  @Put('reorder')
  reorder(
    @Param('problemId', ParseIntPipe) problemId: number,
    @Body('testCaseIds') testCaseIds: string[],
  ) {
    return this.testCaseService.reorder(BigInt(problemId), testCaseIds);
  }
}
