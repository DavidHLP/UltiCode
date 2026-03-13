<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { IconPlus, IconRefresh, IconX } from '@tabler/icons-vue'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { useAdminProblemListsStore } from '@/stores/admin/problem-lists'
import { useAuthStore } from '@/stores/auth'
import type { ProblemList } from '@/api/admin/problem-lists'

import DataTable from '@/components/table/DataTable.vue'
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

const {
  searchQuery,
  tablePagination,
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
  filters: {
    featuredFilter: featuredFilter.value,
    visibilityFilter: visibilityFilter.value,
  },
  transformParams: ({ search, filters, page, limit }) => ({
    search,
    is_featured:
      filters.featuredFilter === 'all' ? undefined : filters.featuredFilter === 'featured',
    is_public:
      filters.visibilityFilter === 'all' ? undefined : filters.visibilityFilter === 'public',
    page,
    limit,
  }),
  autoLoad: true,
})

// Stats for terminal ticker
const stats = computed(() => ({
  total: store.total,
  featured: store.lists.filter((l) => l.is_featured).length,
  public: store.lists.filter((l) => l.is_public).length,
}))

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
  <div class="relative flex flex-col overflow-auto">
    <!-- Terminal Header -->
    <div class="border-b border-[var(--silver-200)] bg-[var(--card)]">
      <!-- Title Row -->
      <div class="px-4 lg:px-6 py-4 flex items-center justify-between">
        <div class="flex items-center gap-4">
          <span class="terminal-prompt">problem_lists</span>
          <span class="terminal-cursor" />
          <h1 class="text-base font-semibold text-[var(--foreground)]">
            {{ t('problemLists.title') }}
          </h1>
        </div>
        <Button
          v-if="canCreate"
          variant="terminal"
          class="font-data text-xs"
          @click="router.push({ name: 'problem-list-create' })"
        >
          <IconPlus class="mr-1.5 h-3.5 w-3.5" />
          CREATE
        </Button>
      </div>

      <!-- Stats Ticker -->
      <div
        class="px-4 lg:px-6 py-2.5 border-t border-[var(--silver-200)] bg-[var(--surface-sunken)]"
      >
        <div class="flex items-center gap-6 text-xs">
          <div class="flex items-center gap-2">
            <span class="terminal-label">total:</span>
            <span class="font-data text-[var(--terminal-cyan)]">{{ stats.total }}</span>
          </div>
          <div class="flex items-center gap-2">
            <span class="terminal-label">featured:</span>
            <span class="font-data text-[var(--terminal-amber)]">{{ stats.featured }}</span>
          </div>
          <div class="flex items-center gap-2">
            <span class="terminal-label">public:</span>
            <span class="font-data text-[var(--terminal-green)]">{{ stats.public }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Main Content -->
    <div class="flex-1 py-4">
      <!-- Error State -->
      <div
        v-if="error"
        class="mb-4 flex items-center justify-between border border-[var(--terminal-red)] bg-[oklch(0.6_0.2_25/0.08)] px-4 py-3"
      >
        <div class="flex items-center gap-2">
          <span class="font-data text-xs text-[var(--terminal-red)]">&gt; ERROR:</span>
          <span class="text-sm text-[var(--foreground)]">{{ error }}</span>
        </div>
        <Button
          variant="outline"
          size="sm"
          class="font-data text-xs border-[var(--terminal-red)] text-[var(--terminal-red)] hover:bg-[oklch(0.6_0.2_25/0.15)]"
          @click="loadLists()"
        >
          {{ t('common.retry') }}
        </Button>
      </div>

      <!-- Data Table -->
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
              :placeholder="t('problemLists.searchPlaceholder')"
              class="terminal-input h-8 min-w-[150px] w-full lg:w-[250px]"
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
              <Select v-model="featuredFilter">
                <SelectTrigger class="terminal-input h-8 w-[130px] font-data text-xs">
                  <SelectValue :placeholder="t('problemLists.filters.type')" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">{{ t('problemLists.filters.allTypes') }}</SelectItem>
                  <SelectItem value="featured">{{ t('problemLists.filters.featured') }}</SelectItem>
                  <SelectItem value="standard">{{ t('problemLists.filters.standard') }}</SelectItem>
                </SelectContent>
              </Select>

              <Select v-model="visibilityFilter">
                <SelectTrigger class="terminal-input h-8 w-[130px] font-data text-xs">
                  <SelectValue :placeholder="t('problemLists.filters.visibility')" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">{{ t('problemLists.filters.allVisibility') }}</SelectItem>
                  <SelectItem value="public">{{ t('problemLists.filters.public') }}</SelectItem>
                  <SelectItem value="private">{{ t('problemLists.filters.private') }}</SelectItem>
                </SelectContent>
              </Select>

              <Button
                variant="ghost"
                size="icon"
                class="h-8 w-8 hover:bg-[var(--silver-100)] dark:hover:bg-[var(--silver-800)]"
                @click="loadLists()"
                :title="t('common.refresh')"
              >
                <IconRefresh
                  class="h-3.5 w-3.5 text-[var(--silver-400)]"
                  :class="{ 'animate-spin': loading }"
                />
              </Button>
            </div>
          </div>
        </template>
      </DataTable>
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
