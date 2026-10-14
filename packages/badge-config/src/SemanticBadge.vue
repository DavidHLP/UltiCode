<script setup lang="ts">
import { Badge } from "@ulticode/design-system/ui/badge";
import { CircleCheck, CircleAlert, CircleX, Info } from "@lucide/vue";
import type { SemanticColor } from "./semantic-colors";
withDefaults(
  defineProps<{
    color: SemanticColor;
    label?: string;
    dot?: boolean;
    pulse?: boolean;
    size?: "xs" | "sm" | "md";
  }>(),
  { label: "", dot: false, pulse: false, size: "md" },
);
const colors: Record<SemanticColor, string> = {
  success: "bg-status-success-surface text-status-success-mark",
  warning: "bg-status-warning-surface text-status-warning-mark",
  error: "bg-status-error-surface text-status-error-mark",
  info: "bg-status-info-surface text-status-info-mark",
  purple: "bg-status-special-surface text-status-special-mark",
  electric: "bg-status-info-surface text-status-info-mark",
  neutral: "",
};
const icons = {
  success: CircleCheck,
  warning: CircleAlert,
  error: CircleX,
  info: Info,
  purple: Info,
  electric: Info,
  neutral: undefined,
};
</script>
<template>
  <Badge variant="secondary" :class="colors[color]"
    ><component
      :is="icons[color]"
      v-if="icons[color]"
      aria-hidden="true"
    /><slot>{{ label }}</slot></Badge
  >
</template>
