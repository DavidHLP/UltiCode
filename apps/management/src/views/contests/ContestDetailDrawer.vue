<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useContestsStore } from '@/stores/admin/contests'
import { useI18n } from 'vue-i18n'
import { formatDateTimeByLocale } from '@/i18n/utils'
import { Button } from '@/components/ui/button'
import { IconCalendar, IconClock, IconTrophy, IconUsers, IconExternalLink } from '@tabler/icons-vue'
import BaseDetailDrawer from '@/components/shared/BaseDetailDrawer.vue'
import {
  DataBlock,
  SemanticBadge,
  CONTEST_STATUS_COLOR_MAP,
  CONTEST_TYPE_COLOR_MAP,
} from '@/components/ui/terminal'

const props = defineProps<{
  open: boolean
  contestId: string | null
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  success: []
}>()

const router = useRouter()
const contestsStore = useContestsStore()
const { t } = useI18n()
const loading = ref(false)

async function loadContest() {
  if (!props.contestId) return
  loading.value = true
  try {
    await contestsStore.fetchContest(props.contestId)
  } finally {
    loading.value = false
  }
}

watch(
  () => props.open,
  (newOpen) => {
    if (newOpen && props.contestId) {
      loadContest()
    }
  },
)

function navigateToDetail() {
  if (!props.contestId) return
  emit('update:open', false)
  router.push({ name: 'contest-detail', params: { id: props.contestId } })
}
</script>

