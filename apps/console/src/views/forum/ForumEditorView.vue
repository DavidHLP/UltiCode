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
import { Input } from "@ulticode/design-system/ui/input";
import { Button } from "@ulticode/design-system/ui/button";
import { Badge } from "@ulticode/design-system/ui/badge";
import { Separator } from "@ulticode/design-system/ui/separator";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@ulticode/design-system/ui/popover";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  SelectGroup,
} from "@ulticode/design-system/ui/select";
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
} from "@lucide/vue";

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

function isTagSelected(tag: ForumTag) {
  return (
    selectedTags.value.includes(tag.id) || selectedTags.value.includes(tag.name)
  );
}

function getTagName(value: string) {
  return tags.value.find((tag) => tag.id === value)?.name ?? value;
}

function toggleTag(tag: ForumTag) {
  if (isTagSelected(tag)) {
    selectedTags.value = selectedTags.value.filter(
      (value) => value !== tag.id && value !== tag.name,
    );
  } else {
    selectedTags.value.push(tag.name);
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
    class="flex min-h-dvh w-full flex-col bg-background animate-in fade-in duration-500 lg:h-dvh lg:overflow-hidden"
  >
    <!-- Header -->
    <header
      class="sticky top-0 flex h-16 flex-shrink-0 items-center border-b border-input bg-card px-6 gap-4 shadow-sm z-10"
    >
      <Button
        variant="ghost"
        size="sm"
        class="gap-2"
        :aria-label="t('forum.actions.back')"
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
          class="gap-2 px-6"
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
          class="flex flex-shrink-0 flex-col gap-4 px-6 py-6 bg-muted/10 border-b border-input/20"
        >
          <div class="max-w-[1600px] mx-auto w-full space-y-4">
            <Input
              v-model="title"
              :placeholder="t('forum.post.titlePlaceholder')"
            />

            <div class="flex flex-wrap gap-3 items-center">
              <div
                class="flex items-center rounded-lg border border-input bg-card shadow-sm hover:border-primary focus-within:border-primary focus-within:ring-1 focus-within:ring-primary transition-colors h-9 overflow-hidden"
              >
                <div
                  class="bg-[color-mix(in_oklch,var(--primary)_15%,transparent)] text-primary px-2.5 h-full flex items-center justify-center border-r border-input"
                >
                  <LayoutGrid class="h-3.5 w-3.5" />
                </div>
                <Select v-model="communityId">
                  <SelectTrigger class="w-[180px] h-full px-3">
                    <SelectValue :placeholder="t('forum.post.community')" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectGroup
                      ><SelectItem
                        v-for="community in communities"
                        :key="community.id"
                        :value="community.id"
                      >
                        {{ community.name }}
                      </SelectItem></SelectGroup
                    >
                  </SelectContent>
                </Select>
              </div>

              <div
                class="flex items-center rounded-lg border border-input bg-card shadow-sm hover:border-primary focus-within:border-primary focus-within:ring-1 focus-within:ring-primary transition-colors h-9 overflow-hidden"
              >
                <div
                  class="bg-[color-mix(in_oklch,var(--status-warning-mark)_15%,transparent)] text-foreground px-2.5 h-full flex items-center justify-center border-r border-input"
                >
                  <Tag class="h-3.5 w-3.5" />
                </div>
                <Select v-model="flairType">
                  <SelectTrigger class="w-[140px] h-full px-3">
                    <SelectValue :placeholder="t('forum.post.flair')" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectGroup
                      ><SelectItem
                        v-for="flair in flairOptions"
                        :key="flair.value"
                        :value="flair.value"
                      >
                        {{ flair.label }}
                      </SelectItem></SelectGroup
                    >
                  </SelectContent>
                </Select>
              </div>

              <Input
                v-if="flairType"
                v-model="flairLabel"
                :placeholder="t('forum.post.customLabel')"
                class="w-[150px]"
              />

              <Popover>
                <PopoverTrigger as-child>
                  <Button variant="outline" size="sm" class="gap-2 px-4">
                    <Plus class="h-3.5 w-3.5" />
                    {{ t("forum.post.tags") }}
                  </Button>
                </PopoverTrigger>
                <PopoverContent
                  align="end"
                  :aria-label="t('forum.post.selectTags')"
                  class="w-64 max-h-(--reka-popover-content-available-height) gap-0 p-0"
                >
                  <div
                    class="shrink-0 rounded-t-lg border-b border-border px-4 py-3 bg-muted/30"
                  >
                    <h4
                      class="text-xs font-black uppercase tracking-widest text-muted-foreground"
                    >
                      {{ t("forum.post.selectTags") }}
                    </h4>
                  </div>
                  <div class="min-h-0 max-h-64 overflow-y-auto p-2 space-y-1">
                    <Button
                      variant="outline"
                      v-for="tag in tags"
                      :key="tag.id"
                      type="button"
                      class="flex w-full items-center justify-between px-3 py-2"
                      :aria-pressed="isTagSelected(tag)"
                      @click="toggleTag(tag)"
                    >
                      <span class="font-medium text-foreground/80">{{
                        tag.name
                      }}</span>
                      <div class="h-5 w-5 flex items-center justify-center">
                        <Check
                          v-if="isTagSelected(tag)"
                          class="h-4 w-4 text-primary font-bold"
                        />
                      </div>
                    </Button>
                  </div>
                </PopoverContent>
              </Popover>
            </div>

            <div
              v-if="selectedTags.length > 0"
              class="flex flex-wrap gap-2 pt-1"
            >
              <Badge
                v-for="tag in selectedTags"
                :key="tag"
                variant="secondary"
                class="flex items-center gap-1.5 h-7 px-3"
              >
                {{ getTagName(tag) }}
                <Button
                  variant="outline"
                  type="button"
                  class="ml-1 focus:outline-none"
                  :aria-label="
                    t('forum.post.removeTag', { tag: getTagName(tag) })
                  "
                  @click="removeTag(tag)"
                >
                  <X class="h-3 w-3" />
                </Button>
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
              class="flex flex-col h-full overflow-hidden border border-input rounded-lg bg-card shadow-sm focus-within:border-primary focus-within:ring-2 focus-within:ring-ring transition-all"
            >
              <div
                class="flex items-center border-b bg-muted/30 px-4 h-10 shrink-0"
              >
                <span
                  class="text-xs font-black uppercase tracking-widest text-muted-foreground"
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
              class="hidden lg:flex flex-col rounded-lg border border-input bg-card overflow-hidden shadow-sm"
            >
              <div
                class="flex items-center border-b bg-muted/30 px-4 h-10 shrink-0"
              >
                <span
                  class="text-xs font-black uppercase tracking-widest text-muted-foreground"
                  >{{ t("forum.post.livePreview") }}</span
                >
              </div>
              <div class="flex-1 overflow-y-auto p-8">
                <div class="typeset typeset-docs max-w-none">
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
@media (max-height: 400px) {
  header {
    position: static;
  }
}
</style>
