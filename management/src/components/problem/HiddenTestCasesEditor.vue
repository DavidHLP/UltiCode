<script setup lang="ts">
import { onMounted, watch } from 'vue'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog'
import { IconFlask, IconPlus, IconUpload, IconDownload, IconLoader2 } from '@tabler/icons-vue'
import { useTestCases } from './composables/useTestCases'
import TestCaseList from './components/TestCaseList.vue'
import TestCaseDetail from './components/TestCaseDetail.vue'
import TestCaseForm from './components/TestCaseForm.vue'

const props = defineProps<{
  problemId: string
}>()

const {
  testCases,
  loading,
  saving,
  activeId,
  activeTestCase,
  sampleCount,
  hiddenCount,
  editDialogOpen,
  importDialogOpen,
  editingTestCase,
  formData,
  importText,
  replaceExisting,
  importing,
  loadTestCases,
  selectTestCase,
  openCreateDialog,
  openEditDialog,
  saveTestCase,
  deleteTestCase,
  setCaseScope,
  exportTestCases,
  openImportDialog,
  importTestCases,
} = useTestCases(() => props.problemId)

onMounted(() => {
  loadTestCases()
})

watch(
  () => props.problemId,
  () => {
    activeId.value = null
    loadTestCases()
  },
)
</script>

<template>
  <div class="hidden-test-cases-editor">
    <!-- Header -->
    <div class="flex items-center justify-between mb-4">
      <div class="flex items-center gap-2">
        <IconFlask class="h-5 w-5 text-muted-foreground" />
        <h3 class="font-semibold">{{ $t('testCases.title') }}</h3>
        <div class="flex gap-2 ml-2">
          <Badge v-if="sampleCount > 0" variant="secondary" class="text-xs">
            {{ sampleCount }} {{ $t('testCases.sample') }}
          </Badge>
          <Badge v-if="hiddenCount > 0" variant="outline" class="text-xs">
            {{ hiddenCount }} {{ $t('testCases.hidden') }}
          </Badge>
        </div>
      </div>
      <div class="flex items-center gap-2">
        <Button size="sm" variant="ghost" @click="openImportDialog">
          <IconUpload class="h-4 w-4 mr-1" />
          {{ $t('testCases.import') }}
        </Button>
        <Button
          size="sm"
          variant="ghost"
          :disabled="testCases.length === 0"
          @click="exportTestCases"
        >
          <IconDownload class="h-4 w-4 mr-1" />
          {{ $t('testCases.export') }}
        </Button>
        <Button size="sm" @click="openCreateDialog">
          <IconPlus class="h-4 w-4 mr-1" />
          {{ $t('testCases.add') }}
        </Button>
      </div>
    </div>

    <!-- Loading state -->
    <div v-if="loading" class="flex items-center justify-center py-8">
      <IconLoader2 class="h-6 w-6 animate-spin text-muted-foreground" />
      <span class="ml-2 text-muted-foreground">{{ $t('common.loading') }}</span>
    </div>

    <!-- Empty state -->
    <div
      v-else-if="testCases.length === 0"
      class="flex flex-col items-center justify-center py-8 text-muted-foreground"
    >
      <IconFlask class="h-12 w-12 mb-2 opacity-50" />
      <p>{{ $t('testCases.noTestCases') }}</p>
      <Button size="sm" variant="outline" class="mt-2" @click="openCreateDialog">
        <IconPlus class="h-4 w-4 mr-1" />
        {{ $t('testCases.addFirst') }}
      </Button>
    </div>

    <!-- Test cases list with editor -->
    <div v-else class="grid grid-cols-12 gap-4">
      <TestCaseList
        :test-cases="testCases"
        :active-id="activeId"
        @select="selectTestCase"
        @edit="openEditDialog"
        @set-scope="(tc, scope) => setCaseScope(tc, scope)"
        @delete="(tc) => deleteTestCase(tc)"
      />
      <TestCaseDetail
        v-if="activeTestCase"
        :test-case="activeTestCase"
        @edit="openEditDialog"
        @set-scope="(tc, scope) => setCaseScope(tc, scope)"
      />
    </div>

    <!-- Create/Edit Dialog -->
    <TestCaseForm
      :open="editDialogOpen"
      :editing-test-case="editingTestCase"
      :form-data="formData"
      :saving="saving"
      @update:open="editDialogOpen = $event"
      @update:form-data="formData = $event"
      @save="saveTestCase"
    />

    <!-- Import Dialog -->
    <Dialog v-model:open="importDialogOpen">
      <DialogContent class="max-w-2xl">
        <DialogHeader>
          <DialogTitle>{{ $t('testCases.importTestCases') }}</DialogTitle>
        </DialogHeader>

        <div class="space-y-4 py-4">
          <div>
            <Label class="text-sm text-muted-foreground mb-1 block">{{
              $t('testCases.importData')
            }}</Label>
            <Textarea
              v-model="importText"
              :placeholder="$t('testCases.importPlaceholder')"
              class="font-mono text-sm min-h-[200px]"
            />
            <p class="text-xs text-muted-foreground mt-1">{{ $t('testCases.importHelp') }}</p>
          </div>

          <div class="flex items-center gap-2">
            <Checkbox v-model="replaceExisting" id="replace_existing" />
            <Label for="replace_existing" class="text-sm">{{
              $t('testCases.replaceExisting')
            }}</Label>
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" @click="importDialogOpen = false">{{
            $t('common.cancel')
          }}</Button>
          <Button :disabled="importing" @click="importTestCases">
            <IconLoader2 v-if="importing" class="h-4 w-4 mr-1 animate-spin" />
            <IconUpload v-else class="h-4 w-4 mr-1" />
            {{ importing ? $t('testCases.importing') : $t('testCases.import') }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>
