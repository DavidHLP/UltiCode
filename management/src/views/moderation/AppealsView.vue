<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { toast } from 'vue-sonner'

import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { IconRefresh, IconScale, IconLoader2, IconCheck, IconX } from '@tabler/icons-vue'

import DataTable from '@/components/table/DataTable.vue'
import DataTableToolbar, { type Filter } from '@/components/table/DataTableToolbar.vue'

import { useModerationStore } from '@/stores/admin/moderation'
import { type Appeal, AppealStatus, type QueryAppealsParams } from '@/api/admin/moderation'
import { useDataTable } from '@/composables/useDataTable'
import { createAppealsColumns, type AppealActions } from './appeals-columns'

const { t } = useI18n()
const store = useModerationStore()

const isLoaded = ref(false)

// Filters
const statusFilter = ref<AppealStatus | 'all'>('all')

// Review dialog state
const reviewDialogOpen = ref(false)
const selectedAppeal = ref<Appeal | null>(null)
const reviewDecision = ref<'APPROVED' | 'REJECTED'>('APPROVED')
const reviewResponse = ref('')
const reviewLoading = ref(false)

onMounted(() => {
  setTimeout(() => {
    isLoaded.value = true
  }, 100)
})

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

// Filter configuration
const filters = computed<Filter[]>(() => [
  {
    modelValue: statusFilter.value,
    placeholder: t('moderation.appealStatus.title'),
    options: [
      { value: 'all', label: t('moderation.appealStatus.all') },
      { value: AppealStatus.PENDING, label: t('moderation.appealStatus.PENDING') },
      { value: AppealStatus.UNDER_REVIEW, label: t('moderation.appealStatus.UNDER_REVIEW') },
      { value: AppealStatus.APPROVED, label: t('moderation.appealStatus.APPROVED') },
      { value: AppealStatus.REJECTED, label: t('moderation.appealStatus.REJECTED') },
    ],
    width: 'w-[160px]',
  },
])

const {
  searchQuery,
  tablePagination,
  selectedRows,
  loading,
  data,
  total,
  loadEntities: loadAppeals,
} = useDataTable<Appeal, { status: AppealStatus | 'all' }, QueryAppealsParams>({
  store: {
    data: computed(() => store.appeals),
    total: computed(() => store.appealsTotal),
    isLoading: computed(() => store.appealsLoading),
    error: computed(() => store.appealsError),
    fetch: (params) => store.fetchAppeals(params),
  },
  filters: () => ({ status: statusFilter.value }),
  transformParams: ({ filters, page, limit }) => ({
    page,
    limit,
    status: filters.status === 'all' ? undefined : filters.status,
  }),
  debounceMs: 300,
  autoLoad: true,
})

