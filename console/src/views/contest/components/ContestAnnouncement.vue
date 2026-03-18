<script setup lang="ts">
/**
 * ContestAnnouncement - Announcement card component
 *
 * Displays contest announcement with title, content, timestamp,
 * and pinned indicator.
 */
import { computed } from "vue";
import { useI18n } from "vue-i18n";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Pin, Clock, User } from "lucide-vue-next";
import type { ContestAnnouncement as ContestAnnouncementType } from "@/types/contest";
import { formatDateTime } from "@/utils/date";

const props = defineProps<{
  /** Announcement data */
  announcement: ContestAnnouncementType;
  /** Show in compact mode */
  compact?: boolean;
  /** Highlight as new/unread */
  isNew?: boolean;
}>();

const { t } = useI18n();

const formattedDate = computed(() => {
  return formatDateTime(props.announcement.createdAt);
});

const authorName = computed(() => {
  return (
    props.announcement.author?.username || t("common.labels.admin", "Admin")
  );
});
</script>

<template>
  <Card
    :class="[
      'transition-colors',
      { 'border-primary/50 bg-primary/5': isNew },
      { 'py-3': compact },
    ]"
  >
    <CardHeader :class="{ 'pb-2': compact, 'py-3': compact }">
      <div class="flex items-start justify-between gap-3">
        <div class="flex items-center gap-2 min-w-0">
          <!-- Pinned indicator -->
          <Pin
            v-if="announcement.isPinned"
            class="h-4 w-4 text-primary shrink-0"
          />

          <!-- New badge -->
          <Badge v-if="isNew" variant="default" class="shrink-0">
            {{ t("common.labels.new", "New") }}
          </Badge>

          <!-- Title -->
          <CardTitle
            :class="['truncate', { 'text-base': !compact, 'text-sm': compact }]"
          >
            {{ announcement.title }}
          </CardTitle>
        </div>
      </div>
    </CardHeader>

    <CardContent :class="{ 'pt-0 pb-3': compact }">
      <!-- Content -->
      <div
        :class="[
          'text-muted-foreground',
          { 'text-sm line-clamp-3': compact, 'text-base mt-2': !compact },
        ]"
      >
        {{ announcement.content }}
      </div>

      <!-- Meta info -->
      <div
        :class="[
          'flex items-center gap-4 text-xs text-muted-foreground',
          { 'mt-2': compact, 'mt-4': !compact },
        ]"
      >
        <div class="flex items-center gap-1">
          <User class="h-3 w-3" />
          <span>{{ authorName }}</span>
        </div>
        <div class="flex items-center gap-1">
          <Clock class="h-3 w-3" />
          <span>{{ formattedDate }}</span>
        </div>
      </div>
    </CardContent>
  </Card>
</template>
