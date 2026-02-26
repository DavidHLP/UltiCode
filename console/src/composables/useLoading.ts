import { ref, computed, type Ref } from "vue";

/**
 * Loading state for a single operation
 */
interface LoadingOperation {
  id: string;
  message?: string;
  startedAt: number;
}

/**
 * Options for loading state management
 */
export interface UseLoadingOptions {
  /** Default message to show when loading */
  defaultMessage?: string;
  /** Auto-clear loading after timeout (ms) */
  timeout?: number;
}

/**
 * Global loading state store
 */
const globalLoadingOperations = ref<LoadingOperation[]>([]);

/**
 * Composable for managing loading states across the application
 *
 * Features:
 * - Track multiple concurrent loading operations
 * - Global and local loading states
 * - Customizable loading messages
 * - Automatic timeout cleanup
 *
 * @param options - Configuration options
 * @returns Loading state utilities
 */
export function useLoading(options: UseLoadingOptions = {}) {
  const { defaultMessage = "Loading...", timeout } = options;

  // Local loading operations for this composable instance
  const localOperations = ref<Set<string>>(new Set());

  /**
   * Check if any global loading operation is active
   */
  const isLoading = computed(() => globalLoadingOperations.value.length > 0);

  /**
   * Get the current loading message (from the most recent operation)
   */
  const loadingMessage = computed(() => {
    const operations = globalLoadingOperations.value;
    const latest =
      operations.length > 0 ? operations[operations.length - 1] : undefined;
    return latest?.message || defaultMessage;
  });

  /**
   * Check if a specific operation is loading
   */
  function isOperationLoading(operationId: string): boolean {
    return globalLoadingOperations.value.some((op) => op.id === operationId);
  }

  /**
   * Start a loading operation
   *
   * @param operationId - Unique identifier for this operation
   * @param message - Optional custom message
   */
  function startLoading(operationId: string, message?: string): void {
    // Don't add duplicate operations
    if (isOperationLoading(operationId)) {
      return;
    }

    const operation: LoadingOperation = {
      id: operationId,
      message: message || defaultMessage,
      startedAt: Date.now(),
    };

    globalLoadingOperations.value = [
      ...globalLoadingOperations.value,
      operation,
    ];
    localOperations.value.add(operationId);

    // Set up timeout if configured
    if (timeout) {
      setTimeout(() => {
        stopLoading(operationId);
      }, timeout);
    }
  }

  /**
   * Stop a loading operation
   *
   * @param operationId - The operation to stop
   */
  function stopLoading(operationId: string): void {
    globalLoadingOperations.value = globalLoadingOperations.value.filter(
      (op) => op.id !== operationId,
    );
    localOperations.value.delete(operationId);
  }

  /**
   * Stop all loading operations
   */
  function stopAllLoading(): void {
    globalLoadingOperations.value = [];
    localOperations.value.clear();
  }

  /**
   * Stop all local loading operations (started by this composable instance)
   */
  function stopLocalLoading(): void {
    for (const operationId of localOperations.value) {
      globalLoadingOperations.value = globalLoadingOperations.value.filter(
        (op) => op.id !== operationId,
      );
    }
    localOperations.value.clear();
  }

  /**
   * Update the message for a running operation
   *
   * @param operationId - The operation to update
   * @param message - New message
   */
  function updateMessage(operationId: string, message: string): void {
    globalLoadingOperations.value = globalLoadingOperations.value.map((op) =>
      op.id === operationId ? { ...op, message } : op,
    );
  }

  /**
   * Wrap an async function with loading state management
   *
   * @param operationId - Unique identifier for this operation
   * @param fn - Async function to execute
   * @param message - Optional loading message
   * @returns Result of the async function
   */
  async function withLoading<T>(
    operationId: string,
    fn: () => Promise<T>,
    message?: string,
  ): Promise<T> {
    try {
      startLoading(operationId, message);
      return await fn();
    } finally {
      stopLoading(operationId);
    }
  }

  /**
   * Create a reusable loading wrapper for a specific operation
   *
   * @param operationId - Unique identifier for this operation
   * @param defaultMessage - Default message for this operation
   * @returns Wrapped function with loading state
   */
  function createLoadingWrapper<
    T extends (...args: unknown[]) => Promise<unknown>,
  >(operationId: string, defaultMessage?: string) {
    return async (fn: T, message?: string): Promise<ReturnType<T>> => {
      return withLoading(
        operationId,
        fn as () => Promise<ReturnType<T>>,
        message || defaultMessage,
      ) as Promise<ReturnType<T>>;
    };
  }

  /**
   * Get loading duration for an operation
   *
   * @param operationId - The operation to check
   * @returns Duration in milliseconds or 0 if not loading
   */
  function getLoadingDuration(operationId: string): number {
    const operation = globalLoadingOperations.value.find(
      (op) => op.id === operationId,
    );
    return operation ? Date.now() - operation.startedAt : 0;
  }

  /**
   * Reactive loading state for a specific operation
   */
  function useOperationLoading(operationId: string): Ref<boolean> {
    return computed(() => isOperationLoading(operationId));
  }

  return {
    // State
    isLoading,
    loadingMessage,
    operations: globalLoadingOperations,

    // Actions
    startLoading,
    stopLoading,
    stopAllLoading,
    stopLocalLoading,
    updateMessage,

    // Helpers
    withLoading,
    createLoadingWrapper,
    isOperationLoading,
    getLoadingDuration,
    useOperationLoading,
  };
}
