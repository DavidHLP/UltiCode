<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { IconPlus, IconList } from '@tabler/icons-vue'

import { Button } from '@/components/ui/button'
import { useAdminProblemListsStore } from '@/stores/admin/problem-lists'
import { useAuthStore } from '@/stores/auth'
import type { ProblemList } from '@/api/admin/problem-lists'

import DataTable from '@/components/table/DataTable.vue'
import DataTableToolbar, { type Filter } from '@/components/table/DataTableToolbar.vue'
import EntityActionDialog from '@/components/shared/EntityActionDialog.vue'
import { useDataTable } from '@/composables/useDataTable'
import { createColumns } from './columns'

const router = useRouter()
const { t } = useI18n()
const store = useAdminProblemListsStore()
const authStore = useAuthStore()

const featuredFilter = ref<string>('all')
const visibilityFilter = ref<string>('all')

const selectedListId = ref<string | null>(null)
const selectedListName = ref<string | null>(null)
const deleteDialogOpen = ref(false)

const canCreate = computed(() => authStore.hasPermission('CREATE', 'PROBLEM_LIST'))
const canUpdate = computed(() => authStore.hasPermission('UPDATE', 'PROBLEM_LIST'))
const canDelete = computed(() => authStore.hasPermission('DELETE', 'PROBLEM_LIST'))

// Animation state for staggered reveal
const isLoaded = ref(false)

onMounted(() => {
  setTimeout(() => {
    isLoaded.value = true
  }, 100)
})

const {
  searchQuery,
  tablePagination,
  selectedRows,
  loading,
  data,
  total,
  error,
  loadEntities: loadLists,
} = useDataTable<
  ProblemList,
  { featuredFilter: string; visibilityFilter: string },
  Parameters<typeof store.fetchLists>[0]
>({
  store: {
    data: computed(() => store.lists),
    total: computed(() => store.total),
    isLoading: computed(() => store.isLoading),
    error: computed(() => store.error),
    fetch: (params) => store.fetchLists(params),
  },
  filters: () => ({
    featuredFilter: featuredFilter.value,
    visibilityFilter: visibilityFilter.value,
  }),
  transformParams: ({ search, filters, page, limit }) => ({
    search,
    isFeatured:
      filters.featuredFilter === 'all' ? undefined : filters.featuredFilter === 'featured',
    isPublic:
      filters.visibilityFilter === 'all' ? undefined : filters.visibilityFilter === 'public',
    page,
    limit,
  }),
  autoLoad: true,
})

// Stats for terminal ticker
const stats = computed(() => ({
  total: store.total,
  featured: (store.lists || []).filter((l) => l.isFeatured).length,
  public: (store.lists || []).filter((l) => l.isPublic).length,
}))

// Toolbar filters for DataTableToolbar
const toolbarFilters = computed<Filter[]>(() => [
  {
    modelValue: featuredFilter.value,
    placeholder: t('problemLists.filters.type'),
    width: 'w-[130px]',
    options: [
      { value: 'all', label: t('problemLists.filters.allTypes') },
      { value: 'featured', label: t('problemLists.filters.featured') },
      { value: 'standard', label: t('problemLists.filters.standard') },
    ],
  },
  {
    modelValue: visibilityFilter.value,
    placeholder: t('problemLists.filters.visibility'),
    width: 'w-[130px]',
    options: [
      { value: 'all', label: t('problemLists.filters.allVisibility') },
      { value: 'public', label: t('problemLists.filters.public') },
      { value: 'private', label: t('problemLists.filters.private') },
    ],
  },
])

function editList(id: string) {
  router.push({ name: 'problem-list-edit', params: { id } })
}

function confirmDelete(list: ProblemList) {
  selectedListId.value = list.id
  selectedListName.value = list.name
  deleteDialogOpen.value = true
}

async function handleDelete(id: string | number) {
  await store.deleteList(String(id))
}

const columns = createColumns(
  t,
  { editList, deleteList: confirmDelete },
  () => canUpdate.value,
  () => canDelete.value,
)
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
          {{ t('problemLists.title') }}
        </h1>
        <Button
          v-if="canCreate"
          variant="terminal"
          size="sm"
          class="font-data text-xs border-[var(--silver-300)] hover:border-[var(--accent-electric)] hover:text-[var(--accent-electric)] transition-colors"
          @click="router.push({ name: 'problem-list-create' })"
        >
          <IconPlus class="h-4 w-4 mr-1.5" />
          <span class="uppercase tracking-wider">{{ t('problemLists.addList') }}</span>
        </Button>
      </div>

      <!-- Stats Ticker -->
      <div
        class="px-4 lg:px-6 py-2.5 flex items-center gap-6 border-t border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--surface-sunken)]"
      >
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]"
            >{{ t('problemLists.stats.total') }}:</span
          >
          <span class="font-data text-sm text-[var(--terminal-cyan)] tabular-nums">{{
            stats.total
          }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]"
            >{{ t('problemLists.stats.featured') }}:</span
          >
          <span class="font-data text-sm text-[var(--terminal-amber)] tabular-nums">{{
            stats.featured
          }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]"
            >{{ t('problemLists.stats.public') }}:</span
          >
          <span class="font-data text-sm text-[var(--terminal-green)] tabular-nums">{{
            stats.public
          }}</span>
        </div>
        <div class="ml-auto flex items-center gap-2 text-[var(--silver-400)]">
          <IconList class="h-4 w-4" />
          <span class="text-xs font-data uppercase tracking-wider">{{
            t('problemLists.stats.listManagement')
          }}</span>
        </div>
      </div>
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
            :search-placeholder="t('problemLists.searchPlaceholder')"
            :filters="toolbarFilters"
            @update:filter="
              (index, value) =>
                index === 0 ? (featuredFilter = String(value)) : (visibilityFilter = String(value))
            "
            :loading="loading"
            :on-refresh="loadLists"
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
          @click="loadLists()"
        >
          {{ t('common.retry') }}
        </Button>
      </div>
    </div>
  </div>

  <EntityActionDialog
    v-model:open="deleteDialogOpen"
    :entity-id="selectedListId"
    :entity-title="selectedListName"
    action="delete"
    :title="t('problemLists.delete.title')"
    :description="
      t('problemLists.delete.description', {
        name: selectedListName || t('problemLists.delete.thisList'),
      })
    "
    :confirm-label="t('problemLists.delete.confirm')"
    :cancel-label="t('problemLists.delete.cancel')"
    :success-label="t('problemLists.toast.deletedSuccess')"
    :error-label="t('problemLists.toast.deleteFailed')"
    :on-action="handleDelete"
    @success="loadLists"
  />
</template>
