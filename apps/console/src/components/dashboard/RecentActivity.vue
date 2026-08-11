<script setup lang="ts">
import type { RecentActivity } from "@/types/userStats";
import { cn } from "@/lib/utils";
import { CheckCircle, MessageSquare, FileText, Clock } from "lucide-vue-next";
import { useI18n } from "vue-i18n";
import type { SemanticColor } from "@/shared/badge-config/src";
import {
  getStatusColor,
  getStatusLabelI18nKey,
} from "@/shared/submission-status/src";

defineProps<{
  activities: RecentActivity[];
  title?: string;
}>();

const { t } = useI18n();

const getSubmissionLabel = (status: string): string => {
  const key = getStatusLabelI18nKey(status);
  return key ? t(key) : status;
};

const iconMap = {
  submission: CheckCircle,
  solution: FileText,
  post: MessageSquare,
  comment: MessageSquare,
};

// Single SemanticColor → text-class map for this surface, fed by the shared
// verdict→color truth. Covers every verdict (incl. Sandbox Error → neutral),
// so no status ever resolves to class="undefined" (the previous statusColors
// map had only 5 entries and no fallback).
const SEMANTIC_TEXT_CLASS: Record<SemanticColor, string> = {
  success: "text-foreground decoration-status-success underline decoration-2",
  warning: "text-foreground decoration-status-warning underline decoration-2",
  error: "text-foreground decoration-status-error underline decoration-2",
  info: "text-foreground decoration-status-info underline decoration-2",
  purple: "text-foreground decoration-status-special underline decoration-2",
  electric: "text-foreground decoration-link-decoration underline decoration-2",
  neutral: "text-muted-foreground",
};

const SEMANTIC_ACCENT_CLASS: Record<SemanticColor, string> = {
  success: "text-status-success",
  warning: "text-status-warning",
  error: "text-status-error",
  info: "text-status-info",
  purple: "text-status-special",
  electric: "text-[var(--accent-primary)]",
  neutral: "text-muted-foreground",
};

const getStatusTextClass = (status: string | undefined): string =>
  SEMANTIC_TEXT_CLASS[getStatusColor(status ?? "")];

const getStatusAccentClass = (status: string | undefined): string =>
  SEMANTIC_ACCENT_CLASS[getStatusColor(status ?? "")];

const typeColors: Record<string, string> = {
  submission: "text-[var(--accent-primary)]",
  solution: "text-status-special",
  post: "text-status-success",
  comment: "text-status-warning",
};

function formatTime(dateStr: string): string {
  const date = new Date(dateStr);
  const now = new Date();
  const diff = now.getTime() - date.getTime();
  const minutes = Math.floor(diff / 60000);
  const hours = Math.floor(diff / 3600000);
  const days = Math.floor(diff / 86400000);

  if (minutes < 1) return "Just now";
  if (minutes < 60) return `${minutes}m ago`;
  if (hours < 24) return `${hours}h ago`;
  if (days < 7) return `${days}d ago`;
  return date.toLocaleDateString();
}
</script>

<template>
  <div class="space-y-2">
    <h3 v-if="title" class="text-sm font-medium">{{ title }}</h3>

    <div class="space-y-3">
      <div
        v-for="activity in activities"
        :key="activity.id"
        class="flex items-start gap-3 rounded-none border p-3 transition-colors hover:bg-muted/50"
      >
        <!-- Icon -->
        <div
          :class="
            cn(
              'flex h-8 w-8 shrink-0 items-center justify-center rounded-full',
              activity.type === 'submission' && activity.status === 'Accepted'
                ? 'bg-status-success-surface'
                : 'bg-muted',
            )
          "
        >
          <component
            :is="iconMap[activity.type]"
            :class="
              cn(
                'h-4 w-4',
                activity.type === 'submission'
                  ? getStatusAccentClass(activity.status)
                  : typeColors[activity.type],
              )
            "
          />
        </div>

        <!-- Content -->
        <div class="flex-1 space-y-0.5 overflow-hidden">
          <p class="truncate text-sm font-medium">{{ activity.title }}</p>
          <div class="flex items-center gap-2 text-xs text-muted-foreground">
            <span class="capitalize">{{ activity.type }}</span>
            <span
              v-if="activity.status"
              :class="getStatusTextClass(activity.status)"
            >
              {{ getSubmissionLabel(activity.status) }}
            </span>
          </div>
        </div>

        <!-- Time -->
        <div
          class="flex shrink-0 items-center gap-1 text-xs text-muted-foreground"
        >
          <Clock class="h-3 w-3" />
          {{ formatTime(activity.createdAt) }}
        </div>
      </div>

      <!-- Empty state -->
      <div
        v-if="activities.length === 0"
        class="py-8 text-center text-sm text-muted-foreground"
      >
        No recent activity
      </div>
    </div>
  </div>
</template>
