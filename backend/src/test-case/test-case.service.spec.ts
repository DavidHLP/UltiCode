import { Test, TestingModule } from '@nestjs/testing';
import { TestCaseService } from './test-case.service';
import { PrismaService } from '../prisma.service';
import { NotFoundException } from '@nestjs/common';

describe('TestCaseService', () => {
  let service: TestCaseService;
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  let prisma: PrismaService;

  const mockTestCase = {
    id: 'clx123456',
    problem_id: BigInt(1),
    is_sample: false,
    is_hidden: true,
    test_order: 0,
    input_text: '[1, 2, 3]',
    output_text: '[3, 2, 1]',
    explanation: 'Test explanation',
    constraints: null,
    created_at: new Date(),
    updated_at: new Date(),
  };

  const mockPrismaService = {
    testCase: {
      create: jest.fn().mockResolvedValue(mockTestCase),
      findMany: jest.fn().mockResolvedValue([mockTestCase]),
      findFirst: jest.fn().mockResolvedValue(mockTestCase),
      update: jest.fn().mockResolvedValue(mockTestCase),
      delete: jest.fn().mockResolvedValue(mockTestCase),
      deleteMany: jest.fn().mockResolvedValue({ count: 0 }),
      createMany: jest.fn().mockResolvedValue({ count: 3 }),
      count: jest.fn().mockResolvedValue(1),
      aggregate: jest.fn().mockResolvedValue({ _max: { test_order: null } }),
    },
    $transaction: jest.fn((promises) => Promise.all(promises)),
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        TestCaseService,
        {
          provide: PrismaService,
          useValue: mockPrismaService,
        },
      ],
    }).compile();

    service = module.get<TestCaseService>(TestCaseService);
    prisma = module.get<PrismaService>(PrismaService);

    // Reset mocks
    jest.clearAllMocks();
  });

  it('should be defined', () => {
    expect(service).toBeDefined();
  });

  describe('create', () => {
    it('should create a test case', async () => {
      mockPrismaService.testCase.aggregate.mockResolvedValue({
        _max: { test_order: null },
      });
      mockPrismaService.testCase.create.mockResolvedValue(mockTestCase);

      const result = await service.create(BigInt(1), {
        input_text: '[1, 2, 3]',
        output_text: '[3, 2, 1]',
      });

      expect(result).toEqual(mockTestCase);
      expect(mockPrismaService.testCase.create).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({
            input_text: '[1, 2, 3]',
            output_text: '[3, 2, 1]',
          }),
        }),
      );
    });

    it('should auto-increment test_order', async () => {
      mockPrismaService.testCase.aggregate.mockResolvedValue({
        _max: { test_order: 5 },
      });
      mockPrismaService.testCase.create.mockResolvedValue({
        ...mockTestCase,
        test_order: 6,
      });

      await service.create(BigInt(1), {
        input_text: 'input',
        output_text: 'output',
      });

      expect(mockPrismaService.testCase.create).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({
            test_order: 6,
          }),
        }),
      );
    });
  });

  describe('findAll', () => {
    it('should return paginated test cases', async () => {
      mockPrismaService.testCase.count.mockResolvedValue(2);
      mockPrismaService.testCase.findMany.mockResolvedValue([
        mockTestCase,
        mockTestCase,
      ]);

      const result = await service.findAll(BigInt(1), { page: 1, limit: 20 });

      expect(result.total).toBe(2);
      expect(result.items).toHaveLength(2);
    });

    it('should filter by is_sample', async () => {
      mockPrismaService.testCase.count.mockResolvedValue(1);
      mockPrismaService.testCase.findMany.mockResolvedValue([mockTestCase]);

      await service.findAll(BigInt(1), { page: 1, limit: 20, is_sample: true });

      expect(mockPrismaService.testCase.findMany).toHaveBeenCalledWith(
        expect.objectContaining({
          where: expect.objectContaining({
            is_sample: true,
          }),
        }),
      );
    });
  });

  describe('findOne', () => {
    it('should return a test case', async () => {
      mockPrismaService.testCase.findFirst.mockResolvedValue(mockTestCase);

      const result = await service.findOne(BigInt(1), 'clx123456');

      expect(result).toEqual(mockTestCase);
    });

    it('should throw NotFoundException if not found', async () => {
      mockPrismaService.testCase.findFirst.mockResolvedValue(null);

      await expect(service.findOne(BigInt(1), 'nonexistent')).rejects.toThrow(
        NotFoundException,
      );
    });
  });

  describe('update', () => {
    it('should update a test case', async () => {
      mockPrismaService.testCase.findFirst.mockResolvedValue(mockTestCase);
      mockPrismaService.testCase.update.mockResolvedValue({
        ...mockTestCase,
        input_text: 'new input',
      });

      const result = await service.update(BigInt(1), 'clx123456', {
        input_text: 'new input',
      });

      expect(result.input_text).toBe('new input');
    });
  });

  describe('remove', () => {
    it('should delete a test case', async () => {
      mockPrismaService.testCase.findFirst.mockResolvedValue(mockTestCase);
      mockPrismaService.testCase.delete.mockResolvedValue(mockTestCase);

      const result = await service.remove(BigInt(1), 'clx123456');

      expect(result.success).toBe(true);
    });
  });

  describe('bulkImport', () => {
    it('should import multiple test cases', async () => {
      mockPrismaService.testCase.aggregate.mockResolvedValue({
        _max: { test_order: null },
      });
      mockPrismaService.testCase.createMany.mockResolvedValue({ count: 3 });

      const result = await service.bulkImport(BigInt(1), {
        test_cases: [
          { input_text: '1', output_text: '1' },
          { input_text: '2', output_text: '2' },
          { input_text: '3', output_text: '3' },
        ],
      });

      expect(result.count).toBe(3);
    });

    it('should replace existing test cases when flag is set', async () => {
      mockPrismaService.testCase.deleteMany.mockResolvedValue({ count: 2 });
      mockPrismaService.testCase.aggregate.mockResolvedValue({
        _max: { test_order: null },
      });
      mockPrismaService.testCase.createMany.mockResolvedValue({ count: 3 });

      await service.bulkImport(BigInt(1), {
        replace_existing: true,
        test_cases: [
          { input_text: '1', output_text: '1' },
          { input_text: '2', output_text: '2' },
          { input_text: '3', output_text: '3' },
        ],
      });

      expect(mockPrismaService.testCase.deleteMany).toHaveBeenCalled();
    });
  });

  describe('export', () => {
    it('should export all test cases', async () => {
      mockPrismaService.testCase.findMany.mockResolvedValue([
        mockTestCase,
        mockTestCase,
      ]);

      const result = await service.export(BigInt(1));

      expect(result).toHaveLength(2);
    });
  });

  describe('reorder', () => {
    it('should reorder test cases', async () => {
      mockPrismaService.testCase.findMany.mockResolvedValue([
        { id: '1' },
        { id: '2' },
        { id: '3' },
      ]);
      mockPrismaService.testCase.update.mockResolvedValue(mockTestCase);

      await service.reorder(BigInt(1), ['3', '1', '2']);

      expect(mockPrismaService.$transaction).toHaveBeenCalled();
    });

    it('should throw if test case not found', async () => {
      mockPrismaService.testCase.findMany.mockResolvedValue([{ id: '1' }]);

      await expect(service.reorder(BigInt(1), ['1', '2', '3'])).rejects.toThrow(
        NotFoundException,
      );
    });
  });
});
