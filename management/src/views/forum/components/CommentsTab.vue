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
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
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

defineProps<{
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

onMounted(() => loadComments())

async function loadComments() {
  await commentsStore.fetchComments({
    type: 'forum',
    // Note: The comments API doesn't currently support filtering by post_id
    // We'll need to add this to the backend or filter client-side
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
      }),
    cell: ({ row }) =>
      h(Checkbox, {
        modelValue: row.getIsSelected(),
        'onUpdate:modelValue': (value: boolean | 'indeterminate') => row.toggleSelected(!!value),
        'aria-label': t('common.select'),
      }),
    enableSorting: false,
    enableHiding: false,
  },
  {
    accessorKey: 'content',
    header: () => t('comments.columns.comment'),
    cell: ({ row }) => {
      const comment = row.original
      const truncated =
        comment.content.length > 150 ? comment.content.slice(0, 150) + '...' : comment.content

      return h('div', { class: 'flex flex-col gap-2' }, [
        h('span', { class: 'text-sm' }, truncated),
        h('div', { class: 'flex items-center gap-1 text-xs text-muted-foreground' }, [
          h(IconUser, { class: 'h-3 w-3' }),
          h('span', {}, comment.author?.username || t('forum.overview.unknown')),
        ]),
      ])
    },
  },
  {
    accessorKey: 'created_at',
    header: () => t('comments.columns.created'),
    cell: ({ row }) => {
      const date = new Date(row.getValue('created_at') as string)
      return h('span', { class: 'text-muted-foreground text-sm' }, date.toLocaleDateString())
    },
  },
  {
    accessorKey: 'is_flagged',
    header: () => t('comments.columns.status'),
    cell: ({ row }) => {
      const isFlagged = row.getValue('is_flagged') as boolean
      const isDeleted = row.original.is_deleted

      if (isDeleted) {
        return h(Badge, { variant: 'destructive' }, () => [
          h(IconTrash, { class: 'mr-1 h-3 w-3' }),
          t('comments.status.deleted'),
        ])
      }

      if (isFlagged) {
        return h(Badge, { variant: 'destructive' }, () => [
          h(IconFlag, { class: 'mr-1 h-3 w-3' }),
          t('comments.status.flagged'),
        ])
      }

      return h(Badge, { variant: 'secondary' }, () => t('comments.status.active'))
    },
  },
  {
    id: 'actions',
    header: () => t('common.actions'),
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
                    { variant: 'ghost', size: 'icon', class: 'h-8 w-8 p-0' },
                    {
                      default: () => [
                        h('span', { class: 'sr-only' }, t('common.open')),
                        h(IconDotsVertical, { class: 'h-4 w-4' }),
                      ],
                    },
                  ),
              },
            ),
            h(
              DropdownMenuContent,
              { align: 'end' },
              {
                default: () => [
                  comment.is_flagged
                    ? h(
                        DropdownMenuItem,
                        { onClick: () => unflagComment(comment) },
                        {
                          default: () =>
                            h('div', { class: 'flex items-center gap-2 text-emerald-600' }, [
                              h(IconCheck, { class: 'h-4 w-4' }),
                              t('comments.actions.unflag'),
                            ]),
                        },
                      )
                    : h(
                        DropdownMenuItem,
                        { onClick: () => openFlagDialog(comment) },
                        {
                          default: () =>
                            h('div', { class: 'flex items-center gap-2 text-amber-600' }, [
                              h(IconFlag, { class: 'h-4 w-4' }),
                              t('comments.actions.flag'),
                            ]),
                        },
                      ),
                  h(DropdownMenuSeparator, {}),
                  h(
                    DropdownMenuItem,
                    { onClick: () => confirmDelete(comment) },
                    {
                      default: () =>
                        h('div', { class: 'flex items-center gap-2 text-destructive' }, [
                          h(IconTrash, { class: 'h-4 w-4' }),
                          t('comments.actions.delete'),
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
    <!-- Header with Refresh -->
    <div class="flex items-center justify-between">
      <div class="flex items-center gap-2">
        <IconMessage class="h-5 w-5 text-muted-foreground" />
        <h3 class="text-lg font-semibold">{{ t('forum.comments.postComments') }}</h3>
      </div>
      <Button
        variant="ghost"
        size="icon"
        class="h-8 w-8"
        @click="loadComments"
        :title="t('common.refresh')"
      >
        <IconRefresh class="h-4 w-4" :class="{ 'animate-spin': commentsStore.loading }" />
      </Button>
    </div>

    <!-- Comments Table -->
    <DataTable
      :columns="columns"
      :data="commentsStore.comments"
      :pagination="tablePagination"
      :row-count="commentsStore.total"
      :loading="commentsStore.loading"
      @update:pagination="tablePagination = $event"
    />

    <!-- Error state -->
    <div
      v-if="commentsStore.error"
      class="flex items-center justify-between rounded-lg border border-destructive/50 bg-destructive/10 p-4"
    >
      <span class="text-destructive">{{ commentsStore.error }}</span>
      <Button variant="outline" size="sm" @click="loadComments">{{ t('common.retry') }}</Button>
    </div>

    <!-- Empty state -->
    <div
      v-if="!commentsStore.loading && commentsStore.comments.length === 0 && !commentsStore.error"
      class="text-center py-8"
    >
      <IconMessage class="h-12 w-12 text-muted-foreground mx-auto mb-3" />
      <p class="text-sm text-muted-foreground">{{ t('forum.comments.noCommentsFound') }}</p>
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