function handleFilterUpdate(index: number, value: string | number) {
  if (index === 0) statusFilter.value = value as AppealStatus | 'all'
}

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
    <!-- Terminal Header -->
    <div
      :class="[
        'border border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--card)]',
        'transition-all duration-500',
        isLoaded ? 'opacity-100 translate-y-0' : 'opacity-0 -translate-y-2',
      ]"
    >
      <div class="px-4 lg:px-6 py-4 flex items-center justify-between">
        <h1 class="text-xl font-medium tracking-tight text-[var(--foreground)]">
          {{ t('moderation.appeals.title') }}
        </h1>
        <Button
          variant="terminal"
          size="sm"
          class="font-data text-xs border-[var(--silver-300)] hover:border-[var(--accent-electric)] hover:text-[var(--accent-electric)] transition-colors"
          @click="loadAppeals"
          :disabled="loading"
        >
          <IconRefresh :class="['h-3.5 w-3.5', { 'animate-spin': loading }]" />
          <span class="uppercase tracking-wider hidden sm:inline">{{ t('common.refresh') }}</span>
        </Button>
      </div>

      <!-- Stats Ticker -->
      <div
        class="px-4 lg:px-6 py-2.5 flex items-center gap-6 border-t border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--surface-sunken)]"
      >
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]"
            >{{ t('moderation.terminal.total') }}:</span
          >
          <span class="font-data text-sm text-[var(--terminal-cyan)] tabular-nums">{{
            stats.total
          }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]"
            >{{ t('moderation.terminal.pending') }}:</span
          >
          <span class="font-data text-sm text-[var(--terminal-amber)] tabular-nums">{{
            stats.pending
          }}</span>
        </div>
        <div class="ml-auto flex items-center gap-2 text-[var(--silver-400)]">
          <IconScale class="h-4 w-4" />
          <span class="text-xs font-data uppercase tracking-wider">{{ t('moderation.appeals.pageTitle') }}</span>
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
        class="terminal-table"
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
      <DialogContent class="terminal-card border-[var(--silver-300)]">
        <DialogHeader
          class="terminal-card-header border-b border-[var(--silver-300)] bg-[var(--surface-sunken)] px-4 py-3 -mx-6 -mt-6"
        >
          <DialogTitle
            class="flex items-center gap-2 font-data text-sm uppercase tracking-wider text-[var(--terminal-purple)]"
          >
            <IconScale class="h-4 w-4" />
            &gt; {{ t('moderation.appeals.reviewAppeal') }}
          </DialogTitle>
          <DialogDescription class="font-data text-xs text-[var(--silver-400)]">
            Review the appeal and provide your decision.
          </DialogDescription>
        </DialogHeader>

        <div v-if="selectedAppeal" class="space-y-4 pt-4">
          <!-- Appeal Info -->
          <div
            class="border border-[var(--silver-200)] dark:border-[var(--silver-300)] p-4 bg-[var(--surface-sunken)]"
          >
            <p class="text-xs font-data uppercase tracking-wider text-[var(--silver-500)] mb-2">
              {{ t('moderation.appeals.reason') }}
            </p>
            <p class="text-sm">{{ selectedAppeal.reason }}</p>
            <p v-if="selectedAppeal.evidence" class="text-xs text-[var(--silver-500)] mt-2">
              {{ t('moderation.detail.evidence') }}: {{ selectedAppeal.evidence }}
            </p>
          </div>

          <!-- Decision -->
          <div>
            <Label class="text-xs font-data uppercase tracking-wider text-[var(--silver-500)]">
              Decision
            </Label>
            <div class="mt-2 flex gap-2">
              <Button
                :variant="reviewDecision === 'APPROVED' ? 'default' : 'terminal'"
                :class="[
                  'h-9 font-data text-xs',
                  reviewDecision === 'APPROVED'
                    ? 'border-[var(--terminal-green)] text-[var(--terminal-green)] bg-[color-mix(in_oklch,_var(--terminal-green)_10%,_transparent)]'
                    : 'border-[var(--silver-300)] hover:border-[var(--terminal-green)] hover:text-[var(--terminal-green)]',
                ]"
                size="sm"
                @click="reviewDecision = 'APPROVED'"
              >
                <IconCheck class="h-3.5 w-3.5 mr-1.5" />
                {{ t('moderation.appeals.approveAppeal') }}
              </Button>
              <Button
                :variant="reviewDecision === 'REJECTED' ? 'default' : 'terminal'"
                :class="[
                  'h-9 font-data text-xs',
                  reviewDecision === 'REJECTED'
                    ? 'border-[var(--terminal-red)] text-[var(--terminal-red)] bg-[color-mix(in_oklch,_var(--terminal-red)_10%,_transparent)]'
                    : 'border-[var(--silver-300)] hover:border-[var(--terminal-red)] hover:text-[var(--terminal-red)]',
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
            <Label
              for="review-response"
              class="text-xs font-data uppercase tracking-wider text-[var(--silver-500)]"
            >
              {{ t('moderation.appeals.response') }}
            </Label>
            <Textarea
              id="review-response"
              v-model="reviewResponse"
              :placeholder="t('moderation.appeals.responsePlaceholder')"
              rows="3"
              class="mt-2 font-data text-sm border-[var(--silver-300)] hover:border-[var(--accent-electric)] bg-transparent placeholder:text-[var(--silver-400)]"
            />
          </div>
        </div>

        <DialogFooter class="gap-2">
          <Button
            variant="terminal"
            size="sm"
            class="font-data text-xs border-[var(--silver-300)] hover:border-[var(--silver-500)]"
            @click="reviewDialogOpen = false"
          >
            {{ t('moderation.dialogs.cancel') }}
          </Button>
          <Button
            variant="terminal"
            size="sm"
            :class="[
              'font-data text-xs',
              reviewDecision === 'APPROVED'
                ? 'border-[var(--terminal-green)] text-[var(--terminal-green)] hover:bg-[color-mix(in_oklch,_var(--terminal-green)_10%,_transparent)]'
                : 'border-[var(--terminal-red)] text-[var(--terminal-red)] hover:bg-[color-mix(in_oklch,_var(--terminal-red)_10%,_transparent)]',
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
