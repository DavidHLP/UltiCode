<script setup lang="ts">
import { ref, computed, h, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import type { ColumnDef } from '@tanstack/vue-table'
import { toast } from 'vue-sonner'
import {
  IconCheck,
  IconDotsVertical,
  IconFlag,
  IconTrash,
  IconUser,
  IconEye,
  IconThumbUp,
  IconPin,
  IconLock,
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
import { useForumStore } from '@/stores/admin/forum'
import { useAuthStore } from '@/stores/auth'
import type { ForumPost } from '@/api/admin/forum'

import DataTable from '@/components/table/DataTable.vue'
import DataTableToolbar, { type Filter } from '@/components/table/DataTableToolbar.vue'
import EntityActionDialog from '@/components/shared/EntityActionDialog.vue'
import { useDataTable } from '@/composables/useDataTable'

const router = useRouter()
const { t } = useI18n()
const forumStore = useForumStore()
const authStore = useAuthStore()

const communityFilter = ref<string>('all')
const flaggedFilter = ref<string>('all')
const pinnedFilter = ref<string>('all')
const lockedFilter = ref<string>('all')

const selectedPostId = ref<string | null>(null)
const selectedPostTitle = ref<string | null>(null)
const deleteDialogOpen = ref(false)
const flagDialogOpen = ref(false)

const canModerate = computed(() => authStore.hasPermission('MODERATE', 'FORUM_POST'))

const toolbarFilters = computed<Filter[]>(() => [
  {
    modelValue: communityFilter.value,
    placeholder: t('forum.filters.community'),
    width: 'w-[150px]',
    options: [
      { value: 'all', label: t('forum.filters.allCommunities') },
      ...forumStore.communities.map((c) => ({ value: c.id, label: c.name })),
    ],
  },
  {
    modelValue: flaggedFilter.value,
    placeholder: t('forum.filters.flagStatus'),
    width: 'w-[130px]',
    options: [
      { value: 'all', label: t('forum.filters.all') },
      { value: 'flagged', label: t('forum.filters.flagged') },
      { value: 'clean', label: t('forum.filters.clean') },
    ],
  },
  {
    modelValue: pinnedFilter.value,
    placeholder: t('forum.filters.pinned'),
    width: 'w-[130px]',
    options: [
      { value: 'all', label: t('forum.filters.all') },
      { value: 'pinned', label: t('forum.filters.pinnedOnly') },
      { value: 'unpinned', label: t('forum.filters.unpinnedOnly') },
    ],
  },
  {
    modelValue: lockedFilter.value,
    placeholder: t('forum.filters.locked'),
    width: 'w-[130px]',
    options: [
      { value: 'all', label: t('forum.filters.all') },
      { value: 'locked', label: t('forum.filters.lockedOnly') },
      { value: 'unlocked', label: t('forum.filters.unlockedOnly') },
    ],
  },
])

const {
  searchQuery,
  tablePagination,
  loading,
  data,
  total,
  error,
  loadEntities: loadPosts,
} = useDataTable<
  ForumPost,
  {
    communityFilter: string
    flaggedFilter: string
    pinnedFilter: string
    lockedFilter: string
  },
  Parameters<typeof forumStore.fetchPosts>[0]
>({
  store: {
    data: computed(() => forumStore.posts),
    total: computed(() => forumStore.totalPosts),
    isLoading: computed(() => forumStore.postsLoading),
    error: computed(() => forumStore.postsError),
    fetch: (params) => forumStore.fetchPosts(params),
  },
  filters: {
    communityFilter: communityFilter.value,
    flaggedFilter: flaggedFilter.value,
    pinnedFilter: pinnedFilter.value,
    lockedFilter: lockedFilter.value,
  },
  transformParams: ({ search, filters, page, limit }) => ({
    search,
    communityId: filters.communityFilter === 'all' ? undefined : filters.communityFilter,
    is_flagged: filters.flaggedFilter === 'all' ? undefined : filters.flaggedFilter === 'flagged',
    is_pinned: filters.pinnedFilter === 'all' ? undefined : filters.pinnedFilter === 'pinned',
    is_locked: filters.lockedFilter === 'all' ? undefined : filters.lockedFilter === 'locked',
    page,
    limit,
  }),
  autoLoad: true,
})

// Load communities on mount
onMounted(() => {
  forumStore.fetchCommunities()
})

function confirmDelete(post: ForumPost) {
  selectedPostId.value = post.id
  selectedPostTitle.value = post.title
  deleteDialogOpen.value = true
}

function openFlagDialog(post: ForumPost) {
  selectedPostId.value = post.id
  selectedPostTitle.value = post.title
  flagDialogOpen.value = true
}

function viewPostDetails(post: ForumPost) {
  router.push({ name: 'forum-post-detail-overview', params: { id: post.id } })
}

async function togglePin(post: ForumPost) {
  try {
    await forumStore.togglePin(post)
    toast.success(
      post.is_pinned ? t('forum.toast.unpinnedSuccessfully') : t('forum.toast.pinnedSuccessfully'),
    )
  } catch {
    toast.error(t('forum.toast.failedToUpdatePin'))
  }
}

async function toggleLock(post: ForumPost) {
  try {
    await forumStore.toggleLock(post)
    toast.success(
      post.is_locked ? t('forum.toast.unlockedSuccessfully') : t('forum.toast.lockedSuccessfully'),
    )
  } catch {
    toast.error(t('forum.toast.failedToUpdateLock'))
  }
}

async function unflagPost(id: string) {
  try {
    await forumStore.unflagPost(id)
    toast.success(t('forum.toast.unflaggedSuccessfully'))
  } catch {
    toast.error(t('forum.toast.failedToUnflag'))
  }
}

async function handleDeletePost(id: string | number) {
  await forumStore.deletePost(String(id))
}

async function handleFlagPost(id: string | number, reason?: string) {
  await forumStore.flagPost(String(id), reason || '')
}

const columns: ColumnDef<ForumPost>[] = [
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
    accessorKey: 'title',
    header: () => t('forum.columns.title'),
    cell: ({ row }) => {
      const post = row.original
      return h('div', { class: 'flex flex-col gap-1' }, [
        h('div', { class: 'flex items-center gap-2' }, [
          h('span', { class: 'font-medium text-sm' }, post.title),
          post.is_pinned &&
            h(IconPin, { class: 'h-3 w-3 text-blue-500', 'aria-label': t('forum.status.pinned') }),
          post.is_locked &&
            h(IconLock, {
              class: 'h-3 w-3 text-amber-500',
              'aria-label': t('forum.status.locked'),
            }),
        ]),
        h('div', { class: 'flex items-center gap-1 text-xs text-muted-foreground' }, [
          h(IconUser, { class: 'h-3 w-3' }),
          h('span', {}, post.author?.username || t('forum.overview.unknown')),
          h('span', { class: 'mx-1' }, '•'),
          h('span', {}, post.community?.name || t('forum.drawer.unknownCommunity')),
        ]),
      ])
    },
  },
  {
    accessorKey: 'stats',
    header: () => t('forum.columns.stats'),
    cell: ({ row }) => {
      const post = row.original
      return h('div', { class: 'flex items-center gap-3 text-muted-foreground text-xs' }, [
        h('div', { class: 'flex items-center gap-1' }, [
          h(IconEye, { class: 'h-3 w-3' }),
          h('span', {}, post.view_count || 0),
        ]),
        h('div', { class: 'flex items-center gap-1' }, [
          h(IconThumbUp, { class: 'h-3 w-3' }),
          h('span', {}, post.upvotes || 0),
        ]),
      ])
    },
  },
  {
    accessorKey: 'is_flagged',
    header: () => t('forum.columns.status'),
    cell: ({ row }) => {
      const isFlagged = row.getValue('is_flagged') as boolean
      const isDeleted = row.original.is_deleted

      if (isDeleted) {
        return h(Badge, { variant: 'destructive' }, () => [
          h(IconTrash, { class: 'mr-1 h-3 w-3' }),
          t('forum.status.deleted'),
        ])
      }

      if (isFlagged) {
        return h(Badge, { variant: 'destructive' }, () => [
          h(IconFlag, { class: 'mr-1 h-3 w-3' }),
          t('forum.status.flagged'),
        ])
      }

      return h(Badge, { variant: 'secondary' }, () => t('forum.status.active'))
    },
  },
  {
    accessorKey: 'created_at',
    header: () => t('forum.columns.created'),
    cell: ({ row }) => {
      const date = new Date(row.getValue('created_at') as string)
      return h('span', { class: 'text-muted-foreground text-sm' }, date.toLocaleDateString())
    },
  },
  {
    id: 'actions',
    header: () => t('forum.columns.actions'),
    cell: ({ row }) => {
      const post = row.original
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
                  h(
                    DropdownMenuItem,
                    { onClick: () => viewPostDetails(post) },
                    {
                      default: () =>
                        h('div', { class: 'flex items-center gap-2' }, [
                          h(IconEye, { class: 'h-4 w-4' }),
                          t('forum.actions.viewDetails'),
                        ]),
                    },
                  ),
                  canModerate.value ? h(DropdownMenuSeparator, {}) : null,
                  canModerate.value
                    ? h(
                        DropdownMenuItem,
                        { onClick: () => togglePin(post) },
                        {
                          default: () =>
                            h('div', { class: 'flex items-center gap-2' }, [
                              h(IconPin, { class: 'h-4 w-4' }),
                              post.is_pinned ? t('forum.actions.unpin') : t('forum.actions.pin'),
                            ]),
                        },
                      )
                    : null,
                  canModerate.value
                    ? h(
                        DropdownMenuItem,
                        { onClick: () => toggleLock(post) },
                        {
                          default: () =>
                            h('div', { class: 'flex items-center gap-2' }, [
                              h(IconLock, { class: 'h-4 w-4' }),
                              post.is_locked ? t('forum.actions.unlock') : t('forum.actions.lock'),
                            ]),
                        },
                      )
                    : null,
                  canModerate.value ? h(DropdownMenuSeparator, {}) : null,
                  canModerate.value
                    ? post.is_flagged
                      ? h(
                          DropdownMenuItem,
                          { onClick: () => unflagPost(post.id) },
                          {
                            default: () =>
                              h('div', { class: 'flex items-center gap-2 text-emerald-600' }, [
                                h(IconCheck, { class: 'h-4 w-4' }),
                                t('forum.actions.unflag'),
                              ]),
                          },
                        )
                      : h(
                          DropdownMenuItem,
                          { onClick: () => openFlagDialog(post) },
                          {
                            default: () =>
                              h('div', { class: 'flex items-center gap-2 text-amber-600' }, [
                                h(IconFlag, { class: 'h-4 w-4' }),
                                t('forum.actions.flag'),
                              ]),
                          },
                        )
                    : null,
                  canModerate.value ? h(DropdownMenuSeparator, {}) : null,
                  canModerate.value
                    ? h(
                        DropdownMenuItem,
                        { onClick: () => confirmDelete(post) },
                        {
                          default: () =>
                            h('div', { class: 'flex items-center gap-2 text-destructive' }, [
                              h(IconTrash, { class: 'h-4 w-4' }),
                              t('forum.actions.delete'),
                            ]),
                        },
                      )
                    : null,
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
        <DataTableToolbar
          :search-model-value="searchQuery"
          @update:search-model-value="searchQuery = $event"
          :search-placeholder="t('forum.searchPlaceholder')"
          search-width="min-w-[150px] w-full lg:w-[250px]"
          :filters="toolbarFilters"
          @update:filter="
            (index, value) => {
              if (index === 0) communityFilter = String(value)
              else if (index === 1) flaggedFilter = String(value)
              else if (index === 2) pinnedFilter = String(value)
              else lockedFilter = String(value)
            }
          "
          :loading="loading"
          :on-refresh="loadPosts"
        />
      </template>
    </DataTable>

    <!-- Error state -->
    <div
      v-if="error"
      class="flex items-center justify-between rounded-lg border border-destructive/50 bg-destructive/10 p-4"
    >
      <span class="text-destructive">{{ error }}</span>
      <Button variant="outline" size="sm" @click="loadPosts()">{{ t('common.retry') }}</Button>
    </div>
  </div>

  <EntityActionDialog
    v-model:open="deleteDialogOpen"
    :entity-id="selectedPostId"
    :entity-title="selectedPostTitle"
    action="delete"
    :title="t('forum.delete.title')"
    :description="t('forum.delete.description')"
    :confirm-label="t('forum.delete.confirm')"
    :cancel-label="t('forum.delete.cancel')"
    :success-label="t('forum.toast.deletedSuccessfully')"
    :error-label="t('forum.toast.failedToDelete')"
    :on-action="handleDeletePost"
    @success="loadPosts"
  />

  <EntityActionDialog
    v-model:open="flagDialogOpen"
    :entity-id="selectedPostId"
    action="flag"
    :title="t('forum.flag.title')"
    :description="t('forum.flag.description')"
    :confirm-label="t('forum.flag.confirm')"
    :cancel-label="t('forum.flag.cancel')"
    :success-label="t('forum.toast.flaggedSuccessfully')"
    :error-label="t('forum.toast.failedToFlag')"
    :reason-label="t('forum.flag.reasonLabel')"
    :reason-placeholder="t('forum.flag.reasonPlaceholder')"
    :reason-required-label="t('forum.toast.reasonRequired')"
    :on-action="handleFlagPost"
    @success="loadPosts"
  />
</template>
