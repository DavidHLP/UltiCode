<script setup lang="ts">
import { ref, onMounted, computed, h, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { watchDebounced } from '@vueuse/core'
import type { ColumnDef } from '@tanstack/vue-table'
import { toast } from 'vue-sonner'
import {
  IconCircleXFilled,
  IconDotsVertical,
  IconGitMerge,
  IconHash,
  IconPencil,
  IconPlus,
  IconRefresh,
  IconTag,
  IconTrash,
} from '@tabler/icons-vue'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { Checkbox } from '@/components/ui/checkbox'
import { Separator } from '@/components/ui/separator'
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

const { t } = useI18n()

import DataTable from '@/components/table/DataTable.vue'
import TagEditDialog from './TagEditDialog.vue'
import TagDeleteDialog from './TagDeleteDialog.vue'
import TagMergeDialog from './TagMergeDialog.vue'

const tagsStore = useTagsStore()
const authStore = useAuthStore()

const searchQuery = ref('')
const tagTypeFilter = ref<TagType>(TagType.PROBLEM)
const tablePagination = ref({ pageIndex: 0, pageSize: 10 })

const selectedTag = ref<Tag | null>(null)
const editDialogOpen = ref(false)
const deleteDialogOpen = ref(false)
const mergeDialogOpen = ref(false)

const bulkActionLoading = ref(false)
const selectedRows = ref<Tag[]>([])

const canManageTags = computed(
  () =>
    authStore.hasPermission('MANAGE_USERS', 'SYSTEM') ||
    authStore.hasPermission('UPDATE', 'PROBLEM'),
)

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

async function handleBulkDelete() {
  if (selectedRows.value.length === 0) return
  const count = selectedRows.value.length
  const confirmMsg = t('tags.toast.bulkDeleteConfirm', { count })
  if (!confirm(confirmMsg)) return

  bulkActionLoading.value = true
  try {
    for (const tag of selectedRows.value) {
      await tagsStore.deleteTag(tag.id, tagTypeFilter.value)
    }
    await loadTags()
    selectedRows.value = []
    toast.success(t('tags.toast.bulkDeleteSuccess', { count }))
  } catch {
    toast.error(t('tags.toast.bulkDeleteFailed'))
  } finally {
    bulkActionLoading.value = false
  }
}

const columns: ColumnDef<Tag>[] = [
  {
    id: 'select',
    header: ({ table }) =>
      h(Checkbox, {
        modelValue:
          table.getIsAllPageRowsSelected() ||
          (table.getIsSomePageRowsSelected() && 'indeterminate'),
        'onUpdate:modelValue': (value: boolean | 'indeterminate') =>
          table.toggleAllPageRowsSelected(!!value),
        'aria-label': t('common.selectAll'),
      }),
    cell: ({ row }) =>
      h(Checkbox, {
        modelValue: row.getIsSelected(),
        'onUpdate:modelValue': (value: boolean | 'indeterminate') => row.toggleSelected(!!value),
        'aria-label': t('table.selected', { count: 1 }),
      }),
    enableSorting: false,
    enableHiding: false,
  },
  {
    accessorKey: 'name',
    header: () => t('tags.columns.tag'),
    cell: ({ row }) => {
      const tag = row.original
      return h('div', { class: 'flex items-center gap-3' }, [
        h(
          'div',
          {
            class: 'h-9 w-9 rounded-lg flex items-center justify-center',
            style: {
              backgroundColor: tag.color ? `${tag.color}20` : 'var(--primary-10)',
              color: tag.color || 'var(--primary)',
            },
          },
          [h(IconTag, { class: 'h-4 w-4' })],
        ),
        h('div', { class: 'flex flex-col' }, [
          h('div', { class: 'flex items-center gap-2' }, [
            tag.color
              ? h('div', {
                  class: 'w-2.5 h-2.5 rounded-full',
                  style: { backgroundColor: tag.color },
                })
              : null,
            h('span', { class: 'font-medium text-sm' }, tag.name),
          ]),
          h('span', { class: 'text-muted-foreground text-xs' }, tag.slug || '-'),
        ]),
      ])
    },
  },
  {
    accessorKey: 'usage_count',
    header: () => t('tags.columns.usage'),
    cell: ({ row }) => {
      return h('div', { class: 'flex items-center gap-2' }, [
        h(IconHash, { class: 'h-4 w-4 text-muted-foreground' }),
        h(Badge, { variant: 'secondary' }, () => row.original.usage_count.toLocaleString()),
      ])
    },
  },
  {
    accessorKey: 'description',
    header: () => t('tags.columns.description'),
    cell: ({ row }) =>
      h(
        'span',
        { class: 'text-muted-foreground text-sm truncate max-w-[250px] block' },
        row.original.description || '-',
      ),
  },
  {
    id: 'actions',
    header: () => t('tags.columns.actions'),
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
                default: () =>
                  [
                    canManageTags.value &&
                      h(
                        DropdownMenuItem,
                        { onClick: () => openEditDialog(tag) },
                        {
                          default: () =>
                            h('div', { class: 'flex items-center gap-2' }, [
                              h(IconPencil, { class: 'h-4 w-4' }),
                              t('tags.actions.edit'),
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
                              t('tags.actions.mergeInto'),
                            ]),
                        },
                      ),
                    canManageTags.value && h(DropdownMenuSeparator),
                    canManageTags.value &&
                      h(
                        DropdownMenuItem,
                        { onClick: () => openDeleteDialog(tag) },
                        {
                          default: () =>
                            h('div', { class: 'flex items-center gap-2 text-destructive' }, [
                              h(IconTrash, { class: 'h-4 w-4' }),
                              t('tags.actions.delete'),
                            ]),
                        },
                      ),
                    !canManageTags.value &&
                      h(
                        DropdownMenuItem,
                        { disabled: true },
                        { default: () => t('tags.actions.noActionsAvailable') },
                      ),
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
    <div
      v-if="selectedRows.length > 0"
      class="flex items-center justify-between rounded-lg border border-primary/20 bg-primary/5 p-2 px-4 animate-in fade-in slide-in-from-top-2"
    >
      <div class="flex items-center gap-3">
        <span class="text-sm font-medium">
          {{ t('tags.selected', { count: selectedRows.length }) }}
        </span>
        <Separator orientation="vertical" class="h-4" />
        <div class="flex items-center gap-2">
          <Button
            v-if="canManageTags"
            variant="destructive"
            size="sm"
            class="h-8 text-xs"
            @click="handleBulkDelete"
            :disabled="bulkActionLoading"
          >
            <IconTrash class="h-3.5 w-3.5 mr-1" />
            {{ t('tags.bulkDelete') }}
          </Button>
        </div>
      </div>
      <Button variant="ghost" size="sm" class="h-8 text-xs" @click="selectedRows = []">
        {{ t('tags.clearSelection') }}
      </Button>
    </div>

    <DataTable
      :columns="columns"
      :data="tagsStore.tags"
      :pagination="tablePagination"
      :row-count="tagsStore.total"
      :loading="tagsStore.isLoading"
      v-model:selected-rows="selectedRows"
      @update:pagination="tablePagination = $event"
    >
      <template #toolbar-left>
        <Input v-model="searchQuery" :placeholder="t('tags.searchPlaceholder')" class="min-w-[200px] w-[260px]">
          <template #trailing>
            <button
              v-if="searchQuery"
              @click="searchQuery = ''"
              class="rounded-sm opacity-70 hover:opacity-100"
            >
              <IconCircleXFilled class="h-4 w-4" />
            </button>
          </template>
        </Input>
        <Select v-model="tagTypeFilter">
          <SelectTrigger class="w-[150px]">
            <SelectValue :placeholder="t('tags.tagType')" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem :value="TagType.PROBLEM">{{ t('tags.problemTags') }}</SelectItem>
            <SelectItem :value="TagType.FORUM">{{ t('tags.forumTags') }}</SelectItem>
          </SelectContent>
        </Select>
        <Button variant="outline" size="icon" @click="loadTags()" :title="t('common.refresh')">
          <IconRefresh class="h-4 w-4" :class="{ 'animate-spin': tagsStore.isLoading }" />
        </Button>
      </template>

      <template #extra-actions>
        <Button v-if="canManageTags" variant="outline" size="sm" @click="openCreateDialog">
          <IconPlus />
          <span class="hidden lg:inline">{{ t('tags.createTag') }}</span>
        </Button>
      </template>
    </DataTable>

    <!-- Error state -->
    <div
      v-if="tagsStore.error"
      class="flex items-center justify-between rounded-lg border border-destructive/50 bg-destructive/10 p-4"
    >
      <span class="text-destructive">{{ tagsStore.error }}</span>
      <Button variant="outline" size="sm" @click="loadTags()">Retry</Button>
    </div>
  </div>

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
</template>
