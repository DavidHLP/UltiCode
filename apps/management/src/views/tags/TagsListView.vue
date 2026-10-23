<script setup lang="ts">
import { tagPermissionRules } from '@/composables/domainPermissionMaps'
import { usePermissionMap } from '@/composables/usePermissionMap'
import { ref, computed, onMounted, h } from 'vue'
import { useI18n } from 'vue-i18n'
import type { ColumnDef } from '@tanstack/vue-table'
import { toast } from 'vue-sonner'
import {
  EllipsisVertical as IconDotsVertical,
  GitMerge as IconGitMerge,
  Hash as IconHash,
  Pencil as IconPencil,
  Plus as IconPlus,
  Tag as IconTag,
  Trash as IconTrash,
} from '@lucide/vue'

import { Button } from '@ulticode/design-system/ui/button'
import { Badge } from '@ulticode/design-system/ui/badge'
import { createSelectionColumn } from '@/components/table/selectionColumn'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@ulticode/design-system/ui/dropdown-menu'
import { useTagsStore } from '@/stores/admin/tags'
import { TagType, type Tag } from '@/api/admin/tags'

import DataTable from '@/components/table/DataTable.vue'
import DataTableToolbar, { type Filter } from '@/components/table/DataTableToolbar.vue'
import TagEditDialog from './TagEditDialog.vue'
import TagMergeDialog from './TagMergeDialog.vue'
import EntityActionDialog from '@/components/shared/EntityActionDialog.vue'
import { useRemoteTable } from '@/composables/useRemoteTable'

const { t } = useI18n()
const tagsStore = useTagsStore()
const can = { tag: usePermissionMap(tagPermissionRules) }

const selectedTag = ref<Tag | null>(null)
const selectedTagName = ref<string | null>(null)
const editDialogOpen = ref(false)
const deleteDialogOpen = ref(false)
const mergeDialogOpen = ref(false)

const bulkActionLoading = ref(false)
const selectedRows = ref<Tag[]>([])

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
const {
  query,
  searchQuery,
  tablePagination,
  loading,
  data,
  total,
  error,
  refresh: loadTags,
  setFilters,
} = useRemoteTable<Tag, { tagType: TagType }, Parameters<typeof tagsStore.fetchTags>[0]>({
  store: tagsStore,
  initialQuery: { filters: { tagType: TagType.PROBLEM } },
  toParams: ({ search, filters, page, limit }) => ({
    search,
    type: filters.tagType,
    page,
    limit,
  }),
  autoLoad: true,
})

const tagTypeFilter = computed({
  get: () => query.value.filters.tagType,
  set: (tagType: TagType) => setFilters({ tagType }),
})

const canManageTags = can.tag.manage

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
  ...createSelectionColumn<Tag>(t),
  {
    accessorKey: 'name',
    header: () => t('tags.columns.tag'),
    cell: ({ row }) => {
      const tag = row.original
      return h('div', { class: 'flex items-center gap-3' }, [
        h(
          'div',
          {
            style: {
              backgroundColor: `color-mix(in srgb, ${tag.color || 'var(--primary)'} 12%, transparent)`,
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
    <!-- Page header -->
    <div
      :class="[
        'border border-border dark:border-border bg-card',
        'transition-all duration-500',
        isLoaded ? 'opacity-100 translate-y-0' : 'opacity-0 -translate-y-2',
      ]"
    >
      <!-- Title Row -->
      <div class="px-4 lg:px-6 py-4 flex items-center justify-between">
        <h1 class="text-xl font-medium tracking-tight text-foreground">
          {{ t('tags.title') }}
        </h1>
        <Button v-if="canManageTags" variant="outline" size="sm" @click="openCreateDialog">
          <IconPlus class="h-4 w-4 mr-1.5" />
          <span class="uppercase tracking-wider">{{ t('tags.createTag') }}</span>
        </Button>
      </div>

      <!-- Stats Ticker -->
      <div
        class="px-4 lg:px-6 py-2.5 flex items-center gap-6 border-t border-border dark:border-border bg-muted"
      >
        <div class="flex items-center gap-2">
          <span class="text-sm font-medium text-muted-foreground"
            >{{ t('tags.stats.total') }}:</span
          >
          <span class="font-mono text-sm text-foreground tabular-nums">{{ total }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="text-sm font-medium text-muted-foreground"
            >{{ t('tags.stats.problem') }}:</span
          >
          <span class="font-mono text-sm text-foreground tabular-nums">{{
            stats.problemTags
          }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="text-sm font-medium text-muted-foreground"
            >{{ t('tags.stats.forum') }}:</span
          >
          <span class="font-mono text-sm text-foreground tabular-nums">{{ stats.forumTags }}</span>
        </div>
        <div class="ml-auto flex items-center gap-2 text-muted-foreground">
          <IconTag class="h-4 w-4" />
          <span class="text-xs font-mono uppercase tracking-wider">{{
            t('tags.tagManagement')
          }}</span>
        </div>
      </div>
    </div>

    <!-- Bulk Action Bar - Terminal Style -->
    <div
      v-if="selectedRows.length > 0"
      :class="[
        'mt-4 flex items-center justify-between border border-status-warning-mark bg-[color-mix(in_oklch,_var(--status-warning-mark)_8%,_transparent)] dark:bg-[color-mix(in_oklch,_var(--status-warning-mark)_15%,_transparent)] p-3',
        'animate-in fade-in slide-in-from-top-2 duration-200',
      ]"
    >
      <div class="flex items-center gap-4">
        <div class="flex items-center gap-2">
          <span class="font-mono text-sm text-foreground">
            &gt; SELECTED:{{ selectedRows.length }}
          </span>
        </div>
        <div class="h-4 w-px bg-border" />
        <div class="flex items-center gap-2">
          <Button
            v-if="canManageTags"
            variant="outline"
            size="sm"
            @click="handleBulkDelete"
            :disabled="bulkActionLoading"
          >
            <IconTrash class="h-3.5 w-3.5 mr-1.5" />
            <span class="uppercase tracking-wider">{{ t('tags.bulkDelete') }}</span>
          </Button>
        </div>
      </div>
      <Button variant="outline" size="sm" @click="selectedRows = []">
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
        class="w-full text-sm"
      >
        <template #toolbar-left>
          <DataTableToolbar
            :search-model-value="searchQuery"
            @update:search-model-value="searchQuery = $event"
            :search-placeholder="t('tags.searchPlaceholder')"
            :filters="toolbarFilters"
            @update:filter="
              (index, value) => (index === 0 ? (tagTypeFilter = value as TagType) : null)
            "
            :loading="loading"
            :on-refresh="loadTags"
          />
        </template>
      </DataTable>

      <!-- Error state - Terminal Style -->
      <div
        v-if="error"
        class="mt-4 flex items-center justify-between border border-status-error-mark bg-[color-mix(in_oklch,_var(--status-error-mark)_8%,_transparent)] p-4"
      >
        <div class="flex items-center gap-3">
          <span class="font-mono text-sm text-foreground">&gt; ERROR:</span>
          <span class="text-sm text-foreground">{{ error }}</span>
        </div>
        <Button variant="outline" size="sm" @click="loadTags()">
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
