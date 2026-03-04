<script setup lang="ts">
import { ref, watch } from 'vue'
import { useUsersStore } from '@/stores/admin/users'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { IconMail, IconTrophy, IconFlame } from '@tabler/icons-vue'
import BaseDetailDrawer from '@/components/shared/BaseDetailDrawer.vue'
import { DataBlock } from '@/components/ui/terminal'

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

// ASCII-style progress bar
function renderAsciiProgress(
  count: number,
  total: number,
  width = 20,
): { filled: string; empty: string; percent: number } {
  const percent = total > 0 ? Math.round((count / total) * 100) : 0
  const filledCount = Math.round((percent / 100) * width)
  const filled = '█'.repeat(filledCount)
  const empty = '░'.repeat(width - filledCount)
  return { filled, empty, percent }
}

// Get status badge styling
function getStatusStyle(entity: { is_banned: boolean; is_active: boolean }) {
  if (entity.is_banned) {
    return {
      class: 'terminal-badge-error animate-pulse-subtle',
      label: 'BANNED',
    }
  }
  if (!entity.is_active) {
    return {
      class:
        'bg-[var(--silver-100)] dark:bg-[var(--silver-800)] text-[var(--silver-500)] border border-[var(--silver-300)]',
      label: 'INACTIVE',
    }
  }
  return {
    class: 'terminal-badge-success animate-pulse-subtle',
    label: 'ACTIVE',
  }
}

// Get role styling
function getRoleStyle(role: string) {
  const styles: Record<string, string> = {
    SUPER_ADMIN: 'terminal-badge-info',
    ADMIN: 'terminal-badge-info',
    MODERATOR: 'terminal-badge-warning',
    USER: 'bg-[var(--silver-100)] dark:bg-[var(--silver-800)] text-[var(--silver-500)] border border-[var(--silver-300)]',
  }
  return styles[role] || styles.USER
}

// Get difficulty color
function getDifficultyColor(diff: string): string {
  const colors: Record<string, string> = {
    Easy: 'text-[var(--terminal-green)]',
    Medium: 'text-[var(--terminal-amber)]',
    Hard: 'text-[var(--terminal-red)]',
  }
  return colors[diff] || 'text-[var(--silver-400)]'
}
</script>

