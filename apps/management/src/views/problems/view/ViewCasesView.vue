<script setup lang="ts">
import { onMounted, computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { FlaskConical as IconFlask } from '@lucide/vue'

import { useProblemsStore } from '@/stores/admin/problems'
import CasesDisplay from '../components/CasesDisplay.vue'
import HiddenCasesView from '@/components/problem/HiddenCasesView.vue'

const route = useRoute()
const { t } = useI18n()
const problemsStore = useProblemsStore()

const isLoaded = ref(false)

const problemId = computed(() => route.params.id as string)
const problem = computed(() => problemsStore.casesData)

onMounted(async () => {
  if (problemId.value && !problem.value) {
    await problemsStore.fetchCases(problemId.value)
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
    "testCasesView": "test_cases",
    "testData": "test data",
    "fetchingData": "fetching problem data..."
  },
  "zh-CN": {
    "viewLabel": "视图:",
    "testCasesView": "测试用例",
    "testData": "测试数据",
    "fetchingData": "正在获取题目数据..."
  }
}
</i18n>

<template>
  <div class="relative flex flex-col gap-4 w-full min-w-0">
    <!-- Terminal Header (only visible on mobile or when not in parent view) -->
    <div
      :class="[
        'border border-border dark:border-border bg-card lg:hidden',
        'transition-all duration-500',
        isLoaded ? 'opacity-100 translate-y-0' : 'opacity-0 -translate-y-2',
      ]"
    >
      <!-- Page context -->
      <div class="px-4 lg:px-6 py-2.5 flex items-center gap-6 bg-muted">
        <div class="flex items-center gap-2">
          <span class="text-sm font-medium text-muted-foreground">{{ t('viewLabel') }}</span>
          <span class="font-mono text-sm text-foreground">{{ t('testCasesView') }}</span>
        </div>
        <div class="ml-auto hidden sm:flex items-center gap-2 text-muted-foreground">
          <IconFlask class="h-4 w-4" />
          <span class="text-xs font-mono uppercase tracking-wider">{{ t('testData') }}</span>
        </div>
      </div>
    </div>

    <!-- Main Content -->
    <div class="flex-1">
      <div v-if="problem" class="space-y-4">
        <CasesDisplay :problem="problem" />
        <!--
          Admin-only hidden cases viewer (read-only). Per P0-1
          backend projection, this is the ONLY frontend surface that may
          render hidden case data — the admin authn path is enforced by
          @PreAuthorize on AdminTestCaseController. console/ never imports
          this component.
        -->
        <HiddenCasesView :problem-id="problemId" />
      </div>

      <!-- Loading State - Terminal Style -->
      <div v-else class="flex flex-col items-center justify-center py-24 text-center">
        <div
          class="w-12 h-12 rounded-full bg-muted border border-border dark:border-border flex items-center justify-center mb-3"
        >
          <div
            class="h-6 w-6 animate-spin rounded-full border-2 border-primary border-t-transparent"
          ></div>
        </div>
        <h2 class="text-sm font-medium mb-1 font-mono">{{ t('problems.view.loading') }}</h2>
        <p class="text-xs text-muted-foreground font-mono">// {{ t('fetchingData') }}</p>
      </div>
    </div>
  </div>
</template>
