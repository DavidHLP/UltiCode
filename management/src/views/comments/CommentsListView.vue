<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { toast } from 'vue-sonner'
import {
  IconCheck,
  IconMessage,
  IconTrash,
  IconUser,
  IconFileText,
} from '@tabler/icons-vue'

import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from '@/components/ui/dialog'

import { useCommentsStore } from '@/stores/admin/comments'
import { useAuthStore } from '@/stores/auth'
import type { Comment, CommentType } from '@/api/admin/comments'

import DataTable from '@/components/table/DataTable.vue'
import DataTableToolbar, { type Filter } from '@/components/table/DataTableToolbar.vue'
import EntityActionDialog from '@/components/shared/EntityActionDialog.vue'
import { useDataTable } from '@/composables/useDataTable'
import { createColumns } from './columns'
import { renderMarkdown } from '@/utils/markdown'

const { t } = useI18n()
const commentsStore = useCommentsStore()
const authStore = useAuthStore()

const typeFilter = ref<CommentType | 'all'>('all')
const flaggedFilter = ref<string>('all')
const deletedFilter = ref<string>('all')

const selectedCommentId = ref<string | null>(null)
const selectedCommentType = ref<CommentType | null>(null)
const selectedCommentContent = ref<string | null>(null)
const deleteDialogOpen = ref(false)
const flagDialogOpen = ref(false)

const detailDialogOpen = ref(false)
const detailComment = ref<Comment | null>(null)

const bulkActionLoading = ref(false)

// Animation state for staggered reveal
const isLoaded = ref(false)

onMounted(() => {
  setTimeout(() => {
    isLoaded.value = true
  }, 100)
})

const canModerateForum = computed(() => authStore.hasPermission('MODERATE', 'FORUM_COMMENT'))
const canModerateSolution = computed(() => authStore.hasPermission('MODERATE', 'SOLUTION_COMMENT'))

// Stats for terminal ticker
const stats = computed(() => {
  const comments = commentsStore.comments.filter(Boolean)
  const total = commentsStore.total
  const flagged = comments.filter((c) => c.isFlagged).length
  const forumCount = comments.filter((c) => c.type === 'forum').length
  const solutionCount = comments.filter((c) => c.type === 'solution').length
  return { total, flagged, forumCount, solutionCount }
})

const toolbarFilters = computed<Filter[]>(() => [
  {
    modelValue: typeFilter.value,
    placeholder: t('comments.filters.type'),
    options: [
      { value: 'all', label: t('comments.filters.allTypes') },
      { value: 'forum', label: t('comments.type.forum') },
      { value: 'solution', label: t('comments.type.solution') },
    ],
  },
  {
    modelValue: flaggedFilter.value,
    placeholder: t('comments.filters.flagStatus'),
    width: 'w-[140px]',
    options: [
      { value: 'all', label: t('comments.filters.all') },
      { value: 'flagged', label: t('comments.filters.flagged') },
      { value: 'clean', label: t('comments.filters.clean') },
    ],
  },
  {
    modelValue: deletedFilter.value,
    placeholder: t('comments.filters.deletedStatus'),
    width: 'w-[140px]',
    options: [
      { value: 'all', label: t('comments.filters.all') },
      { value: 'deleted', label: t('comments.filters.deleted') },
      { value: 'active', label: t('comments.filters.active') },
    ],
  },
])

const {
  searchQuery,
  tablePagination,
  selectedRows,
  loading,
  data,
  total,
  error,
  loadEntities: loadComments,
} = useDataTable<
  Comment,
  { type: CommentType | 'all'; flaggedFilter: string; deletedFilter: string },
  Parameters<typeof commentsStore.fetchComments>[0]
>({
  store: {
    data: computed(() => commentsStore.comments),
    total: computed(() => commentsStore.total),
    isLoading: computed(() => commentsStore.loading),
    error: computed(() => commentsStore.error),
    fetch: (params) => commentsStore.fetchComments(params),
  },
  filters: () => ({
    type: typeFilter.value,
    flaggedFilter: flaggedFilter.value,
    deletedFilter: deletedFilter.value,
  }),
  transformParams: ({ search, filters, page, limit }) => ({
    search,
    type: filters.type === 'all' ? undefined : filters.type,
    isFlagged: filters.flaggedFilter === 'all' ? undefined : filters.flaggedFilter === 'flagged',
    isDeleted: filters.deletedFilter === 'all' ? undefined : filters.deletedFilter === 'deleted',
    page,
    limit,
  }),
  autoLoad: true,
})

