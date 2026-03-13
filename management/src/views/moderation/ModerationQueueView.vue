<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { toast } from 'vue-sonner'
import { Button } from '@/components/ui/button'
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
  IconShield,
} from '@tabler/icons-vue'
import { problemsApi, type Problem } from '@/api/admin/problems'

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

// Animation state for staggered reveal
const isLoaded = ref(false)

onMounted(() => {
  setTimeout(() => {
    isLoaded.value = true
  }, 100)
  loadFlaggedProblems()
})

// Stats for terminal ticker
const stats = computed(() => {
  const problems = flaggedProblems.value
  return {
    total: total.value,
    pending: problems.filter((p) => p.flag_status === 'PENDING').length,
    reviewed: problems.filter((p) => p.flag_status === 'REVIEWED').length,
    resolved: problems.filter((p) => p.flag_status === 'RESOLVED').length,
  }
})

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

function getStatusClasses(status: FlagStatus | null) {
  switch (status) {
    case 'PENDING':
      return 'bg-[oklch(0.75_0.15_85/0.15)] border-[oklch(0.75_0.15_85/0.4)] text-[var(--terminal-amber)]'
    case 'REVIEWED':
      return 'bg-[oklch(0.7_0.12_200/0.15)] border-[oklch(0.7_0.12_200/0.4)] text-[var(--terminal-cyan)]'
    case 'RESOLVED':
      return 'bg-[oklch(0.7_0.15_145/0.15)] border-[oklch(0.7_0.15_145/0.4)] text-[var(--terminal-green)]'
    case 'DISMISSED':
      return 'bg-[oklch(0.6_0.2_25/0.15)] border-[oklch(0.6_0.2_25/0.4)] text-[var(--terminal-red)]'
    default:
      return 'bg-[var(--silver-200)] border-[var(--silver-300)] text-[var(--silver-600)]'
  }
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
</script>

<template>
  <div class="relative flex flex-col gap-0 overflow-auto">
    <!-- Terminal Header -->
    <div
      :class="[
        'border-b border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--card)]',
        'transition-all duration-500',
        isLoaded ? 'opacity-100 translate-y-0' : 'opacity-0 -translate-y-2',
      ]"
    >
      <!-- Title Row -->
      <div class="px-4 lg:px-6 py-4 flex items-center justify-between">
        <div class="flex items-center gap-4">
          <div class="flex items-center gap-2">
            <span class="terminal-prompt text-base">moderation</span>
            <span class="terminal-cursor" />
          </div>
          <h1 class="text-xl font-medium tracking-tight text-[var(--foreground)]">
            {{ t('moderation.title') }}
          </h1>
        </div>
        <Select v-model="statusFilter" @update:model-value="loadFlaggedProblems">
          <SelectTrigger
            class="w-[180px] h-9 font-data text-xs border-[var(--silver-300)] hover:border-[var(--accent-electric)] bg-transparent"
          >
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

      <!-- Stats Ticker -->
      <div
        class="px-4 lg:px-6 py-2.5 flex items-center gap-6 border-t border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--surface-sunken)]"
      >
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]">total:</span>
          <span class="font-data text-sm text-[var(--terminal-cyan)] tabular-nums">{{
            stats.total
          }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]">pending:</span>
          <span class="font-data text-sm text-[var(--terminal-amber)] tabular-nums">{{
            stats.pending
          }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]">reviewed:</span>
          <span class="font-data text-sm text-[var(--terminal-cyan)] tabular-nums">{{
            stats.reviewed
          }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]">resolved:</span>
          <span class="font-data text-sm text-[var(--terminal-green)] tabular-nums">{{
            stats.resolved
          }}</span>
        </div>
        <div class="ml-auto flex items-center gap-2 text-[var(--silver-400)]">
          <IconShield class="h-4 w-4" />
          <span class="text-xs font-data uppercase tracking-wider">content moderation</span>
        </div>
      </div>
    </div>

    <!-- Bulk Action Bar - Terminal Style -->
    <div
      v-if="selectedCount > 0"
      :class="[
        'mx-4 lg:mx-6 mt-4 flex items-center justify-between border border-[var(--terminal-amber)] bg-[oklch(0.75_0.15_85/0.08)] dark:bg-[oklch(0.75_0.15_85/0.15)] p-3',
        'animate-in fade-in slide-in-from-top-2 duration-200',
      ]"
    >
      <div class="flex items-center gap-4">
        <div class="flex items-center gap-2">
          <span class="font-data text-sm text-[var(--terminal-amber)]">
            &gt; SELECTED:{{ selectedCount }}
          </span>
        </div>
        <div class="h-4 w-px bg-[var(--silver-300)]" />
        <div class="flex items-center gap-2">
          <Button
            variant="terminal"
            size="sm"
            class="h-8 font-data text-xs border-[var(--silver-300)] hover:border-[var(--terminal-green)] hover:text-[var(--terminal-green)]"
            @click="openBatchModerationDialog('RESOLVED')"
          >
            <IconCheck class="h-3.5 w-3.5 mr-1.5" />
            <span class="uppercase tracking-wider">{{ t('moderation.batchResolve') }}</span>
          </Button>
          <Button
            variant="terminal"
            size="sm"
            class="h-8 font-data text-xs border-[var(--silver-300)] hover:border-[var(--terminal-red)] hover:text-[var(--terminal-red)]"
            @click="openBatchModerationDialog('DISMISSED')"
          >
            <IconX class="h-3.5 w-3.5 mr-1.5" />
            <span class="uppercase tracking-wider">{{ t('moderation.batchDismiss') }}</span>
          </Button>
        </div>
      </div>
      <Button
        variant="terminal"
        size="sm"
        class="h-8 font-data text-xs text-[var(--silver-500)] hover:text-[var(--foreground)]"
        @click="selectedIds.clear()"
      >
        [ESC] {{ t('common.clearSelection') }}
      </Button>
    </div>

    <!-- Main Content Area -->
    <div class="flex-1 py-4">
      <!-- Loading State -->
      <div
        v-if="loading"
        class="flex items-center justify-center py-12 border border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--surface-sunken)]"
      >
        <div class="flex items-center gap-3">
          <span class="font-data text-sm text-[var(--terminal-cyan)]">&gt; LOADING...</span>
        </div>
      </div>

      <!-- Empty State - Terminal Style -->
      <div
        v-else-if="filteredProblems.length === 0"
        class="flex flex-col items-center justify-center py-12 border border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--surface-sunken)]"
      >
        <div class="flex flex-col items-center gap-4">
          <div class="flex items-center gap-2 text-[var(--silver-400)]">
            <IconFlagOff class="h-8 w-8" />
          </div>
          <div class="text-center">
            <span class="font-data text-sm text-[var(--silver-500)]"> &gt; NO_FLAGGED_ITEMS </span>
            <p class="text-sm text-[var(--silver-400)] mt-2 max-w-md">
              {{ t('moderation.noFlaggedDescription') }}
            </p>
          </div>
        </div>
      </div>

      <!-- Problem Cards - Terminal Style -->
      <div v-else class="space-y-3">
        <!-- Select all header -->
        <div
          class="flex items-center gap-2 px-3 py-2 border border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--surface-sunken)]"
        >
          <Checkbox
            :checked="isAllSelected"
            :indeterminate="selectedCount > 0 && !isAllSelected"
            @update:checked="toggleSelectAll"
          />
          <span class="text-xs font-data text-[var(--silver-500)] uppercase tracking-wider">
            {{ t('moderation.selectAll') }}
          </span>
        </div>

        <div
          v-for="problem in filteredProblems"
          :key="problem.id"
          :class="[
            'border border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--card)]',
            'hover:border-[var(--silver-400)] dark:hover:border-[var(--silver-400)] transition-colors',
            selectedIds.has(problem.id) ? 'ring-2 ring-[var(--accent-electric)]' : '',
          ]"
        >
          <!-- Card Header -->
          <div
            class="px-4 py-3 border-b border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--surface-sunken)] flex items-center justify-between"
          >
            <div class="flex items-center gap-3 flex-1">
              <Checkbox
                :checked="selectedIds.has(problem.id)"
                @update:checked="toggleSelection(problem.id)"
              />
              <div class="flex items-center gap-3 flex-1">
                <span class="font-data text-base text-[var(--foreground)]">{{
                  problem.title
                }}</span>
                <!-- Status Badge -->
                <span
                  :class="[
                    'inline-flex items-center gap-1 px-2 py-0.5 border text-xs font-data uppercase tracking-wider',
                    getStatusClasses(problem.flag_status || null),
                  ]"
                >
                  <component :is="getStatusIcon(problem.flag_status || null)" class="h-3 w-3" />
                  {{ t(`moderation.status${problem.flag_status || 'PENDING'}`) }}
                </span>
                <!-- Additional Badges -->
                <span
                  v-if="problem.is_premium"
                  class="inline-flex items-center px-2 py-0.5 border border-[var(--terminal-amber)] bg-[oklch(0.75_0.15_85/0.15)] text-xs font-data text-[var(--terminal-amber)] uppercase"
                >
                  {{ t('common.premium') }}
                </span>
                <span
                  v-if="!problem.is_published"
                  class="inline-flex items-center px-2 py-0.5 border border-[var(--silver-300)] bg-[var(--silver-200)] text-xs font-data text-[var(--silver-600)] uppercase"
                >
                  {{ t('common.unpublished') }}
                </span>
              </div>
            </div>
            <div class="flex gap-2">
              <Button
                variant="terminal"
                size="sm"
                class="h-8 font-data text-xs border-[var(--silver-300)] hover:border-[var(--terminal-cyan)] hover:text-[var(--terminal-cyan)]"
                @click="viewProblem(problem)"
              >
                <IconEye class="h-3.5 w-3.5 mr-1.5" />
                <span class="uppercase tracking-wider">{{ t('common.view') }}</span>
              </Button>
              <Button
                variant="terminal"
                size="sm"
                class="h-8 font-data text-xs border-[var(--terminal-amber)] text-[var(--terminal-amber)] hover:bg-[oklch(0.75_0.15_85/0.1)]"
                @click="openModerationDialog(problem)"
              >
                <IconFlag class="h-3.5 w-3.5 mr-1.5" />
                <span class="uppercase tracking-wider">{{ t('moderation.moderate') }}</span>
              </Button>
            </div>
          </div>

          <!-- Card Content -->
          <div class="px-4 py-3 space-y-3">
            <!-- Slug and Difficulty -->
            <div class="text-sm font-data text-[var(--silver-500)]">
              {{ problem.slug }} •
              {{ t(`common.difficulty.${problem.difficulty.toLowerCase()}`) }}
            </div>

            <!-- Flag Reason Block - Terminal Red Style -->
            <div
              v-if="problem.flag_reason"
              class="border border-[var(--terminal-red)] bg-[oklch(0.6_0.2_25/0.08)] p-3"
            >
              <div class="flex items-start gap-2">
                <IconAlertTriangle
                  class="h-5 w-5 text-[var(--terminal-red)] flex-shrink-0 mt-0.5"
                />
                <div class="flex-1">
                  <p
                    class="text-xs font-data text-[var(--terminal-red)] uppercase tracking-wider mb-1"
                  >
                    &gt; FLAG_REASON
                  </p>
                  <p class="text-sm text-[var(--foreground)]">{{ problem.flag_reason }}</p>
                </div>
              </div>
            </div>

            <!-- Moderation Notes Block -->
            <div
              v-if="problem.flag_notes"
              class="border border-[var(--silver-300)] bg-[var(--surface-sunken)] p-3"
            >
              <p class="text-xs font-data text-[var(--silver-500)] uppercase tracking-wider mb-1">
                &gt; MODERATION_NOTES
              </p>
              <p class="text-sm text-[var(--foreground)]">{{ problem.flag_notes }}</p>
            </div>

            <!-- Metadata Row -->
            <div class="flex flex-wrap gap-x-4 gap-y-1 text-xs font-data text-[var(--silver-400)]">
              <span>{{ t('common.reportedBy') }}: {{ problem.flag_reported_by }}</span>
              <span
                >{{ t('common.reportedAt') }}:
                {{ new Date(problem.flag_reported_at || '').toLocaleDateString() }}</span
              >
              <span>{{ t('common.submissions') }}: {{ problem.submission_count }}</span>
              <span>{{ t('common.solutions') }}: {{ problem.solution_count }}</span>
            </div>

            <!-- Tags -->
            <div v-if="problem.tags.length > 0" class="flex flex-wrap gap-1">
              <span
                v-for="tag in problem.tags"
                :key="tag.id"
                class="px-2 py-0.5 border border-[var(--silver-300)] text-xs font-data text-[var(--silver-500)] uppercase"
              >
                {{ tag.label }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- Pagination - Terminal Style -->
      <div
        v-if="totalPages > 1"
        class="mt-4 flex items-center justify-center gap-4 py-3 border border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--surface-sunken)]"
      >
        <Button
          variant="terminal"
          size="sm"
          class="h-8 font-data text-xs border-[var(--silver-300)] hover:border-[var(--terminal-cyan)] hover:text-[var(--terminal-cyan)] disabled:opacity-40"
          :disabled="currentPage === 1"
          @click="handlePreviousPage"
        >
          &lt; PREV
        </Button>
        <span class="font-data text-sm text-[var(--foreground)] tabular-nums">
          {{ String(currentPage).padStart(2, '0') }}/{{ String(totalPages).padStart(2, '0') }}
        </span>
        <Button
          variant="terminal"
          size="sm"
          class="h-8 font-data text-xs border-[var(--silver-300)] hover:border-[var(--terminal-cyan)] hover:text-[var(--terminal-cyan)] disabled:opacity-40"
          :disabled="currentPage === totalPages"
          @click="handleNextPage"
        >
          NEXT &gt;
        </Button>
      </div>
    </div>

    <!-- Single Moderation Dialog - Terminal Style -->
    <Dialog v-model:open="moderationDialogOpen">
      <DialogContent class="terminal-card border-[var(--silver-300)]">
        <DialogHeader
          class="terminal-card-header border-b border-[var(--silver-300)] bg-[var(--surface-sunken)] px-4 py-3 -mx-6 -mt-6"
        >
          <DialogTitle
            class="font-data text-sm uppercase tracking-wider text-[var(--terminal-amber)]"
          >
            &gt; {{ t('moderation.moderateTitle') }}
          </DialogTitle>
          <DialogDescription
            v-if="selectedProblem"
            class="font-data text-xs text-[var(--silver-400)]"
          >
            {{ selectedProblem.title }}
          </DialogDescription>
        </DialogHeader>
        <div class="space-y-4 pt-4">
          <div>
            <Label
              for="status"
              class="text-xs font-data uppercase tracking-wider text-[var(--silver-500)]"
            >
              {{ t('moderation.status') }}
            </Label>
            <Select v-model="moderationStatus">
              <SelectTrigger
                id="status"
                class="mt-2 h-9 font-data text-xs border-[var(--silver-300)] hover:border-[var(--accent-electric)] bg-transparent"
              >
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
            <Label
              for="notes"
              class="text-xs font-data uppercase tracking-wider text-[var(--silver-500)]"
            >
              {{ t('moderation.notes') }}
            </Label>
            <Textarea
              id="notes"
              v-model="moderationNotes"
              :placeholder="t('moderation.notesPlaceholder')"
              rows="4"
              class="mt-2 font-data text-sm border-[var(--silver-300)] hover:border-[var(--accent-electric)] bg-transparent placeholder:text-[var(--silver-400)]"
            />
          </div>
        </div>
        <DialogFooter class="gap-2">
          <Button
            variant="terminal"
            size="sm"
            class="font-data text-xs border-[var(--silver-300)] hover:border-[var(--silver-500)]"
            @click="moderationDialogOpen = false"
          >
            {{ t('common.cancel') }}
          </Button>
          <Button
            variant="terminal"
            size="sm"
            class="font-data text-xs border-[var(--terminal-green)] text-[var(--terminal-green)] hover:bg-[oklch(0.7_0.15_145/0.1)]"
            :disabled="moderating"
            @click="handleModeration"
          >
            <IconCheck v-if="!moderating" class="h-3.5 w-3.5 mr-1.5" />
            {{ moderating ? t('common.saving') : t('common.save') }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <!-- Batch Moderation Dialog - Terminal Style -->
    <Dialog v-model:open="batchModerationDialogOpen">
      <DialogContent class="terminal-card border-[var(--silver-300)]">
        <DialogHeader
          class="terminal-card-header border-b border-[var(--silver-300)] bg-[var(--surface-sunken)] px-4 py-3 -mx-6 -mt-6"
        >
          <DialogTitle
            class="flex items-center gap-2 font-data text-sm uppercase tracking-wider text-[var(--terminal-amber)]"
          >
            <IconChecks class="h-4 w-4" />
            &gt; {{ t('moderation.batchModerateTitle') }}
          </DialogTitle>
          <DialogDescription class="font-data text-xs text-[var(--silver-400)]">
            {{ t('moderation.batchModerateDescription', { count: selectedCount }) }}
          </DialogDescription>
        </DialogHeader>
        <div class="space-y-4 pt-4">
          <div>
            <Label class="text-xs font-data uppercase tracking-wider text-[var(--silver-500)]">
              {{ t('moderation.newStatus') }}
            </Label>
            <div class="mt-2 flex gap-2">
              <Button
                :variant="batchModerationStatus === 'RESOLVED' ? 'default' : 'terminal'"
                :class="[
                  'h-9 font-data text-xs',
                  batchModerationStatus === 'RESOLVED'
                    ? 'border-[var(--terminal-green)] text-[var(--terminal-green)] bg-[oklch(0.7_0.15_145/0.1)]'
                    : 'border-[var(--silver-300)] hover:border-[var(--terminal-green)] hover:text-[var(--terminal-green)]',
                ]"
                size="sm"
                @click="batchModerationStatus = 'RESOLVED'"
              >
                <IconCheck class="h-3.5 w-3.5 mr-1.5" />
                {{ t('moderation.statusResolved') }}
              </Button>
              <Button
                :variant="batchModerationStatus === 'DISMISSED' ? 'default' : 'terminal'"
                :class="[
                  'h-9 font-data text-xs',
                  batchModerationStatus === 'DISMISSED'
                    ? 'border-[var(--terminal-red)] text-[var(--terminal-red)] bg-[oklch(0.6_0.2_25/0.1)]'
                    : 'border-[var(--silver-300)] hover:border-[var(--terminal-red)] hover:text-[var(--terminal-red)]',
                ]"
                size="sm"
                @click="batchModerationStatus = 'DISMISSED'"
              >
                <IconX class="h-3.5 w-3.5 mr-1.5" />
                {{ t('moderation.statusDismissed') }}
              </Button>
            </div>
          </div>
          <div>
            <Label
              for="batch-notes"
              class="text-xs font-data uppercase tracking-wider text-[var(--silver-500)]"
            >
              {{ t('moderation.notes') }}
            </Label>
            <Textarea
              id="batch-notes"
              v-model="batchModerationNotes"
              :placeholder="t('moderation.batchNotesPlaceholder')"
              rows="3"
              class="mt-2 font-data text-sm border-[var(--silver-300)] hover:border-[var(--accent-electric)] bg-transparent placeholder:text-[var(--silver-400)]"
            />
          </div>
        </div>
        <DialogFooter class="gap-2">
          <Button
            variant="terminal"
            size="sm"
            class="font-data text-xs border-[var(--silver-300)] hover:border-[var(--silver-500)]"
            @click="batchModerationDialogOpen = false"
          >
            {{ t('common.cancel') }}
          </Button>
          <Button
            variant="terminal"
            size="sm"
            class="font-data text-xs border-[var(--terminal-green)] text-[var(--terminal-green)] hover:bg-[oklch(0.7_0.15_145/0.1)]"
            :disabled="batchModerating"
            @click="handleBatchModeration"
          >
            <IconChecks v-if="!batchModerating" class="h-3.5 w-3.5 mr-1.5" />
            {{ batchModerating ? t('common.saving') : t('moderation.apply') }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>
