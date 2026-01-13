<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'

const props = defineProps<{
  formData: {
    title: string
    slug: string
    type: string
    start_time: string
    duration: number
    is_published: boolean
    selectedProblems?: {
      id: string
      title: string
      difficulty: string
      score?: number
    }[]
    [key: string]: unknown
  }
}>()

const { t } = useI18n()

const formattedDate = computed(() => {
  if (!props.formData.start_time) return t('contests.scheduleStep.notSet')
  return new Date(props.formData.start_time).toLocaleString()
})
</script>

<template>
  <div class="space-y-6">
    <div class="grid gap-4 md:grid-cols-2">
      <Card>
        <CardHeader class="pb-2">
          <CardTitle class="text-sm font-medium text-muted-foreground">
            {{ t('contests.reviewStep.basicInfo') }}
          </CardTitle>
        </CardHeader>
        <CardContent class="space-y-2">
          <div>
            <span class="text-xs text-muted-foreground">{{ t('contests.basics.title') }}:</span>
            <p class="font-medium">{{ formData.title }}</p>
          </div>
          <div>
            <span class="text-xs text-muted-foreground">{{ t('contests.basics.slug') }}:</span>
            <p class="font-mono text-sm">{{ formData.slug }}</p>
          </div>
          <div>
            <span class="text-xs text-muted-foreground">{{ t('contests.basics.type') }}:</span>
            <p>
              <Badge variant="outline">{{ t(`contests.type.${formData.type}`) }}</Badge>
            </p>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader class="pb-2">
          <CardTitle class="text-sm font-medium text-muted-foreground">
            {{ t('contests.reviewStep.schedule') }}
          </CardTitle>
        </CardHeader>
        <CardContent class="space-y-2">
          <div>
            <span class="text-xs text-muted-foreground"
              >{{ t('contests.reviewStep.startTime') }}:</span
            >
            <p class="font-medium">{{ formattedDate }}</p>
          </div>
          <div>
            <span class="text-xs text-muted-foreground"
              >{{ t('contests.reviewStep.duration') }}:</span
            >
            <p class="font-medium">{{ formData.duration }} {{ t('common.minutes') }}</p>
          </div>
          <div>
            <span class="text-xs text-muted-foreground"
              >{{ t('contests.reviewStep.visibility') }}:</span
            >
            <p>
              <Badge :variant="formData.is_published ? 'default' : 'secondary'">
                {{
                  formData.is_published
                    ? t('contests.reviewStep.published')
                    : t('contests.reviewStep.draft')
                }}
              </Badge>
            </p>
          </div>
        </CardContent>
      </Card>
    </div>

    <Card>
      <CardHeader class="pb-2">
        <CardTitle class="text-sm font-medium text-muted-foreground">
          {{
            t('contests.reviewStep.problemsCount', {
              count: formData.selectedProblems?.length || 0,
            })
          }}
        </CardTitle>
      </CardHeader>
      <CardContent>
        <div class="space-y-2">
          <div
            v-for="(problem, index) in formData.selectedProblems || []"
            :key="problem.id"
            class="flex items-center justify-between text-sm py-1"
          >
            <div class="flex items-center gap-2">
              <span class="font-mono text-muted-foreground w-4">
                {{ String.fromCharCode(65 + index) }}
              </span>
              <span>{{ problem.title }}</span>
            </div>
            <div class="flex items-center gap-4">
              <Badge variant="outline" class="text-[10px] capitalize">
                {{ problem.difficulty?.toLowerCase() }}
              </Badge>
              <span class="text-muted-foreground w-12 text-right">
                {{ problem.score }} {{ t('contests.drawer.pts') }}</span
              >
            </div>
          </div>
          <div
            v-if="!formData.selectedProblems?.length"
            class="text-sm text-muted-foreground italic"
          >
            {{ t('contests.reviewStep.noProblemsSelected') }}
          </div>
        </div>
      </CardContent>
    </Card>
  </div>
</template>
