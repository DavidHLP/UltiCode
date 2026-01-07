<script setup lang="ts">
import { ref, onMounted, computed, h, watch } from 'vue'
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
import { useAuthStore } from '@/stores/admin/auth'
import type { Comment, CommentType } from '@/api/admin/comments'

import DataTable from '@/components/table/DataTable.vue'
import CommentDeleteDialog from '../../comments/CommentDeleteDialog.vue'
import CommentFlagDialog from '../../comments/CommentFlagDialog.vue'

defineProps<{
  postId: string
}>()

const commentsStore = useCommentsStore()
const authStore = useAuthStore()

const tablePagination = ref({ pageIndex: 0, pageSize: 10 })
const selectedCommentId = ref<string | null>(null)
const selectedCommentType = ref<CommentType | null>(null)
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
watch(() => tablePagination.value.pageIndex, () => loadComments())
watch(() => tablePagination.value.pageSize, () => loadComments())

function confirmDelete(comment: Comment) {
  selectedCommentId.value = comment.id
  selectedCommentType.value = comment.type
  deleteDialogOpen.value = true
}

function openFlagDialog(comment: Comment) {
  selectedCommentId.value = comment.id
  selectedCommentType.value = comment.type
  flagDialogOpen.value = true
}

async function unflagComment(comment: Comment) {
  try {
    await commentsStore.unflagComment(comment.id, comment.type)
    toast.success('Comment unflagged successfully')
  } catch {
    toast.error('Failed to unflag comment')
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
        'aria-label': 'Select all',
      }),
    cell: ({ row }) =>
      h(Checkbox, {
        modelValue: row.getIsSelected(),
        'onUpdate:modelValue': (value: boolean | 'indeterminate') => row.toggleSelected(!!value),
        'aria-label': 'Select row',
      }),
    enableSorting: false,
    enableHiding: false,
  },
  {
    accessorKey: 'content',
    header: 'Comment',
    cell: ({ row }) => {
      const comment = row.original
      const truncated =
        comment.content.length > 150 ? comment.content.slice(0, 150) + '...' : comment.content

      return h('div', { class: 'flex flex-col gap-2' }, [
        h('span', { class: 'text-sm' }, truncated),
        h('div', { class: 'flex items-center gap-1 text-xs text-muted-foreground' }, [
          h(IconUser, { class: 'h-3 w-3' }),
          h('span', {}, comment.author?.username || 'Unknown'),
        ]),
      ])
    },
  },
  {
    accessorKey: 'created_at',
    header: 'Created',
    cell: ({ row }) => {
      const date = new Date(row.getValue('created_at') as string)
      return h('span', { class: 'text-muted-foreground text-sm' }, date.toLocaleDateString())
    },
  },
  {
    accessorKey: 'is_flagged',
    header: 'Status',
    cell: ({ row }) => {
      const isFlagged = row.getValue('is_flagged') as boolean
      const isDeleted = row.original.is_deleted

      if (isDeleted) {
        return h(Badge, { variant: 'destructive' }, () => [
          h(IconTrash, { class: 'mr-1 h-3 w-3' }),
          'Deleted',
        ])
      }

      if (isFlagged) {
        return h(Badge, { variant: 'destructive' }, () => [
          h(IconFlag, { class: 'mr-1 h-3 w-3' }),
          'Flagged',
        ])
      }

      return h(Badge, { variant: 'secondary' }, () => 'Active')
    },
  },
  {
    id: 'actions',
    header: 'Actions',
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
                        h('span', { class: 'sr-only' }, 'Open menu'),
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
                              'Unflag',
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
                              'Flag',
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
                          'Delete',
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
        <h3 class="text-lg font-semibold">Post Comments</h3>
      </div>
      <Button variant="ghost" size="icon" class="h-8 w-8" @click="loadComments" title="Refresh">
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
      <Button variant="outline" size="sm" @click="loadComments">Retry</Button>
    </div>

    <!-- Empty state -->
    <div
      v-if="!commentsStore.loading && commentsStore.comments.length === 0 && !commentsStore.error"
      class="text-center py-8"
    >
      <IconMessage class="h-12 w-12 text-muted-foreground mx-auto mb-3" />
      <p class="text-sm text-muted-foreground">No comments found for this post</p>
    </div>

    <!-- Dialogs -->
    <CommentDeleteDialog
      v-model:open="deleteDialogOpen"
      :comment-id="selectedCommentId"
      :comment-type="selectedCommentType"
      @success="loadComments"
    />

    <CommentFlagDialog
      v-model:open="flagDialogOpen"
      :comment-id="selectedCommentId"
      :comment-type="selectedCommentType"
      @success="loadComments"
    />
  </div>
</template>
