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

const router = useRouter()
const route = useRoute()
const { t } = useI18n()
const store = useAdminProblemListsStore()

const isInitialLoad = ref(true)
const listId = computed(() => route.params.id as string)
const isCreate = computed(() => route.name === 'problem-list-create')

const list = computed({
  get: () => store.currentList,
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

function handleListUpdate(updatedList: typeof store.currentList) {
  store.currentList = updatedList
}
</script>

<template>
  <div class="min-h-[calc(100vh-4rem)] bg-background flex flex-col">
    <!-- Terminal Header -->
    <header
      class="sticky top-0 z-10 bg-[var(--card)]/95 backdrop-blur border-b border-[var(--silver-200)]"
    >
      <div class="flex items-center justify-between h-14 px-4 lg:px-6">
        <div class="flex items-center gap-4">
          <Button
            variant="ghost"
            size="icon"
            class="h-8 w-8 text-[var(--silver-400)] -ml-2 hover:bg-[var(--silver-100)] dark:hover:bg-[var(--silver-800)]"
            @click="back"
          >
            <ArrowLeft :size="18" />
          </Button>
          <span class="terminal-prompt">{{ isCreate ? 'new_list' : 'edit_list' }}</span>
          <span class="terminal-cursor" />
          <h1 class="text-sm font-semibold text-[var(--foreground)]">
            {{ isCreate ? t('problemLists.createList') : list?.name || t('problemLists.editList') }}
          </h1>
        </div>
      </div>

      <!-- Status Ticker (edit mode only) -->
      <div
        v-if="list && !isCreate"
        class="px-4 lg:px-6 py-2.5 border-t border-[var(--silver-200)] bg-[var(--surface-sunken)]"
      >
        <div class="flex items-center gap-6 text-xs">
          <div class="flex items-center gap-2">
            <span class="terminal-label">{{ t('problemLists.status.visibility') }}:</span>
            <span
              :class="
                list.isPublic
                  ? 'font-data text-[var(--terminal-green)]'
                  : 'font-data text-[var(--silver-400)]'
              "
            >
              {{
                list.isPublic ? t('problemLists.status.public') : t('problemLists.status.private')
              }}
            </span>
          </div>
          <div class="flex items-center gap-2">
            <span class="terminal-label">{{ t('problemLists.status.problems') }}:</span>
            <span class="font-data text-[var(--terminal-cyan)]">{{
              list.problems?.length || 0
            }}</span>
          </div>
          <div v-if="list.isFeatured" class="flex items-center gap-2">
            <span class="terminal-label">{{ t('problemLists.status.featured') }}:</span>
            <span class="font-data text-[var(--terminal-amber)]">{{
              t('problemLists.status.featured')
            }}</span>
          </div>
        </div>
      </div>
    </header>

    <!-- Main Content -->
    <main class="flex-1 w-full max-w-5xl mx-auto p-4 lg:p-6 lg:pt-8">
      <!-- Loading State -->
      <div v-if="isInitialLoad || (store.isLoading && !list && !isCreate)" class="space-y-6">
        <div class="space-y-4">
          <Skeleton class="h-12 w-1/3 rounded-lg" />
          <Skeleton class="h-64 w-full rounded-xl" />
        </div>
      </div>

      <!-- Error State -->
      <div
        v-else-if="store.error && !isCreate"
        class="flex flex-col items-center justify-center py-24 text-center"
      >
        <div
          class="w-12 h-12 rounded-full border-2 border-[var(--terminal-red)] flex items-center justify-center mb-3"
        >
          <FileText :size="24" class="text-[var(--terminal-red)]" />
        </div>
        <h2 class="text-sm font-semibold mb-1 text-[var(--foreground)]">
          {{ t('problemLists.errorLoading') }}
        </h2>
        <p class="text-xs font-data text-[var(--silver-400)] mb-4">{{ store.error }}</p>
        <Button
          variant="outline"
          size="sm"
          class="font-data text-xs border-[var(--silver-300)]"
          @click="back"
        >
          {{ t('problemLists.backToLists') }}
        </Button>
      </div>

      <!-- Content -->
      <div v-else class="space-y-8">
        <!-- Basic Info Section -->
        <BasicInfoSection
          :model-value="list"
          :disabled="isCreate"
          :is-create="isCreate"
          @update:model-value="handleListUpdate"
          @success="handleCreateSuccess"
        />

        <!-- Visibility Section -->
        <VisibilitySection
          :model-value="list"
          :disabled="isCreate"
          @update:model-value="handleListUpdate"
        />

        <!-- Banner Section -->
        <BannerSection
          :model-value="list"
          :disabled="isCreate"
          @update:model-value="handleListUpdate"
        />

        <!-- Problems Manager -->
        <ProblemsManager :list="list" />
      </div>
    </main>
  </div>
</template>
