<script setup lang="ts">
import { computed, h, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { ColumnDef } from '@tanstack/vue-table'
import {
  Check as IconCheck,
  EllipsisVertical as IconDotsVertical,
  Flag as IconFlag,
  MessageSquare as IconMessage,
  RefreshCw as IconRefresh,
  Trash as IconTrash,
  User as IconUser,
} from '@lucide/vue'

import { badge } from '@ulticode/badge-config'
import { formatDateByLocale } from '@/i18n/utils'
import { Button } from '@ulticode/design-system/ui/button'
import DataTable from '@/components/table/DataTable.vue'
import EntityActionDialog from '@/components/shared/EntityActionDialog.vue'
import { createSelectionColumn } from '@/components/table/selectionColumn'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@ulticode/design-system/ui/dropdown-menu'
import { useCommentsStore } from '@/stores/admin/comments'
import type { Comment } from '@/api/admin/comments'

import { useCommentModeration } from '@/composables/useCommentModeration'
import { renderInlineContent } from '@/utils/comment-renderer'
import { useRemoteTable } from '@/composables/useRemoteTable'

const props = defineProps<{
  postId: string
}>()

const { t } = useI18n()
const commentsStore = useCommentsStore()

const {
  tablePagination,
  loading,
  data,
  total,
  error,
  refresh: loadComments,
} = useRemoteTable<
  Comment,
  Record<string, never>,
  Parameters<typeof commentsStore.fetchComments>[0]
>({
  store: commentsStore,
  initialQuery: {
    filters: {},
    pagination: { pageIndex: 0, pageSize: 10 },
  },
  toParams: ({ page, limit }) => ({
    type: 'forum',
    parentEntityId: props.postId,
    page,
    limit,
  }),
  autoLoad: true,
})

const stats = computed(() => ({
  total: total.value,
  flagged: data.value.filter((comment) => comment.isFlagged).length,
  deleted: data.value.filter((comment) => comment.isDeleted).length,
}))

const {
  selectedCommentId,
  selectedCommentContent,
  deleteDialogOpen,
  flagDialogOpen,
  canModerate,
  confirmDelete,
  openFlagDialog,
  handleDeleteComment,
  handleFlagComment,
  unflagComment,
} = useCommentModeration({ refresh: loadComments })

watch(
  () => props.postId,
  () => void loadComments(),
)

function renderStatusBadge(comment: Comment, t: (key: string) => string) {
  if (comment.isDeleted) {
    return badge({ color: 'error', label: t('comments.status.deleted'), size: 'sm' })
  }

  if (comment.isFlagged) {
    return badge({ color: 'error', label: t('comments.status.flagged'), size: 'sm', pulse: true })
  }

  return badge({
    color: 'success',
    label: t('comments.status.active'),
    size: 'sm',
    dot: true,
    pulse: true,
  })
}

const columns: ColumnDef<Comment>[] = [
  ...createSelectionColumn<Comment>(t, {
    checkboxClass:
      'border-border data-[state=checked]:bg-primary data-[state=checked]:border-primary',
  }),
  {
    id: 'row_num',
    header: () => '#',
    cell: ({ row, table }) => {
      const pageIndex = table.getState().pagination.pageIndex
      const pageSize = table.getState().pagination.pageSize
      const rowNum = pageIndex * pageSize + row.index + 1
      return h(
        'span',
        { class: 'text-xs tabular-nums text-muted-foreground' },
        String(rowNum).padStart(2, '0'),
      )
    },
    enableSorting: false,
    enableHiding: false,
  },
  {
    accessorKey: 'content',
    header: () =>
      h(
        'span',
        { class: 'font-mono text-xs uppercase tracking-widest text-muted-foreground' },
        t('comments.columns.comment'),
      ),
    cell: ({ row }) => {
      const comment = row.original
      const truncated =
        comment.content.length > 150 ? comment.content.slice(0, 150) + '...' : comment.content

      return h('div', { class: 'flex flex-col gap-1.5 py-1' }, [
        h(
          'div',
          {
            class: 'text-sm text-foreground leading-relaxed flex flex-wrap items-center gap-y-1',
          },
          renderInlineContent(truncated),
        ),
        h('div', { class: 'flex items-center gap-1.5 text-xs text-muted-foreground' }, [
          h(IconUser, { class: 'h-3 w-3' }),
          h(
            'span',
            { class: 'font-mono' },
            comment.author?.username || t('forum.overview.unknown'),
          ),
        ]),
      ])
    },
  },
  {
    accessorKey: 'createdAt',
    header: () =>
      h(
        'span',
        { class: 'font-mono text-xs uppercase tracking-widest text-muted-foreground' },
        t('comments.columns.created'),
      ),
    cell: ({ row }) => {
      const date = new Date(row.getValue('createdAt') as string)
      return h(
        'span',
        { class: 'font-mono text-xs text-muted-foreground tabular-nums' },
        formatDateByLocale(date),
      )
    },
  },
  {
    accessorKey: 'isFlagged',
    header: () =>
      h(
        'span',
        { class: 'font-mono text-xs uppercase tracking-widest text-muted-foreground' },
        t('comments.columns.status'),
      ),
    cell: ({ row }) => {
      const comment = row.original
      return renderStatusBadge(comment, t)
    },
  },
  {
    id: 'actions',
    header: () =>
      h(
        'span',
        { class: 'font-mono text-xs uppercase tracking-widest text-muted-foreground' },
        t('common.actions.label'),
      ),
    cell: ({ row }) => {
      const comment = row.original
      if (!canModerate(comment)) return null

      return h(
        DropdownMenu,
        {},
        {
          default: () => [
            h(
              DropdownMenuTrigger,
              { asChild: true },
              {
                default: () =>
                  h(
                    Button,
                    {
                      variant: 'ghost',
                      size: 'icon',
                      class: 'h-8 w-8 p-0 hover:bg-accent dark:hover:bg-foreground',
                    },
                    {
                      default: () => [
                        h('span', { class: 'sr-only' }, t('common.open')),
                        h(IconDotsVertical, { class: 'h-4 w-4 text-muted-foreground' }),
                      ],
                    },
                  ),
              },
            ),
            h(
              DropdownMenuContent,
              {
                align: 'end',
                class: 'border-border dark:border-foreground',
              },
              {
                default: () => [
                  comment.isFlagged
                    ? h(
                        DropdownMenuItem,
                        {
                          onClick: () => unflagComment(comment),
                          class: 'font-mono text-xs cursor-pointer',
                        },
                        {
                          default: () =>
                            h('div', { class: 'flex items-center gap-2' }, [
                              h(IconCheck, { class: 'h-4 w-4 text-foreground' }),
                              h('span', { class: 'text-foreground' }, t('comments.actions.unflag')),
                            ]),
                        },
                      )
                    : h(
                        DropdownMenuItem,
                        {
                          onClick: () => openFlagDialog(comment),
                          class: 'font-mono text-xs cursor-pointer',
                        },
                        {
                          default: () =>
                            h('div', { class: 'flex items-center gap-2' }, [
                              h(IconFlag, { class: 'h-4 w-4 text-foreground' }),
                              h('span', { class: 'text-foreground' }, t('comments.actions.flag')),
                            ]),
                        },
                      ),
                  h(DropdownMenuSeparator, {
                    class: 'bg-border dark:bg-foreground',
                  }),
                  h(
                    DropdownMenuItem,
                    {
                      onClick: () => confirmDelete(comment),
                      class: 'font-mono text-xs cursor-pointer',
                    },
                    {
                      default: () =>
                        h('div', { class: 'flex items-center gap-2' }, [
                          h(IconTrash, { class: 'h-4 w-4 text-foreground' }),
                          h('span', { class: 'text-foreground' }, t('comments.actions.delete')),
                        ]),
                    },
                  ),
                ],
              },
            ),
          ],
        },
      )
    },
  },
]
</script>

<template>
  <div class="space-y-4">
    <!-- Page header -->
    <div class="border border-border dark:border-border bg-card">
      <!-- Title Row -->
      <div
        class="px-4 py-3 flex items-center justify-between border-b border-border dark:border-border"
      >
        <div class="flex items-center gap-3">
          <div class="flex items-center gap-2">
            <span class="hidden" />
          </div>
          <IconMessage class="h-4 w-4 text-status-info-mark" />
          <h3 class="text-sm font-medium text-foreground">
            {{ t('forum.comments.postComments') }}
          </h3>
        </div>
        <Button
          variant="outline"
          size="icon"
          class="w-7 p-0"
          @click="loadComments"
          :title="t('common.refresh')"
        >
          <IconRefresh class="h-3.5 w-3.5" :class="{ 'animate-spin': loading }" />
        </Button>
      </div>

      <!-- Stats Ticker -->
      <div class="px-4 py-2 flex items-center gap-4 bg-muted">
        <div class="flex items-center gap-2">
          <span class="text-sm font-medium text-muted-foreground"
            >{{ t('comments.stats.total') }}:</span
          >
          <span class="font-mono text-xs text-foreground tabular-nums">{{ stats.total }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="text-sm font-medium text-muted-foreground"
            >{{ t('comments.stats.flagged') }}:</span
          >
          <span class="font-mono text-xs text-foreground tabular-nums">{{ stats.flagged }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="text-sm font-medium text-muted-foreground"
            >{{ t('comments.stats.deleted') }}:</span
          >
          <span class="font-mono text-xs text-foreground tabular-nums">{{ stats.deleted }}</span>
        </div>
      </div>
    </div>

    <DataTable
      :columns="columns"
      :data="data"
      :pagination="tablePagination"
      :row-count="total"
      :loading="loading"
      @update:pagination="tablePagination = $event"
      class="w-full text-sm"
    />

    <!-- Error state - Terminal Style -->
    <div
      v-if="error"
      class="flex items-center justify-between border border-status-error-mark bg-[color-mix(in_oklch,_var(--status-error-mark)_8%,_transparent)] p-4"
    >
      <div class="flex items-center gap-3">
        <span class="font-mono text-sm text-foreground">&gt; ERROR:</span>
        <span class="text-sm text-foreground">{{ error }}</span>
      </div>
      <Button variant="outline" size="sm" @click="loadComments">
        {{ t('common.retry') }}
      </Button>
    </div>

    <!-- Empty state - Terminal Style -->
    <div
      v-if="!loading && data.length === 0 && !error"
      class="border border-border dark:border-border p-8 text-center bg-card"
    >
      <div class="w-10 h-10 border border-border flex items-center justify-center mx-auto mb-3">
        <IconMessage class="h-5 w-5 text-muted-foreground" />
      </div>
      <p class="font-mono text-xs text-muted-foreground">
        &gt; {{ t('forum.comments.noCommentsFound') }}
      </p>
    </div>

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
  </div>
</template>
