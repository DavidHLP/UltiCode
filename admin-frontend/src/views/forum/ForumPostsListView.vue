<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { toast } from 'vue-sonner'

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

const columns = createColumns(
  t,
  {
    viewPostDetails: (post: ForumPost) => {
      router.push({ name: 'forum-post-detail-overview', params: { id: post.id } })
    },
    togglePin: async (post: ForumPost) => {
      try {
        await forumStore.togglePin(post)
        toast.success(
          post.is_pinned
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
          post.is_locked
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
