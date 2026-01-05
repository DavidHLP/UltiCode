<script setup lang="ts" generic="TData extends { id: string | number }, TValue">
import type {
  ColumnDef,
  ColumnFiltersState,
  PaginationState,
  SortingState,
  VisibilityState,
} from '@tanstack/vue-table'
import { RestrictToVerticalAxis } from '@dnd-kit/abstract/modifiers'
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

import DraggableRow from './DraggableRow.vue'

const props = defineProps<{
  columns: ColumnDef<TData, TValue>[]
  data: TData[]
  pagination?: PaginationState
  emptyTitle?: string
  emptyDescription?: string
}>()

const emit = defineEmits<{
  'update:pagination': [value: PaginationState]
}>()

const sorting = ref<SortingState>([])
const columnFilters = ref<ColumnFiltersState>([])
const columnVisibility = ref<VisibilityState>({})
const rowSelection = ref({})

const table = useVueTable({
  get data() {
    return props.data
  },
  get columns() {
    return props.columns
  },
  getCoreRowModel: getCoreRowModel(),
  getPaginationRowModel: getPaginationRowModel(),
  getSortedRowModel: getSortedRowModel(),
  getFilteredRowModel: getFilteredRowModel(),
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

// Watch for external pagination changes
watch(
  () => props.pagination,
  (newPagination) => {
    if (newPagination) {
      table.setPagination(newPagination)
    }
  },
  { deep: true },
)
</script>

<template>
  <div class="flex flex-col gap-4">
    <div class="flex items-center justify-between px-1">
      <div class="flex items-center gap-2">
        <slot name="toolbar-left" :table="table" />
      </div>

      <div class="flex items-center gap-2">
        <slot name="toolbar-actions" :table="table" />

        <DropdownMenu>
          <DropdownMenuTrigger as-child>
            <Button variant="outline" size="sm">
              <IconLayoutColumns />
              <span class="hidden lg:inline">Customize Columns</span>
              <span class="lg:hidden">Columns</span>
              <IconChevronDown />
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
                class="capitalize"
                :model-value="column.getIsVisible()"
                @update:model-value="
                  (value) => {
                    column.toggleVisibility(!!value)
                  }
                "
              >
                {{ column.id }}
              </DropdownMenuCheckboxItem>
            </template>
          </DropdownMenuContent>
        </DropdownMenu>
        <slot name="extra-actions" />
      </div>
    </div>

    <div class="overflow-hidden rounded-lg border">
      <DragDropProvider :modifiers="[RestrictToVerticalAxis]">
        <Table>
          <TableHeader class="bg-muted sticky top-0 z-10">
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
            <template v-if="table.getRowModel().rows.length">
              <DraggableRow
                v-for="row in table.getRowModel().rows"
                :key="row.id"
                :row="row"
                :index="row.index"
              />
            </template>
            <TableRow v-else>
              <TableCell :col-span="columns.length" class="h-96 text-center">
                <slot name="empty">
                  <Empty class="border-none">
                    <EmptyMedia variant="icon">
                      <IconSearchOff />
                    </EmptyMedia>
                    <EmptyContent>
                      <EmptyTitle>{{ emptyTitle || 'No results found' }}</EmptyTitle>
                      <EmptyDescription>
                        {{
                          emptyDescription ||
                          "We couldn't find what you're looking for. Try adjusting your filters or search query."
                        }}
                      </EmptyDescription>
                    </EmptyContent>
                  </Empty>
                </slot>
              </TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </DragDropProvider>
    </div>
    <div class="flex items-center justify-between px-2">
      <div class="text-muted-foreground hidden flex-1 text-sm lg:flex">
        {{ table.getFilteredSelectedRowModel().rows.length }} of
        {{ table.getFilteredRowModel().rows.length }} row(s) selected.
      </div>
      <div class="flex w-full items-center gap-8 lg:w-fit">
        <div class="hidden items-center gap-2 lg:flex">
          <Label for="rows-per-page" class="text-sm font-medium"> Rows per page </Label>
          <Select
            :model-value="`${table.getState().pagination.pageSize}`"
            @update:model-value="
              (value) => {
                table.setPageSize(Number(value))
              }
            "
          >
            <SelectTrigger id="rows-per-page" size="sm" class="w-20">
              <SelectValue :placeholder="`${table.getState().pagination.pageSize}`" />
            </SelectTrigger>
            <SelectContent side="top">
              <SelectItem
                v-for="pageSize in [10, 20, 30, 40, 50]"
                :key="pageSize"
                :value="`${pageSize}`"
              >
                {{ pageSize }}
              </SelectItem>
            </SelectContent>
          </Select>
        </div>
        <div class="flex w-fit items-center justify-center text-sm font-medium">
          Page {{ table.getState().pagination.pageIndex + 1 }} of
          {{ table.getPageCount() }}
        </div>
        <div class="ml-auto flex items-center gap-2 lg:ml-0">
          <Button
            variant="outline"
            class="hidden h-8 w-8 p-0 lg:flex"
            :disabled="!table.getCanPreviousPage()"
            @click="table.setPageIndex(0)"
          >
            <span class="sr-only">Go to first page</span>
            <IconChevronsLeft />
          </Button>
          <Button
            variant="outline"
            class="size-8"
            size="icon"
            :disabled="!table.getCanPreviousPage()"
            @click="table.previousPage()"
          >
            <span class="sr-only">Go to previous page</span>
            <IconChevronLeft />
          </Button>
          <Button
            variant="outline"
            class="size-8"
            size="icon"
            :disabled="!table.getCanNextPage()"
            @click="table.nextPage()"
          >
            <span class="sr-only">Go to next page</span>
            <IconChevronRight />
          </Button>
          <Button
            variant="outline"
            class="hidden size-8 lg:flex"
            size="icon"
            :disabled="!table.getCanNextPage()"
            @click="table.setPageIndex(table.getPageCount() - 1)"
          >
            <span class="sr-only">Go to last page</span>
            <IconChevronsRight />
          </Button>
        </div>
      </div>
    </div>
  </div>
</template>
