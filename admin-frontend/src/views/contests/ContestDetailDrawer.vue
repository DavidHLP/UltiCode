<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useContestsStore } from '@/stores/admin/contests'
import { useI18n } from 'vue-i18n'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Separator } from '@/components/ui/separator'
import {
  IconCalendar,
  IconClock,
  IconTrophy,
  IconUsers,
  IconEye,
  IconEyeOff,
  IconExternalLink,
} from '@tabler/icons-vue'
import BaseDetailDrawer from '@/components/shared/BaseDetailDrawer.vue'
import { getContestStatusBadgeVariant, getContestTypeBadgeVariant } from '@/lib/ui/status'

const props = defineProps<{
  open: boolean
  contestId: string | null
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
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
        <IconExternalLink class="h-4 w-4 mr-1" />
        {{ t('contests.drawer.fullView') }}
      </Button>
    </template>

    <template #content="{ entity }">
      <!-- Contest Header -->
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-4">
          <div
            class="h-16 w-16 rounded-xl bg-primary/10 flex items-center justify-center text-primary"
          >
            <IconTrophy class="h-8 w-8" />
          </div>
          <div class="flex flex-col gap-1">
            <h3 class="text-xl font-semibold leading-none">
              {{ entity.title }}
            </h3>
            <p class="text-sm text-muted-foreground font-mono">
              {{ entity.slug }}
            </p>
            <div class="flex flex-wrap gap-2 mt-1">
              <Badge :variant="getContestTypeBadgeVariant(entity.contest_type)">
                {{ t(`contests.type.${entity.contest_type}`) }}
              </Badge>
              <Badge :variant="getContestStatusBadgeVariant(entity.status)">
                {{ t(`contests.status.${entity.status.toLowerCase()}`) }}
              </Badge>
              <Badge v-if="entity.is_visible" variant="outline">
                <IconEye class="h-3 w-3 mr-1" />
                {{ t('contests.drawer.published') }}
              </Badge>
              <Badge v-else variant="secondary">
                <IconEyeOff class="h-3 w-3 mr-1" />
                {{ t('contests.detail.hidden') }}
              </Badge>
            </div>
          </div>
        </div>
      </div>

      <Separator />

      <!-- Statistics -->
      <div class="space-y-4">
        <h4 class="text-sm font-medium text-muted-foreground uppercase tracking-wider">
          {{ t('contests.drawer.statistics') }}
        </h4>
        <div class="grid grid-cols-2 gap-4">
          <div
            class="rounded-lg border bg-card p-4 flex flex-col items-center justify-center text-center"
          >
            <IconTrophy class="h-8 w-8 text-yellow-500 mb-2" />
            <span class="text-2xl font-bold">{{
              entity.problems?.length || 0
            }}</span>
            <span class="text-xs text-muted-foreground uppercase">{{
              t('contests.drawer.problems')
            }}</span>
          </div>
          <div
            class="rounded-lg border bg-card p-4 flex flex-col items-center justify-center text-center"
          >
            <IconUsers class="h-8 w-8 text-blue-500 mb-2" />
            <span class="text-2xl font-bold">{{
              entity.participant_count || 0
            }}</span>
            <span class="text-xs text-muted-foreground uppercase">{{
              t('contests.drawer.participants')
            }}</span>
          </div>
        </div>
      </div>

      <Separator />

      <!-- Details Grid -->
      <div class="grid gap-6">
        <div class="space-y-4">
          <h4 class="text-sm font-medium text-muted-foreground uppercase tracking-wider">
            {{ t('contests.drawer.schedule') }}
          </h4>
          <div class="grid grid-cols-2 gap-4">
            <div class="space-y-1">
              <p class="text-sm font-medium flex items-center gap-2">
                <IconCalendar class="h-4 w-4 text-muted-foreground" />
                {{ t('contests.drawer.start') }}
              </p>
              <p class="text-sm text-muted-foreground pl-6">
                {{ new Date(entity.start_time).toLocaleString() }}
              </p>
            </div>
            <div class="space-y-1">
              <p class="text-sm font-medium flex items-center gap-2">
                <IconClock class="h-4 w-4 text-muted-foreground" />
                {{ t('contests.drawer.duration') }}
              </p>
              <p class="text-sm text-muted-foreground pl-6">
                {{ entity.duration_minutes }} {{ t('common.minutes') }}
              </p>
            </div>
          </div>
        </div>

        <div v-if="entity.description" class="space-y-4">
          <h4 class="text-sm font-medium text-muted-foreground uppercase tracking-wider">
            {{ t('contests.drawer.description') }}
          </h4>
          <p class="text-sm text-muted-foreground whitespace-pre-wrap">
            {{ entity.description }}
          </p>
        </div>

        <!-- Problems List Preview -->
        <div v-if="entity.problems?.length" class="space-y-4">
          <h4 class="text-sm font-medium text-muted-foreground uppercase tracking-wider">
            {{
              t('contests.drawer.problemsCount', {
                count: entity.problems.length,
              })
            }}
          </h4>
          <div class="space-y-2">
            <div
              v-for="cp in entity.problems.slice(0, 5)"
              :key="cp.id"
              class="flex items-center justify-between rounded-lg border p-3"
            >
              <div class="flex items-center gap-3">
                <span class="font-mono text-sm font-medium text-muted-foreground">
                  {{ cp.problem_index }}
                </span>
                <div>
                  <p class="text-sm font-medium">{{ cp.problem.title }}</p>
                  <p class="text-xs text-muted-foreground">{{ cp.problem.slug }}</p>
                </div>
              </div>
              <Badge variant="outline">{{ cp.score }} {{ t('contests.drawer.pts') }}</Badge>
            </div>
            <p
              v-if="entity.problems.length > 5"
              class="text-xs text-muted-foreground text-center pt-2"
            >
              {{
                t('contests.drawer.moreProblems', {
                  count: entity.problems.length - 5,
                })
              }}
            </p>
          </div>
        </div>
      </div>
    </template>
  </BaseDetailDrawer>
</template>
