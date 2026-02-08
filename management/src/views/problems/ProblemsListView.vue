<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import {
  IconDownload,
  IconPlus,
  IconUpload,
  IconTrophy,
  IconEye,
  IconEyeOff,
  IconTrash,
  IconRefresh,
  IconPencil,
} from '@tabler/icons-vue'
import { Button } from '@/components/ui/button'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import DataTable from '@/components/table/DataTable.vue'
import DataTableToolbar from '@/components/table/DataTableToolbar.vue'
import EntityActionDialog from '@/components/shared/EntityActionDialog.vue'
import ProblemImportDialog from '@/components/problems/ProblemImportDialog.vue'
import BulkActionDialog from '@/components/problems/BulkActionDialog.vue'
import BulkEditDialog from '@/components/problems/BulkEditDialog.vue'

import { useProblemsFilters } from './composables/useProblemsFilters'
import { useProblemsData } from './composables/useProblemsData'
import { useProblemsActions } from './composables/useProblemsActions'
import { useProblemsUrlSync } from './composables/useProblemsUrlSync'
import ProblemsTable from './components/ProblemsTable.vue'

const router = useRouter()
const authStore = useAuthStore()

// Use composables
const {
  searchQuery,
  difficultyFilter,
  statusFilter,
  publishedFilter,
  sortBy,
  sortOrder,
  toolbarFilters,
} = useProblemsFilters()

const { tablePagination, loading, data, total, error, loadProblems } = useProblemsData({
  searchQuery,
  difficultyFilter,
  statusFilter,
  publishedFilter,
  sortBy,
  sortOrder,
})

const actions = useProblemsActions(loadProblems)

// Setup URL synchronization
useProblemsUrlSync({
  searchQuery,
  difficultyFilter,
  statusFilter,
  publishedFilter,
  sortBy,
  sortOrder,
  tablePagination,
  loadProblems,
})

// Permissions
const canCreateProblem = computed(() => authStore.hasPermission('CREATE', 'PROBLEM'))
const canUpdateProblem = computed(() => authStore.hasPermission('UPDATE', 'PROBLEM'))
const canDeleteProblem = computed(() => authStore.hasPermission('DELETE', 'PROBLEM'))

// Table reference for columns
const problemsTableRef = ref<InstanceType<typeof ProblemsTable>>()
</script>

