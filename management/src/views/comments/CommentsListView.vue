<script setup lang="ts">
import { ref, computed, h } from 'vue'
import { useI18n } from 'vue-i18n'
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
import { useAuthStore } from '@/stores/auth'
import type { Comment, CommentType } from '@/api/admin/comments'

import DataTable from '@/components/table/DataTable.vue'
import EntityActionDialog from '@/components/shared/EntityActionDialog.vue'
import { useDataTable } from '@/composables/useDataTable'
import { getCommentStatusBadge, getCommentTypeIcon } from '@/lib/entities/comment'

const { t } = useI18n()
const commentsStore = useCommentsStore()
const authStore = useAuthStore()

const typeFilter = ref<CommentType | 'all'>('all')
const flaggedFilter = ref<string>('all')

const selectedCommentId = ref<string | null>(null)
const selectedCommentType = ref<CommentType | null>(null)
const selectedCommentContent = ref<string | null>(null)
const deleteDialogOpen = ref(false)
const flagDialogOpen = ref(false)

const canModerateForum = computed(() => authStore.hasPermission('MODERATE', 'FORUM_COMMENT'))
const canModerateSolution = computed(() => authStore.hasPermission('MODERATE', 'SOLUTION_COMMENT'))

const {
  searchQuery,
  tablePagination,
  loading,
  data,
  total,
  error,
  loadEntities: loadComments,
} = useDataTable<
  Comment,
  { type: CommentType | 'all'; flaggedFilter: string },
  Parameters<typeof commentsStore.fetchComments>[0]
>({
  store: {
    data: computed(() => commentsStore.comments),
    total: computed(() => commentsStore.total),
    isLoading: computed(() => commentsStore.loading),
    error: computed(() => commentsStore.error),
    fetch: (params) => commentsStore.fetchComments(params),
  },
  filters: {
    type: typeFilter.value,
    flaggedFilter: flaggedFilter.value,
  },
  transformParams: ({ search, filters, page, limit }) => ({
    search,
    type: filters.type === 'all' ? undefined : filters.type,
    is_flagged: filters.flaggedFilter === 'all' ? undefined : filters.flaggedFilter === 'flagged',
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
          getCommentTypeIcon(comment.type),
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
      const comment = row.original
      return getCommentStatusBadge(comment.is_flagged, comment.is_deleted, t)
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
                        h('span', { class: 'sr-only' }, t('common.actions')),
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
      :data="data"
      :pagination="tablePagination"
      :row-count="total"
      :loading="loading"
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
              <IconRefresh class="h-3.5 w-3.5" :class="{ 'animate-spin': loading }" />
            </Button>
          </div>
        </div>
      </template>
    </DataTable>

    <!-- Error state -->
    <div
      v-if="error"
      class="flex items-center justify-between rounded-lg border border-destructive/50 bg-destructive/10 p-4"
    >
      <span class="text-destructive">{{ error }}</span>
      <Button variant="outline" size="sm" @click="loadComments()">{{ t('common.retry') }}</Button>
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
</template>
