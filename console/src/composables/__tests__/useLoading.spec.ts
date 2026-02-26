import { describe, it, expect, beforeEach, vi } from "vitest";
import { useLoading } from "../useLoading";

// Mock timers
vi.useFakeTimers();

describe("useLoading", () => {
  beforeEach(() => {
    // Reset global state between tests
    const { stopAllLoading } = useLoading();
    stopAllLoading();
  });

  describe("initial state", () => {
    it("should not be loading initially", () => {
      const { isLoading } = useLoading();
      expect(isLoading.value).toBe(false);
    });

    it("should return default message initially", () => {
      const { loadingMessage } = useLoading();
      expect(loadingMessage.value).toBe("Loading...");
    });
  });

  describe("startLoading", () => {
    it("should set isLoading to true", () => {
      const { isLoading, startLoading } = useLoading();

      startLoading("test-operation");

      expect(isLoading.value).toBe(true);
    });

    it("should set custom message", () => {
      const { loadingMessage, startLoading } = useLoading();

      startLoading("test-operation", "Custom loading message");

      expect(loadingMessage.value).toBe("Custom loading message");
    });

    it("should not add duplicate operations", () => {
      const { operations, startLoading } = useLoading();

      startLoading("test-operation");
      startLoading("test-operation");

      expect(operations.value).toHaveLength(1);
    });

    it("should track multiple operations", () => {
      const { operations, startLoading } = useLoading();

      startLoading("operation-1");
      startLoading("operation-2");
      startLoading("operation-3");

      expect(operations.value).toHaveLength(3);
    });
  });

  describe("stopLoading", () => {
    it("should set isLoading to false when all operations stop", () => {
      const { isLoading, startLoading, stopLoading } = useLoading();

      startLoading("test-operation");
      expect(isLoading.value).toBe(true);

      stopLoading("test-operation");
      expect(isLoading.value).toBe(false);
    });

    it("should keep loading true when other operations exist", () => {
      const { isLoading, startLoading, stopLoading } = useLoading();

      startLoading("operation-1");
      startLoading("operation-2");
      expect(isLoading.value).toBe(true);

      stopLoading("operation-1");
      expect(isLoading.value).toBe(true);
    });

    it("should not error when stopping non-existent operation", () => {
      const { stopLoading } = useLoading();

      expect(() => stopLoading("non-existent")).not.toThrow();
    });
  });

  describe("stopAllLoading", () => {
    it("should stop all loading operations", () => {
      const { isLoading, operations, startLoading, stopAllLoading } =
        useLoading();

      startLoading("operation-1");
      startLoading("operation-2");
      startLoading("operation-3");

      expect(operations.value).toHaveLength(3);

      stopAllLoading();

      expect(isLoading.value).toBe(false);
      expect(operations.value).toHaveLength(0);
    });
  });

  describe("updateMessage", () => {
    it("should update message for a running operation", () => {
      const { loadingMessage, startLoading, updateMessage } = useLoading();

      startLoading("test-operation", "Initial message");
      expect(loadingMessage.value).toBe("Initial message");

      updateMessage("test-operation", "Updated message");
      expect(loadingMessage.value).toBe("Updated message");
    });
  });

  describe("isOperationLoading", () => {
    it("should return true for active operation", () => {
      const { startLoading, isOperationLoading } = useLoading();

      startLoading("test-operation");

      expect(isOperationLoading("test-operation")).toBe(true);
      expect(isOperationLoading("other-operation")).toBe(false);
    });
  });

  describe("withLoading", () => {
    it("should wrap async function with loading state", async () => {
      const { isLoading, withLoading } = useLoading();

      const asyncFn = vi.fn().mockImplementation(async () => {
        expect(isLoading.value).toBe(true);
        return "result";
      });

      const result = await withLoading("test-operation", asyncFn);

      expect(result).toBe("result");
      expect(isLoading.value).toBe(false);
      expect(asyncFn).toHaveBeenCalled();
    });

    it("should stop loading even on error", async () => {
      const { isLoading, withLoading } = useLoading();

      const asyncFn = vi.fn().mockRejectedValue(new Error("Test error"));

      await expect(withLoading("test-operation", asyncFn)).rejects.toThrow(
        "Test error",
      );

      expect(isLoading.value).toBe(false);
    });
  });

  describe("timeout option", () => {
    it("should auto-clear loading after timeout", () => {
      const { isLoading, startLoading } = useLoading({ timeout: 5000 });

      startLoading("test-operation");
      expect(isLoading.value).toBe(true);

      vi.advanceTimersByTime(5000);

      expect(isLoading.value).toBe(false);
    });
  });

  describe("defaultMessage option", () => {
    it("should use custom default message", () => {
      const { loadingMessage } = useLoading({
        defaultMessage: "Custom default",
      });

      expect(loadingMessage.value).toBe("Custom default");
    });
  });

  describe("useOperationLoading", () => {
    it("should return reactive loading state for operation", () => {
      const { startLoading, stopLoading, useOperationLoading } = useLoading();

      const operationLoading = useOperationLoading("test-operation");

      expect(operationLoading.value).toBe(false);

      startLoading("test-operation");
      expect(operationLoading.value).toBe(true);

      stopLoading("test-operation");
      expect(operationLoading.value).toBe(false);
    });
  });
});