function confirmDelete(comment: Comment) {
  selectedCommentId.value = comment.id
  selectedCommentType.value = comment.type
  selectedCommentContent.value = comment.content
  deleteDialogOpen.value = true
}

function openFlagDialog(comment: Comment) {
  selectedCommentId.value = comment.id
  selectedCommentType.value = comment.type
  selectedCommentContent.value = comment.content
  flagDialogOpen.value = true
}

async function handleDeleteComment(id: string | number) {
  if (!selectedCommentType.value) return
  await commentsStore.deleteComment(String(id), selectedCommentType.value)
}

async function handleFlagComment(id: string | number, reason?: string) {
  if (!selectedCommentType.value) return
  await commentsStore.flagComment(String(id), selectedCommentType.value, reason || '')
}

async function unflagComment(comment: Comment) {
  try {
    await commentsStore.unflagComment(comment.id, comment.type)
    toast.success(t('comments.toast.unflaggedSuccessfully'))
  } catch {
    toast.error(t('comments.toast.failedToUnflag'))
  }
}

function viewCommentDetails(comment: Comment) {
  detailComment.value = comment
  detailDialogOpen.value = true
}

function canModerate(comment: Comment) {
  if (comment.type === 'forum') return canModerateForum.value
  if (comment.type === 'solution') return canModerateSolution.value
  return false
}

const columns = createColumns(
  t,
  {
    viewCommentDetails,
    unflagComment,
    openFlagDialog,
    confirmDelete,
  },
  canModerate,
)

async function handleBulkUnflag() {
  if (selectedRows.value.length === 0) return

  // Group by type for API call
  const byType = selectedRows.value.reduce(
    (acc, r) => {
      if (!acc[r.type]) acc[r.type] = []
      acc[r.type].push(r.id)
      return acc
    },
    {} as Record<CommentType, string[]>,
  )

  bulkActionLoading.value = true
  try {
    await Promise.all(
      Object.entries(byType).map(([type, ids]) =>
        commentsStore.bulkAction({ ids, type: type as CommentType, action: 'unflag' }),
      ),
    )
    await loadComments()
    selectedRows.value = []
    toast.success(t('comments.toast.bulkUnflaggedSuccessfully'))
  } catch {
    toast.error(t('comments.toast.failedToBulkUnflag'))
  } finally {
    bulkActionLoading.value = false
  }
}

const bulkDeleteDialogOpen = ref(false)

async function handleBulkDelete() {
  if (selectedRows.value.length === 0) return
  bulkDeleteDialogOpen.value = true
}

async function handleBulkDeleteConfirm() {
  if (selectedRows.value.length === 0) return

  // Group by type for API call
  const byType = selectedRows.value.reduce(
    (acc, r) => {
      if (!acc[r.type]) acc[r.type] = []
      acc[r.type].push(r.id)
      return acc
    },
    {} as Record<CommentType, string[]>,
  )

  bulkActionLoading.value = true
  try {
    await Promise.all(
      Object.entries(byType).map(([type, ids]) =>
        commentsStore.bulkAction({ ids, type: type as CommentType, action: 'delete' }),
      ),
    )
    await loadComments()
    selectedRows.value = []
    toast.success(t('comments.toast.bulkDeletedSuccessfully'))
  } catch {
    toast.error(t('comments.toast.failedToBulkDelete'))
  } finally {
    bulkActionLoading.value = false
  }
}
</script>