<template>
  <BaseDetailDrawer
    :open="open"
    @update:open="emit('update:open', $event)"
    :loading="loading"
    :entity="contestsStore.currentContest"
    :title="t('contests.drawer.title')"
    :description="t('contests.drawer.subtitle')"
    :loading-text="t('contests.drawer.loadingDetails')"
    :not-found-text="t('contests.drawer.contestNotFound')"
  >
    <template #headerActions>
      <Button
        variant="terminal"
        size="sm"
        class="font-data text-xs border-[var(--border-subtle)] hover:border-[var(--primary)] hover:text-[var(--primary)] transition-colors"
        @click="navigateToDetail"
      >
        <IconExternalLink class="h-4 w-4 mr-1.5" />
        <span class="uppercase tracking-wider">{{ t('contests.drawer.fullView') }}</span>
      </Button>
    </template>

    <template #content="{ entity }">
      <!-- Contest Header - Terminal Style -->
      <div
        class="border border-[var(--border-subtle)] dark:border-[var(--foreground-strong)] bg-[var(--card)]"
      >
        <!-- Header Bar -->
        <div
          class="border-b border-[var(--border-subtle)] dark:border-[var(--foreground-strong)] px-4 py-2 bg-[var(--surface-sunken)]"
        >
          <span class="terminal-comment">contest_profile</span>
        </div>

        <div class="p-4">
          <div class="flex items-start gap-4">
            <!-- Icon -->
            <div
              class="h-16 w-16 border flex items-center justify-center bg-[var(--surface-highlight)] dark:bg-[var(--foreground-strong)] border-[var(--border-subtle)] dark:border-[var(--foreground-strong)] text-[var(--primary)]"
            >
              <IconTrophy class="h-8 w-8" />
            </div>

            <div class="flex-1 min-w-0">
              <div class="flex items-center gap-2 mb-1">
                <span class="font-medium text-lg truncate">{{ entity.title }}</span>
              </div>
              <div class="font-data text-xs text-[var(--foreground-muted)] mb-2">
                {{ entity.slug }}
              </div>
              <div class="flex flex-wrap gap-2">
                <SemanticBadge
                  :color="CONTEST_TYPE_COLOR_MAP[entity.contestType] ?? 'neutral'"
                  :label="t(`contests.type.${entity.contestType}`, entity.contestType)"
                  size="sm"
                />
                <SemanticBadge
                  :color="CONTEST_STATUS_COLOR_MAP[entity.status] ?? 'neutral'"
                  :label="entity.status"
                  size="sm"
                  :dot="entity.status === 'RUNNING'"
                  :pulse="entity.status === 'RUNNING'"
                />
                <SemanticBadge
                  v-if="entity.isVisible"
                  color="success"
                  :label="t('contests.drawer.published')"
                  size="sm"
                />
                <SemanticBadge
                  v-else
                  color="neutral"
                  :label="t('contests.detail.hidden')"
                  size="sm"
                />
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Statistics - Terminal Style -->
      <div
        class="border border-[var(--border-subtle)] dark:border-[var(--foreground-strong)] bg-[var(--card)]"
      >
        <div
          class="border-b border-[var(--border-subtle)] dark:border-[var(--foreground-strong)] px-4 py-2 bg-[var(--surface-sunken)]"
        >
          <span class="terminal-comment">statistics</span>
        </div>

        <div class="p-4">
          <div class="grid grid-cols-2 gap-3">
            <div
              class="flex items-center gap-3 p-3 border border-[var(--border-subtle)] dark:border-[var(--foreground-strong)] bg-[var(--surface-sunken)]"
            >
              <IconTrophy class="h-5 w-5 text-[var(--status-warning-mark)]" />
              <div>
                <div class="font-data text-lg tabular-nums text-[var(--foreground)]">
                  {{ entity.problemCount || 0 }}
                </div>
                <div class="terminal-label">{{ t('contests.drawer.problems') }}</div>
              </div>
            </div>
            <div
              class="flex items-center gap-3 p-3 border border-[var(--border-subtle)] dark:border-[var(--foreground-strong)] bg-[var(--surface-sunken)]"
            >
              <IconUsers class="h-5 w-5 text-[var(--status-info-mark)]" />
              <div>
                <div class="font-data text-lg tabular-nums text-[var(--foreground)]">
                  {{ entity.participantCount || 0 }}
                </div>
                <div class="terminal-label">{{ t('contests.drawer.participants') }}</div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Schedule Info - Terminal Style -->
      <div
        class="border border-[var(--border-subtle)] dark:border-[var(--foreground-strong)] bg-[var(--card)]"
      >
        <div
          class="border-b border-[var(--border-subtle)] dark:border-[var(--foreground-strong)] px-4 py-2 bg-[var(--surface-sunken)]"
        >
          <span class="terminal-comment">schedule</span>
        </div>

        <div class="p-4">
          <div class="grid grid-cols-2 gap-4">
            <DataBlock :label="t('contests.drawer.start')">
              <div class="flex items-center gap-2">
                <IconCalendar class="h-4 w-4 text-[var(--foreground-muted)]" />
                <span class="font-data text-sm tabular-nums">
                  {{ formatDateTimeByLocale(entity.startTime) }}
                </span>
              </div>
            </DataBlock>
            <DataBlock :label="t('contests.drawer.duration')">
              <div class="flex items-center gap-2">
                <IconClock class="h-4 w-4 text-[var(--foreground-muted)]" />
                <span class="font-data text-sm tabular-nums">
                  {{ entity.duration }} {{ t('common.minutes') }}
                </span>
              </div>
            </DataBlock>
          </div>
        </div>
      </div>

      <!-- Description - Terminal Style -->
      <div
        v-if="entity.description"
        class="border border-[var(--border-subtle)] dark:border-[var(--foreground-strong)] bg-[var(--card)]"
      >
        <div
          class="border-b border-[var(--border-subtle)] dark:border-[var(--foreground-strong)] px-4 py-2 bg-[var(--surface-sunken)]"
        >
          <span class="terminal-comment">description</span>
        </div>

        <div class="p-4">
          <p class="text-sm text-[var(--foreground-muted)] whitespace-pre-wrap font-data">
            {{ entity.description }}
          </p>
        </div>
      </div>
    </template>
  </BaseDetailDrawer>
</template>
