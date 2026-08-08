<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { toast } from 'vue-sonner'
import { IconFlag, IconLock, IconPin, IconTrash, IconMessages } from '@tabler/icons-vue'

import { Button } from '@/components/ui/button'

import { useForumStore } from '@/stores/admin/forum'
import { useAuthStore } from '@/stores/auth'
import type { ForumPost } from '@/api/admin/forum'

import DataTable from '@/components/table/DataTable.vue'
import DataTableToolbar, { type Filter } from '@/components/table/DataTableToolbar.vue'
import EntityActionDialog from '@/components/shared/EntityActionDialog.vue'
import { createColumns } from './columns'
import { useDataTable } from '@/composables/useDataTable'

const router = useRouter()
const { t } = useI18n()
const forumStore = useForumStore()
const authStore = useAuthStore()

const communityFilter = ref<string>('all')
const flaggedFilter = ref<string>('all')
const pinnedFilter = ref<string>('all')
const lockedFilter = ref<string>('all')
const deletedFilter = ref<string>('all')

const selectedPostId = ref<string | null>(null)
const selectedPostTitle = ref<string | null>(null)
const deleteDialogOpen = ref(false)
const flagDialogOpen = ref(false)

// Animation state for staggered reveal
const isLoaded = ref(false)

onMounted(() => {
  setTimeout(() => {
    isLoaded.value = true
  }, 100)
})

const canModerate = computed(() => authStore.hasPermission('MODERATE', 'FORUM_POST'))

// Stats for terminal ticker
const stats = computed(() => {
  const posts = forumStore.posts
  const total = forumStore.totalPosts
  const pinned = posts.filter((p) => p.isPinned).length
  const locked = posts.filter((p) => p.isLocked).length
  const flagged = posts.filter((p) => p.isFlagged).length
  return { total, pinned, locked, flagged }
})

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
  {
    modelValue: deletedFilter.value,
    placeholder: t('forum.filters.deleted'),
    width: 'w-[130px]',
    options: [
      { value: 'all', label: t('forum.filters.all') },
      { value: 'deleted', label: t('forum.filters.deletedOnly') },
      { value: 'active', label: t('forum.filters.activeOnly') },
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
  loadEntities: loadPosts,
} = useDataTable<
  ForumPost,
  {
    communityFilter: string
    flaggedFilter: string
    pinnedFilter: string
    lockedFilter: string
    deletedFilter: string
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
  filters: () => ({
    communityFilter: communityFilter.value,
    flaggedFilter: flaggedFilter.value,
    pinnedFilter: pinnedFilter.value,
    lockedFilter: lockedFilter.value,
    deletedFilter: deletedFilter.value,
  }),
  transformParams: ({ search, filters, page, limit }) => ({
    search,
    communityId: filters.communityFilter === 'all' ? undefined : filters.communityFilter,
    isFlagged: filters.flaggedFilter === 'all' ? undefined : filters.flaggedFilter === 'flagged',
    isPinned: filters.pinnedFilter === 'all' ? undefined : filters.pinnedFilter === 'pinned',
    isLocked: filters.lockedFilter === 'all' ? undefined : filters.lockedFilter === 'locked',
    isDeleted: filters.deletedFilter === 'all' ? undefined : filters.deletedFilter === 'deleted',
    page,
    limit,
  }),
  autoLoad: true,
})

// Load communities on mount
onMounted(() => {
  forumStore.fetchCommunities()
})

const columns = createColumns(
  t,
  {
    viewPostDetails: (post: ForumPost) => {
      router.push({ name: 'forum-post-detail', params: { id: post.id } })
    },
    togglePin: async (post: ForumPost) => {
      try {
        await forumStore.togglePin(post)
        toast.success(
          post.isPinned
            ? t('forum.toast.unpinnedSuccessfully')
            : t('forum.toast.pinnedSuccessfully'),
        )
      } catch {
        toast.error(t('forum.toast.failedToUpdatePin'))
      }
    },
    toggleLock: async (post: ForumPost) => {
      try {
        await forumStore.toggleLock(post)
        toast.success(
          post.isLocked
            ? t('forum.toast.unlockedSuccessfully')
            : t('forum.toast.lockedSuccessfully'),
        )
      } catch {
        toast.error(t('forum.toast.failedToUpdateLock'))
      }
    },
    openFlagDialog: (post: ForumPost) => {
      selectedPostId.value = post.id
      selectedPostTitle.value = post.title
      flagDialogOpen.value = true
    },
    unflagPost: async (id: string) => {
      try {
        await forumStore.unflagPost(id)
        toast.success(t('forum.toast.unflaggedSuccessfully'))
      } catch {
        toast.error(t('forum.toast.failedToUnflag'))
      }
    },
    confirmDelete: (post: ForumPost) => {
      selectedPostId.value = post.id
      selectedPostTitle.value = post.title
      deleteDialogOpen.value = true
    },
  },
  () => canModerate.value,
)

async function handleDeletePost(id: string | number) {
  await forumStore.deletePost(String(id))
}

async function handleFlagPost(id: string | number, reason?: string) {
  await forumStore.flagPost(String(id), reason || '')
}

// Bulk actions
const bulkActionLoading = ref(false)

async function handleBulkPin() {
  if (selectedRows.value.length === 0) return
  const ids = selectedRows.value.map((r) => r.id)

  bulkActionLoading.value = true
  try {
    await forumStore.bulkAction(ids, 'pin')
    await loadPosts()
    selectedRows.value = []
    toast.success(t('forum.toast.bulkPinnedSuccessfully'))
  } catch {
    toast.error(t('forum.toast.failedToUpdatePin'))
  } finally {
    bulkActionLoading.value = false
  }
}

