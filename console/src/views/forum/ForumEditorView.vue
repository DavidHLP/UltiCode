<script setup lang="ts">
import { ref, onMounted, computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  fetchForumCommunities,
  fetchForumPost,
  fetchForumTags,
  createForumPost,
  updateForumPost,
} from "@/api/forum";
import type { ForumCommunity, ForumPost, ForumTag } from "@/types/forum";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { toast } from "vue-sonner";
import { isAuthenticated } from "@/utils/auth";
import { MarkdownEdit, MarkdownView } from "@/components/markdown";
import { useI18n } from "vue-i18n";
import {
  ArrowLeft,
  SendHorizonal,
  X,
  Tag,
  Check,
  LayoutGrid,
  Plus,
} from "lucide-vue-next";

const route = useRoute();
const router = useRouter();
const { t } = useI18n();

const isEditMode = computed(() => Boolean(route.params.postId));
const isLoading = ref(true);
const isSaving = ref(false);

const communities = ref<ForumCommunity[]>([]);
const tags = ref<ForumTag[]>([]);

const title = ref("");
const excerpt = ref("");
const communityId = ref("");
const flairType = ref<string | null>(null);
const flairLabel = ref("");
const selectedTags = ref<string[]>([]);
const showTagPicker = ref(false);

const defaultTemplate = `## Context

Share the background briefly.

## Details

- What did you try?
- What did you learn?

## Question

What are you looking for from the community?
`;

const flairOptions = computed(() => [
  { value: "discussion", label: t("forum.flairs.discussion") },
  { value: "question", label: t("forum.flairs.question") },
  { value: "announcement", label: t("forum.flairs.announcement") },
  { value: "showcase", label: t("forum.flairs.showcase") },
  { value: "hiring", label: t("forum.flairs.hiring") },
]);

function applyPost(post: ForumPost) {
  title.value = post.title || "";
  excerpt.value = post.excerpt || "";
  communityId.value = post.community?.id || "";
  flairType.value = post.flair?.type ?? null;
  flairLabel.value = post.flair?.text ?? "";
  selectedTags.value = Array.isArray(post.tags) ? [...post.tags] : [];
}

async function loadData() {
  isLoading.value = true;
  try {
    const [communityRows, tagRows] = await Promise.all([
      fetchForumCommunities(),
      fetchForumTags(),
    ]);
    communities.value = communityRows;
    tags.value = tagRows;

    if (isEditMode.value) {
      const postId = route.params.postId as string;
      const post = await fetchForumPost(postId);
      if (!post) {
        toast.error(t("forum.messages.postNotFound"));
        return;
      }
      applyPost(post);
    } else if (communityRows.length > 0) {
      communityId.value = communityRows[0]?.id ?? "";
    }
  } catch (error) {
    console.error("Failed to load editor data", error);
    toast.error(t("forum.messages.loadFailed"));
  } finally {
    isLoading.value = false;
  }
}

async function handleSave() {
  if (!isAuthenticated()) {
    toast.error(t("forum.messages.loginToPublish"));
    return;
  }
  if (!title.value.trim() || !excerpt.value.trim() || !communityId.value) {
    toast.error(t("forum.messages.fillRequired"));
    return;
  }
  isSaving.value = true;
  try {
    const payload = {
      title: title.value.trim(),
      excerpt: excerpt.value.trim(),
      tags: selectedTags.value,
      flairType: flairType.value,
      flairLabel: flairLabel.value.trim() || undefined,
    };

    if (isEditMode.value) {
      const postId = route.params.postId as string;
      await updateForumPost(postId, payload);
      toast.success(t("forum.messages.postUpdated"));
      router.push({ name: "forum-thread", params: { postId } });
    } else {
      const post = await createForumPost({
        ...payload,
        communityId: communityId.value,
      });
      toast.success(t("forum.messages.postCreated"));
      router.push({ name: "forum-thread", params: { postId: post.id } });
    }
  } catch (error) {
    console.error("Failed to save post", error);
    toast.error(t("forum.messages.saveFailed"));
  } finally {
    isSaving.value = false;
  }
}

