<script lang="ts">
import { z } from 'zod'
import { h } from 'vue'

export const schema = z.object({
  id: z.number(),
  header: z.string(),
  type: z.string(),
  status: z.string(),
  target: z.string(),
  limit: z.string(),
  reviewer: z.string(),
})
</script>

<script setup lang="ts">
import type { ColumnDef } from '@tanstack/vue-table'
import {
  IconCircleCheckFilled,
  IconDotsVertical,
  IconLoader,
  IconPlus,
} from '@tabler/icons-vue'
import { Badge } from '@/components/ui/badge'

import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
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

import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'

import DataTable from '../components/table/DataTable.vue'
import DragHandle from '../components/table/DragHandle.vue'

defineProps<{
  data: TableData[]
}>()

interface TableData {
  id: number
  header: string
  type: string
  status: string
  target: string
  limit: string
  reviewer: string
}

const columns: ColumnDef<TableData>[] = [
  {
    id: 'drag',
    header: () => null,
    cell: ({ row }) => h(DragHandle),
  },
  {
    id: 'select',
    header: ({ table }) =>
      h(Checkbox, {
        modelValue:
          table.getIsAllPageRowsSelected() ||
          (table.getIsSomePageRowsSelected() && 'indeterminate'),
        'onUpdate:modelValue': (value) => table.toggleAllPageRowsSelected(!!value),
        'aria-label': 'Select all',
      }),
    cell: ({ row }) =>
      h(Checkbox, {
        modelValue: row.getIsSelected(),
        'onUpdate:modelValue': (value) => row.toggleSelected(!!value),
        'aria-label': 'Select row',
      }),
    enableSorting: false,
    enableHiding: false,
  },
  {
    accessorKey: 'header',
    header: 'Header',
    cell: ({ row }) => h('div', String(row.getValue('header'))),
    enableHiding: false,
  },
  {
    accessorKey: 'type',
    header: 'Section Type',
    cell: ({ row }) =>
      h(
        Badge,
        {
          variant: 'outline',
        },
        () => String(row.getValue('type')),
      ),
  },
  {
    accessorKey: 'status',
    header: 'Status',
    cell: ({ row }) => {
      const status = row.getValue('status') as string
      return h('div', { class: 'flex items-center gap-2' }, [
        status === 'Done'
          ? h(IconCircleCheckFilled, { class: 'h-4 w-4 text-emerald-500' })
          : h(IconLoader, { class: 'h-4 w-4 animate-spin text-muted-foreground' }),
        h('span', {}, status),
      ])
    },
  },
  {
    accessorKey: 'target',
    header: () => h('div', { class: 'flex items-center gap-1' }, ['Target']),
    cell: ({ row }) =>
      h(
        Button,
        {
          variant: 'ghost',
          size: 'sm',
          class: 'h-auto p-1 text-xs font-mono',
        },
        () => [h('span', { class: 'ml-1 font-semibold' }, String(row.getValue('target')))],
      ),
  },
  {
    accessorKey: 'limit',
    header: () => h('div', { class: 'flex items-center gap-1' }, ['Limit']),
    cell: ({ row }) =>
      h(
        Button,
        {
          variant: 'ghost',
          size: 'sm',
          class: 'h-auto p-1 text-xs font-mono',
        },
        () => [h('span', { class: 'ml-1 font-semibold' }, String(row.getValue('limit')))],
      ),
  },
  {
    accessorKey: 'reviewer',
    header: 'Reviewer',
    cell: ({ row }) => {
      const reviewer = row.getValue('reviewer') as string
      const isAssigned = reviewer !== 'Assign reviewer'

      if (isAssigned) {
        return h('span', {}, reviewer)
      }

      return h(
        Select,
        {},
        {
          default: () => [
            h(
              SelectTrigger,
              { class: 'w-full' },
              {
                default: () => h(SelectValue, { placeholder: 'Assign reviewer' }),
              },
            ),
            h(
              SelectContent,
              {},
              {
                default: () => [
                  h(SelectItem, { value: 'eddie' }, () => 'Eddie Lake'),
                  h(SelectItem, { value: 'jamik' }, () => 'Jamik Tashpulatov'),
                ],
              },
            ),
          ],
        },
      )
    },
  },
  {
    id: 'actions',
    cell: () =>
      h(
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
                    {
                      variant: 'ghost',
                      class: 'h-8 w-8 p-0',
                    },
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
                  h(DropdownMenuItem, {}, () => 'Edit'),
                  h(DropdownMenuItem, {}, () => 'Make a copy'),
                  h(DropdownMenuItem, {}, () => 'Favorite'),
                  h(DropdownMenuSeparator, {}),
                  h(DropdownMenuItem, {}, () => 'Delete'),
                ],
              },
            ),
          ],
        },
      ),
  },
]
</script>

<template>
  <Tabs default-value="outline" class="w-full flex-col justify-start gap-6">
    <div class="flex items-center justify-between px-4 lg:px-6">
      <Label for="view-selector" class="sr-only"> View </Label>
      <Select default-value="outline">
        <SelectTrigger id="view-selector" class="flex w-fit @4xl/main:hidden" size="sm">
          <SelectValue placeholder="Select a view" />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="outline"> Outline </SelectItem>
          <SelectItem value="past-performance"> Past Performance </SelectItem>
          <SelectItem value="key-personnel"> Key Personnel </SelectItem>
          <SelectItem value="focus-documents"> Focus Documents </SelectItem>
        </SelectContent>
      </Select>
      <TabsList
        class="**:data-[slot=badge]:bg-muted-foreground/30 hidden **:data-[slot=badge]:size-5 **:data-[slot=badge]:rounded-full **:data-[slot=badge]:px-1 @4xl/main:flex"
      >
        <TabsTrigger value="outline"> Outline </TabsTrigger>
        <TabsTrigger value="past-performance">
          Past Performance <Badge variant="secondary"> 3 </Badge>
        </TabsTrigger>
        <TabsTrigger value="key-personnel">
          Key Personnel <Badge variant="secondary"> 2 </Badge>
        </TabsTrigger>
        <TabsTrigger value="focus-documents"> Focus Documents </TabsTrigger>
      </TabsList>
    </div>
    <TabsContent value="outline" class="relative flex flex-col gap-4 overflow-auto px-4 lg:px-6">
      <DataTable :columns="columns" :data="data">
        <template #extra-actions>
          <Button variant="outline" size="sm">
            <IconPlus />
            <span class="hidden lg:inline">Add Section</span>
          </Button>
        </template>
      </DataTable>
    </TabsContent>
    <TabsContent value="past-performance" class="flex flex-col px-4 lg:px-6">
      <div class="aspect-video w-full flex-1 rounded-lg border border-dashed" />
    </TabsContent>
    <TabsContent value="key-personnel" class="flex flex-col px-4 lg:px-6">
      <div class="aspect-video w-full flex-1 rounded-lg border border-dashed" />
    </TabsContent>
    <TabsContent value="focus-documents" class="flex flex-col px-4 lg:px-6">
      <div class="aspect-video w-full flex-1 rounded-lg border border-dashed" />
    </TabsContent>
  </Tabs>
</template>