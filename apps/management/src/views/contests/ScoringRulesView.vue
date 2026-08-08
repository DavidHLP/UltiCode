<script setup lang="ts">
import { ref, computed, onMounted, h } from 'vue'
import { useI18n } from 'vue-i18n'
import type { ColumnDef } from '@tanstack/vue-table'
import { toast } from 'vue-sonner'
import {
  IconCalculator,
  IconCircleXFilled,
  IconDotsVertical,
  IconPencil,
  IconPlus,
  IconRefresh,
  IconStar,
  IconTrash,
} from '@tabler/icons-vue'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { createSelectionColumn } from '@/components/table/selectionColumn'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Switch } from '@/components/ui/switch'
import { useAuthStore } from '@/stores/auth'
import {
  scoringRulesApi,
  type ScoringRule,
  type CreateScoringRuleDto,
  type UpdateScoringRuleDto,
} from '@/api/admin/scoring-rules'

import DataTable from '@/components/table/DataTable.vue'
import ScoringRuleForm from './components/ScoringRuleForm.vue'
import EntityActionDialog from '@/components/shared/EntityActionDialog.vue'
import type { PaginationState } from '@tanstack/vue-table'

const { t } = useI18n()
const authStore = useAuthStore()

// State
const searchQuery = ref('')
const includeInactive = ref(false)
const loading = ref(false)
const error = ref<string | null>(null)
const scoringRules = ref<ScoringRule[]>([])
const tablePagination = ref<PaginationState>({
  pageIndex: 0,
  pageSize: 10,
})
const selectedRows = ref<ScoringRule[]>([])

// Dialogs
const formDialogOpen = ref(false)
const selectedRule = ref<ScoringRule | null>(null)
const deleteDialogOpen = ref(false)
const selectedRuleName = ref<string | null>(null)

// Animation state for staggered reveal
const isLoaded = ref(false)

onMounted(() => {
  setTimeout(() => {
    isLoaded.value = true
  }, 100)
  loadScoringRules()
})

const canManageRules = computed(
  () =>
    authStore.hasPermission('MANAGE_USERS', 'SYSTEM') ||
    authStore.hasPermission('UPDATE', 'CONTEST'),
)

// Stats for terminal ticker
const stats = computed(() => {
  const rules = scoringRules.value
  const total = rules.length
  const active = rules.filter((r) => r.isActive).length
  const defaults = rules.filter((r) => r.isDefault).length
  const inactive = rules.filter((r) => !r.isActive).length
  return { total, active, defaults, inactive }
})

async function loadScoringRules() {
  loading.value = true
  error.value = null
  try {
    scoringRules.value = await scoringRulesApi.getAll(includeInactive.value)
  } catch (err) {
    error.value = t('scoringRules.loadError')
    console.error(err)
  } finally {
    loading.value = false
  }
}

function openCreateDialog() {
  selectedRule.value = null
  formDialogOpen.value = true
}

function openEditDialog(rule: ScoringRule) {
  selectedRule.value = rule
  formDialogOpen.value = true
}

function openDeleteDialog(rule: ScoringRule) {
  selectedRule.value = rule
  selectedRuleName.value = rule.name
  deleteDialogOpen.value = true
}

async function handleSaveForm(data: CreateScoringRuleDto | UpdateScoringRuleDto) {
  try {
    if (selectedRule.value) {
      await scoringRulesApi.update(selectedRule.value.id, data)
      toast.success(t('scoringRules.toast.updatedSuccessfully'))
    } else {
      await scoringRulesApi.create(data as CreateScoringRuleDto)
      toast.success(t('scoringRules.toast.createdSuccessfully'))
    }
    formDialogOpen.value = false
    await loadScoringRules()
  } catch (err) {
    toast.error(
      selectedRule.value
        ? t('scoringRules.toast.failedToUpdate')
        : t('scoringRules.toast.failedToCreate'),
    )
    console.error(err)
  }
}

async function handleDeleteRule(id: string | number) {
  await scoringRulesApi.delete(String(id))
}

async function handleSetDefault(rule: ScoringRule) {
  try {
    await scoringRulesApi.update(rule.id, { isDefault: true })
    toast.success(t('scoringRules.toast.setDefaultSuccess'))
    await loadScoringRules()
  } catch (err) {
    toast.error(t('scoringRules.toast.failedToSetDefault'))
    console.error(err)
  }
}

