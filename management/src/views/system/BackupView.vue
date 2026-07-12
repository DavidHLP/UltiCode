<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { formatDateTimeByLocale } from '@/i18n/utils'
import { toast } from 'vue-sonner'
import { backupApi } from '@/api/admin/backup'
import type { Backup, BackupStatus } from '@/api/admin/backup'
import { Button } from '@/components/ui/button'
import { SemanticBadge, type SemanticColor } from '@/components/ui/terminal'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  IconDatabase,
  IconDownload,
  IconTrash,
  IconRefresh,
  IconPlus,
  IconLoader2,
  IconAlertTriangle,
  IconCheck,
  IconClock,
  IconX,
} from '@tabler/icons-vue'

const { t } = useI18n()

// State
const backups = ref<Backup[]>([])
const loading = ref(true)
const creating = ref(false)
const selectedBackup = ref<Backup | null>(null)
const showRestoreDialog = ref(false)
const showDeleteDialog = ref(false)
const restoring = ref(false)
const deleting = ref(false)
const restoreConfirmText = ref('')
const isLoaded = ref(false)

// Computed
const completedBackups = computed(() => backups.value.filter((b) => b.status === 'COMPLETED'))

const pendingBackups = computed(() =>
  backups.value.filter((b) => b.status === 'PENDING' || b.status === 'IN_PROGRESS'),
)

// Methods
async function loadBackups() {
  loading.value = true
  try {
    const response = await backupApi.getBackups({ limit: 100 })
    backups.value = response.items
  } catch (error) {
    console.error('Failed to load backups:', error)
    toast.error(t('system.backup.toast.loadFailed'))
  } finally {
    loading.value = false
  }
}

async function createBackup() {
  creating.value = true
  try {
    await backupApi.createBackup({ type: 'FULL' })
    toast.success(t('system.backup.toast.createSuccess'))
    await loadBackups()
  } catch (error) {
    console.error('Failed to create backup:', error)
    toast.error(t('system.backup.toast.createFailed'))
  } finally {
    creating.value = false
  }
}

async function downloadBackup(backup: Backup) {
  try {
    await backupApi.downloadBackup(backup.id)
    toast.success(t('system.backup.toast.downloadSuccess'))
  } catch (error) {
    console.error('Failed to download backup:', error)
    toast.error(t('system.backup.toast.downloadFailed'))
  }
}

function openRestoreDialog(backup: Backup) {
  selectedBackup.value = backup
  showRestoreDialog.value = true
}

function openDeleteDialog(backup: Backup) {
  selectedBackup.value = backup
  showDeleteDialog.value = true
}

async function confirmRestore() {
  if (!selectedBackup.value) return
  if (restoreConfirmText.value !== 'RESTORE') return

  restoring.value = true
  try {
    await backupApi.restoreBackup(selectedBackup.value.id)
    toast.success(t('system.backup.toast.restoreSuccess'))
    showRestoreDialog.value = false
  } catch (error) {
    console.error('Failed to restore backup:', error)
    toast.error(t('system.backup.toast.restoreFailed'))
  } finally {
    restoring.value = false
  }
}

async function confirmDelete() {
  if (!selectedBackup.value) return

  deleting.value = true
  try {
    await backupApi.deleteBackup(selectedBackup.value.id)
    toast.success(t('system.backup.toast.deleteSuccess'))
    showDeleteDialog.value = false
    await loadBackups()
  } catch (error) {
    console.error('Failed to delete backup:', error)
    toast.error(t('system.backup.toast.deleteFailed'))
  } finally {
    deleting.value = false
  }
}

