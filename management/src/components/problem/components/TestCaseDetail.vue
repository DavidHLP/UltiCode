<script setup lang="ts">
import { Textarea } from '@/components/ui/textarea'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Switch } from '@/components/ui/switch'
import { Button } from '@/components/ui/button'
import type { TestCase } from '@/api/admin/test-cases'

defineProps<{
  testCase: TestCase
}>()

const emit = defineEmits<{
  edit: [testCase: TestCase]
  'toggle-sample': [testCase: TestCase]
  'toggle-hidden': [testCase: TestCase]
}>()
</script>

<template>
  <div class="col-span-8 space-y-4">
    <div class="p-4 rounded-none border bg-card">
      <div class="flex items-center justify-between mb-4">
        <h4 class="font-medium">Test Case Details</h4>
        <div class="flex items-center gap-4">
          <div class="flex items-center gap-2">
            <Label class="text-xs">Sample</Label>
            <Switch
              :checked="testCase.isSample"
              @update:checked="emit('toggle-sample', testCase)"
            />
          </div>
          <div class="flex items-center gap-2">
            <Label class="text-xs">Hidden</Label>
            <Switch
              :checked="testCase.isHidden"
              @update:checked="emit('toggle-hidden', testCase)"
            />
          </div>
        </div>
      </div>

      <div class="space-y-4">
        <div>
          <Label class="text-sm text-muted-foreground mb-1 block">Input</Label>
          <Textarea
            :model-value="testCase.inputText"
            readonly
            class="font-mono text-sm bg-muted min-h-[100px]"
          />
        </div>
        <div>
          <Label class="text-sm text-muted-foreground mb-1 block">Output</Label>
          <Textarea
            :model-value="testCase.outputText"
            readonly
            class="font-mono text-sm bg-muted min-h-[100px]"
          />
        </div>
        <div v-if="testCase.explanation">
          <Label class="text-sm text-muted-foreground mb-1 block">Explanation</Label>
          <Input :model-value="testCase.explanation" readonly />
        </div>
      </div>

      <div class="flex justify-end mt-4">
        <Button size="sm" @click="emit('edit', testCase)">Edit</Button>
      </div>
    </div>
  </div>
</template>
