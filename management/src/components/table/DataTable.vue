<script setup lang="ts" generic="TData extends { id: string | number }, TValue">
import type {
  ColumnDef,
  ColumnFiltersState,
  PaginationState,
  SortingState,
  VisibilityState,
} from '@tanstack/vue-table'
import { RestrictToVerticalAxis } from '@dnd-kit/abstract/modifiers'
import { KeyboardSensor, PointerSensor } from '@dnd-kit/dom'
import {
  IconChevronDown,
  IconChevronLeft,
  IconChevronRight,
  IconChevronsLeft,
  IconChevronsRight,
  IconLayoutColumns,
  IconSearchOff,
} from '@tabler/icons-vue'
import {
  FlexRender,
  getCoreRowModel,
  getFilteredRowModel,
  getPaginationRowModel,
  getSortedRowModel,
  useVueTable,
} from '@tanstack/vue-table'
import { DragDropProvider } from 'dnd-kit-vue'
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuCheckboxItem,
  DropdownMenuContent,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import {
  Empty,
  EmptyContent,
  EmptyDescription,
  EmptyMedia,
  EmptyTitle,
} from '@/components/ui/empty'
import { Skeleton } from '@/components/ui/skeleton'
import DraggableRow from './DraggableRow.vue'

// Constants for page size options
const PAGE_SIZE_OPTIONS = [10, 20, 30, 40, 50] as const

const props = defineProps<{
  columns: ColumnDef<TData, TValue>[]
  data: TData[]
  pagination?: PaginationState
  rowCount?: number
  emptyTitle?: string
  emptyDescription?: string
  loading?: boolean
  selectedRows?: TData[]
}>()

const emit = defineEmits<{
  'update:pagination': [value: PaginationState]
  'update:selectedRows': [value: TData[]]
}>()

const { t } = useI18n()

function resolveColumnName(columnId: string): string {
  // C8 normalisation: column ids may arrive from the backend as snake_case
  // (joined_at, last_login_at, …) but the i18n keys are camelCase only.
  // Normalise at the seam so we ship one key per column, not two.
  const normalised = toCamelCase(columnId)
  const name = t(`table.columnNames.${normalised}`, normalised)
  if (import.meta.env.DEV && name === normalised) {
    console.error(
      `[i18n] Missing translation key: table.columnNames.${normalised}. ` +
        `Add it to management/src/i18n/locales/*/modules/table.ts`,
    )
  }
  return name
}

/**
 * Convert snake_case → camelCase. Column ids that are already camelCase
 * (or single-word) pass through unchanged. Booleans like {@code is_active}
 * become {@code isActive}; underscored compound names like {@code last_login_at}
 * become {@code lastLoginAt}.
 *
 * @param id raw column id from the column definition
 * @return camelCase key matching the {@code table.columnNames.*} namespace
 */
function toCamelCase(id: string): string {
  return id.replace(/_([a-z0-9])/g, (_, ch: string) => ch.toUpperCase())
}

const sorting = ref<SortingState>([])
const columnFilters = ref<ColumnFiltersState>([])
const columnVisibility = ref<VisibilityState>({})
const rowSelection = ref({})

const sensors = [
  PointerSensor.configure({
    activationConstraints: {
      distance: { value: 10 },
    },
  }),
  KeyboardSensor,
]

const table = useVueTable({
  get data() {
    return props.data || []
  },
  get columns() {
    return props.columns
  },
  get rowCount() {
    return props.rowCount
  },
  getCoreRowModel: getCoreRowModel(),
  getSortedRowModel: getSortedRowModel(),
  getFilteredRowModel: getFilteredRowModel(),
  getPaginationRowModel: getPaginationRowModel(),
  manualPagination: true,
  onSortingChange: (updaterOrValue) => {
    sorting.value =
      typeof updaterOrValue === 'function' ? updaterOrValue(sorting.value) : updaterOrValue
  },
  onColumnFiltersChange: (updaterOrValue) => {
    columnFilters.value =
      typeof updaterOrValue === 'function' ? updaterOrValue(columnFilters.value) : updaterOrValue
  },
  onColumnVisibilityChange: (updaterOrValue) => {
    columnVisibility.value =
      typeof updaterOrValue === 'function' ? updaterOrValue(columnVisibility.value) : updaterOrValue
  },
  onRowSelectionChange: (updaterOrValue) => {
    rowSelection.value =
      typeof updaterOrValue === 'function' ? updaterOrValue(rowSelection.value) : updaterOrValue

    // Emit selected data objects
    const selectedData = table.getSelectedRowModel().flatRows.map((row) => row.original as TData)
    emit('update:selectedRows', selectedData)
  },
  onPaginationChange: (updaterOrValue) => {
    const pagination =
      typeof updaterOrValue === 'function'
        ? updaterOrValue(table.getState().pagination)
        : updaterOrValue
    emit('update:pagination', pagination)
  },
  state: {
    get sorting() {
      return sorting.value
    },
    get columnFilters() {
      return columnFilters.value
    },
    get columnVisibility() {
      return columnVisibility.value
    },
    get rowSelection() {
      return rowSelection.value
    },
    get pagination() {
      return props.pagination
    },
  },
})

