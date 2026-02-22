import type { App, ComponentPublicInstance } from "vue";
import type { Router } from "vue-router";

/**
 * Error handler configuration
 */
export interface ErrorHandlerOptions {
  /** Router instance for navigation on error */
  router?: Router;
  /** Log errors to console */
  logToConsole?: boolean;
  /** Show toast notifications on error */
  showToast?: boolean;
  /** Callback when error occurs */
  onError?: (error: Error, instance: ComponentPublicInstance | null, info: string) => void;
  /** Custom error page route */
  errorRoute?: string;
}

/**
 * Error information for logging
 */
interface ErrorLog {
  timestamp: Date;
  error: Error;
  componentStack?: string;
  url?: string;
  userAgent?: string;
}

// Store recent errors for debugging
const errorLog: ErrorLog[] = [];
const MAX_ERROR_LOG_SIZE = 50;

/**
 * Log an error
 */
function logError(error: Error, info?: string): void {
  const logEntry: ErrorLog = {
    timestamp: new Date(),
    error,
    componentStack: info,
    url: window.location.href,
    userAgent: navigator.userAgent,
  };

  errorLog.push(logEntry);

  // Keep only recent errors
  if (errorLog.length > MAX_ERROR_LOG_SIZE) {
    errorLog.shift();
  }
}

/**
 * Get recent error logs
 */
export function getErrorLog(): ErrorLog[] {
  return [...errorLog];
}

/**
 * Clear error logs
 */
export function clearErrorLog(): void {
  errorLog.length = 0;
}

/**
 * Extract error message from various error types
 */
function getErrorMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }
  if (typeof error === "string") {
    return error;
  }
  if (error && typeof error === "object" && "message" in error) {
    return String((error as { message: unknown }).message);
  }
  return "An unknown error occurred";
}

/**
 * Check if error is a navigation error
 */
function isNavigationError(error: Error): boolean {
  return (
    error.message.includes("Failed to resolve async component") ||
    error.message.includes("Unable to resolve") ||
    error.name === "NavigationDuplicated" ||
    error.name === "NavigationCancelled"
  );
}

/**
 * Show error toast notification
 */
async function showErrorToast(message: string): Promise<void> {
  try {
    const { toast } = await import("vue-sonner");
    toast.error(message, {
      duration: 5000,
      position: "top-right",
    });
  } catch {
    // Toast library not available, fall back to console
    console.error("Error:", message);
  }
}

/**
 * Create Vue error handler plugin
 */
export function createErrorHandler(options: ErrorHandlerOptions = {}) {
  const {
    logToConsole = true,
    showToast = true,
    onError,
  } = options;

  return {
    install(app: App) {
      // Global error handler for Vue
      app.config.errorHandler = (
        err: unknown,
        instance: ComponentPublicInstance | null,
        info: string,
      ) => {
        const error = err instanceof Error ? err : new Error(getErrorMessage(err));

        // Log the error
        logError(error, info);

        if (logToConsole) {
          console.error("Vue Error:", error);
          console.error("Component:", instance?.$options?.name || "Unknown");
          console.error("Error Info:", info);
        }

        // Call custom error handler
        if (onError) {
          onError(error, instance, info);
        }

        // Show toast notification
        if (showToast && !isNavigationError(error)) {
          showErrorToast(getErrorMessage(error));
        }
      };

      // Global warning handler
      app.config.warnHandler = (msg: string, instance: ComponentPublicInstance | null, trace: string) => {
        if (logToConsole) {
          console.warn("Vue Warning:", msg);
          console.warn("Component:", instance?.$options?.name || "Unknown");
          console.warn("Trace:", trace);
        }
      };

      // Handle unhandled promise rejections
      window.addEventListener("unhandledrejection", (event) => {
        const error = event.reason instanceof Error
          ? event.reason
          : new Error(getErrorMessage(event.reason));

        logError(error);

        if (logToConsole) {
          console.error("Unhandled Promise Rejection:", error);
        }

        if (onError) {
          onError(error, null, "unhandledrejection");
        }

        if (showToast) {
          showErrorToast(getErrorMessage(event.reason));
        }

        // Prevent default browser error logging (we handled it)
        event.preventDefault();
      });

      // Handle global JavaScript errors
      window.addEventListener("error", (event) => {
        const error = event.error instanceof Error
          ? event.error
          : new Error(event.message);

        logError(error);

        if (logToConsole) {
          console.error("Global Error:", error);
        }

        if (onError) {
          onError(error, null, "global");
        }

        // Don't show toast for script loading errors
        if (showToast && !event.filename) {
          showErrorToast(event.message);
        }
      });
    },
  };
}

/**
 * Vue plugin for error handling
 */
export default {
  install: (app: App, options?: ErrorHandlerOptions) => {
    const handler = createErrorHandler(options);
    handler.install(app);
  },
};
