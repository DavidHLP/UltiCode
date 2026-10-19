<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  Check as IconCheck,
  MessageSquare as IconMessage,
  Trash as IconTrash,
  User as IconUser,
  FileText as IconFileText,
} from '@lucide/vue'

import { Button } from '@ulticode/design-system/ui/button'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from '@ulticode/design-system/ui/dialog'

import { useCommentsStore } from '@/stores/admin/comments'
import type { Comment, CommentType } from '@/api/admin/comments'

import DataTable from '@/components/table/DataTable.vue'
import DataTableToolbar, { type Filter } from '@/components/table/DataTableToolbar.vue'
import EntityActionDialog from '@/components/shared/EntityActionDialog.vue'
import { useRemoteTable } from '@/composables/useRemoteTable'
import { useCommentModeration } from '@/composables/useCommentModeration'
import { createColumns } from './columns'
import { renderMarkdown } from '@ulticode/markdown-utils'

const { t } = useI18n()
const commentsStore = useCommentsStore()

const detailDialogOpen = ref(false)
const detailComment = ref<Comment | null>(null)
const selectedRows = ref<Comment[]>([])

// Animation state for staggered reveal
const isLoaded = ref(false)

onMounted(() => {
  setTimeout(() => {
    isLoaded.value = true
  }, 100)
})

const {
  query,
  searchQuery,
  tablePagination,
  loading,
  data,
  total,
  error,
  refresh: loadComments,
  setFilters,
} = useRemoteTable<
  Comment,
  { type: CommentType | 'all'; flaggedFilter: string; deletedFilter: string },
  Parameters<typeof commentsStore.fetchComments>[0]
>({
  store: commentsStore,
  initialQuery: {
    filters: { type: 'all', flaggedFilter: 'all', deletedFilter: 'all' },
  },
  toParams: ({ search, filters, page, limit }) => ({
    search,
    type: filters.type === 'all' ? undefined : filters.type,
    isFlagged: filters.flaggedFilter === 'all' ? undefined : filters.flaggedFilter === 'flagged',
    isDeleted: filters.deletedFilter === 'all' ? undefined : filters.deletedFilter === 'deleted',
    page,
    limit,
  }),
  autoLoad: true,
})

const typeFilter = computed({
  get: () => query.value.filters.type,
  set: (type: CommentType | 'all') => setFilters({ ...query.value.filters, type }),
})
const flaggedFilter = computed({
  get: () => query.value.filters.flaggedFilter,
  set: (flaggedFilter: string) => setFilters({ ...query.value.filters, flaggedFilter }),
})
const deletedFilter = computed({
  get: () => query.value.filters.deletedFilter,
  set: (deletedFilter: string) => setFilters({ ...query.value.filters, deletedFilter }),
})

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
  selectedCommentId,
  selectedCommentContent,
  deleteDialogOpen,
  flagDialogOpen,
  bulkDeleteDialogOpen,
  bulkActionLoading,
  canModerate,
  confirmDelete,
  openFlagDialog,
  promptBulkDelete,
  handleDeleteComment,
  handleFlagComment,
  unflagComment,
  bulkUnflag,
  bulkDelete,
} = useCommentModeration({ refresh: loadComments })

function viewCommentDetails(comment: Comment) {
  detailComment.value = comment
  detailDialogOpen.value = true
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
  const ok = await bulkUnflag(selectedRows.value)
  if (ok) selectedRows.value = []
}

function handleBulkDelete() {
  if (selectedRows.value.length === 0) return
  promptBulkDelete()
}

async function handleBulkDeleteConfirm() {
  if (selectedRows.value.length === 0) return
  const ok = await bulkDelete(selectedRows.value)
  if (ok) selectedRows.value = []
}
</script>

