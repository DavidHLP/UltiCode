<template>
  <div class="version-history">
    <div class="version-header">
      <h3>{{ t('problems.versionHistory.title') }}</h3>
      <Button variant="outline" size="sm" @click="loadVersions" :disabled="loading">
        <RefreshCw class="h-4 w-4 mr-2" :class="{ 'animate-spin': loading }" />
        {{ t('common.refresh') }}
      </Button>
    </div>

    <div v-if="loading" class="loading-state">
      <Loader2 class="h-8 w-8 animate-spin text-muted-foreground" />
      <p>{{ t('common.loading') }}</p>
    </div>

    <div v-else-if="versions.length === 0" class="empty-state">
      <History class="h-12 w-12 text-muted-foreground" />
      <p>{{ t('problems.versionHistory.noVersions') }}</p>
    </div>

    <div v-else class="version-list">
      <div
        v-for="version in versions"
        :key="version.id"
        class="version-item"
        :class="{ 'is-current': version.versionNumber === currentVersion }"
      >
        <div class="version-info">
          <div class="version-number">
            <Badge :variant="version.versionNumber === currentVersion ? 'default' : 'secondary'">
              v{{ version.versionNumber }}
            </Badge>
            <Badge v-if="version.changeType === 'create'" variant="outline">Initial</Badge>
            <Badge v-else-if="version.changeType === 'rollback'" variant="destructive"
              >Rollback</Badge
            >
            <Badge v-else variant="outline">Update</Badge>
          </div>
          <div class="version-meta">
            <span class="change-summary">{{ version.changeSummary || 'No description' }}</span>
            <span class="version-date">{{ formatDate(version.createdAt) }}</span>
            <span v-if="version.createdBy" class="version-author">
              {{ t('problems.versionHistory.by') }} {{ version.createdBy }}
            </span>
          </div>
        </div>
        <div class="version-actions">
          <Button
            variant="ghost"
            size="sm"
            @click="viewVersion(version.id)"
            :title="t('problems.versionHistory.viewDetails')"
          >
            <Eye class="h-4 w-4" />
          </Button>
          <Button
            v-if="version.versionNumber !== currentVersion"
            variant="ghost"
            size="sm"
            @click="compareWithCurrent(version.id)"
            :title="t('problems.versionHistory.compare')"
          >
            <GitCompare class="h-4 w-4" />
          </Button>
          <Button
            v-if="version.versionNumber !== currentVersion"
            variant="ghost"
            size="sm"
            @click="confirmRollback(version)"
            :title="t('problems.versionHistory.rollback')"
          >
            <Undo2 class="h-4 w-4" />
          </Button>
        </div>
      </div>

      <div v-if="pagination.totalPages > 1" class="pagination">
        <Button
          variant="outline"
          size="sm"
          :disabled="pagination.page <= 1"
          @click="changePage(pagination.page - 1)"
        >
          {{ t('common.previous') }}
        </Button>
        <span class="page-info"> {{ pagination.page }} / {{ pagination.totalPages }} </span>
        <Button
          variant="outline"
          size="sm"
          :disabled="pagination.page >= pagination.totalPages"
          @click="changePage(pagination.page + 1)"
        >
          {{ t('common.next') }}
        </Button>
      </div>
    </div>

    <!-- Version Detail Dialog -->
    <Dialog v-model:open="showVersionDetail">
      <DialogContent class="max-w-3xl">
        <DialogHeader>
          <DialogTitle
            >{{ t('problems.versionHistory.versionDetails') }} - v{{
              selectedVersion?.versionNumber
            }}</DialogTitle
          >
        </DialogHeader>
        <div v-if="versionDetail" class="version-detail">
          <div class="detail-grid">
            <div class="detail-item">
              <label>{{ t('problems.form.title') }}</label>
              <span>{{ versionDetail.title }}</span>
            </div>
            <div class="detail-item">
              <label>{{ t('problems.form.slug') }}</label>
              <span>{{ versionDetail.slug }}</span>
            </div>
            <div class="detail-item">
              <label>{{ t('problems.form.difficulty') }}</label>
              <Badge :variant="getDifficultyVariant(versionDetail.difficulty)">
                {{ versionDetail.difficulty }}
              </Badge>
            </div>
            <div class="detail-item">
              <label>{{ t('problems.form.isPremium') }}</label>
              <span>{{ versionDetail.isPremium ? t('common.yes') : t('common.no') }}</span>
            </div>
          </div>
          <div v-if="versionDetail.summary" class="detail-section">
            <label>{{ t('problems.form.summary') }}</label>
            <div class="content-box">{{ versionDetail.summary }}</div>
          </div>
          <div v-if="versionDetail.constraints?.length" class="detail-section">
            <label>{{ t('problems.form.constraints') }}</label>
            <ul>
              <li v-for="(c, i) in versionDetail.constraints" :key="i">{{ c }}</li>
            </ul>
          </div>
          <div v-if="versionDetail.tags?.length" class="detail-section">
            <label>{{ t('problems.form.tags') }}</label>
            <div class="tags-list">
              <Badge v-for="tag in versionDetail.tags" :key="tag" variant="secondary">
                {{ tag }}
              </Badge>
            </div>
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" @click="showVersionDetail = false">
            {{ t('common.close') }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <!-- Diff Dialog -->
    <Dialog v-model:open="showDiffDialog">
      <DialogContent class="max-w-4xl">
        <DialogHeader>
          <DialogTitle>{{ t('problems.versionHistory.compareVersions') }}</DialogTitle>
        </DialogHeader>
        <div v-if="versionDiff" class="diff-content">
          <div v-if="versionDiff.diffs.length === 0" class="no-changes">
            {{ t('problems.versionHistory.noChanges') }}
          </div>
          <div v-else class="diff-list">
            <div v-for="diff in versionDiff.diffs" :key="diff.field" class="diff-item">
              <h4>{{ diff.field }}</h4>
              <div class="diff-values">
                <div class="old-value">
                  <label>{{ t('problems.versionHistory.oldValue') }}</label>
                  <pre>{{ JSON.stringify(diff.oldValue, null, 2) }}</pre>
                </div>
                <div class="new-value">
                  <label>{{ t('problems.versionHistory.newValue') }}</label>
                  <pre>{{ JSON.stringify(diff.newValue, null, 2) }}</pre>
                </div>
              </div>
            </div>
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" @click="showDiffDialog = false">
            {{ t('common.close') }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <!-- Rollback Confirmation Dialog -->
    <Dialog v-model:open="showRollbackDialog">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{{ t('problems.versionHistory.rollbackTitle') }}</DialogTitle>
          <DialogDescription>
            {{
              t('problems.versionHistory.rollbackConfirm', {
                version: rollbackVersion?.versionNumber,
              })
            }}
          </DialogDescription>
        </DialogHeader>
        <div class="rollback-form">
          <label>{{ t('problems.versionHistory.rollbackReason') }}</label>
          <Textarea
            v-model="rollbackReason"
            :placeholder="t('problems.versionHistory.rollbackReasonPlaceholder')"
          />
        </div>
        <DialogFooter>
          <Button variant="outline" @click="showRollbackDialog = false">
            {{ t('common.cancel') }}
          </Button>
          <Button variant="destructive" @click="executeRollback" :disabled="rollingBack">
            <Loader2 v-if="rollingBack" class="h-4 w-4 mr-2 animate-spin" />
            {{ t('problems.versionHistory.rollbackButton') }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { toast } from 'vue-sonner'
import {
  problemsApi,
  type ProblemVersion,
  type ProblemVersionDetail,
  type VersionWithDiff,
} from '@/api/admin/problems'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog'
import { Textarea } from '@/components/ui/textarea'
import { RefreshCw, Loader2, History, Eye, GitCompare, Undo2 } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'

const props = defineProps<{
  problemId: string
  currentVersion?: number
}>()

const emit = defineEmits<{
  (e: 'rollback'): void
}>()

const { t } = useI18n()

const loading = ref(false)
const versions = ref<ProblemVersion[]>([])
const pagination = ref({
  total: 0,
  page: 1,
  limit: 10,
  totalPages: 0,
})

const showVersionDetail = ref(false)
const selectedVersionId = ref<string | null>(null)
const versionDetail = ref<ProblemVersionDetail | null>(null)

const showDiffDialog = ref(false)
const versionDiff = ref<VersionWithDiff | null>(null)

const showRollbackDialog = ref(false)
const rollbackVersion = ref<ProblemVersion | null>(null)
const rollbackReason = ref('')
const rollingBack = ref(false)

const selectedVersion = computed(() => {
  if (!selectedVersionId.value) return null
  return versions.value.find((v) => v.id === selectedVersionId.value)
})

async function loadVersions() {
  loading.value = true
  try {
    const response = await problemsApi.getProblemVersions(props.problemId, {
      page: pagination.value.page,
      limit: pagination.value.limit,
    })
    versions.value = response.versions
    pagination.value = response.pagination
  } catch (error) {
    console.error('Failed to load versions:', error)
    toast.error(t('problems.versionHistory.loadError'))
  } finally {
    loading.value = false
  }
}

async function viewVersion(versionId: string) {
  selectedVersionId.value = versionId
  try {
    versionDetail.value = await problemsApi.getProblemVersion(props.problemId, versionId)
    showVersionDetail.value = true
  } catch (error) {
    console.error('Failed to load version details:', error)
    toast.error(t('problems.versionHistory.loadDetailError'))
  }
}

async function compareWithCurrent(versionId: string) {
  try {
    // Get current version first
    const current = versions.value.find((v) => v.versionNumber === props.currentVersion)
    if (!current) return

    versionDiff.value = await problemsApi.getVersionDiff(props.problemId, current.id, versionId)
    showDiffDialog.value = true
  } catch (error) {
    console.error('Failed to compare versions:', error)
    toast.error(t('problems.versionHistory.compareError'))
  }
}

function confirmRollback(version: ProblemVersion) {
  rollbackVersion.value = version
  rollbackReason.value = ''
  showRollbackDialog.value = true
}

async function executeRollback() {
  if (!rollbackVersion.value) return

  rollingBack.value = true
  try {
    await problemsApi.rollbackToVersion(
      props.problemId,
      rollbackVersion.value.id,
      rollbackReason.value,
    )
    toast.success(t('problems.versionHistory.rollbackSuccess'))
    showRollbackDialog.value = false
    emit('rollback')
    await loadVersions()
  } catch (error) {
    console.error('Failed to rollback:', error)
    toast.error(t('problems.versionHistory.rollbackError'))
  } finally {
    rollingBack.value = false
  }
}

function changePage(page: number) {
  pagination.value.page = page
  loadVersions()
}

function formatDate(date: Date | string): string {
  return new Date(date).toLocaleString()
}

function getDifficultyVariant(difficulty: string): 'default' | 'secondary' | 'destructive' {
  switch (difficulty) {
    case 'EASY':
      return 'default'
    case 'MEDIUM':
      return 'secondary'
    case 'HARD':
      return 'destructive'
    default:
      return 'default'
  }
}

onMounted(() => {
  loadVersions()
})
</script>

<style scoped>
.version-history {
  @apply space-y-4;
}

.version-header {
  @apply flex items-center justify-between;
}

.version-header h3 {
  @apply text-lg font-semibold;
}

.loading-state,
.empty-state {
  @apply flex flex-col items-center justify-center py-8 text-muted-foreground;
}

.version-list {
  @apply space-y-2;
}

.version-item {
  @apply flex items-center justify-between p-3 rounded-lg border bg-card;
}

.version-item.is-current {
  @apply border-primary bg-primary/5;
}

.version-info {
  @apply space-y-1;
}

.version-number {
  @apply flex items-center gap-2;
}

.version-meta {
  @apply flex items-center gap-4 text-sm text-muted-foreground;
}

.change-summary {
  @apply font-medium text-foreground;
}

.version-actions {
  @apply flex items-center gap-1;
}

.pagination {
  @apply flex items-center justify-center gap-4 pt-4;
}

.page-info {
  @apply text-sm text-muted-foreground;
}

.version-detail {
  @apply space-y-4;
}

.detail-grid {
  @apply grid grid-cols-2 gap-4;
}

.detail-item {
  @apply space-y-1;
}

.detail-item label {
  @apply text-sm font-medium text-muted-foreground;
}

.detail-section {
  @apply space-y-2;
}

.detail-section label {
  @apply text-sm font-medium text-muted-foreground;
}

.content-box {
  @apply p-3 rounded-lg bg-muted text-sm;
}

.tags-list {
  @apply flex flex-wrap gap-2;
}

.diff-content {
  @apply space-y-4;
}

.no-changes {
  @apply text-center py-8 text-muted-foreground;
}

.diff-list {
  @apply space-y-4;
}

.diff-item {
  @apply space-y-2;
}

.diff-item h4 {
  @apply font-medium;
}

.diff-values {
  @apply grid grid-cols-2 gap-4;
}

.old-value,
.new-value {
  @apply space-y-1;
}

.old-value label,
.new-value label {
  @apply text-xs font-medium text-muted-foreground;
}

.old-value pre,
.new-value pre {
  @apply p-2 rounded text-xs overflow-auto max-h-40;
}

.old-value pre {
  @apply bg-red-100 dark:bg-red-900/20;
}

.new-value pre {
  @apply bg-green-100 dark:bg-green-900/20;
}

.rollback-form {
  @apply space-y-2;
}

.rollback-form label {
  @apply text-sm font-medium;
}
</style>
