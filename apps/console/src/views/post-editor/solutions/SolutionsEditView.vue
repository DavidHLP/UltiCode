<template>
  <div
    class="flex min-h-dvh w-full flex-col bg-background lg:h-dvh lg:overflow-hidden"
  >
    <!-- 顶部导航栏 -->
    <header
      class="sticky top-0 z-10 flex min-h-14 flex-shrink-0 flex-wrap items-center gap-y-2 border-b bg-background px-4 py-2"
    >
      <Button variant="ghost" size="sm" class="gap-2" @click="handleGoBack">
        <ArrowLeft class="h-4 w-4" />
        {{ t("solution.editor.back") }}
      </Button>
      <div class="flex-1" />
      <span v-if="!initError" class="text-xs text-muted-foreground">
        {{ draftStatus }}
      </span>
      <Button
        size="sm"
        class="ml-4 gap-2"
        :disabled="isPublishing || Boolean(initError)"
        @click="publish"
      >
        <SendHorizonal class="h-4 w-4" />
        {{
          isPublishing
            ? t("solution.editor.publishing")
            : isEditMode
              ? t("solution.editor.update")
              : t("solution.editor.publish")
        }}
      </Button>
    </header>

    <!-- 主体内容 -->
    <main class="flex min-h-0 flex-1 lg:overflow-hidden">
      <!-- Unresolvable-route failure: surface an explicit error instead of
           rendering the editor against uninitialized (empty) refs. -->
      <div
        v-if="initError"
        class="flex w-full flex-col items-center justify-center gap-3 px-6 py-12 text-center"
      >
        <p class="text-sm text-destructive">{{ initError }}</p>
        <Button variant="ghost" size="sm" class="gap-2" @click="handleGoBack">
          <ArrowLeft class="h-4 w-4" />
          {{ t("solution.editor.back") }}
        </Button>
      </div>
      <div v-else class="flex min-h-0 w-full flex-col lg:overflow-hidden">
        <!-- 标题和话题区域 -->
        <div class="flex flex-shrink-0 flex-col gap-3 px-4 py-3">
          <div class="rounded-lg border bg-card p-3">
            <Input
              v-model="title"
              :placeholder="t('solution.editor.enterTitle')"
            />

            <div class="mt-3 flex flex-wrap items-center gap-2">
              <Popover>
                <PopoverTrigger as-child>
                  <Button
                    variant="outline"
                    type="button"
                    class="flex items-center gap-2 px-3"
                  >
                    <Tag class="h-4 w-4" />
                    {{ t("solution.editor.topics") }}
                  </Button>
                </PopoverTrigger>
                <PopoverContent
                  align="start"
                  :aria-label="t('solution.editor.topics')"
                  class="w-80 max-h-(--reka-popover-content-available-height) gap-0 p-0"
                >
                  <div class="shrink-0 border-b border-border px-4 py-3">
                    <h4 class="text-sm font-medium">
                      {{ t("solution.editor.selectTopics") }}
                    </h4>
                  </div>

                  <div
                    v-if="isLoadingTopics"
                    class="flex items-center justify-center py-8"
                  >
                    <span class="text-sm text-muted-foreground">{{
                      t("solution.editor.loading")
                    }}</span>
                  </div>
                  <div v-else-if="topicLoadError" class="py-8 text-center">
                    <p class="text-sm text-destructive">{{ topicLoadError }}</p>
                  </div>
                  <div
                    v-else-if="!topicOptions.length"
                    class="py-8 text-center"
                  >
                    <p class="text-sm text-muted-foreground">
                      {{ t("solution.editor.noTopics") }}
                    </p>
                  </div>
                  <div v-else class="min-h-0 max-h-64 overflow-y-auto">
                    <div class="p-2">
                      <Button
                        variant="outline"
                        v-for="topic in topicOptions"
                        :key="topic.id"
                        :aria-pressed="selectedTopicIds.includes(topic.id)"
                        type="button"
                        class="flex w-full items-center justify-between px-2 py-1.5"
                        @click="toggleTopic(topic.id)"
                      >
                        <span>{{ topic.name }}</span>
                        <Check
                          v-if="selectedTopicIds.includes(topic.id)"
                          class="h-4 w-4"
                        />
                      </Button>
                    </div>
                  </div>
                </PopoverContent>
              </Popover>

              <span
                v-for="topic in selectedTopics"
                :key="topic.id"
                class="inline-flex items-center gap-1.5 rounded-lg bg-secondary px-2 py-1 text-sm"
              >
                {{ topic.name }}
                <Button
                  variant="outline"
                  type="button"
                  :aria-label="
                    t('solution.editor.removeTopic', { topic: topic.name })
                  "
                  class="inline-flex w-4 items-center justify-center"
                  @click="removeTopic(topic.id)"
                >
                  <X class="h-3 w-3" />
                </Button>
              </span>
            </div>
          </div>
        </div>

        <div class="min-h-0 flex-1 px-4 pb-4 lg:overflow-hidden">
          <div class="grid grid-cols-1 gap-4 lg:h-full lg:grid-cols-2">
            <!-- Monaco Editor Container -->
            <div class="h-96 min-h-0 min-w-0 lg:h-auto">
              <MarkdownEdit
                v-model="editorContent"
                :default-value="dynamicTemplate"
              />
            </div>

            <!-- Markdown Preview -->
            <div
              class="flex min-h-0 min-w-0 flex-col rounded-lg border bg-card overflow-hidden"
            >
              <div
                class="flex items-center border-b bg-muted/30 px-3 py-2 text-xs font-medium text-muted-foreground"
              >
                {{ t("solution.editor.preview") }}
              </div>
              <div class="flex-1 overflow-y-auto p-4">
                <MarkdownView :content="editorContent" />
              </div>
            </div>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from "vue";
