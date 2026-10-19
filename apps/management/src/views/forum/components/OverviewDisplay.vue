<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { formatDateTimeByLocale } from '@/i18n/utils'
import { Avatar, AvatarFallback, AvatarImage } from '@ulticode/design-system/ui/avatar'
import DetailPanel from '@ulticode/design-system/DetailPanel.vue'
import StatusBadge from '@ulticode/design-system/StatusBadge.vue'
import DetailField from '@ulticode/design-system/DetailField.vue'
import {
  Calendar as IconCalendar,
  Eye as IconEye,
  Flag as IconFlag,
  MessageSquare as IconMessage,
  MessageCircle as IconMessageCircle,
  ThumbsDown as IconThumbDown,
  ThumbsUp as IconThumbUp,
  Trash as IconTrash,
} from '@lucide/vue'

import type { ForumPostDetail } from '@/api/admin/forum'

const props = defineProps<{
  post: ForumPostDetail
}>()

const { t } = useI18n()

const authorInitials = computed(() => {
  if (!props.post.author?.username) return '?'
  return props.post.author.username.slice(0, 2).toUpperCase()
})
</script>

<template>
  <div class="space-y-4">
    <!-- Post Header Card -->
    <DetailPanel title="post_detail">
      <div class="flex items-start gap-4">
        <div
          class="h-12 w-12 border border-border bg-muted flex items-center justify-center text-foreground flex-shrink-0"
        >
          <IconMessageCircle class="h-6 w-6" />
        </div>
        <div class="flex-1 min-w-0">
          <h3 class="text-lg font-medium text-foreground mb-3">{{ post.title }}</h3>
          <div class="flex flex-wrap gap-1.5">
            <StatusBadge
              v-if="post.isPinned"
              variant="info"
              pulse
              :label="t('forum.status.pinned')"
            />
            <StatusBadge v-if="post.isLocked" variant="warning" :label="t('forum.status.locked')" />
            <StatusBadge
              v-if="post.isFlagged"
              variant="error"
              pulse
              :label="t('forum.status.flagged')"
            />
            <StatusBadge v-if="post.isDeleted" variant="error" :label="t('forum.status.deleted')" />
            <StatusBadge
              v-if="!post.isPinned && !post.isLocked && !post.isFlagged && !post.isDeleted"
              variant="success"
              :label="t('forum.status.active')"
            />
          </div>
        </div>
      </div>

      <div class="border-t border-border my-4" />

      <!-- Author & Community -->
      <div class="flex items-center gap-3 mb-4">
        <Avatar class="h-10 w-10">
          <AvatarImage :src="post.author?.avatar || ''" :alt="post.author?.username" />
          <AvatarFallback>{{ authorInitials }}</AvatarFallback>
        </Avatar>
        <div class="flex flex-col gap-0.5">
          <span class="font-medium text-sm text-foreground">{{
            post.author?.username || t('forum.overview.unknown')
          }}</span>
          <span class="font-mono text-xs text-muted-foreground">
            @{{ post.community?.name || t('forum.drawer.unknownCommunity') }}
          </span>
        </div>
      </div>

      <!-- Content -->
      <div class="space-y-2">
        <span class="text-sm font-medium text-muted-foreground">{{
          t('forum.detail.content')
        }}</span>
        <div class="border border-border dark:border-border p-4 bg-muted">
          <p class="font-mono text-sm text-foreground whitespace-pre-wrap leading-relaxed">
            {{
              post.fullContent ||
              post.content ||
              post.excerpt ||
              t('forum.detail.noContentAvailable')
            }}
          </p>
        </div>
      </div>
    </DetailPanel>

    <!-- Statistics Cards -->
    <div class="grid grid-cols-2 lg:grid-cols-4 gap-3">
      <div class="border border-border dark:border-border p-4 bg-card">
        <div class="flex items-center gap-2 mb-3">
          <IconEye class="h-5 w-5 text-status-info-mark" />
          <span class="text-sm font-medium text-muted-foreground">{{
            t('forum.detail.views')
          }}</span>
        </div>
        <span class="font-mono text-2xl tabular-nums text-foreground">{{
          post.viewCount || 0
        }}</span>
      </div>
      <div class="border border-border dark:border-border p-4 bg-card">
        <div class="flex items-center gap-2 mb-3">
          <IconMessage class="h-5 w-5 text-status-info-mark" />
          <span class="text-sm font-medium text-muted-foreground">{{
            t('forum.detail.comments')
          }}</span>
        </div>
        <span class="font-mono text-2xl tabular-nums text-foreground">{{
          post.commentCount || 0
        }}</span>
      </div>
      <div class="border border-border dark:border-border p-4 bg-card">
        <div class="flex items-center gap-2 mb-3">
          <IconThumbUp class="h-5 w-5 text-status-success-mark" />
          <span class="text-sm font-medium text-muted-foreground">{{
            t('forum.detail.upvotes')
          }}</span>
        </div>
        <span class="font-mono text-2xl tabular-nums text-foreground">{{ post.upvotes || 0 }}</span>
      </div>
      <div class="border border-border dark:border-border p-4 bg-card">
        <div class="flex items-center gap-2 mb-3">
          <IconThumbDown class="h-5 w-5 text-status-error-mark" />
          <span class="text-sm font-medium text-muted-foreground">{{
            t('forum.detail.downvotes')
          }}</span>
        </div>
        <span class="font-mono text-2xl tabular-nums text-foreground">{{
          post.downvotes || 0
        }}</span>
      </div>
    </div>

    <!-- Timeline -->
    <DetailPanel :title="t('forum.detail.timeline')">
      <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
        <DetailField :label="t('forum.detail.created')">
          <div class="flex items-center gap-2">
            <IconCalendar class="h-4 w-4 text-muted-foreground" />
            <span class="font-mono text-sm text-foreground">{{
              formatDateTimeByLocale(post.createdAt)
            }}</span>
          </div>
        </DetailField>
        <DetailField :label="t('forum.detail.updated')">
          <div class="flex items-center gap-2">
            <IconCalendar class="h-4 w-4 text-muted-foreground" />
            <span class="font-mono text-sm text-foreground">{{
              formatDateTimeByLocale(post.updatedAt)
            }}</span>
          </div>
        </DetailField>
      </div>
    </DetailPanel>

    <!-- Flagged Info -->
    <div
      v-if="post.isFlagged && post.flaggedReason"
      class="border border-status-error-mark bg-[color-mix(in_oklch,_var(--status-error-mark)_8%,_transparent)]"
    >
      <div
        class="px-4 py-3 border-b border-status-error-mark/30 bg-[color-mix(in_oklch,_var(--status-error-mark)_5%,_transparent)]"
      >
        <div class="flex items-center gap-2">
          <IconFlag class="h-4 w-4 text-status-error-mark" />
          <span class="font-mono text-xs uppercase tracking-wider text-foreground">
            {{ t('forum.detail.flagInformation') }}
          </span>
        </div>
      </div>
      <div class="p-4 space-y-3">
        <DetailField :label="t('forum.detail.reason')" :value="post.flaggedReason" size="sm" />
        <p v-if="post.flaggedAt" class="font-mono text-xs text-muted-foreground">
          {{ t('forum.detail.flaggedOn') }} {{ formatDateTimeByLocale(post.flaggedAt) }}
        </p>
      </div>
    </div>

    <!-- Deleted Info -->
    <div
      v-if="post.isDeleted"
      class="border border-status-error-mark bg-[color-mix(in_oklch,_var(--status-error-mark)_8%,_transparent)]"
    >
      <div
        class="px-4 py-3 border-b border-status-error-mark/30 bg-[color-mix(in_oklch,_var(--status-error-mark)_5%,_transparent)]"
      >
        <div class="flex items-center gap-2">
          <IconTrash class="h-4 w-4 text-status-error-mark" />
          <span class="font-mono text-xs uppercase tracking-wider text-foreground">
            {{ t('forum.detail.deletionInformation') }}
          </span>
        </div>
      </div>
      <div class="p-4">
        <p v-if="post.deletedAt" class="font-mono text-xs text-muted-foreground">
          {{ t('forum.detail.deletedOn') }} {{ formatDateTimeByLocale(post.deletedAt) }}
        </p>
      </div>
    </div>

    <!-- Identifiers -->
    <DetailPanel :title="t('forum.detail.identifiers')">
      <div class="grid gap-3">
        <div class="flex items-center gap-3">
          <span class="text-sm font-medium text-muted-foreground min-w-[80px]">{{
            t('forum.detail.postId')
          }}</span>
          <code class="font-mono text-xs bg-muted px-2 py-1 border border-border">{{
            post.id
          }}</code>
        </div>
        <div class="flex items-center gap-3">
          <span class="text-sm font-medium text-muted-foreground min-w-[80px]">{{
            t('forum.detail.authorId')
          }}</span>
          <code class="font-mono text-xs bg-muted px-2 py-1 border border-border">{{
            post.userId
          }}</code>
        </div>
        <div class="flex items-center gap-3">
          <span class="text-sm font-medium text-muted-foreground min-w-[80px]">{{
            t('forum.detail.communityId')
          }}</span>
          <code class="font-mono text-xs bg-muted px-2 py-1 border border-border">{{
            post.communityId
          }}</code>
        </div>
      </div>
    </DetailPanel>
  </div>
</template>
