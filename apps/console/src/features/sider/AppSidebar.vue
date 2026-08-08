<script setup lang="ts">
import type { SidebarProps } from "@/components/ui/sidebar";
import Calendars from "@/features/sider/Calendars.vue";
import NavUser from "@/features/sider/NavUser.vue";
import SidebarNav from "@/features/sider/SidebarNav.vue";
import {
  forumSidebarData,
  problemSidebarData,
  contestSidebarData,
  personalSidebarData,
} from "@/features/sider/sidebar.data";
import { computed } from "vue";
import { useRoute } from "vue-router";
import { useAuthStore } from "@/stores/auth";
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarHeader,
  SidebarRail,
  SidebarSeparator,
} from "@/components/ui/sidebar";

const props = defineProps<SidebarProps>();
const route = useRoute();
const authStore = useAuthStore();

const isAuthenticated = computed(() => authStore.isAuthenticated);

const user = computed(() => {
  if (isAuthenticated.value && authStore.user) {
    return {
      name: authStore.user.name || authStore.user.username,
      email: authStore.user.email || "",
      avatar: authStore.user.avatar || "",
    };
  }
  return {
    name: "",
    email: "",
    avatar: "",
  };
});

const isProblemContext = computed(() => route.path.startsWith("/problemset"));
const isContestContext = computed(() => route.path.startsWith("/contest"));
const isPersonalContext = computed(() => route.path.startsWith("/personal"));
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
  return forumSidebarData;
});
</script>

<template>
  <Sidebar v-bind="props">
    <SidebarHeader
      class="h-14 border-b border-sidebar-border flex items-center justify-center p-2"
    >
      <NavUser :user="user" :is-authenticated="isAuthenticated" />
    </SidebarHeader>
    <SidebarContent>
      <!-- Dynamic Sidebar Navigation -->
      <SidebarNav :sections="currentSidebarData" />

      <template v-if="isProblemContext">
        <SidebarSeparator class="mx-0" />
        <!-- Problem Lists (Only for Problem Context) -->
        <Calendars />
      </template>
    </SidebarContent>
    <SidebarFooter> </SidebarFooter>
    <SidebarRail />
  </Sidebar>
</template>
