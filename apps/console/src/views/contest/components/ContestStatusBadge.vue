<script setup lang="ts">
/**
 * ContestStatusBadge - Displays contest status with  colors
 *
 * Uses SemanticBadge for consistent inline-flex items-center gap-1 rounded-lg border px-2 py-0.5 text-xs styling.
 */
import { computed } from "vue";
import { useI18n } from "vue-i18n";
import SemanticBadge from "@ulticode/badge-config/SemanticBadge.vue";
import {
  CONTEST_STATUS_COLOR_MAP,
  type SemanticColor,
} from "@ulticode/badge-config";

const props = defineProps<{
  status: string;
  size?: "sm" | "md" | "lg";
}>();

const { t } = useI18n();

const STATUS_LABELS: Record<string, string> = {
  DRAFT: "contest.status.draft",
  UPCOMING: "contest.status.upcoming",
  RUNNING: "contest.status.running",
  FINISHED: "contest.status.finished",
  CANCELLED: "contest.status.cancelled",
};

const color = computed<SemanticColor>(
  () => CONTEST_STATUS_COLOR_MAP[props.status] ?? "neutral",
);

const label = computed(() => {
  const key = STATUS_LABELS[props.status];
  return key ? t(key, props.status) : props.status;
});

const pulse = computed(() => props.status === "RUNNING");

const badgeSize = computed(() => {
  if (props.size === "sm") return "xs" as const;
  if (props.size === "lg") return "md" as const;
  return "sm" as const;
});
</script>

<template>
  <SemanticBadge
    :color="color"
    :label="label"
    :pulse="pulse"
    :size="badgeSize"
  />
</template>