function toggleTag(tagName: string) {
  if (selectedTags.value.includes(tagName)) {
    selectedTags.value = selectedTags.value.filter((t) => t !== tagName);
  } else {
    selectedTags.value.push(tagName);
  }
}

function removeTag(tagName: string) {
  selectedTags.value = selectedTags.value.filter((t) => t !== tagName);
}

const handleGoBack = () => {
  router.back();
};

onMounted(loadData);
</script>

<template>
  <div
    class="flex h-screen w-full flex-col overflow-hidden bg-background animate-in fade-in duration-500"
  >
    <!-- Header -->
    <header
      class="flex h-16 flex-shrink-0 items-center border-b bg-card px-6 gap-4 shadow-sm z-10"
    >
      <Button
        variant="ghost"
        size="sm"
        class="gap-2 rounded-full hover:bg-muted"
        @click="handleGoBack"
      >
        <ArrowLeft class="h-4 w-4" />
        <span class="hidden sm:inline">{{ t("forum.actions.back") }}</span>
      </Button>

      <Separator orientation="vertical" class="h-6" />

      <div class="flex items-center gap-2 flex-1">
        <h2 class="text-sm font-black uppercase tracking-widest text-primary">
          {{
            isEditMode ? t("forum.post.editPost") : t("forum.post.createPost")
          }}
        </h2>
      </div>

      <div class="flex items-center gap-3">
        <Button
          size="sm"
          class="gap-2 rounded-full px-6 h-9 font-bold"
          :disabled="isSaving || isLoading"
          @click="handleSave"
        >
          <SendHorizonal class="h-4 w-4" />
          {{
            isEditMode
              ? t("forum.post.updateButton")
              : t("forum.post.publishButton")
          }}
        </Button>
      </div>
    </header>

    <!-- Main Content -->
    <main class="flex flex-1 overflow-hidden">
      <div class="flex w-full flex-col overflow-hidden">
        <!-- Meta Section -->
        <div class="flex flex-shrink-0 flex-col gap-4 px-6 py-6 bg-muted/10">
          <div class="max-w-5xl mx-auto w-full space-y-4">
            <Input
              v-model="title"
              :placeholder="t('forum.post.titlePlaceholder')"
              class="rounded-none border-0 border-b-2 border-transparent bg-transparent px-0 text-3xl font-black shadow-none focus-visible:ring-0 focus-visible:border-primary transition-all placeholder:text-muted-foreground/30 h-14"
            />

            <div class="flex flex-wrap gap-3 items-center">
              <div
                class="flex items-center gap-2 bg-card rounded-full border p-1 pr-3 shadow-sm hover:border-primary/50 transition-colors"
              >
                <div class="bg-primary/10 text-primary p-1.5 rounded-full">
                  <LayoutGrid class="h-3.5 w-3.5" />
                </div>
                <Select v-model="communityId">
                  <SelectTrigger
                    class="w-[180px] h-7 border-0 bg-transparent shadow-none focus:ring-0 text-xs font-bold uppercase tracking-wider"
                  >
                    <SelectValue :placeholder="t('forum.post.community')" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem
                      v-for="community in communities"
                      :key="community.id"
                      :value="community.id"
                    >
                      {{ community.name }}
                    </SelectItem>
                  </SelectContent>
                </Select>
              </div>

              <div
                class="flex items-center gap-2 bg-card rounded-full border p-1 pr-3 shadow-sm hover:border-primary/50 transition-colors"
              >
                <div class="bg-amber-500/10 text-amber-600 p-1.5 rounded-full">
                  <Tag class="h-3.5 w-3.5" />
                </div>
                <Select v-model="flairType">
                  <SelectTrigger
                    class="w-[140px] h-7 border-0 bg-transparent shadow-none focus:ring-0 text-xs font-bold uppercase tracking-wider"
                  >
                    <SelectValue :placeholder="t('forum.post.flair')" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem
                      v-for="flair in flairOptions"
                      :key="flair.value"
                      :value="flair.value"
                    >
                      {{ flair.label }}
                    </SelectItem>
                  </SelectContent>
                </Select>
              </div>

              <Input
                v-if="flairType"
                v-model="flairLabel"
                :placeholder="t('forum.post.customLabel')"
                class="h-9 w-[150px] text-xs font-bold rounded-full shadow-sm"
              />

              <div class="relative">
                <Button
                  variant="outline"
                  size="sm"
                  class="h-9 rounded-full gap-2 px-4 shadow-sm"
                  @click="showTagPicker = !showTagPicker"
                >
                  <Plus class="h-3.5 w-3.5" />
                  {{ t("forum.post.tags") }}
                </Button>
                <div
                  v-if="showTagPicker"
                  class="absolute left-0 top-11 z-50 w-64 rounded-xl border border-border bg-popover shadow-xl animate-in zoom-in-95 duration-200"
                >
                  <div class="border-b border-border px-4 py-3 bg-muted/30">
                    <h4
                      class="text-[10px] font-black uppercase tracking-widest text-muted-foreground"
                    >
                      {{ t("forum.post.selectTags") }}
                    </h4>
                  </div>
                  <div class="max-h-64 overflow-y-auto p-2 space-y-1">
                    <button
                      v-for="tag in tags"
                      :key="tag.id"
                      type="button"
                      class="flex w-full items-center justify-between rounded-lg px-3 py-2 text-sm hover:bg-muted transition-colors"
                      @click="toggleTag(tag.name)"
                    >
                      <span class="font-medium text-foreground/80">{{
                        tag.name
                      }}</span>
                      <div class="h-5 w-5 flex items-center justify-center">
                        <Check
                          v-if="selectedTags.includes(tag.name)"
                          class="h-4 w-4 text-primary font-bold"
                        />
                      </div>
                    </button>
                  </div>
                </div>
              </div>
            </div>

            <div
              v-if="selectedTags.length > 0"
              class="flex flex-wrap gap-2 pt-1"
            >
              <Badge
                v-for="tag in selectedTags"
                :key="tag"
                variant="secondary"
                class="flex items-center gap-1.5 h-7 px-3 rounded-full text-[10px] font-bold bg-primary/5 text-primary border-primary/10 border"
              >
                {{ tag }}
                <button
                  type="button"
                  class="ml-1 hover:text-destructive transition-colors focus:outline-none"
                  @click="removeTag(tag)"
                >
                  <X class="h-3 w-3" />
                </button>
              </Badge>
            </div>
          </div>
        </div>

        <!-- Editor Section -->
        <div class="flex-1 px-6 pb-6 overflow-hidden">
          <div
            class="max-w-[1600px] mx-auto h-full grid grid-cols-1 lg:grid-cols-2 gap-6"
          >
            <div
              class="flex flex-col h-full overflow-hidden border rounded-2xl bg-card shadow-sm focus-within:border-primary/50 transition-all"
            >
              <div
                class="flex items-center border-b bg-muted/30 px-4 h-10 shrink-0"
              >
                <span
                  class="text-[10px] font-black uppercase tracking-widest text-muted-foreground"
                  >{{ t("forum.post.editorMarkdown") }}</span
                >
              </div>
              <div class="flex-1 overflow-hidden p-2">
                <MarkdownEdit
                  v-model="excerpt"
                  :default-value="defaultTemplate"
                />
              </div>
            </div>

            <div
              class="hidden lg:flex flex-col rounded-2xl border bg-card overflow-hidden shadow-sm"
            >
              <div
                class="flex items-center border-b bg-muted/30 px-4 h-10 shrink-0"
              >
                <span
                  class="text-[10px] font-black uppercase tracking-widest text-muted-foreground"
                  >{{ t("forum.post.livePreview") }}</span
                >
              </div>
              <div class="flex-1 overflow-y-auto p-8 scrollbar-thin">
                <div class="prose prose-sm dark:prose-invert max-w-none">
                  <h1 class="text-3xl font-black mb-6">
                    {{ title || t("forum.post.untitled") }}
                  </h1>
                  <MarkdownView :content="excerpt || defaultTemplate" />
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<style>
.markdown-content h1 {
  font-size: 1.5rem;
  font-weight: 700;
  margin-top: 1rem;
  margin-bottom: 0.5rem;
}

.markdown-content h2 {
  font-size: 1.25rem;
  font-weight: 600;
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
  font-family: "Fira Code", monospace;
  font-size: 0.875rem;
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
