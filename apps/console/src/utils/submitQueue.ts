/**
 * Submission Queue Utility
 *
 * Provides offline-first submission queue using IndexedDB.
 * Stores submissions locally when offline and processes them when back online.
 */

import { openDB, type IDBPDatabase } from "idb";

const DB_NAME = "ulticode-offline";
const DB_VERSION = 1;
const STORE_NAME = "submission-queue";

/**
 * Represents a queued submission stored in IndexedDB
 */
export interface QueuedSubmission {
  id: string;
  problemId: string;
  language: string;
  code: string;
  queuedAt: Date;
}

let dbPromise: Promise<IDBPDatabase<unknown>> | null = null;

/**
 * Initialize the submission queue database
 * Creates the object store and indexes if they don't exist
 */
export async function initSubmitQueue(): Promise<void> {
  if (!dbPromise) {
    dbPromise = openDB<unknown>(DB_NAME, DB_VERSION, {
      upgrade(db) {
        if (!db.objectStoreNames.contains(STORE_NAME)) {
          const store = db.createObjectStore(STORE_NAME, { keyPath: "id" });
          store.createIndex("by-queuedAt", "queuedAt");
        }
      },
    });
  }
  await dbPromise;
}

/**
 * Get the database instance, initializing if necessary
 */
async function getDB(): Promise<IDBPDatabase<unknown>> {
  if (!dbPromise) {
    await initSubmitQueue();
  }
  return dbPromise!;
}

/**
 * Generate a unique ID for a queued submission
 */
function generateId(): string {
  return `${Date.now()}-${Math.random().toString(36).substring(2, 11)}`;
}

/**
 * Add a submission to the queue
 * @param submission - The submission data (without id and queuedAt)
 * @returns The generated ID of the queued submission
 */
export async function addToQueue(submission: {
  problemId: string;
  language: string;
  code: string;
}): Promise<string> {
  const db = await getDB();

  const id = generateId();
  const queuedSubmission: QueuedSubmission = {
    id,
    problemId: submission.problemId,
    language: submission.language,
    code: submission.code,
    queuedAt: new Date(),
  };

  await db.put(STORE_NAME, queuedSubmission);
  return id;
}

/**
 * Get all queued submissions, sorted by queuedAt (oldest first)
 * @returns Array of queued submissions
 */
export async function getQueue(): Promise<QueuedSubmission[]> {
  const db = await getDB();

  const submissions = await db.getAllFromIndex(STORE_NAME, "by-queuedAt");

  // Convert queuedAt back to Date objects (IndexedDB stores them as strings)
  return submissions.map((sub) => ({
    ...sub,
    queuedAt: new Date(sub.queuedAt),
  }));
}

/**
 * Get the number of submissions in the queue
 * @returns The count of queued submissions
 */
export async function getQueueLength(): Promise<number> {
  const db = await getDB();
  return db.count(STORE_NAME);
}

/**
 * Remove a specific submission from the queue by ID
 * @param id - The ID of the submission to remove
 */
export async function removeFromQueue(id: string): Promise<void> {
  const db = await getDB();
  await db.delete(STORE_NAME, id);
}

/**
 * Clear all submissions from the queue
 */
export async function clearQueue(): Promise<void> {
  const db = await getDB();
  await db.clear(STORE_NAME);
}

/**
 * Process each submission in the queue with the provided handler
 * Successfully processed submissions are removed from the queue
 * @param handler - Async function to process each submission, returns true on success
 */
export async function processQueue(
  handler: (submission: QueuedSubmission) => Promise<boolean>,
): Promise<{ processed: number; failed: number }> {
  const queue = await getQueue();
  let processed = 0;
  let failed = 0;

  for (const submission of queue) {
    try {
      const success = await handler(submission);
      if (success) {
        await removeFromQueue(submission.id);
        processed++;
      } else {
        failed++;
      }
    } catch {
      failed++;
    }
  }

  return { processed, failed };
}
