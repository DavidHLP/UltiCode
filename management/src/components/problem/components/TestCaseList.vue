<script setup lang="ts">
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { IconGripVertical, IconDotsVertical } from '@tabler/icons-vue'
import type { TestCase } from '@/api/admin/test-cases'

defineProps<{
  testCases: TestCase[]
  activeId: string | null
}>()

const emit = defineEmits<{
  select: [id: string]
  edit: [testCase: TestCase]
  toggleSample: [testCase: TestCase]
  toggleHidden: [testCase: TestCase]
  delete: [testCase: TestCase]
}>()
</script>

<template>
  <div class="col-span-4 space-y-1 max-h-[500px] overflow-y-auto">
    <div
      v-for="(testCase, index) in testCases"
      :key="testCase.id"
      class="flex items-center gap-2 p-2 rounded-none cursor-pointer transition-colors"
      :class="
        activeId === testCase.id ? 'bg-primary/10 border border-primary/20' : 'hover:bg-muted/50'
      "
      @click="emit('select', testCase.id)"
    >
      <IconGripVertical class="h-4 w-4 text-muted-foreground cursor-grab" />
      <span class="text-sm font-medium flex-1">
        #{{ index + 1 }}
        <Badge v-if="testCase.is_sample" variant="secondary" class="ml-1 text-2xs">
          Sample
        </Badge>
        <Badge v-if="testCase.is_hidden" variant="outline" class="ml-1 text-2xs"> Hidden </Badge>
      </span>
      <DropdownMenu>
        <DropdownMenuTrigger as-child>
          <Button variant="ghost" size="icon" class="h-6 w-6">
            <IconDotsVertical class="h-4 w-4" />
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          <DropdownMenuItem @click.stop="emit('edit', testCase)">Edit</DropdownMenuItem>
          <DropdownMenuItem @click.stop="emit('toggleSample', testCase)">
            {{ testCase.is_sample ? 'Mark as Hidden' : 'Mark as Sample' }}
          </DropdownMenuItem>
          <DropdownMenuItem @click.stop="emit('toggleHidden', testCase)">
            {{ testCase.is_hidden ? 'Make Visible' : 'Make Hidden' }}
          </DropdownMenuItem>
          <DropdownMenuItem
            class="text-destructive focus:text-destructive"
            @click.stop="emit('delete', testCase)"
          >
            Delete
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </div>
  </div>
</template>
