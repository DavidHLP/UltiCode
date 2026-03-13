<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { toast } from 'vue-sonner'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import {
  IconRefresh,
  IconEye,
  IconPlayerPlay,
  IconSearch,
  IconFilter,
  IconLoader2,
  IconDatabase,
} from '@tabler/icons-vue'
import {
  submissionsApi,
  type SubmissionListItem,
  type SubmissionDetail,
  type SubmissionStatistics,
  type StatusOption,
} from '@/api/admin/submissions'
import { TerminalBadge } from '@/components/ui/terminal'
import { formatDistanceToNow } from 'date-fns'

const { t } = useI18n()

// Animation state for staggered reveal
const isLoaded = ref(false)

onMounted(() => {
  setTimeout(() => {
    isLoaded.value = true
  }, 100)
})

// State
const submissions = ref<SubmissionListItem[]>([])
const statistics = ref<SubmissionStatistics | null>(null)
const statuses = ref<StatusOption[]>([])
const languages = ref<string[]>([])
const loading = ref(false)
const statsLoading = ref(false)

// Filters
const searchQuery = ref('')
const statusFilter = ref<string>('all')
const languageFilter = ref<string>('all')
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
const totalPages = ref(0)

// Selection
const selectedIds = ref<Set<string>>(new Set())

// Detail dialog
const detailDialogOpen = ref(false)
const selectedSubmission = ref<SubmissionDetail | null>(null)
const detailLoading = ref(false)
const currentRejudgeId = ref<string | null>(null)

// Rejudge dialog
const rejudgeDialogOpen = ref(false)
const rejudging = ref(false)

// Batch rejudge dialog
const batchRejudgeDialogOpen = ref(false)
const batchRejudging = ref(false)

// Computed
const selectedCount = computed(() => selectedIds.value.size)
const isAllSelected = computed(
  () => submissions.value.length > 0 && selectedIds.value.size === submissions.value.length,
)

// Stats for terminal ticker
const stats = computed(() => {
  const stats = statistics.value
  if (!stats) return { total: 0, pending: 0, topLanguage: '-', acceptedRate: '0' }
  const acceptedCount = stats.byStatus.find((s) => s.status === 'ACCEPTED')?.count || 0
  const acceptedRate = stats.total > 0 ? ((acceptedCount / stats.total) * 100).toFixed(1) : '0'
  return {
    total: stats.total,
    pending: stats.pending,
    topLanguage: stats.byLanguage[0]?.language || '-',
    acceptedRate,
  }
})

// Methods
async function loadSubmissions() {
  loading.value = true
  try {
    const response = await submissionsApi.getList({
      page: currentPage.value,
      limit: pageSize.value,
      search: searchQuery.value || undefined,
      status: statusFilter.value === 'all' ? undefined : statusFilter.value,
      language: languageFilter.value === 'all' ? undefined : languageFilter.value,
    })
    submissions.value = response.data
    total.value = response.total
    totalPages.value = response.totalPages
    selectedIds.value.clear()
  } catch (error) {
    console.error('Failed to load submissions:', error)
    toast.error(t('submissions.loadError'))
  } finally {
    loading.value = false
  }
}

async function loadStatistics() {
  statsLoading.value = true
  try {
    statistics.value = await submissionsApi.getStatistics()
  } catch (error) {
    console.error('Failed to load statistics:', error)
  } finally {
    statsLoading.value = false
  }
}

async function loadFilters() {
  try {
    const [statusesRes, languagesRes] = await Promise.all([
      submissionsApi.getStatuses(),
      submissionsApi.getLanguages(),
    ])
    statuses.value = statusesRes
    languages.value = languagesRes
  } catch (error) {
    console.error('Failed to load filters:', error)
  }
}

async function viewSubmission(id: string) {
  detailLoading.value = true
  detailDialogOpen.value = true
  try {
    selectedSubmission.value = await submissionsApi.getById(id)
  } catch (error) {
    console.error('Failed to load submission:', error)
    toast.error(t('submissions.loadDetailError'))
    detailDialogOpen.value = false
  } finally {
    detailLoading.value = false
  }
}

function openRejudgeDialog(id: string) {
  currentRejudgeId.value = id
  rejudgeDialogOpen.value = true
}