<template>
  <div class="relative flex flex-col gap-4 w-full min-w-0">
    <!-- Page header -->
    <div
      :class="[
        'border border-border dark:border-border bg-card',
        'transition-all duration-500',
        isLoaded ? 'opacity-100 translate-y-0' : 'opacity-0 -translate-y-2',
      ]"
    >
      <!-- Title Row -->
      <div class="px-4 lg:px-6 py-4 flex items-center justify-between">
        <h1 class="text-xl font-medium tracking-tight text-foreground">
          {{ t('comments.title') }}
        </h1>
      </div>

      <!-- Stats Ticker -->
      <div
        class="px-4 lg:px-6 py-2.5 flex items-center gap-6 border-t border-border dark:border-border bg-muted"
      >
        <div class="flex items-center gap-2">
          <span class="text-sm font-medium text-muted-foreground"
            >{{ t('comments.stats.total') }}:</span
          >
          <span class="font-mono text-sm text-foreground tabular-nums">{{ stats.total }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="text-sm font-medium text-muted-foreground"
            >{{ t('comments.stats.flagged') }}:</span
          >
          <span class="font-mono text-sm text-foreground tabular-nums">{{ stats.flagged }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="text-sm font-medium text-muted-foreground"
            >{{ t('comments.stats.forum') }}:</span
          >
          <span class="font-mono text-sm text-foreground tabular-nums">{{ stats.forumCount }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="text-sm font-medium text-muted-foreground"
            >{{ t('comments.stats.solution') }}:</span
          >
          <span class="font-mono text-sm text-foreground tabular-nums">{{
            stats.solutionCount
          }}</span>
        </div>
        <div class="ml-auto flex items-center gap-2 text-muted-foreground">
          <IconMessage class="h-4 w-4" />
          <span class="text-xs font-mono uppercase tracking-wider">{{
            t('comments.stats.commentModeration')
          }}</span>
        </div>
      </div>
    </div>

    <!-- Bulk Action Bar - Terminal Style -->
    <div
      v-if="selectedRows.length > 0"
      :class="[
        'mt-4 flex items-center justify-between border border-status-warning-mark bg-[color-mix(in_oklch,_var(--status-warning-mark)_8%,_transparent)] dark:bg-[color-mix(in_oklch,_var(--status-warning-mark)_15%,_transparent)] p-3',
        'animate-in fade-in slide-in-from-top-2 duration-200',
      ]"
    >
      <div class="flex items-center gap-4">
        <div class="flex items-center gap-2">
          <span class="font-mono text-sm text-foreground">
            &gt; SELECTED:{{ selectedRows.length }}
          </span>
        </div>
        <div class="h-4 w-px bg-border" />
        <div class="flex items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            @click="handleBulkUnflag"
            :disabled="bulkActionLoading"
          >
            <IconCheck class="h-3.5 w-3.5 mr-1.5" />
            <span class="uppercase tracking-wider">{{ t('comments.bulkActions.bulkUnflag') }}</span>
          </Button>
          <Button
            variant="outline"
            size="sm"
            @click="handleBulkDelete"
            :disabled="bulkActionLoading"
          >
            <IconTrash class="h-3.5 w-3.5 mr-1.5" />
            <span class="uppercase tracking-wider">{{ t('comments.bulkActions.bulkDelete') }}</span>
          </Button>
        </div>
      </div>
      <Button variant="outline" size="sm" @click="selectedRows = []">
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
        class="w-full text-sm"
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
        class="mt-4 flex items-center justify-between border border-status-error-mark bg-[color-mix(in_oklch,_var(--status-error-mark)_8%,_transparent)] p-4"
      >
        <div class="flex items-center gap-3">
          <span class="font-mono text-sm text-foreground">&gt; ERROR:</span>
          <span class="text-sm text-foreground">{{ error }}</span>
        </div>
        <Button variant="outline" size="sm" @click="loadComments()">
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
    <DialogContent class="sm:max-w-lg">
      <DialogHeader>
        <DialogTitle>
          {{ t('comments.detail.title') }}
        </DialogTitle>
        <DialogDescription class="sr-only">
          {{ t('comments.detail.title') }}
        </DialogDescription>
      </DialogHeader>
      <div v-if="detailComment" class="space-y-4 py-2">
        <!-- Comment Content -->
        <div class="space-y-1.5">
          <span class="text-sm font-medium text-muted-foreground">{{
            t('comments.columns.content')
          }}</span>
          <div
            class="typeset typeset-docs max-w-none text-sm text-foreground leading-relaxed rounded-lg border border-border dark:border-foreground bg-muted p-3 markdown-content"
            v-html="renderMarkdown(detailComment.content)"
          ></div>
        </div>
        <!-- Type & Author Row -->
        <div class="grid grid-cols-2 gap-4">
          <div class="space-y-1.5">
            <span class="text-sm font-medium text-muted-foreground">{{
              t('comments.columns.type')
            }}</span>
            <div class="flex items-center gap-2 text-sm text-foreground">
              <component
                :is="detailComment.type === 'forum' ? IconMessage : IconFileText"
                class="h-4 w-4 text-muted-foreground"
              />
              <span>{{
                detailComment.type === 'forum'
                  ? t('comments.type.forum')
                  : t('comments.type.solution')
              }}</span>
            </div>
          </div>
          <div class="space-y-1.5">
            <span class="text-sm font-medium text-muted-foreground">{{
              t('comments.columns.author')
            }}</span>
            <div class="flex items-center gap-2 text-sm text-foreground">
              <IconUser class="h-4 w-4 text-muted-foreground" />
              <span>{{ detailComment.author?.username || t('comments.status.unknown') }}</span>
            </div>
          </div>
        </div>
        <!-- Parent Title -->
        <div v-if="detailComment.parentTitle" class="space-y-1.5">
          <span class="text-sm font-medium text-muted-foreground">{{
            t('comments.detail.parent')
          }}</span>
          <p class="text-sm text-foreground">{{ detailComment.parentTitle }}</p>
        </div>
        <!-- Status & Time Row -->
        <div class="grid grid-cols-2 gap-4">
          <div class="space-y-1.5">
            <span class="text-sm font-medium text-muted-foreground">{{
              t('comments.columns.status')
            }}</span>
            <span
              :class="[
                'font-mono text-xs uppercase tracking-wider',
                detailComment.isDeleted
                  ? 'text-foreground'
                  : detailComment.isFlagged
                    ? 'text-foreground'
                    : 'text-foreground',
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
            <span class="text-sm font-medium text-muted-foreground">{{
              t('comments.columns.created')
            }}</span>
            <span class="font-mono text-xs text-muted-foreground tabular-nums">{{
              detailComment.createdAt
            }}</span>
          </div>
        </div>
        <!-- Flag Reason (if flagged) -->
        <div v-if="detailComment.isFlagged && detailComment.flaggedReason" class="space-y-1.5">
          <span class="text-sm font-medium text-muted-foreground">{{
            t('comments.flag.reasonLabel')
          }}</span>
          <p class="text-sm text-foreground">{{ detailComment.flaggedReason }}</p>
        </div>
      </div>
    </DialogContent>
  </Dialog>
</template>
