<script setup lang="ts">
import { ref, onMounted, computed, h, watch } from 'vue'
import { watchDebounced } from '@vueuse/core'
import type { ColumnDef } from '@tanstack/vue-table'
import { toast } from 'vue-sonner'
import {
  IconDotsVertical,
  IconFlag,
  IconRefresh,
  IconTrash,
  IconX,
  IconUser,
  IconMessage,
  IconEye,
  IconThumbUp,
  IconPin,
  IconLock,
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
import { useForumStore } from '@/stores/admin/forum'
import { useAuthStore } from '@/stores/admin/auth'
import type { ForumPost } from '@/api/admin/forum'

import DataTable from '@/components/table/DataTable.vue'
import ForumPostDeleteDialog from './ForumPostDeleteDialog.vue'

const forumStore = useForumStore()
const authStore = useAuthStore()

const searchQuery = ref('')
const communityFilter = ref<string>('all')
const flaggedFilter = ref<string>('all')
const pinnedFilter = ref<string>('all')
const lockedFilter = ref<string>('all')
const tablePagination = ref({ pageIndex: 0, pageSize: 10 })

const selectedPostId = ref<string | null>(null)
const deleteDialogOpen = ref(false)

const canModerate = computed(() => authStore.hasPermission('MODERATE', 'FORUM_POST'))

onMounted(() => {
  loadPosts()
  forumStore.fetchCommunities()
})

async function loadPosts() {
  await forumStore.fetchPosts({
    search: searchQuery.value || undefined,
    communityId: communityFilter.value === 'all' ? undefined : communityFilter.value,
    is_flagged: flaggedFilter.value === 'all' ? undefined : flaggedFilter.value === 'flagged',
    is_pinned: pinnedFilter.value === 'all' ? undefined : pinnedFilter.value === 'pinned',
    is_locked: lockedFilter.value === 'all' ? undefined : lockedFilter.value === 'locked',
    page: tablePagination.value.pageIndex + 1,
    limit: tablePagination.value.pageSize,
  })
}

// Watchers
watchDebounced(
  searchQuery,
  () => {
    tablePagination.value.pageIndex = 0
    loadPosts()
  },
  { debounce: 500 },
)

watch([communityFilter, flaggedFilter, pinnedFilter, lockedFilter], () => {
  tablePagination.value.pageIndex = 0
  loadPosts()
})

watch(
  () => tablePagination.value,
  () => loadPosts(),
  { deep: true },
)

function confirmDelete(post: ForumPost) {
  selectedPostId.value = post.id
  deleteDialogOpen.value = true
}

async function togglePin(post: ForumPost) {
  try {
    await forumStore.togglePin(post)
    toast.success(post.is_pinned ? 'Post unpinned' : 'Post pinned')
  } catch {
    toast.error('Failed to update pin status')
  }
}

async function toggleLock(post: ForumPost) {
  try {
    await forumStore.toggleLock(post)
    toast.success(post.is_locked ? 'Post unlocked' : 'Post locked')
  } catch {
    toast.error('Failed to update lock status')
  }
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
    accessorKey: 'title',
    header: 'Title',
    cell: ({ row }) => {
      const post = row.original
      return h('div', { class: 'flex flex-col gap-1' }, [
        h('div', { class: 'flex items-center gap-2' }, [
          h('span', { class: 'font-medium text-sm' }, post.title),
          post.is_pinned && h(IconPin, { class: 'h-3 w-3 text-blue-500', 'aria-label': 'Pinned' }),
          post.is_locked &&
            h(IconLock, { class: 'h-3 w-3 text-amber-500', 'aria-label': 'Locked' }),
        ]),
        h('div', { class: 'flex items-center gap-1 text-xs text-muted-foreground' }, [
          h(IconUser, { class: 'h-3 w-3' }),
          h('span', {}, post.author?.username || 'Unknown'),
          h('span', { class: 'mx-1' }, '•'),
          h('span', {}, post.community?.name || 'Unknown Community'),
        ]),
      ])
    },
  },
  {
    accessorKey: 'stats',
    header: 'Stats',
    cell: ({ row }) => {
      const post = row.original
      return h('div', { class: 'flex items-center gap-3 text-muted-foreground text-xs' }, [
        h('div', { class: 'flex items-center gap-1' }, [
          h(IconEye, { class: 'h-3 w-3' }),
          h('span', {}, post.view_count || 0),
        ]),
        h('div', { class: 'flex items-center gap-1' }, [
          h(IconMessage, { class: 'h-3 w-3' }),
          h('span', {}, post.comment_count || 0),
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
    accessorKey: 'created_at',
    header: 'Created',
    cell: ({ row }) => {
      const date = new Date(row.getValue('created_at') as string)
      return h('span', { class: 'text-muted-foreground text-sm' }, date.toLocaleDateString())
    },
  },
  {
    id: 'actions',
    header: 'Actions',
    cell: ({ row }) => {
      const post = row.original
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
                  h(
                    DropdownMenuItem,
                    { onClick: () => togglePin(post) },
                    {
                      default: () =>
                        h('div', { class: 'flex items-center gap-2' }, [
                          h(IconPin, { class: 'h-4 w-4' }),
                          post.is_pinned ? 'Unpin' : 'Pin',
                        ]),
                    },
                  ),
                  h(
                    DropdownMenuItem,
                    { onClick: () => toggleLock(post) },
                    {
                      default: () =>
                        h('div', { class: 'flex items-center gap-2' }, [
                          h(IconLock, { class: 'h-4 w-4' }),
                          post.is_locked ? 'Unlock' : 'Lock',
                        ]),
                    },
                  ),
                  h(DropdownMenuSeparator, {}),
                  h(
                    DropdownMenuItem,
                    { onClick: () => confirmDelete(post) },
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
  <div class="relative flex flex-col gap-4 overflow-auto px-4 lg:px-6">
    <DataTable
      :columns="columns"
      :data="forumStore.posts"
      :pagination="tablePagination"
      :row-count="forumStore.totalPosts"
      :loading="forumStore.postsLoading"
      @update:pagination="tablePagination = $event"
    >
      <template #toolbar-left>
        <div class="flex flex-wrap items-center gap-2 w-full lg:w-auto">
          <Input
            v-model="searchQuery"
            placeholder="Search posts..."
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
            <Select v-model="communityFilter">
              <SelectTrigger class="h-8 w-[150px]">
                <SelectValue placeholder="Community" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">All Communities</SelectItem>
                <SelectItem
                  v-for="community in forumStore.communities"
                  :key="community.id"
                  :value="community.id"
                >
                  {{ community.name }}
                </SelectItem>
              </SelectContent>
            </Select>

            <Select v-model="flaggedFilter">
              <SelectTrigger class="h-8 w-[130px]">
                <SelectValue placeholder="Flag Status" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">All</SelectItem>
                <SelectItem value="flagged">Flagged</SelectItem>
                <SelectItem value="clean">Clean</SelectItem>
              </SelectContent>
            </Select>

            <Select v-model="pinnedFilter">
              <SelectTrigger class="h-8 w-[130px]">
                <SelectValue placeholder="Pinned" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">All</SelectItem>
                <SelectItem value="pinned">Pinned</SelectItem>
                <SelectItem value="unpinned">Unpinned</SelectItem>
              </SelectContent>
            </Select>

            <Select v-model="lockedFilter">
              <SelectTrigger class="h-8 w-[130px]">
                <SelectValue placeholder="Locked" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">All</SelectItem>
                <SelectItem value="locked">Locked</SelectItem>
                <SelectItem value="unlocked">Unlocked</SelectItem>
              </SelectContent>
            </Select>

            <Button
              variant="ghost"
              size="icon"
              class="h-8 w-8"
              @click="loadPosts()"
              title="Refresh"
            >
              <IconRefresh
                class="h-3.5 w-3.5"
                :class="{ 'animate-spin': forumStore.postsLoading }"
              />
            </Button>
          </div>
        </div>
      </template>
    </DataTable>

    <!-- Error state -->
    <div
      v-if="forumStore.postsError"
      class="flex items-center justify-between rounded-lg border border-destructive/50 bg-destructive/10 p-4"
    >
      <span class="text-destructive">{{ forumStore.postsError }}</span>
      <Button variant="outline" size="sm" @click="loadPosts()">Retry</Button>
    </div>
  </div>

  <ForumPostDeleteDialog
    v-model:open="deleteDialogOpen"
    :post-id="selectedPostId"
    @success="loadPosts"
  />
</template>
