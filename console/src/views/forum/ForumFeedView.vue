<script setup lang="ts">
import { useAuthStore } from "@/stores/auth";
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
import { vote, VoteTargetType } from "@/api/vote";
import { useI18n } from "vue-i18n";

const posts = ref<ForumPost[]>([]);
const communities = ref<ForumCommunity[]>([]);
const quickFilters = ref<Array<{ label: string; value: string }>>([]);
const currentCommunity = ref<ForumCommunity | null>(null);
const communityRules = ref<ForumCommunityRule[]>([]);
const communityLinks = ref<ForumCommunityLink[]>([]);
const isLoading = ref(true);
const currentPage = ref(1);
const totalPages = ref(1);

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
    const [postResult, communityRows, filters] = await Promise.all([
      fetchForumPosts({
        sortBy: quickFilter.value,
        page: currentPage.value,
        pageSize: 20,
      }),
      fetchForumCommunities(),
      fetchForumQuickFilters(),
    ]);
    posts.value = postResult.posts;
    totalPages.value = postResult.totalPages;
    communities.value = communityRows;
    quickFilters.value = filters.map((f) => ({
      ...f,
      label: t(`forum.sort.${f.value}`),
    }));
    currentCommunity.value = null;
    communityRules.value = [];
    communityLinks.value = [];
  } catch (error) {
    console.error("[ForumFeedView] Failed to load forum data", error);
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
    const [communityData, postResult] = await Promise.all([
      fetchForumCommunity(slug),
      fetchCommunityPosts(slug, {
        sortBy: quickFilter.value,
        page: currentPage.value,
        pageSize: 20,
      }),
    ]);
    currentCommunity.value = communityData.community;
    communityRules.value = communityData.rules ?? [];
    communityLinks.value = communityData.links ?? [];
    posts.value = postResult.posts;
    totalPages.value = postResult.totalPages;
  } catch (error) {
    console.error("[ForumFeedView] Failed to load community data", error);
    posts.value = [];
    currentCommunity.value = null;
    communityRules.value = [];
    communityLinks.value = [];
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
      selectedFlair.value === "all" || post.flairType === selectedFlair.value;

    return matchesSearch && matchesFlair;
  });
});

function reloadPosts() {
  if (selectedCommunity.value === "all" || !route.params.category) {
    loadAllPosts();
  } else {
    loadCommunityPosts(String(route.params.category));
  }
}

watch(quickFilter, () => {
  currentPage.value = 1;
  reloadPosts();
});

function handleCreatePost() {
  if (!useAuthStore().isAuthenticated) {
    toast.error(t("forum.messages.loginToCreate"));
    return;
  }
  router.push({ name: "forum-create" });
}

async function handlePostVote(postId: string, type: 1 | -1) {
  if (!useAuthStore().isAuthenticated) {
    toast.error(t("forum.messages.loginToVote"));
    return;
  }
  try {
    const res = await vote(VoteTargetType.FORUM_POST, postId, type);
    const post = posts.value.find((item) => item.id === postId);
    if (post) {
      post.userVote = res.userVote;
      post.voteState =
        res.userVote === 1
          ? "upvoted"
          : res.userVote === -1
            ? "downvoted"
            : "neutral";
      post.stats = {
        ...(post.stats ?? {}),
        likes: res.likes,
        dislikes: res.dislikes,
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
        class="flex flex-col gap-3 terminal-card bg-card p-4 sm:flex-row sm:items-center sm:justify-between shadow-sm"
      >
        <div class="flex flex-1 items-center gap-3">
          <div class="relative flex-1 max-w-md">
            <Search
              class="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground"
            />
            <Input
              v-model="searchQuery"
              :placeholder="t('forum.list.searchPlaceholder')"
              class="h-10 pl-10 rounded-none bg-[var(--surface-sunken)]/50 font-data border-silver focus-visible:border-[var(--accent-electric)] focus-visible:ring-[var(--accent-electric-glow)]"
            />
          </div>
          <Select v-model="quickFilter">
            <SelectTrigger
              class="h-10 w-40 rounded-none bg-[var(--surface-sunken)]/50 font-data border-silver focus-visible:border-[var(--accent-electric)] focus-visible:ring-[var(--accent-electric-glow)]"
            >
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
        <Button
          class="h-10 rounded-none px-6 gap-2 font-data font-bold uppercase tracking-wide border border-[var(--accent-electric)] bg-[var(--accent-electric)]/10 text-[var(--accent-electric)] hover:bg-[var(--accent-electric)]/20 shadow-none transition-colors duration-200"
          @click="handleCreatePost"
        >
          <Plus class="h-4 w-4" />
          {{ t("forum.list.newPost") }}
        </Button>
      </div>
      <div v-if="isLoading" class="space-y-4">
        <ForumPostSkeleton v-for="i in 3" :key="i" />
      </div>
      <div v-else class="space-y-4">
        <ForumPostCard
          v-for="post in filteredPosts"
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
