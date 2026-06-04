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
    <div v-if="community" class="terminal-card overflow-hidden shadow-sm">
      <div
        class="terminal-card-header p-4 font-medium"
        :style="{
          borderLeftColor: community.color || 'oklch(0.5924 0.2025 355.9)',
          borderLeftWidth: '4px',
        }"
      >
        {{ t("forum.sidebar.aboutCommunityPrefix") }} {{ community.name }}
      </div>
      <div class="p-4 text-sm">
        <p class="text-muted-foreground">{{ community.description }}</p>

        <!-- Stats -->
        <div class="mt-4 space-y-2">
          <div class="flex justify-between text-xs font-data">
            <span class="text-muted-foreground">{{
              t("forum.sidebar.members")
            }}</span>
            <span class="font-bold tabular-nums text-foreground">{{
              community.members?.toLocaleString()
            }}</span>
          </div>
          <div class="flex justify-between text-xs font-data">
            <span class="text-muted-foreground">{{
              t("forum.sidebar.online")
            }}</span>
            <span class="font-bold tabular-nums text-[var(--terminal-green)]">{{
              community.online?.toLocaleString()
            }}</span>
          </div>
          <div
            v-if="community.postsToday"
            class="flex justify-between text-xs font-data"
          >
            <span class="text-muted-foreground">{{
              t("forum.sidebar.postsToday")
            }}</span>
            <span class="font-bold tabular-nums text-foreground">{{
              community.postsToday
            }}</span>
          </div>
          <div
            v-if="community.postsWeek"
            class="flex justify-between text-xs font-data"
          >
            <span class="text-muted-foreground">{{
              t("forum.sidebar.postsWeek")
            }}</span>
            <span class="font-bold tabular-nums text-foreground">{{
              community.postsWeek
            }}</span>
          </div>
        </div>

        <!-- Rules -->
        <div
          v-if="rules && rules.length > 0"
          class="mt-4 border-t border-silver/40 pt-4"
        >
          <h3
            class="mb-2 text-xs font-black uppercase tracking-widest text-foreground font-data"
          >
            {{ t("forum.sidebar.communityRules") }}
          </h3>
          <ol class="space-y-2 text-xs">
            <li v-for="rule in rules" :key="rule.id">
              <strong class="font-data font-bold text-foreground"
                >{{ rule.sortOrder }}. {{ rule.title }}</strong
              >
              <p class="text-muted-foreground mt-0.5">{{ rule.body }}</p>
            </li>
          </ol>
        </div>

        <!-- Links -->
        <div
          v-if="links && links.length > 0"
          class="mt-4 border-t border-silver/40 pt-4"
        >
          <h3
            class="mb-2 text-xs font-black uppercase tracking-widest text-foreground font-data"
          >
            {{ t("forum.sidebar.resources") }}
          </h3>
          <ul class="space-y-1 text-xs">
            <li v-for="link in links" :key="link.id">
              <a
                :href="link.url"
                target="_blank"
                rel="noopener noreferrer"
                class="text-[var(--accent-electric)] hover:underline flex items-center gap-1 font-data"
              >
                <span>{{ link.label }}</span>
              </a>
            </li>
          </ul>
        </div>
      </div>
    </div>

    <!-- Default sidebar (when viewing all posts) -->
    <div v-else class="terminal-card overflow-hidden shadow-sm">
      <div class="terminal-card-header">
        {{ t("forum.sidebar.aboutCommunity") }}
      </div>
      <div class="p-4 text-sm text-muted-foreground leading-relaxed">
        <p>
          {{ t("forum.sidebar.welcome") }}
        </p>
      </div>
    </div>
  </aside>
</template>
