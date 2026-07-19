<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { Label } from '@/components/ui/label'
import { Switch } from '@/components/ui/switch'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { IconToggleLeft } from '@tabler/icons-vue'
import type { FeatureToggles } from '@/api/admin/settings'

const { t } = useI18n()

defineProps<{
  settings: FeatureToggles
}>()

const emit = defineEmits<{
  'update:settings': [patch: Partial<FeatureToggles>]
}>()

const featureToggles: { key: keyof FeatureToggles; labelKey: string; descKey: string }[] = [
  {
    key: 'feature_contest',
    labelKey: 'settings.features.contest',
    descKey: 'settings.features.contestDescription',
  },
  {
    key: 'feature_forum',
    labelKey: 'settings.features.forum',
    descKey: 'settings.features.forumDescription',
  },
  {
    key: 'feature_solutions',
    labelKey: 'settings.features.solutions',
    descKey: 'settings.features.solutionsDescription',
  },
  {
    key: 'feature_subscriptions',
    labelKey: 'settings.features.subscriptions',
    descKey: 'settings.features.subscriptionsDescription',
  },
  {
    key: 'feature_achievements',
    labelKey: 'settings.features.achievements',
    descKey: 'settings.features.achievementsDescription',
  },
  {
    key: 'feature_notifications',
    labelKey: 'settings.features.notifications',
    descKey: 'settings.features.notificationsDescription',
  },
  {
    key: 'feature_bookmarks',
    labelKey: 'settings.features.bookmarks',
    descKey: 'settings.features.bookmarksDescription',
  },
  {
    key: 'feature_problem_lists',
    labelKey: 'settings.features.problemLists',
    descKey: 'settings.features.problemListsDescription',
  },
]

function updateField<K extends keyof FeatureToggles>(key: K, value: FeatureToggles[K]) {
  emit('update:settings', { [key]: value })
}
</script>

<template>
  <Card>
    <CardHeader>
      <div class="flex items-center gap-2">
        <IconToggleLeft class="h-5 w-5 text-muted-foreground" />
        <CardTitle>{{ t('settings.features.title') }}</CardTitle>
      </div>
      <CardDescription>{{ t('settings.features.description') }}</CardDescription>
    </CardHeader>
    <CardContent>
      <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
        <div
          v-for="toggle in featureToggles"
          :key="toggle.key"
          class="flex items-center justify-between space-x-2"
        >
          <Label class="flex flex-col space-y-1">
            <span>{{ t(toggle.labelKey) }}</span>
            <span class="font-normal text-xs text-muted-foreground">{{ t(toggle.descKey) }}</span>
          </Label>
          <Switch
            :checked="Boolean(settings[toggle.key])"
            @update:checked="updateField(toggle.key, $event)"
          />
        </div>
      </div>
    </CardContent>
  </Card>
</template>
