<script setup lang="ts">
import { ref } from 'vue'
import { toast } from 'vue-sonner'
import { IconFlag, IconLoader } from '@tabler/icons-vue'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { useSolutionsStore } from '@/stores/admin/solutions'

const props = defineProps<{
  open: boolean
  solutionId: string | null
  solutionTitle: string | null
}>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'success'): void
}>()

const solutionsStore = useSolutionsStore()
const loading = ref(false)
const reason = ref('')

async function handleFlag() {
  if (!props.solutionId) return
  if (!reason.value.trim()) {
    toast.error('Please provide a reason for flagging')
    return
  }

  loading.value = true
  try {
    await solutionsStore.flagSolution(props.solutionId, { reason: reason.value })
    toast.success('Solution flagged successfully')
    reason.value = ''
    emit('update:open', false)
    emit('success')
  } catch (error) {
    toast.error('Failed to flag solution')
    console.error(error)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <Dialog :open="open" @update:open="$emit('update:open', $event)">
    <DialogContent>
      <DialogHeader>
        <DialogTitle class="flex items-center gap-2 text-amber-600">
          <IconFlag class="h-5 w-5" />
          Flag Solution
        </DialogTitle>
        <DialogDescription>
          Flagging solution <span class="font-medium text-foreground">"{{ solutionTitle }}"</span> will mark it for review and may hide it from public view depending on settings.
        </DialogDescription>
      </DialogHeader>

      <div class="grid gap-4 py-4">
        <div class="space-y-2">
          <Label for="reason">Reason for flagging</Label>
          <Textarea
            id="reason"
            v-model="reason"
            placeholder="Please explain why this solution violates community guidelines..."
            class="min-h-[100px]"
          />
        </div>
      </div>

      <DialogFooter>
        <Button variant="outline" @click="$emit('update:open', false)" :disabled="loading">
          Cancel
        </Button>
        <Button class="bg-amber-600 hover:bg-amber-700 text-white" @click="handleFlag" :disabled="loading">
          <IconLoader v-if="loading" class="mr-2 h-4 w-4 animate-spin" />
          Flag Solution
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