<template>
  <div class="relative flex flex-col gap-4 w-full min-w-0">
    <!-- Terminal Header -->
    <div
      :class="[
        'border border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--card)]',
        'transition-all duration-500',
        isLoaded ? 'opacity-100 translate-y-0' : 'opacity-0 -translate-y-2',
      ]"
    >
      <!-- Title Row -->
      <div class="px-4 lg:px-6 py-4 flex items-center justify-between">
        <h1 class="text-xl font-medium tracking-tight text-[var(--foreground)]">
          {{ t('comments.title') }}
        </h1>
      </div>

      <!-- Stats Ticker -->
      <div
        class="px-4 lg:px-6 py-2.5 flex items-center gap-6 border-t border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--surface-sunken)]"
      >
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]">{{ t('comments.stats.total') }}:</span>
          <span class="font-data text-sm text-[var(--terminal-cyan)] tabular-nums">{{
            stats.total
          }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]">{{ t('comments.stats.flagged') }}:</span>
          <span class="font-data text-sm text-[var(--terminal-amber)] tabular-nums">{{
            stats.flagged
          }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]">{{ t('comments.stats.forum') }}:</span>
          <span class="font-data text-sm text-[var(--terminal-cyan)] tabular-nums">{{
            stats.forumCount
          }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]">{{ t('comments.stats.solution') }}:</span>
          <span class="font-data text-sm text-[var(--terminal-green)] tabular-nums">{{
            stats.solutionCount
          }}</span>
        </div>
        <div class="ml-auto flex items-center gap-2 text-[var(--silver-400)]">
          <IconMessage class="h-4 w-4" />
          <span class="text-xs font-data uppercase tracking-wider">{{ t('comments.stats.commentModeration') }}</span>
        </div>
      </div>
    </div>

    <!-- Bulk Action Bar - Terminal Style -->
    <div
      v-if="selectedRows.length > 0"
      :class="[
        'mt-4 flex items-center justify-between border border-[var(--terminal-amber)] bg-[color-mix(in_oklch,_var(--terminal-amber)_8%,_transparent)] dark:bg-[color-mix(in_oklch,_var(--terminal-amber)_15%,_transparent)] p-3',
        'animate-in fade-in slide-in-from-top-2 duration-200',
      ]"
    >
      <div class="flex items-center gap-4">
        <div class="flex items-center gap-2">
          <span class="font-data text-sm text-[var(--terminal-amber)]">
            &gt; SELECTED:{{ selectedRows.length }}
          </span>
        </div>
        <div class="h-4 w-px bg-[var(--silver-300)]" />
        <div class="flex items-center gap-2">
          <Button
            variant="terminal"
            size="sm"
            class="h-8 font-data text-xs border-[var(--silver-300)] hover:border-[var(--terminal-green)] hover:text-[var(--terminal-green)]"
            @click="handleBulkUnflag"
            :disabled="bulkActionLoading"
          >
            <IconCheck class="h-3.5 w-3.5 mr-1.5" />
            <span class="uppercase tracking-wider">{{ t('comments.bulkActions.bulkUnflag') }}</span>
          </Button>
          <Button
            variant="terminal"
            size="sm"
            class="h-8 font-data text-xs border-[var(--terminal-red)] text-[var(--terminal-red)] hover:bg-[color-mix(in_oklch,_var(--terminal-red)_10%,_transparent)]"
            @click="handleBulkDelete"
            :disabled="bulkActionLoading"
          >
            <IconTrash class="h-3.5 w-3.5 mr-1.5" />
            <span class="uppercase tracking-wider">{{ t('comments.bulkActions.bulkDelete') }}</span>
          </Button>
        </div>
      </div>
      <Button
        variant="terminal"
        size="sm"
        class="h-8 font-data text-xs text-[var(--silver-500)] hover:text-[var(--foreground)]"
        @click="selectedRows = []"
      >
        [ESC] {{ t('comments.clearSelection') }}
      </Button>
    </div>

    <!-- Main Content Area -->
    <div class="flex-1">
      <DataTable
        :columns="columns"
        :data="data"
        :pagination="tablePagination"
        :row-count="total"
        :loading="loading"
        v-model:selected-rows="selectedRows"
        @update:pagination="tablePagination = $event"
        class="terminal-table"
      >
        <template #toolbar-left>
          <DataTableToolbar
            :search-model-value="searchQuery"
            @update:search-model-value="searchQuery = $event"
            :search-placeholder="t('comments.searchPlaceholder')"
            :filters="toolbarFilters"
            @update:filter="
              (index, value) => {
                if (index === 0) typeFilter = value as CommentType | 'all'
                else if (index === 1) flaggedFilter = String(value)
                else deletedFilter = String(value)
              }
            "
            :loading="loading"
            :on-refresh="loadComments"
          />
        </template>
      </DataTable>

      <!-- Error state - Terminal Style -->
      <div
        v-if="error"
        class="mt-4 flex items-center justify-between border border-[var(--terminal-red)] bg-[color-mix(in_oklch,_var(--terminal-red)_8%,_transparent)] p-4"
      >
        <div class="flex items-center gap-3">
          <span class="font-data text-sm text-[var(--terminal-red)]">&gt; ERROR:</span>
          <span class="text-sm text-[var(--foreground)]">{{ error }}</span>
        </div>
        <Button
          variant="terminal"
          size="sm"
          class="font-data text-xs border-[var(--terminal-red)] text-[var(--terminal-red)] hover:bg-[color-mix(in_oklch,_var(--terminal-red)_10%,_transparent)]"
          @click="loadComments()"
        >
          {{ t('common.retry') }}
        </Button>
      </div>
    </div>
  </div>

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
    @success="loadComments"
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
    @success="loadComments"
  />

  <EntityActionDialog
    v-model:open="bulkDeleteDialogOpen"
    entity-id="bulk"
    action="delete"
    :title="t('comments.delete.title')"
    :description="t('comments.delete.description')"
    :confirm-label="t('comments.delete.confirm')"
    :cancel-label="t('comments.delete.cancel')"
    :success-label="t('comments.toast.bulkDeletedSuccessfully')"
    :error-label="t('comments.toast.failedToBulkDelete')"
    :on-action="handleBulkDeleteConfirm"
    @success="loadComments"
  />

  <!-- Comment Detail Dialog -->
  <Dialog v-model:open="detailDialogOpen">
    <DialogContent class="sm:max-w-lg border-[var(--silver-200)] dark:border-[var(--silver-700)]">
      <DialogHeader>
        <DialogTitle class="font-data text-sm uppercase tracking-wider text-[var(--terminal-cyan)]">
          {{ t('comments.detail.title') }}
        </DialogTitle>
        <DialogDescription class="sr-only">
          {{ t('comments.detail.title') }}
        </DialogDescription>
      </DialogHeader>
      <div v-if="detailComment" class="space-y-4 py-2">
        <!-- Comment Content -->
        <div class="space-y-1.5">
          <span class="terminal-label text-[var(--silver-500)]">{{
            t('comments.columns.content')
          }}</span>
          <div
            class="prose dark:prose-invert max-w-none text-sm text-[var(--foreground)] leading-relaxed rounded-none border border-[var(--silver-200)] dark:border-[var(--silver-700)] bg-[var(--surface-sunken)] p-3 markdown-content"
            v-html="renderMarkdown(detailComment.content)"
          >
          </div>
        </div>
        <!-- Type & Author Row -->
        <div class="grid grid-cols-2 gap-4">
          <div class="space-y-1.5">
            <span class="terminal-label text-[var(--silver-500)]">{{
              t('comments.columns.type')
            }}</span>
            <div class="flex items-center gap-2 text-sm text-[var(--foreground)]">
              <component
                :is="detailComment.type === 'forum' ? IconMessage : IconFileText"
                class="h-4 w-4 text-[var(--silver-400)]"
              />
              <span>{{
                detailComment.type === 'forum'
                  ? t('comments.type.forum')
                  : t('comments.type.solution')
              }}</span>
            </div>
          </div>
          <div class="space-y-1.5">
            <span class="terminal-label text-[var(--silver-500)]">{{
              t('comments.columns.author')
            }}</span>
            <div class="flex items-center gap-2 text-sm text-[var(--foreground)]">
              <IconUser class="h-4 w-4 text-[var(--silver-400)]" />
              <span>{{ detailComment.author?.username || t('comments.status.unknown') }}</span>
            </div>
          </div>
        </div>
        <!-- Parent Title -->
        <div v-if="detailComment.parentTitle" class="space-y-1.5">
          <span class="terminal-label text-[var(--silver-500)]">{{
            t('comments.detail.parent')
          }}</span>
          <p class="text-sm text-[var(--foreground)]">{{ detailComment.parentTitle }}</p>
        </div>
        <!-- Status & Time Row -->
        <div class="grid grid-cols-2 gap-4">
          <div class="space-y-1.5">
            <span class="terminal-label text-[var(--silver-500)]">{{
              t('comments.columns.status')
            }}</span>
            <span
              :class="[
                'font-data text-xs uppercase tracking-wider',
                detailComment.isDeleted
                  ? 'text-[var(--terminal-red)]'
                  : detailComment.isFlagged
                    ? 'text-[var(--terminal-amber)]'
                    : 'text-[var(--terminal-green)]',
              ]"
            >
              {{
                detailComment.isDeleted
                  ? t('comments.status.deleted')
                  : detailComment.isFlagged
                    ? t('comments.status.flagged')
                    : t('comments.status.active')
              }}
            </span>
          </div>
          <div class="space-y-1.5">
            <span class="terminal-label text-[var(--silver-500)]">{{
              t('comments.columns.created')
            }}</span>
            <span class="font-data text-xs text-[var(--silver-400)] tabular-nums">{{
              detailComment.createdAt
            }}</span>
          </div>
        </div>
        <!-- Flag Reason (if flagged) -->
        <div v-if="detailComment.isFlagged && detailComment.flaggedReason" class="space-y-1.5">
          <span class="terminal-label text-[var(--silver-500)]">{{
            t('comments.flag.reasonLabel')
          }}</span>
          <p class="text-sm text-[var(--terminal-amber)]">{{ detailComment.flaggedReason }}</p>
        </div>
      </div>
    </DialogContent>
  </Dialog>
</template>
