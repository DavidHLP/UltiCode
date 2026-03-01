<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { toast } from 'vue-sonner'
import {
  IconHistory,
  IconRefresh,
  IconRestore,
  IconArrowRight,
  IconClock,
  IconUser,
  IconFileText,
  IconX,
  IconLoader,
} from '@tabler/icons-vue'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Separator } from '@/components/ui/separator'
import { problemsApi, type ProblemVersion } from '@/api/admin/problems'

interface Props {
  problemId: string
  open: boolean
}

const props = defineProps<Props>()
const emit = defineEmits<{
  'update:open': [value: boolean]
  restored: []
}>()

const { t } = useI18n()

const versions = ref<ProblemVersion[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
const selectedVersion = ref<ProblemVersion | null>(null)
const restoreDialogOpen = ref(false)
const restoring = ref(false)

const sortedVersions = computed(() => {
  return [...versions.value].sort((a, b) => {
    return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
  })
})

async function loadVersions() {
  if (!props.problemId) return

  loading.value = true
  error.value = null

  try {
    const response = await problemsApi.getProblemVersions(props.problemId)
    versions.value = response.versions
  } catch (err) {
    console.error('Failed to load versions:', err)
    error.value = t('problems.versionHistory.loadError')
  } finally {
    loading.value = false
  }
}

function openRestoreDialog(version: ProblemVersion) {
  selectedVersion.value = version
  restoreDialogOpen.value = true
}

async function confirmRestore() {
  if (!selectedVersion.value || !props.problemId) return

  restoring.value = true
  try {
    await problemsApi.rollbackToVersion(props.problemId, selectedVersion.value.id)
    toast.success(t('problems.versionHistory.restoreSuccess'))
    restoreDialogOpen.value = false
    emit('restored')
    emit('update:open', false)
  } catch (err) {
    console.error('Failed to restore version:', err)
    toast.error(t('problems.versionHistory.restoreError'))
  } finally {
    restoring.value = false
  }
}

function getActionLabel(changeType: string) {
  switch (changeType) {
    case 'create':
      return t('problems.versionHistory.actions.created')
    case 'update':
      return t('problems.versionHistory.actions.updated')
    case 'rollback':
      return t('problems.versionHistory.action.RESTORE')
    default:
      return changeType
  }
}

function getActionIcon(changeType: string) {
  switch (changeType) {
    case 'create':
      return IconFileText
    case 'update':
      return IconRefresh
    case 'rollback':
      return IconRestore
    default:
      return IconHistory
  }
}

function formatTimestamp(timestamp: Date | string) {
  const date = new Date(timestamp)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffMins = Math.floor(diffMs / 60000)
  const diffHours = Math.floor(diffMs / 3600000)
  const diffDays = Math.floor(diffMs / 86400000)

  if (diffMins < 1) return t('common.justNow')
  if (diffMins < 60) return t('common.minutesAgo', { count: diffMins })
  if (diffHours < 24) return t('common.hoursAgo', { count: diffHours })
  if (diffDays < 7) return t('common.daysAgo', { count: diffDays })

  return date.toLocaleDateString()
}

function getChangedFields(version: ProblemVersion): string[] {
  // New API doesn't store oldValues/newValues directly
  // Return empty array - the change summary will show what changed
  if (!version.changeSummary) return []
  // Extract field names from change summary if possible
  return []
}

onMounted(() => {
  if (props.open) {
    loadVersions()
  }
})

function handleClose() {
  emit('update:open', false)
}
</script>

<template>
  <Dialog :open="open" @update:open="handleClose">
    <DialogContent class="max-w-2xl max-h-[80vh] overflow-hidden flex flex-col">
      <DialogHeader>
        <div class="flex items-center gap-2">
          <IconHistory class="h-5 w-5 text-muted-foreground" />
          <DialogTitle>{{ t('problems.versionHistory.title') }}</DialogTitle>
        </div>
        <DialogDescription>
          {{ t('problems.versionHistory.description') }}
        </DialogDescription>
      </DialogHeader>

      <!-- Content -->
      <div class="flex-1 overflow-y-auto -mx-6 px-6">
        <!-- Loading State -->
        <div v-if="loading" class="space-y-4 py-4">
          <div v-for="i in 3" :key="i" class="space-y-2">
            <Skeleton class="h-4 w-32" />
            <Skeleton class="h-16 w-full" />
          </div>
        </div>

        <!-- Error State -->
        <div v-else-if="error" class="flex flex-col items-center justify-center py-12 text-center">
          <IconX class="h-10 w-10 text-destructive mb-3" />
          <p class="text-sm text-muted-foreground mb-4">{{ error }}</p>
          <Button variant="outline" size="sm" @click="loadVersions">
            <IconRefresh class="mr-2 h-4 w-4" />
            {{ t('common.retry') }}
          </Button>
        </div>

        <!-- Empty State -->
        <div
          v-else-if="sortedVersions.length === 0"
          class="flex flex-col items-center justify-center py-12 text-center"
        >
          <IconHistory class="h-10 w-10 text-muted-foreground mb-3" />
          <p class="text-sm text-muted-foreground">
            {{ t('problems.versionHistory.noVersions') }}
          </p>
        </div>

        <!-- Version List -->
        <div v-else class="py-4 space-y-4">
          <div
            v-for="(version, index) in sortedVersions"
            :key="version.id"
            class="relative pl-6 pb-4"
            :class="{ 'pb-0': index === sortedVersions.length - 1 }"
          >
            <!-- Timeline Line -->
            <div
              v-if="index !== sortedVersions.length - 1"
              class="absolute left-[7px] top-8 bottom-0 w-0.5 bg-border"
            />

            <!-- Timeline Dot -->
            <div class="absolute left-0 top-1.5">
              <div
                class="h-4 w-4 rounded-full border-2 border-primary bg-background flex items-center justify-center"
              >
                <component
                  :is="getActionIcon(version.changeType)"
                  class="h-2.5 w-2.5 text-primary"
                />
              </div>
            </div>

            <!-- Version Content -->
            <div class="space-y-2">
              <!-- Header -->
              <div class="flex items-start justify-between gap-2">
                <div class="flex items-center gap-2">
                  <Badge variant="outline" class="text-xs">
                    {{ getActionLabel(version.changeType) }}
                  </Badge>
                  <div class="flex items-center gap-1 text-xs text-muted-foreground">
                    <IconClock class="h-3 w-3" />
                    <span>{{ formatTimestamp(version.createdAt) }}</span>
                  </div>
                </div>

                <Button
                  v-if="version.changeType !== 'create'"
                  variant="ghost"
                  size="sm"
                  class="h-7 text-xs"
                  @click="openRestoreDialog(version)"
                >
                  <IconRestore class="mr-1 h-3 w-3" />
                  {{ t('problems.versionHistory.restore') }}
                </Button>
              </div>

              <!-- Performer -->
              <div class="flex items-center gap-1.5 text-xs text-muted-foreground">
                <IconUser class="h-3 w-3" />
                <span>{{ version.createdBy || t('common.unknown') }}</span>
              </div>

              <!-- Changed Fields -->
              <div v-if="version.changeType !== 'create'" class="space-y-1.5">
                <div class="flex items-center gap-1.5 text-xs font-medium text-muted-foreground">
                  <IconArrowRight class="h-3 w-3" />
                  <span>{{ t('problems.versionHistory.changedFields') }}</span>
                </div>
                <div class="flex flex-wrap gap-1">
                  <Badge
                    v-for="field in getChangedFields(version)"
                    :key="field"
                    variant="secondary"
                    class="text-xs"
                  >
                    {{ field }}
                  </Badge>
                  <span
                    v-if="getChangedFields(version).length === 0"
                    class="text-xs text-muted-foreground"
                  >
                    {{ t('problems.versionHistory.noChanges') }}
                  </span>
                </div>
              </div>

              <!-- Separator -->
              <Separator v-if="index !== sortedVersions.length - 1" class="my-4" />
            </div>
          </div>
        </div>
      </div>

      <!-- Footer -->
      <DialogFooter class="border-t pt-4">
        <Button variant="outline" @click="handleClose">
          {{ t('common.close') }}
        </Button>
      </DialogFooter>
    </DialogContent>

    <!-- Restore Confirmation Dialog -->
    <Dialog v-model:open="restoreDialogOpen">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{{ t('problems.versionHistory.restoreConfirmTitle') }}</DialogTitle>
          <DialogDescription>
            {{ t('problems.versionHistory.restoreConfirmDescription') }}
          </DialogDescription>
        </DialogHeader>

        <div v-if="selectedVersion" class="space-y-2 py-4">
          <div class="flex items-center gap-2 text-sm">
            <IconClock class="h-4 w-4 text-muted-foreground" />
            <span class="text-muted-foreground">{{
              formatTimestamp(selectedVersion.createdAt)
            }}</span>
          </div>
          <div class="flex items-center gap-2 text-sm">
            <IconUser class="h-4 w-4 text-muted-foreground" />
            <span class="text-muted-foreground">{{
              selectedVersion.createdBy || t('common.unknown')
            }}</span>
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" @click="restoreDialogOpen = false">
            {{ t('common.cancel') }}
          </Button>
          <Button variant="destructive" :disabled="restoring" @click="confirmRestore">
            <IconLoader v-if="restoring" class="mr-2 h-4 w-4 animate-spin" />
            <IconRestore v-else class="mr-2 h-4 w-4" />
            {{ t('problems.versionHistory.restore') }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </Dialog>
</template>