const columns: ColumnDef<ScoringRule>[] = [
  ...createSelectionColumn<ScoringRule>(t),
  {
    accessorKey: 'name',
    header: () => t('scoringRules.columns.name'),
    cell: ({ row }) => {
      const rule = row.original
      return h('div', { class: 'flex items-center gap-3' }, [
        h(
          'div',
          {
            class:
              'h-9 w-9 rounded-none flex items-center justify-center bg-primary/10 text-primary',
          },
          [h(IconCalculator, { class: 'h-4 w-4' })],
        ),
        h('div', { class: 'flex flex-col' }, [
          h('div', { class: 'flex items-center gap-2' }, [
            h('span', { class: 'font-medium text-sm' }, rule.name),
            rule.isDefault
              ? h(Badge, { variant: 'default', class: 'text-xs' }, () =>
                  t('scoringRules.badges.default'),
                )
              : null,
            !rule.isActive
              ? h(Badge, { variant: 'secondary', class: 'text-xs' }, () =>
                  t('scoringRules.badges.inactive'),
                )
              : null,
          ]),
          rule.description
            ? h(
                'span',
                { class: 'text-muted-foreground text-xs truncate max-w-[200px]' },
                rule.description,
              )
            : null,
        ]),
      ])
    },
  },
  {
    accessorKey: 'baseScorePerProblem',
    header: () => t('scoringRules.columns.baseScore'),
    cell: ({ row }) =>
      h(Badge, { variant: 'outline' }, () => row.original.baseScorePerProblem.toString()),
  },
  {
    accessorKey: 'timeBonusPerMinute',
    header: () => t('scoringRules.columns.timeBonus'),
    cell: ({ row }) =>
      h(Badge, { variant: 'outline' }, () => row.original.timeBonusPerMinute.toString()),
  },
  {
    accessorKey: 'wrongAnswerPenalty',
    header: () => t('scoringRules.columns.wrongPenalty'),
    cell: ({ row }) =>
      h(Badge, { variant: 'outline' }, () => row.original.wrongAnswerPenalty.toString()),
  },
  {
    accessorKey: 'firstSolveBonus',
    header: () => t('scoringRules.columns.firstSolveBonus'),
    cell: ({ row }) =>
      h(Badge, { variant: 'outline' }, () => row.original.firstSolveBonus.toString()),
  },
  {
    id: 'actions',
    header: () => t('scoringRules.columns.actions'),
    cell: ({ row }) => {
      const rule = row.original
      return h(
        DropdownMenu,
        {},
        {
          default: () => [
            h(
              DropdownMenuTrigger,
              { asChild: true },
              {
                default: () =>
                  h(
                    Button,
                    { variant: 'ghost', size: 'icon', class: 'h-8 w-8 p-0' },
                    {
                      default: () => [
                        h('span', { class: 'sr-only' }, t('common.open')),
                        h(IconDotsVertical, { class: 'h-4 w-4' }),
                      ],
                    },
                  ),
              },
            ),
            h(
              DropdownMenuContent,
              { align: 'end' },
              {
                default: () =>
                  [
                    canManageRules.value &&
                      !rule.isDefault &&
                      h(
                        DropdownMenuItem,
                        { onClick: () => handleSetDefault(rule) },
                        {
                          default: () =>
                            h('div', { class: 'flex items-center gap-2' }, [
                              h(IconStar, { class: 'h-4 w-4' }),
                              t('scoringRules.actions.setDefault'),
                            ]),
                        },
                      ),
                    canManageRules.value &&
                      h(
                        DropdownMenuItem,
                        { onClick: () => openEditDialog(rule) },
                        {
                          default: () =>
                            h('div', { class: 'flex items-center gap-2' }, [
                              h(IconPencil, { class: 'h-4 w-4' }),
                              t('scoringRules.actions.edit'),
                            ]),
                        },
                      ),
                    canManageRules.value && h(DropdownMenuSeparator),
                    canManageRules.value &&
                      h(
                        DropdownMenuItem,
                        { onClick: () => openDeleteDialog(rule) },
                        {
                          default: () =>
                            h('div', { class: 'flex items-center gap-2 text-destructive' }, [
                              h(IconTrash, { class: 'h-4 w-4' }),
                              t('scoringRules.actions.delete'),
                            ]),
                        },
                      ),
                    !canManageRules.value &&
                      h(
                        DropdownMenuItem,
                        { disabled: true },
                        { default: () => t('scoringRules.actions.noActionsAvailable') },
                      ),
                  ].filter(Boolean),
              },
            ),
          ],
        },
      )
    },
  },
]
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
        <h1 class="text-xl font-medium tracking-tight text-[var(--foreground)]">
          {{ t('scoringRules.title') }}
        </h1>
        <Button
          v-if="canManageRules"
          variant="terminal"
          size="sm"
          class="font-data text-xs border-[var(--silver-300)] hover:border-[var(--accent-electric)] hover:text-[var(--accent-electric)] transition-colors"
          @click="openCreateDialog"
        >
          <IconPlus class="h-4 w-4 mr-1.5" />
          <span class="uppercase tracking-wider">{{ t('scoringRules.createRule') }}</span>
        </Button>
      </div>

      <!-- Stats Ticker -->
      <div
        class="px-4 lg:px-6 py-2.5 flex items-center gap-6 border-t border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--surface-sunken)]"
      >
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]"
            >{{ t('scoringRules.stats.total') }}:</span
          >
          <span class="font-data text-sm text-[var(--terminal-cyan)] tabular-nums">{{
            stats.total
          }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]"
            >{{ t('scoringRules.stats.active') }}:</span
          >
          <span class="font-data text-sm text-[var(--terminal-green)] tabular-nums">{{
            stats.active
          }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]"
            >{{ t('scoringRules.stats.defaults') }}:</span
          >
          <span class="font-data text-sm text-[var(--terminal-amber)] tabular-nums">{{
            stats.defaults
          }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]"
            >{{ t('scoringRules.stats.inactive') }}:</span
          >
          <span class="font-data text-sm text-[var(--silver-400)] tabular-nums">{{
            stats.inactive
          }}</span>
        </div>
        <div class="ml-auto flex items-center gap-2 text-[var(--silver-400)]">
          <IconCalculator class="h-4 w-4" />
          <span class="text-xs font-data uppercase tracking-wider">{{
            t('scoringRules.stats.scoringManagement')
          }}</span>
        </div>
      </div>
    </div>

    <!-- Main Content Area -->
    <div class="flex-1 py-4 px-4 lg:px-6">
      <!-- Toolbar -->
      <div class="flex items-center justify-between mb-4">
        <div class="flex items-center gap-2">
          <Input
            v-model="searchQuery"
            :placeholder="t('scoringRules.searchPlaceholder')"
            class="min-w-[200px] w-[260px]"
          >
            <template #trailing>
              <button
                v-if="searchQuery"
                @click="searchQuery = ''"
                class="rounded-none opacity-70 hover:opacity-100"
              >
                <IconCircleXFilled class="h-4 w-4" />
              </button>
            </template>
          </Input>
          <div class="flex items-center gap-2 px-3 py-1.5 border rounded-none">
            <Switch
              :checked="includeInactive"
              @update:checked="
                (val: boolean) => {
                  includeInactive = val
                  loadScoringRules()
                }
              "
              class="data-[state=checked]:bg-primary"
            />
            <span class="text-xs text-muted-foreground">{{ t('scoringRules.showInactive') }}</span>
          </div>
        </div>
        <Button
          variant="outline"
          size="icon"
          @click="loadScoringRules()"
          :title="t('common.refresh')"
        >
          <IconRefresh class="h-4 w-4" :class="{ 'animate-spin': loading }" />
        </Button>
      </div>

      <!-- Bulk Action Bar -->
      <div
        v-if="selectedRows.length > 0"
        :class="[
          'mb-4 flex items-center justify-between border border-[var(--terminal-amber)] bg-[color-mix(in_oklch,_var(--terminal-amber)_8%,_transparent)] dark:bg-[color-mix(in_oklch,_var(--terminal-amber)_15%,_transparent)] p-3',
          'animate-in fade-in slide-in-from-top-2 duration-200',
        ]"
      >
        <div class="flex items-center gap-4">
          <div class="flex items-center gap-2">
            <span class="font-data text-sm text-[var(--terminal-amber)]">
              &gt; SELECTED:{{ selectedRows.length }}
            </span>
          </div>
        </div>
        <Button
          variant="terminal"
          size="sm"
          class="h-8 font-data text-xs text-[var(--silver-500)] hover:text-[var(--foreground)]"
          @click="selectedRows = []"
        >
          [ESC] {{ t('common.clearSelection') }}
        </Button>
      </div>

      <DataTable
        :columns="columns"
        :data="scoringRules"
        :pagination="tablePagination"
        :row-count="scoringRules.length"
        :loading="loading"
        v-model:selected-rows="selectedRows"
        @update:pagination="tablePagination = $event"
        :empty-title="t('scoringRules.emptyTitle')"
        :empty-description="t('scoringRules.emptyDescription')"
        class="terminal-table"
      />

      <!-- Error state - Terminal Style -->
      <div
        v-if="error"
        class="mt-4 flex items-center justify-between border border-[var(--terminal-red)] bg-[color-mix(in_oklch,_var(--terminal-red)_8%,_transparent)] p-4"
      >
        <div class="flex items-center gap-3">
          <span class="font-data text-sm text-[var(--terminal-red)]">&gt; ERROR:</span>
          <span class="text-sm text-[var(--foreground)]">{{ error }}</span>
        </div>
        <Button
          variant="terminal"
          size="sm"
          class="font-data text-xs border-[var(--terminal-red)] text-[var(--terminal-red)] hover:bg-[color-mix(in_oklch,_var(--terminal-red)_10%,_transparent)]"
          @click="loadScoringRules()"
        >
          {{ t('common.retry') }}
        </Button>
      </div>
    </div>
  </div>

  <ScoringRuleForm
    v-model:open="formDialogOpen"
    :rule-to-edit="selectedRule"
    @save="handleSaveForm"
    @cancel="formDialogOpen = false"
  />

  <EntityActionDialog
    v-model:open="deleteDialogOpen"
    :entity-id="selectedRule?.id || null"
    :entity-title="selectedRuleName"
    action="delete"
    :title="t('scoringRules.delete.title')"
    :description="
      t('scoringRules.delete.description', {
        name: selectedRuleName || t('scoringRules.delete.thisRule'),
      })
    "
    :confirm-label="t('scoringRules.delete.confirm')"
    :cancel-label="t('common.cancel')"
    :success-label="t('scoringRules.toast.deletedSuccessfully')"
    :error-label="t('scoringRules.toast.failedToDelete')"
    :on-action="handleDeleteRule"
    @success="loadScoringRules"
  />
</template>
