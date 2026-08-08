<script setup lang="ts">
import { computed } from "vue";
import type { SolutionFeedItem } from "@/types/solution";
import { resolveVoteCounts } from "@/utils/vote";
import { formatRelativeTime } from "@/utils/datetime";
import { useI18n } from "vue-i18n";
import { ThumbsUp, Eye, MessageSquare } from "lucide-vue-next";
import { useAvatar } from "@/composables/useAvatar";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";

const props = defineProps<{
  item: SolutionFeedItem;
}>();

const emit = defineEmits<{
  select: [item: SolutionFeedItem];
}>();

const { t } = useI18n();

const authorInitial = computed(
  () => props.item.author.name.charAt(0)?.toUpperCase() ?? "?",
);

const { normalizedAvatar: authorAvatarUrl } = useAvatar(
  computed(() => props.item.author.username),
  computed(() => props.item.author.avatar),
);

const languageLabel = computed(() => {
  const lang = props.item.language || props.item.languageFilter;
  if (!lang) return t("problem.submissions.language");
  return lang.charAt(0).toUpperCase() + lang.slice(1).toLowerCase();
});

const topicLabel = computed(
  () =>
    props.item.topicName ||
    props.item.topicTranslated ||
    props.item.topic ||
    t("forum.post.flair"),
);

const formattedDate = computed(() => formatRelativeTime(props.item.created_at));

const handleSelect = () => emit("select", props.item);

const voteCounts = computed(() =>
  resolveVoteCounts(props.item.likes, props.item.dislikes, props.item.stats),
);
</script>

<template>
  <article
    class="group flex cursor-pointer flex-col gap-2.5 bg-card border border-border/40 p-4 rounded-none hover:border-[var(--accent-electric)]/50 hover:bg-muted hover:shadow-xs transition-all duration-200"
    tabindex="0"
    role="button"
    @click="handleSelect"
    @keyup.enter.prevent="handleSelect"
  >
    <header class="flex items-start gap-2.5">
      <Avatar class="h-8 w-8 border border-border/50">
        <AvatarImage :src="authorAvatarUrl" :alt="props.item.author.name" />
        <AvatarFallback
          class="text-xxs font-semibold text-white"
          :style="{ backgroundColor: props.item.author.avatarColor }"
        >
          {{ authorInitial }}
        </AvatarFallback>
      </Avatar>
      <div class="flex flex-1 flex-col gap-0.5 text-xs leading-none">
        <div class="flex flex-wrap items-center gap-1.5">
          <span class="font-bold text-foreground">
            {{ props.item.author.name }}
          </span>
          <span
            class="text-2xs font-bold uppercase tracking-wider text-muted-foreground bg-muted px-1 py-0.2 rounded-none"
          >
            {{ props.item.author.role }}
          </span>
          <span class="text-2xs text-muted-foreground ml-auto">
            {{ formattedDate }}
          </span>
          <span
            v-if="props.item.flair"
            class="rounded-none bg-[var(--terminal-amber)]/15 px-1.5 py-0.2 text-2xs font-bold uppercase tracking-wider text-[var(--terminal-amber)] border border-[var(--terminal-amber)]/20"
          >
            {{ props.item.flair }}
          </span>
        </div>

        <!-- Language and Topic badging -->
        <div class="flex flex-wrap items-center gap-1.5 mt-1.5">
          <span
            class="rounded-none bg-muted px-2 py-0.5 text-2xs font-bold text-[var(--accent-electric)] uppercase tracking-wider"
          >
            {{ languageLabel }}
          </span>
          <span
            class="rounded-none bg-muted px-2 py-0.5 text-2xs font-bold text-[var(--terminal-amber)] uppercase tracking-wider"
          >
            {{ topicLabel }}
          </span>
          <span
            v-for="badge in props.item.badges"
            :key="badge"
            class="rounded-none border border-border px-2 py-0.5 text-2xs font-bold text-foreground uppercase tracking-wider"
          >
            {{ badge }}
          </span>
        </div>
      </div>
    </header>

    <section class="space-y-1.5">
      <div class="space-y-0.5">
        <p
          v-if="props.item.highlight"
          class="text-2xs font-bold uppercase tracking-widest text-[var(--accent-electric)]"
        >
          {{ props.item.highlight }}
        </p>
        <h3
          class="text-sm font-bold text-foreground group-hover:text-[var(--accent-electric)] transition-colors line-clamp-1"
        >
          {{ props.item.title }}
        </h3>
      </div>
      <p class="line-clamp-2 text-xs text-muted-foreground leading-relaxed">
        {{ props.item.summary }}
      </p>
    </section>

    <!-- Footer containing tags & stats -->
    <footer
      class="mt-2.5 flex items-center justify-between border-t border-border/20 pt-2.5 gap-2"
    >
      <!-- Left: Algorithm Tags -->
      <div
        v-if="props.item.tags && props.item.tags.length"
        class="flex flex-wrap gap-1 items-center"
      >
        <span
          v-for="tag in props.item.tags.slice(0, 2)"
          :key="tag"
          class="rounded-none bg-muted/70 px-2 py-0.5 text-2xs text-muted-foreground capitalize border border-transparent hover:border-border transition-colors font-medium"
        >
          {{ tag }}
        </span>
        <span
          v-if="props.item.tags.length > 2"
          class="text-2xs font-bold text-muted-foreground bg-muted/40 px-1 py-0.2"
        >
          +{{ props.item.tags.length - 2 }}
        </span>
      </div>
      <div v-else />

      <!-- Right: Community Interaction Stats -->
      <div
        class="flex items-center gap-3.5 text-xxs font-data text-muted-foreground"
      >
        <div
          class="flex items-center gap-1 hover:text-[var(--solarized-green)] transition-colors"
        >
          <ThumbsUp class="h-3.5 w-3.5 text-muted-foreground/80 text-current" />
          <span class="font-bold">{{ voteCounts.likes }}</span>
        </div>
        <div
          class="flex items-center gap-1 hover:text-[var(--accent-electric)] transition-colors"
        >
          <Eye class="h-3.5 w-3.5 text-muted-foreground/80 text-current" />
          <span class="font-bold">{{ props.item.stats?.views ?? 0 }}</span>
        </div>
        <div
          class="flex items-center gap-1 hover:text-[var(--accent-electric)] transition-colors"
        >
          <MessageSquare
            class="h-3.5 w-3.5 text-muted-foreground/80 text-current"
          />
          <span class="font-bold">{{ props.item.stats?.comments ?? 0 }}</span>
        </div>
      </div>
    </footer>
  </article>
</template>
