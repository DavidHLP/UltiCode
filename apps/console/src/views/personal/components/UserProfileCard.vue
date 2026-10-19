<script setup lang="ts">
import {
  Avatar,
  AvatarFallback,
  AvatarImage,
} from "@ulticode/design-system/ui/avatar";
import { Button } from "@ulticode/design-system/ui/button";
import { Badge } from "@ulticode/design-system/ui/badge";
import { formatDate } from "@/utils/datetime";
import { MapPin, Link as LinkIcon, Calendar, Zap, Edit } from "@lucide/vue";
import { IconBrandGithub, IconBrandTwitter } from "@tabler/icons-vue";

import { RouterLink } from "vue-router";
import { useI18n } from "vue-i18n";
import { computed } from "vue";
import type { ProfileData } from "@/api/user";
import { useAvatar } from "@/composables/useAvatar";

const props = defineProps<{
  user: ProfileData;
}>();

const { t } = useI18n();
const { normalizedAvatar } = useAvatar(
  computed(() => props.user.username),
  computed(() => props.user.avatar),
);
</script>

<template>
  <div
    class="relative overflow-hidden rounded-lg border bg-card p-4 md:p-6 shadow-[var(--shadow-float)]"
  >
    <div
      class="absolute right-0 top-0 h-32 w-32 -translate-y-8 translate-x-8 rounded-full bg-primary/10 blur-3xl"
    ></div>
    <div
      class="absolute bottom-0 left-0 h-32 w-32 translate-y-8 -translate-x-8 rounded-full bg-primary/5 blur-3xl"
    ></div>

    <div class="relative flex flex-col gap-6 md:flex-row md:items-center">
      <div class="shrink-0 flex justify-center md:block">
        <div class="relative group">
          <div
            class="absolute -inset-1 rounded-lg bg-primary opacity-20 blur-sm group-hover:opacity-40 transition duration-500"
          ></div>
          <Avatar class="h-24 w-24 relative">
            <AvatarImage :src="normalizedAvatar" :alt="user.name" />
            <AvatarFallback>{{
              user.username.substring(0, 2).toUpperCase()
            }}</AvatarFallback>
          </Avatar>
          <div
            class="absolute -bottom-1 -right-1 flex h-8 w-8 items-center justify-center rounded-lg bg-primary text-primary-foreground shadow-[var(--shadow-float)] border-4 border-background"
            :title="t('personal.profile.proMember')"
          >
            <Zap class="h-3.5 w-3.5 fill-current" />
          </div>
        </div>
      </div>

      <div class="flex flex-1 flex-col space-y-3 text-center md:text-left">
        <div
          class="flex flex-col md:flex-row md:items-center justify-between gap-3"
        >
          <div class="space-y-0.5">
            <h1
              class="text-2xl md:text-3xl font-extrabold tracking-tighter text-foreground"
            >
              {{ user.name || user.username }}
            </h1>
            <div
              class="flex items-center justify-center md:justify-start gap-2"
            >
              <span class="text-base font-medium text-muted-foreground"
                >@{{ user.username }}</span
              >
              <Badge variant="secondary" class="px-2 py-0 h-5">
                {{ t("personal.profile.proBadge") }}
              </Badge>
            </div>
          </div>
          <Button
            variant="outline"
            size="sm"
            class="gap-1.5 self-center md:self-start px-4"
            as-child
          >
            <RouterLink to="/personal/account">
              <Edit class="h-4 w-4" />
              {{ t("personal.profile.editProfile") }}
            </RouterLink>
          </Button>
        </div>

        <p class="max-w-2xl text-base leading-relaxed text-muted-foreground">
          {{ user.bio || t("personal.profile.noBio") }}
        </p>

        <div
          class="flex flex-wrap justify-center md:justify-start gap-x-6 gap-y-3 text-sm font-medium"
        >
          <div
            class="flex items-center gap-1.5 text-muted-foreground"
            v-if="user.location"
          >
            <MapPin class="h-4 w-4 text-primary/70" />
            <span>{{ user.location }}</span>
          </div>
          <div class="flex items-center gap-1.5" v-if="user.website">
            <LinkIcon class="h-4 w-4 text-primary/70" />
            <a
              :href="user.website"
              class="text-primary hover:underline transition-all underline-offset-4"
              target="_blank"
              rel="noopener noreferrer"
              >{{ user.website.replace(/^https?:\/\//, "") }}</a
            >
          </div>
          <div
            class="flex items-center gap-1.5 text-muted-foreground"
            v-if="user.github"
          >
            <IconBrandGithub class="h-4 w-4 text-primary/70" />
            <span>{{ user.github }}</span>
          </div>
          <div
            class="flex items-center gap-1.5 text-muted-foreground"
            v-if="user.twitter"
          >
            <IconBrandTwitter class="h-4 w-4 text-primary/70" />
            <span>{{ user.twitter }}</span>
          </div>
          <div class="flex items-center gap-1.5 text-muted-foreground">
            <Calendar class="h-4 w-4 text-primary/70" />
            <span>{{
              t("personal.profile.joinedDate", {
                date: user.joinedAt
                  ? formatDate(user.joinedAt, undefined, {
                      month: "long",
                      year: "numeric",
                    })
                  : "Recently",
              })
            }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
