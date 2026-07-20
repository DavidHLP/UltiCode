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
  'set-scope': [testCase: TestCase, scope: 'SAMPLE' | 'HIDDEN']
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
        <Badge v-if="testCase.isSample" variant="secondary" class="ml-1 text-2xs">{{ $t('testCases.sample') }}</Badge>
        <Badge v-if="testCase.isHidden" variant="outline" class="ml-1 text-2xs">{{ $t('testCases.hidden') }}</Badge>
      </span>
      <DropdownMenu>
        <DropdownMenuTrigger as-child>
          <Button variant="ghost" size="icon" class="h-6 w-6">
            <IconDotsVertical class="h-4 w-4" />
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          <DropdownMenuItem @click.stop="emit('edit', testCase)">{{ $t('common.edit') }}</DropdownMenuItem>
          <DropdownMenuItem
            v-if="!testCase.isSample"
            @click.stop="emit('set-scope', testCase, 'SAMPLE')"
          >
            {{ $t('testCases.markAsSample') }}
          </DropdownMenuItem>
          <DropdownMenuItem
            v-if="!testCase.isHidden"
            @click.stop="emit('set-scope', testCase, 'HIDDEN')"
          >
            {{ $t('testCases.markAsHidden') }}
          </DropdownMenuItem>
          <DropdownMenuItem
            class="text-destructive focus:text-destructive"
            @click.stop="emit('delete', testCase)"
          >
            {{ $t('common.delete') }}
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </div>
  </div>
</template>
