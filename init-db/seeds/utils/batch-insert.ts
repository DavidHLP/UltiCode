import type {
  BatchInsertOptions,
  BatchInsertResult,
  BatchProgress,
  PrismaDelegate,
} from '../core/interfaces';

/**
 * Default batch size for insert operations
 */
const DEFAULT_BATCH_SIZE = 100;

/**
 * Perform a batch insert operation using Prisma's createMany.
 *
 * Features:
 * - Chunks data into configurable batch sizes
 * - Uses skipDuplicates to handle unique constraint violations
 * - Reports inserted/skipped/failed counts
 * - Supports progress callbacks for large operations
 *
 * @param delegate - Prisma model delegate (e.g., prisma.user)
 * @param data - Array of records to insert
 * @param options - Batch insert options
 * @returns Batch insert result with counts and timing
 */
export async function batchInsert<T extends object>(
  delegate: PrismaDelegate<T>,
  data: T[],
  options: BatchInsertOptions = {},
): Promise<BatchInsertResult> {
  const {
    batchSize = DEFAULT_BATCH_SIZE,
    skipDuplicates = true,
    onError = 'throw',
    onProgress,
  } = options;

  const startTime = Date.now();
  const result: BatchInsertResult = {
    total: data.length,
    inserted: 0,
    skipped: 0,
    failed: 0,
    errors: [],
    duration: 0,
  };

  if (data.length === 0) {
    result.duration = Date.now() - startTime;
    return result;
  }

  // Chunk the data into batches
  const batches = chunkArray(data, batchSize);
  const totalBatches = batches.length;

  for (let i = 0; i < batches.length; i++) {
    const batch = batches[i];

    try {
      const { count } = await delegate.createMany({
        data: batch,
        skipDuplicates,
      });

      result.inserted += count;

      // If skipDuplicates is true and count < batch.length, some were skipped
      if (skipDuplicates && count < batch.length) {
        result.skipped += batch.length - count;
      }
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error);

      if (onError === 'throw') {
        throw error;
      } else if (onError === 'skip-batch') {
        result.failed += batch.length;
        result.errors.push(`Batch ${i + 1}: ${errorMessage}`);
      } else if (onError === 'continue') {
        // Try individual inserts for this batch
        const individualResult = await insertIndividually(
          delegate,
          batch,
          skipDuplicates,
        );
        result.inserted += individualResult.inserted;
        result.skipped += individualResult.skipped;
        result.failed += individualResult.failed;
        result.errors.push(...individualResult.errors);
      }
    }

    // Report progress
    if (onProgress) {
      const processed = Math.min((i + 1) * batchSize, data.length);
      const progress: BatchProgress = {
        batch: i + 1,
        totalBatches,
        processed,
        total: data.length,
        percentage: (processed / data.length) * 100,
      };
      onProgress(progress);
    }
  }

  result.duration = Date.now() - startTime;
  return result;
}

/**
 * Insert records individually (fallback for failed batches)
 */
async function insertIndividually<T extends object>(
  delegate: PrismaDelegate<T>,
  data: T[],
  skipDuplicates: boolean,
): Promise<Omit<BatchInsertResult, 'total' | 'duration'>> {
  const result = {
    inserted: 0,
    skipped: 0,
    failed: 0,
    errors: [] as string[],
  };

  for (let i = 0; i < data.length; i++) {
    const record = data[i];
    try {
      const { count } = await delegate.createMany({
        data: [record],
        skipDuplicates,
      });

      if (count > 0) {
        result.inserted++;
      } else {
        result.skipped++;
      }
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error);
      result.failed++;
      result.errors.push(`Record ${i}: ${errorMessage}`);
    }
  }

  return result;
}

/**
 * Split an array into chunks of specified size
 */
function chunkArray<T>(array: T[], size: number): T[][] {
  const chunks: T[][] = [];
  for (let i = 0; i < array.length; i += size) {
    chunks.push(array.slice(i, i + size));
  }
  return chunks;
}

/**
 * Calculate optimal batch size based on record size and table complexity
 *
 * @param recordCount - Number of records to insert
 * @param estimatedRecordSize - Estimated size of each record in bytes
 * @returns Recommended batch size
 */
export function calculateOptimalBatchSize(
  recordCount: number,
  estimatedRecordSize: number = 500,
): number {
  // Target ~50KB per batch for good performance
  const targetBatchBytes = 50 * 1024;
  const optimalSize = Math.floor(targetBatchBytes / estimatedRecordSize);

  // Clamp between 10 and 500
  const clamped = Math.max(10, Math.min(500, optimalSize));

  // For small datasets, use smaller batches
  if (recordCount < 50) {
    return Math.min(clamped, recordCount);
  }

  return clamped;
}

/**
 * Helper to prepare data for batch insert by removing undefined values
 * and converting dates to ISO strings if needed
 */
export function prepareForBatchInsert<T extends object>(
  data: T[],
  options: {
    removeUndefined?: boolean;
    convertDates?: boolean;
  } = {},
): T[] {
  const { removeUndefined = true, convertDates = false } = options;

  return data.map((record) => {
    const prepared = { ...record };

    for (const key of Object.keys(prepared) as (keyof T)[]) {
      const value = prepared[key];

      // Remove undefined values
      if (removeUndefined && value === undefined) {
        delete prepared[key];
        continue;
      }

      // Convert Date objects to ISO strings if needed
      if (convertDates && value instanceof Date) {
        (prepared as Record<string, unknown>)[key as string] = value.toISOString();
      }
    }

    return prepared;
  });
}
