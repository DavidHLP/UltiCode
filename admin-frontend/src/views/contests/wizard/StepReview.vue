<script setup lang="ts">
import { computed } from 'vue'
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

const formattedDate = computed(() => {
  if (!props.formData.start_time) return 'Not set'
  return new Date(props.formData.start_time).toLocaleString()
})
</script>

<template>
  <div class="space-y-6">
    <div class="grid gap-4 md:grid-cols-2">
      <Card>
        <CardHeader class="pb-2">
          <CardTitle class="text-sm font-medium text-muted-foreground"> Basic Info </CardTitle>
        </CardHeader>
        <CardContent class="space-y-2">
          <div>
            <span class="text-xs text-muted-foreground">Title:</span>
            <p class="font-medium">{{ formData.title }}</p>
          </div>
          <div>
            <span class="text-xs text-muted-foreground">Slug:</span>
            <p class="font-mono text-sm">{{ formData.slug }}</p>
          </div>
          <div>
            <span class="text-xs text-muted-foreground">Type:</span>
            <p>
              <Badge variant="outline">{{ formData.type }}</Badge>
            </p>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader class="pb-2">
          <CardTitle class="text-sm font-medium text-muted-foreground">Schedule</CardTitle>
        </CardHeader>
        <CardContent class="space-y-2">
          <div>
            <span class="text-xs text-muted-foreground">Start Time:</span>
            <p class="font-medium">{{ formattedDate }}</p>
          </div>
          <div>
            <span class="text-xs text-muted-foreground">Duration:</span>
            <p class="font-medium">{{ formData.duration }} minutes</p>
          </div>
          <div>
            <span class="text-xs text-muted-foreground">Visibility:</span>
            <p>
              <Badge :variant="formData.is_published ? 'default' : 'secondary'">
                {{ formData.is_published ? 'Published' : 'Draft' }}
              </Badge>
            </p>
          </div>
        </CardContent>
      </Card>
    </div>

    <Card>
      <CardHeader class="pb-2">
        <CardTitle class="text-sm font-medium text-muted-foreground">
          Problems ({{ formData.selectedProblems?.length || 0 }})
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
              <span class="text-muted-foreground w-12 text-right"> {{ problem.score }} pts </span>
            </div>
          </div>
          <div
            v-if="!formData.selectedProblems?.length"
            class="text-sm text-muted-foreground italic"
          >
            No problems selected.
          </div>
        </div>
      </CardContent>
    </Card>
  </div>
</template>
