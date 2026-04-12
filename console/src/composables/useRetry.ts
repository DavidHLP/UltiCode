import { ref, computed, onScopeDispose, type Ref } from "vue";

/**
 * Retry configuration options
 */
export interface RetryOptions {
  /** Maximum number of retry attempts (default: 3) */
  maxRetries?: number;
  /** Initial delay in milliseconds (default: 1000) */
  initialDelay?: number;
  /** Maximum delay in milliseconds (default: 30000) */
  maxDelay?: number;
  /** Backoff multiplier (default: 2) */
  backoffMultiplier?: number;
  /** Function to determine if error should trigger retry */
  shouldRetry?: (error: unknown, attempt: number) => boolean;
  /** Callback on each retry attempt */
  onRetry?: (error: unknown, attempt: number, delay: number) => void;
}

/**
 * Default retry options
 */
const DEFAULT_OPTIONS: Required<Omit<RetryOptions, "shouldRetry" | "onRetry">> =
  {
    maxRetries: 3,
    initialDelay: 1000,
    maxDelay: 30000,
    backoffMultiplier: 2,
  };

/**
 * Calculate delay with exponential backoff
 */
function calculateDelay(
  attempt: number,
  initialDelay: number,
  maxDelay: number,
  multiplier: number,
): number {
  const delay = initialDelay * Math.pow(multiplier, attempt - 1);
  return Math.min(delay, maxDelay);
}

/**
 * Sleep for a specified duration
 */
function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/**
 * Composable for retry logic with exponential backoff
 *
 * Features:
 * - Exponential backoff with configurable parameters
 * - Maximum retry attempts
 * - Custom retry conditions
 * - Progress tracking
 * - Countdown timer
 *
 * @param options - Retry configuration
 * @returns Retry utilities
 */
export function useRetry(options: RetryOptions = {}) {
  const mergedOptions = { ...DEFAULT_OPTIONS, ...options };

  // State
  const attemptCount = ref(0);
  const isRetrying = ref(false);
  const lastError: Ref<unknown> = ref(null);
  const nextRetryDelay = ref(0);
  const countdown = ref(0);

  // Countdown interval
  let countdownInterval: ReturnType<typeof setInterval> | null = null;

  onScopeDispose(() => {
    clearCountdown();
  });

  /**
   * Clear countdown interval
   */
  function clearCountdown(): void {
    if (countdownInterval) {
      clearInterval(countdownInterval);
      countdownInterval = null;
    }
  }

  /**
   * Start countdown timer
   */
  function startCountdown(delay: number): void {
    clearCountdown();
    countdown.value = Math.ceil(delay / 1000);
    nextRetryDelay.value = delay;

    countdownInterval = setInterval(() => {
      countdown.value = Math.max(0, countdown.value - 1);
      if (countdown.value <= 0) {
        clearCountdown();
      }
    }, 1000);
  }

  /**
   * Check if an error should trigger a retry
   */
  function shouldRetryError(error: unknown, attempt: number): boolean {
    if (options.shouldRetry) {
      return options.shouldRetry(error, attempt);
    }

    // Default: retry on network errors and 5xx responses
    if (error instanceof TypeError && error.message.includes("network")) {
      return true;
    }

    if (error && typeof error === "object" && "status" in error) {
      const status = (error as { status: number }).status;
      return status >= 500 && status < 600;
    }

    return true;
  }

  /**
   * Execute a function with retry logic
   *
   * @param fn - Async function to execute
   * @returns Result of the function
   */
  async function retry<T>(fn: () => Promise<T>): Promise<T> {
    const { maxRetries, initialDelay, maxDelay, backoffMultiplier, onRetry } =
      mergedOptions;

    attemptCount.value = 0;
    isRetrying.value = false;
    lastError.value = null;

    let lastAttemptError: unknown = null;

    for (let attempt = 1; attempt <= maxRetries + 1; attempt++) {
      attemptCount.value = attempt;

      try {
        const result = await fn();
        // Reset state on success
        isRetrying.value = false;
        lastError.value = null;
        clearCountdown();
        return result;
      } catch (error) {
        lastAttemptError = error;
        lastError.value = error;

        // Check if we should retry
        if (attempt <= maxRetries && shouldRetryError(error, attempt)) {
          isRetrying.value = true;
          const delay = calculateDelay(
            attempt,
            initialDelay,
            maxDelay,
            backoffMultiplier,
          );

          startCountdown(delay);

          // Call onRetry callback
          if (onRetry) {
            onRetry(error, attempt, delay);
          }

          await sleep(delay);
        } else {
          // No more retries
          isRetrying.value = false;
          clearCountdown();
          throw error;
        }
      }
    }

    // This should never be reached, but TypeScript needs it
    throw lastAttemptError;
  }

  /**
   * Create a retry wrapper for a function
   *
   * @param fn - Function to wrap
   * @param overrideOptions - Options to override
   * @returns Wrapped function with retry logic
   */
  function withRetry<T extends (...args: unknown[]) => Promise<unknown>>(
    fn: T,
    overrideOptions?: RetryOptions,
  ): T {
    return (async (...args: Parameters<T>) => {
      const retryFn = useRetry({ ...options, ...overrideOptions });
      return retryFn.retry(() => fn(...args));
    }) as T;
  }

  /**
   * Reset retry state
   */
  function reset(): void {
    attemptCount.value = 0;
    isRetrying.value = false;
    lastError.value = null;
    nextRetryDelay.value = 0;
    countdown.value = 0;
    clearCountdown();
  }

  /**
   * Check if max retries reached
   */
  const maxRetriesReached = computed(
    () => attemptCount.value >= mergedOptions.maxRetries,
  );

  /**
   * Remaining retry attempts
   */
  const remainingRetries = computed(() =>
    Math.max(0, mergedOptions.maxRetries - attemptCount.value + 1),
  );

  return {
    // State
    attemptCount,
    isRetrying,
    lastError,
    nextRetryDelay,
    countdown,
    maxRetriesReached,
    remainingRetries,

    // Actions
    retry,
    withRetry,
    reset,

    // Utilities
    shouldRetryError,
  };
}

/**
 * Simple retry function without state management
 *
 * @param fn - Function to execute
 * @param options - Retry options
 * @returns Result of the function
 */
export async function retryWithBackoff<T>(
  fn: () => Promise<T>,
  options: RetryOptions = {},
): Promise<T> {
  const { retry } = useRetry(options);
  return retry(fn);
}
