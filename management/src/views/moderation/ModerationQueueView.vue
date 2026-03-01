<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { toast } from 'vue-sonner'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import {
  IconFlag,
  IconFlagOff,
  IconCheck,
  IconX,
  IconClock,
  IconEye,
  IconAlertTriangle,
  IconChecks,
} from '@tabler/icons-vue'
import { problemsApi, type Problem } from '@/api/admin/problems'
import { getFlagStatusBadgeVariant } from '@/lib/ui/status'

const { t } = useI18n()
const router = useRouter()

type FlagStatus = 'PENDING' | 'REVIEWED' | 'RESOLVED' | 'DISMISSED'
type FilterValue = FlagStatus | 'all'

const flaggedProblems = ref<Problem[]>([])
const loading = ref(false)
const statusFilter = ref<FilterValue>('all')
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
const totalPages = ref(0)

const selectedProblem = ref<Problem | null>(null)
const moderationDialogOpen = ref(false)
const moderationNotes = ref('')
const moderationStatus = ref<FlagStatus>('REVIEWED')
const moderating = ref(false)

// Batch selection
const selectedIds = ref<Set<string>>(new Set())
const batchModerationDialogOpen = ref(false)
const batchModerationStatus = ref<FlagStatus>('RESOLVED')
const batchModerationNotes = ref('')
const batchModerating = ref(false)

async function loadFlaggedProblems() {
  loading.value = true
  selectedIds.value.clear()
  try {
    const response = await problemsApi.getFlaggedProblems({
      page: currentPage.value,
      limit: pageSize.value,
      status: statusFilter.value === 'all' ? undefined : statusFilter.value,
    })
    flaggedProblems.value = response.data
    total.value = response.total
    totalPages.value = response.totalPages
  } catch (error) {
    console.error('Failed to load flagged problems:', error)
    toast.error(t('moderation.loadError'))
  } finally {
    loading.value = false
  }
}

function openModerationDialog(problem: Problem) {
  selectedProblem.value = problem
  moderationNotes.value = problem.flag_notes || ''
  moderationStatus.value = (problem.flag_status as FlagStatus) || 'REVIEWED'
  moderationDialogOpen.value = true
}

async function handleModeration() {
  if (!selectedProblem.value) return

  moderating.value = true
  try {
    await problemsApi.moderateProblem(selectedProblem.value.id, {
      status: moderationStatus.value,
      notes: moderationNotes.value || undefined,
    })

    toast.success(t('moderation.success'))
    moderationDialogOpen.value = false
    selectedProblem.value = null
    moderationNotes.value = ''

    await loadFlaggedProblems()
  } catch (error) {
    console.error('Failed to moderate problem:', error)
    toast.error(t('moderation.error'))
  } finally {
    moderating.value = false
  }
}

// Batch operations
function toggleSelection(id: string) {
  if (selectedIds.value.has(id)) {
    selectedIds.value.delete(id)
  } else {
    selectedIds.value.add(id)
  }
}

function toggleSelectAll() {
  if (selectedIds.value.size === flaggedProblems.value.length) {
    selectedIds.value.clear()
  } else {
    flaggedProblems.value.forEach((p) => selectedIds.value.add(p.id))
  }
}

function openBatchModerationDialog(status: FlagStatus) {
  batchModerationStatus.value = status
  batchModerationNotes.value = ''
  batchModerationDialogOpen.value = true
}

async function handleBatchModeration() {
  if (selectedIds.value.size === 0) return

  batchModerating.value = true
  try {
    const result = await problemsApi.batchModerateProblems({
      ids: Array.from(selectedIds.value),
      status: batchModerationStatus.value,
      notes: batchModerationNotes.value || undefined,
    })

    const successCount = result.results.filter((r) => r.success).length
    const failCount = result.results.filter((r) => !r.success).length

    if (failCount === 0) {
      toast.success(t('moderation.batchSuccess', { count: successCount }))
    } else {
      toast.warning(t('moderation.batchPartial', { success: successCount, failed: failCount }))
    }

    batchModerationDialogOpen.value = false
    selectedIds.value.clear()
    await loadFlaggedProblems()
  } catch (error) {
    console.error('Failed to batch moderate:', error)
    toast.error(t('moderation.batchError'))
  } finally {
    batchModerating.value = false
  }
}