async function rejudgeSubmission() {
  if (!currentRejudgeId.value) return
  rejudging.value = true
  try {
    const result = await submissionsApi.rejudge(currentRejudgeId.value)
    if (result.success) {
      toast.success(t('submissions.rejudgeSuccess'))
      await loadSubmissions()
    } else {
      toast.error(t('submissions.rejudgeError', { error: result.error }))
    }
  } catch (error) {
    console.error('Failed to rejudge:', error)
    toast.error(t('submissions.rejudgeError', { error: 'Unknown error' }))
  } finally {
    rejudging.value = false
    rejudgeDialogOpen.value = false
    currentRejudgeId.value = null
  }
}

async function batchRejudge() {
  if (selectedIds.value.size === 0) return

  batchRejudging.value = true
  try {
    const result = await submissionsApi.batchRejudge(Array.from(selectedIds.value))
    if (result.failed === 0) {
      toast.success(t('submissions.batchRejudgeSuccess', { count: result.successful }))
    } else {
      toast.warning(
        t('submissions.batchRejudgePartial', {
          success: result.successful,
          failed: result.failed,
        }),
      )
    }
    selectedIds.value.clear()
    await loadSubmissions()
  } catch (error) {
    console.error('Failed to batch rejudge:', error)
    toast.error(t('submissions.batchRejudgeError'))
  } finally {
    batchRejudging.value = false
    batchRejudgeDialogOpen.value = false
  }
}

function toggleSelection(id: string) {
  if (selectedIds.value.has(id)) {
    selectedIds.value.delete(id)
  } else {
    selectedIds.value.add(id)
  }
}

function toggleSelectAll() {
  if (isAllSelected.value) {
    selectedIds.value.clear()
  } else {
    submissions.value.forEach((s) => selectedIds.value.add(s.id))
  }
}

function getStatusBadgeVariant(
  status: string,
): 'success' | 'warning' | 'error' | 'info' | 'default' {
  if (status === 'ACCEPTED') return 'success'
  if (status === 'PENDING' || status === 'JUDGING') return 'warning'
  if (
    status === 'WRONG_ANSWER' ||
    status === 'TIME_LIMIT_EXCEEDED' ||
    status === 'MEMORY_LIMIT_EXCEEDED' ||
    status === 'RUNTIME_ERROR' ||
    status === 'COMPILE_ERROR'
  )
    return 'error'
  return 'default'
}

function shouldPulse(status: string): boolean {
  return status === 'PENDING' || status === 'JUDGING'
}

