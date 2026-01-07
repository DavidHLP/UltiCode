<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useContestsStore } from '@/stores/admin/contests'
import {
  Drawer,
  DrawerContent,
  DrawerDescription,
  DrawerHeader,
  DrawerTitle,
} from '@/components/ui/drawer'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { ScrollArea } from '@/components/ui/scroll-area'
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

const props = defineProps<{
  open: boolean
  contestId: string | null
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
}>()

const router = useRouter()
const contestsStore = useContestsStore()
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

function getStatusBadgeVariant(
  status: string,
): 'default' | 'secondary' | 'destructive' | 'outline' {
  switch (status) {
    case 'RUNNING':
      return 'default'
    case 'FINISHED':
      return 'secondary'
    default:
      return 'outline'
  }
}

function getTypeBadgeVariant(type: string): 'default' | 'secondary' | 'destructive' | 'outline' {
  switch (type) {
    case 'PUBLIC':
      return 'default'
    case 'PRIVATE':
      return 'secondary'
    default:
      return 'outline'
  }
}

function navigateToDetail() {
  if (!props.contestId) return
  emit('update:open', false)
  router.push({ name: 'contest-detail', params: { id: props.contestId } })
}
</script>

<template>
  <Drawer :open="open" @update:open="emit('update:open', $event)" direction="right">
    <DrawerContent class="h-full w-[400px] sm:w-[540px]">
      <DrawerHeader class="border-b px-6 py-4">
        <div class="flex items-center justify-between">
          <div>
            <DrawerTitle>Contest Details</DrawerTitle>
            <DrawerDescription>View contest information and statistics.</DrawerDescription>
          </div>
          <Button variant="outline" size="sm" @click="navigateToDetail">
            <IconExternalLink class="h-4 w-4 mr-1" />
            Full View
          </Button>
        </div>
      </DrawerHeader>

      <div v-if="loading" class="flex h-full items-center justify-center p-8">
        <div class="flex flex-col items-center gap-2">
          <div
            class="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent"
          ></div>
          <p class="text-sm text-muted-foreground">Loading contest details...</p>
        </div>
      </div>

      <ScrollArea v-else-if="contestsStore.currentContest" class="flex-1">
        <div class="flex flex-col gap-6 p-6">
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
                  {{ contestsStore.currentContest.title }}
                </h3>
                <p class="text-sm text-muted-foreground font-mono">
                  {{ contestsStore.currentContest.slug }}
                </p>
                <div class="flex flex-wrap gap-2 mt-1">
                  <Badge :variant="getTypeBadgeVariant(contestsStore.currentContest.contest_type)">
                    {{ contestsStore.currentContest.contest_type }}
                  </Badge>
                  <Badge :variant="getStatusBadgeVariant(contestsStore.currentContest.status)">
                    {{ contestsStore.currentContest.status }}
                  </Badge>
                  <Badge v-if="contestsStore.currentContest.is_visible" variant="outline">
                    <IconEye class="h-3 w-3 mr-1" />
                    Published
                  </Badge>
                  <Badge v-else variant="secondary">
                    <IconEyeOff class="h-3 w-3 mr-1" />
                    Hidden
                  </Badge>
                </div>
              </div>
            </div>
          </div>

          <Separator />

          <!-- Statistics -->
          <div class="space-y-4">
            <h4 class="text-sm font-medium text-muted-foreground uppercase tracking-wider">
              Statistics
            </h4>
            <div class="grid grid-cols-2 gap-4">
              <div
                class="rounded-lg border bg-card p-4 flex flex-col items-center justify-center text-center"
              >
                <IconTrophy class="h-8 w-8 text-yellow-500 mb-2" />
                <span class="text-2xl font-bold">{{
                  contestsStore.currentContest.problems?.length || 0
                }}</span>
                <span class="text-xs text-muted-foreground uppercase">Problems</span>
              </div>
              <div
                class="rounded-lg border bg-card p-4 flex flex-col items-center justify-center text-center"
              >
                <IconUsers class="h-8 w-8 text-blue-500 mb-2" />
                <span class="text-2xl font-bold">{{
                  contestsStore.currentContest.participant_count || 0
                }}</span>
                <span class="text-xs text-muted-foreground uppercase">Participants</span>
              </div>
            </div>
          </div>

          <Separator />

          <!-- Details Grid -->
          <div class="grid gap-6">
            <div class="space-y-4">
              <h4 class="text-sm font-medium text-muted-foreground uppercase tracking-wider">
                Schedule
              </h4>
              <div class="grid grid-cols-2 gap-4">
                <div class="space-y-1">
                  <p class="text-sm font-medium flex items-center gap-2">
                    <IconCalendar class="h-4 w-4 text-muted-foreground" />
                    Start Time
                  </p>
                  <p class="text-sm text-muted-foreground pl-6">
                    {{ new Date(contestsStore.currentContest.start_time).toLocaleString() }}
                  </p>
                </div>
                <div class="space-y-1">
                  <p class="text-sm font-medium flex items-center gap-2">
                    <IconClock class="h-4 w-4 text-muted-foreground" />
                    Duration
                  </p>
                  <p class="text-sm text-muted-foreground pl-6">
                    {{ contestsStore.currentContest.duration_minutes }} minutes
                  </p>
                </div>
              </div>
            </div>

            <div v-if="contestsStore.currentContest.description" class="space-y-4">
              <h4 class="text-sm font-medium text-muted-foreground uppercase tracking-wider">
                Description
              </h4>
              <p class="text-sm text-muted-foreground whitespace-pre-wrap">
                {{ contestsStore.currentContest.description }}
              </p>
            </div>

            <!-- Problems List Preview -->
            <div v-if="contestsStore.currentContest.problems?.length" class="space-y-4">
              <h4 class="text-sm font-medium text-muted-foreground uppercase tracking-wider">
                Problems ({{ contestsStore.currentContest.problems.length }})
              </h4>
              <div class="space-y-2">
                <div
                  v-for="cp in contestsStore.currentContest.problems.slice(0, 5)"
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
                  <Badge variant="outline">{{ cp.score }} pts</Badge>
                </div>
                <p
                  v-if="contestsStore.currentContest.problems.length > 5"
                  class="text-xs text-muted-foreground text-center pt-2"
                >
                  + {{ contestsStore.currentContest.problems.length - 5 }} more problems
                </p>
              </div>
            </div>
          </div>
        </div>
      </ScrollArea>

      <div v-else class="flex h-full items-center justify-center p-8">
        <p class="text-muted-foreground">Contest not found</p>
      </div>
    </DrawerContent>
  </Drawer>
</template>