<template>
  <BaseDetailDrawer
    :open="open"
    @update:open="emit('update:open', $event)"
    :loading="loading"
    :entity="usersStore.currentUser"
    title="User Details"
    description="View comprehensive information about the user."
    loading-text="Loading user details..."
    not-found-text="User not found"
  >
    <template #content="{ entity }">
      <!-- Profile Header - Terminal Style -->
      <div
        class="border border-[var(--silver-200)] dark:border-[var(--silver-700)] bg-[var(--card)]"
      >
        <!-- Header Bar -->
        <div
          class="border-b border-[var(--silver-200)] dark:border-[var(--silver-700)] px-4 py-2 bg-[var(--surface-sunken)]"
        >
          <span class="terminal-comment">user_profile</span>
        </div>

        <div class="p-4">
          <div class="flex items-start gap-4">
            <!-- Avatar with status ring -->
            <div
              :class="[
                'relative',
                entity.is_banned
                  ? 'ring-2 ring-[var(--terminal-red)] ring-offset-2 ring-offset-background'
                  : entity.is_active
                    ? 'ring-2 ring-[var(--terminal-green)] ring-offset-2 ring-offset-background'
                    : '',
              ]"
            >
              <Avatar class="h-16 w-16 rounded-sm">
                <AvatarImage :src="entity.avatar ?? ''" :alt="entity.username" />
                <AvatarFallback
                  class="font-data text-lg bg-[var(--silver-100)] dark:bg-[var(--silver-800)]"
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
              <div class="flex items-center gap-1.5 text-sm text-[var(--silver-400)] mb-2">
                <IconMail class="h-3.5 w-3.5" />
                <span class="font-data text-xs truncate">{{ entity.email || 'no-email' }}</span>
              </div>
              <div class="flex flex-wrap gap-2">
                <span :class="['terminal-badge', getRoleStyle(entity.role)]">
                  {{ entity.role.replace('_', ' ') }}
                </span>
                <span :class="['terminal-badge', getStatusStyle(entity).class]">
                  {{ getStatusStyle(entity).label }}
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Performance Stats - Terminal Style -->
      <div
        v-if="entity.stats"
        class="border border-[var(--silver-200)] dark:border-[var(--silver-700)] bg-[var(--card)]"
      >
        <div
          class="border-b border-[var(--silver-200)] dark:border-[var(--silver-700)] px-4 py-2 bg-[var(--surface-sunken)]"
        >
          <span class="terminal-comment">performance_stats</span>
        </div>

        <div class="p-4 space-y-4">
          <!-- Summary Stats -->
          <div class="grid grid-cols-2 gap-3">
            <div
              class="flex items-center gap-3 p-3 border border-[var(--silver-200)] dark:border-[var(--silver-700)] bg-[var(--surface-sunken)]"
            >
              <IconTrophy class="h-5 w-5 text-[var(--terminal-amber)]" />
              <div>
                <div class="font-data text-lg tabular-nums text-[var(--foreground)]">
                  {{ entity.stats.totalSolved }}
                </div>
                <div class="terminal-label">Solved</div>
              </div>
            </div>
            <div
              class="flex items-center gap-3 p-3 border border-[var(--silver-200)] dark:border-[var(--silver-700)] bg-[var(--surface-sunken)]"
            >
              <IconFlame class="h-5 w-5 text-[var(--terminal-red)]" />
              <div>
                <div class="font-data text-lg tabular-nums text-[var(--foreground)]">
                  {{ entity.stats.streak }}
                </div>
                <div class="terminal-label">Streak</div>
              </div>
            </div>
          </div>

          <!-- ASCII Progress Bars -->
          <div class="space-y-3">
            <div v-for="(data, diff) in entity.stats.stats" :key="diff" class="space-y-1">
              <div class="flex items-center justify-between">
                <span class="font-data text-xs" :class="getDifficultyColor(diff as string)">{{
                  diff
                }}</span>
                <span class="font-data text-xs text-[var(--silver-400)] tabular-nums">
                  {{ data.count }}/{{ data.total }}
                </span>
              </div>
              <div class="ascii-progress">
                <span :class="getDifficultyColor(diff as string)">{{
                  renderAsciiProgress(data.count, data.total).filled
                }}</span>
                <span class="ascii-progress-track">{{
                  renderAsciiProgress(data.count, data.total).empty
                }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Account Information - Terminal Style -->
      <div
        class="border border-[var(--silver-200)] dark:border-[var(--silver-700)] bg-[var(--card)]"
      >
        <div
          class="border-b border-[var(--silver-200)] dark:border-[var(--silver-700)] px-4 py-2 bg-[var(--surface-sunken)]"
        >
          <span class="terminal-comment">account_info</span>
        </div>

        <div class="p-4">
          <div class="grid grid-cols-2 gap-4">
            <DataBlock :label="$t('users.columns.username')" :value="entity.username" />
            <DataBlock :label="$t('users.columns.role')">
              <span :class="['terminal-badge', getRoleStyle(entity.role)]">
                {{ entity.role.replace('_', ' ') }}
              </span>
            </DataBlock>
            <DataBlock :label="$t('users.columns.joined')">
              <span class="font-data text-sm tabular-nums">
                {{ new Date(entity.joined_at).toLocaleDateString() }}
              </span>
            </DataBlock>
            <DataBlock :label="$t('users.columns.lastLogin')">
              <span v-if="entity.last_login_at" class="font-data text-sm tabular-nums">
                {{ new Date(entity.last_login_at).toLocaleDateString() }}
              </span>
              <span v-else class="text-[var(--silver-400)] italic">Never</span>
            </DataBlock>
          </div>
        </div>
      </div>

      <!-- Ban Information -->
      <div
        v-if="entity.is_banned"
        class="border border-[var(--terminal-red)] bg-[oklch(0.6_0.2_25/0.08)]"
      >
        <div class="border-b border-[var(--terminal-red)] px-4 py-2 bg-[oklch(0.6_0.2_25/0.12)]">
          <span class="terminal-comment text-[var(--terminal-red)]">ban_info</span>
        </div>

        <div class="p-4 space-y-3">
          <DataBlock :label="$t('users.form.banReason')">
            <span class="text-sm italic text-[var(--foreground)]">
              {{ entity.ban_reason || 'No reason provided' }}
            </span>
          </DataBlock>
          <DataBlock :label="$t('users.columns.bannedAt')">
            <span class="font-data text-sm tabular-nums text-[var(--terminal-red)]">
              {{ entity.banned_at ? new Date(entity.banned_at).toLocaleString() : 'Unknown' }}
            </span>
          </DataBlock>
        </div>
      </div>
    </template>
  </BaseDetailDrawer>
</template>
