<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useContestsStore } from '@/stores/admin/contests'
import { useI18n } from 'vue-i18n'
import { formatDateTimeByLocale } from '@/i18n/utils'
import { Button } from '@ulticode/design-system/ui/button'
import {
  Calendar as IconCalendar,
  Clock as IconClock,
  Trophy as IconTrophy,
  Users as IconUsers,
  ExternalLink as IconExternalLink,
} from '@lucide/vue'

import BaseDetailDrawer from '@/components/shared/BaseDetailDrawer.vue'
import DetailField from '@ulticode/design-system/DetailField.vue'
import SemanticBadge from '@ulticode/badge-config/SemanticBadge.vue'
import { CONTEST_STATUS_COLOR_MAP, CONTEST_TYPE_COLOR_MAP } from '@ulticode/badge-config'

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
      <Button variant="outline" size="sm" @click="navigateToDetail">
        <IconExternalLink class="h-4 w-4 mr-1.5" />
        <span class="uppercase tracking-wider">{{ t('contests.drawer.fullView') }}</span>
      </Button>
    </template>

    <template #content="{ entity }">
      <!-- Contest Header - Terminal Style -->
      <div class="border border-border dark:border-foreground bg-card">
        <!-- Header Bar -->
        <div class="border-b border-border dark:border-foreground px-4 py-2 bg-muted">
          <span class="text-sm text-muted-foreground">contest_profile</span>
        </div>

        <div class="p-4">
          <div class="flex items-start gap-4">
            <!-- Icon -->
            <div
              class="h-16 w-16 border flex items-center justify-center bg-accent dark:bg-foreground border-border dark:border-foreground text-primary"
            >
              <IconTrophy class="h-8 w-8" />
            </div>

            <div class="flex-1 min-w-0">
              <div class="flex items-center gap-2 mb-1">
                <span class="font-medium text-lg truncate">{{ entity.title }}</span>
              </div>
              <div class="font-mono text-xs text-muted-foreground mb-2">
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
      <div class="border border-border dark:border-foreground bg-card">
        <div class="border-b border-border dark:border-foreground px-4 py-2 bg-muted">
          <span class="text-sm text-muted-foreground">statistics</span>
        </div>

        <div class="p-4">
          <div class="grid grid-cols-2 gap-3">
            <div
              class="flex items-center gap-3 p-3 border border-border dark:border-foreground bg-muted"
            >
              <IconTrophy class="h-5 w-5 text-status-warning-mark" />
              <div>
                <div class="font-mono text-lg tabular-nums text-foreground">
                  {{ entity.problemCount || 0 }}
                </div>
                <div class="text-sm font-medium">{{ t('contests.drawer.problems') }}</div>
              </div>
            </div>
            <div
              class="flex items-center gap-3 p-3 border border-border dark:border-foreground bg-muted"
            >
              <IconUsers class="h-5 w-5 text-status-info-mark" />
              <div>
                <div class="font-mono text-lg tabular-nums text-foreground">
                  {{ entity.participantCount || 0 }}
                </div>
                <div class="text-sm font-medium">{{ t('contests.drawer.participants') }}</div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Schedule Info - Terminal Style -->
      <div class="border border-border dark:border-foreground bg-card">
        <div class="border-b border-border dark:border-foreground px-4 py-2 bg-muted">
          <span class="text-sm text-muted-foreground">schedule</span>
        </div>

        <div class="p-4">
          <div class="grid grid-cols-2 gap-4">
            <DetailField :label="t('contests.drawer.start')">
              <div class="flex items-center gap-2">
                <IconCalendar class="h-4 w-4 text-muted-foreground" />
                <span class="font-mono text-sm tabular-nums">
                  {{ formatDateTimeByLocale(entity.startTime) }}
                </span>
              </div>
            </DetailField>
            <DetailField :label="t('contests.drawer.duration')">
              <div class="flex items-center gap-2">
                <IconClock class="h-4 w-4 text-muted-foreground" />
                <span class="font-mono text-sm tabular-nums">
                  {{ entity.duration }} {{ t('common.minutes') }}
                </span>
              </div>
            </DetailField>
          </div>
        </div>
      </div>

      <!-- Description - Terminal Style -->
      <div v-if="entity.description" class="border border-border dark:border-foreground bg-card">
        <div class="border-b border-border dark:border-foreground px-4 py-2 bg-muted">
          <span class="text-sm text-muted-foreground">description</span>
        </div>

        <div class="p-4">
          <p class="text-sm text-muted-foreground whitespace-pre-wrap font-mono">
            {{ entity.description }}
          </p>
        </div>
      </div>
    </template>
  </BaseDetailDrawer>
</template>
