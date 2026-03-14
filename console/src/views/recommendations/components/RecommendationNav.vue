<!-- console/src/views/recommendations/components/RecommendationNav.vue -->
<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Sparkles, Target, Flame, GitBranch } from 'lucide-vue-next'
import { cn } from '@/lib/utils'
import type { RecommendType } from '@/types/recommendation'

const props = defineProps<{
  modelValue: RecommendType
}>()

const router = useRouter()
const { t } = useI18n()

const navItems: { key: RecommendType; label: string; icon: typeof Sparkles }[] = [
  { key: 'daily', label: 'sidebar.recommendation.daily', icon: Sparkles },
  { key: 'weak-points', label: 'sidebar.recommendation.weakPoints', icon: Target },
  { key: 'challenge', label: 'sidebar.recommendation.challenge', icon: Flame },
  { key: 'similar', label: 'sidebar.recommendation.similar', icon: GitBranch },
]

function navigate(key: RecommendType) {
  // Navigation is handled by router, parent uses computed property for modelValue
  router.push({ name: `recommendations-${key}` })
}
</script>

<template>
  <nav class="w-48 shrink-0" aria-label="Recommendation types">
    <div class="sticky top-4 space-y-1">
      <button
        v-for="item in navItems"
        :key="item.key"
        type="button"
        :aria-current="modelValue === item.key ? 'page' : undefined"
        :class="cn(
          'flex w-full items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors',
          modelValue === item.key
            ? 'bg-primary text-primary-foreground'
            : 'hover:bg-muted'
        )"
        @click="navigate(item.key)"
      >
        <component :is="item.icon" class="h-4 w-4" />
        <span>{{ t(item.label) }}</span>
      </button>
    </div>
  </nav>
</template>
