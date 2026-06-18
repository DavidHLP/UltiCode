<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { toast } from 'vue-sonner'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { Textarea } from '@/components/ui/textarea'
import { Skeleton } from '@/components/ui/skeleton'
import { IconHistory, IconEye, IconRotateClockwise, IconGitCompare, IconX } from '@tabler/icons-vue'
import {
  problemsApi,
  type ProblemVersion,
  type ProblemVersionDetail,
  type VersionWithDiff,
} from '@/api/admin/problems'
import { formatDate } from '@/lib/format/date'

const { t } = useI18n()

interface Props {
  open: boolean
  problemId: string
}

const props = defineProps<Props>()
const emit = defineEmits<{
  'update:open': [value: boolean]
  restored: []
}>()

// State
const loading = ref(false)
const versions = ref<ProblemVersion[]>([])
const pagination = ref({ total: 0, page: 1, limit: 20, totalPages: 0 })
const selectedVersion = ref<ProblemVersionDetail | null>(null)
const versionDetailLoading = ref(false)
const compareMode = ref(false)
const compareFrom = ref<string | null>(null)
const compareTo = ref<string | null>(null)
const diffResult = ref<VersionWithDiff | null>(null)
const diffLoading = ref(false)
const rollbackDialogOpen = ref(false)
const rollbackTarget = ref<ProblemVersion | null>(null)
const rollbackReason = ref('')
const rollbackLoading = ref(false)
const createInitialLoading = ref(false)

// Computed
const isOpen = computed({
  get: () => props.open,
  set: (value) => emit('update:open', value),
})

const hasVersions = computed(() => (versions.value?.length ?? 0) > 0)

// Methods
async function loadVersions() {
  if (!props.problemId) return

  loading.value = true
  try {
    const response = await problemsApi.getProblemVersions(props.problemId, {
      page: pagination.value.page,
      limit: pagination.value.limit,
    })
    versions.value = response.versions ?? []
    pagination.value = response.pagination ?? { total: 0, page: 1, limit: 20, totalPages: 0 }
  } catch (error) {
    console.error('Failed to load version history:', error)
    toast.error(t('problems.versionHistory.loadError'))
  } finally {
    loading.value = false
  }
}

async function viewVersionDetail(version: ProblemVersion) {
  versionDetailLoading.value = true
  try {
    const detail = await problemsApi.getProblemVersion(props.problemId, version.id)
    selectedVersion.value = detail
  } catch (error) {
    console.error('Failed to load version detail:', error)
    toast.error(t('problems.versionHistory.loadDetailError'))
  } finally {
    versionDetailLoading.value = false
  }
}

function startCompare(versionId: string) {
  if (!compareFrom.value) {
    compareFrom.value = versionId
    compareMode.value = true
  } else if (compareFrom.value !== versionId) {
    compareTo.value = versionId
    executeCompare()
  }
}

function cancelCompare() {
  compareMode.value = false
  compareFrom.value = null
  compareTo.value = null
  diffResult.value = null
}

async function executeCompare() {
  if (!compareFrom.value || !compareTo.value) return

  diffLoading.value = true
  try {
    const diff = await problemsApi.getVersionDiff(
      props.problemId,
      compareFrom.value,
      compareTo.value,
    )
    diffResult.value = diff
  } catch (error) {
    console.error('Failed to compare versions:', error)
    toast.error(t('problems.versionHistory.compareError'))
  } finally {
    diffLoading.value = false
  }
}

function confirmRollback(version: ProblemVersion) {
  rollbackTarget.value = version
  rollbackReason.value = ''
  rollbackDialogOpen.value = true
}

async function executeRollback() {
  if (!rollbackTarget.value) return

  rollbackLoading.value = true
  try {
    await problemsApi.rollbackToVersion(
      props.problemId,
      rollbackTarget.value.id,
      rollbackReason.value || undefined,
    )
    toast.success(
      t('problems.versionHistory.rollbackSuccess', { version: rollbackTarget.value.versionNumber }),
    )
    rollbackDialogOpen.value = false
    rollbackTarget.value = null
    rollbackReason.value = ''
    loadVersions()
    emit('restored')
  } catch (error) {
    console.error('Failed to rollback:', error)
    toast.error(t('problems.versionHistory.rollbackError'))
  } finally {
    rollbackLoading.value = false
  }
}

async function createInitialSnapshot() {
  createInitialLoading.value = true
  try {
    const result = await problemsApi.createInitialVersion(props.problemId)
    if (result.success) {
      toast.success(t('problems.versionHistory.createInitialSuccess'))
      loadVersions()
      emit('restored')
    } else {
      toast.info(t('problems.versionHistory.alreadyHasVersions'))
    }
  } catch (error) {
    console.error('Failed to create initial version:', error)
    toast.error(t('problems.versionHistory.createInitialError'))
  } finally {
    createInitialLoading.value = false
  }
}

