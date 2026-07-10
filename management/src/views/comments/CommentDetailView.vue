<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { toast } from 'vue-sonner'
import {
  IconArrowLeft,
  IconFlag,
  IconTrash,
  IconMessage,
  IconFileText,
  IconUser,
} from '@tabler/icons-vue'

import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { TerminalBadge } from '@/components/ui/terminal'

import { useCommentsStore } from '@/stores/admin/comments'
import { PERM } from '@/constants/permissions'
import { useAuthStore } from '@/stores/auth'
import type { CommentType } from '@/api/admin/comments'
import { formatDate } from '@/lib/format/date'
import EntityActionDialog from '@/components/shared/EntityActionDialog.vue'
import { renderMarkdown } from '@/shared/markdown-utils/src'
import { useDetailWorkspace } from '@/composables/useDetailWorkspace'

const router = useRouter()
const route = useRoute()
const { t } = useI18n()
const commentsStore = useCommentsStore()
const authStore = useAuthStore()

const deleteDialogOpen = ref(false)
const flagDialogOpen = ref(false)

const commentId = computed(() => route.params.id as string)
const commentType = computed((): CommentType => {
  return (route.params.type as CommentType) || 'forum'
})

// Detail lifecycle (first-load skeleton, mount animation, refresh) lives in
// the shared workspace; comments have no secondary refresh.
const { isInitialLoad, isLoaded, refresh } = useDetailWorkspace({
  entityId: commentId,
  fetch: async (id) => {
    await commentsStore.fetchComment(id, commentType.value)
  },
})

const comment = computed(() => commentsStore.currentComment)
const canModerate = computed(() =>
  commentType.value === 'forum'
    ? authStore.hasPermission(
        PERM.MODERATE_FORUM_COMMENT.action,
        PERM.MODERATE_FORUM_COMMENT.resource,
      )
    : authStore.hasPermission(
        PERM.MODERATE_SOLUTION_COMMENT.action,
        PERM.MODERATE_SOLUTION_COMMENT.resource,
      ),
)
const canDelete = computed(() =>
  commentType.value === 'forum'
    ? authStore.hasPermission(PERM.DELETE_FORUM_COMMENT.action, PERM.DELETE_FORUM_COMMENT.resource)
    : authStore.hasPermission(
        PERM.DELETE_SOLUTION_COMMENT.action,
        PERM.DELETE_SOLUTION_COMMENT.resource,
      ),
)

async function unflagComment() {
  if (!comment.value) return
  try {
    await commentsStore.unflagComment(commentId.value, commentType.value)
    toast.success(t('comments.toast.unflaggedSuccessfully'))
    await refresh()
  } catch {
    toast.error(t('comments.toast.failedToUnflag'))
  }
}

async function handleDeleteComment(id: string | number) {
  await commentsStore.deleteComment(String(id), commentType.value)
}

async function handleFlagComment(id: string | number, reason?: string) {
  await commentsStore.flagComment(String(id), commentType.value, reason || '')
}

function handleDeleteSuccess() {
  router.push({ name: 'comments' })
}

function handleFlagSuccess() {
  refresh()
}
</script>

