<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAdminProblemListsStore } from '@/stores/admin/problem-lists'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { ArrowLeft, FileText } from 'lucide-vue-next'
import GeneralInfo from './components/GeneralInfo.vue'
import ProblemsManager from './components/ProblemsManager.vue'

const router = useRouter()
const route = useRoute()
const { t } = useI18n()
const store = useAdminProblemListsStore()

const isInitialLoad = ref(true)
const listId = computed(() => route.params.id as string)
const isCreate = computed(() => route.name === 'problem-list-create')
const activeTab = ref('general')

const list = computed(() => store.currentList)

const tabs = computed(() => [
  { value: 'general', label: t('problemLists.generalInfo') },
  { value: 'problems', label: t('problemLists.problems'), disabled: isCreate.value },
])

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
                list.is_public
                  ? 'font-data text-[var(--terminal-green)]'
                  : 'font-data text-[var(--silver-400)]'
              "
            >
              {{
                list.is_public ? t('problemLists.status.public') : t('problemLists.status.private')
              }}
            </span>
          </div>
          <div class="flex items-center gap-2">
            <span class="terminal-label">{{ t('problemLists.status.problems') }}:</span>
            <span class="font-data text-[var(--terminal-cyan)]">{{
              list.problems?.length || 0
            }}</span>
          </div>
          <div v-if="list.is_featured" class="flex items-center gap-2">
            <span class="terminal-label">{{ t('problemLists.status.status') }}:</span>
            <span class="font-data text-[var(--terminal-amber)]">{{
              t('problemLists.status.featured')
            }}</span>
          </div>
        </div>
      </div>
    </header>

    <!-- Terminal Tabs Navigation -->
    <div class="border-b border-[var(--silver-200)] bg-[var(--card)]">
      <div class="px-4 lg:px-6 flex gap-1">
        <button
          v-for="tab in tabs"
          :key="tab.value"
          :disabled="tab.disabled"
          :class="[
            'px-4 py-3 font-data text-xs uppercase tracking-[0.05em] border-b-2 transition-colors',
            activeTab === tab.value
              ? 'border-[var(--accent-electric)] text-[var(--foreground)]'
              : 'border-transparent text-[var(--silver-400)] hover:text-[var(--silver-600)] dark:hover:text-[var(--silver-300)]',
            tab.disabled ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer',
          ]"
          @click="!tab.disabled && (activeTab = tab.value)"
        >
          <span v-if="activeTab === tab.value" class="text-[var(--accent-electric)]">//</span>
          {{ tab.label }}
        </button>
      </div>
    </div>

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
      <div v-else>
        <!-- General Tab -->
        <div v-show="activeTab === 'general'">
          <GeneralInfo
            :list="list"
            :mode="isCreate ? 'create' : 'edit'"
            @success="handleCreateSuccess"
          />
        </div>

        <!-- Problems Tab -->
        <div v-show="activeTab === 'problems'">
          <ProblemsManager :list="list" />
        </div>
      </div>
    </main>
  </div>
</template>
