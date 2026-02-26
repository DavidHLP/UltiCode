import { describe, it, expect, beforeEach, afterEach } from "vitest";
import { useRetry, retryWithBackoff } from "../useRetry";
import { vi } from "vitest";

describe("useRetry", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  describe("initial state", () => {
    it("should have correct initial values", () => {
      const { attemptCount, isRetrying, lastError, countdown } = useRetry();

      expect(attemptCount.value).toBe(0);
      expect(isRetrying.value).toBe(false);
      expect(lastError.value).toBeNull();
      expect(countdown.value).toBe(0);
    });
  });

  describe("retry successful function", () => {
    it("should return result on first successful attempt", async () => {
      const { retry, attemptCount, isRetrying } = useRetry();

      const fn = vi.fn().mockResolvedValue("success");

      const result = await retry(fn);

      expect(result).toBe("success");
      expect(fn).toHaveBeenCalledTimes(1);
      expect(attemptCount.value).toBe(1);
      expect(isRetrying.value).toBe(false);
    });
  });

  describe("retry failed function", () => {
    it("should retry on failure with exponential backoff", async () => {
      vi.useRealTimers(); // Use real timers for this test

      const { retry, attemptCount } = useRetry({
        maxRetries: 3,
        initialDelay: 10, // Use short delay for testing
      });

      const fn = vi
        .fn()
        .mockRejectedValueOnce(new Error("Error 1"))
        .mockRejectedValueOnce(new Error("Error 2"))
        .mockResolvedValue("success");

      const result = await retry(fn);

      expect(result).toBe("success");
      expect(fn).toHaveBeenCalledTimes(3);
      expect(attemptCount.value).toBe(3);

      vi.useFakeTimers();
    });

    it("should throw after max retries exceeded", async () => {
      vi.useRealTimers(); // Use real timers for this test

      const { retry, attemptCount, maxRetriesReached } = useRetry({
        maxRetries: 2,
        initialDelay: 10,
      });

      const fn = vi.fn().mockRejectedValue(new Error("Always fails"));

      await expect(retry(fn)).rejects.toThrow("Always fails");
      expect(fn).toHaveBeenCalledTimes(3); // Initial + 2 retries
      expect(attemptCount.value).toBe(3);
      expect(maxRetriesReached.value).toBe(true);

      vi.useFakeTimers();
    });
  });

  describe("onRetry callback", () => {
    it("should call onRetry callback on each retry", async () => {
      vi.useRealTimers();

      const onRetry = vi.fn();
      const { retry } = useRetry({
        maxRetries: 2,
        initialDelay: 10,
        onRetry,
      });

      const fn = vi
        .fn()
        .mockRejectedValueOnce(new Error("Error 1"))
        .mockResolvedValue("success");

      await retry(fn);

      expect(onRetry).toHaveBeenCalledTimes(1);
      expect(onRetry).toHaveBeenCalledWith(expect.any(Error), 1, 10);

      vi.useFakeTimers();
    });
  });

  describe("shouldRetry option", () => {
    it("should not retry when shouldRetry returns false", async () => {
      const { retry } = useRetry({
        maxRetries: 3,
        shouldRetry: () => false,
      });

      const fn = vi.fn().mockRejectedValue(new Error("Non-retryable error"));

      await expect(retry(fn)).rejects.toThrow("Non-retryable error");
      expect(fn).toHaveBeenCalledTimes(1);
    });

    it("should retry when shouldRetry returns true", async () => {
      vi.useRealTimers();

      const { retry } = useRetry({
        maxRetries: 3,
        initialDelay: 10,
        shouldRetry: (error) =>
          error instanceof Error && error.message.includes("retryable"),
      });

      const fn = vi
        .fn()
        .mockRejectedValueOnce(new Error("retryable error"))
        .mockResolvedValue("success");

      const result = await retry(fn);

      expect(result).toBe("success");
      expect(fn).toHaveBeenCalledTimes(2);

      vi.useFakeTimers();
    });
  });

  describe("reset", () => {
    it("should reset all state", async () => {
      vi.useRealTimers();

      const { retry, reset, attemptCount, isRetrying, lastError } = useRetry({
        maxRetries: 1,
        initialDelay: 10,
      });

      const fn = vi.fn().mockRejectedValue(new Error("Error"));

      try {
        await retry(fn);
      } catch {
        // Expected to fail
      }

      expect(attemptCount.value).toBeGreaterThan(0);

      reset();

      expect(attemptCount.value).toBe(0);
      expect(isRetrying.value).toBe(false);
      expect(lastError.value).toBeNull();

      vi.useFakeTimers();
    });
  });

  describe("remainingRetries", () => {
    it("should calculate remaining retries correctly", () => {
      const { remainingRetries } = useRetry({
        maxRetries: 3,
      });

      // maxRetries + 1 for initial attempt
      expect(remainingRetries.value).toBe(4);
    });
  });
});

describe("retryWithBackoff", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("should work without state management", async () => {
    vi.useRealTimers();

    const fn = vi
      .fn()
      .mockRejectedValueOnce(new Error("Error"))
      .mockResolvedValue("success");

    const result = await retryWithBackoff(fn, {
      maxRetries: 1,
      initialDelay: 10,
    });

    expect(result).toBe("success");
    expect(fn).toHaveBeenCalledTimes(2);

    vi.useFakeTimers();
  });
});
