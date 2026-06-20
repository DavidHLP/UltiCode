<script setup lang="ts">
import { ref, computed, onMounted, h } from 'vue'
import { useI18n } from 'vue-i18n'
import type { ColumnDef } from '@tanstack/vue-table'
import { toast } from 'vue-sonner'
import {
  IconDotsVertical,
  IconGitMerge,
  IconHash,
  IconPencil,
  IconPlus,
  IconTag,
  IconTrash,
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
import { useTagsStore } from '@/stores/admin/tags'
import { useAuthStore } from '@/stores/auth'
import { TagType, type Tag } from '@/api/admin/tags'

import DataTable from '@/components/table/DataTable.vue'
import DataTableToolbar, { type Filter } from '@/components/table/DataTableToolbar.vue'
import TagEditDialog from './TagEditDialog.vue'
import TagMergeDialog from './TagMergeDialog.vue'
import EntityActionDialog from '@/components/shared/EntityActionDialog.vue'
import { useDataTable } from '@/composables/useDataTable'

const { t } = useI18n()
const tagsStore = useTagsStore()
const authStore = useAuthStore()

const tagTypeFilter = ref<TagType>(TagType.PROBLEM)

const selectedTag = ref<Tag | null>(null)
const selectedTagName = ref<string | null>(null)
const editDialogOpen = ref(false)
const deleteDialogOpen = ref(false)
const mergeDialogOpen = ref(false)

const bulkActionLoading = ref(false)

const isLoaded = ref(false)

// Stats
const stats = computed(() => ({
  problemTags: tagsStore.tags.filter((t) => t.type === TagType.PROBLEM).length,
  forumTags: tagsStore.tags.filter((t) => t.type === TagType.FORUM).length,
}))

const toolbarFilters = computed<Filter[]>(() => [
  {
    modelValue: tagTypeFilter.value,
    placeholder: t('tags.tagType'),
    options: [
      { value: TagType.PROBLEM, label: t('tags.problemTags') },
      { value: TagType.FORUM, label: t('tags.forumTags') },
    ],
  },
])

const canManageTags = computed(
  () =>
    authStore.hasPermission('MANAGE_USERS', 'SYSTEM') ||
    authStore.hasPermission('UPDATE', 'PROBLEM'),
)

const {
  searchQuery,
  tablePagination,
  selectedRows,
  loading,
  data,
  total,
  error,
  loadEntities: loadTags,
} = useDataTable<Tag, { tagType: TagType }, Parameters<typeof tagsStore.fetchTags>[0]>({
  store: {
    data: computed(() => tagsStore.tags),
    total: computed(() => tagsStore.total),
    isLoading: computed(() => tagsStore.isLoading),
    error: computed(() => tagsStore.error),
    fetch: (params) => tagsStore.fetchTags(params),
  },
  filters: () => ({
    tagType: tagTypeFilter.value,
  }),
  transformParams: ({ search, filters, page, limit }) => ({
    search,
    type: filters.tagType,
    page,
    limit,
  }),
  autoLoad: true,
})

onMounted(() => {
  setTimeout(() => {
    isLoaded.value = true
  }, 100)
})

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
  selectedTagName.value = tag.name
  deleteDialogOpen.value = true
}

function openMergeDialog(tag: Tag) {
  selectedTag.value = tag
  mergeDialogOpen.value = true
}

async function handleDeleteTag(id: string | number) {
  await tagsStore.deleteTag(String(id), tagTypeFilter.value)
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
        'aria-label': t('table.selectAll'),
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
            class: 'h-9 w-9 rounded-none flex items-center justify-center',
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
          {{ t('tags.title') }}
        </h1>
        <Button
          v-if="canManageTags"
          variant="terminal"
          size="sm"
          class="font-data text-xs border-[var(--silver-300)] hover:border-[var(--accent-electric)] hover:text-[var(--accent-electric)] transition-colors"
          @click="openCreateDialog"
        >
          <IconPlus class="h-4 w-4 mr-1.5" />
          <span class="uppercase tracking-wider">{{ t('tags.createTag') }}</span>
        </Button>
      </div>

      <!-- Stats Ticker -->
      <div
        class="px-4 lg:px-6 py-2.5 flex items-center gap-6 border-t border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--surface-sunken)]"
      >
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]">{{ t('tags.stats.total') }}:</span>
          <span class="font-data text-sm text-[var(--terminal-cyan)] tabular-nums">{{
            total
          }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]">{{ t('tags.stats.problem') }}:</span>
          <span class="font-data text-sm text-[var(--terminal-green)] tabular-nums">{{
            stats.problemTags
          }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]">{{ t('tags.stats.forum') }}:</span>
          <span class="font-data text-sm text-[var(--terminal-amber)] tabular-nums">{{
            stats.forumTags
          }}</span>
        </div>
        <div class="ml-auto flex items-center gap-2 text-[var(--silver-400)]">
          <IconTag class="h-4 w-4" />
          <span class="text-xs font-data uppercase tracking-wider">{{ t('tags.tagManagement') }}</span>
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
            v-if="canManageTags"
            variant="terminal"
            size="sm"
            class="h-8 font-data text-xs border-[var(--silver-300)] hover:border-[var(--terminal-red)] hover:text-[var(--terminal-red)]"
            @click="handleBulkDelete"
            :disabled="bulkActionLoading"
          >
            <IconTrash class="h-3.5 w-3.5 mr-1.5" />
            <span class="uppercase tracking-wider">{{ t('tags.bulkDelete') }}</span>
          </Button>
        </div>
      </div>
      <Button
        variant="terminal"
        size="sm"
        class="h-8 font-data text-xs text-[var(--silver-500)] hover:text-[var(--foreground)]"
        @click="selectedRows = []"
      >
        [ESC] {{ t('tags.clearSelection') }}
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
            :search-placeholder="t('tags.searchPlaceholder')"
            :filters="toolbarFilters"
            @update:filter="(index, value) => index === 0 ? (tagTypeFilter = value as TagType) : null"
            :loading="loading"
            :on-refresh="loadTags"
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
          @click="loadTags()"
        >
          {{ t('common.retry') }}
        </Button>
      </div>
    </div>
  </div>

  <TagEditDialog
    v-model:open="editDialogOpen"
    :tag-to-edit="selectedTag"
    :tag-type="tagTypeFilter"
    @success="loadTags"
  />

  <EntityActionDialog
    v-model:open="deleteDialogOpen"
    :entity-id="selectedTag?.id || null"
    :entity-title="selectedTagName"
    action="delete"
    :title="t('tags.delete.title')"
    :description="t('tags.delete.description')"
    :confirm-label="t('tags.delete.confirm')"
    :cancel-label="t('common.cancel')"
    :success-label="t('tags.toast.deletedSuccessfully')"
    :error-label="t('tags.toast.failedToDelete')"
    :on-action="handleDeleteTag"
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
