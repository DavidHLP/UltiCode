<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { toast } from 'vue-sonner'

import { Button } from '@ulticode/design-system/ui/button'
import { Label } from '@ulticode/design-system/ui/label'
import { Textarea } from '@ulticode/design-system/ui/textarea'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@ulticode/design-system/ui/dialog'
import {
  RefreshCw as IconRefresh,
  Scale as IconScale,
  Loader2 as IconLoader2,
  Check as IconCheck,
  X as IconX,
} from '@lucide/vue'

import DataTable from '@/components/table/DataTable.vue'
import DataTableToolbar from '@/components/table/DataTableToolbar.vue'

import { useModerationStore } from '@/stores/admin/moderation'
import { type Appeal, AppealStatus, type QueryAppealsParams } from '@/api/admin/moderation'
import { useRemoteTable } from '@/composables/useRemoteTable'
import { createAppealsColumns, type AppealActions } from './appeals-columns'
import { useModerationFilters } from './composables/useModerationFilters'

const { t } = useI18n()
const store = useModerationStore()

const isLoaded = ref(false)

// Filters

// Review dialog state
const reviewDialogOpen = ref(false)
const selectedAppeal = ref<Appeal | null>(null)
const reviewDecision = ref<'APPROVED' | 'REJECTED'>('APPROVED')
const reviewResponse = ref('')
const selectedRows = ref<Appeal[]>([])
const reviewLoading = ref(false)

onMounted(() => {
  setTimeout(() => {
    isLoaded.value = true
  }, 100)
})
const {
  query,
  searchQuery,
  tablePagination,
  loading,
  data,
  total,
  refresh: loadAppeals,
  setFilters,
} = useRemoteTable<Appeal, { status: AppealStatus | 'all' }, QueryAppealsParams>({
  store: store.appealsCollection,
  initialQuery: { filters: { status: 'all' } },
  toParams: ({ filters, page, limit }) => ({
    page,
    limit,
    status: filters.status === 'all' ? undefined : filters.status,
  }),
  debounceMs: 300,
  autoLoad: true,
})

const { buildFilters, handleFilterUpdate } = useModerationFilters(
  { query, setFilters },
  {
    statusValues: Object.values(AppealStatus),
    statusNamespace: 'moderation.appealStatus',
    includeCategory: false,
    includeEntityType: false,
  },
)
const filters = buildFilters(t)

// Stats
const stats = computed(() => ({
  total: store.appealsTotal,
  pending: store.appeals.filter(
    (a) => a.status === AppealStatus.PENDING || a.status === AppealStatus.UNDER_REVIEW,
  ).length,
}))

// Table columns
const columns = computed(() => {
  const actions: AppealActions = {
    viewAppeal: (appeal) => {
      selectedAppeal.value = appeal
      reviewDecision.value = 'APPROVED'
      reviewResponse.value = ''
      reviewDialogOpen.value = true
    },
    approveAppeal: (appeal) => {
      selectedAppeal.value = appeal
      reviewDecision.value = 'APPROVED'
      reviewResponse.value = ''
      reviewDialogOpen.value = true
    },
    rejectAppeal: (appeal) => {
      selectedAppeal.value = appeal
      reviewDecision.value = 'REJECTED'
      reviewResponse.value = ''
      reviewDialogOpen.value = true
    },
  }
  return createAppealsColumns(t, actions)
})

async function handleReviewSubmit() {
  if (!selectedAppeal.value) return

  reviewLoading.value = true
  try {
    await store.reviewAppeal(selectedAppeal.value.id, {
      decision: reviewDecision.value as AppealStatus.APPROVED | AppealStatus.REJECTED,
      response: reviewResponse.value || undefined,
    })

    toast.success(
      reviewDecision.value === 'APPROVED'
        ? t('moderation.toast.appealApproved')
        : t('moderation.toast.appealRejected'),
    )

    reviewDialogOpen.value = false
    selectedAppeal.value = null
    reviewResponse.value = ''
  } catch (error) {
    console.error('Failed to review appeal:', error)
    toast.error(t('moderation.toast.error'))
  } finally {
    reviewLoading.value = false
  }
}
</script>

