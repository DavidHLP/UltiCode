<script setup lang="ts">
import { onMounted, computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { IconCode } from '@tabler/icons-vue'
import { useProblemsStore } from '@/stores/admin/problems'
import CodeDisplay from '../components/CodeDisplay.vue'

const route = useRoute()
const { t } = useI18n()
const problemsStore = useProblemsStore()

const isLoaded = ref(false)

const problemId = computed(() => route.params.id as string)
const codeData = computed(() => problemsStore.codeData)

onMounted(async () => {
  if (problemId.value && !codeData.value) {
    await problemsStore.fetchCode(problemId.value)
  }
  setTimeout(() => {
    isLoaded.value = true
  }, 100)
})
</script>

<i18n lang="json">
{
  "en-US": {
    "viewLabel": "view:",
    "codeView": "code",
    "languageTemplates": "language templates",
    "fetchingData": "fetching problem data..."
  },
  "zh-CN": {
    "viewLabel": "视图:",
    "codeView": "代码",
    "languageTemplates": "语言模板",
    "fetchingData": "正在获取题目数据..."
  }
}
</i18n>

<template>
  <div class="relative flex flex-col gap-4 w-full min-w-0">
    <!-- Terminal Header (only visible on mobile or when not in parent view) -->
    <div
      :class="[
        'border border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--card)] lg:hidden',
        'transition-all duration-500',
        isLoaded ? 'opacity-100 translate-y-0' : 'opacity-0 -translate-y-2',
      ]"
    >
      <!-- Info Ticker -->
      <div class="px-4 lg:px-6 py-2.5 flex items-center gap-6 bg-[var(--surface-sunken)]">
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]">{{ t('viewLabel') }}</span>
          <span class="font-data text-sm text-[var(--terminal-cyan)]">{{ t('codeView') }}</span>
        </div>
        <div class="ml-auto hidden sm:flex items-center gap-2 text-[var(--silver-400)]">
          <IconCode class="h-4 w-4" />
          <span class="text-xs font-data uppercase tracking-wider">{{
            t('languageTemplates')
          }}</span>
        </div>
      </div>
    </div>

    <!-- Main Content -->
    <div class="flex-1">
      <div v-if="codeData" class="space-y-4">
        <CodeDisplay :languages="codeData.languages" />
      </div>

      <!-- Loading State - Terminal Style -->
      <div v-else class="flex flex-col items-center justify-center py-24 text-center">
        <div
          class="w-12 h-12 rounded-full bg-[var(--surface-sunken)] border border-[var(--silver-200)] dark:border-[var(--silver-300)] flex items-center justify-center mb-3"
        >
          <div
            class="h-6 w-6 animate-spin rounded-full border-2 border-[var(--accent-electric)] border-t-transparent"
          ></div>
        </div>
        <h2 class="text-sm font-medium mb-1 font-data">{{ t('problems.view.loading') }}</h2>
        <p class="text-xs text-[var(--silver-500)] font-data">// {{ t('fetchingData') }}</p>
      </div>
    </div>
  </div>
</template>
