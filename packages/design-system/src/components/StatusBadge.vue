<script setup lang="ts">
import { Badge } from "@ulticode/design-system/ui/badge";
import { CircleCheck, CircleAlert, Info, CircleX } from "@lucide/vue";
withDefaults(
  defineProps<{
    label: string;
    variant?: "success" | "warning" | "error" | "info" | "default";
    pulse?: boolean;
  }>(),
  { variant: "default", pulse: false },
);
const icons = {
  success: CircleCheck,
  warning: CircleAlert,
  error: CircleX,
  info: Info,
};
const colors = {
  success: "text-status-success-mark bg-status-success-surface",
  warning: "text-status-warning-mark bg-status-warning-surface",
  error: "text-status-error-mark bg-status-error-surface",
  info: "text-status-info-mark bg-status-info-surface",
  default: "",
};
</script>
<template>
  <Badge variant="secondary" :class="colors[variant]">
    <component
      :is="icons[variant]"
      v-if="variant !== 'default'"
      aria-hidden="true"
    />
    {{ label }}
  </Badge>
</template>
