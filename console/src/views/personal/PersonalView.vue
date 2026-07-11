<script setup lang="ts">
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Activity } from "lucide-vue-next";
import { onMounted, ref, computed } from "vue";
import { RouterLink } from "vue-router";
import PersonalPageShell from "./components/PersonalPageShell.vue";
import UserProfileCard from "./components/UserProfileCard.vue";
import UserStatsPanel from "./components/UserStatsPanel.vue";
import {
  fetchUserProfile,
  fetchUserStats,
  fetchUserSkills,
  type ProfileData,
} from "@/api/user";
import type { UserStats, UserSkill } from "@/types/userStats";
import { fetchUserSubmissions } from "@/api/submission";
import type { SubmissionRecord } from "@/types/submission";
import { useAuthStore } from "@/stores/auth";
import { useI18n } from "vue-i18n";

const { t } = useI18n();
const authStore = useAuthStore();
const loading = ref(true);
const user = ref<ProfileData | null>(null);
const submissions = ref<SubmissionRecord[]>([]);
const statsData = ref<UserStats | null>(null);
const skillsData = ref<UserSkill[]>([]);
const skillsLoading = ref(false);

const currentUserId = computed(() => authStore.user?.id);

onMounted(async () => {
  try {
    const userId = currentUserId.value;
    if (!userId) {
      loading.value = false;
      return;
    }

    const [userData, userSubmissions, userStats] = await Promise.all([
      fetchUserProfile(userId),
      fetchUserSubmissions(),
      fetchUserStats(userId),
    ]);

    user.value = userData;
    submissions.value = userSubmissions;
    statsData.value = userStats;

    skillsLoading.value = true;
    fetchUserSkills(userId)
      .then((data) => {
        skillsData.value = data.skills;
      })
      .catch((e) => {
        console.error("Failed to load skills data", e);
      })
      .finally(() => {
        skillsLoading.value = false;
      });
  } catch (e) {
    console.error("[PersonalView] Failed to load profile data", e);
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <div v-if="loading" class="flex h-[60vh] items-center justify-center">
    <div class="flex flex-col items-center gap-2">
      <div
        class="h-8 w-8 animate-spin rounded-full border-2 border-primary border-t-transparent"
      ></div>
      <p class="text-sm text-muted-foreground animate-pulse">
        {{ t("personal.profile.loadingProfile") }}
      </p>
    </div>
  </div>
  <div v-else-if="!user" class="flex h-[60vh] items-center justify-center">
    <Card class="w-full max-w-md border-dashed rounded-none">
      <CardContent class="flex flex-col items-center py-10 text-center">
        <div class="mb-4 rounded-full bg-muted p-3 text-muted-foreground">
          <Activity class="h-10 w-10" />
        </div>
        <h3 class="text-xl font-semibold">
          {{ t("personal.profile.authenticationRequired") }}
        </h3>
        <p class="mb-6 mt-2 text-sm text-muted-foreground">
          {{ t("personal.profile.loginToView") }}
        </p>
        <Button as-child class="px-6 font-bold">
          <RouterLink to="/login">{{
            t("personal.profile.signIn")
          }}</RouterLink>
        </Button>
      </CardContent>
    </Card>
  </div>
  <PersonalPageShell v-else-if="user">
    <UserProfileCard :user="user" />
    <UserStatsPanel
      :stats-data="statsData"
      :submissions="submissions"
      :skills-data="skillsData"
      :skills-loading="skillsLoading"
      :user-rank="user.globalRank ?? undefined"
    />
  </PersonalPageShell>
</template>
