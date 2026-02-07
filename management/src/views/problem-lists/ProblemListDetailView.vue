<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAdminProblemListsStore } from '@/stores/admin/problem-lists'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs'
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
</script>

<template>
  <div class="min-h-[calc(100vh-4rem)] bg-background flex flex-col">
    <!-- Header -->
    <header class="sticky top-0 z-10 bg-background/95 backdrop-blur border-b">
      <div class="flex items-center justify-between h-14 px-4 lg:px-6">
        <div class="flex items-center gap-4">
          <Button
            variant="ghost"
            size="icon"
            class="h-8 w-8 text-muted-foreground -ml-2"
            @click="router.push({ name: 'problem-lists' })"
          >
            <ArrowLeft :size="18" />
          </Button>

          <div class="flex items-center gap-3">
            <h1 class="text-sm font-semibold">
              {{
                isCreate ? t('problemLists.createList') : list?.name || t('problemLists.editList')
              }}
            </h1>
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
        <div class="w-12 h-12 rounded-full bg-muted flex items-center justify-center mb-3">
          <FileText :size="24" class="text-muted-foreground" />
        </div>
        <h2 class="text-sm font-semibold mb-1">{{ t('problemLists.errorLoading') }}</h2>
        <p class="text-xs text-muted-foreground mb-4">{{ store.error }}</p>
        <Button variant="outline" size="sm" @click="router.push({ name: 'problem-lists' })">
          {{ t('problemLists.backToLists') }}
        </Button>
      </div>

      <!-- Content -->
      <div v-else class="space-y-6">
        <Tabs v-model="activeTab" class="w-full">
          <TabsList class="grid w-full grid-cols-2 max-w-[400px]">
            <TabsTrigger value="general">{{ t('problemLists.generalInfo') }}</TabsTrigger>
            <TabsTrigger value="problems" :disabled="isCreate">{{
              t('problemLists.problems')
            }}</TabsTrigger>
          </TabsList>

          <TabsContent value="general" class="mt-6">
            <GeneralInfo
              :list="list"
              :mode="isCreate ? 'create' : 'edit'"
              @success="handleCreateSuccess"
            />
          </TabsContent>

          <TabsContent value="problems" class="mt-6">
            <ProblemsManager :list="list" />
          </TabsContent>
        </Tabs>
      </div>
    </main>
  </div>
</template>