<template>
  <div class="relative flex flex-col gap-4 overflow-auto px-4 lg:px-6">
    <DataTable
      :columns="problemsTableRef?.columns || []"
      :data="data"
      :pagination="tablePagination"
      :row-count="total"
      :loading="loading"
      :selected-rows="actions.selectedRows.value"
      @update:pagination="tablePagination = $event"
      @update:selected-rows="actions.selectedRows.value = $event"
    >
      <template #toolbar-left>
        <DataTableToolbar
          :search-model-value="searchQuery"
          @update:search-model-value="searchQuery = $event"
          search-placeholder="Search problems..."
          search-width="min-w-[150px] w-full lg:w-[250px]"
          :filters="toolbarFilters"
          @update:filter="
            (index, value) => {
              if (index === 0) difficultyFilter = String(value)
              else if (index === 1) statusFilter = String(value)
              else publishedFilter = String(value)
            }
          "
          :loading="loading"
          :on-refresh="loadProblems"
        >
          <template #extra-actions>
            <Select v-model="sortBy">
              <SelectTrigger class="h-8 w-[150px]">
                <SelectValue placeholder="Sort by" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="default">Default</SelectItem>
                <SelectItem value="title">Title (A-Z)</SelectItem>
                <SelectItem value="difficulty">Difficulty</SelectItem>
                <SelectItem value="created_at">Created (Newest)</SelectItem>
                <SelectItem value="updated_at">Updated (Newest)</SelectItem>
                <SelectItem value="submission_count">Submissions</SelectItem>
              </SelectContent>
            </Select>

            <Button
              variant="ghost"
              size="icon"
              class="h-8 w-8"
              @click="sortOrder = sortOrder === 'asc' ? 'desc' : 'asc'"
              title="Toggle sort order"
            >
              <IconTrophy class="h-3.5 w-3.5" :class="{ 'rotate-180': sortOrder === 'asc' }" />
            </Button>

            <DropdownMenu v-if="actions.selectedRows.value.length > 0">
              <DropdownMenuTrigger as-child>
                <Button variant="outline" size="sm" class="h-8 gap-1.5">
                  <span>{{ actions.selectedRows.value.length }} selected</span>
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                <DropdownMenuItem @click="actions.handleBulkAction('publish')">
                  <IconEye class="mr-2 h-4 w-4 text-emerald-600" />
                  <span>Publish</span>
                </DropdownMenuItem>
                <DropdownMenuItem @click="actions.handleBulkAction('unpublish')">
                  <IconEyeOff class="mr-2 h-4 w-4 text-amber-600" />
                  <span>Unpublish</span>
                </DropdownMenuItem>
                <DropdownMenuItem @click="actions.handleBulkAction('delete')">
                  <IconTrash class="mr-2 h-4 w-4 text-destructive" />
                  <span>Delete</span>
                </DropdownMenuItem>
                <DropdownMenuItem @click="actions.handleBulkAction('restore')">
                  <IconRefresh class="mr-2 h-4 w-4 text-blue-600" />
                  <span>Restore</span>
                </DropdownMenuItem>
                <DropdownMenuSeparator />
                <DropdownMenuItem @click="actions.bulkEditDialogOpen.value = true">
                  <IconPencil class="mr-2 h-4 w-4" />
                  <span>Edit Selected</span>
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </template>
        </DataTableToolbar>
      </template>

      <template #extra-actions>
        <div class="flex items-center gap-2">
          <DropdownMenu>
            <DropdownMenuTrigger as-child>
              <Button variant="outline" size="sm" class="h-8 gap-1.5">
                <IconDownload class="h-4 w-4" />
                <span class="hidden sm:inline">Export</span>
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem
                @click="
                  actions.exportProblems('json', {
                    searchQuery,
                    difficultyFilter,
                    statusFilter,
                    publishedFilter,
                  })
                "
              >
                Export as JSON
              </DropdownMenuItem>
              <DropdownMenuItem
                @click="
                  actions.exportProblems('csv', {
                    searchQuery,
                    difficultyFilter,
                    statusFilter,
                    publishedFilter,
                  })
                "
              >
                Export as CSV
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>

          <Button
            variant="outline"
            size="sm"
            class="h-8 gap-1.5"
            @click="actions.importDialogOpen.value = true"
          >
            <IconUpload class="h-4 w-4" />
            <span class="hidden sm:inline">Import</span>
          </Button>

          <Button
            v-if="canCreateProblem"
            size="sm"
            class="h-8"
            @click="router.push({ name: 'problem-create' })"
          >
            <IconPlus class="mr-2 h-4 w-4" />
            <span>Add Problem</span>
          </Button>
        </div>
      </template>
    </DataTable>

    <!-- Error state -->
    <div
      v-if="error"
      class="flex items-center justify-between rounded-lg border border-destructive/50 bg-destructive/10 p-4"
    >
      <span class="text-destructive">{{ error }}</span>
      <Button variant="outline" size="sm" @click="loadProblems()">Retry</Button>
    </div>
  </div>

  <!-- ProblemsTable component to provide columns -->
  <ProblemsTable
    ref="problemsTableRef"
    :data="data"
    :loading="loading"
    :can-update-problem="canUpdateProblem"
    :can-delete-problem="canDeleteProblem"
    :view-problem="actions.viewProblem"
    :view-problem-code="actions.viewProblemCode"
    :view-problem-cases="actions.viewProblemCases"
    :edit-problem="actions.editProblem"
    :edit-problem-code="actions.editProblemCode"
    :edit-problem-cases="actions.editProblemCases"
    :confirm-delete="actions.confirmDelete"
    :publish-problem="actions.publishProblem"
    :unpublish-problem="actions.unpublishProblem"
    :flag-problem="actions.flagProblem"
    :unflag-problem="actions.unflagProblem"
    class="hidden"
  />

  <EntityActionDialog
    v-model:open="actions.deleteDialogOpen.value"
    :entity-id="actions.selectedProblemId.value"
    :entity-title="actions.selectedProblemTitle.value"
    action="delete"
    title="Delete Problem"
    :description="`Are you sure you want to delete ${actions.selectedProblemTitle.value || 'this problem'}?`"
    confirm-label="Delete"
    cancel-label="Cancel"
    success-label="Problem deleted successfully"
    error-label="Failed to delete problem"
    :on-action="actions.handleDeleteProblem"
    @success="loadProblems"
  />

  <ProblemImportDialog
    v-model:open="actions.importDialogOpen.value"
    @imported="actions.handleImported"
  />

  <BulkActionDialog
    v-model:open="actions.bulkActionDialogOpen.value"
    :action="actions.bulkActionType.value"
    :count="actions.selectedRows.value.length"
    @confirm="actions.confirmBulkAction"
  />

  <BulkEditDialog
    v-model:open="actions.bulkEditDialogOpen.value"
    :problems="actions.selectedRows.value"
    @edited="actions.handleBulkEdited"
  />
</template>
