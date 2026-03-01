<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { toast } from 'vue-sonner'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
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
  IconCode,
  IconClock,
  IconDatabase,
  IconChecks,
  IconEye,
  IconPlayerPlay,
  IconSearch,
  IconFilter,
  IconLoader2,
} from '@tabler/icons-vue'
import {
  submissionsApi,
  type SubmissionListItem,
  type SubmissionDetail,
  type SubmissionStatistics,
  type StatusOption,
} from '@/api/admin/submissions'
import { formatDistanceToNow } from 'date-fns'

const { t } = useI18n()

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

async function rejudgeSubmission(id: string) {
  rejudging.value = true
  try {
    const result = await submissionsApi.rejudge(id)
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
): 'default' | 'secondary' | 'destructive' | 'outline' {
  if (status === 'ACCEPTED') return 'default'
  if (status === 'PENDING' || status === 'JUDGING') return 'secondary'
  if (
    status === 'WRONG_ANSWER' ||
    status === 'TIME_LIMIT_EXCEEDED' ||
    status === 'MEMORY_LIMIT_EXCEEDED'
  )
    return 'destructive'
  return 'outline'
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
  <div class="space-y-6">
    <!-- Header -->
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-3xl font-bold tracking-tight">{{ t('submissions.title') }}</h1>
        <p class="text-muted-foreground mt-1">{{ t('submissions.description') }}</p>
      </div>
      <Button @click="loadSubmissions">
        <IconRefresh class="h-4 w-4 mr-2" />
        {{ t('common.refresh') }}
      </Button>
    </div>

    <!-- Statistics -->
    <div v-if="statistics" class="grid grid-cols-1 md:grid-cols-4 gap-4">
      <Card>
        <CardHeader class="flex flex-row items-center justify-between space-y-0 pb-2">
          <CardTitle class="text-sm font-medium">{{ t('submissions.totalSubmissions') }}</CardTitle>
          <IconCode class="h-4 w-4 text-muted-foreground" />
        </CardHeader>
        <CardContent>
          <div class="text-2xl font-bold">{{ statistics.total.toLocaleString() }}</div>
          <p class="text-xs text-muted-foreground">
            {{ t('submissions.last24h', { count: statistics.last24h }) }}
          </p>
        </CardContent>
      </Card>
      <Card>
        <CardHeader class="flex flex-row items-center justify-between space-y-0 pb-2">
          <CardTitle class="text-sm font-medium">{{ t('submissions.pending') }}</CardTitle>
          <IconClock class="h-4 w-4 text-muted-foreground" />
        </CardHeader>
        <CardContent>
          <div class="text-2xl font-bold">{{ statistics.pending }}</div>
          <p class="text-xs text-muted-foreground">{{ t('submissions.inQueue') }}</p>
        </CardContent>
      </Card>
      <Card>
        <CardHeader class="flex flex-row items-center justify-between space-y-0 pb-2">
          <CardTitle class="text-sm font-medium">{{ t('submissions.topLanguage') }}</CardTitle>
          <IconDatabase class="h-4 w-4 text-muted-foreground" />
        </CardHeader>
        <CardContent>
          <div class="text-2xl font-bold">{{ statistics.byLanguage[0]?.language || '-' }}</div>
          <p class="text-xs text-muted-foreground">
            {{ t('submissions.submissionsCount', { count: statistics.byLanguage[0]?.count || 0 }) }}
          </p>
        </CardContent>
      </Card>
      <Card>
        <CardHeader class="flex flex-row items-center justify-between space-y-0 pb-2">
          <CardTitle class="text-sm font-medium">{{ t('submissions.acceptedRate') }}</CardTitle>
          <IconChecks class="h-4 w-4 text-muted-foreground" />
        </CardHeader>
        <CardContent>
          <div class="text-2xl font-bold">
            {{
              statistics.total > 0
                ? (
                    ((statistics.byStatus.find((s) => s.status === 'ACCEPTED')?.count || 0) /
                      statistics.total) *
                    100
                  ).toFixed(1)
                : 0
            }}%
          </div>
          <p class="text-xs text-muted-foreground">{{ t('submissions.acceptedRateDesc') }}</p>
        </CardContent>
      </Card>
    </div>

    <!-- Filters -->
    <Card>
      <CardContent class="pt-6">
        <div class="flex flex-wrap gap-4 items-end">
          <div class="flex-1 min-w-[200px]">
            <Label>{{ t('submissions.search') }}</Label>
            <div class="relative mt-1">
              <IconSearch
                class="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground"
              />
              <Input
                v-model="searchQuery"
                :placeholder="t('submissions.searchPlaceholder')"
                class="pl-9"
                @keyup.enter="handleSearch"
              />
            </div>
          </div>
          <div class="w-[180px]">
            <Label>{{ t('submissions.status') }}</Label>
            <Select v-model="statusFilter">
              <SelectTrigger class="mt-1">
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
            <Label>{{ t('submissions.language') }}</Label>
            <Select v-model="languageFilter">
              <SelectTrigger class="mt-1">
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
          <Button @click="handleSearch">
            <IconFilter class="h-4 w-4 mr-2" />
            {{ t('common.filter') }}
          </Button>
        </div>
      </CardContent>
    </Card>

    <!-- Batch Actions -->
    <div
      v-if="selectedCount > 0"
      class="flex items-center gap-4 p-4 bg-primary/10 rounded-lg border border-primary/20"
    >
      <span class="text-sm font-medium">
        {{ t('submissions.selectedCount', { count: selectedCount }) }}
      </span>
      <Button variant="outline" size="sm" @click="batchRejudgeDialogOpen = true">
        <IconPlayerPlay class="h-4 w-4 mr-1" />
        {{ t('submissions.batchRejudge') }}
      </Button>
      <Button variant="ghost" size="sm" @click="selectedIds.clear()">
        {{ t('common.clearSelection') }}
      </Button>
    </div>

    <!-- Table -->
    <Card>
      <CardContent class="p-0">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead class="w-12">
                <Checkbox
                  :checked="isAllSelected"
                  :indeterminate="selectedCount > 0 && !isAllSelected"
                  @update:checked="toggleSelectAll"
                />
              </TableHead>
              <TableHead>{{ t('submissions.id') }}</TableHead>
              <TableHead>{{ t('submissions.problem') }}</TableHead>
              <TableHead>{{ t('submissions.user') }}</TableHead>
              <TableHead>{{ t('submissions.language') }}</TableHead>
              <TableHead>{{ t('submissions.status') }}</TableHead>
              <TableHead>{{ t('submissions.runtime') }}</TableHead>
              <TableHead>{{ t('submissions.memory') }}</TableHead>
              <TableHead>{{ t('submissions.submittedAt') }}</TableHead>
              <TableHead class="text-right">{{ t('common.actions') }}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow v-if="loading">
              <TableCell colspan="10" class="text-center py-12">
                <IconLoader2 class="h-6 w-6 animate-spin mx-auto text-muted-foreground" />
                <p class="mt-2 text-muted-foreground">{{ t('common.loading') }}</p>
              </TableCell>
            </TableRow>
            <TableRow v-else-if="submissions.length === 0">
              <TableCell colspan="10" class="text-center py-12">
                <p class="text-muted-foreground">{{ t('submissions.noSubmissions') }}</p>
              </TableCell>
            </TableRow>
            <TableRow
              v-for="submission in submissions"
              :key="submission.id"
              :class="{ 'bg-primary/5': selectedIds.has(submission.id) }"
            >
              <TableCell>
                <Checkbox
                  :checked="selectedIds.has(submission.id)"
                  @update:checked="toggleSelection(submission.id)"
                />
              </TableCell>
              <TableCell class="font-mono text-xs">{{ submission.id.slice(0, 8) }}</TableCell>
              <TableCell>
                <div>
                  <div class="font-medium">{{ submission.problemTitle }}</div>
                  <div class="text-xs text-muted-foreground">{{ submission.problemSlug }}</div>
                </div>
              </TableCell>
              <TableCell>{{ submission.username }}</TableCell>
              <TableCell>
                <Badge variant="outline">{{ submission.language }}</Badge>
              </TableCell>
              <TableCell>
                <Badge :variant="getStatusBadgeVariant(submission.status)">
                  {{ submission.status }}
                </Badge>
              </TableCell>
              <TableCell>{{ formatRuntime(submission.runtime) }}</TableCell>
              <TableCell>{{ formatMemory(submission.memory) }}</TableCell>
              <TableCell class="text-sm text-muted-foreground">
                {{ formatDate(submission.createdAt) }}
              </TableCell>
              <TableCell class="text-right">
                <div class="flex justify-end gap-2">
                  <Button variant="ghost" size="sm" @click="viewSubmission(submission.id)">
                    <IconEye class="h-4 w-4" />
                  </Button>
                  <Button variant="ghost" size="sm" @click="rejudgeDialogOpen = true">
                    <IconRefresh class="h-4 w-4" />
                  </Button>
                </div>
              </TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </CardContent>
    </Card>

    <!-- Pagination -->
    <div v-if="totalPages > 1" class="flex items-center justify-center gap-2">
      <Button
        variant="outline"
        :disabled="currentPage === 1"
        @click="
          () => {
            currentPage--
            loadSubmissions()
          }
        "
      >
        {{ t('common.previous') }}
      </Button>
      <span class="text-sm text-muted-foreground">
        {{ t('common.page') }} {{ currentPage }} / {{ totalPages }}
      </span>
      <Button
        variant="outline"
        :disabled="currentPage === totalPages"
        @click="
          () => {
            currentPage++
            loadSubmissions()
          }
        "
      >
        {{ t('common.next') }}
      </Button>
    </div>

    <!-- Detail Dialog -->
    <Dialog v-model:open="detailDialogOpen">
      <DialogContent class="max-w-4xl max-h-[80vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{{ t('submissions.detail') }}</DialogTitle>
          <DialogDescription v-if="selectedSubmission">
            {{ selectedSubmission.problemTitle }} - {{ selectedSubmission.username }}
          </DialogDescription>
        </DialogHeader>
        <div v-if="detailLoading" class="flex items-center justify-center py-12">
          <IconLoader2 class="h-6 w-6 animate-spin" />
        </div>
        <div v-else-if="selectedSubmission" class="space-y-4">
          <div class="grid grid-cols-2 gap-4">
            <div>
              <Label class="text-muted-foreground">{{ t('submissions.id') }}</Label>
              <p class="font-mono text-sm">{{ selectedSubmission.id }}</p>
            </div>
            <div>
              <Label class="text-muted-foreground">{{ t('submissions.status') }}</Label>
              <div>
                <Badge :variant="getStatusBadgeVariant(selectedSubmission.status)">
                  {{ selectedSubmission.status }}
                </Badge>
              </div>
            </div>
            <div>
              <Label class="text-muted-foreground">{{ t('submissions.runtime') }}</Label>
              <p>{{ formatRuntime(selectedSubmission.runtime) }}</p>
            </div>
            <div>
              <Label class="text-muted-foreground">{{ t('submissions.memory') }}</Label>
              <p>{{ formatMemory(selectedSubmission.memory) }}</p>
            </div>
          </div>
          <div>
            <Label class="text-muted-foreground">{{ t('submissions.code') }}</Label>
            <pre
              class="mt-2 p-4 bg-muted rounded-lg overflow-x-auto text-sm font-mono"
            ><code>{{ selectedSubmission.code }}</code></pre>
          </div>
          <div v-if="selectedSubmission.notes">
            <Label class="text-muted-foreground">{{ t('submissions.notes') }}</Label>
            <p class="mt-1 text-sm">{{ selectedSubmission.notes }}</p>
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" @click="detailDialogOpen = false">
            {{ t('common.close') }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <!-- Rejudge Dialog -->
    <Dialog v-model:open="rejudgeDialogOpen">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{{ t('submissions.rejudgeTitle') }}</DialogTitle>
          <DialogDescription>{{ t('submissions.rejudgeDescription') }}</DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline" @click="rejudgeDialogOpen = false">
            {{ t('common.cancel') }}
          </Button>
          <Button :disabled="rejudging" @click="rejudgeSubmission">
            <IconLoader2 v-if="rejudging" class="h-4 w-4 mr-2 animate-spin" />
            {{ t('submissions.rejudge') }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <!-- Batch Rejudge Dialog -->
    <Dialog v-model:open="batchRejudgeDialogOpen">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{{ t('submissions.batchRejudgeTitle') }}</DialogTitle>
          <DialogDescription>
            {{ t('submissions.batchRejudgeDescription', { count: selectedCount }) }}
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline" @click="batchRejudgeDialogOpen = false">
            {{ t('common.cancel') }}
          </Button>
          <Button :disabled="batchRejudging" @click="batchRejudge">
            <IconLoader2 v-if="batchRejudging" class="h-4 w-4 mr-2 animate-spin" />
            {{ t('submissions.batchRejudge') }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>
