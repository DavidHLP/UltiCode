import {
  batchInsert,
  calculateOptimalBatchSize,
  prepareForBatchInsert,
} from '../../utils/batch-insert';
import type { PrismaDelegate, BatchInsertOptions } from '../../core/interfaces';

describe('batchInsert', () => {
  let mockDelegate: jest.Mocked<PrismaDelegate<{ id: number; name: string }>>;

  beforeEach(() => {
    mockDelegate = {
      createMany: jest.fn(),
      deleteMany: jest.fn(),
    };
  });

  it('should return early for empty data', async () => {
    const result = await batchInsert(mockDelegate, []);

    expect(result.total).toBe(0);
    expect(result.inserted).toBe(0);
    expect(mockDelegate.createMany).not.toHaveBeenCalled();
  });

  it('should insert data in batches', async () => {
    mockDelegate.createMany.mockResolvedValue({ count: 50 });

    const data = Array.from({ length: 100 }, (_, i) => ({
      id: i,
      name: `Item ${i}`,
    }));

    const result = await batchInsert(mockDelegate, data, { batchSize: 50 });

    expect(mockDelegate.createMany).toHaveBeenCalledTimes(2);
    expect(result.total).toBe(100);
    expect(result.inserted).toBe(100);
  });

  it('should track skipped duplicates', async () => {
    // First batch: 50 inserted, 0 skipped
    // Second batch: 40 inserted, 10 skipped
    mockDelegate.createMany
      .mockResolvedValueOnce({ count: 50 })
      .mockResolvedValueOnce({ count: 40 });

    const data = Array.from({ length: 100 }, (_, i) => ({
      id: i,
      name: `Item ${i}`,
    }));

    const result = await batchInsert(mockDelegate, data, {
      batchSize: 50,
      skipDuplicates: true,
    });

    expect(result.inserted).toBe(90);
    expect(result.skipped).toBe(10);
  });

  it('should call progress callback', async () => {
    mockDelegate.createMany.mockResolvedValue({ count: 25 });

    const data = Array.from({ length: 100 }, (_, i) => ({
      id: i,
      name: `Item ${i}`,
    }));

    const progressCalls: number[] = [];
    const onProgress = jest.fn((progress) => {
      progressCalls.push(progress.percentage);
    });

    await batchInsert(mockDelegate, data, {
      batchSize: 25,
      onProgress,
    });

    expect(onProgress).toHaveBeenCalledTimes(4);
    expect(progressCalls).toEqual([25, 50, 75, 100]);
  });

  it('should throw on error when onError is throw', async () => {
    mockDelegate.createMany.mockRejectedValue(new Error('DB error'));

    const data = [{ id: 1, name: 'Test' }];

    await expect(
      batchInsert(mockDelegate, data, { onError: 'throw' }),
    ).rejects.toThrow('DB error');
  });

  it('should skip batch on error when onError is skip-batch', async () => {
    mockDelegate.createMany
      .mockResolvedValueOnce({ count: 50 })
      .mockRejectedValueOnce(new Error('DB error'));

    const data = Array.from({ length: 100 }, (_, i) => ({
      id: i,
      name: `Item ${i}`,
    }));

    const result = await batchInsert(mockDelegate, data, {
      batchSize: 50,
      onError: 'skip-batch',
    });

    expect(result.inserted).toBe(50);
    expect(result.failed).toBe(50);
    expect(result.errors).toHaveLength(1);
  });
});

describe('calculateOptimalBatchSize', () => {
  it('should return reasonable batch size for typical records', () => {
    const size = calculateOptimalBatchSize(1000, 500);
    expect(size).toBeGreaterThanOrEqual(10);
    expect(size).toBeLessThanOrEqual(500);
  });

  it('should clamp to minimum batch size', () => {
    const size = calculateOptimalBatchSize(1000, 50000); // Very large records
    expect(size).toBe(10);
  });

  it('should clamp to maximum batch size', () => {
    const size = calculateOptimalBatchSize(1000, 10); // Very small records
    expect(size).toBe(500);
  });

  it('should use smaller batches for small datasets', () => {
    const size = calculateOptimalBatchSize(20, 500);
    expect(size).toBeLessThanOrEqual(20);
  });
});

describe('prepareForBatchInsert', () => {
  it('should remove undefined values by default', () => {
    const data = [
      { id: 1, name: 'Test', optional: undefined },
      { id: 2, name: 'Test2', optional: 'value' },
    ];

    const prepared = prepareForBatchInsert(data);

    expect(prepared[0]).not.toHaveProperty('optional');
    expect(prepared[1]).toHaveProperty('optional', 'value');
  });

  it('should keep undefined values when configured', () => {
    const data = [{ id: 1, name: 'Test', optional: undefined }];

    const prepared = prepareForBatchInsert(data, { removeUndefined: false });

    expect(prepared[0]).toHaveProperty('optional', undefined);
  });

  it('should convert dates when configured', () => {
    const date = new Date('2024-01-01');
    const data = [{ id: 1, createdAt: date }];

    const prepared = prepareForBatchInsert(data, { convertDates: true });

    expect(prepared[0].createdAt).toBe(date.toISOString());
  });
});
