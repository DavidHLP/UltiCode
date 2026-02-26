<script setup lang="ts">
import { computed } from "vue";
import { useI18n } from "vue-i18n";
import { RefreshCw } from "lucide-vue-next";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

const props = defineProps<{
  /** Whether currently retrying */
  retrying?: boolean;
  /** Current retry attempt (1-indexed) */
  attempt?: number;
  /** Maximum retry attempts */
  maxAttempts?: number;
  /** Countdown seconds until next retry */
  countdown?: number;
  /** Button variant */
  variant?: "default" | "outline" | "ghost" | "destructive";
  /** Button size */
  size?: "default" | "sm" | "lg" | "icon";
  /** Disable the button */
  disabled?: boolean;
  /** Show countdown text */
  showCountdown?: boolean;
  /** Additional CSS classes */
  class?: string;
}>();

const emit = defineEmits<{
  /** Emitted when retry button is clicked */
  retry: [];
}>();

const { t } = useI18n();

// Compute button state
const isDisabled = computed(() => {
  return (
    props.disabled || props.retrying || (props.countdown && props.countdown > 0)
  );
});

// Compute button text
const buttonText = computed(() => {
  if (props.retrying) {
    return t("common.status.processing");
  }

  if (props.countdown && props.countdown > 0 && props.showCountdown !== false) {
    return t("common.actions.retry") + ` (${props.countdown}s)`;
  }

  if (props.attempt && props.maxAttempts) {
    return `${t("common.actions.retry")} (${props.attempt}/${props.maxAttempts})`;
  }

  return t("common.actions.retry");
});

// Handle click
function handleClick() {
  if (!isDisabled.value) {
    emit("retry");
  }
}
</script>

<template>
  <Button
    :variant="variant ?? 'outline'"
    :size="size ?? 'default'"
    :disabled="isDisabled"
    :class="cn('gap-2', props.class)"
    @click="handleClick"
  >
    <RefreshCw :class="['size-4', retrying && 'animate-spin']" />
    <slot>{{ buttonText }}</slot>
  </Button>
</template>