// Watch for external clearing of selected rows
watch(
  () => props.selectedRows,
  (newVal) => {
    if (!newVal || newVal.length === 0) {
      if (Object.keys(rowSelection.value).length > 0) {
        table.resetRowSelection()
      }
    }
  },
)
</script>

<template>
  <!-- Unified Card Container (always visible) -->
  <div
    class="w-full border border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--card)] flex flex-col rounded-none"
  >
    <!-- Toolbar Row -->
    <div
      class="px-4 lg:px-6 py-2.5 flex flex-col gap-3 md:flex-row md:items-center md:justify-between md:gap-4 border-b border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--surface-sunken)]"
    >
      <div class="flex flex-wrap items-center gap-2">
        <slot name="toolbar-left" :table="table" />
      </div>

      <div class="flex flex-wrap items-center gap-2">
        <slot name="toolbar-actions" :table="table" />

        <DropdownMenu>
          <DropdownMenuTrigger as-child>
            <Button
              variant="terminal"
              size="sm"
              class="h-8 font-data text-xs border-[var(--silver-300)]"
            >
              <IconLayoutColumns class="h-3.5 w-3.5" />
              <span class="hidden lg:inline uppercase tracking-wider">{{
                t('table.customizeColumns')
              }}</span>
              <span class="lg:hidden uppercase tracking-wider">{{ t('table.columns') }}</span>
              <IconChevronDown class="h-3.5 w-3.5" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" class="w-56">
            <template
              v-for="column in table
                .getAllColumns()
                .filter(
                  (column) => typeof column.accessorFn !== 'undefined' && column.getCanHide(),
                )"
              :key="column.id"
            >
              <DropdownMenuCheckboxItem
                :model-value="column.getIsVisible()"
                @update:model-value="
                  (value) => {
                    column.toggleVisibility(!!value)
                  }
                "
              >
                {{ resolveColumnName(column.id) }}
              </DropdownMenuCheckboxItem>
            </template>
          </DropdownMenuContent>
        </DropdownMenu>
        <slot name="extra-actions" />
      </div>
    </div>

    <!-- Table Body (Data/Loading State) -->
    <div
      v-if="loading || table.getRowModel().rows.length"
      class="w-full overflow-auto min-h-[200px]"
      style="max-height: calc(100vh - 380px);"
    >
      <DragDropProvider :sensors="sensors" :modifiers="[RestrictToVerticalAxis]">
        <Table :overflow="false">
          <colgroup>
            <col
              v-for="column in table.getVisibleFlatColumns()"
              :key="column.id"
              :style="{
                width:
                  column.columnDef.size !== undefined ? `${column.columnDef.size}px` : undefined,
                minWidth:
                  column.columnDef.minSize !== undefined
                    ? `${column.columnDef.minSize}px`
                    : undefined,
                maxWidth:
                  column.columnDef.maxSize !== undefined
                    ? `${column.columnDef.maxSize}px`
                    : undefined,
              }"
            />
          </colgroup>
          <TableHeader class="bg-[var(--surface-sunken)] sticky top-0 z-10">
            <TableRow v-for="headerGroup in table.getHeaderGroups()" :key="headerGroup.id">
              <TableHead
                v-for="header in headerGroup.headers"
                :key="header.id"
                :col-span="header.colSpan"
              >
                <FlexRender
                  v-if="!header.isPlaceholder"
                  :render="header.column.columnDef.header"
                  :props="header.getContext()"
                />
              </TableHead>
            </TableRow>
          </TableHeader>
          <TableBody class="**:data-[slot=table-cell]:first:w-8">
            <template v-if="loading">
              <TableRow v-for="i in 5" :key="i">
                <TableCell v-for="cell in table.getVisibleFlatColumns().length" :key="cell">
                  <Skeleton class="h-4 w-full" />
                </TableCell>
              </TableRow>
            </template>
            <template v-else>
              <DraggableRow
                v-for="row in table.getRowModel().rows"
                :key="row.id"
                :row="row"
                :index="row.index"
              />
            </template>
          </TableBody>
        </Table>
      </DragDropProvider>
    </div>

    <!-- Empty State -->
    <div
      v-else
      class="flex h-96 items-center justify-center rounded-none bg-[var(--card)]"
    >
      <slot name="empty">
        <Empty class="border-none">
          <EmptyMedia variant="icon">
            <IconSearchOff />
          </EmptyMedia>
          <EmptyContent>
            <EmptyTitle>{{ emptyTitle || t('table.emptyTitle') }}</EmptyTitle>
            <EmptyDescription>
              {{ emptyDescription || t('table.emptyDescription') }}
            </EmptyDescription>
          </EmptyContent>
        </Empty>
      </slot>
    </div>

    <!-- Pagination Footer -->
    <div
      v-if="loading || table.getRowModel().rows.length"
      class="flex items-center justify-between px-4 lg:px-6 py-2.5 border-t border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--surface-sunken)]"
    >
      <div
        class="hidden flex-1 text-sm lg:flex items-center gap-2 font-data text-xs text-[var(--silver-500)]"
      >
        <span class="text-[var(--terminal-cyan)] tabular-nums">{{
          table.getFilteredSelectedRowModel().rows.length
        }}</span>
        <span>{{ t('table.of') }}</span>
        <span class="tabular-nums">{{ table.getFilteredRowModel().rows.length }}</span>
        <span>{{ t('table.rowsSelected') }}</span>
      </div>
      <div class="flex w-full items-center gap-6 lg:w-fit">
        <div class="hidden items-center gap-2 lg:flex">
          <Label
            for="rows-per-page"
            class="text-xs font-data text-[var(--silver-500)] uppercase tracking-wider"
            >{{ t('table.rowsPerPage') }}</Label
          >
          <Select
            :disabled="loading"
            :model-value="`${table.getState().pagination.pageSize}`"
            @update:model-value="
              (value) => {
                table.setPageSize(Number(value))
              }
            "
          >
            <SelectTrigger
              id="rows-per-page"
              size="sm"
              class="w-20 h-7 font-data text-xs border-[var(--silver-300)]"
            >
              <SelectValue :placeholder="`${table.getState().pagination.pageSize}`" />
            </SelectTrigger>
            <SelectContent side="top">
              <SelectItem
                v-for="pageSize in PAGE_SIZE_OPTIONS"
                :key="pageSize"
                :value="`${pageSize}`"
              >
                {{ pageSize }}
              </SelectItem>
            </SelectContent>
          </Select>
        </div>
        <div
          class="flex w-fit items-center justify-center text-xs font-data text-[var(--silver-500)]"
        >
          <span>{{ t('table.page') }}</span>
          <span class="mx-1.5 text-[var(--terminal-cyan)] tabular-nums">{{
            table.getState().pagination.pageIndex + 1
          }}</span>
          <span>{{ t('table.of') }}</span>
          <span class="ml-1.5 tabular-nums">{{ table.getPageCount() }}</span>
        </div>
        <div class="ml-auto flex items-center gap-1.5 lg:ml-0">
          <Button
            variant="terminal"
            class="hidden h-7 w-7 p-0 lg:flex border-[var(--silver-300)]"
            :disabled="loading || !table.getCanPreviousPage()"
            @click="table.setPageIndex(0)"
          >
            <span class="sr-only">{{ t('table.goToFirstPage') }}</span>
            <IconChevronsLeft class="h-3.5 w-3.5" />
          </Button>
          <Button
            variant="terminal"
            class="h-7 w-7 p-0 border-[var(--silver-300)]"
            size="icon"
            :disabled="loading || !table.getCanPreviousPage()"
            @click="table.previousPage()"
          >
            <span class="sr-only">{{ t('table.goToPreviousPage') }}</span>
            <IconChevronLeft class="h-3.5 w-3.5" />
          </Button>
          <Button
            variant="terminal"
            class="h-7 w-7 p-0 border-[var(--silver-300)]"
            size="icon"
            :disabled="loading || !table.getCanNextPage()"
            @click="table.nextPage()"
          >
            <span class="sr-only">{{ t('table.goToNextPage') }}</span>
            <IconChevronRight class="h-3.5 w-3.5" />
          </Button>
          <Button
            variant="terminal"
            class="hidden h-7 w-7 p-0 lg:flex border-[var(--silver-300)]"
            size="icon"
            :disabled="loading || !table.getCanNextPage()"
            @click="table.setPageIndex(table.getPageCount() - 1)"
          >
            <span class="sr-only">{{ t('table.goToLastPage') }}</span>
            <IconChevronsRight />
          </Button>
        </div>
      </div>
    </div>
  </div>
</template>
