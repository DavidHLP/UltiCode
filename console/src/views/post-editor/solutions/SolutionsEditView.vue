<template>
  <div class="flex h-screen w-full flex-col overflow-hidden bg-background">
    <!-- 顶部导航栏 -->
    <header class="flex h-14 flex-shrink-0 items-center border-b px-4">
      <Button variant="ghost" size="sm" class="gap-2" @click="handleGoBack">
        <ArrowLeft class="h-4 w-4" />
        {{ t("solution.editor.back") }}
      </Button>
      <div class="flex-1" />
      <span class="text-xs text-muted-foreground">
        {{ draftStatus }}
      </span>
      <Button size="sm" class="ml-4 gap-2" @click="publish">
        <SendHorizonal class="h-4 w-4" />
        {{
          isEditMode
            ? t("solution.editor.update")
            : t("solution.editor.publish")
        }}
      </Button>
    </header>

    <!-- 主体内容 -->
    <main class="flex flex-1 overflow-hidden">
      <div class="flex w-full flex-col overflow-hidden">
        <!-- 标题和话题区域 -->
        <div class="flex flex-shrink-0 flex-col gap-3 px-4 py-3">
          <div class="rounded-none border bg-card p-3">
            <Input
              v-model="title"
              :placeholder="t('solution.editor.enterTitle')"
              class="rounded-none border-0 border-b bg-transparent px-0 text-base font-medium shadow-none focus-visible:ring-0"
            />

            <div class="mt-3 flex flex-wrap items-center gap-2">
              <div class="relative">
                <button
                  type="button"
                  class="flex h-8 items-center gap-2 rounded-none border border-border bg-background px-3 text-sm hover:bg-muted"
                  @click="showTopicPicker = !showTopicPicker"
                >
                  <Tag class="h-4 w-4" />
                  {{ t("solution.editor.topics") }}
                </button>
                <div
                  v-if="showTopicPicker"
                  class="absolute left-0 top-10 z-50 w-80 rounded-none border border-border bg-card shadow-lg"
                >
                  <div class="border-b border-border px-4 py-3">
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
                  <div v-else class="max-h-64 overflow-y-auto">
                    <div class="p-2">
                      <button
                        v-for="topic in topicOptions"
                        :key="topic.id"
                        type="button"
                        class="flex w-full items-center justify-between rounded px-2 py-1.5 text-sm hover:bg-muted"
                        @click="toggleTopic(topic.id)"
                      >
                        <span>{{ topic.name }}</span>
                        <Check
                          v-if="selectedTopicIds.includes(topic.id)"
                          class="h-4 w-4"
                        />
                      </button>
                    </div>
                  </div>
                </div>
              </div>

              <span
                v-for="topic in selectedTopics"
                :key="topic.id"
                class="inline-flex items-center gap-1.5 rounded-none bg-secondary px-2 py-1 text-sm"
              >
                {{ topic.name }}
                <button
                  type="button"
                  class="inline-flex h-4 w-4 items-center justify-center hover:opacity-70"
                  @click="removeTopic(topic.id)"
                >
                  <X class="h-3 w-3" />
                </button>
              </span>
            </div>
          </div>
        </div>

        <div class="flex-1 px-4 pb-4 overflow-hidden">
          <div class="grid h-full grid-cols-2 gap-4">
            <!-- Monaco Editor Container -->
            <MarkdownEdit
              v-model="editorContent"
              :default-value="dynamicTemplate"
            />

            <!-- Markdown Preview -->
            <div
              class="flex flex-col rounded-none border bg-card overflow-hidden"
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
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { SendHorizonal, Tag, X, ArrowLeft, Check } from "lucide-vue-next";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { useI18n } from "vue-i18n";

import { MarkdownEdit, MarkdownView } from "@/components/markdown";
import { useSolutionAuthoring } from "@/composables/useSolutionAuthoring";

const router = useRouter();
const { t } = useI18n();

// UI-only state for the topic-picker popover; everything else is owned by
// the authoring composable.
const showTopicPicker = ref<boolean>(false);

const {
  title,
  editorContent,
  dynamicTemplate,
  isEditMode,
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