function formatBytes(bytes: number): string {
  if (bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let unitIndex = 0
  let value = bytes

  while (value >= 1024 && unitIndex < units.length - 1) {
    value /= 1024
    unitIndex++
  }

  return `${value.toFixed(1)} ${units[unitIndex]}`
}

function getStatusIcon(status: BackupStatus) {
  switch (status) {
    case 'COMPLETED':
      return IconCheck
    case 'PENDING':
    case 'IN_PROGRESS':
      return IconClock
    case 'FAILED':
      return IconX
    default:
      return IconClock
  }
}

function getStatusColor(status: BackupStatus): string {
  switch (status) {
    case 'COMPLETED':
      return 'text-green-500'
    case 'PENDING':
    case 'IN_PROGRESS':
      return 'text-yellow-500'
    case 'FAILED':
      return 'text-red-500'
    default:
      return 'text-gray-500'
  }
}

function getStatusBadgeColor(status: BackupStatus): SemanticColor {
  switch (status) {
    case 'COMPLETED':
      return 'success'
    case 'FAILED':
      return 'error'
    default:
      return 'neutral'
  }
}

// Lifecycle
onMounted(async () => {
  await loadBackups()
  isLoaded.value = true
})
</script>

<template>
  <div class="relative flex flex-col gap-4 w-full min-w-0">
    <!-- Terminal Header -->
    <div
      :class="[
        'border border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--card)]',
        'transition-all duration-500',
        isLoaded ? 'opacity-100 translate-y-0' : 'opacity-0 -translate-y-2',
      ]"
    >
      <!-- Title Row -->
      <div class="px-4 lg:px-6 py-4 flex items-center justify-between">
        <div class="space-y-1">
          <h1 class="text-xl font-medium tracking-tight text-[var(--foreground)]">
            {{ t('system.backup.title') }}
          </h1>
          <p class="text-xs text-[var(--silver-500)]">{{ t('system.backup.description') }}</p>
        </div>
        <div class="flex items-center gap-2">
          <Button
            variant="terminal"
            size="sm"
            class="font-data text-xs border-[var(--silver-300)] hover:border-[var(--accent-electric)] hover:text-[var(--accent-electric)] transition-colors"
            :disabled="loading"
            @click="loadBackups"
          >
            <IconLoader2 v-if="loading" class="h-3.5 w-3.5 mr-1.5 animate-spin" />
            <IconRefresh v-else class="h-3.5 w-3.5 mr-1.5" />
            <span class="uppercase tracking-wider">{{ t('common.refresh') }}</span>
          </Button>
          <Button
            variant="terminal"
            size="sm"
            class="font-data text-xs border-[var(--silver-300)] hover:border-[var(--accent-electric)] hover:text-[var(--accent-electric)] transition-colors"
            :disabled="creating"
            @click="createBackup"
          >
            <IconLoader2 v-if="creating" class="h-3.5 w-3.5 mr-1.5 animate-spin" />
            <IconPlus v-else class="h-3.5 w-3.5 mr-1.5" />
            <span class="uppercase tracking-wider">{{ t('system.backup.createBackup') }}</span>
          </Button>
        </div>
      </div>
    </div>

    <!-- Main Content Area -->
    <div
      :class="[
        'mt-6 space-y-6 transition-all duration-500 delay-100',
        isLoaded ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-2',
      ]"
    >
      <!-- Stats Cards -->
      <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
        <Card>
          <CardHeader class="pb-2">
            <CardTitle class="text-sm font-medium text-muted-foreground">
              {{ t('system.backup.totalBackups') }}
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div class="text-2xl font-bold">{{ backups.length }}</div>
          </CardContent>
        </Card>
        <Card>
          <CardHeader class="pb-2">
            <CardTitle class="text-sm font-medium text-muted-foreground">
              {{ t('system.backup.completedBackups') }}
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div class="text-2xl font-bold text-green-500">{{ completedBackups.length }}</div>
          </CardContent>
        </Card>
        <Card>
          <CardHeader class="pb-2">
            <CardTitle class="text-sm font-medium text-muted-foreground">
              {{ t('system.backup.pendingBackups') }}
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div class="text-2xl font-bold text-yellow-500">{{ pendingBackups.length }}</div>
          </CardContent>
        </Card>
      </div>

      <!-- Backups List -->
      <Card>
        <CardHeader>
          <CardTitle class="flex items-center gap-2">
            <IconDatabase class="h-5 w-5" />
            {{ t('system.backup.backupList') }}
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div v-if="loading" class="flex items-center justify-center py-8">
            <IconLoader2 class="h-6 w-6 animate-spin text-muted-foreground" />
          </div>

          <div v-else-if="backups.length === 0" class="text-center py-8 text-muted-foreground">
            {{ t('system.backup.noBackups') }}
          </div>

          <div v-else class="space-y-4">
            <div
              v-for="backup in backups"
              :key="backup.id"
              class="flex items-center justify-between p-4 rounded-none border bg-card hover:bg-muted/50 transition-colors"
            >
              <div class="flex items-center gap-4">
                <component
                  :is="getStatusIcon(backup.status)"
                  :class="['h-5 w-5', getStatusColor(backup.status)]"
                />
                <div>
                  <div class="font-medium">{{ backup.filename }}</div>
                  <div class="text-sm text-muted-foreground">
                    {{ t('system.backup.type') }}: {{ backup.type }} |
                    {{ t('system.backup.size') }}: {{ formatBytes(backup.size) }} |
                    {{ t('system.backup.createdAt') }}:
                    {{ formatDateTimeByLocale(backup.created_at) }}
                  </div>
                </div>
              </div>

              <div class="flex items-center gap-2">
                <SemanticBadge :color="getStatusBadgeColor(backup.status)">
                  {{ t(`system.backup.status.${backup.status}`, backup.status) }}
                </SemanticBadge>

                <Button
                  v-if="backup.status === 'COMPLETED'"
                  variant="ghost"
                  size="sm"
                  @click="downloadBackup(backup)"
                >
                  <IconDownload class="h-4 w-4" />
                </Button>

                <Button
                  v-if="backup.status === 'COMPLETED'"
                  variant="ghost"
                  size="sm"
                  @click="openRestoreDialog(backup)"
                >
                  <IconRefresh class="h-4 w-4" />
                </Button>

                <Button
                  variant="ghost"
                  size="sm"
                  class="text-destructive hover:text-destructive"
                  @click="openDeleteDialog(backup)"
                >
                  <IconTrash class="h-4 w-4" />
                </Button>
              </div>
            </div>
          </div>
        </CardContent>
      </Card>

      <!-- Restore Dialog -->
      <Dialog
        v-model:open="showRestoreDialog"
        @update:open="
          (v: boolean) => {
            showRestoreDialog = v
            if (!v) restoreConfirmText = ''
          }
        "
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{{ t('system.backup.restoreBackup') }}</DialogTitle>
          </DialogHeader>
          <div class="py-4">
            <div class="flex items-center gap-2 p-4 bg-yellow-500/10 rounded-none text-yellow-600">
              <IconAlertTriangle class="h-5 w-5" />
              <p class="text-sm">{{ t('system.backup.restoreWarning') }}</p>
            </div>
            <p class="mt-4 text-sm text-muted-foreground">
              {{ t('system.backup.restoreConfirm', { filename: selectedBackup?.filename }) }}
            </p>
            <div class="mt-4 space-y-2">
              <Label for="restore-confirm" class="text-sm text-destructive">
                Type <span class="font-mono font-bold">RESTORE</span> to confirm
              </Label>
              <Input
                id="restore-confirm"
                v-model="restoreConfirmText"
                placeholder="RESTORE"
                class="font-mono"
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" @click="showRestoreDialog = false">
              {{ t('common.cancel') }}
            </Button>
            <Button
              variant="destructive"
              :disabled="restoring || restoreConfirmText !== 'RESTORE'"
              @click="confirmRestore"
            >
              <IconLoader2 v-if="restoring" class="h-4 w-4 mr-1 animate-spin" />
              <IconAlertTriangle v-else class="h-4 w-4 mr-1" />
              {{ t('system.backup.restore') }}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <!-- Delete Dialog -->
      <Dialog v-model:open="showDeleteDialog">
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{{ t('system.backup.deleteBackup') }}</DialogTitle>
          </DialogHeader>
          <div class="py-4">
            <p class="text-sm text-muted-foreground">
              {{ t('system.backup.deleteConfirm', { filename: selectedBackup?.filename }) }}
            </p>
          </div>
          <DialogFooter>
            <Button variant="outline" @click="showDeleteDialog = false">
              {{ t('common.cancel') }}
            </Button>
            <Button variant="destructive" :disabled="deleting" @click="confirmDelete">
              <IconLoader2 v-if="deleting" class="h-4 w-4 mr-1 animate-spin" />
              {{ t('common.delete') }}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  </div>
</template>
