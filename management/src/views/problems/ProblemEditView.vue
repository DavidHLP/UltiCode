<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import EditDescriptionView from './edit/EditDescriptionView.vue'
import EditCodeView from './edit/EditCodeView.vue'
import EditCasesView from './edit/EditCasesView.vue'

const route = useRoute()
const router = useRouter()

// Valid tab values for edit mode
const VALID_TABS = ['description', 'code', 'cases'] as const
type TabType = (typeof VALID_TABS)[number]

const problemId = computed(() => route.params.id as string)

// Determine current tab from route
const currentTab = computed<TabType>(() => {
  const tab = route.params.tab as string
  if (VALID_TABS.includes(tab as TabType)) {
    return tab as TabType
  }
  return 'description'
})

// Redirect to default tab if no tab specified
onMounted(() => {
  if (!route.params.tab) {
    router.replace({
      name: 'problem-edit',
      params: { id: problemId.value, tab: 'description' },
    })
  }
})
</script>

<template>
  <!-- Render the appropriate edit view based on current tab -->
  <!-- Each edit view has its own header and layout -->
  <EditDescriptionView v-if="currentTab === 'description'" />
  <EditCodeView v-else-if="currentTab === 'code'" />
  <EditCasesView v-else-if="currentTab === 'cases'" />
</template>
