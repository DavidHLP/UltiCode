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
} from '@ulticode/design-system/ui/drawer'
import { Avatar, AvatarFallback, AvatarImage } from '@ulticode/design-system/ui/avatar'
import { ScrollArea } from '@ulticode/design-system/ui/scroll-area'
import DetailPanel from '@ulticode/design-system/DetailPanel.vue'
import StatusBadge from '@ulticode/design-system/StatusBadge.vue'
import DetailField from '@ulticode/design-system/DetailField.vue'
import {
  Eye as IconEye,
  Flag as IconFlag,
  MessageSquare as IconMessage,
  MessageCircle as IconMessageCircle,
  ThumbsDown as IconThumbDown,
  ThumbsUp as IconThumbUp,
  Trash as IconTrash,
} from '@lucide/vue'

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
    <DrawerContent class="h-full w-[400px] sm:w-[540px]">
      <DrawerHeader class="px-6 py-4">
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-2">
            <span class="text-muted-foreground font-mono text-sm">//</span>
            <DrawerTitle>{{ t('forum.drawer.title') }}</DrawerTitle>
          </div>
        </div>
        <DrawerDescription>
          {{ t('forum.drawer.description') }}
        </DrawerDescription>
      </DrawerHeader>

      <ScrollArea v-if="post" class="flex-1">
        <div class="flex flex-col gap-4 p-4">
          <!-- Post Header -->
          <DetailPanel title="post_info">
            <div class="flex items-start gap-3">
              <div
                class="h-10 w-10 border border-border bg-muted flex items-center justify-center text-foreground"
              >
                <IconMessageCircle class="h-5 w-5" />
              </div>
              <div class="flex-1 min-w-0 space-y-2">
                <h3 class="text-sm font-medium leading-tight text-foreground">
                  {{ post.title }}
                </h3>
                <div class="flex flex-wrap gap-1.5">
                  <StatusBadge
                    v-if="post.isPinned"
                    variant="info"
                    pulse
                    :label="t('forum.status.pinned')"
                  />
                  <StatusBadge
                    v-if="post.isLocked"
                    variant="warning"
                    :label="t('forum.status.locked')"
                  />
                  <StatusBadge
                    v-if="post.isFlagged"
                    variant="error"
                    pulse
                    :label="t('forum.status.flagged')"
                  />
                  <StatusBadge
                    v-if="post.isDeleted"
                    variant="error"
                    :label="t('forum.status.deleted')"
                  />
                  <StatusBadge
                    v-if="!post.isPinned && !post.isLocked && !post.isFlagged && !post.isDeleted"
                    variant="success"
                    :label="t('forum.status.active')"
                  />
                </div>
              </div>
            </div>
          </DetailPanel>

          <!-- Author & Community -->
          <DetailPanel :title="t('forum.drawer.authorCommunity')">
            <div class="flex items-center gap-3">
              <Avatar class="h-9 w-9">
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
          </DetailPanel>

          <!-- Statistics Grid -->
          <div class="grid grid-cols-2 gap-3">
            <div class="border border-border dark:border-border p-3 bg-card">
              <div class="flex items-center gap-2 mb-2">
                <IconEye class="h-4 w-4 text-status-info-mark" />
                <span class="text-sm font-medium text-muted-foreground">{{
                  t('forum.detail.views')
                }}</span>
              </div>
              <span class="font-mono text-xl tabular-nums text-foreground">{{
                post.viewCount || 0
              }}</span>
            </div>
            <div class="border border-border dark:border-border p-3 bg-card">
              <div class="flex items-center gap-2 mb-2">
                <IconMessage class="h-4 w-4 text-status-info-mark" />
                <span class="text-sm font-medium text-muted-foreground">{{
                  t('forum.detail.comments')
                }}</span>
              </div>
              <span class="font-mono text-xl tabular-nums text-foreground">{{
                post.commentCount || 0
              }}</span>
            </div>
            <div class="border border-border dark:border-border p-3 bg-card">
              <div class="flex items-center gap-2 mb-2">
                <IconThumbUp class="h-4 w-4 text-status-success-mark" />
                <span class="text-sm font-medium text-muted-foreground">{{
                  t('forum.detail.upvotes')
                }}</span>
              </div>
              <span class="font-mono text-xl tabular-nums text-foreground">{{
                post.upvotes || 0
              }}</span>
            </div>
            <div class="border border-border dark:border-border p-3 bg-card">
              <div class="flex items-center gap-2 mb-2">
                <IconThumbDown class="h-4 w-4 text-status-error-mark" />
                <span class="text-sm font-medium text-muted-foreground">{{
                  t('forum.detail.downvotes')
                }}</span>
              </div>
              <span class="font-mono text-xl tabular-nums text-foreground">{{
                post.downvotes || 0
              }}</span>
            </div>
          </div>

          <!-- Timeline -->
          <DetailPanel :title="t('forum.detail.timeline')">
            <div class="grid grid-cols-2 gap-4">
              <DetailField
                :label="t('forum.detail.created')"
                :value="formatDateTimeByLocale(post.createdAt)"
                size="sm"
              />
              <DetailField
                :label="t('forum.detail.updated')"
                :value="formatDateTimeByLocale(post.updatedAt)"
                size="sm"
              />
            </div>
          </DetailPanel>

          <!-- Flagged Info -->
          <div
            v-if="post.isFlagged && post.flaggedReason"
            class="border border-status-error-mark bg-[color-mix(in_oklch,_var(--status-error-mark)_8%,_transparent)] p-4"
          >
            <div class="flex items-center gap-2 mb-3">
              <IconFlag class="h-4 w-4 text-status-error-mark" />
              <span class="font-mono text-xs uppercase tracking-wider text-foreground">
                {{ t('forum.detail.flagInformation') }}
              </span>
            </div>
            <DetailField :label="t('forum.detail.reason')" :value="post.flaggedReason" size="sm" />
            <p v-if="post.flaggedAt" class="font-mono text-xs text-muted-foreground mt-2">
              {{ t('forum.detail.flaggedOn') }} {{ formatDateTimeByLocale(post.flaggedAt) }}
            </p>
          </div>

          <!-- Deleted Info -->
          <div
            v-if="post.isDeleted"
            class="border border-status-error-mark bg-[color-mix(in_oklch,_var(--status-error-mark)_8%,_transparent)] p-4"
          >
            <div class="flex items-center gap-2 mb-3">
              <IconTrash class="h-4 w-4 text-status-error-mark" />
              <span class="font-mono text-xs uppercase tracking-wider text-foreground">
                {{ t('forum.detail.deletionInformation') }}
              </span>
            </div>
            <p v-if="post.deletedAt" class="font-mono text-xs text-muted-foreground">
              {{ t('forum.detail.deletedOn') }} {{ formatDateTimeByLocale(post.deletedAt) }}
            </p>
          </div>

          <!-- Content Preview -->
          <DetailPanel :title="t('forum.drawer.contentPreview')">
            <div class="border border-border dark:border-border p-3 bg-muted">
              <p
                class="font-mono text-xs text-muted-foreground whitespace-pre-wrap leading-relaxed"
              >
                {{ post.content || post.excerpt || t('forum.detail.noContentAvailable') }}
              </p>
            </div>
          </DetailPanel>

          <!-- IDs -->
          <DetailPanel :title="t('forum.detail.identifiers')">
            <div class="grid gap-2">
              <DetailField :label="t('forum.detail.postId')" size="sm">
                <code class="font-mono text-xs bg-muted px-1.5 py-0.5 border border-border">{{
                  post.id
                }}</code>
              </DetailField>
              <DetailField :label="t('forum.detail.authorId')" size="sm">
                <code class="font-mono text-xs bg-muted px-1.5 py-0.5 border border-border">{{
                  post.userId
                }}</code>
              </DetailField>
              <DetailField :label="t('forum.detail.communityId')" size="sm">
                <code class="font-mono text-xs bg-muted px-1.5 py-0.5 border border-border">{{
                  post.communityId
                }}</code>
              </DetailField>
            </div>
          </DetailPanel>
        </div>
      </ScrollArea>

      <div v-else class="flex h-full items-center justify-center p-8">
        <div class="text-center">
          <span class="font-mono text-sm text-muted-foreground"
            >&gt; {{ t('forum.drawer.postNotFound') }}</span
          >
        </div>
      </div>
    </DrawerContent>
  </Drawer>
</template>