function getChangeTypeStyle(type: string): { bg: string; border: string; text: string } {
  const defaultStyle = {
    bg: 'bg-[color-mix(in_oklch,_var(--terminal-amber)_15%,_transparent)]',
    border: 'border-[color-mix(in_oklch,_var(--terminal-amber)_40%,_transparent)]',
    text: 'text-[var(--terminal-amber)]',
  }
  const styles: Record<string, { bg: string; border: string; text: string }> = {
    create: {
      bg: 'bg-[color-mix(in_oklch,_var(--terminal-green)_15%,_transparent)]',
      border: 'border-[color-mix(in_oklch,_var(--terminal-green)_40%,_transparent)]',
      text: 'text-[var(--terminal-green)]',
    },
    update: defaultStyle,
    rollback: {
      bg: 'bg-[color-mix(in_oklch,_var(--accent-electric)_15%,_transparent)]',
      border: 'border-[color-mix(in_oklch,_var(--accent-electric)_40%,_transparent)]',
      text: 'text-[var(--accent-electric)]',
    },
  }
  return styles[type] ?? defaultStyle
}

function formatFieldName(field: string): string {
  return field.replace(/([A-Z])/g, ' $1').replace(/^./, (s) => s.toUpperCase())
}

// Watch
watch(
  () => props.open,
  (open) => {
    if (open) {
      loadVersions()
    } else {
      // Reset state when dialog closes
      selectedVersion.value = null
      cancelCompare()
    }
  },
)
</script>

