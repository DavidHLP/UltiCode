import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";

// Mock IndexedDB using a simple in-memory store
const mockStore = new Map<string, unknown>();

vi.mock("idb", () => ({
  openDB: vi.fn().mockImplementation(() => {
    mockStore.clear();
    return Promise.resolve({
      put: async (_storeName: string, value: { id: string }) => {
        mockStore.set(value.id, value);
      },
      getAllFromIndex: async () => {
        // Return sorted by queuedAt (oldest first)
        const items = Array.from(mockStore.values()) as Array<{
          queuedAt: string;
        }>;
        return items.sort(
          (a, b) =>
            new Date(a.queuedAt).getTime() - new Date(b.queuedAt).getTime(),
        );
      },
      count: async () => mockStore.size,
      delete: async (_storeName: string, id: string) => {
        mockStore.delete(id);
      },
      clear: async () => {
        mockStore.clear();
      },
    });
  }),
}));

// Import after mock is set up
import {
  initSubmitQueue,
  addToQueue,
  getQueue,
  removeFromQueue,
  clearQueue,
  getQueueLength,
  processQueue,
} from "../submitQueue";

describe("submitQueue", () => {
  beforeEach(async () => {
    mockStore.clear();
    await clearQueue();
  });

  afterEach(async () => {
    mockStore.clear();
    await clearQueue();
  });

  describe("initSubmitQueue", () => {
    it("should initialize without error", async () => {
      await expect(initSubmitQueue()).resolves.not.toThrow();
    });
  });

  describe("addToQueue", () => {
    it("should add a submission to the queue", async () => {
      const submission = {
        problemId: "test-problem",
        language: "typescript",
        code: 'console.log("hello")',
      };

      const id = await addToQueue(submission);
      expect(id).toBeDefined();
      expect(typeof id).toBe("string");
    });

    it("should return the queued submission with id and timestamp", async () => {
      const submission = {
        problemId: "test-problem",
        language: "typescript",
        code: 'console.log("hello")',
      };

      await addToQueue(submission);
      const queue = await getQueue();

      expect(queue).toHaveLength(1);
      expect(queue[0].problemId).toBe("test-problem");
      expect(queue[0].language).toBe("typescript");
      expect(queue[0].code).toBe('console.log("hello")');
      expect(queue[0].id).toBeDefined();
      expect(queue[0].queuedAt).toBeInstanceOf(Date);
    });
  });

  describe("getQueueLength", () => {
    it("should return 0 for empty queue", async () => {
      const length = await getQueueLength();
      expect(length).toBe(0);
    });

    it("should return correct count after adding items", async () => {
      await addToQueue({ problemId: "test-1", language: "ts", code: "code1" });
      await addToQueue({ problemId: "test-2", language: "py", code: "code2" });
      expect(await getQueueLength()).toBe(2);
    });
  });

  describe("removeFromQueue", () => {
    it("should remove a submission by id", async () => {
      const id = await addToQueue({
        problemId: "test",
        language: "ts",
        code: "code",
      });
      await removeFromQueue(id);
      expect(await getQueue()).toHaveLength(0);
    });
  });

  describe("clearQueue", () => {
    it("should remove all submissions", async () => {
      await addToQueue({ problemId: "test-1", language: "ts", code: "code1" });
      await addToQueue({ problemId: "test-2", language: "py", code: "code2" });
      await clearQueue();
      expect(await getQueue()).toHaveLength(0);
    });
  });

  describe("processQueue", () => {
    it("should process each submission and remove successful ones", async () => {
      await addToQueue({ problemId: "test-1", language: "ts", code: "code1" });
      await addToQueue({ problemId: "test-2", language: "py", code: "code2" });

      const handler = vi.fn().mockResolvedValue(true);
      const result = await processQueue(handler);

      expect(result.processed).toBe(2);
      expect(result.failed).toBe(0);
      expect(handler).toHaveBeenCalledTimes(2);
      expect(await getQueueLength()).toBe(0);
    });

    it("should keep failed submissions in queue", async () => {
      await addToQueue({ problemId: "test-1", language: "ts", code: "code1" });
      await addToQueue({ problemId: "test-2", language: "py", code: "code2" });

      const handler = vi
        .fn()
        .mockResolvedValueOnce(true)
        .mockResolvedValueOnce(false);

      const result = await processQueue(handler);

      expect(result.processed).toBe(1);
      expect(result.failed).toBe(1);
      expect(await getQueueLength()).toBe(1);
    });

    it("should handle handler exceptions", async () => {
      await addToQueue({ problemId: "test-1", language: "ts", code: "code1" });
      await addToQueue({ problemId: "test-2", language: "py", code: "code2" });

      const handler = vi
        .fn()
        .mockResolvedValueOnce(true)
        .mockRejectedValueOnce(new Error("Network error"));

      const result = await processQueue(handler);

      expect(result.processed).toBe(1);
      expect(result.failed).toBe(1);
      expect(await getQueueLength()).toBe(1);
    });
  });
});
