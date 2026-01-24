<script setup lang="ts">
import { ref, onMounted, computed, h, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { watchDebounced } from '@vueuse/core'
import type { ColumnDef } from '@tanstack/vue-table'
import {
  IconDotsVertical,
  IconEye,
  IconEyeOff,
  IconPencil,
  IconPlus,
  IconRefresh,
  IconStar,
  IconStarFilled,
  IconTrash,
  IconX,
} from '@tabler/icons-vue'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
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
import ProblemListDeleteDialog from './ProblemListDeleteDialog.vue'

const router = useRouter()
const { t } = useI18n()
const store = useAdminProblemListsStore()
const authStore = useAuthStore()

const searchQuery = ref('')
const featuredFilter = ref<string>('all')
const visibilityFilter = ref<string>('all')
const tablePagination = ref({ pageIndex: 0, pageSize: 10 })

const selectedListId = ref<string | null>(null)
const selectedListName = ref<string | null>(null)
const deleteDialogOpen = ref(false)

const canCreate = computed(() => authStore.hasPermission('CREATE', 'PROBLEM_LIST'))
const canUpdate = computed(() => authStore.hasPermission('UPDATE', 'PROBLEM_LIST'))
const canDelete = computed(() => authStore.hasPermission('DELETE', 'PROBLEM_LIST'))

onMounted(() => loadLists())

async function loadLists() {
  await store.fetchLists({
    search: searchQuery.value || undefined,
    is_featured: featuredFilter.value === 'all' ? undefined : featuredFilter.value === 'featured',
    is_public: visibilityFilter.value === 'all' ? undefined : visibilityFilter.value === 'public',
    page: tablePagination.value.pageIndex + 1,
    limit: tablePagination.value.pageSize,
  })
}

// Watchers
watchDebounced(
  searchQuery,
  () => {
    tablePagination.value.pageIndex = 0
    loadLists()
  },
  { debounce: 500 },
)

watch([featuredFilter, visibilityFilter], () => {
  tablePagination.value.pageIndex = 0
  loadLists()
})

watch(
  () => tablePagination.value,
  () => loadLists(),
  { deep: true },
)

function editList(id: string) {
  router.push({ name: 'problem-list-edit', params: { id } })
}

function confirmDelete(list: ProblemList) {
  selectedListId.value = list.id
  selectedListName.value = list.name
  deleteDialogOpen.value = true
}

const columns: ColumnDef<ProblemList>[] = [
  {
    accessorKey: 'name',
    header: () => t('problemLists.columns.name'),
    cell: ({ row }) => {
      const list = row.original
      return h('div', { class: 'flex flex-col' }, [
        h('span', { class: 'font-medium text-sm' }, list.name),
        h(
          'span',
          { class: 'text-muted-foreground text-xs line-clamp-1' },
          list.description || t('common.noData'),
        ),
      ])
    },
  },
  {
    accessorKey: 'is_featured',
    header: () => t('problemLists.columns.featured'),
    cell: ({ row }) => {
      const isFeatured = row.getValue('is_featured') as boolean
      return isFeatured
        ? h(IconStarFilled, { class: 'h-4 w-4 text-yellow-500' })
        : h(IconStar, { class: 'h-4 w-4 text-muted-foreground opacity-20' })
    },
  },
  {
    accessorKey: 'is_public',
    header: () => t('problemLists.columns.visibility'),
    cell: ({ row }) => {
      const isPublic = row.getValue('is_public') as boolean
      return h(
        Badge,
        { variant: isPublic ? 'default' : 'secondary' },
        {
          default: () => [
            isPublic
              ? h(IconEye, { class: 'mr-1 h-3 w-3' })
              : h(IconEyeOff, { class: 'mr-1 h-3 w-3' }),
            isPublic ? t('problemLists.filters.public') : t('problemLists.filters.private'),
          ],
        },
      )
    },
  },
  {
    accessorKey: 'problem_count',
    header: () => t('problemLists.columns.problems'),
    cell: ({ row }) => {
      const count = row.original.problem_count || 0
      return h(
        'span',
        { class: 'text-muted-foreground text-sm tabular-nums' },
        count.toLocaleString(),
      )
    },
  },
  {
    accessorKey: 'banner_order',
    header: () => t('problemLists.columns.order'),
    cell: ({ row }) => {
      const order = row.original.banner_order
      return h('span', { class: 'text-muted-foreground text-sm tabular-nums' }, order)
    },
  },
  {
    accessorKey: 'updated_at',
    header: () => t('common.updated'),
    cell: ({ row }) => {
      const date = new Date(row.getValue('updated_at') as string)
      return h('span', { class: 'text-muted-foreground text-sm' }, date.toLocaleDateString())
    },
  },
  {
    id: 'actions',
    header: () => t('common.actions'),
    cell: ({ row }) => {
      const list = row.original
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
                  canUpdate.value
                    ? h(
                        DropdownMenuItem,
                        { onClick: () => editList(list.id) },
                        {
                          default: () =>
                            h('div', { class: 'flex items-center gap-2' }, [
                              h(IconPencil, { class: 'h-4 w-4' }),
                              t('common.edit'),
                            ]),
                        },
                      )
                    : null,
                  canDelete.value
                    ? h(
                        DropdownMenuItem,
                        { onClick: () => confirmDelete(list) },
                        {
                          default: () =>
                            h('div', { class: 'flex items-center gap-2 text-destructive' }, [
                              h(IconTrash, { class: 'h-4 w-4' }),
                              t('common.delete'),
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
      :data="store.lists"
      :pagination="tablePagination"
      :row-count="store.total"
      :loading="store.isLoading"
      @update:pagination="tablePagination = $event"
    >
      <template #toolbar-left>
        <div class="flex flex-wrap items-center gap-2 w-full lg:w-auto">
          <Input
            v-model="searchQuery"
            :placeholder="t('problemLists.searchPlaceholder')"
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
            <Select v-model="featuredFilter">
              <SelectTrigger class="h-8 w-[130px]">
                <SelectValue :placeholder="t('problemLists.filters.type')" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">{{ t('problemLists.filters.allTypes') }}</SelectItem>
                <SelectItem value="featured">{{ t('problemLists.filters.featured') }}</SelectItem>
                <SelectItem value="standard">{{ t('problemLists.filters.standard') }}</SelectItem>
              </SelectContent>
            </Select>

            <Select v-model="visibilityFilter">
              <SelectTrigger class="h-8 w-[130px]">
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
              class="h-8 w-8"
              @click="loadLists()"
              :title="t('common.refresh')"
            >
              <IconRefresh class="h-3.5 w-3.5" :class="{ 'animate-spin': store.isLoading }" />
            </Button>
          </div>
        </div>
      </template>

      <template #extra-actions>
        <Button
          v-if="canCreate"
          size="sm"
          class="h-8"
          @click="router.push({ name: 'problem-list-create' })"
        >
          <IconPlus class="mr-2 h-4 w-4" />
          <span>{{ t('problemLists.addList') }}</span>
        </Button>
      </template>
    </DataTable>

    <!-- Error state -->
    <div
      v-if="store.error"
      class="flex items-center justify-between rounded-lg border border-destructive/50 bg-destructive/10 p-4"
    >
      <span class="text-destructive">{{ store.error }}</span>
      <Button variant="outline" size="sm" @click="loadLists()">{{ t('common.retry') }}</Button>
    </div>
  </div>

  <ProblemListDeleteDialog
    v-model:open="deleteDialogOpen"
    :list-id="selectedListId"
    :list-name="selectedListName"
    @success="loadLists"
  />
</template>