function viewProblem(problem: Problem) {
  router.push(`/admin/problems/${problem.id}`)
}

function getStatusBadgeVariant(status: FlagStatus | null) {
  if (!status) return 'default'
  const statusMap: Record<FlagStatus, 'PENDING' | 'RESOLVED'> = {
    PENDING: 'PENDING',
    REVIEWED: 'PENDING',
    RESOLVED: 'RESOLVED',
    DISMISSED: 'RESOLVED',
  }
  return getFlagStatusBadgeVariant(statusMap[status] || 'PENDING')
}

function getStatusIcon(status: FlagStatus | null) {
  switch (status) {
    case 'PENDING':
      return IconAlertTriangle
    case 'REVIEWED':
      return IconClock
    case 'RESOLVED':
      return IconCheck
    case 'DISMISSED':
      return IconX
    default:
      return IconFlag
  }
}

function handlePreviousPage() {
  currentPage.value--
  loadFlaggedProblems()
}

function handleNextPage() {
  currentPage.value++
  loadFlaggedProblems()
}

const filteredProblems = computed(() => {
  return flaggedProblems.value
})

const isAllSelected = computed(() => {
  return flaggedProblems.value.length > 0 && selectedIds.value.size === flaggedProblems.value.length
})

const selectedCount = computed(() => selectedIds.value.size)

onMounted(() => {
  loadFlaggedProblems()
})
</script>

