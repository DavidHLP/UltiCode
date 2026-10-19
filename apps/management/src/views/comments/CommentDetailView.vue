<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  ArrowLeft as IconArrowLeft,
  Flag as IconFlag,
  Trash as IconTrash,
  MessageSquare as IconMessage,
  FileText as IconFileText,
  User as IconUser,
} from '@lucide/vue'

import { Button } from '@ulticode/design-system/ui/button'
import { Skeleton } from '@ulticode/design-system/ui/skeleton'
import StatusBadge from '@ulticode/design-system/StatusBadge.vue'

import { useCommentsStore } from '@/stores/admin/comments'
import type { CommentType } from '@/api/admin/comments'
import { formatDate } from '@/lib/format/date'
import EntityActionDialog from '@/components/shared/EntityActionDialog.vue'
import { renderMarkdown } from '@ulticode/markdown-utils'
import { useDetailWorkspace } from '@/composables/useDetailWorkspace'
import { useCommentModeration } from '@/composables/useCommentModeration'

const router = useRouter()
const route = useRoute()
const { t } = useI18n()
const commentsStore = useCommentsStore()

const commentId = computed(() => route.params.id as string)
const commentType = computed((): CommentType => {
  return (route.params.type as CommentType) || 'forum'
})

const {
  canModerate,
  canDelete,
  selectedCommentId,
  selectedCommentContent,
  deleteDialogOpen,
  flagDialogOpen,
  confirmDelete,
  openFlagDialog,
  handleDeleteComment,
  handleFlagComment,
  unflagComment,
} = useCommentModeration()

// Detail lifecycle (first-load skeleton, mount animation, refresh) lives in
// the shared workspace; comments have no secondary refresh.
const { isInitialLoad, isLoaded, refresh } = useDetailWorkspace({
  entityId: commentId,
  fetch: async (id) => {
    await commentsStore.fetchComment(id, commentType.value)
  },
})

const comment = computed(() => commentsStore.currentComment)