<template>
  <div class="min-h-[calc(100vh-4rem)] bg-background flex flex-col">
    <!-- Terminal Header -->
    <header
      :class="[
        'sticky top-0 z-10 bg-[var(--card)] border-b border-[var(--silver-200)] dark:border-[var(--silver-300)]',
        'transition-all duration-500',
        isLoaded ? 'opacity-100 translate-y-0' : 'opacity-0 -translate-y-2',
      ]"
    >
      <!-- Title Row -->
      <div class="px-4 lg:px-6 py-4 flex items-center justify-between">
        <!-- Left: Back & Title -->
        <div class="flex items-center gap-4 min-w-0">
          <Button
            variant="terminal"
            size="sm"
            class="h-8 w-8 p-0 border-[var(--silver-300)] hover:border-[var(--silver-400)]"
            @click="router.push({ name: 'comments' })"
          >
            <IconArrowLeft class="h-4 w-4" />
          </Button>

          <div v-if="comment" class="flex items-center gap-3 min-w-0">
            <div class="flex items-center gap-2"></div>
            <h1 class="text-base font-medium text-[var(--foreground)] truncate">
              {{ t('comments.detail.title') }}
            </h1>
          </div>
          <Skeleton v-else class="h-5 w-32" />
        </div>

        <!-- Right: Actions & Status -->
        <div v-if="comment" class="flex items-center gap-3">
          <!-- Status Badges -->
          <div class="hidden sm:flex items-center gap-2">
            <TerminalBadge
              v-if="comment.isFlagged"
              variant="error"
              pulse
              :label="t('comments.status.flagged')"
            />
            <TerminalBadge
              v-if="comment.isDeleted"
              variant="error"
              :label="t('comments.status.deleted')"
            />
          </div>

          <!-- Action Buttons -->
          <template v-if="canModerate">
            <Button
              v-if="comment.isFlagged"
              variant="terminal"
              size="sm"
              class="h-8 font-data text-xs border-[var(--terminal-green)] text-[var(--terminal-green)] hover:bg-[color-mix(in_oklch,_var(--terminal-green)_10%,_transparent)]"
              @click="unflagComment"
            >
              <IconFlag class="h-3.5 w-3.5 mr-1.5" />
              <span class="uppercase tracking-wider">{{ t('comments.actions.unflag') }}</span>
            </Button>
            <Button
              v-else
              variant="terminal"
              size="sm"
              class="h-8 font-data text-xs border-[var(--terminal-amber)] text-[var(--terminal-amber)] hover:bg-[color-mix(in_oklch,_var(--terminal-amber)_10%,_transparent)]"
              @click="flagDialogOpen = true"
            >
              <IconFlag class="h-3.5 w-3.5 mr-1.5" />
              <span class="uppercase tracking-wider">{{ t('comments.actions.flag') }}</span>
            </Button>
          </template>

          <Button
            v-if="canDelete"
            variant="terminal"
            size="sm"
            class="h-8 w-8 p-0 border-[var(--terminal-red)] text-[var(--terminal-red)] hover:bg-[color-mix(in_oklch,_var(--terminal-red)_10%,_transparent)]"
            @click="deleteDialogOpen = true"
          >
            <IconTrash class="h-4 w-4" />
          </Button>
        </div>
      </div>
    </header>

    <!-- Main Content -->
    <main class="flex-1 w-full max-w-[1600px] mx-auto p-4 lg:p-6 lg:pt-8">
      <!-- Error State -->
      <div
        v-if="commentsStore.error"
        class="flex flex-col items-center justify-center py-24 text-center"
      >
        <div
          class="w-12 h-12 border border-[var(--terminal-red)] flex items-center justify-center mb-3"
        >
          <IconMessage :size="24" class="text-[var(--terminal-red)]" />
        </div>
        <h2 class="text-sm font-semibold mb-1 font-data">
          {{ t('comments.error.loadingComment') }}
        </h2>
        <p class="text-xs text-[var(--silver-400)] mb-4 font-data">{{ commentsStore.error }}</p>
        <div class="flex gap-2">
          <Button
            variant="terminal"
            size="sm"
            class="font-data text-xs border-[var(--silver-300)]"
            @click="router.push({ name: 'comments' })"
          >
            {{ t('comments.error.back') }}
          </Button>
          <Button
            variant="terminal"
            size="sm"
            class="font-data text-xs border-[var(--accent-electric)] text-[var(--accent-electric)]"
            @click="refresh"
          >
            {{ t('comments.error.retry') }}
          </Button>
        </div>
      </div>

      <!-- Loading State -->
      <div v-else-if="isInitialLoad || commentsStore.loading" class="space-y-6">
        <div class="grid grid-cols-1 lg:grid-cols-12 gap-6">
          <div class="lg:col-span-8 space-y-4">
            <Skeleton class="h-12 w-3/4 rounded-none" />
            <Skeleton class="h-64 w-full rounded-none" />
          </div>
          <div class="lg:col-span-4 space-y-4">
            <Skeleton class="h-32 w-full rounded-none" />
            <Skeleton class="h-32 w-full rounded-none" />
          </div>
        </div>
      </div>

      <!-- Not Found State -->
      <div v-else-if="!comment" class="flex flex-col items-center justify-center py-24 text-center">
        <div
          class="w-12 h-12 border border-[var(--silver-300)] flex items-center justify-center mb-3"
        >
          <IconMessage :size="24" class="text-[var(--silver-400)]" />
        </div>
        <h2 class="text-sm font-semibold mb-1 font-data">
          {{ t('comments.error.commentNotFound') }}
        </h2>
        <p class="text-xs text-[var(--silver-400)] mb-4">
          {{ t('comments.error.notFoundDescription') }}
        </p>
        <Button
          variant="terminal"
          size="sm"
          class="font-data text-xs border-[var(--silver-300)]"
          @click="router.push({ name: 'comments' })"
        >
          {{ t('comments.error.backToComments') }}
        </Button>
      </div>

      <!-- Comment Content -->
      <template v-else>
        <div class="grid grid-cols-1 lg:grid-cols-12 gap-6">
          <!-- Left Column: Comment Content -->
          <div class="lg:col-span-8 space-y-6">
            <!-- Comment Card -->
            <div
              class="border border-[var(--silver-200)] dark:border-[var(--silver-700)] bg-[var(--card)] p-6"
            >
              <!-- Comment Header -->
              <div
                class="flex items-center justify-between mb-4 pb-4 border-b border-[var(--silver-200)] dark:border-[var(--silver-700)]"
              >
                <div class="flex items-center gap-3">
                  <div class="flex items-center gap-2">
                    <IconUser class="h-4 w-4 text-[var(--silver-400)]" />
                    <span class="text-sm font-medium text-[var(--foreground)]">
                      {{ comment.author?.username || t('comments.status.unknown') }}
                    </span>
                  </div>
                  <span class="text-xs text-[var(--silver-400)] font-data tabular-nums">
                    {{ formatDate(comment.createdAt) }}
                  </span>
                </div>
                <div class="flex items-center gap-2">
                  <TerminalBadge
                    :variant="comment.type === 'forum' ? 'info' : 'success'"
                    :label="
                      comment.type === 'forum'
                        ? t('comments.type.forum')
                        : t('comments.type.solution')
                    "
                  />
                </div>
              </div>

              <!-- Comment Body -->
              <div
                class="prose dark:prose-invert max-w-none text-[var(--foreground)] leading-relaxed markdown-content"
                v-html="renderMarkdown(comment.content)"
              >
              </div>

              <!-- Mobile Status Badges -->
              <div
                v-if="comment.isFlagged || comment.isDeleted"
                class="mt-4 pt-4 border-t border-[var(--silver-200)] dark:border-[var(--silver-700)] flex gap-2 sm:hidden"
              >
                <TerminalBadge
                  v-if="comment.isFlagged"
                  variant="error"
                  pulse
                  :label="t('comments.status.flagged')"
                />
                <TerminalBadge
                  v-if="comment.isDeleted"
                  variant="error"
                  :label="t('comments.status.deleted')"
                />
              </div>
            </div>
          </div>

          <!-- Right Column: Metadata -->
          <div class="lg:col-span-4 space-y-6">
            <!-- Parent Info -->
            <div
              class="border border-[var(--silver-200)] dark:border-[var(--silver-700)] bg-[var(--card)] p-4"
            >
              <h3
                class="text-xs font-data uppercase tracking-widest text-[var(--silver-500)] mb-3"
              >
                {{ t('comments.detail.parent') }}
              </h3>
              <div class="flex items-start gap-2">
                <component
                  :is="comment.type === 'forum' ? IconMessage : IconFileText"
                  class="h-4 w-4 text-[var(--silver-400)] mt-0.5 shrink-0"
                />
                <div class="min-w-0">
                  <p class="text-sm text-[var(--foreground)] truncate">
                    {{ comment.parentTitle || t('comments.type.unknown') }}
                  </p>
                  <p class="text-xs text-[var(--silver-400)] mt-1">
                    {{
                      comment.type === 'forum'
                        ? t('comments.type.forum')
                        : t('comments.type.solution')
                    }}
                  </p>
                </div>
              </div>
            </div>

            <!-- Metadata -->
            <div
              class="border border-[var(--silver-200)] dark:border-[var(--silver-700)] bg-[var(--card)] p-4"
            >
              <h3
                class="text-xs font-data uppercase tracking-widest text-[var(--silver-500)] mb-3"
              >
                {{ t('comments.detail.metadata') }}
              </h3>
              <div class="space-y-3">
                <div class="flex justify-between items-center">
                  <span class="text-xs text-[var(--silver-400)] font-data">ID</span>
                  <span class="text-xs text-[var(--foreground)] font-data tabular-nums">{{
                    comment.id
                  }}</span>
                </div>
                <div class="flex justify-between items-center">
                  <span class="text-xs text-[var(--silver-400)] font-data">{{
                    t('comments.columns.author')
                  }}</span>
                  <span class="text-xs text-[var(--foreground)]">{{
                    comment.author?.username
                  }}</span>
                </div>
                <div class="flex justify-between items-center">
                  <span class="text-xs text-[var(--silver-400)] font-data">{{
                    t('comments.columns.created')
                  }}</span>
                  <span class="text-xs text-[var(--foreground)] font-data">{{
                    formatDate(comment.createdAt)
                  }}</span>
                </div>
                <div class="flex justify-between items-center">
                  <span class="text-xs text-[var(--silver-400)] font-data">{{
                    t('comments.columns.status')
                  }}</span>
                  <TerminalBadge
                    v-if="comment.isDeleted"
                    variant="error"
                    :label="t('comments.status.deleted')"
                  />
                  <TerminalBadge
                    v-else-if="comment.isFlagged"
                    variant="warning"
                    :label="t('comments.status.flagged')"
                  />
                  <TerminalBadge v-else variant="success" :label="t('comments.status.active')" />
                </div>
              </div>
            </div>
          </div>
        </div>
      </template>
    </main>

    <!-- Dialogs -->
    <EntityActionDialog
      v-model:open="deleteDialogOpen"
      :entity-id="commentId"
      :entity-title="comment?.content || null"
      action="delete"
      :title="t('comments.delete.title')"
      :description="t('comments.delete.description')"
      :confirm-label="t('comments.delete.confirm')"
      :cancel-label="t('comments.delete.cancel')"
      :success-label="t('comments.toast.deletedSuccessfully')"
      :error-label="t('comments.toast.failedToDelete')"
      :on-action="handleDeleteComment"
      @success="handleDeleteSuccess"
    />

    <EntityActionDialog
      v-model:open="flagDialogOpen"
      :entity-id="commentId"
      action="flag"
      :title="t('comments.flag.title')"
      :description="t('comments.flag.description')"
      :confirm-label="t('comments.flag.confirm')"
      :cancel-label="t('comments.flag.cancel')"
      :success-label="t('comments.toast.flaggedSuccessfully')"
      :error-label="t('comments.toast.failedToFlag')"
      :reason-label="t('comments.flag.reasonLabel')"
      :reason-placeholder="t('comments.flag.reasonPlaceholder')"
      :reason-required-label="t('comments.toast.reasonRequired')"
      :on-action="handleFlagComment"
      @success="handleFlagSuccess"
    />
  </div>
</template>
