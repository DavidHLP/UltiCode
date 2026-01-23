<script setup lang="ts">
import type {
  ForumFlairType,
  ForumPost,
  ForumCommunity,
  ForumCommunityRule,
  ForumCommunityLink,
} from "@/types/forum";
import ForumPostCard from "@/views/forum/components/ForumPostCard.vue";
import ForumPostSkeleton from "@/views/forum/components/ForumPostSkeleton.vue";
import ForumSidebar from "@/views/forum/components/ForumSidebar.vue";
import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  fetchForumCommunities,
  fetchForumCommunity,
  fetchCommunityPosts,
  fetchForumPosts,
  fetchForumQuickFilters,
} from "@/api/forum";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Search, Plus } from "lucide-vue-next";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { toast } from "vue-sonner";
import { isAuthenticated } from "@/utils/auth";
import { vote, VoteTargetType } from "@/api/vote";
import { useI18n } from "vue-i18n";

const posts = ref<ForumPost[]>([]);
const communities = ref<ForumCommunity[]>([]);
const quickFilters = ref<Array<{ label: string; value: string }>>([]);
const currentCommunity = ref<ForumCommunity | null>(null);
const communityRules = ref<ForumCommunityRule[]>([]);
const communityLinks = ref<ForumCommunityLink[]>([]);
const isLoading = ref(true);

const { t } = useI18n();

const props = defineProps<{
  filter?: string;
}>();

const route = useRoute();
const router = useRouter();
const searchQuery = ref("");
const quickFilter = ref(props.filter || "hot");
const selectedCommunity = ref("all");
const selectedFlair = ref<"all" | ForumFlairType>("all");

// Load all posts and communities on mount
async function loadAllPosts() {
  isLoading.value = true;
  try {
    const [postRows, communityRows, filters] = await Promise.all([
      fetchForumPosts(),
      fetchForumCommunities(),
      fetchForumQuickFilters(),
    ]);
    posts.value = postRows;
    communities.value = communityRows;
    quickFilters.value = filters.map((f) => ({
      ...f,
      label: t(`forum.sort.${f.value}`),
    }));
    currentCommunity.value = null;
    communityRules.value = [];
    communityLinks.value = [];
  } catch (error) {
    console.error("Failed to load forum data", error);
    posts.value = [];
    communities.value = [];
    quickFilters.value = [];
  } finally {
    isLoading.value = false;
  }
}

// Load posts for a specific community
async function loadCommunityPosts(slug: string) {
  isLoading.value = true;
  try {
    const [communityPosts, communityData] = await Promise.all([
      fetchCommunityPosts(slug, {
        sortBy: quickFilter.value as "hot" | "new" | "top",
      }),
      fetchForumCommunity(slug),
    ]);
    posts.value = communityPosts;
    currentCommunity.value = communityData.community;
    communityRules.value = communityData.rules;
    communityLinks.value = communityData.links;
  } catch (error) {
    console.error("Failed to load community posts", error);
    posts.value = [];
    currentCommunity.value = null;
  } finally {
    isLoading.value = false;
  }
}

// Watch for category changes in route
watch(
  () => route.params.category,
  async (newCategory) => {
    if (newCategory) {
      selectedCommunity.value = String(newCategory);
      await loadCommunityPosts(String(newCategory));
    } else {
      selectedCommunity.value = "all";
      await loadAllPosts();
    }
  },
  { immediate: true },
);

// Check props for filter
watch(
  () => props.filter,
  (newFilter: string | undefined) => {
    if (newFilter) {
      quickFilter.value = newFilter;
    }
  },
  { immediate: true },
);

const filteredPosts = computed(() => {
  const normalizedSearch = searchQuery.value.trim().toLowerCase();
  return posts.value.filter((post) => {
    const matchesSearch =
      !normalizedSearch ||
      post.title.toLowerCase().includes(normalizedSearch) ||
      post.excerpt?.toLowerCase().includes(normalizedSearch) ||
      (Array.isArray(post.tags) &&
        post.tags.some((tag: string) =>
          tag.toLowerCase().includes(normalizedSearch),
        ));

    const matchesFlair =
      selectedFlair.value === "all" || post.flair?.type === selectedFlair.value;

    return matchesSearch && matchesFlair;
  });
});

