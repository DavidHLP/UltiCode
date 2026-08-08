<script setup lang="ts">
import { ref, onErrorCaptured, type ComponentPublicInstance } from "vue";
import { useI18n } from "vue-i18n";
import { AlertTriangle, RefreshCw, Home, Bug } from "lucide-vue-next";
import { Button } from "@/components/ui/button";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";

const props = defineProps<{
  /** Fallback component to render on error */
  fallback?: ComponentPublicInstance;
  /** Show detailed error information */
  showDetails?: boolean;
  /** Custom error handler */
  onError?: (
    error: Error,
    instance: ComponentPublicInstance | null,
    info: string,
  ) => void;
  /** Additional CSS classes */
  class?: string;
}>();

const emit = defineEmits<{
  /** Emitted when an error is captured */
  error: [error: Error, instance: ComponentPublicInstance | null, info: string];
  /** Emitted when retry is clicked */
  retry: [];
}>();

const { t } = useI18n();

// Error state
const hasError = ref(false);
const capturedError = ref<Error | null>(null);
const errorInfo = ref<string>("");
const showExpandedDetails = ref(false);

// Capture errors from child components
onErrorCaptured(
  (error: Error, instance: ComponentPublicInstance | null, info: string) => {
    hasError.value = true;
    capturedError.value = error;
    errorInfo.value = info;

    // Call custom error handler
    if (props.onError) {
      props.onError(error, instance, info);
    }

    // Emit error event
    emit("error", error, instance, info);

    // Log to console in development
    if (import.meta.env.DEV) {
      console.error("Error captured by ErrorBoundary:", error);
      console.error("Component:", instance?.$options?.name || "Unknown");
      console.error("Error info:", info);
    }

    // Return false to prevent the error from propagating
    return false;
  },
);

// Retry handler
function handleRetry() {
  hasError.value = false;
  capturedError.value = null;
  errorInfo.value = "";
  showExpandedDetails.value = false;
  emit("retry");
}

// Navigate to home
function goHome() {
  window.location.href = "/";
}

// Get error stack for display
const errorStack = computed(() => {
  if (!capturedError.value) return null;
  return capturedError.value.stack || capturedError.value.message;
});

// Truncate stack for display
const displayStack = computed(() => {
  const stack = errorStack.value;
  if (!stack) return null;

  if (showExpandedDetails.value) {
    return stack;
  }

  // Show first 5 lines
  const lines = stack.split("\n").slice(0, 5);
  return lines.join("\n") + (stack.split("\n").length > 5 ? "\n..." : "");
});

// Toggle details
function toggleDetails() {
  showExpandedDetails.value = !showExpandedDetails.value;
}

// Import computed
import { computed } from "vue";
</script>

<template>
  <!-- Render fallback or error UI if error occurred -->
  <template v-if="hasError">
    <slot name="fallback">
      <div
        :class="[
          'flex flex-col items-center justify-center p-8 min-h-[200px]',
          props.class,
        ]"
      >
        <Alert variant="destructive" class="max-w-lg">
          <AlertTriangle class="size-4" />
          <AlertTitle>{{ t("common.error.title") }}</AlertTitle>
          <AlertDescription class="mt-2">
            {{ t("common.error.boundaryMessage") }}
          </AlertDescription>

          <!-- Error details (if enabled) -->
          <template v-if="showDetails && displayStack">
            <div class="mt-4">
              <Button
                variant="ghost"
                size="sm"
                class="h-auto p-1 text-xs"
                @click="toggleDetails"
              >
                <Bug class="size-3 mr-1" />
                {{
                  showExpandedDetails
                    ? t("common.error.hideDetails")
                    : t("common.error.showDetails")
                }}
              </Button>
              <pre
                v-if="showExpandedDetails"
                class="mt-2 p-2 bg-muted rounded text-xs overflow-auto max-h-[200px]"
                >{{ displayStack }}</pre
              >
            </div>
          </template>

          <!-- Actions -->
          <div class="flex gap-2 mt-4">
            <Button variant="outline" size="sm" @click="handleRetry">
              <RefreshCw class="size-3 mr-1" />
              {{ t("common.actions.retry") }}
            </Button>
            <Button variant="ghost" size="sm" @click="goHome">
              <Home class="size-3 mr-1" />
              {{ t("common.actions.back") }}
            </Button>
          </div>
        </Alert>
      </div>
    </slot>
  </template>

  <!-- Render children if no error -->
  <slot v-else />
</template>