<template>
  <div class="relative flex flex-col gap-4 w-full min-w-0">
    <!-- Page header -->
    <div
      :class="[
        'border border-border dark:border-border bg-card',
        'transition-all duration-500',
        isLoaded ? 'opacity-100 translate-y-0' : 'opacity-0 -translate-y-2',
      ]"
    >
      <div class="px-4 lg:px-6 py-4 flex items-center justify-between">
        <h1 class="text-xl font-medium tracking-tight text-foreground">
          {{ t('moderation.appeals.title') }}
        </h1>
        <Button variant="outline" size="sm" @click="loadAppeals" :disabled="loading">
          <IconRefresh :class="['h-3.5 w-3.5', { 'animate-spin': loading }]" />
          <span class="uppercase tracking-wider hidden sm:inline">{{ t('common.refresh') }}</span>
        </Button>
      </div>

      <!-- Stats Ticker -->
      <div
        class="px-4 lg:px-6 py-2.5 flex items-center gap-6 border-t border-border dark:border-border bg-muted"
      >
        <div class="flex items-center gap-2">
          <span class="text-sm font-medium text-muted-foreground"
            >{{ t('moderation.terminal.total') }}:</span
          >
          <span class="font-mono text-sm text-foreground tabular-nums">{{ stats.total }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="text-sm font-medium text-muted-foreground"
            >{{ t('moderation.terminal.pending') }}:</span
          >
          <span class="font-mono text-sm text-foreground tabular-nums">{{ stats.pending }}</span>
        </div>
        <div class="ml-auto flex items-center gap-2 text-muted-foreground">
          <IconScale class="h-4 w-4" />
          <span class="text-xs font-mono uppercase tracking-wider">{{
            t('moderation.appeals.pageTitle')
          }}</span>
        </div>
      </div>
    </div>

    <!-- Main Content -->
    <div
      :class="[
        'flex-1 py-4',
        'transition-all duration-500 delay-200',
        isLoaded ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-2',
      ]"
    >
      <DataTable
        :columns="columns"
        :data="data"
        :pagination="tablePagination"
        :row-count="total"
        :loading="loading"
        v-model:selected-rows="selectedRows"
        @update:pagination="tablePagination = $event"
        :empty-title="t('moderation.appeals.emptyTitle')"
        :empty-description="t('moderation.appeals.emptyDescription')"
        class="w-full text-sm"
      >
        <template #toolbar-left>
          <DataTableToolbar
            v-model:search-model-value="searchQuery"
            :search-placeholder="t('moderation.searchPlaceholder')"
            :filters="filters"
            :loading="loading"
            :on-refresh="loadAppeals"
            @update:filter="handleFilterUpdate"
          />
        </template>
      </DataTable>
    </div>

    <!-- Review Dialog -->
    <Dialog v-model:open="reviewDialogOpen">
      <DialogContent>
        <DialogHeader class="flex items-center gap-2 px-4 py-3 px-4 py-3 -mx-6 -mt-6">
          <DialogTitle class="flex items-center gap-2">
            <IconScale class="h-4 w-4" />
            &gt; {{ t('moderation.appeals.reviewAppeal') }}
          </DialogTitle>
          <DialogDescription> Review the appeal and provide your decision. </DialogDescription>
        </DialogHeader>

        <div v-if="selectedAppeal" class="space-y-4 pt-4">
          <!-- Appeal Info -->
          <div class="border border-border dark:border-border p-4 bg-muted">
            <p class="text-xs font-mono uppercase tracking-wider text-muted-foreground mb-2">
              {{ t('moderation.appeals.reason') }}
            </p>
            <p class="text-sm">{{ selectedAppeal.reason }}</p>
            <p v-if="selectedAppeal.evidence" class="text-xs text-muted-foreground mt-2">
              {{ t('moderation.detail.evidence') }}: {{ selectedAppeal.evidence }}
            </p>
          </div>

          <!-- Decision -->
          <div>
            <Label> Decision </Label>
            <div class="mt-2 flex gap-2">
              <Button
                :variant="reviewDecision === 'APPROVED' ? 'default' : 'outline'"
                :class="[
                  'h-9 font-mono text-xs',
                  reviewDecision === 'APPROVED'
                    ? 'border-status-success-mark text-foreground bg-[color-mix(in_oklch,_var(--status-success-mark)_10%,_transparent)]'
                    : 'border-border hover:border-status-success-mark hover:text-foreground',
                ]"
                size="sm"
                @click="reviewDecision = 'APPROVED'"
              >
                <IconCheck class="h-3.5 w-3.5 mr-1.5" />
                {{ t('moderation.appeals.approveAppeal') }}
              </Button>
              <Button
                :variant="reviewDecision === 'REJECTED' ? 'default' : 'outline'"
                :class="[
                  'h-9 font-mono text-xs',
                  reviewDecision === 'REJECTED'
                    ? 'border-status-error-mark text-foreground bg-[color-mix(in_oklch,_var(--status-error-mark)_10%,_transparent)]'
                    : 'border-border hover:border-status-error-mark hover:text-foreground',
                ]"
                size="sm"
                @click="reviewDecision = 'REJECTED'"
              >
                <IconX class="h-3.5 w-3.5 mr-1.5" />
                {{ t('moderation.appeals.rejectAppeal') }}
              </Button>
            </div>
          </div>

          <!-- Response -->
          <div>
            <Label for="review-response">
              {{ t('moderation.appeals.response') }}
            </Label>
            <Textarea
              id="review-response"
              v-model="reviewResponse"
              :placeholder="t('moderation.appeals.responsePlaceholder')"
              rows="3"
              class="mt-2 placeholder:text-muted-foreground"
            />
          </div>
        </div>

        <DialogFooter class="gap-2">
          <Button variant="outline" size="sm" @click="reviewDialogOpen = false">
            {{ t('moderation.dialogs.cancel') }}
          </Button>
          <Button
            variant="outline"
            size="sm"
            :class="[
              'font-mono text-xs',
              reviewDecision === 'APPROVED'
                ? 'border-status-success-mark text-foreground hover:bg-[color-mix(in_oklch,_var(--status-success-mark)_10%,_transparent)]'
                : 'border-status-error-mark text-foreground hover:bg-[color-mix(in_oklch,_var(--status-error-mark)_10%,_transparent)]',
            ]"
            :disabled="reviewLoading"
            @click="handleReviewSubmit"
          >
            <IconLoader2 v-if="reviewLoading" class="h-3.5 w-3.5 mr-1.5 animate-spin" />
            <IconCheck v-else-if="reviewDecision === 'APPROVED'" class="h-3.5 w-3.5 mr-1.5" />
            <IconX v-else class="h-3.5 w-3.5 mr-1.5" />
            {{ reviewLoading ? t('common.saving') : t('moderation.dialogs.confirm') }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>
