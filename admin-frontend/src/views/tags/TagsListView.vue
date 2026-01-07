<script setup lang="ts">
import { ref, onMounted, computed, h, watch } from 'vue'
import { watchDebounced } from '@vueuse/core'
import type { ColumnDef } from '@tanstack/vue-table'
import {
  IconRefresh,
  IconPlus,
  IconDotsVertical,
  IconPencil,
  IconTrash,
  IconGitMerge,
  IconX,
  IconSearch,
} from '@tabler/icons-vue'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
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
import { useTagsStore } from '@/stores/admin/tags'
import { useAuthStore } from '@/stores/admin/auth'
import { TagType, type Tag } from '@/api/admin/tags'

import DataTable from '@/components/table/DataTable.vue'
import TagEditDialog from './TagEditDialog.vue'
import TagDeleteDialog from './TagDeleteDialog.vue'
import TagMergeDialog from './TagMergeDialog.vue'

const tagsStore = useTagsStore()
const authStore = useAuthStore()

const searchQuery = ref('')
const tagTypeFilter = ref<TagType>(TagType.PROBLEM)
const tablePagination = ref({ pageIndex: 0, pageSize: 20 })

const selectedTag = ref<Tag | null>(null)
const editDialogOpen = ref(false)
const deleteDialogOpen = ref(false)
const mergeDialogOpen = ref(false)

const canManageTags = computed(() => authStore.hasPermission('MANAGE_USERS', 'SYSTEM') || authStore.hasPermission('UPDATE', 'PROBLEM')) // Approximate perm

onMounted(() => loadTags())

async function loadTags() {
  await tagsStore.fetchTags({
    search: searchQuery.value || undefined,
    type: tagTypeFilter.value,
    page: tablePagination.value.pageIndex + 1,
    limit: tablePagination.value.pageSize,
  })
}

watchDebounced(
  searchQuery,
  () => {
    tablePagination.value.pageIndex = 0
    loadTags()
  },
  { debounce: 500 },
)

watch(tagTypeFilter, () => {
  tablePagination.value.pageIndex = 0
  loadTags()
})

watch(
  () => tablePagination.value,
  () => loadTags(),
  { deep: true },
)

function openCreateDialog() {
  selectedTag.value = null
  editDialogOpen.value = true
}

function openEditDialog(tag: Tag) {
  selectedTag.value = tag
  editDialogOpen.value = true
}

function openDeleteDialog(tag: Tag) {
  selectedTag.value = tag
  deleteDialogOpen.value = true
}

function openMergeDialog(tag: Tag) {
  selectedTag.value = tag
  mergeDialogOpen.value = true
}

const columns: ColumnDef<Tag>[] = [
  {
    accessorKey: 'name',
    header: 'Name',
    cell: ({ row }) => {
      const tag = row.original
      return h('div', { class: 'flex items-center gap-2' }, [
        tag.color
          ? h('div', {
              class: 'w-3 h-3 rounded-full',
              style: { backgroundColor: tag.color },
            })
          : null,
        h('span', { class: 'font-medium' }, tag.name),
      ])
    },
  },
  {
    accessorKey: 'slug',
    header: 'Slug',
    cell: ({ row }) => h(Badge, { variant: 'secondary' }, () => row.original.slug || '-'),
  },
  {
    accessorKey: 'usage_count',
    header: 'Usage',
    cell: ({ row }) => h('span', { class: 'tabular-nums' }, row.original.usage_count.toLocaleString()),
  },
  {
    accessorKey: 'description',
    header: 'Description',
    cell: ({ row }) =>
      h(
        'span',
        { class: 'text-muted-foreground truncate max-w-[200px] block' },
        row.original.description || '-'
      ),
  },
  {
    id: 'actions',
    header: 'Actions',
    cell: ({ row }) => {
      const tag = row.original
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
                  canManageTags.value &&
                    h(
                      DropdownMenuItem,
                      { onClick: () => openEditDialog(tag) },
                      {
                        default: () =>
                          h('div', { class: 'flex items-center gap-2' }, [
                            h(IconPencil, { class: 'h-4 w-4' }),
                            'Edit',
                          ]),
                      },
                    ),
                  canManageTags.value &&
                    h(
                      DropdownMenuItem,
                      { onClick: () => openMergeDialog(tag) },
                      {
                        default: () =>
                          h('div', { class: 'flex items-center gap-2' }, [
                            h(IconGitMerge, { class: 'h-4 w-4' }),
                            'Merge into...',
                          ]),
                      },
                    ),
                  canManageTags.value && h(DropdownMenuSeparator),
                  canManageTags.value &&
                    h(
                      DropdownMenuItem,
                      { onClick: () => openDeleteDialog(tag), class: 'text-destructive' },
                      {
                        default: () =>
                          h('div', { class: 'flex items-center gap-2' }, [
                            h(IconTrash, { class: 'h-4 w-4' }),
                            'Delete',
                          ]),
                      },
                    ),
                  !canManageTags.value && h(DropdownMenuItem, { disabled: true }, { default: () => 'No actions available' }),
                ].filter(Boolean),
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
    <div class="flex items-center justify-between">
      <h1 class="text-2xl font-bold tracking-tight">Tags Management</h1>
      <Button v-if="canManageTags" @click="openCreateDialog">
        <IconPlus class="mr-2 h-4 w-4" />
        Create Tag
      </Button>
    </div>

    <DataTable
      :columns="columns"
      :data="tagsStore.tags"
      :pagination="tablePagination"
      :row-count="tagsStore.total"
      :loading="tagsStore.isLoading"
      @update:pagination="tablePagination = $event"
    >
      <template #toolbar-left>
        <div class="flex flex-wrap items-center gap-2 w-full lg:w-auto">
          <Input
            v-model="searchQuery"
            placeholder="Search tags..."
            class="h-8 min-w-[150px] w-full lg:w-[250px]"
          >
            <template #leading>
              <IconSearch class="h-3 w-3 text-muted-foreground" />
            </template>
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

          <Select v-model="tagTypeFilter">
            <SelectTrigger class="h-8 w-[150px]">
              <SelectValue placeholder="Tag Type" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem :value="TagType.PROBLEM">Problem Tags</SelectItem>
              <SelectItem :value="TagType.FORUM">Forum Tags</SelectItem>
            </SelectContent>
          </Select>

          <Button
            variant="ghost"
            size="icon"
            class="h-8 w-8"
            @click="loadTags()"
            title="Refresh"
          >
            <IconRefresh
              class="h-3.5 w-3.5"
              :class="{ 'animate-spin': tagsStore.isLoading }"
            />
          </Button>
        </div>
      </template>
    </DataTable>

    <TagEditDialog
      v-model:open="editDialogOpen"
      :tag-to-edit="selectedTag"
      :tag-type="tagTypeFilter"
      @success="loadTags"
    />

    <TagDeleteDialog
      v-model:open="deleteDialogOpen"
      :tag-id="selectedTag?.id || null"
      :tag-name="selectedTag?.name || null"
      :tag-type="tagTypeFilter"
      @success="loadTags"
    />

    <TagMergeDialog
      v-model:open="mergeDialogOpen"
      :source-tag-id="selectedTag?.id || null"
      :source-tag-name="selectedTag?.name || null"
      :tag-type="tagTypeFilter"
      @success="loadTags"
    />
  </div>
</template>