async function doUnflag() {
  if (!comment.value) return
  await unflagComment({ id: commentId.value, type: commentType.value })
  await refresh()
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
    <!-- Page header -->
    <header
      :class="[
        'sticky top-0 z-10 bg-card border-b border-border dark:border-border',
        'transition-all duration-500',
        isLoaded ? 'opacity-100 translate-y-0' : 'opacity-0 -translate-y-2',
      ]"
    >
      <!-- Title Row -->
      <div class="px-4 lg:px-6 py-4 flex items-center justify-between">
        <!-- Left: Back & Title -->
        <div class="flex items-center gap-4 min-w-0">
          <Button
            variant="outline"
            size="sm"
            class="w-8 p-0"
            @click="router.push({ name: 'comments' })"
          >
            <IconArrowLeft class="h-4 w-4" />
          </Button>

          <div v-if="comment" class="flex items-center gap-3 min-w-0">
            <div class="flex items-center gap-2"></div>
            <h1 class="text-base font-medium text-foreground truncate">
              {{ t('comments.detail.title') }}
            </h1>
          </div>
          <Skeleton v-else class="h-5 w-32" />
        </div>

        <!-- Right: Actions & Status -->
        <div v-if="comment" class="flex items-center gap-3">
          <!-- Status Badges -->
          <div class="hidden sm:flex items-center gap-2">
            <StatusBadge
              v-if="comment.isFlagged"
              variant="error"
              pulse
              :label="t('comments.status.flagged')"
            />
            <StatusBadge
              v-if="comment.isDeleted"
              variant="error"
              :label="t('comments.status.deleted')"
            />
          </div>

          <!-- Action Buttons -->
          <template v-if="canModerate(comment)">
            <Button v-if="comment.isFlagged" variant="outline" size="sm" @click="doUnflag">
              <IconFlag class="h-3.5 w-3.5 mr-1.5" />
              <span class="uppercase tracking-wider">{{ t('comments.actions.unflag') }}</span>
            </Button>
            <Button v-else variant="outline" size="sm" @click="comment && openFlagDialog(comment)">
              <IconFlag class="h-3.5 w-3.5 mr-1.5" />
              <span class="uppercase tracking-wider">{{ t('comments.actions.flag') }}</span>
            </Button>
          </template>

          <Button
            v-if="canDelete(comment)"
            variant="outline"
            size="sm"
            class="w-8 p-0"
            @click="comment && confirmDelete(comment)"
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
          class="w-12 h-12 border border-status-error-mark flex items-center justify-center mb-3"
        >
          <IconMessage :size="24" class="text-status-error-mark" />
        </div>
        <h2 class="text-sm font-semibold mb-1 font-mono">
          {{ t('comments.error.loadingComment') }}
        </h2>
        <p class="text-xs text-muted-foreground mb-4 font-mono">
          {{ commentsStore.error }}
        </p>
        <div class="flex gap-2">
          <Button variant="outline" size="sm" @click="router.push({ name: 'comments' })">
            {{ t('comments.error.back') }}
          </Button>
          <Button variant="outline" size="sm" @click="refresh">
            {{ t('comments.error.retry') }}
          </Button>
        </div>
      </div>

      <!-- Loading State -->
      <div v-else-if="isInitialLoad || commentsStore.loading" class="space-y-6">
        <div class="grid grid-cols-1 lg:grid-cols-12 gap-6">
          <div class="lg:col-span-8 space-y-4">
            <Skeleton class="h-12 w-3/4" />
            <Skeleton class="h-64 w-full" />
          </div>
          <div class="lg:col-span-4 space-y-4">
            <Skeleton class="h-32 w-full" />
            <Skeleton class="h-32 w-full" />
          </div>
        </div>
      </div>

      <!-- Not Found State -->
      <div v-else-if="!comment" class="flex flex-col items-center justify-center py-24 text-center">
        <div class="w-12 h-12 border border-border flex items-center justify-center mb-3">
          <IconMessage :size="24" class="text-muted-foreground" />
        </div>
        <h2 class="text-sm font-semibold mb-1 font-mono">
          {{ t('comments.error.commentNotFound') }}
        </h2>
        <p class="text-xs text-muted-foreground mb-4">
          {{ t('comments.error.notFoundDescription') }}
        </p>
        <Button variant="outline" size="sm" @click="router.push({ name: 'comments' })">
          {{ t('comments.error.backToComments') }}
        </Button>
      </div>

      <!-- Comment Content -->
      <template v-else>
        <div class="grid grid-cols-1 lg:grid-cols-12 gap-6">
          <!-- Left Column: Comment Content -->
          <div class="lg:col-span-8 space-y-6">
            <!-- Comment Card -->
            <div class="border border-border dark:border-foreground bg-card p-6">
              <!-- Comment Header -->
              <div
                class="flex items-center justify-between mb-4 pb-4 border-b border-border dark:border-foreground"
              >
                <div class="flex items-center gap-3">
                  <div class="flex items-center gap-2">
                    <IconUser class="h-4 w-4 text-muted-foreground" />
                    <span class="text-sm font-medium text-foreground">
                      {{ comment.author?.username || t('comments.status.unknown') }}
                    </span>
                  </div>
                  <span class="text-xs text-muted-foreground font-mono tabular-nums">
                    {{ formatDate(comment.createdAt) }}
                  </span>
                </div>
                <div class="flex items-center gap-2">
                  <StatusBadge
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
                class="typeset typeset-docs max-w-none text-foreground leading-relaxed markdown-content"
                v-html="renderMarkdown(comment.content)"
              ></div>

              <!-- Mobile Status Badges -->
              <div
                v-if="comment.isFlagged || comment.isDeleted"
                class="mt-4 pt-4 border-t border-border dark:border-foreground flex gap-2 sm:hidden"
              >
                <StatusBadge
                  v-if="comment.isFlagged"
                  variant="error"
                  pulse
                  :label="t('comments.status.flagged')"
                />
                <StatusBadge
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
            <div class="border border-border dark:border-foreground bg-card p-4">
              <h3 class="text-xs font-mono uppercase tracking-widest text-muted-foreground mb-3">
                {{ t('comments.detail.parent') }}
              </h3>
              <div class="flex items-start gap-2">
                <component
                  :is="comment.type === 'forum' ? IconMessage : IconFileText"
                  class="h-4 w-4 text-muted-foreground mt-0.5 shrink-0"
                />
                <div class="min-w-0">
                  <p class="text-sm text-foreground truncate">
                    {{ comment.parentTitle || t('comments.type.unknown') }}
                  </p>
                  <p class="text-xs text-muted-foreground mt-1">
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
            <div class="border border-border dark:border-foreground bg-card p-4">
              <h3 class="text-xs font-mono uppercase tracking-widest text-muted-foreground mb-3">
                {{ t('comments.detail.metadata') }}
              </h3>
              <div class="space-y-3">
                <div class="flex justify-between items-center">
                  <span class="text-xs text-muted-foreground font-mono">ID</span>
                  <span class="text-xs text-foreground font-mono tabular-nums">{{
                    comment.id
                  }}</span>
                </div>
                <div class="flex justify-between items-center">
                  <span class="text-xs text-muted-foreground font-mono">{{
                    t('comments.columns.author')
                  }}</span>
                  <span class="text-xs text-foreground">{{ comment.author?.username }}</span>
                </div>
                <div class="flex justify-between items-center">
                  <span class="text-xs text-muted-foreground font-mono">{{
                    t('comments.columns.created')
                  }}</span>
                  <span class="text-xs text-foreground font-mono">{{
                    formatDate(comment.createdAt)
                  }}</span>
                </div>
                <div class="flex justify-between items-center">
                  <span class="text-xs text-muted-foreground font-mono">{{
                    t('comments.columns.status')
                  }}</span>
                  <StatusBadge
                    v-if="comment.isDeleted"
                    variant="error"
                    :label="t('comments.status.deleted')"
                  />
                  <StatusBadge
                    v-else-if="comment.isFlagged"
                    variant="warning"
                    :label="t('comments.status.flagged')"
                  />
                  <StatusBadge v-else variant="success" :label="t('comments.status.active')" />
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
      :entity-id="selectedCommentId"
      :entity-title="selectedCommentContent"
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
      :entity-id="selectedCommentId"
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
