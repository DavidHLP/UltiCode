<script setup lang="ts">
import type {
  ForumCommunity,
  ForumCommunityRule,
  ForumCommunityLink,
} from "@/types/forum";
import { useI18n } from "vue-i18n";

defineProps<{
  community?: ForumCommunity | null;
  rules?: ForumCommunityRule[];
  links?: ForumCommunityLink[];
}>();

const { t } = useI18n();
</script>

<template>
  <aside class="hidden w-[312px] flex-none space-y-4 lg:block">
    <!-- Community Info (when viewing specific community) -->
    <div
      v-if="community"
      class="overflow-hidden rounded-lg border bg-card text-card-foreground shadow-sm"
    >
      <div
        class="bg-muted/50 p-4 font-medium"
        :style="{
          borderLeftColor: community.color || '#6366f1',
          borderLeftWidth: '4px',
        }"
      >
        {{ t("forum.sidebar.aboutCommunityPrefix") }} {{ community.name }}
      </div>
      <div class="p-4 text-sm">
        <p class="text-muted-foreground">{{ community.description }}</p>

        <!-- Stats -->
        <div class="mt-4 space-y-2">
          <div class="flex justify-between text-xs">
            <span class="text-muted-foreground">{{
              t("forum.sidebar.members")
            }}</span>
            <span class="font-medium">{{
              community.members?.toLocaleString()
            }}</span>
          </div>
          <div class="flex justify-between text-xs">
            <span class="text-muted-foreground">{{
              t("forum.sidebar.online")
            }}</span>
            <span class="font-medium text-green-600">{{
              community.online?.toLocaleString()
            }}</span>
          </div>
          <div v-if="community.postsToday" class="flex justify-between text-xs">
            <span class="text-muted-foreground">{{
              t("forum.sidebar.postsToday")
            }}</span>
            <span class="font-medium">{{ community.postsToday }}</span>
          </div>
          <div v-if="community.postsWeek" class="flex justify-between text-xs">
            <span class="text-muted-foreground">{{
              t("forum.sidebar.postsWeek")
            }}</span>
            <span class="font-medium">{{ community.postsWeek }}</span>
          </div>
        </div>

        <!-- Rules -->
        <div v-if="rules && rules.length > 0" class="mt-4">
          <h3 class="mb-2 text-sm font-semibold">
            {{ t("forum.sidebar.communityRules") }}
          </h3>
          <ol class="space-y-2 text-xs">
            <li v-for="rule in rules" :key="rule.id">
              <strong>{{ rule.sortOrder }}. {{ rule.title }}</strong>
              <p class="text-muted-foreground">{{ rule.body }}</p>
            </li>
          </ol>
        </div>

        <!-- Links -->
        <div v-if="links && links.length > 0" class="mt-4">
          <h3 class="mb-2 text-sm font-semibold">
            {{ t("forum.sidebar.resources") }}
          </h3>
          <ul class="space-y-1 text-xs">
            <li v-for="link in links" :key="link.id">
              <a
                :href="link.url"
                target="_blank"
                rel="noopener noreferrer"
                class="text-primary hover:underline"
              >
                {{ link.label }}
              </a>
            </li>
          </ul>
        </div>
      </div>
    </div>

    <!-- Default sidebar (when viewing all posts) -->
    <div
      v-else
      class="overflow-hidden rounded-lg border bg-card text-card-foreground shadow-sm"
    >
      <div class="bg-muted/50 p-4 font-medium">
        {{ t("forum.sidebar.aboutCommunity") }}
      </div>
      <div class="p-4 text-sm text-muted-foreground">
        <p>
          {{ t("forum.sidebar.welcome") }}
        </p>
      </div>
    </div>
  </aside>
</template>
