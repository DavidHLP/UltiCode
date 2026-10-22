<script setup lang="ts">
import type { SidebarProps } from "@ulticode/design-system/ui/sidebar";
import Calendars from "@/features/sider/Calendars.vue";
import NavUser from "@/features/sider/NavUser.vue";
import SidebarNav from "@/features/sider/SidebarNav.vue";
import {
  forumSidebarData,
  problemSidebarData,
  contestSidebarData,
  personalSidebarData,
} from "@/features/sider/sidebar.data";
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { MessageSquare } from "@lucide/vue";
import { AccountSwitcher } from "@ulticode/auth-ui";
import { accountSessions } from "@/api/accountSessions";
import { useI18n } from "vue-i18n";
import { fetchForumCommunities } from "@/api/forum";
import type { ForumCommunity } from "@/types/forum";
import { Button } from "@ulticode/design-system/ui/button";
import { useAppearance } from "@ulticode/theme";
import {
  isNavigationFailure,
  NavigationFailureType,
  useRoute,
  useRouter,
} from "vue-router";
import { useAuthStore } from "@/stores/auth";
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuItem,
  SidebarRail,
  SidebarSeparator,
  useSidebar,
} from "@ulticode/design-system/ui/sidebar";

const props = withDefaults(defineProps<SidebarProps>(), {
  collapsible: "icon",
});
const route = useRoute();
const authStore = useAuthStore();
const { setOpenMobile } = useSidebar();
const { settingsOpen } = useAppearance();
const { t } = useI18n();

watch(settingsOpen, (open) => {
  if (open) setOpenMobile(false);
});

const removeNavigationHook = useRouter().afterEach((_to, _from, failure) => {
  if (
    !failure ||
    isNavigationFailure(failure, NavigationFailureType.duplicated)
  ) {
    setOpenMobile(false);
  }
});
onBeforeUnmount(removeNavigationHook);

const isAuthenticated = computed(() => authStore.isAuthenticated);

const user = computed(() => {
  if (isAuthenticated.value && authStore.user) {
    return {
      username: authStore.user.username,
      name: authStore.user.name || authStore.user.username,
      email: authStore.user.email || "",
      avatar: authStore.user.avatar || "",
    };
  }
  return {
    username: "",
    name: "",
    email: "",
    avatar: "",
  };
});

const isProblemContext = computed(() => route.path.startsWith("/problemset"));
const isContestContext = computed(() => route.path.startsWith("/contest"));
const isPersonalContext = computed(() => route.path.startsWith("/personal"));
const isForumContext = computed(
  () =>
    !isProblemContext.value &&
    !isContestContext.value &&
    !isPersonalContext.value,
);
const forumCommunities = ref<ForumCommunity[]>([]);
const communitiesLoaded = ref(false);
const loadingCommunities = ref(false);
const communityError = ref(false);

async function loadForumCommunities() {
  if (loadingCommunities.value) return;
  loadingCommunities.value = true;
  communityError.value = false;
  try {
    forumCommunities.value = await fetchForumCommunities();
    communitiesLoaded.value = true;
  } catch (error) {
    communityError.value = true;
    console.error("[AppSidebar] Failed to load forum communities", error);
  } finally {
    loadingCommunities.value = false;
  }
}

watch(
  isForumContext,
  async (active) => {
    if (active && !communitiesLoaded.value) await loadForumCommunities();
  },
  { immediate: true },
);

const currentSidebarData = computed(() => {
  if (isProblemContext.value) {
    return problemSidebarData;
  }
  if (isContestContext.value) {
    return contestSidebarData;
  }
  if (isPersonalContext.value) {
    return personalSidebarData;
  }
  return forumSidebarData.map((section) =>
    section.name === "sidebar.forum.categories"
      ? {
          ...section,
          items: forumCommunities.value.map((community) => ({
            title: community.id,
            label: community.name,
            url: `/forum/c/${encodeURIComponent(community.slug || community.id)}`,
            icon: MessageSquare,
          })),
        }
      : section,
  );
});
</script>

<template>
  <Sidebar v-bind="props" class="uc-sidebar-shell">
    <SidebarHeader class="uc-sidebar-header">
      <SidebarMenu>
        <SidebarMenuItem>
          <AccountSwitcher
            :client="accountSessions"
            :account-name="user.name || t('auth.guest.name')"
          />
        </SidebarMenuItem>
      </SidebarMenu>
    </SidebarHeader>
    <SidebarContent class="uc-sidebar-content">
      <!-- Dynamic Sidebar Navigation -->
      <SidebarNav :sections="currentSidebarData" />
      <div
        v-if="isForumContext && communityError"
        role="status"
        class="px-3 py-2 text-sm text-muted-foreground"
      >
        <p>{{ t("forum.list.communitiesLoadFailed") }}</p>
        <Button
          variant="ghost"
          size="sm"
          :disabled="loadingCommunities"
          @click="loadForumCommunities"
        >
          {{ t("common.actions.retry") }}
        </Button>
      </div>

      <template v-if="isProblemContext">
        <div class="contents">
          <SidebarSeparator class="uc-sidebar-separator" />
          <!-- Problem Lists (Only for Problem Context) -->
          <Calendars />
        </div>
      </template>
    </SidebarContent>
    <SidebarFooter>
      <NavUser :user="user" :is-authenticated="isAuthenticated" />
    </SidebarFooter>
    <SidebarRail />
  </Sidebar>
</template>