const sortedPosts = computed(() => {
  const postsArray = [...filteredPosts.value];
  const sorters: Record<string, (a: ForumPost, b: ForumPost) => number> = {
    hot: (a, b) => (b.stats?.score ?? 0) - (a.stats?.score ?? 0),
    new: (a, b) =>
      new Date(b.createdAt).valueOf() - new Date(a.createdAt).valueOf(),
    top: (a, b) => (b.stats?.saves ?? 0) - (a.stats?.saves ?? 0),
    rising: (a, b) => (b.stats?.shares ?? 0) - (a.stats?.shares ?? 0),
  };

  const sorter = sorters[quickFilter.value] ?? sorters.hot;
  postsArray.sort(sorter);

  const pinned = postsArray.filter((post) => post.isPinned);
  const rest = postsArray.filter((post) => !post.isPinned);

  return [...pinned, ...rest];
});

function handleCreatePost() {
  if (!isAuthenticated()) {
    toast.error(t("forum.messages.loginToCreate"));
    return;
  }
  router.push({ name: "forum-create" });
}

async function handlePostVote(postId: string, type: 1 | -1) {
  if (!isAuthenticated()) {
    toast.error(t("forum.messages.loginToVote"));
    return;
  }
  try {
    const res = await vote(VoteTargetType.FORUM_POST, postId, type);
    const post = posts.value.find((item) => item.id === postId);
    if (post) {
      post.likes = res.likes;
      post.dislikes = res.dislikes;
      post.userVote = res.userVote;
      post.voteState =
        res.userVote === 1
          ? "upvoted"
          : res.userVote === -1
            ? "downvoted"
            : "neutral";
      post.stats = {
        ...(post.stats ?? {}),
        score: res.likes - res.dislikes,
      };
    }
  } catch (error) {
    console.error("Failed to vote post", error);
    toast.error(t("forum.messages.voteFailed"));
  }
}

function handlePostSave(postId: string, isSaved: boolean) {
  const post = posts.value.find((item) => item.id === postId);
  if (post) {
    post.isSaved = isSaved;
  }
}
</script>

<template>
  <div
    class="mx-auto flex w-full max-w-7xl items-start gap-6 px-4 py-8 animate-in fade-in slide-in-from-bottom-4 duration-500"
  >
    <!-- Main Feed -->
    <main class="w-full min-w-0 flex-1 space-y-6">
      <div
        class="flex flex-col gap-3 rounded-2xl border border-border/40 bg-card/60 p-4 sm:flex-row sm:items-center sm:justify-between shadow-sm"
      >
        <div class="flex flex-1 items-center gap-3">
          <div class="relative flex-1 max-w-md">
            <Search
              class="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground"
            />
            <Input
              v-model="searchQuery"
              :placeholder="t('forum.list.searchPlaceholder')"
              class="h-10 pl-10 rounded-full bg-background/50"
            />
          </div>
          <Select v-model="quickFilter">
            <SelectTrigger class="h-10 w-40 rounded-full bg-background/50">
              <SelectValue :placeholder="t('forum.list.sort')" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem
                v-for="filter in quickFilters"
                :key="filter.value"
                :value="filter.value"
              >
                {{ filter.label }}
              </SelectItem>
            </SelectContent>
          </Select>
        </div>
        <Button class="h-10 rounded-full px-6 gap-2" @click="handleCreatePost">
          <Plus class="h-4 w-4" />
          {{ t("forum.list.newPost") }}
        </Button>
      </div>
      <div v-if="isLoading" class="space-y-4">
        <ForumPostSkeleton v-for="i in 3" :key="i" />
      </div>
      <div v-else class="space-y-4">
        <ForumPostCard
          v-for="post in sortedPosts"
          :key="post.id"
          :post="post"
          @vote="handlePostVote"
          @save="handlePostSave"
        />
      </div>
    </main>

    <!-- Right Sidebar -->
    <ForumSidebar
      class="sticky top-24"
      :community="currentCommunity"
      :rules="communityRules"
      :links="communityLinks"
    />
  </div>
</template>
