<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useUsersStore } from '@/stores/admin/users'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { IconMail, IconTrophy, IconFlame } from '@tabler/icons-vue'
import BaseDetailDrawer from '@/components/shared/BaseDetailDrawer.vue'
import { DataBlock, SemanticBadge, USER_ROLE_COLOR_MAP } from '@/components/ui/terminal'
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

async function loadUser() {
  if (!props.userId) return
  loading.value = true
  try {
    await usersStore.fetchUser(props.userId)
  } finally {
    loading.value = false
  }
}

watch(
  () => props.open,
  (newOpen) => {
    if (newOpen && props.userId) {
      loadUser()
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
        class="border border-[var(--status-warning-mark)] bg-[color-mix(in_oklch,_var(--status-warning-mark)_8%,_transparent)] p-3 text-sm"
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
        class="border border-[var(--status-warning-mark)] bg-[color-mix(in_oklch,_var(--status-warning-mark)_8%,_transparent)] p-3 text-sm"
      >
        <strong>{{ t('users.degradation.profile') }}</strong>
        <span v-if="entity.profileReason"> — {{ entity.profileReason }}</span>
      </div>

      <div
        v-if="entity.statsStatus && entity.statsStatus !== 'OK'"
        role="status"
        class="border border-[var(--status-warning-mark)] bg-[color-mix(in_oklch,_var(--status-warning-mark)_8%,_transparent)] p-3 text-sm"
      >
        <strong>{{ t('users.degradation.stats') }}</strong>
        <span v-if="entity.statsReason"> — {{ entity.statsReason }}</span>
      </div>

      <div
        v-if="entity.permissionsStatus && entity.permissionsStatus !== 'OK'"
        role="status"
        class="border border-[var(--status-warning-mark)] bg-[color-mix(in_oklch,_var(--status-warning-mark)_8%,_transparent)] p-3 text-sm"
      >
        <strong>{{ t('users.degradation.permissions') }}</strong>
        <span v-if="entity.permissionsReason"> — {{ entity.permissionsReason }}</span>
      </div>

      <!-- Profile Header - Terminal Style -->
      <div
        class="border border-[var(--border-subtle)] dark:border-[var(--foreground-strong)] bg-[var(--card)]"
      >
        <!-- Header Bar -->
        <div
          class="border-b border-[var(--border-subtle)] dark:border-[var(--foreground-strong)] px-4 py-2 bg-[var(--surface-sunken)]"
        >
          <span class="terminal-comment">{{ t('users.drawer.sections.profile') }}</span>
        </div>

        <div class="p-4">
          <div class="flex items-start gap-4">
            <!-- Avatar with status ring -->
            <div
              :class="[
                'relative',
                entity.isBanned
                  ? 'ring-2 ring-[var(--status-error-mark)] ring-offset-2 ring-offset-background'
                  : entity.isActive
                    ? 'ring-2 ring-[var(--status-success-mark)] ring-offset-2 ring-offset-background'
                    : '',
              ]"
            >
              <Avatar class="h-16 w-16 rounded-none">
                <AvatarImage :src="entity.avatar ?? ''" :alt="entity.username" />
                <AvatarFallback
                  class="font-data text-lg bg-[var(--surface-highlight)] dark:bg-[var(--foreground-strong)]"
                >
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
              <div class="flex items-center gap-1.5 text-sm text-[var(--foreground-muted)] mb-2">
                <IconMail class="h-3.5 w-3.5" />
                <span class="font-data text-xs truncate">{{ entity.email || 'no-email' }}</span>
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
        </div>
      </div>

      <!-- Performance Stats - Terminal Style -->
      <div
        v-if="entity.stats && (!entity.statsStatus || entity.statsStatus === 'OK')"
        class="border border-[var(--border-subtle)] dark:border-[var(--foreground-strong)] bg-[var(--card)]"
      >
        <div
          class="border-b border-[var(--border-subtle)] dark:border-[var(--foreground-strong)] px-4 py-2 bg-[var(--surface-sunken)]"
        >
          <span class="terminal-comment">{{ t('users.drawer.sections.performance') }}</span>
        </div>

        <div class="p-4 space-y-4">
          <!-- Summary Stats -->
          <div class="grid grid-cols-2 gap-3">
            <div
              class="flex items-center gap-3 p-3 border border-[var(--border-subtle)] dark:border-[var(--foreground-strong)] bg-[var(--surface-sunken)]"
            >
              <IconTrophy class="h-5 w-5 text-[var(--status-warning-mark)]" />
              <div>
                <div class="font-data text-lg tabular-nums text-[var(--foreground)]">
                  {{ entity.stats?.totalSolutions ?? 0 }}
                </div>
                <div class="terminal-label">{{ $t('users.stats.solutions') }}</div>
              </div>
            </div>
            <div
              class="flex items-center gap-3 p-3 border border-[var(--border-subtle)] dark:border-[var(--foreground-strong)] bg-[var(--surface-sunken)]"
            >
              <IconFlame class="h-5 w-5 text-[var(--status-error-mark)]" />
              <div>
                <div class="font-data text-lg tabular-nums text-[var(--foreground)]">
                  {{ entity.stats?.streak ?? 0 }}
                </div>
                <div class="terminal-label">{{ $t('users.stats.streak') }}</div>
              </div>
            </div>
            <div
              class="flex items-center gap-3 p-3 border border-[var(--border-subtle)] dark:border-[var(--foreground-strong)] bg-[var(--surface-sunken)]"
            >
              <div class="h-5 w-5 flex items-center justify-center">
                <span class="font-data text-sm text-[var(--foreground-strong)]">∑</span>
              </div>
              <div>
                <div class="font-data text-lg tabular-nums text-[var(--foreground)]">
                  {{ entity.stats?.totalSubmissions ?? 0 }}
                </div>
                <div class="terminal-label">{{ $t('users.stats.submissions') }}</div>
              </div>
            </div>
            <div
              class="flex items-center gap-3 p-3 border border-[var(--border-subtle)] dark:border-[var(--foreground-strong)] bg-[var(--surface-sunken)]"
            >
              <div class="h-5 w-5 flex items-center justify-center">
                <span class="font-data text-sm text-[var(--foreground-strong)]">✓</span>
              </div>
              <div>
                <div class="font-data text-lg tabular-nums text-[var(--foreground)]">
                  {{ entity.stats?.acceptedSubmissions ?? 0 }}
                </div>
                <div class="terminal-label">{{ $t('users.stats.accepted') }}</div>
              </div>
            </div>
          </div>

          <!-- Submission Progress Bar -->
          <div
            class="space-y-2"
            v-if="entity.stats && (!entity.statsStatus || entity.statsStatus === 'OK')"
          >
            <div class="flex items-center justify-between">
              <span class="font-data text-xs text-[var(--foreground-muted)]">{{
                $t('users.stats.acceptanceRate')
              }}</span>
              <span class="font-data text-xs text-[var(--foreground-muted)] tabular-nums"
                >{{ acceptanceRate }}%</span
              >
            </div>
            <div class="ascii-progress text-2xs">
              <span class="text-[var(--foreground-strong)]">{{ progressFilled }}</span>
              <span class="ascii-progress-track">{{ progressEmpty }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Permissions - only a proven snapshot is rendered as permissions. -->
      <div
        v-if="entity.permissions && (!entity.permissionsStatus || entity.permissionsStatus === 'OK')"
        class="border border-[var(--border-subtle)] dark:border-[var(--foreground-strong)] bg-[var(--card)]"
      >
        <div
          class="border-b border-[var(--border-subtle)] dark:border-[var(--foreground-strong)] px-4 py-2 bg-[var(--surface-sunken)]"
        >
          <span class="terminal-comment">{{ t('users.drawer.sections.permissions') }}</span>
        </div>
        <div class="p-4">
          <ul v-if="entity.permissions.length" class="space-y-1">
            <li
              v-for="permission in entity.permissions"
              :key="`${permission.action}:${permission.resource}`"
              class="font-data text-xs text-[var(--foreground)]"
            >
              {{ permission.action }}:{{ permission.resource }}
            </li>
          </ul>
          <span v-else class="text-sm text-[var(--foreground-muted)]">
            {{ t('users.degradation.noPermissions') }}
          </span>
        </div>
      </div>

      <!-- Account Information - Terminal Style -->
      <div
        class="border border-[var(--border-subtle)] dark:border-[var(--foreground-strong)] bg-[var(--card)]"
      >
        <div
          class="border-b border-[var(--border-subtle)] dark:border-[var(--foreground-strong)] px-4 py-2 bg-[var(--surface-sunken)]"
        >
          <span class="terminal-comment">{{ t('users.drawer.sections.account') }}</span>
        </div>

        <div class="p-4">
          <div class="grid grid-cols-2 gap-4">
            <DataBlock :label="$t('users.columns.username')" :value="entity.username" />
            <DataBlock :label="$t('users.columns.role')">
              <SemanticBadge
                :color="USER_ROLE_COLOR_MAP[entity.role] ?? 'neutral'"
                :label="t(`users.filters.role.${entity.role}`, entity.role)"
              />
            </DataBlock>
            <DataBlock :label="$t('users.columns.joined')">
              <span class="font-data text-sm tabular-nums">
                {{ formatDateByLocale(entity.joinedAt) }}
              </span>
            </DataBlock>
            <DataBlock :label="$t('users.columns.lastLogin')">
              <span v-if="entity.lastLoginAt" class="font-data text-sm tabular-nums">
                {{ formatDateByLocale(entity.lastLoginAt) }}
              </span>
              <span v-else class="text-[var(--foreground-muted)] italic">{{
                $t('users.stats.never')
              }}</span>
            </DataBlock>
          </div>
        </div>
      </div>

      <!-- Ban Information -->
      <div
        v-if="entity.isBanned"
        class="border border-[var(--status-error-mark)] bg-[color-mix(in_oklch,_var(--status-error-mark)_8%,_transparent)]"
      >
        <div
          class="border-b border-[var(--status-error-mark)] px-4 py-2 bg-[color-mix(in_oklch,_var(--status-error-mark)_12%,_transparent)]"
        >
          <span class="terminal-comment text-[var(--foreground-strong)]">ban_info</span>
        </div>

        <div class="p-4 space-y-3">
          <DataBlock :label="$t('users.form.banReason')">
            <span class="text-sm italic text-[var(--foreground)]">
              {{ entity.banReason || $t('users.form.noReasonProvided') }}
            </span>
          </DataBlock>
          <DataBlock :label="$t('users.columns.bannedUntil')">
            <span class="font-data text-sm tabular-nums text-[var(--foreground-strong)]">
              {{
                entity.bannedUntil
                  ? formatDateTimeByLocale(entity.bannedUntil)
                  : $t('users.form.unknown')
              }}
            </span>
          </DataBlock>
        </div>
      </div>
    </template>
  </BaseDetailDrawer>
</template>
