<script setup lang="ts">

import { ref, watch, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { toast } from 'vue-sonner'
import { useUsersStore } from '@/stores/admin/users'
import { useAvatarUpload } from '@/composables/useAvatarUpload'
import { extractApiErrorMessage } from '@/utils/error'
import { Avatar, AvatarFallback, AvatarImage } from '@ulticode/design-system/ui/avatar'
import { Button } from '@ulticode/design-system/ui/button'
import {
  Mail as IconMail,
  Trophy as IconTrophy,
  Flame as IconFlame,
  Upload as IconUpload,
  Loader2 as IconLoader2,
} from '@lucide/vue'

import BaseDetailDrawer from '@/components/shared/BaseDetailDrawer.vue'
import DetailField from '@ulticode/design-system/DetailField.vue'
import SemanticBadge from '@ulticode/badge-config/SemanticBadge.vue'
import { USER_ROLE_COLOR_MAP } from '@ulticode/badge-config'
import { formatDateByLocale, formatDateTimeByLocale } from '@/i18n/utils'
const { t } = useI18n()

const props = defineProps<{
  open: boolean
  userId: string | null
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  success: []
}>()

const usersStore = useUsersStore()
const loading = ref(false)
const avatarInput = ref<HTMLInputElement | null>(null)
let loadGeneration = 0
const {
  uploading: avatarUploading,
  progress: avatarProgress,
  upload: uploadAvatar,
} = useAvatarUpload(computed(() => props.userId))

async function handleAvatarChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  const targetUserId = props.userId
  if (!file || !targetUserId) return

  try {
    const avatar = await uploadAvatar(file)
    if (props.userId !== targetUserId) return
    if (usersStore.currentUser?.id === targetUserId) {
      usersStore.currentUser = { ...usersStore.currentUser, avatar }
    }
    const refreshed = await loadSelectedUser(targetUserId)
    if (props.userId !== targetUserId) return
    emit('success')
    toast.success(t('users.toast.avatarUploadSuccess'))
    if (!refreshed) {
      // The upload itself succeeded (the returned URL is already applied above);
      // the detail refresh failing must be reported instead of silently leaving
      // the rest of the drawer stale.
      toast.warning(t('users.toast.avatarRefreshWarning'))
    }
  } catch (error) {
    if (props.userId !== targetUserId) return
    toast.error(extractApiErrorMessage(error, t('users.toast.avatarUploadFailed')))
  }
}

// Computed stats for progress display
const acceptanceRate = computed(() => {
  const stats = usersStore.currentUser?.stats
  if (!stats || stats.totalSubmissions === 0) return 0
  return Math.round((stats.acceptedSubmissions / stats.totalSubmissions) * 100)
})

const progressFilled = computed(() => {
  const percent = acceptanceRate.value
  const filledCount = Math.round((percent / 100) * 24)
  return '█'.repeat(filledCount)
})

const progressEmpty = computed(() => {
  const percent = acceptanceRate.value
  const filledCount = Math.round((percent / 100) * 24)
  return '░'.repeat(24 - filledCount)
})

async function loadUser(userId: string | null = props.userId) {
  if (!userId) return null
  loading.value = true
  try {
    return await usersStore.fetchUser(userId, false)
  } finally {
    loading.value = false
  }
}

async function loadSelectedUser(userId: string) {
  const generation = ++loadGeneration
  const user = await loadUser(userId)
  if (props.userId !== userId || generation !== loadGeneration) return null
  if (user) {
    usersStore.currentUser = user
  }
  return user
}

watch(
  () => props.open,
  (newOpen) => {
    const targetUserId = props.userId
    if (newOpen && targetUserId) {
      void loadSelectedUser(targetUserId)
    }
  },
)
</script>

