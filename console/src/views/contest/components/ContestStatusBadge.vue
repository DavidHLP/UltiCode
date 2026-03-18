<script setup lang="ts">
/**
 * ContestStatusBadge - Displays contest status with appropriate styling
 *
 * Shows status badge with colors for each contest lifecycle stage:
 * - DRAFT: Gray (not ready)
 * - PUBLISHED: Blue (visible but not open for registration)
 * - REGISTERING: Green (registration open)
 * - UPCOMING: Yellow (registered, waiting for start)
 * - ONGOING/RUNNING: Red (contest in progress)
 * - FREEZING: Orange (final freeze period)
 * - FINISHED: Gray (ended)
 * - ARCHIVED: Muted gray (stored for history)
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
    PUBLISHED: {
      variant: "default",
      label: t("contest.status.published", "Published"),
      customClass: "",
    },
    REGISTERING: {
      variant: "outline",
      label: t("contest.status.registrationOpen", "Registration Open"),
      customClass:
        "bg-green-100 text-green-800 border-green-200 dark:bg-green-900/30 dark:text-green-400 dark:border-green-800",
    },
    UPCOMING: {
      variant: "outline",
      label: t("contest.status.upcoming", "Upcoming"),
      customClass:
        "bg-yellow-100 text-yellow-800 border-yellow-200 dark:bg-yellow-900/30 dark:text-yellow-400 dark:border-yellow-800",
    },
    ONGOING: {
      variant: "outline",
      label: t("contest.status.running", "Running"),
      customClass:
        "bg-red-100 text-red-800 border-red-200 dark:bg-red-900/30 dark:text-red-400 dark:border-red-800",
    },
    RUNNING: {
      variant: "outline",
      label: t("contest.status.running", "Running"),
      customClass:
        "bg-red-100 text-red-800 border-red-200 dark:bg-red-900/30 dark:text-red-400 dark:border-red-800",
    },
    FREEZING: {
      variant: "outline",
      label: t("contest.status.freezing", "Freezing"),
      customClass:
        "bg-orange-100 text-orange-800 border-orange-200 dark:bg-orange-900/30 dark:text-orange-400 dark:border-orange-800",
    },
    FINISHED: {
      variant: "outline",
      label: t("contest.status.finished", "Finished"),
      customClass:
        "bg-gray-50 text-gray-600 border-gray-200 dark:bg-gray-800/50 dark:text-gray-400 dark:border-gray-700",
    },
    ARCHIVED: {
      variant: "secondary",
      label: t("contest.status.archived", "Archived"),
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