async function handleBulkLock() {
  if (selectedRows.value.length === 0) return
  const ids = selectedRows.value.map((r) => r.id)

  bulkActionLoading.value = true
  try {
    await forumStore.bulkAction(ids, 'lock')
    await loadPosts()
    selectedRows.value = []
    toast.success(t('forum.toast.bulkLockedSuccessfully'))
  } catch {
    toast.error(t('forum.toast.failedToUpdateLock'))
  } finally {
    bulkActionLoading.value = false
  }
}

async function handleBulkUnflag() {
  if (selectedRows.value.length === 0) return
  const ids = selectedRows.value.map((r) => r.id)

  bulkActionLoading.value = true
  try {
    await forumStore.bulkAction(ids, 'unflag')
    await loadPosts()
    selectedRows.value = []
    toast.success(t('forum.toast.bulkUnflaggedSuccessfully'))
  } catch {
    toast.error(t('forum.toast.failedToUnflag'))
  } finally {
    bulkActionLoading.value = false
  }
}

async function handleBulkDelete() {
  if (selectedRows.value.length === 0) return
  const ids = selectedRows.value.map((r) => r.id)
  const count = ids.length
  if (!confirm(t('forum.deleteConfirm', { count }))) return

  bulkActionLoading.value = true
  try {
    await forumStore.bulkAction(ids, 'delete')
    await loadPosts()
    selectedRows.value = []
    toast.success(t('forum.toast.bulkDeletedSuccessfully'))
  } catch {
    toast.error(t('forum.toast.failedToDelete'))
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
          {{ t('forum.title') }}
        </h1>
      </div>

      <!-- Stats Ticker -->
      <div
        class="px-4 lg:px-6 py-2.5 flex items-center gap-6 border-t border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--surface-sunken)]"
      >
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]">{{ t('forum.stats.total') }}:</span>
          <span class="font-data text-sm text-[var(--terminal-cyan)] tabular-nums">{{
            stats.total
          }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]"
            >{{ t('forum.stats.pinned') }}:</span
          >
          <span class="font-data text-sm text-[var(--terminal-cyan)] tabular-nums">{{
            stats.pinned
          }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]"
            >{{ t('forum.stats.locked') }}:</span
          >
          <span class="font-data text-sm text-[var(--terminal-amber)] tabular-nums">{{
            stats.locked
          }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]"
            >{{ t('forum.stats.flagged') }}:</span
          >
          <span class="font-data text-sm text-[var(--terminal-red)] tabular-nums">{{
            stats.flagged
          }}</span>
        </div>
        <div class="ml-auto flex items-center gap-2 text-[var(--silver-400)]">
          <IconMessages class="h-4 w-4" />
          <span class="text-xs font-data uppercase tracking-wider">{{
            t('forum.stats.postManagement')
          }}</span>
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
            v-if="canModerate"
            variant="terminal"
            size="sm"
            class="h-8 font-data text-xs border-[var(--silver-300)] hover:border-[var(--terminal-cyan)] hover:text-[var(--terminal-cyan)]"
            @click="handleBulkPin"
            :disabled="bulkActionLoading"
          >
            <IconPin class="h-3.5 w-3.5 mr-1.5" />
            <span class="uppercase tracking-wider">{{ t('forum.bulkActions.bulkPin') }}</span>
          </Button>
          <Button
            v-if="canModerate"
            variant="terminal"
            size="sm"
            class="h-8 font-data text-xs border-[var(--silver-300)] hover:border-[var(--terminal-amber)] hover:text-[var(--terminal-amber)]"
            @click="handleBulkLock"
            :disabled="bulkActionLoading"
          >
            <IconLock class="h-3.5 w-3.5 mr-1.5" />
            <span class="uppercase tracking-wider">{{ t('forum.bulkActions.bulkLock') }}</span>
          </Button>
          <Button
            v-if="canModerate"
            variant="terminal"
            size="sm"
            class="h-8 font-data text-xs border-[var(--silver-300)] hover:border-[var(--terminal-green)] hover:text-[var(--terminal-green)]"
            @click="handleBulkUnflag"
            :disabled="bulkActionLoading"
          >
            <IconFlag class="h-3.5 w-3.5 mr-1.5" />
            <span class="uppercase tracking-wider">{{ t('forum.bulkActions.bulkUnflag') }}</span>
          </Button>
          <Button
            v-if="canModerate"
            variant="terminal"
            size="sm"
            class="h-8 font-data text-xs border-[var(--terminal-red)] text-[var(--terminal-red)] hover:bg-[color-mix(in_oklch,_var(--terminal-red)_10%,_transparent)]"
            @click="handleBulkDelete"
            :disabled="bulkActionLoading"
          >
            <IconTrash class="h-3.5 w-3.5 mr-1.5" />
            <span class="uppercase tracking-wider">{{ t('forum.bulkActions.bulkDelete') }}</span>
          </Button>
        </div>
      </div>
      <Button
        variant="terminal"
        size="sm"
        class="h-8 font-data text-xs text-[var(--silver-500)] hover:text-[var(--foreground)]"
        @click="selectedRows = []"
      >
        [ESC] {{ t('forum.clearSelection') }}
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
          @click="loadPosts()"
        >
          {{ t('common.retry') }}
        </Button>
      </div>
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
