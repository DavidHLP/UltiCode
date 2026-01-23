import { useI18n } from "vue-i18n";

export interface ErrorHandlerOptions {
  fallbackMessage?: string;
  logToConsole?: boolean;
  showToast?: boolean;
  showDetailedMessage?: boolean;
  resetState?: () => void;
}

/**
 * Error handler composable for consistent error handling across the application
 *
 * Features:
 * - Automatic toast notifications using vue-sonner
 * - i18n support for localized error messages
 * - Optional console logging for debugging
 * - Configurable fallback messages
 *
 * @returns Error handling utilities
 */
export function useErrorHandler() {
  const { t } = useI18n();

  /**
   * Handle errors with consistent behavior
   *
   * @param error - The error object (can be any type)
   * @param options - Configuration options for error handling
   */
  async function handleError(
    error: unknown,
    options: ErrorHandlerOptions = {},
  ) {
    const {
      fallbackMessage = "common.error.default",
      logToConsole = false,
      showToast = true,
      showDetailedMessage = false,
      resetState,
    } = options;

    // Log to console if requested
    if (logToConsole) {
      console.error("Error occurred:", error);
    }

    // Extract error message
    let detailedMessage = "";

    if (error instanceof Error) {
      detailedMessage = error.message;
    } else if (typeof error === "string") {
      detailedMessage = error;
    } else if (error && typeof error === "object" && "message" in error) {
      detailedMessage = String(error.message);
    }

    // Show toast notification if enabled
    if (showToast) {
      const { toast } = await import("vue-sonner");

      const message =
        showDetailedMessage && detailedMessage
          ? `${t(fallbackMessage)}: ${detailedMessage}`
          : t(fallbackMessage);

      toast.error(message, {
        duration: 5000,
        position: "top-right",
      });
    }

    // Call resetState callback if provided
    if (resetState) {
      resetState();
    }

    // Return the error for further handling if needed
    return error;
  }

  /**
   * Handle async errors in try-catch blocks
   *
   * @param fn - Async function to execute
   * @param options - Error handling options
   * @returns Result or null if error occurred
   */
  async function handleAsync<T>(
    fn: () => Promise<T>,
    options: ErrorHandlerOptions = {},
  ): Promise<T | null> {
    try {
      return await fn();
    } catch (error) {
      await handleError(error, options);
      return null;
    }
  }

  /**
   * Create a wrapped version of an async function with error handling
   *
   * @param fn - Async function to wrap
   * @param options - Error handling options
   * @returns Wrapped function with error handling
   */
  function withErrorHandler<T extends (...args: unknown[]) => Promise<unknown>>(
    fn: T,
    options: ErrorHandlerOptions = {},
  ): T {
    return (async (...args: Parameters<T>) => {
      try {
        return await fn(...args);
      } catch (error) {
        await handleError(error, options);
        return null;
      }
    }) as T;
  }

  return {
    handleError,
    handleAsync,
    withErrorHandler,
  };
}
