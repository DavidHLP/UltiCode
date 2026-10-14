<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAdminProblemListsStore } from '@/stores/admin/problem-lists'
import { Button } from '@ulticode/design-system/ui/button'
import { Skeleton } from '@ulticode/design-system/ui/skeleton'
import { ArrowLeft, FileText } from '@lucide/vue'
import BasicInfoSection from './components/BasicInfoSection.vue'
import VisibilitySection from './components/VisibilitySection.vue'
import BannerSection from './components/BannerSection.vue'
import ProblemsManager from './components/ProblemsManager.vue'
import type { ProblemListDetail } from '@/api/admin/problem-lists'

const router = useRouter()
const route = useRoute()
const { t } = useI18n()
const store = useAdminProblemListsStore()

const isInitialLoad = ref(true)
const listId = computed(() => route.params.id as string)
const isCreate = computed(() => route.name === 'problem-list-create')

const list = computed({
  get: () => store.currentList as ProblemListDetail | null,
  set: (val) => {
    store.currentList = val
  },
})

onMounted(async () => {
  if (!isCreate.value && listId.value) {
    try {
      await store.fetchList(listId.value)
    } catch {
      // Error handled by store
    } finally {
      isInitialLoad.value = false
    }
  } else {
    isInitialLoad.value = false
    store.currentList = null
  }
})

function handleCreateSuccess(id: string) {
  router.replace({ name: 'problem-list-edit', params: { id } })
}

function back() {
  router.push({ name: 'problem-lists' })
}

function handleListUpdate(updatedList: ProblemListDetail | null) {
  store.currentList = updatedList
}
</script>

<template>
  <div class="editor-container w-full flex flex-col gap-5 lg:gap-6">
    <!-- Refined Header Section -->
    <div class="flex flex-col gap-2.5 pb-2 border-b border-border select-none">
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-3">
          <!-- Back button using correct translations -->
          <Button
            variant="outline"
            size="sm"
            class="px-2.5 cursor-pointer flex items-center gap-1.5"
            @click="back"
          >
            <ArrowLeft :size="12" />
            <span>{{ t('common.back') }}</span>
          </Button>
          <span class="text-muted-foreground font-mono opacity-50">/</span>
          <!-- Prominent Title -->
          <h1 class="text-base lg:text-lg font-bold font-sans text-foreground tracking-tight">
            {{ isCreate ? t('problemLists.createList') : list?.name || t('problemLists.editList') }}
          </h1>
        </div>
      </div>

      <!-- Muted Status Ticker Metadata Line -->
      <div
        v-if="list && !isCreate"
        class="flex flex-wrap items-center gap-2 font-mono text-xs text-muted-foreground"
      >
        <span
          class="font-bold uppercase tracking-wider"
          :class="list.isPublic ? 'text-status-success-mark' : 'text-status-warning-mark'"
        >
          {{ list.isPublic ? t('problemLists.status.public') : t('problemLists.status.private') }}
        </span>
        <span class="opacity-40">•</span>
        <span>
          {{ list.problems?.length || 0 }}
          {{
            t('problemLists.problemsManager.problemsCount', {
              count: list.problems?.length || 0,
            }).split(' ')[1] || '题目'
          }}
        </span>
        <template v-if="list.isFeatured">
          <span class="opacity-40">•</span>
          <span class="text-status-warning-mark font-bold uppercase tracking-wider">
            {{ t('problemLists.status.featured') }}
          </span>
        </template>
      </div>
    </div>

    <!-- Main Editor Interface -->
    <div class="w-full">
      <!-- Loading State -->
      <div v-if="isInitialLoad || (store.operationLoading && !list && !isCreate)" class="space-y-6">
        <div class="space-y-4">
          <Skeleton class="h-10 w-1/3" />
          <Skeleton class="h-48 w-full" />
        </div>
      </div>

      <!-- Error State -->
      <div
        v-else-if="store.operationError && !isCreate"
        class="flex flex-col items-center justify-center py-20 text-center border border-border bg-card"
      >
        <div
          class="w-10 h-10 border border-status-error-mark flex items-center justify-center mb-3 text-status-error-mark"
        >
          <FileText :size="20" />
        </div>
        <h2 class="text-xs font-bold mb-1 text-foreground font-mono uppercase tracking-wider">
          {{ t('problemLists.errorLoading') }}
        </h2>
        <p class="text-xs font-mono text-muted-foreground mb-4">
          {{ store.operationError }}
        </p>
        <Button variant="outline" size="sm" @click="back">
          {{ t('problemLists.backToLists') }}
        </Button>
      </div>

      <!-- Content -->
      <div v-else>
        <!-- Create Mode: Centered form -->
        <div v-if="isCreate" class="max-w-2xl mx-auto w-full">
          <BasicInfoSection
            :model-value="list"
            :disabled="isCreate"
            :is-create="isCreate"
            @update:model-value="handleListUpdate"
            @success="handleCreateSuccess"
          />
        </div>

        <!-- Edit Mode: Dense editorial grid -->
        <div v-else class="problem-list-editor-grid">
          <div class="editor-grid-basic">
            <BasicInfoSection
              :model-value="list"
              :disabled="isCreate"
              :is-create="isCreate"
              @update:model-value="handleListUpdate"
              @success="handleCreateSuccess"
            />
          </div>

          <div class="editor-grid-visibility">
            <VisibilitySection
              :model-value="list"
              :disabled="isCreate"
              @update:model-value="handleListUpdate"
            />
          </div>

          <div class="editor-grid-banner">
            <BannerSection
              :model-value="list"
              :disabled="isCreate"
              @update:model-value="handleListUpdate"
            />
          </div>

          <div class="editor-grid-problems">
            <ProblemsManager :list="list" />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style>
.problem-list-editor-grid {
  display: grid;
  grid-template-areas:
    'basic'
    'visibility'
    'banner'
    'problems';
  gap: 1.5rem;
  align-items: start;
}

.editor-grid-basic,
.editor-grid-visibility,
.editor-grid-banner,
.editor-grid-problems {
  min-width: 0;
}

.editor-grid-basic {
  grid-area: basic;
}

.editor-grid-visibility {
  grid-area: visibility;
}

.editor-grid-banner {
  grid-area: banner;
}

.editor-grid-problems {
  grid-area: problems;
}

@media (min-width: 1024px) {
  .problem-list-editor-grid {
    grid-template-columns: minmax(0, 2fr) minmax(320px, 1fr);
    grid-template-areas:
      'basic visibility'
      'problems banner';
  }
}
</style>
