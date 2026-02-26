<script setup lang="ts">
import { computed } from "vue";
import { useLoading } from "@/composables/useLoading";
import { useI18n } from "vue-i18n";
import { Spinner } from "@/components/ui/spinner";

const props = defineProps<{
  /** Override loading state (uses global state if not provided) */
  loading?: boolean;
  /** Custom loading message */
  message?: string;
  /** Show overlay as transparent */
  transparent?: boolean;
  /** Size of the spinner */
  spinnerSize?: "sm" | "md" | "lg";
  /** Additional CSS classes */
  class?: string;
  /** Z-index for the overlay */
  zIndex?: number;
}>();

const { t } = useI18n();
const { isLoading: globalIsLoading, loadingMessage: globalMessage } =
  useLoading();

// Use provided loading state or global state
const isLoading = computed(() => {
  if (props.loading !== undefined) {
    return props.loading;
  }
  return globalIsLoading.value;
});

// Use provided message or global message
const message = computed(() => {
  if (props.message) {
    return props.message;
  }
  return globalMessage.value || t("common.status.loading");
});

// Spinner size classes
const spinnerSizeClasses = {
  sm: "size-6",
  md: "size-8",
  lg: "size-12",
};

// Z-index for overlay
const overlayZIndex = computed(() => props.zIndex ?? 50);
</script>

<template>
  <Teleport to="body">
    <Transition
      enter-active-class="transition-opacity duration-200"
      enter-from-class="opacity-0"
      enter-to-class="opacity-100"
      leave-active-class="transition-opacity duration-200"
      leave-from-class="opacity-100"
      leave-to-class="opacity-0"
    >
      <div
        v-if="isLoading"
        :class="[
          'fixed inset-0 flex items-center justify-center',
          transparent ? 'bg-transparent' : 'bg-background/80 backdrop-blur-sm',
          props.class,
        ]"
        :style="{ zIndex: overlayZIndex }"
        role="alert"
        aria-busy="true"
        aria-live="polite"
      >
        <div class="flex flex-col items-center gap-4">
          <Spinner :class="spinnerSizeClasses[spinnerSize ?? 'md']" />
          <p v-if="message" class="text-sm text-muted-foreground animate-pulse">
            {{ message }}
          </p>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>
