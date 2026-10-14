<script setup lang="ts">
import { Badge } from '@ulticode/design-system/ui/badge'
import { Button } from '@ulticode/design-system/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
  DropdownMenuGroup,
} from '@ulticode/design-system/ui/dropdown-menu'
import { GripVertical as IconGripVertical, EllipsisVertical as IconDotsVertical } from '@lucide/vue'

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
      class="flex items-center gap-2 p-2 rounded-lg cursor-pointer transition-colors"
      :class="
        activeId === testCase.id ? 'bg-primary/10 border border-primary/20' : 'hover:bg-muted/50'
      "
      @click="emit('select', testCase.id)"
    >
      <IconGripVertical class="h-4 w-4 text-muted-foreground cursor-grab" />
      <span class="text-sm font-medium flex-1">
        #{{ index + 1 }}
        <Badge v-if="testCase.isSample" variant="secondary" class="ml-1">{{
          $t('testCases.sample')
        }}</Badge>
        <Badge v-if="testCase.isHidden" variant="outline" class="ml-1">{{
          $t('testCases.hidden')
        }}</Badge>
      </span>
      <DropdownMenu>
        <DropdownMenuTrigger as-child>
          <Button variant="ghost" size="icon" class="w-6">
            <IconDotsVertical class="h-4 w-4" />
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          <DropdownMenuGroup
            ><DropdownMenuItem @click.stop="emit('edit', testCase)">{{
              $t('common.edit')
            }}</DropdownMenuItem>
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
            <DropdownMenuItem @click.stop="emit('delete', testCase)">
              {{ $t('common.delete') }}
            </DropdownMenuItem></DropdownMenuGroup
          >
        </DropdownMenuContent>
      </DropdownMenu>
    </div>
  </div>
</template>
