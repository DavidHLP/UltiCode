<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { formatDateTimeByLocale } from '@/i18n/utils'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { TerminalCard, TerminalBadge, DataBlock } from '@/components/ui/terminal'
import {
  IconCalendar,
  IconEye,
  IconFlag,
  IconMessage,
  IconMessageCircle,
  IconThumbDown,
  IconThumbUp,
  IconTrash,
} from '@tabler/icons-vue'
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
    <TerminalCard title="post_detail">
      <div class="flex items-start gap-4">
        <div
          class="h-12 w-12 border border-[var(--silver-300)] bg-[var(--surface-sunken)] flex items-center justify-center text-[var(--terminal-cyan)] flex-shrink-0"
        >
          <IconMessageCircle class="h-6 w-6" />
        </div>
        <div class="flex-1 min-w-0">
          <h3 class="text-lg font-medium text-[var(--foreground)] mb-3">{{ post.title }}</h3>
          <div class="flex flex-wrap gap-1.5">
            <TerminalBadge
              v-if="post.isPinned"
              variant="info"
              pulse
              :label="t('forum.status.pinned')"
            />
            <TerminalBadge
              v-if="post.isLocked"
              variant="warning"
              :label="t('forum.status.locked')"
            />
            <TerminalBadge
              v-if="post.isFlagged"
              variant="error"
              pulse
              :label="t('forum.status.flagged')"
            />
            <TerminalBadge
              v-if="post.isDeleted"
              variant="error"
              :label="t('forum.status.deleted')"
            />
            <TerminalBadge
              v-if="!post.isPinned && !post.isLocked && !post.isFlagged && !post.isDeleted"
              variant="success"
              :label="t('forum.status.active')"
            />
          </div>
        </div>
      </div>

      <div class="terminal-separator my-4" />

      <!-- Author & Community -->
      <div class="flex items-center gap-3 mb-4">
        <Avatar class="h-10 w-10 rounded-none border border-[var(--silver-300)]">
          <AvatarImage :src="post.author?.avatar || ''" :alt="post.author?.username" />
          <AvatarFallback class="font-data text-xs bg-[var(--surface-sunken)]">{{
            authorInitials
          }}</AvatarFallback>
        </Avatar>
        <div class="flex flex-col gap-0.5">
          <span class="font-medium text-sm text-[var(--foreground)]">{{
            post.author?.username || t('forum.overview.unknown')
          }}</span>
          <span class="font-data text-xs text-[var(--silver-400)]">
            @{{ post.community?.name || t('forum.drawer.unknownCommunity') }}
          </span>
        </div>
      </div>

      <!-- Content -->
      <div class="space-y-2">
        <span class="terminal-label text-[var(--silver-500)]">{{ t('forum.detail.content') }}</span>
        <div
          class="border border-[var(--silver-200)] dark:border-[var(--silver-300)] p-4 bg-[var(--surface-sunken)]"
        >
          <p class="font-data text-sm text-[var(--foreground)] whitespace-pre-wrap leading-relaxed">
            {{
              post.fullContent ||
              post.content ||
              post.excerpt ||
              t('forum.detail.noContentAvailable')
            }}
          </p>
        </div>
      </div>
    </TerminalCard>

    <!-- Statistics Cards -->
    <div class="grid grid-cols-2 lg:grid-cols-4 gap-3">
      <div
        class="border border-[var(--silver-200)] dark:border-[var(--silver-300)] p-4 bg-[var(--card)]"
      >
        <div class="flex items-center gap-2 mb-3">
          <IconEye class="h-5 w-5 text-[var(--terminal-cyan)]" />
          <span class="terminal-label text-[var(--silver-500)]">{{ t('forum.detail.views') }}</span>
        </div>
        <span class="font-data text-2xl tabular-nums text-[var(--foreground)]">{{
          post.viewCount || 0
        }}</span>
      </div>
      <div
        class="border border-[var(--silver-200)] dark:border-[var(--silver-300)] p-4 bg-[var(--card)]"
      >
        <div class="flex items-center gap-2 mb-3">
          <IconMessage class="h-5 w-5 text-[var(--terminal-cyan)]" />
          <span class="terminal-label text-[var(--silver-500)]">{{
            t('forum.detail.comments')
          }}</span>
        </div>
        <span class="font-data text-2xl tabular-nums text-[var(--foreground)]">{{
          post.commentCount || 0
        }}</span>
      </div>
      <div
        class="border border-[var(--silver-200)] dark:border-[var(--silver-300)] p-4 bg-[var(--card)]"
      >
        <div class="flex items-center gap-2 mb-3">
          <IconThumbUp class="h-5 w-5 text-[var(--terminal-green)]" />
          <span class="terminal-label text-[var(--silver-500)]">{{
            t('forum.detail.upvotes')
          }}</span>
        </div>
        <span class="font-data text-2xl tabular-nums text-[var(--terminal-green)]">{{
          post.upvotes || 0
        }}</span>
      </div>
      <div
        class="border border-[var(--silver-200)] dark:border-[var(--silver-300)] p-4 bg-[var(--card)]"
      >
        <div class="flex items-center gap-2 mb-3">
          <IconThumbDown class="h-5 w-5 text-[var(--terminal-red)]" />
          <span class="terminal-label text-[var(--silver-500)]">{{
            t('forum.detail.downvotes')
          }}</span>
        </div>
        <span class="font-data text-2xl tabular-nums text-[var(--terminal-red)]">{{
          post.downvotes || 0
        }}</span>
      </div>
    </div>

    <!-- Timeline -->
    <TerminalCard :title="t('forum.detail.timeline')">
      <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
        <DataBlock :label="t('forum.detail.created')">
          <div class="flex items-center gap-2">
            <IconCalendar class="h-4 w-4 text-[var(--silver-400)]" />
            <span class="font-data text-sm text-[var(--foreground)]">{{
              formatDateTimeByLocale(post.createdAt)
            }}</span>
          </div>
        </DataBlock>
        <DataBlock :label="t('forum.detail.updated')">
          <div class="flex items-center gap-2">
            <IconCalendar class="h-4 w-4 text-[var(--silver-400)]" />
            <span class="font-data text-sm text-[var(--foreground)]">{{
              formatDateTimeByLocale(post.updatedAt)
            }}</span>
          </div>
        </DataBlock>
      </div>
    </TerminalCard>

    <!-- Flagged Info -->
    <div
      v-if="post.isFlagged && post.flaggedReason"
      class="border border-[var(--terminal-red)] bg-[color-mix(in_oklch,_var(--terminal-red)_8%,_transparent)]"
    >
      <div
        class="px-4 py-3 border-b border-[var(--terminal-red)]/30 bg-[color-mix(in_oklch,_var(--terminal-red)_5%,_transparent)]"
      >
        <div class="flex items-center gap-2">
          <IconFlag class="h-4 w-4 text-[var(--terminal-red)]" />
          <span class="font-data text-xs uppercase tracking-wider text-[var(--terminal-red)]">
            {{ t('forum.detail.flagInformation') }}
          </span>
        </div>
      </div>
      <div class="p-4 space-y-3">
        <DataBlock :label="t('forum.detail.reason')" :value="post.flaggedReason" size="sm" />
        <p v-if="post.flaggedAt" class="font-data text-xs text-[var(--silver-400)]">
          {{ t('forum.detail.flaggedOn') }} {{ formatDateTimeByLocale(post.flaggedAt) }}
        </p>
      </div>
    </div>

    <!-- Deleted Info -->
    <div
      v-if="post.isDeleted"
      class="border border-[var(--terminal-red)] bg-[color-mix(in_oklch,_var(--terminal-red)_8%,_transparent)]"
    >
      <div
        class="px-4 py-3 border-b border-[var(--terminal-red)]/30 bg-[color-mix(in_oklch,_var(--terminal-red)_5%,_transparent)]"
      >
        <div class="flex items-center gap-2">
          <IconTrash class="h-4 w-4 text-[var(--terminal-red)]" />
          <span class="font-data text-xs uppercase tracking-wider text-[var(--terminal-red)]">
            {{ t('forum.detail.deletionInformation') }}
          </span>
        </div>
      </div>
      <div class="p-4">
        <p v-if="post.deletedAt" class="font-data text-xs text-[var(--silver-400)]">
          {{ t('forum.detail.deletedOn') }} {{ formatDateTimeByLocale(post.deletedAt) }}
        </p>
      </div>
    </div>

    <!-- Identifiers -->
    <TerminalCard :title="t('forum.detail.identifiers')">
      <div class="grid gap-3">
        <div class="flex items-center gap-3">
          <span class="terminal-label text-[var(--silver-500)] min-w-[80px]">{{
            t('forum.detail.postId')
          }}</span>
          <code
            class="font-data text-xs bg-[var(--surface-sunken)] px-2 py-1 border border-[var(--silver-300)]"
            >{{ post.id }}</code
          >
        </div>
        <div class="flex items-center gap-3">
          <span class="terminal-label text-[var(--silver-500)] min-w-[80px]">{{
            t('forum.detail.authorId')
          }}</span>
          <code
            class="font-data text-xs bg-[var(--surface-sunken)] px-2 py-1 border border-[var(--silver-300)]"
            >{{ post.userId }}</code
          >
        </div>
        <div class="flex items-center gap-3">
          <span class="terminal-label text-[var(--silver-500)] min-w-[80px]">{{
            t('forum.detail.communityId')
          }}</span>
          <code
            class="font-data text-xs bg-[var(--surface-sunken)] px-2 py-1 border border-[var(--silver-300)]"
            >{{ post.communityId }}</code
          >
        </div>
      </div>
    </TerminalCard>
  </div>
</template>
