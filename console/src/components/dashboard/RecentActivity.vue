<script setup lang="ts">
import type { RecentActivity } from "@/types/userStats";
import { cn } from "@/lib/utils";
import { CheckCircle, MessageSquare, FileText, Clock } from "lucide-vue-next";

defineProps<{
  activities: RecentActivity[];
  title?: string;
}>();

const iconMap = {
  submission: CheckCircle,
  solution: FileText,
  post: MessageSquare,
  comment: MessageSquare,
};

const statusColors: Record<string, string> = {
  Accepted: "text-green-600",
  "Wrong Answer": "text-red-600",
  "Time Limit Exceeded": "text-orange-600",
  "Runtime Error": "text-red-600",
  "Compile Error": "text-red-600",
};

const typeColors: Record<string, string> = {
  submission: "text-blue-500",
  solution: "text-purple-500",
  post: "text-green-500",
  comment: "text-orange-500",
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
        class="flex items-start gap-3 rounded-lg border p-3 transition-colors hover:bg-muted/50"
      >
        <!-- Icon -->
        <div
          :class="
            cn(
              'flex h-8 w-8 shrink-0 items-center justify-center rounded-full',
              activity.type === 'submission' && activity.status === 'Accepted'
                ? 'bg-green-100 dark:bg-green-900/30'
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
                  ? statusColors[activity.status || ''] || 'text-gray-500'
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
            <span v-if="activity.status" :class="statusColors[activity.status]">
              {{ activity.status }}
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