<template>
  <Dialog v-model:open="isOpen">
    <DialogContent class="sm:max-w-2xl max-h-[85vh] overflow-hidden flex flex-col">
      <DialogHeader>
        <DialogTitle class="flex items-center gap-2">
          <IconHistory class="h-5 w-5" />
          {{ t('problems.versionHistory.title') }}
        </DialogTitle>
        <DialogDescription>
          {{ t('problems.versionHistory.description') }}
        </DialogDescription>
      </DialogHeader>

      <div class="flex-1 overflow-y-auto -mx-6 px-6">
        <!-- Compare Mode Banner -->
        <div
          v-if="compareMode"
          class="mb-4 p-3 rounded-none border border-[var(--accent-electric)] bg-[color-mix(in_oklch,_var(--accent-electric)_10%,_transparent)]"
        >
          <div class="flex items-center justify-between">
            <span class="text-sm text-[var(--accent-electric)]">
              {{ t('problems.versionHistory.compareWith') }}: Version {{ compareFrom }}
            </span>
            <Button variant="ghost" size="sm" @click="cancelCompare">
              <IconX class="h-4 w-4" />
            </Button>
          </div>
        </div>

        <!-- Loading State -->
        <div v-if="loading" class="space-y-3">
          <Skeleton v-for="i in 5" :key="i" class="h-16 w-full rounded-none" />
        </div>

        <!-- Empty State -->
        <div
          v-else-if="!hasVersions"
          class="flex flex-col items-center justify-center py-12 text-center"
        >
          <div class="w-12 h-12 rounded-full bg-muted flex items-center justify-center mb-3">
            <IconHistory class="h-6 w-6 text-muted-foreground" />
          </div>
          <p class="text-sm text-muted-foreground mb-4">
            {{ t('problems.versionHistory.noVersions') }}
          </p>
          <Button
            variant="outline"
            size="sm"
            :disabled="createInitialLoading"
            @click="createInitialSnapshot"
          >
            <IconRotateClockwise class="h-4 w-4 mr-2" />
            {{
              createInitialLoading
                ? t('common.loading')
                : t('problems.versionHistory.createInitial')
            }}
          </Button>
        </div>

        <!-- Version List -->
        <div v-else class="space-y-2">
          <div
            v-for="version in versions"
            :key="version.id"
            class="group p-4 rounded-none border bg-card hover:bg-muted/50 transition-colors"
            :class="{
              'border-[var(--accent-electric)]': compareFrom === version.id,
            }"
          >
            <div class="flex items-start justify-between gap-4">
              <div class="flex items-start gap-3">
                <!-- Version Number -->
                <div class="flex items-center gap-2">
                  <Badge variant="outline" class="font-mono text-xs">
                    v{{ version.versionNumber }}
                  </Badge>
                  <Badge
                    variant="outline"
                    :class="[
                      'font-data text-2xs uppercase',
                      getChangeTypeStyle(version.changeType).bg,
                      getChangeTypeStyle(version.changeType).border,
                      getChangeTypeStyle(version.changeType).text,
                    ]"
                  >
                    {{
                      t(
                        `problems.versionHistory.action.${version.changeType.toUpperCase()}`,
                        version.changeType,
                      )
                    }}
                  </Badge>
                </div>

                <!-- Version Info -->
                <div class="flex-1 min-w-0">
                  <p v-if="version.changeSummary" class="text-sm truncate">
                    {{ version.changeSummary }}
                  </p>
                  <p class="text-xs text-muted-foreground mt-1">
                    {{ formatDate(version.createdAt) }}
                    <span v-if="version.createdBy">
                      {{ t('problems.versionHistory.author') }} {{ version.createdBy }}
                    </span>
                  </p>
                </div>
              </div>

              <!-- Actions -->
              <div
                class="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity"
              >
                <Button
                  variant="ghost"
                  size="icon"
                  class="h-8 w-8"
                  @click="viewVersionDetail(version)"
                >
                  <IconEye class="h-4 w-4" />
                </Button>
                <Button
                  variant="ghost"
                  size="icon"
                  class="h-8 w-8"
                  :class="{ 'text-[var(--accent-electric)]': compareFrom === version.id }"
                  @click="startCompare(version.id)"
                >
                  <IconGitCompare class="h-4 w-4" />
                </Button>
                <Button
                  variant="ghost"
                  size="icon"
                  class="h-8 w-8 text-[var(--terminal-amber)]"
                  @click="confirmRollback(version)"
                >
                  <IconRotateClockwise class="h-4 w-4" />
                </Button>
              </div>
            </div>
          </div>
        </div>

        <!-- Version Detail Panel -->
        <div v-if="selectedVersion" class="mt-4 p-4 rounded-none border bg-muted/30">
          <div class="flex items-center justify-between mb-3">
            <h4 class="font-medium text-sm">
              {{ t('problems.versionHistory.versionDetails') }} - v{{
                selectedVersion.versionNumber
              }}
            </h4>
            <Button variant="ghost" size="sm" @click="selectedVersion = null">
              <IconX class="h-4 w-4" />
            </Button>
          </div>
          <div class="grid grid-cols-2 gap-3 text-sm">
            <div>
              <span class="text-muted-foreground">Title:</span>
              <span class="ml-2">{{ selectedVersion.title }}</span>
            </div>
            <div>
              <span class="text-muted-foreground">Slug:</span>
              <span class="ml-2 font-mono">{{ selectedVersion.slug }}</span>
            </div>
            <div>
              <span class="text-muted-foreground">Difficulty:</span>
              <span class="ml-2">{{ selectedVersion.difficulty }}</span>
            </div>
            <div>
              <span class="text-muted-foreground">Premium:</span>
              <span class="ml-2">{{ selectedVersion.isPremium ? 'Yes' : 'No' }}</span>
            </div>
          </div>
        </div>

        <!-- Diff Result Panel -->
        <div v-if="diffResult" class="mt-4 p-4 rounded-none border bg-muted/30">
          <div class="flex items-center justify-between mb-3">
            <h4 class="font-medium text-sm">
              {{ t('problems.versionHistory.compareVersions') }}
            </h4>
            <Button variant="ghost" size="sm" @click="diffResult = null">
              <IconX class="h-4 w-4" />
            </Button>
          </div>

          <div v-if="diffResult.diffs.length === 0" class="text-sm text-muted-foreground">
            {{ t('problems.versionHistory.noChanges') }}
          </div>

          <div v-else class="space-y-2">
            <div
              v-for="(diff, index) in diffResult.diffs"
              :key="index"
              class="p-3 rounded-none border bg-card"
            >
              <p class="font-medium text-sm mb-2">{{ formatFieldName(diff.field) }}</p>
              <div class="grid grid-cols-2 gap-4 text-xs">
                <div>
                  <p class="text-muted-foreground mb-1">
                    {{ t('problems.versionHistory.oldValue') }}:
                  </p>
                  <pre
                    class="p-2 rounded-none bg-red-50 dark:bg-red-950/30 text-red-600 dark:text-red-400 overflow-x-auto"
                    >{{ JSON.stringify(diff.oldValue, null, 2) }}</pre
                  >
                </div>
                <div>
                  <p class="text-muted-foreground mb-1">
                    {{ t('problems.versionHistory.newValue') }}:
                  </p>
                  <pre
                    class="p-2 rounded-none bg-green-50 dark:bg-green-950/30 text-green-600 dark:text-green-400 overflow-x-auto"
                    >{{ JSON.stringify(diff.newValue, null, 2) }}</pre
                  >
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </DialogContent>
  </Dialog>

  <!-- Rollback Confirmation Dialog -->
  <AlertDialog v-model:open="rollbackDialogOpen">
    <AlertDialogContent>
      <AlertDialogHeader>
        <AlertDialogTitle>{{ t('problems.versionHistory.rollbackTitle') }}</AlertDialogTitle>
        <AlertDialogDescription>
          {{
            t('problems.versionHistory.rollbackConfirm', { version: rollbackTarget?.versionNumber })
          }}
        </AlertDialogDescription>
      </AlertDialogHeader>
      <div class="py-4">
        <Textarea
          v-model="rollbackReason"
          :placeholder="t('problems.versionHistory.rollbackReasonPlaceholder')"
          rows="3"
        />
      </div>
      <AlertDialogFooter>
        <AlertDialogCancel>{{ t('common.cancel') }}</AlertDialogCancel>
        <AlertDialogAction :disabled="rollbackLoading" @click="executeRollback">
          <IconRotateClockwise class="h-4 w-4 mr-2" />
          {{ t('problems.versionHistory.rollbackButton') }}
        </AlertDialogAction>
      </AlertDialogFooter>
    </AlertDialogContent>
  </AlertDialog>
</template>