<template>
  <div class="space-y-6">
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-3xl font-bold tracking-tight">
          {{ t('moderation.title') }}
        </h1>
        <p class="text-muted-foreground mt-1">
          {{ t('moderation.description') }}
        </p>
      </div>
      <div class="flex items-center gap-2">
        <Select v-model="statusFilter" @update:model-value="loadFlaggedProblems">
          <SelectTrigger class="w-[180px]">
            <SelectValue :placeholder="t('moderation.filterStatus')" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">{{ t('moderation.allStatuses') }}</SelectItem>
            <SelectItem value="PENDING">{{ t('moderation.statusPending') }}</SelectItem>
            <SelectItem value="REVIEWED">{{ t('moderation.statusReviewed') }}</SelectItem>
            <SelectItem value="RESOLVED">{{ t('moderation.statusResolved') }}</SelectItem>
            <SelectItem value="DISMISSED">{{ t('moderation.statusDismissed') }}</SelectItem>
          </SelectContent>
        </Select>
      </div>
    </div>

    <!-- Batch action bar -->
    <div
      v-if="selectedCount > 0"
      class="flex items-center gap-4 p-4 bg-primary/10 rounded-lg border border-primary/20"
    >
      <span class="text-sm font-medium">
        {{ t('moderation.selectedCount', { count: selectedCount }) }}
      </span>
      <div class="flex gap-2">
        <Button variant="outline" size="sm" @click="openBatchModerationDialog('RESOLVED')">
          <IconCheck class="h-4 w-4 mr-1" />
          {{ t('moderation.batchResolve') }}
        </Button>
        <Button variant="outline" size="sm" @click="openBatchModerationDialog('DISMISSED')">
          <IconX class="h-4 w-4 mr-1" />
          {{ t('moderation.batchDismiss') }}
        </Button>
        <Button variant="ghost" size="sm" @click="selectedIds.clear()">
          {{ t('common.clearSelection') }}
        </Button>
      </div>
    </div>

    <div v-if="loading" class="flex items-center justify-center py-12">
      <div class="text-muted-foreground">{{ t('common.loading') }}</div>
    </div>

    <div
      v-else-if="filteredProblems.length === 0"
      class="flex flex-col items-center justify-center py-12 text-center"
    >
      <IconFlagOff class="h-12 w-12 text-muted-foreground mb-4" />
      <h3 class="text-lg font-semibold mb-2">{{ t('moderation.noFlagged') }}</h3>
      <p class="text-muted-foreground max-w-md">
        {{ t('moderation.noFlaggedDescription') }}
      </p>
    </div>

    <div v-else class="space-y-4">
      <!-- Select all header -->
      <div class="flex items-center gap-2 px-2">
        <Checkbox
          :checked="isAllSelected"
          :indeterminate="selectedCount > 0 && !isAllSelected"
          @update:checked="toggleSelectAll"
        />
        <span class="text-sm text-muted-foreground">{{ t('moderation.selectAll') }}</span>
      </div>

      <Card
        v-for="problem in filteredProblems"
        :key="problem.id"
        class="hover:shadow-md transition-shadow"
        :class="{
          'ring-2 ring-primary': selectedIds.has(problem.id),
        }"
      >
        <CardHeader>
          <div class="flex items-start justify-between">
            <div class="flex items-start gap-3 flex-1">
              <Checkbox
                :checked="selectedIds.has(problem.id)"
                @update:checked="toggleSelection(problem.id)"
                class="mt-1"
              />
              <div class="flex-1">
                <div class="flex items-center gap-2 mb-2">
                  <CardTitle class="text-xl">{{ problem.title }}</CardTitle>
                  <Badge :variant="getStatusBadgeVariant(problem.flag_status || null)">
                    <component
                      :is="getStatusIcon(problem.flag_status || null)"
                      class="h-3 w-3 mr-1"
                    />
                    {{ t(`moderation.status${problem.flag_status || 'PENDING'}`) }}
                  </Badge>
                  <Badge v-if="problem.is_premium" variant="secondary">
                    {{ t('common.premium') }}
                  </Badge>
                  <Badge v-if="!problem.is_published" variant="outline">
                    {{ t('common.unpublished') }}
                  </Badge>
                </div>
                <CardDescription class="text-sm">
                  {{ problem.slug }} •
                  {{ t(`common.difficulty.${problem.difficulty.toLowerCase()}`) }}
                </CardDescription>
              </div>
            </div>
            <div class="flex gap-2">
              <Button variant="outline" size="sm" @click="viewProblem(problem)">
                <IconEye class="h-4 w-4 mr-1" />
                {{ t('common.view') }}
              </Button>
              <Button variant="default" size="sm" @click="openModerationDialog(problem)">
                <IconFlag class="h-4 w-4 mr-1" />
                {{ t('moderation.moderate') }}
              </Button>
            </div>
          </div>
        </CardHeader>
        <CardContent>
          <div class="space-y-3">
            <div
              v-if="problem.flag_reason"
              class="bg-destructive/10 border border-destructive/20 rounded-lg p-3"
            >
              <div class="flex items-start gap-2">
                <IconAlertTriangle class="h-5 w-5 text-destructive flex-shrink-0 mt-0.5" />
                <div class="flex-1">
                  <p class="text-sm font-medium text-destructive mb-1">
                    {{ t('moderation.flagReason') }}
                  </p>
                  <p class="text-sm text-muted-foreground">{{ problem.flag_reason }}</p>
                </div>
              </div>
            </div>

            <div v-if="problem.flag_notes" class="bg-muted rounded-lg p-3">
              <p class="text-sm font-medium mb-1">{{ t('moderation.moderationNotes') }}</p>
              <p class="text-sm text-muted-foreground">{{ problem.flag_notes }}</p>
            </div>

            <div class="flex flex-wrap gap-2 text-sm text-muted-foreground">
              <span>{{ t('common.reportedBy') }}: {{ problem.flag_reported_by }}</span>
              <span>•</span>
              <span
                >{{ t('common.reportedAt') }}:
                {{ new Date(problem.flag_reported_at || '').toLocaleDateString() }}</span
              >
              <span>•</span>
              <span>{{ t('common.submissions') }}: {{ problem.submission_count }}</span>
              <span>•</span>
              <span>{{ t('common.solutions') }}: {{ problem.solution_count }}</span>
            </div>

            <div v-if="problem.tags.length > 0" class="flex flex-wrap gap-1">
              <Badge v-for="tag in problem.tags" :key="tag.id" variant="outline" class="text-xs">
                {{ tag.label }}
              </Badge>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>

    <div v-if="totalPages > 1" class="flex items-center justify-center gap-2">
      <Button variant="outline" :disabled="currentPage === 1" @click="handlePreviousPage">
        {{ t('common.previous') }}
      </Button>
      <span class="text-sm text-muted-foreground">
        {{ t('common.page') }} {{ currentPage }} / {{ totalPages }}
      </span>
      <Button variant="outline" :disabled="currentPage === totalPages" @click="handleNextPage">
        {{ t('common.next') }}
      </Button>
    </div>

    <!-- Single moderation dialog -->
    <Dialog v-model:open="moderationDialogOpen">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{{ t('moderation.moderateTitle') }}</DialogTitle>
          <DialogDescription v-if="selectedProblem">
            {{ selectedProblem.title }}
          </DialogDescription>
        </DialogHeader>
        <div class="space-y-4">
          <div>
            <Label for="status">{{ t('moderation.status') }}</Label>
            <Select v-model="moderationStatus">
              <SelectTrigger id="status">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="PENDING">{{ t('moderation.statusPending') }}</SelectItem>
                <SelectItem value="REVIEWED">{{ t('moderation.statusReviewed') }}</SelectItem>
                <SelectItem value="RESOLVED">{{ t('moderation.statusResolved') }}</SelectItem>
                <SelectItem value="DISMISSED">{{ t('moderation.statusDismissed') }}</SelectItem>
              </SelectContent>
            </Select>
          </div>
          <div>
            <Label for="notes">{{ t('moderation.notes') }}</Label>
            <Textarea
              id="notes"
              v-model="moderationNotes"
              :placeholder="t('moderation.notesPlaceholder')"
              rows="4"
            />
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" @click="moderationDialogOpen = false">
            {{ t('common.cancel') }}
          </Button>
          <Button :disabled="moderating" @click="handleModeration">
            <IconCheck v-if="!moderating" class="h-4 w-4 mr-1" />
            {{ moderating ? t('common.saving') : t('common.save') }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <!-- Batch moderation dialog -->
    <Dialog v-model:open="batchModerationDialogOpen">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>
            <div class="flex items-center gap-2">
              <IconChecks class="h-5 w-5" />
              {{ t('moderation.batchModerateTitle') }}
            </div>
          </DialogTitle>
          <DialogDescription>
            {{ t('moderation.batchModerateDescription', { count: selectedCount }) }}
          </DialogDescription>
        </DialogHeader>
        <div class="space-y-4">
          <div>
            <Label>{{ t('moderation.newStatus') }}</Label>
            <div class="mt-2 flex gap-2">
              <Button
                :variant="batchModerationStatus === 'RESOLVED' ? 'default' : 'outline'"
                size="sm"
                @click="batchModerationStatus = 'RESOLVED'"
              >
                <IconCheck class="h-4 w-4 mr-1" />
                {{ t('moderation.statusResolved') }}
              </Button>
              <Button
                :variant="batchModerationStatus === 'DISMISSED' ? 'default' : 'outline'"
                size="sm"
                @click="batchModerationStatus = 'DISMISSED'"
              >
                <IconX class="h-4 w-4 mr-1" />
                {{ t('moderation.statusDismissed') }}
              </Button>
            </div>
          </div>
          <div>
            <Label for="batch-notes">{{ t('moderation.notes') }}</Label>
            <Textarea
              id="batch-notes"
              v-model="batchModerationNotes"
              :placeholder="t('moderation.batchNotesPlaceholder')"
              rows="3"
            />
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" @click="batchModerationDialogOpen = false">
            {{ t('common.cancel') }}
          </Button>
          <Button :disabled="batchModerating" @click="handleBatchModeration">
            <IconChecks v-if="!batchModerating" class="h-4 w-4 mr-1" />
            {{ batchModerating ? t('common.saving') : t('moderation.apply') }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>
