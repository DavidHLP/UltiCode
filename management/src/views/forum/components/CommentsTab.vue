<script setup lang="ts">
import { ref, onMounted, computed, h, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { ColumnDef } from '@tanstack/vue-table'
import { toast } from 'vue-sonner'
import {
  IconCheck,
  IconDotsVertical,
  IconFlag,
  IconMessage,
  IconRefresh,
  IconTrash,
  IconUser,
} from '@tabler/icons-vue'
import { badge } from '@/components/ui/terminal'
import { formatDateByLocale } from '@/i18n/utils'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { useCommentsStore } from '@/stores/admin/comments'
import { useAuthStore } from '@/stores/auth'
import type { Comment, CommentType } from '@/api/admin/comments'

import DataTable from '@/components/table/DataTable.vue'
import EntityActionDialog from '@/components/shared/EntityActionDialog.vue'
import { renderInlineContent } from '@/utils/comment-renderer'

const props = defineProps<{
  postId: string
}>()

const { t } = useI18n()
const commentsStore = useCommentsStore()
const authStore = useAuthStore()

const tablePagination = ref({ pageIndex: 0, pageSize: 10 })
const selectedCommentId = ref<string | null>(null)
const selectedCommentType = ref<CommentType | null>(null)
const selectedCommentContent = ref<string | null>(null)
const deleteDialogOpen = ref(false)
const flagDialogOpen = ref(false)

const canModerate = computed(() => authStore.hasPermission('MODERATE', 'FORUM_COMMENT'))

// Stats for terminal ticker
const stats = computed(() => {
  const comments = commentsStore.comments
  const total = commentsStore.total
  const flagged = comments.filter((c) => c.isFlagged).length
  const deleted = comments.filter((c) => c.isDeleted).length
  return { total, flagged, deleted }
})

onMounted(() => loadComments())

async function loadComments() {
  await commentsStore.fetchComments({
    type: 'forum',
    parentEntityId: props.postId,
    page: tablePagination.value.pageIndex + 1,
    limit: tablePagination.value.pageSize,
  })
}

// Only watch pageIndex and pageSize separately to avoid deep watch issues
watch(
  () => tablePagination.value.pageIndex,
  () => loadComments(),
)
watch(
  () => tablePagination.value.pageSize,
  () => loadComments(),
)

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
  void reason // Used by EntityActionDialog
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

function renderStatusBadge(comment: Comment, t: (key: string) => string) {
  if (comment.isDeleted) {
    return badge({ color: 'error', label: t('comments.status.deleted'), size: 'sm' })
  }

  if (comment.isFlagged) {
    return badge({ color: 'error', label: t('comments.status.flagged'), size: 'sm', pulse: true })
  }

  return badge({ color: 'success', label: t('comments.status.active'), size: 'sm', dot: true, pulse: true })
}

const columns: ColumnDef<Comment>[] = [
  {
    id: 'select',
    header: ({ table }) =>
      h(Checkbox, {
        modelValue:
          table.getIsAllPageRowsSelected() ||
          (table.getIsSomePageRowsSelected() && 'indeterminate'),
        'onUpdate:modelValue': (value: boolean | 'indeterminate') =>
          table.toggleAllPageRowsSelected(!!value),
        'aria-label': t('table.selectAll'),
        class:
          'border-[var(--silver-300)] data-[state=checked]:bg-[var(--accent-electric)] data-[state=checked]:border-[var(--accent-electric)]',
      }),
    cell: ({ row }) =>
      h(Checkbox, {
        modelValue: row.getIsSelected(),
        'onUpdate:modelValue': (value: boolean | 'indeterminate') => row.toggleSelected(!!value),
        'aria-label': t('common.select'),
        class:
          'border-[var(--silver-300)] data-[state=checked]:bg-[var(--accent-electric)] data-[state=checked]:border-[var(--accent-electric)]',
      }),
    enableSorting: false,
    enableHiding: false,
  },
  {
    id: 'row_num',
    header: () => '#',
    cell: ({ row, table }) => {
      const pageIndex = table.getState().pagination.pageIndex
      const pageSize = table.getState().pagination.pageSize
      const rowNum = pageIndex * pageSize + row.index + 1
      return h('span', { class: 'terminal-row-num' }, String(rowNum).padStart(2, '0'))
    },
    enableSorting: false,
    enableHiding: false,
  },
  {
    accessorKey: 'content',
    header: () =>
      h(
        'span',
        { class: 'font-data text-2xs uppercase tracking-widest text-[var(--silver-500)]' },
        t('comments.columns.comment'),
      ),
    cell: ({ row }) => {
      const comment = row.original
      const truncated =
        comment.content.length > 150 ? comment.content.slice(0, 150) + '...' : comment.content

      return h('div', { class: 'flex flex-col gap-1.5 py-1' }, [
        h(
          'div',
          { class: 'text-sm text-[var(--foreground)] leading-relaxed flex flex-wrap items-center gap-y-1' },
          renderInlineContent(truncated),
        ),
        h('div', { class: 'flex items-center gap-1.5 text-xs text-[var(--silver-400)]' }, [
          h(IconUser, { class: 'h-3 w-3' }),
          h(
            'span',
            { class: 'font-data' },
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
        { class: 'font-data text-2xs uppercase tracking-widest text-[var(--silver-500)]' },
        t('comments.columns.created'),
      ),
    cell: ({ row }) => {
      const date = new Date(row.getValue('createdAt') as string)
      return h(
        'span',
        { class: 'font-data text-xs text-[var(--silver-400)] tabular-nums' },
        formatDateByLocale(date),
      )
    },
  },
  {
    accessorKey: 'isFlagged',
    header: () =>
      h(
        'span',
        { class: 'font-data text-2xs uppercase tracking-widest text-[var(--silver-500)]' },
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
        { class: 'font-data text-2xs uppercase tracking-widest text-[var(--silver-500)]' },
        t('common.actions.label'),
      ),
    cell: ({ row }) => {
      const comment = row.original
      if (!canModerate.value) return null

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
                      class:
                        'h-8 w-8 p-0 hover:bg-[var(--silver-100)] dark:hover:bg-[var(--silver-800)]',
                    },
                    {
                      default: () => [
                        h('span', { class: 'sr-only' }, t('common.open')),
                        h(IconDotsVertical, { class: 'h-4 w-4 text-[var(--silver-400)]' }),
                      ],
                    },
                  ),
              },
            ),
            h(
              DropdownMenuContent,
              {
                align: 'end',
                class: 'border-[var(--silver-200)] dark:border-[var(--silver-700)]',
              },
              {
                default: () => [
                  comment.isFlagged
                    ? h(
                        DropdownMenuItem,
                        {
                          onClick: () => unflagComment(comment),
                          class: 'font-data text-xs cursor-pointer',
                        },
                        {
                          default: () =>
                            h('div', { class: 'flex items-center gap-2' }, [
                              h(IconCheck, { class: 'h-4 w-4 text-[var(--terminal-green)]' }),
                              h(
                                'span',
                                { class: 'text-[var(--terminal-green)]' },
                                t('comments.actions.unflag'),
                              ),
                            ]),
                        },
                      )
                    : h(
                        DropdownMenuItem,
                        {
                          onClick: () => openFlagDialog(comment),
                          class: 'font-data text-xs cursor-pointer',
                        },
                        {
                          default: () =>
                            h('div', { class: 'flex items-center gap-2' }, [
                              h(IconFlag, { class: 'h-4 w-4 text-[var(--terminal-amber)]' }),
                              h(
                                'span',
                                { class: 'text-[var(--terminal-amber)]' },
                                t('comments.actions.flag'),
                              ),
                            ]),
                        },
                      ),
                  h(DropdownMenuSeparator, {
                    class: 'bg-[var(--silver-200)] dark:bg-[var(--silver-700)]',
                  }),
                  h(
                    DropdownMenuItem,
                    {
                      onClick: () => confirmDelete(comment),
                      class: 'font-data text-xs cursor-pointer',
                    },
                    {
                      default: () =>
                        h('div', { class: 'flex items-center gap-2' }, [
                          h(IconTrash, { class: 'h-4 w-4 text-[var(--terminal-red)]' }),
                          h(
                            'span',
                            { class: 'text-[var(--terminal-red)]' },
                            t('comments.actions.delete'),
                          ),
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
    <!-- Terminal Header -->
    <div class="border border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--card)]">
      <!-- Title Row -->
      <div
        class="px-4 py-3 flex items-center justify-between border-b border-[var(--silver-200)] dark:border-[var(--silver-300)]"
      >
        <div class="flex items-center gap-3">
          <div class="flex items-center gap-2">

            <span class="terminal-cursor" />
          </div>
          <IconMessage class="h-4 w-4 text-[var(--terminal-cyan)]" />
          <h3 class="text-sm font-medium text-[var(--foreground)]">
            {{ t('forum.comments.postComments') }}
          </h3>
        </div>
        <Button
          variant="terminal"
          size="sm"
          class="h-7 w-7 p-0 border-[var(--silver-300)] hover:border-[var(--accent-electric)] hover:text-[var(--accent-electric)]"
          @click="loadComments"
          :title="t('common.refresh')"
        >
          <IconRefresh class="h-3.5 w-3.5" :class="{ 'animate-spin': commentsStore.loading }" />
        </Button>
      </div>

      <!-- Stats Ticker -->
      <div class="px-4 py-2 flex items-center gap-4 bg-[var(--surface-sunken)]">
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]">{{ t('comments.stats.total') }}:</span>
          <span class="font-data text-xs text-[var(--terminal-cyan)] tabular-nums">{{
            stats.total
          }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]">{{ t('comments.stats.flagged') }}:</span>
          <span class="font-data text-xs text-[var(--terminal-red)] tabular-nums">{{
            stats.flagged
          }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]">{{ t('comments.stats.deleted') }}:</span>
          <span class="font-data text-xs text-[var(--terminal-red)] tabular-nums">{{
            stats.deleted
          }}</span>
        </div>
      </div>
    </div>

    <!-- Comments Table -->
    <DataTable
      :columns="columns"
      :data="commentsStore.comments"
      :pagination="tablePagination"
      :row-count="commentsStore.total"
      :loading="commentsStore.loading"
      @update:pagination="tablePagination = $event"
      class="terminal-table"
    />

    <!-- Error state - Terminal Style -->
    <div
      v-if="commentsStore.error"
      class="flex items-center justify-between border border-[var(--terminal-red)] bg-[color-mix(in_oklch,_var(--terminal-red)_8%,_transparent)] p-4"
    >
      <div class="flex items-center gap-3">
        <span class="font-data text-sm text-[var(--terminal-red)]">&gt; ERROR:</span>
        <span class="text-sm text-[var(--foreground)]">{{ commentsStore.error }}</span>
      </div>
      <Button
        variant="terminal"
        size="sm"
        class="font-data text-xs border-[var(--terminal-red)] text-[var(--terminal-red)] hover:bg-[color-mix(in_oklch,_var(--terminal-red)_10%,_transparent)]"
        @click="loadComments"
      >
        {{ t('common.retry') }}
      </Button>
    </div>

    <!-- Empty state - Terminal Style -->
    <div
      v-if="!commentsStore.loading && commentsStore.comments.length === 0 && !commentsStore.error"
      class="border border-[var(--silver-200)] dark:border-[var(--silver-300)] p-8 text-center bg-[var(--card)]"
    >
      <div
        class="w-10 h-10 border border-[var(--silver-300)] flex items-center justify-center mx-auto mb-3"
      >
        <IconMessage class="h-5 w-5 text-[var(--silver-400)]" />
      </div>
      <p class="font-data text-xs text-[var(--silver-400)]">
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
