<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
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

function formatDate(dateStr: string) {
  return new Date(dateStr).toLocaleString()
}
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
              v-if="post.is_pinned"
              variant="info"
              pulse
              :label="t('forum.status.pinned')"
            />
            <TerminalBadge
              v-if="post.is_locked"
              variant="warning"
              :label="t('forum.status.locked')"
            />
            <TerminalBadge
              v-if="post.is_flagged"
              variant="error"
              pulse
              :label="t('forum.status.flagged')"
            />
            <TerminalBadge
              v-if="post.is_deleted"
              variant="error"
              :label="t('forum.status.deleted')"
            />
            <TerminalBadge
              v-if="!post.is_pinned && !post.is_locked && !post.is_flagged && !post.is_deleted"
              variant="success"
              :label="t('forum.status.active')"
            />
          </div>
        </div>
      </div>

      <div class="terminal-separator my-4" />

      <!-- Author & Community -->
      <div class="flex items-center gap-3 mb-4">
        <Avatar class="h-10 w-10 rounded-sm border border-[var(--silver-300)]">
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
              post.full_content ||
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
          post.view_count || 0
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
          post.comment_count || 0
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
              formatDate(post.created_at)
            }}</span>
          </div>
        </DataBlock>
        <DataBlock :label="t('forum.detail.updated')">
          <div class="flex items-center gap-2">
            <IconCalendar class="h-4 w-4 text-[var(--silver-400)]" />
            <span class="font-data text-sm text-[var(--foreground)]">{{
              formatDate(post.updated_at)
            }}</span>
          </div>
        </DataBlock>
      </div>
    </TerminalCard>

    <!-- Flagged Info -->
    <div
      v-if="post.is_flagged && post.flagged_reason"
      class="border border-[var(--terminal-red)] bg-[oklch(0.6_0.2_25/0.08)]"
    >
      <div class="px-4 py-3 border-b border-[var(--terminal-red)]/30 bg-[oklch(0.6_0.2_25/0.05)]">
        <div class="flex items-center gap-2">
          <IconFlag class="h-4 w-4 text-[var(--terminal-red)]" />
          <span class="font-data text-xs uppercase tracking-wider text-[var(--terminal-red)]">
            {{ t('forum.detail.flagInformation') }}
          </span>
        </div>
      </div>
      <div class="p-4 space-y-3">
        <DataBlock :label="t('forum.detail.reason')" :value="post.flagged_reason" size="sm" />
        <p v-if="post.flagged_at" class="font-data text-xs text-[var(--silver-400)]">
          {{ t('forum.detail.flaggedOn') }} {{ formatDate(post.flagged_at) }}
        </p>
      </div>
    </div>

    <!-- Deleted Info -->
    <div
      v-if="post.is_deleted"
      class="border border-[var(--terminal-red)] bg-[oklch(0.6_0.2_25/0.08)]"
    >
      <div class="px-4 py-3 border-b border-[var(--terminal-red)]/30 bg-[oklch(0.6_0.2_25/0.05)]">
        <div class="flex items-center gap-2">
          <IconTrash class="h-4 w-4 text-[var(--terminal-red)]" />
          <span class="font-data text-xs uppercase tracking-wider text-[var(--terminal-red)]">
            {{ t('forum.detail.deletionInformation') }}
          </span>
        </div>
      </div>
      <div class="p-4">
        <p v-if="post.deleted_at" class="font-data text-xs text-[var(--silver-400)]">
          {{ t('forum.detail.deletedOn') }} {{ formatDate(post.deleted_at) }}
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
            >{{ post.user_id }}</code
          >
        </div>
        <div class="flex items-center gap-3">
          <span class="terminal-label text-[var(--silver-500)] min-w-[80px]">{{
            t('forum.detail.communityId')
          }}</span>
          <code
            class="font-data text-xs bg-[var(--surface-sunken)] px-2 py-1 border border-[var(--silver-300)]"
            >{{ post.community_id }}</code
          >
        </div>
      </div>
    </TerminalCard>
  </div>
</template>