function formatRuntime(ms: number): string {
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(2)}s`
}

function formatMemory(kb: number): string {
  if (kb < 1024) return `${kb}KB`
  return `${(kb / 1024).toFixed(1)}MB`
}

function formatDate(date: string): string {
  return formatDistanceToNow(new Date(date), { addSuffix: true })
}

function handleSearch() {
  currentPage.value = 1
  loadSubmissions()
}

// Watchers
watch([statusFilter, languageFilter], () => {
  currentPage.value = 1
  loadSubmissions()
})

// Lifecycle
onMounted(() => {
  loadSubmissions()
  loadStatistics()
  loadFilters()
})
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
      <div class="py-4 flex items-center justify-between">
        <div class="flex items-center gap-4">
          <div class="flex items-center gap-2">
            <span class="terminal-prompt text-base">submissions</span>
            <span class="terminal-cursor" />
          </div>
          <h1 class="text-xl font-medium tracking-tight text-[var(--foreground)]">
            {{ t('submissions.title') }}
          </h1>
        </div>
        <Button
          variant="terminal"
          size="sm"
          class="font-data text-xs border-[var(--silver-300)] hover:border-[var(--accent-electric)] hover:text-[var(--accent-electric)] transition-colors"
          @click="loadSubmissions"
        >
          <IconRefresh class="h-4 w-4 mr-1.5" />
          <span class="uppercase tracking-wider">{{ t('common.refresh') }}</span>
        </Button>
      </div>

      <!-- Stats Ticker -->
      <div
        class="py-2.5 flex items-center gap-6 border-t border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--surface-sunken)]"
      >
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]">total:</span>
          <span class="font-data text-sm text-[var(--terminal-cyan)] tabular-nums">{{
            stats.total.toLocaleString()
          }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]">pending:</span>
          <span class="font-data text-sm text-[var(--terminal-amber)] tabular-nums">{{
            stats.pending
          }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]">top_lang:</span>
          <span class="font-data text-sm text-[var(--terminal-green)]">{{
            stats.topLanguage
          }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]">ac_rate:</span>
          <span class="font-data text-sm text-[var(--terminal-green)] tabular-nums"
            >{{ stats.acceptedRate }}%</span
          >
        </div>
        <div class="ml-auto flex items-center gap-2 text-[var(--silver-400)]">
          <IconDatabase class="h-4 w-4" />
          <span class="text-xs font-data uppercase tracking-wider">submission management</span>
        </div>
      </div>
    </div>

    <!-- Filters - Terminal Style -->
    <div
      :class="[
        'mx-4 lg:mx-6 mt-4 flex flex-wrap items-end gap-3 border border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--card)] p-3',
        'transition-all duration-500 delay-100',
        isLoaded ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-2',
      ]"
    >
      <div class="flex-1 min-w-[200px]">
        <Label class="terminal-label text-[var(--silver-500)]">{{ t('submissions.search') }}</Label>
        <div class="relative mt-1">
          <IconSearch
            class="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-[var(--silver-400)]"
          />
          <Input
            v-model="searchQuery"
            :placeholder="t('submissions.searchPlaceholder')"
            class="pl-9 terminal-input h-9"
            @keyup.enter="handleSearch"
          />
        </div>
      </div>
      <div class="w-[180px]">
        <Label class="terminal-label text-[var(--silver-500)]">{{ t('submissions.status') }}</Label>
        <Select v-model="statusFilter">
          <SelectTrigger class="mt-1 h-9 terminal-input">
            <SelectValue :placeholder="t('submissions.allStatuses')" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">{{ t('submissions.allStatuses') }}</SelectItem>
            <SelectItem v-for="status in statuses" :key="status.key" :value="status.key">
              {{ status.label }}
            </SelectItem>
          </SelectContent>
        </Select>
      </div>
      <div class="w-[150px]">
        <Label class="terminal-label text-[var(--silver-500)]">{{
          t('submissions.language')
        }}</Label>
        <Select v-model="languageFilter">
          <SelectTrigger class="mt-1 h-9 terminal-input">
            <SelectValue :placeholder="t('submissions.allLanguages')" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">{{ t('submissions.allLanguages') }}</SelectItem>
            <SelectItem v-for="lang in languages" :key="lang" :value="lang">
              {{ lang }}
            </SelectItem>
          </SelectContent>
        </Select>
      </div>
      <Button
        variant="terminal"
        size="sm"
        class="h-9 font-data text-xs border-[var(--silver-300)] hover:border-[var(--accent-electric)] hover:text-[var(--accent-electric)]"
        @click="handleSearch"
      >
        <IconFilter class="h-3.5 w-3.5 mr-1.5" />
        <span class="uppercase tracking-wider">{{ t('common.filter') }}</span>
      </Button>
    </div>

    <!-- Bulk Action Bar - Terminal Style -->
    <div
      v-if="selectedCount > 0"
      :class="[
        'mt-4 flex items-center justify-between border border-[var(--terminal-amber)] bg-[oklch(0.75_0.15_85/0.08)] dark:bg-[oklch(0.75_0.15_85/0.15)] p-3',
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
            class="h-8 font-data text-xs border-[var(--silver-300)] hover:border-[var(--terminal-amber)] hover:text-[var(--terminal-amber)]"
            @click="batchRejudgeDialogOpen = true"
            :disabled="batchRejudging"
          >
            <IconPlayerPlay class="h-3.5 w-3.5 mr-1.5" />
            <span class="uppercase tracking-wider">{{ t('submissions.batchRejudge') }}</span>
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

    <!-- Table - Terminal Style -->
    <div
      :class="[
        'flex-1 py-4',
        'transition-all duration-500 delay-200',
        isLoaded ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-2',
      ]"
    >
      <div
        class="border border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--card)]"
      >
        <Table class="terminal-table">
          <TableHeader>
            <TableRow
              class="border-b border-[var(--silver-200)] dark:border-[var(--silver-300)] hover:bg-transparent"
            >
              <TableHead class="w-12">
                <Checkbox
                  :checked="isAllSelected"
                  :indeterminate="selectedCount > 0 && !isAllSelected"
                  @update:checked="toggleSelectAll"
                />
              </TableHead>
              <TableHead class="terminal-label text-[var(--silver-500)]">{{
                t('submissions.id')
              }}</TableHead>
              <TableHead class="terminal-label text-[var(--silver-500)]">{{
                t('submissions.problem')
              }}</TableHead>
              <TableHead class="terminal-label text-[var(--silver-500)]">{{
                t('submissions.user')
              }}</TableHead>
              <TableHead class="terminal-label text-[var(--silver-500)]">{{
                t('submissions.language')
              }}</TableHead>
              <TableHead class="terminal-label text-[var(--silver-500)]">{{
                t('submissions.status')
              }}</TableHead>
              <TableHead class="terminal-label text-[var(--silver-500)]">{{
                t('submissions.runtime')
              }}</TableHead>
              <TableHead class="terminal-label text-[var(--silver-500)]">{{
                t('submissions.memory')
              }}</TableHead>
              <TableHead class="terminal-label text-[var(--silver-500)]">{{
                t('submissions.submittedAt')
              }}</TableHead>
              <TableHead class="terminal-label text-[var(--silver-500)] text-right">{{
                t('common.actions')
              }}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow v-if="loading">
              <TableCell colspan="10" class="text-center py-12">
                <IconLoader2 class="h-6 w-6 animate-spin mx-auto text-[var(--silver-400)]" />
                <p class="mt-2 text-sm text-[var(--silver-500)] font-data">
                  {{ t('common.loading') }}
                </p>
              </TableCell>
            </TableRow>
            <TableRow v-else-if="submissions.length === 0">
              <TableCell colspan="10" class="text-center py-12">
                <p class="text-sm text-[var(--silver-500)] font-data">
                  {{ t('submissions.noSubmissions') }}
                </p>
              </TableCell>
            </TableRow>
            <TableRow
              v-for="submission in submissions"
              :key="submission.id"
              :class="[
                'terminal-table-row border-b border-[var(--silver-200)] dark:border-[var(--silver-300)]',
                selectedIds.has(submission.id) ? 'bg-[oklch(0.75_0.15_85/0.05)]' : '',
              ]"
            >
              <TableCell>
                <Checkbox
                  :checked="selectedIds.has(submission.id)"
                  @update:checked="toggleSelection(submission.id)"
                />
              </TableCell>
              <TableCell class="font-data text-xs text-[var(--terminal-cyan)]">{{
                submission.id.slice(0, 8)
              }}</TableCell>
              <TableCell>
                <div>
                  <div class="font-medium text-sm">{{ submission.problemTitle }}</div>
                  <div class="text-xs text-[var(--silver-400)] font-data">
                    {{ submission.problemSlug }}
                  </div>
                </div>
              </TableCell>
              <TableCell class="text-sm">{{ submission.username }}</TableCell>
              <TableCell>
                <span
                  class="font-data text-xs text-[var(--silver-500)] border border-[var(--silver-200)] dark:border-[var(--silver-300)] px-2 py-0.5 rounded-sm"
                >
                  {{ submission.language }}
                </span>
              </TableCell>
              <TableCell>
                <TerminalBadge
                  :variant="getStatusBadgeVariant(submission.status)"
                  :pulse="shouldPulse(submission.status)"
                  :label="submission.status"
                />
              </TableCell>
              <TableCell class="font-data text-sm tabular-nums">{{
                formatRuntime(submission.runtime)
              }}</TableCell>
              <TableCell class="font-data text-sm tabular-nums">{{
                formatMemory(submission.memory)
              }}</TableCell>
              <TableCell class="text-sm text-[var(--silver-500)]">
                {{ formatDate(submission.createdAt) }}
              </TableCell>
              <TableCell class="text-right">
                <div class="flex justify-end gap-1">
                  <Button
                    variant="terminal"
                    size="sm"
                    class="h-8 w-8 p-0 border-[var(--silver-300)] hover:border-[var(--terminal-cyan)] hover:text-[var(--terminal-cyan)]"
                    @click="viewSubmission(submission.id)"
                  >
                    <IconEye class="h-3.5 w-3.5" />
                  </Button>
                  <Button
                    variant="terminal"
                    size="sm"
                    class="h-8 w-8 p-0 border-[var(--silver-300)] hover:border-[var(--terminal-amber)] hover:text-[var(--terminal-amber)]"
                    @click="openRejudgeDialog(submission.id)"
                  >
                    <IconRefresh class="h-3.5 w-3.5" />
                  </Button>
                </div>
              </TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </div>

      <!-- Pagination - Terminal Style -->
      <div v-if="totalPages > 1" class="mt-4 flex items-center justify-center gap-2">
        <Button
          variant="terminal"
          size="sm"
          class="h-8 font-data text-xs border-[var(--silver-300)] hover:border-[var(--accent-electric)] hover:text-[var(--accent-electric)]"
          :disabled="currentPage === 1"
          @click="
            () => {
              currentPage--
              loadSubmissions()
            }
          "
        >
          &lt;
        </Button>
        <span class="font-data text-sm text-[var(--silver-500)] tabular-nums px-3">
          {{ currentPage }}/{{ totalPages }}
        </span>
        <Button
          variant="terminal"
          size="sm"
          class="h-8 font-data text-xs border-[var(--silver-300)] hover:border-[var(--accent-electric)] hover:text-[var(--accent-electric)]"
          :disabled="currentPage === totalPages"
          @click="
            () => {
              currentPage++
              loadSubmissions()
            }
          "
        >
          &gt;
        </Button>
      </div>
    </div>

    <!-- Detail Dialog - Terminal Style -->
    <Dialog v-model:open="detailDialogOpen">
      <DialogContent
        class="max-w-4xl max-h-[80vh] overflow-y-auto border-[var(--silver-200)] dark:border-[var(--silver-300)]"
      >
        <DialogHeader
          class="border-b border-[var(--silver-200)] dark:border-[var(--silver-300)] pb-4"
        >
          <DialogTitle class="flex items-center gap-2">
            <span class="terminal-prompt text-sm">detail</span>
            <span class="text-lg font-medium">{{ t('submissions.detail') }}</span>
          </DialogTitle>
          <DialogDescription
            v-if="selectedSubmission"
            class="font-data text-sm text-[var(--silver-500)]"
          >
            {{ selectedSubmission.problemTitle }} - {{ selectedSubmission.username }}
          </DialogDescription>
        </DialogHeader>
        <div v-if="detailLoading" class="flex items-center justify-center py-12">
          <IconLoader2 class="h-6 w-6 animate-spin text-[var(--silver-400)]" />
        </div>
        <div v-else-if="selectedSubmission" class="space-y-4 py-4">
          <div class="grid grid-cols-2 md:grid-cols-4 gap-4">
            <div>
              <Label class="terminal-label text-[var(--silver-500)]">{{
                t('submissions.id')
              }}</Label>
              <p class="font-data text-sm text-[var(--terminal-cyan)] mt-1">
                {{ selectedSubmission.id }}
              </p>
            </div>
            <div>
              <Label class="terminal-label text-[var(--silver-500)]">{{
                t('submissions.status')
              }}</Label>
              <div class="mt-1">
                <TerminalBadge
                  :variant="getStatusBadgeVariant(selectedSubmission.status)"
                  :pulse="shouldPulse(selectedSubmission.status)"
                  :label="selectedSubmission.status"
                />
              </div>
            </div>
            <div>
              <Label class="terminal-label text-[var(--silver-500)]">{{
                t('submissions.runtime')
              }}</Label>
              <p class="font-data text-sm mt-1">{{ formatRuntime(selectedSubmission.runtime) }}</p>
            </div>
            <div>
              <Label class="terminal-label text-[var(--silver-500)]">{{
                t('submissions.memory')
              }}</Label>
              <p class="font-data text-sm mt-1">{{ formatMemory(selectedSubmission.memory) }}</p>
            </div>
          </div>
          <div>
            <Label class="terminal-label text-[var(--silver-500)]">{{
              t('submissions.code')
            }}</Label>
            <pre
              class="mt-2 p-4 bg-[var(--surface-sunken)] border border-[var(--silver-200)] dark:border-[var(--silver-300)] overflow-x-auto text-sm font-mono terminal-code-block"
            ><code>{{ selectedSubmission.code }}</code></pre>
          </div>
          <div v-if="selectedSubmission.notes">
            <Label class="terminal-label text-[var(--silver-500)]">{{
              t('submissions.notes')
            }}</Label>
            <p class="mt-1 text-sm text-[var(--silver-600)]">{{ selectedSubmission.notes }}</p>
          </div>
        </div>
        <DialogFooter
          class="border-t border-[var(--silver-200)] dark:border-[var(--silver-300)] pt-4"
        >
          <Button
            variant="terminal"
            size="sm"
            class="font-data text-xs border-[var(--silver-300)] hover:border-[var(--accent-electric)] hover:text-[var(--accent-electric)]"
            @click="detailDialogOpen = false"
          >
            {{ t('common.close') }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <!-- Rejudge Dialog - Terminal Style -->
    <Dialog v-model:open="rejudgeDialogOpen">
      <DialogContent class="border-[var(--silver-200)] dark:border-[var(--silver-300)]">
        <DialogHeader>
          <DialogTitle class="flex items-center gap-2">
            <span class="terminal-prompt text-sm">rejudge</span>
            <span class="text-lg font-medium">{{ t('submissions.rejudgeTitle') }}</span>
          </DialogTitle>
          <DialogDescription class="text-[var(--silver-500)]">
            {{ t('submissions.rejudgeDescription') }}
          </DialogDescription>
        </DialogHeader>
        <DialogFooter class="pt-4">
          <Button
            variant="terminal"
            size="sm"
            class="font-data text-xs border-[var(--silver-300)]"
            @click="rejudgeDialogOpen = false"
          >
            {{ t('common.cancel') }}
          </Button>
          <Button
            variant="terminal"
            size="sm"
            class="font-data text-xs border-[var(--terminal-amber)] text-[var(--terminal-amber)] hover:bg-[oklch(0.75_0.15_85/0.1)]"
            :disabled="rejudging"
            @click="rejudgeSubmission"
          >
            <IconLoader2 v-if="rejudging" class="h-3.5 w-3.5 mr-1.5 animate-spin" />
            {{ t('submissions.rejudge') }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <!-- Batch Rejudge Dialog - Terminal Style -->
    <Dialog v-model:open="batchRejudgeDialogOpen">
      <DialogContent class="border-[var(--silver-200)] dark:border-[var(--silver-300)]">
        <DialogHeader>
          <DialogTitle class="flex items-center gap-2">
            <span class="terminal-prompt text-sm">batch_rejudge</span>
            <span class="text-lg font-medium">{{ t('submissions.batchRejudgeTitle') }}</span>
          </DialogTitle>
          <DialogDescription class="text-[var(--silver-500)]">
            {{ t('submissions.batchRejudgeDescription', { count: selectedCount }) }}
          </DialogDescription>
        </DialogHeader>
        <DialogFooter class="pt-4">
          <Button
            variant="terminal"
            size="sm"
            class="font-data text-xs border-[var(--silver-300)]"
            @click="batchRejudgeDialogOpen = false"
          >
            {{ t('common.cancel') }}
          </Button>
          <Button
            variant="terminal"
            size="sm"
            class="font-data text-xs border-[var(--terminal-amber)] text-[var(--terminal-amber)] hover:bg-[oklch(0.75_0.15_85/0.1)]"
            :disabled="batchRejudging"
            @click="batchRejudge"
          >
            <IconLoader2 v-if="batchRejudging" class="h-3.5 w-3.5 mr-1.5 animate-spin" />
            {{ t('submissions.batchRejudge') }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>

<style scoped>
.terminal-code-block {
  color: var(--foreground);
}

.terminal-code-block code {
  color: inherit;
}

/* Terminal input styling */
:deep(.terminal-input) {
  font-family: 'JetBrains Mono', ui-monospace, monospace;
  font-size: 13px;
  border-radius: 0;
  border: 1px solid var(--silver-200);
  background: var(--surface-sunken);
}

.dark :deep(.terminal-input) {
  border-color: var(--silver-300);
}

:deep(.terminal-input:focus) {
  outline: none;
  border-color: var(--accent-electric);
  box-shadow: 0 0 0 2px var(--accent-electric-glow);
}

/* Terminal table styling */
.terminal-table :deep(th) {
  font-family: 'JetBrains Mono', ui-monospace, monospace;
  font-size: 10px;
  text-transform: uppercase;
  letter-spacing: 0.1em;
  font-weight: 500;
}

.terminal-table :deep(td) {
  padding: 12px 16px;
  vertical-align: middle;
}
</style>
