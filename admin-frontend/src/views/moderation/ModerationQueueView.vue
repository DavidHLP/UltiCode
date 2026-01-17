<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { toast } from 'vue-sonner'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
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
} from '@tabler/icons-vue'
import { problemsApi, type Problem } from '@/api/admin/problems'

const { t } = useI18n()
const router = useRouter()

type FlagStatus = 'PENDING' | 'REVIEWED' | 'RESOLVED' | 'DISMISSED'

const flaggedProblems = ref<Problem[]>([])
const loading = ref(false)
const statusFilter = ref<FlagStatus | ''>('')
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
const totalPages = ref(0)

const selectedProblem = ref<Problem | null>(null)
const moderationDialogOpen = ref(false)
const moderationNotes = ref('')
const moderationStatus = ref<FlagStatus>('REVIEWED')
const moderating = ref(false)

async function loadFlaggedProblems() {
  loading.value = true
  try {
    const response = await problemsApi.getFlaggedProblems({
      page: currentPage.value,
      limit: pageSize.value,
      status: statusFilter.value || undefined,
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
  moderationNotes.value = ''
  moderationStatus.value = 'REVIEWED'
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

function viewProblem(problem: Problem) {
  router.push(`/admin/problems/${problem.id}`)
}

function getStatusBadgeVariant(status: FlagStatus | null) {
  switch (status) {
    case 'PENDING':
      return 'destructive'
    case 'REVIEWED':
      return 'default'
    case 'RESOLVED':
      return 'outline'
    case 'DISMISSED':
      return 'secondary'
    default:
      return 'default'
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

const filteredProblems = computed(() => {
  return flaggedProblems.value
})

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
            <SelectItem value="">{{ t('moderation.allStatuses') }}</SelectItem>
            <SelectItem value="PENDING">{{ t('moderation.statusPending') }}</SelectItem>
            <SelectItem value="REVIEWED">{{ t('moderation.statusReviewed') }}</SelectItem>
            <SelectItem value="RESOLVED">{{ t('moderation.statusResolved') }}</SelectItem>
            <SelectItem value="DISMISSED">{{ t('moderation.statusDismissed') }}</SelectItem>
          </SelectContent>
        </Select>
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
      <Card
        v-for="problem in filteredProblems"
        :key="problem.id"
        class="hover:shadow-md transition-shadow"
      >
        <CardHeader>
          <div class="flex items-start justify-between">
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
      <Button
        variant="outline"
        :disabled="currentPage === 1"
        @click="
          currentPage--
          loadFlaggedProblems()
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
          currentPage++
          loadFlaggedProblems()
        "
      >
        {{ t('common.next') }}
      </Button>
    </div>

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
  </div>
</template>