<template>
  <BaseDetailDrawer
    :open="open"
    @update:open="emit('update:open', $event)"
    :loading="loading"
    :entity="usersStore.currentUser"
    :title="t('users.details.title')"
    :description="t('users.details.description')"
    :loading-text="t('common.loading')"
    :not-found-text="t('users.details.notFound')"
  >
    <template #content="{ entity }">
      <div
        v-if="
          (entity.detailStatus ?? entity.degradationStatus) &&
          (entity.detailStatus ?? entity.degradationStatus) !== 'OK'
        "
        role="status"
        class="border border-status-warning-mark bg-[color-mix(in_oklch,_var(--status-warning-mark)_8%,_transparent)] p-3 text-sm"
      >
        {{
          (entity.detailStatus ?? entity.degradationStatus) === 'PARTIAL'
            ? t('users.degradation.partial')
            : t('users.degradation.unavailable')
        }}
      </div>

      <div
        v-if="entity.profileStatus && entity.profileStatus !== 'OK'"
        role="status"
        class="border border-status-warning-mark bg-[color-mix(in_oklch,_var(--status-warning-mark)_8%,_transparent)] p-3 text-sm"
      >
        <strong>{{ t('users.degradation.profile') }}</strong>
        <span v-if="entity.profileReason"> — {{ entity.profileReason }}</span>
      </div>

      <div
        v-if="entity.statsStatus && entity.statsStatus !== 'OK'"
        role="status"
        class="border border-status-warning-mark bg-[color-mix(in_oklch,_var(--status-warning-mark)_8%,_transparent)] p-3 text-sm"
      >
        <strong>{{ t('users.degradation.stats') }}</strong>
        <span v-if="entity.statsReason"> — {{ entity.statsReason }}</span>
      </div>

      <div
        v-if="entity.permissionsStatus && entity.permissionsStatus !== 'OK'"
        role="status"
        class="border border-status-warning-mark bg-[color-mix(in_oklch,_var(--status-warning-mark)_8%,_transparent)] p-3 text-sm"
      >
        <strong>{{ t('users.degradation.permissions') }}</strong>
        <span v-if="entity.permissionsReason"> — {{ entity.permissionsReason }}</span>
      </div>

      <!-- Profile Header - Terminal Style -->
      <div class="border border-border dark:border-foreground bg-card">
        <!-- Header Bar -->
        <div class="border-b border-border dark:border-foreground px-4 py-2 bg-muted">
          <span class="text-sm text-muted-foreground">{{
            t('users.drawer.sections.profile')
          }}</span>
        </div>

        <div class="p-4">
          <div class="flex items-start gap-4">
            <!-- Avatar with status ring -->
            <div
              :class="[
                'relative',
                entity.isBanned
                  ? 'ring-2 ring-status-error-mark ring-offset-2 ring-offset-background'
                  : entity.isActive
                    ? 'ring-2 ring-status-success-mark ring-offset-2 ring-offset-background'
                    : '',
              ]"
            >
              <Avatar class="h-16 w-16">
                <AvatarImage :src="entity.avatar ?? ''" :alt="entity.username" />
                <AvatarFallback>
                  {{ entity.name?.[0] || entity.username[0] }}
                </AvatarFallback>
              </Avatar>
            </div>

            <div class="flex-1 min-w-0">
              <div class="flex items-center gap-2 mb-1">
                <span class="font-medium text-lg truncate">{{
                  entity.name || entity.username
                }}</span>
              </div>
              <div class="flex items-center gap-1.5 text-sm text-muted-foreground mb-2">
                <IconMail class="h-3.5 w-3.5" />
                <span class="font-mono text-xs truncate">{{ entity.email || 'no-email' }}</span>
              </div>
              <div class="flex flex-wrap gap-2">
                <SemanticBadge
                  :color="USER_ROLE_COLOR_MAP[entity.role] ?? 'neutral'"
                  :label="t(`users.filters.role.${entity.role}`, entity.role)"
                />
                <SemanticBadge
                  :color="entity.isBanned ? 'error' : entity.isActive ? 'success' : 'neutral'"
                  :label="
                    entity.isBanned
                      ? t('users.status.banned')
                      : entity.isActive
                        ? t('users.status.active')
                        : t('users.status.inactive')
                  "
                  :dot="entity.isActive"
                  :pulse="entity.isActive"
                />
              </div>
            </div>
          </div>
          <div class="mt-3 flex flex-wrap items-center gap-2">
            <input
              ref="avatarInput"
              type="file"
              accept=".jpg,.jpeg,.png,.gif,.webp"
              class="sr-only"
              :disabled="avatarUploading"
              :aria-label="t('users.actions.changeAvatar')"
              @change="handleAvatarChange"
            />
            <Button
              type="button"
              size="sm"
              variant="outline"
              :disabled="avatarUploading"
              @click="avatarInput?.click()"
            >
              <IconLoader2 v-if="avatarUploading" class="mr-1 h-4 w-4 animate-spin" />
              <IconUpload v-else class="mr-1 h-4 w-4" />
              {{ t('users.actions.changeAvatar') }}
            </Button>
            <span
              v-if="avatarUploading"
              role="status"
              aria-live="polite"
              class="font-mono text-xs text-muted-foreground"
            >
              {{ t('users.actions.avatarUploading', { progress: avatarProgress }) }}
            </span>
          </div>
        </div>
      </div>

      <!-- Performance Stats - Terminal Style -->
      <div
        v-if="entity.stats && (!entity.statsStatus || entity.statsStatus === 'OK')"
        class="border border-border dark:border-foreground bg-card"
      >
        <div class="border-b border-border dark:border-foreground px-4 py-2 bg-muted">
          <span class="text-sm text-muted-foreground">{{
            t('users.drawer.sections.performance')
          }}</span>
        </div>

        <div class="p-4 space-y-4">
          <!-- Summary Stats -->
          <div class="grid grid-cols-2 gap-3">
            <div
              class="flex items-center gap-3 p-3 border border-border dark:border-foreground bg-muted"
            >
              <IconTrophy class="h-5 w-5 text-status-warning-mark" />
              <div>
                <div class="font-mono text-lg tabular-nums text-foreground">
                  {{ entity.stats?.totalSolutions ?? 0 }}
                </div>
                <div class="text-sm font-medium">{{ $t('users.stats.solutions') }}</div>
              </div>
            </div>
            <div
              class="flex items-center gap-3 p-3 border border-border dark:border-foreground bg-muted"
            >
              <IconFlame class="h-5 w-5 text-status-error-mark" />
              <div>
                <div class="font-mono text-lg tabular-nums text-foreground">
                  {{ entity.stats?.streak ?? 0 }}
                </div>
                <div class="text-sm font-medium">{{ $t('users.stats.streak') }}</div>
              </div>
            </div>
            <div
              class="flex items-center gap-3 p-3 border border-border dark:border-foreground bg-muted"
            >
              <div class="h-5 w-5 flex items-center justify-center">
                <span class="font-mono text-sm text-foreground">∑</span>
              </div>
              <div>
                <div class="font-mono text-lg tabular-nums text-foreground">
                  {{ entity.stats?.totalSubmissions ?? 0 }}
                </div>
                <div class="text-sm font-medium">{{ $t('users.stats.submissions') }}</div>
              </div>
            </div>
            <div
              class="flex items-center gap-3 p-3 border border-border dark:border-foreground bg-muted"
            >
              <div class="h-5 w-5 flex items-center justify-center">
                <span class="font-mono text-sm text-foreground">✓</span>
              </div>
              <div>
                <div class="font-mono text-lg tabular-nums text-foreground">
                  {{ entity.stats?.acceptedSubmissions ?? 0 }}
                </div>
                <div class="text-sm font-medium">{{ $t('users.stats.accepted') }}</div>
              </div>
            </div>
          </div>

          <!-- Submission Progress Bar -->
          <div
            class="space-y-2"
            v-if="entity.stats && (!entity.statsStatus || entity.statsStatus === 'OK')"
          >
            <div class="flex items-center justify-between">
              <span class="font-mono text-xs text-muted-foreground">{{
                $t('users.stats.acceptanceRate')
              }}</span>
              <span class="font-mono text-xs text-muted-foreground tabular-nums"
                >{{ acceptanceRate }}%</span
              >
            </div>
            <div class="font-mono tabular-nums text-xs">
              <span class="text-foreground">{{ progressFilled }}</span>
              <span class="text-muted-foreground">{{ progressEmpty }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Permissions - only a proven snapshot is rendered as permissions. -->
      <div
        v-if="
          entity.permissions && (!entity.permissionsStatus || entity.permissionsStatus === 'OK')
        "
        class="border border-border dark:border-foreground bg-card"
      >
        <div class="border-b border-border dark:border-foreground px-4 py-2 bg-muted">
          <span class="text-sm text-muted-foreground">{{
            t('users.drawer.sections.permissions')
          }}</span>
        </div>
        <div class="p-4">
          <ul v-if="entity.permissions.length" class="space-y-1">
            <li
              v-for="permission in entity.permissions"
              :key="`${permission.action}:${permission.resource}`"
              class="font-mono text-xs text-foreground"
            >
              {{ permission.action }}:{{ permission.resource }}
            </li>
          </ul>
          <span v-else class="text-sm text-muted-foreground">
            {{ t('users.degradation.noPermissions') }}
          </span>
        </div>
      </div>

      <!-- Account Information - Terminal Style -->
      <div class="border border-border dark:border-foreground bg-card">
        <div class="border-b border-border dark:border-foreground px-4 py-2 bg-muted">
          <span class="text-sm text-muted-foreground">{{
            t('users.drawer.sections.account')
          }}</span>
        </div>

        <div class="p-4">
          <div class="grid grid-cols-2 gap-4">
            <DetailField :label="$t('users.columns.username')" :value="entity.username" />
            <DetailField :label="$t('users.columns.role')">
              <SemanticBadge
                :color="USER_ROLE_COLOR_MAP[entity.role] ?? 'neutral'"
                :label="t(`users.filters.role.${entity.role}`, entity.role)"
              />
            </DetailField>
            <DetailField :label="$t('users.columns.joined')">
              <span class="font-mono text-sm tabular-nums">
                {{ formatDateByLocale(entity.joinedAt) }}
              </span>
            </DetailField>
            <DetailField :label="$t('users.columns.lastLogin')">
              <span v-if="entity.lastLoginAt" class="font-mono text-sm tabular-nums">
                {{ formatDateByLocale(entity.lastLoginAt) }}
              </span>
              <span v-else class="text-muted-foreground italic">{{ $t('users.stats.never') }}</span>
            </DetailField>
          </div>
        </div>
      </div>

      <!-- Ban Information -->
      <div
        v-if="entity.isBanned"
        class="border border-status-error-mark bg-[color-mix(in_oklch,_var(--status-error-mark)_8%,_transparent)]"
      >
        <div
          class="border-b border-status-error-mark px-4 py-2 bg-[color-mix(in_oklch,_var(--status-error-mark)_12%,_transparent)]"
        >
          <span class="text-sm text-muted-foreground text-foreground">{{
            $t('users.drawer.sections.banInformation')
          }}</span>
        </div>

        <div class="p-4 space-y-3">
          <DetailField :label="$t('users.form.banReason')">
            <span class="text-sm italic text-foreground">
              {{ entity.banReason || $t('users.form.noReasonProvided') }}
            </span>
          </DetailField>
          <DetailField :label="$t('users.columns.bannedUntil')">
            <span class="font-mono text-sm tabular-nums text-foreground">
              {{
                entity.bannedUntil
                  ? formatDateTimeByLocale(entity.bannedUntil)
                  : $t('users.form.unknown')
              }}
            </span>
          </DetailField>
        </div>
      </div>
    </template>
  </BaseDetailDrawer>
</template>
