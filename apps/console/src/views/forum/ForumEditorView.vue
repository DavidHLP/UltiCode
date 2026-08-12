<script setup lang="ts">
import { useAuthStore } from "@/stores/auth";
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
  if (!useAuthStore().isAuthenticated) {
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
      body: excerpt.value.trim(),
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
      class="flex h-16 flex-shrink-0 items-center border-b border-border-control bg-card px-6 gap-4 shadow-sm z-10"
    >
      <Button
        variant="ghost"
        size="sm"
        class="gap-2 rounded-none hover:bg-accent/30 font-bold"
        @click="handleGoBack"
      >
        <ArrowLeft class="h-4 w-4" />
        <span class="hidden sm:inline">{{ t("forum.actions.back") }}</span>
      </Button>

      <Separator orientation="vertical" class="h-6" />

      <div class="flex items-center gap-2 flex-1">
        <h2
          class="text-sm font-black uppercase tracking-widest text-[var(--primary)]"
        >
          {{
            isEditMode ? t("forum.post.editPost") : t("forum.post.createPost")
          }}
        </h2>
      </div>

      <div class="flex items-center gap-3">
        <Button
          size="sm"
          class="gap-2 rounded-none px-6 h-9 font-black uppercase tracking-wider bg-primary hover:bg-primary/90 text-primary-foreground"
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
        <div
          class="flex flex-shrink-0 flex-col gap-4 px-6 py-6 bg-muted/10 border-b border-border-control/20"
        >
          <div class="max-w-[1600px] mx-auto w-full space-y-4">
            <Input
              v-model="title"
              :placeholder="t('forum.post.titlePlaceholder')"
              class="rounded-none border-0 border-b border-border-control/30 bg-transparent px-0 text-3xl font-black shadow-none focus-visible:ring-0 focus-visible:border-[var(--primary)] transition-all placeholder:text-muted-foreground/30 h-14"
            />

            <div class="flex flex-wrap gap-3 items-center">
              <div
                class="flex items-center rounded-none border border-border-control bg-card shadow-sm hover:border-[var(--primary)] focus-within:border-[var(--primary)] focus-within:ring-1 focus-within:ring-[var(--primary)] transition-colors h-9 overflow-hidden"
              >
                <div
                  class="bg-[color-mix(in_oklch,var(--primary)_15%,transparent)] text-[var(--primary)] px-2.5 h-full flex items-center justify-center border-r border-border-control"
                >
                  <LayoutGrid class="h-3.5 w-3.5" />
                </div>
                <Select v-model="communityId">
                  <SelectTrigger
                    class="w-[180px] h-full border-0 bg-transparent shadow-none focus:ring-0 text-xs font-bold uppercase tracking-wider px-3"
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
                class="flex items-center rounded-none border border-border-control bg-card shadow-sm hover:border-[var(--primary)] focus-within:border-[var(--primary)] focus-within:ring-1 focus-within:ring-[var(--primary)] transition-colors h-9 overflow-hidden"
              >
                <div
                  class="bg-[color-mix(in_oklch,var(--status-warning-mark)_15%,transparent)] text-foreground-strong px-2.5 h-full flex items-center justify-center border-r border-border-control"
                >
                  <Tag class="h-3.5 w-3.5" />
                </div>
                <Select v-model="flairType">
                  <SelectTrigger
                    class="w-[140px] h-full border-0 bg-transparent shadow-none focus:ring-0 text-xs font-bold uppercase tracking-wider px-3"
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
                class="h-9 w-[150px] text-xs font-bold rounded-none border-border-control focus:border-[var(--primary)] focus:ring-2 focus:ring-[var(--accent-glow)] shadow-sm bg-card"
              />

              <div class="relative">
                <Button
                  variant="outline"
                  size="sm"
                  class="h-9 rounded-none border-border-control gap-2 px-4 shadow-sm hover:border-[var(--primary)] hover:bg-[var(--surface-sunken)] transition-colors"
                  @click="showTagPicker = !showTagPicker"
                >
                  <Plus class="h-3.5 w-3.5" />
                  {{ t("forum.post.tags") }}
                </Button>
                <div
                  v-if="showTagPicker"
                  class="absolute left-0 top-11 z-50 w-64 rounded-none border border-border bg-popover shadow-[var(--shadow-float)] animate-in zoom-in-95 duration-200"
                >
                  <div class="border-b border-border px-4 py-3 bg-muted/30">
                    <h4
                      class="text-2xs font-black uppercase tracking-widest text-muted-foreground"
                    >
                      {{ t("forum.post.selectTags") }}
                    </h4>
                  </div>
                  <div class="max-h-64 overflow-y-auto p-2 space-y-1">
                    <button
                      v-for="tag in tags"
                      :key="tag.id"
                      type="button"
                      class="flex w-full items-center justify-between rounded-none px-3 py-2 text-sm hover:bg-muted transition-colors"
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
                class="flex items-center gap-1.5 h-7 px-3 rounded-none text-2xs font-data font-bold uppercase tracking-wider bg-[color-mix(in_oklch,var(--primary)_10%,transparent)] text-[var(--primary)] border-[color-mix(in_oklch,var(--primary)_20%,transparent)] border shadow-sm"
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
        <div class="flex-1 px-6 pt-6 pb-6 overflow-hidden">
          <div
            class="max-w-[1600px] mx-auto h-full grid grid-cols-1 lg:grid-cols-2 gap-6"
          >
            <div
              class="flex flex-col h-full overflow-hidden border border-border-control rounded-none bg-card shadow-sm focus-within:border-[var(--primary)] focus-within:ring-2 focus-within:ring-[var(--accent-glow)] transition-all"
            >
              <div
                class="flex items-center border-b bg-muted/30 px-4 h-10 shrink-0"
              >
                <span
                  class="text-2xs font-black uppercase tracking-widest text-muted-foreground"
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
              class="hidden lg:flex flex-col rounded-none border border-border-control bg-card overflow-hidden shadow-sm"
            >
              <div
                class="flex items-center border-b bg-muted/30 px-4 h-10 shrink-0"
              >
                <span
                  class="text-2xs font-black uppercase tracking-widest text-muted-foreground"
                  >{{ t("forum.post.livePreview") }}</span
                >
              </div>
              <div class="flex-1 overflow-y-auto p-8">
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

<style scoped>
/* Scoped styles can be empty since we rely on Tailwind and the global markdown design system */
</style>
