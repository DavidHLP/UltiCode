<script setup lang="ts">
import { onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible'
import { IconChevronDown, IconLock, IconLoader2 } from '@tabler/icons-vue'
import { ref } from 'vue'
import { testCasesApi, type TestCase } from '@/api/admin/test-cases'

/**
 * Read-only admin view of hidden judge cases for a problem.
 *
 * Uses the same admin endpoint as {@link HiddenTestCasesEditor}
 * (`GET /admin/problems/{id}/test-cases?isHidden=true`) but renders in
 * view-only mode — no edit / create / delete actions.
 *
 * **Visibility contract** (ADR-001, task #3 P0-1):
 *   - This component is admin-only (mounted under `ViewCasesView`, reached
 *     only from `ProblemDetailView` which requires `ROLE_ADMIN` /
 *     `ROLE_SUPER_ADMIN` via `@PreAuthorize` on the backend).
 *   - It MUST NOT be imported or rendered anywhere in `console/` (user-facing).
 *     Submission responses on the user side go through `SubmissionServiceImpl
 *     .toVO()` which strips HIDDEN rows via `isUserVisible(scope)` — the
 *     frontend never has a HIDDEN case to render on the user side.
 */
const props = defineProps<{
  problemId: string
}>()

const { t } = useI18n()

const testCases = ref<TestCase[]>([])
const loading = ref(false)
const expanded = ref(true)

const hiddenCases = computed(() => testCases.value.filter((tc) => tc.isHidden && !tc.isSample))

async function loadHiddenCases() {
  loading.value = true
  try {
    const response = await testCasesApi.getTestCases(props.problemId, {
      isHidden: true,
      limit: 1000,
    })
    testCases.value = response.items
  } catch (error) {
    console.error('Failed to load hidden test cases:', error)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadHiddenCases()
})
</script>

<template>
  <Collapsible v-model:open="expanded" class="w-full">
    <Card>
      <CollapsibleTrigger as-child>
        <CardHeader
          class="cursor-pointer flex flex-row items-center justify-between space-y-0 pb-2"
        >
          <div class="flex items-center gap-2">
            <IconLock class="h-4 w-4 text-muted-foreground" />
            <CardTitle class="text-base font-medium">
              {{ t('testCases.view.hiddenSectionTitle', { count: hiddenCases.length }) }}
            </CardTitle>
          </div>
          <div class="flex items-center gap-2">
            <Badge variant="outline" class="text-xs">
              {{ t('testCases.count.hidden') }}
            </Badge>
            <IconChevronDown
              class="h-4 w-4 transition-transform"
              :class="{ 'rotate-180': expanded }"
            />
          </div>
        </CardHeader>
      </CollapsibleTrigger>
      <CollapsibleContent>
        <CardContent class="space-y-3">
          <p class="text-xs text-muted-foreground">
            {{ t('testCases.view.hiddenSectionHelp') }}
          </p>

          <div v-if="loading" class="flex items-center justify-center py-6 text-muted-foreground">
            <IconLoader2 class="h-4 w-4 animate-spin mr-2" />
            <span class="text-sm">{{ t('common.loading') }}</span>
          </div>

          <div
            v-else-if="hiddenCases.length === 0"
            class="text-sm text-muted-foreground text-center py-4"
          >
            {{ t('testCases.view.noCases') }}
          </div>

          <ul v-else class="space-y-2">
            <li
              v-for="(tc, idx) in hiddenCases"
              :key="tc.id"
              class="border border-[var(--silver-200)] dark:border-[var(--silver-300)] rounded-md p-3 space-y-2"
            >
              <div class="flex items-center justify-between text-xs text-muted-foreground">
                <span class="font-mono">#{{ idx + 1 }}</span>
                <span v-if="tc.testOrder !== undefined" class="font-mono">
                  order: {{ tc.testOrder }}
                </span>
              </div>
              <div class="grid grid-cols-1 lg:grid-cols-2 gap-2">
                <div>
                  <div class="text-xs font-medium text-muted-foreground mb-1">
                    {{ t('testCases.input') }}
                  </div>
                  <pre
                    class="font-mono text-xs bg-muted px-2 py-1 rounded overflow-x-auto whitespace-pre-wrap break-all"
                  ><code>{{ tc.inputText }}</code></pre>
                </div>
                <div>
                  <div class="text-xs font-medium text-muted-foreground mb-1">
                    {{ t('testCases.output') }}
                  </div>
                  <pre
                    class="font-mono text-xs bg-muted px-2 py-1 rounded overflow-x-auto whitespace-pre-wrap break-all"
                  ><code>{{ tc.outputText }}</code></pre>
                </div>
              </div>
              <div v-if="tc.explanation" class="text-xs text-muted-foreground">
                <span class="font-medium">{{ t('testCases.explanation') }}:</span>
                {{ tc.explanation }}
              </div>
            </li>
          </ul>
        </CardContent>
      </CollapsibleContent>
    </Card>
  </Collapsible>
</template>
