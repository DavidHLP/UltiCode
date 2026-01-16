<script setup lang="ts">
import { ref, onMounted, computed, h, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { watchDebounced } from '@vueuse/core'
import type { ColumnDef } from '@tanstack/vue-table'
import { toast } from 'vue-sonner'
import {
  IconCheck,
  IconDotsVertical,
  IconFlag,
  IconRefresh,
  IconTrash,
  IconX,
  IconUser,
  IconMessage,
  IconFileText,
} from '@tabler/icons-vue'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { Checkbox } from '@/components/ui/checkbox'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { useCommentsStore } from '@/stores/admin/comments'
import { useAuthStore } from '@/stores/admin/auth'
import type { Comment, CommentType } from '@/api/admin/comments'

import DataTable from '@/components/table/DataTable.vue'
import CommentDeleteDialog from './CommentDeleteDialog.vue'
import CommentFlagDialog from './CommentFlagDialog.vue'

const { t } = useI18n()
const commentsStore = useCommentsStore()
const authStore = useAuthStore()

const searchQuery = ref('')
const typeFilter = ref<CommentType | 'all'>('all')
const flaggedFilter = ref<string>('all')
const tablePagination = ref({ pageIndex: 0, pageSize: 10 })

const selectedCommentId = ref<string | null>(null)
const selectedCommentType = ref<CommentType | null>(null)
const deleteDialogOpen = ref(false)
const flagDialogOpen = ref(false)

const canModerateForum = computed(() => authStore.hasPermission('MODERATE', 'FORUM_COMMENT'))
const canModerateSolution = computed(() => authStore.hasPermission('MODERATE', 'SOLUTION_COMMENT'))

onMounted(() => loadComments())

async function loadComments() {
  await commentsStore.fetchComments({
    search: searchQuery.value || undefined,
    type: typeFilter.value === 'all' ? undefined : typeFilter.value,
    is_flagged: flaggedFilter.value === 'all' ? undefined : flaggedFilter.value === 'flagged',
    page: tablePagination.value.pageIndex + 1,
    limit: tablePagination.value.pageSize,
  })
}

// Watchers
watchDebounced(
  searchQuery,
  () => {
    tablePagination.value.pageIndex = 0
    loadComments()
  },
  { debounce: 500 },
)

watch([typeFilter, flaggedFilter], () => {
  tablePagination.value.pageIndex = 0
  loadComments()
})

watch(
  () => tablePagination.value,
  () => loadComments(),
  { deep: true },
)

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
    toast.success(t('comments.toast.unflaggedSuccessfully'))
  } catch {
    toast.error(t('comments.toast.failedToUnflag'))
  }
}

function canModerate(comment: Comment) {
  if (comment.type === 'forum') return canModerateForum.value
  if (comment.type === 'solution') return canModerateSolution.value
  return false
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
        comment.content.length > 100 ? comment.content.slice(0, 100) + '...' : comment.content

      return h('div', { class: 'flex flex-col gap-1' }, [
        h('span', { class: 'font-medium text-sm' }, truncated),
        h('div', { class: 'flex items-center gap-1 text-xs text-muted-foreground' }, [
          comment.type === 'forum'
            ? h(IconMessage, { class: 'h-3 w-3' })
            : h(IconFileText, { class: 'h-3 w-3' }),
          h('span', {}, comment.parentTitle || t('comments.type.unknown')),
        ]),
      ])
    },
  },
  {
    accessorKey: 'author',
    header: () => t('comments.columns.author'),
    cell: ({ row }) => {
      const author = row.original.author
      return h('div', { class: 'flex items-center gap-2' }, [
        h(IconUser, { class: 'h-3 w-3 text-muted-foreground' }),
        h('span', { class: 'text-sm' }, author?.username || t('comments.status.unknown')),
      ])
    },
  },
  {
    accessorKey: 'type',
    header: () => t('comments.columns.type'),
    cell: ({ row }) => {
      const type = row.getValue('type') as CommentType
      return h(Badge, { variant: 'outline' }, () => t('comments.type.' + type))
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
    accessorKey: 'created_at',
    header: () => t('comments.columns.created'),
    cell: ({ row }) => {
      const date = new Date(row.getValue('created_at') as string)
      return h('span', { class: 'text-muted-foreground text-sm' }, date.toLocaleDateString())
    },
  },
  {
    id: 'actions',
    header: () => t('comments.columns.actions'),
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
                    { variant: 'ghost', size: 'icon', class: 'h-8 w-8 p-0' },
                    {
                      default: () => [
                        h('span', { class: 'sr-only' }, t('table.actions')),
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
  <div class="relative flex flex-col gap-4 overflow-auto px-4 lg:px-6">
    <DataTable
      :columns="columns"
      :data="commentsStore.comments"
      :pagination="tablePagination"
      :row-count="commentsStore.total"
      :loading="commentsStore.loading"
      @update:pagination="tablePagination = $event"
    >
      <template #toolbar-left>
        <div class="flex flex-wrap items-center gap-2 w-full lg:w-auto">
          <Input
            v-model="searchQuery"
            :placeholder="t('comments.searchPlaceholder')"
            class="h-8 min-w-[150px] w-full lg:w-[250px]"
          >
            <template #trailing>
              <button
                v-if="searchQuery"
                @click="searchQuery = ''"
                class="rounded-sm opacity-70 hover:opacity-100"
              >
                <IconX class="h-3 w-3" />
              </button>
            </template>
          </Input>

          <div class="flex items-center gap-2 overflow-x-auto pb-1 lg:pb-0">
            <Select v-model="typeFilter">
              <SelectTrigger class="h-8 w-[130px]">
                <SelectValue :placeholder="t('comments.filters.type')" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">{{ t('comments.filters.allTypes') }}</SelectItem>
                <SelectItem value="forum">{{ t('comments.type.forum') }}</SelectItem>
                <SelectItem value="solution">{{ t('comments.type.solution') }}</SelectItem>
              </SelectContent>
            </Select>

            <Select v-model="flaggedFilter">
              <SelectTrigger class="h-8 w-[130px]">
                <SelectValue :placeholder="t('comments.filters.flagStatus')" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">{{ t('comments.filters.all') }}</SelectItem>
                <SelectItem value="flagged">{{ t('comments.filters.flagged') }}</SelectItem>
                <SelectItem value="clean">{{ t('comments.filters.clean') }}</SelectItem>
              </SelectContent>
            </Select>

            <Button
              variant="ghost"
              size="icon"
              class="h-8 w-8"
              @click="loadComments()"
              :title="t('common.refresh')"
            >
              <IconRefresh class="h-3.5 w-3.5" :class="{ 'animate-spin': commentsStore.loading }" />
            </Button>
          </div>
        </div>
      </template>
    </DataTable>

    <!-- Error state -->
    <div
      v-if="commentsStore.error"
      class="flex items-center justify-between rounded-lg border border-destructive/50 bg-destructive/10 p-4"
    >
      <span class="text-destructive">{{ commentsStore.error }}</span>
      <Button variant="outline" size="sm" @click="loadComments()">{{ t('common.retry') }}</Button>
    </div>
  </div>

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
</template>
