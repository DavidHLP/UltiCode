<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { toast } from 'vue-sonner'
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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Switch } from '@/components/ui/switch'
import {
  problemsApi,
  Difficulty,
  type Problem,
  type BulkEditProblemDto,
} from '@/api/admin/problems'

interface Props {
  open: boolean
  problems: Problem[]
}

const props = defineProps<Props>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  edited: []
}>()

const { t } = useI18n()

const editing = ref(false)
const difficulty = ref<Difficulty | undefined>(undefined)
const isPremium = ref<boolean | undefined>(undefined)

const hasChanges = computed(() => {
  return difficulty.value !== undefined || isPremium.value !== undefined
})

function resetForm() {
  difficulty.value = undefined
  isPremium.value = undefined
}

async function handleEdit() {
  if (!hasChanges.value) {
    toast.error(t('problems.bulkEdit.noChanges'))
    return
  }

  editing.value = true
  try {
    const editData: BulkEditProblemDto = {
      ids: props.problems.map((p) => p.id),
    }

    if (difficulty.value !== undefined) {
      editData.difficulty = difficulty.value
    }

    if (isPremium.value !== undefined) {
      editData.isPremium = isPremium.value
    }

    const response = await problemsApi.bulkEdit(editData)
    const successCount = response.results.filter((r) => r.success).length
    const failedCount = response.results.filter((r) => !r.success).length

    if (failedCount === 0) {
      toast.success(t('problems.bulkEdit.success', { count: successCount }))
    } else if (successCount === 0) {
      toast.error(t('problems.bulkEdit.failure'))
    } else {
      toast.warning(
        t('problems.bulkEdit.partial', {
          success: successCount,
          failed: failedCount,
        }),
      )
    }

    resetForm()
    emit('edited')
    emit('update:open', false)
  } catch (error) {
    console.error('Failed to bulk edit problems:', error)
    toast.error(t('problems.bulkEdit.error'))
  } finally {
    editing.value = false
  }
}

function handleOpenChange(open: boolean) {
  if (!open) {
    resetForm()
  }
  emit('update:open', open)
}
</script>

<template>
  <Dialog :open="open" @update:open="handleOpenChange">
    <DialogContent class="max-w-2xl">
      <DialogHeader>
        <DialogTitle>{{ t('problems.bulkEdit.title') }}</DialogTitle>
        <DialogDescription>
          {{ t('problems.bulkEdit.description', { count: problems.length }) }}
        </DialogDescription>
      </DialogHeader>

      <div class="space-y-4 py-4">
        <!-- Difficulty -->
        <div class="space-y-2">
          <Label for="difficulty">{{ t('problems.bulkEdit.difficulty') }}</Label>
          <Select v-model="difficulty">
            <SelectTrigger id="difficulty">
              <SelectValue :placeholder="t('problems.bulkEdit.difficultyPlaceholder')" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem :value="Difficulty.EASY">
                {{ t('problems.difficulty.easy') }}
              </SelectItem>
              <SelectItem :value="Difficulty.MEDIUM">
                {{ t('problems.difficulty.medium') }}
              </SelectItem>
              <SelectItem :value="Difficulty.HARD">
                {{ t('problems.difficulty.hard') }}
              </SelectItem>
            </SelectContent>
          </Select>
        </div>

        <!-- Premium -->
        <div class="flex items-center justify-between space-x-2">
          <Label for="premium" class="flex flex-col space-y-1">
            <span>{{ t('problems.bulkEdit.premium') }}</span>
            <span class="font-normal text-xs text-muted-foreground">
              {{ t('problems.bulkEdit.premiumHint') }}
            </span>
          </Label>
          <Switch id="premium" v-model="isPremium" />
        </div>
      </div>

      <DialogFooter>
        <Button type="button" variant="outline" @click="handleOpenChange(false)">
          {{ t('common.cancel') }}
        </Button>
        <Button type="button" :disabled="!hasChanges || editing" @click="handleEdit">
          {{ editing ? t('problems.bulkEdit.editing') : t('problems.bulkEdit.edit') }}
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