import { useRouter } from "vue-router";
import { SendHorizonal, Tag, X, ArrowLeft, Check } from "@lucide/vue";
import { Input } from "@ulticode/design-system/ui/input";
import { Button } from "@ulticode/design-system/ui/button";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@ulticode/design-system/ui/popover";
import { useI18n } from "vue-i18n";

import { MarkdownEdit, MarkdownView } from "@/components/markdown";
import { useSolutionAuthoring } from "@/composables/useSolutionAuthoring";

const router = useRouter();
const { t } = useI18n();

const {
  title,
  editorContent,
  dynamicTemplate,
  isEditMode,
  isPublishing,
  initError,
  draftStatus,
  topicOptions,
  selectedTopicIds,
  selectedTopics,
  isLoadingTopics,
  topicLoadError,
  init,
  toggleTopic,
  removeTopic,
  publish,
} = useSolutionAuthoring({
  onPublishSuccess: ({ problemSlug }) => {
    if (problemSlug) {
      router.push({
        name: "problem-detail",
        params: { slug: problemSlug, tab: "solution" },
      });
    } else {
      router.back();
    }
  },
  onGateFailure: ({ problemSlug }) => {
    router.push({
      name: "problem-detail",
      params: { slug: problemSlug ?? "", tab: "solution" },
    });
  },
  onCollisionRecovery: ({ solutionId }) => {
    router.push({
      name: "solution-edit",
      params: { id: solutionId },
    });
  },
});

onMounted(() => {
  void init();
});

const handleGoBack = () => {
  router.back();
};
</script>

<style>
.markdown-content h1 {
  font-size: var(--uc-text-2xl);
  font-weight: var(--uc-font-weight-bold);
  margin-top: 1rem;
  margin-bottom: 0.5rem;
}

.markdown-content h2 {
  font-size: var(--uc-text-xl);
  font-weight: var(--uc-font-weight-semibold);
  margin-top: 0.875rem;
  margin-bottom: 0.5rem;
}

.markdown-content blockquote {
  border-left: 4px solid var(--color-border);
  padding-left: 1rem;
  color: var(--text-secondary);
  margin: 0.5rem 0;
}

.markdown-content ul {
  list-style: disc;
  margin-left: 1.5rem;
}

.markdown-content ol {
  list-style: decimal;
  margin-left: 1.5rem;
}

.markdown-content pre {
  background-color: var(--color-secondary);
  padding: 1rem;
  border-radius: 0.5rem;
  overflow-x: auto;
  margin: 1rem 0;
}

.markdown-content code {
  font-family: var(--uc-font-code);
  font-size: var(--uc-text-sm);
}

.markdown-content pre code {
  background-color: transparent;
  padding: 0;
}

.markdown-content table {
  width: 100%;
  border-collapse: collapse;
  margin: 1rem 0;
}

.markdown-content th,
.markdown-content td {
  border: 1px solid var(--color-border);
  padding: 0.5rem;
  text-align: left;
}

.markdown-content th {
  background-color: var(--color-muted);
}

.markdown-content a {
  color: var(--color-primary);
  text-decoration: underline;
}
</style>
