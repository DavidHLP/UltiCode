<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { formatDateTimeByLocale } from '@/i18n/utils'
import {
  Drawer,
  DrawerContent,
  DrawerDescription,
  DrawerHeader,
  DrawerTitle,
} from '@/components/ui/drawer'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { ScrollArea } from '@/components/ui/scroll-area'
import { TerminalCard, TerminalBadge, DataBlock } from '@/components/ui/terminal'
import {
  IconEye,
  IconFlag,
  IconMessage,
  IconMessageCircle,
  IconThumbDown,
  IconThumbUp,
  IconTrash,
} from '@tabler/icons-vue'
import type { ForumPost } from '@/api/admin/forum'

const props = defineProps<{
  open: boolean
  post: ForumPost | null
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
}>()

const { t } = useI18n()

const authorInitials = computed(() => {
  if (!props.post?.author?.username) return '?'
  return props.post.author.username.slice(0, 2).toUpperCase()
})

</script>

<template>
  <Drawer :open="open" @update:open="emit('update:open', $event)" direction="right">
    <DrawerContent
      class="h-full w-[400px] sm:w-[540px] border-l border-[var(--silver-200)] dark:border-[var(--silver-300)]"
    >
      <DrawerHeader
        class="border-b border-[var(--silver-200)] dark:border-[var(--silver-300)] px-6 py-4 bg-[var(--surface-sunken)]"
      >
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-2">
            <span class="text-[var(--silver-400)] font-data text-sm">//</span>
            <DrawerTitle class="font-data text-sm uppercase tracking-wider">{{
              t('forum.drawer.title')
            }}</DrawerTitle>
          </div>
        </div>
        <DrawerDescription class="font-data text-xs text-[var(--silver-400)]">
          {{ t('forum.drawer.description') }}
        </DrawerDescription>
      </DrawerHeader>

      <ScrollArea v-if="post" class="flex-1">
        <div class="flex flex-col gap-4 p-4">
          <!-- Post Header -->
          <TerminalCard title="post_info">
            <div class="flex items-start gap-3">
              <div
                class="h-10 w-10 border border-[var(--silver-300)] bg-[var(--surface-sunken)] flex items-center justify-center text-[var(--terminal-cyan)]"
              >
                <IconMessageCircle class="h-5 w-5" />
              </div>
              <div class="flex-1 min-w-0 space-y-2">
                <h3 class="text-sm font-medium leading-tight text-[var(--foreground)]">
                  {{ post.title }}
                </h3>
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
          </TerminalCard>

          <!-- Author & Community -->
          <TerminalCard :title="t('forum.drawer.authorCommunity')">
            <div class="flex items-center gap-3">
              <Avatar class="h-9 w-9 rounded-none border border-[var(--silver-300)]">
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
          </TerminalCard>

          <!-- Statistics Grid -->
          <div class="grid grid-cols-2 gap-3">
            <div
              class="border border-[var(--silver-200)] dark:border-[var(--silver-300)] p-3 bg-[var(--card)]"
            >
              <div class="flex items-center gap-2 mb-2">
                <IconEye class="h-4 w-4 text-[var(--terminal-cyan)]" />
                <span class="terminal-label text-[var(--silver-500)]">{{
                  t('forum.detail.views')
                }}</span>
              </div>
              <span class="font-data text-xl tabular-nums text-[var(--foreground)]">{{
                post.viewCount || 0
              }}</span>
            </div>
            <div
              class="border border-[var(--silver-200)] dark:border-[var(--silver-300)] p-3 bg-[var(--card)]"
            >
              <div class="flex items-center gap-2 mb-2">
                <IconMessage class="h-4 w-4 text-[var(--terminal-cyan)]" />
                <span class="terminal-label text-[var(--silver-500)]">{{
                  t('forum.detail.comments')
                }}</span>
              </div>
              <span class="font-data text-xl tabular-nums text-[var(--foreground)]">{{
                post.commentCount || 0
              }}</span>
            </div>
            <div
              class="border border-[var(--silver-200)] dark:border-[var(--silver-300)] p-3 bg-[var(--card)]"
            >
              <div class="flex items-center gap-2 mb-2">
                <IconThumbUp class="h-4 w-4 text-[var(--terminal-green)]" />
                <span class="terminal-label text-[var(--silver-500)]">{{
                  t('forum.detail.upvotes')
                }}</span>
              </div>
              <span class="font-data text-xl tabular-nums text-[var(--terminal-green)]">{{
                post.upvotes || 0
              }}</span>
            </div>
            <div
              class="border border-[var(--silver-200)] dark:border-[var(--silver-300)] p-3 bg-[var(--card)]"
            >
              <div class="flex items-center gap-2 mb-2">
                <IconThumbDown class="h-4 w-4 text-[var(--terminal-red)]" />
                <span class="terminal-label text-[var(--silver-500)]">{{
                  t('forum.detail.downvotes')
                }}</span>
              </div>
              <span class="font-data text-xl tabular-nums text-[var(--terminal-red)]">{{
                post.downvotes || 0
              }}</span>
            </div>
          </div>

          <!-- Timeline -->
          <TerminalCard :title="t('forum.detail.timeline')">
            <div class="grid grid-cols-2 gap-4">
              <DataBlock
                :label="t('forum.detail.created')"
                :value="formatDateTimeByLocale(post.createdAt)"
                size="sm"
              />
              <DataBlock
                :label="t('forum.detail.updated')"
                :value="formatDateTimeByLocale(post.updatedAt)"
                size="sm"
              />
            </div>
          </TerminalCard>

          <!-- Flagged Info -->
          <div
            v-if="post.isFlagged && post.flaggedReason"
            class="border border-[var(--terminal-red)] bg-[color-mix(in_oklch,_var(--terminal-red)_8%,_transparent)] p-4"
          >
            <div class="flex items-center gap-2 mb-3">
              <IconFlag class="h-4 w-4 text-[var(--terminal-red)]" />
              <span class="font-data text-xs uppercase tracking-wider text-[var(--terminal-red)]">
                {{ t('forum.detail.flagInformation') }}
              </span>
            </div>
            <DataBlock :label="t('forum.detail.reason')" :value="post.flaggedReason" size="sm" />
            <p v-if="post.flaggedAt" class="font-data text-xs text-[var(--silver-400)] mt-2">
              {{ t('forum.detail.flaggedOn') }} {{ formatDateTimeByLocale(post.flaggedAt) }}
            </p>
          </div>

          <!-- Deleted Info -->
          <div
            v-if="post.isDeleted"
            class="border border-[var(--terminal-red)] bg-[color-mix(in_oklch,_var(--terminal-red)_8%,_transparent)] p-4"
          >
            <div class="flex items-center gap-2 mb-3">
              <IconTrash class="h-4 w-4 text-[var(--terminal-red)]" />
              <span class="font-data text-xs uppercase tracking-wider text-[var(--terminal-red)]">
                {{ t('forum.detail.deletionInformation') }}
              </span>
            </div>
            <p v-if="post.deletedAt" class="font-data text-xs text-[var(--silver-400)]">
              {{ t('forum.detail.deletedOn') }} {{ formatDateTimeByLocale(post.deletedAt) }}
            </p>
          </div>

          <!-- Content Preview -->
          <TerminalCard :title="t('forum.drawer.contentPreview')">
            <div
              class="border border-[var(--silver-200)] dark:border-[var(--silver-300)] p-3 bg-[var(--surface-sunken)]"
            >
              <p
                class="font-data text-xs text-[var(--silver-400)] whitespace-pre-wrap leading-relaxed"
              >
                {{ post.content || post.excerpt || t('forum.detail.noContentAvailable') }}
              </p>
            </div>
          </TerminalCard>

          <!-- IDs -->
          <TerminalCard :title="t('forum.detail.identifiers')">
            <div class="grid gap-2">
              <DataBlock :label="t('forum.detail.postId')" size="sm">
                <code
                  class="font-data text-xs bg-[var(--surface-sunken)] px-1.5 py-0.5 border border-[var(--silver-300)]"
                  >{{ post.id }}</code
                >
              </DataBlock>
              <DataBlock :label="t('forum.detail.authorId')" size="sm">
                <code
                  class="font-data text-xs bg-[var(--surface-sunken)] px-1.5 py-0.5 border border-[var(--silver-300)]"
                  >{{ post.userId }}</code
                >
              </DataBlock>
              <DataBlock :label="t('forum.detail.communityId')" size="sm">
                <code
                  class="font-data text-xs bg-[var(--surface-sunken)] px-1.5 py-0.5 border border-[var(--silver-300)]"
                  >{{ post.communityId }}</code
                >
              </DataBlock>
            </div>
          </TerminalCard>
        </div>
      </ScrollArea>

      <div v-else class="flex h-full items-center justify-center p-8">
        <div class="text-center">
          <span class="font-data text-sm text-[var(--silver-400)]"
            >&gt; {{ t('forum.drawer.postNotFound') }}</span
          >
        </div>
      </div>
    </DrawerContent>
  </Drawer>
</template>
