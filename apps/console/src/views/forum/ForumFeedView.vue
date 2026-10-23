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
  fetchForumCommunity,
  fetchCommunityPosts,
  fetchForumPosts,
  fetchForumQuickFilters,
} from "@/api/forum";
import { Input } from "@ulticode/design-system/ui/input";
import { Button } from "@ulticode/design-system/ui/button";
import { Search, Plus } from "@lucide/vue";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  SelectGroup,
} from "@ulticode/design-system/ui/select";
import { toast } from "vue-sonner";
import { vote, VoteTargetType } from "@/api/vote";
import { useI18n } from "vue-i18n";

const posts = ref<ForumPost[]>([]);
const quickFilters = ref<Array<{ label: string; value: string }>>([]);
const currentCommunity = ref<ForumCommunity | null>(null);
const communityRules = ref<ForumCommunityRule[]>([]);
const communityLinks = ref<ForumCommunityLink[]>([]);
const isLoading = ref(true);
const loadError = ref(false);
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
const quickFilterLabel = computed(() => {
  if (quickFilter.value === "explore") return t("sidebar.forum.explore");
  return quickFilters.value.some((filter) => filter.value === quickFilter.value)
    ? t(`forum.sort.${quickFilter.value}`)
    : t("forum.list.sort");
});
const selectedCommunity = ref("all");
const selectedFlair = ref<"all" | ForumFlairType>("all");

// Load all posts and communities on mount
async function loadAllPosts() {
  isLoading.value = true;
  loadError.value = false;
  try {
    const [postResult, filters] = await Promise.all([
      fetchForumPosts({
        sortBy: quickFilter.value,
        page: currentPage.value,
        pageSize: 20,
      }),
      fetchForumQuickFilters(),
    ]);
    posts.value = postResult.items;
    totalPages.value = postResult.totalPages;
    quickFilters.value = filters;
    currentCommunity.value = null;
    communityRules.value = [];
    communityLinks.value = [];
  } catch (error) {
    console.error("[ForumFeedView] Failed to load forum data", error);
    loadError.value = true;
    posts.value = [];
    quickFilters.value = [];
    currentCommunity.value = null;
    communityRules.value = [];
    communityLinks.value = [];
  } finally {
    isLoading.value = false;
  }
}

// Load posts for a specific community
async function loadCommunityPosts(slug: string) {
  isLoading.value = true;
  loadError.value = false;
  try {
    const [communityData, postResult, filters] = await Promise.all([
      fetchForumCommunity(slug),
      fetchCommunityPosts(slug, {
        sortBy: quickFilter.value,
        page: currentPage.value,
        pageSize: 20,
      }),
      fetchForumQuickFilters(),
    ]);
    quickFilters.value = filters;
    currentCommunity.value = communityData.community;
    communityRules.value = communityData.rules ?? [];
    communityLinks.value = communityData.links ?? [];
    posts.value = postResult.items;
    totalPages.value = postResult.totalPages;
  } catch (error) {
    console.error("[ForumFeedView] Failed to load community data", error);
    loadError.value = true;
    posts.value = [];
    quickFilters.value = [];
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
    class="@container/forum mx-auto w-full max-w-7xl min-w-0 pb-10 animate-in fade-in slide-in-from-bottom-4 duration-500"
  >
    <div class="grid min-w-0 grid-cols-1 items-start gap-6 @3xl/forum:grid-cols-[minmax(0,1fr)_18rem]">
    <!-- Main Feed -->
    <main class="w-full min-w-0 flex-1 space-y-6">
      <div
        class="flex flex-col gap-3 rounded-xl border bg-card text-card-foreground bg-card px-2 py-4 sm:p-4 sm:flex-row sm:items-center sm:justify-between shadow-sm"
      >
        <div class="flex min-w-0 flex-1 flex-wrap items-center gap-3">
          <div class="relative min-w-0 flex-1 basis-full max-w-md sm:basis-56">
            <Search
              class="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground"
            />
            <Input
              v-model="searchQuery"
              :placeholder="t('forum.list.searchPlaceholder')"
              class="pl-9"
            />
          </div>
          <Select v-model="quickFilter">
            <SelectTrigger
              :aria-label="t('forum.list.sort')"
              class="w-full shrink-0 sm:w-40"
            >
              <SelectValue :placeholder="t('forum.list.sort')">{{
                quickFilterLabel
              }}</SelectValue>
            </SelectTrigger>
            <SelectContent>
              <SelectGroup
                ><SelectItem
                  v-for="filter in quickFilters"
                  :key="filter.value"
                  :value="filter.value"
                >
                  {{ t(`forum.sort.${filter.value}`) }}
                </SelectItem></SelectGroup
              >
            </SelectContent>
          </Select>
        </div>
        <Button class="px-6 gap-2" @click="handleCreatePost">
          <Plus class="h-4 w-4" />
          {{ t("forum.list.newPost") }}
        </Button>
      </div>
      <div v-if="isLoading" class="space-y-4">
        <ForumPostSkeleton v-for="i in 3" :key="i" />
      </div>
      <div
        v-else-if="loadError"
        role="alert"
        class="space-y-3 rounded-xl border bg-card p-6 text-center"
      >
        <p class="text-muted-foreground">{{ t("forum.list.loadFailed") }}</p>
        <Button variant="outline" @click="reloadPosts">{{
          t("common.actions.retry")
        }}</Button>
      </div>
      <div v-else class="space-y-4">
        <p
          v-if="filteredPosts.length === 0"
          role="status"
          class="rounded-xl border bg-card p-6 text-center text-muted-foreground [overflow-wrap:anywhere]"
        >
          {{
            searchQuery.trim()
              ? t("common.search.noResults", { query: searchQuery.trim() })
              : t("forum.list.noPosts")
          }}
        </p>
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
      class="!hidden !w-full @3xl/forum:!block @3xl/forum:sticky @3xl/forum:top-20"
      :community="currentCommunity"
      :rules="communityRules"
      :links="communityLinks"
    />
    </div>
  </div>
</template>
