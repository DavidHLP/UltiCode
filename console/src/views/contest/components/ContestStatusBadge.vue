<script setup lang="ts">
/**
 * ContestStatusBadge - Displays contest status with appropriate styling
 *
 * Shows status badge with colors for each contest lifecycle stage:
 * - DRAFT: Gray (not ready)
 * - UPCOMING: Yellow (registered, waiting for start)
 * - RUNNING: Red (contest in progress)
 * - FINISHED: Gray (ended)
 * - CANCELLED: Muted (cancelled)
 */
import { computed } from "vue";
import { Badge } from "@/components/ui/badge";
import { useI18n } from "vue-i18n";
import { ContestStatus } from "@/types/contest";

const props = defineProps<{
  status: ContestStatus | string;
  size?: "sm" | "md" | "lg";
}>();

const { t } = useI18n();

type BadgeVariant = "default" | "secondary" | "destructive" | "outline";

interface StatusConfig {
  variant: BadgeVariant;
  label: string;
  customClass: string;
}

const statusConfig = computed((): StatusConfig => {
  const defaultConfig: StatusConfig = {
    variant: "secondary",
    label: t("contest.status.draft", "Draft"),
    customClass: "",
  };

  const configs: Record<string, StatusConfig> = {
    DRAFT: defaultConfig,
    UPCOMING: {
      variant: "outline",
      label: t("contest.status.upcoming", "Upcoming"),
      customClass:
        "bg-[oklch(0.6545_0.1340_85.7_/_0.12)] text-[var(--terminal-amber)] border-[var(--terminal-amber)]/30",
    },
    RUNNING: {
      variant: "outline",
      label: t("contest.status.running", "Running"),
      customClass:
        "bg-[var(--terminal-red)]/10 text-[var(--terminal-red)] border-[var(--terminal-red)]/30 dark:bg-[var(--terminal-red)]/10 dark:text-[var(--terminal-red)] dark:border-[var(--terminal-red)]/30",
    },
    FINISHED: {
      variant: "outline",
      label: t("contest.status.finished", "Finished"),
      customClass:
        "bg-muted text-muted-foreground border-border",
    },
    CANCELLED: {
      variant: "secondary",
      label: t("contest.status.cancelled", "Cancelled"),
      customClass: "",
    },
  };

  return configs[props.status] ?? defaultConfig;
});

const sizeClasses = computed(() => {
  switch (props.size) {
    case "sm":
      return "text-[10px] px-1.5 py-0.5";
    case "lg":
      return "text-sm px-3 py-1";
    default:
      return "text-xs px-2 py-0.5";
  }
});
</script>

<template>
  <Badge
    :class="[sizeClasses, statusConfig.customClass]"
    :variant="statusConfig.variant"
  >
    {{ statusConfig.label }}
  </Badge>
</template>
