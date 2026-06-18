<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAdminProblemListsStore } from '@/stores/admin/problem-lists'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { ArrowLeft, FileText } from 'lucide-vue-next'
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
    <div class="flex flex-col gap-2.5 pb-2 border-b border-[var(--editor-border-weak)] select-none">
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-3">
          <!-- Back button using correct translations -->
          <Button
            variant="outline"
            size="sm"
            class="custom-back-btn h-7 px-2.5 font-mono text-xs text-[var(--editor-text-muted)] border-[var(--editor-control-border)] bg-[var(--editor-control-bg)] hover:bg-[var(--editor-panel-bg)] hover:text-[var(--editor-text-primary)] rounded-none cursor-pointer flex items-center gap-1.5 transition-all"
            @click="back"
          >
            <ArrowLeft :size="12" />
            <span>{{ t('common.back') }}</span>
          </Button>
          <span class="text-[var(--editor-text-muted)] font-mono opacity-50">/</span>
          <!-- Prominent Title -->
          <h1
            class="text-base lg:text-lg font-bold font-sans text-[var(--editor-text-primary)] tracking-tight"
          >
            {{ isCreate ? t('problemLists.createList') : list?.name || t('problemLists.editList') }}
          </h1>
        </div>
      </div>

      <!-- Muted Status Ticker Metadata Line -->
      <div
        v-if="list && !isCreate"
        class="flex flex-wrap items-center gap-2 font-mono text-xxs text-[var(--editor-text-muted)]"
      >
        <span
          class="font-bold uppercase tracking-wider"
          :class="list.isPublic ? 'text-[var(--editor-green)]' : 'text-[var(--editor-yellow)]'"
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
          <span class="text-[var(--editor-yellow)] font-bold uppercase tracking-wider">
            {{ t('problemLists.status.featured') }}
          </span>
        </template>
      </div>
    </div>

    <!-- Main Editor Interface -->
    <div class="w-full">
      <!-- Loading State -->
      <div v-if="isInitialLoad || (store.isLoading && !list && !isCreate)" class="space-y-6">
        <div class="space-y-4">
          <Skeleton class="h-10 w-1/3 rounded-none" />
          <Skeleton class="h-48 w-full rounded-none" />
        </div>
      </div>

      <!-- Error State -->
      <div
        v-else-if="store.error && !isCreate"
        class="flex flex-col items-center justify-center py-20 text-center border border-[var(--editor-panel-border)] bg-[var(--editor-panel-bg)]"
      >
        <div
          class="w-10 h-10 border border-[var(--editor-red)] flex items-center justify-center mb-3 text-[var(--editor-red)]"
        >
          <FileText :size="20" />
        </div>
        <h2
          class="text-xs font-bold mb-1 text-[var(--editor-text-primary)] font-mono uppercase tracking-wider"
        >
          {{ t('problemLists.errorLoading') }}
        </h2>
        <p class="text-xs font-mono text-[var(--editor-text-muted)] mb-4">{{ store.error }}</p>
        <Button
          variant="outline"
          size="sm"
          class="font-mono text-xs border-[var(--editor-control-border)] rounded-none"
          @click="back"
        >
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
/* CSS Variable Hierarchy for Problem List Editor */
.editor-container {
  /* Solarized Light Theme Variables */
  --editor-panel-bg: var(--solarized-base3);
  --editor-panel-border: var(--silver-300); /* base0 - #839496 */
  --editor-border-weak: var(--silver-200); /* base1 - #93a1a1 */
  --editor-control-bg: var(--solarized-base2); /* base2 - sunken appearance */
  --editor-control-border: var(--silver-200);
  --editor-text-primary: var(--solarized-base03); /* base03 - #002b36 */
  --editor-text-muted: var(--solarized-base01); /* base01 - #586e75 */

  /* Semantic Accent Colors */
  --editor-cyan: var(--solarized-cyan); /* #2aa198 */
  --editor-blue: var(--solarized-blue); /* #268bd2 */
  --editor-green: var(--solarized-green); /* #859900 */
  --editor-yellow: var(--solarized-yellow); /* #b58900 */
  --editor-red: var(--solarized-red); /* #dc322f */
}

.dark .editor-container {
  /* Solarized Dark Theme Variables */
  --editor-panel-bg: var(--solarized-base02);
  --editor-panel-border: var(--silver-700); /* base01 - #586e75 */
  --editor-border-weak: rgba(88, 110, 117, 0.22); /* very weak dividers */
  --editor-control-bg: var(--solarized-base03); /* base03 - sunken appearance */
  --editor-control-border: rgba(88, 110, 117, 0.45);
  --editor-text-primary: var(--solarized-base0); /* base0 - #839496 */
  --editor-text-muted: var(--solarized-base01); /* base01 - #586e75 */
}

.custom-back-btn {
  border-radius: 0 !important;
}

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
