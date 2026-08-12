<script setup lang="ts">
import { onMounted, ref } from "vue";
import { RouterLink } from "vue-router";
import { Button } from "@/components/ui/button";
import { Card, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { formatDate } from "@/utils/datetime";
import { SemanticBadge } from "@/components/ui/terminal";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  MessageSquare,
  Eye,
  MoreVertical,
  Pencil,
  Trash2,
  Plus,
  Loader2,
  Calendar,
} from "lucide-vue-next";
import { fetchMyForumPosts, deleteForumPost } from "@/api/forum";
import type { ForumPost } from "@/types/forum";
import { toast } from "vue-sonner";
import { useI18n } from "vue-i18n";
import PersonalPageHeader from "./components/PersonalPageHeader.vue";
import PersonalPageShell from "./components/PersonalPageShell.vue";

const { t, locale } = useI18n();
const isLoading = ref(true);
const posts = ref<ForumPost[]>([]);

async function loadPosts() {
  isLoading.value = true;
  try {
    const result = await fetchMyForumPosts();
    posts.value = result.posts;
  } catch (error) {
    console.error("Failed to load forum posts", error);
    toast.error(t("personal.messages.loadFailed"));
  } finally {
    isLoading.value = false;
  }
}

async function handleDelete(postId: string) {
  try {
    await deleteForumPost(postId);
    posts.value = posts.value.filter((post) => post.id !== postId);
    toast.success(t("forum.messages.postDeleted"));
  } catch (error) {
    console.error("Failed to delete post", error);
    toast.error(t("forum.messages.deleteFailed"));
  }
}

onMounted(loadPosts);
</script>

<template>
  <PersonalPageShell>
    <PersonalPageHeader
      :title="t('personal.forumPosts.title')"
      :description="t('personal.forumPosts.subtitle')"
    >
      <template #actions>
        <Button as-child class="gap-2">
          <RouterLink to="/forum/create">
            <Plus class="h-4 w-4" />
            {{ t("personal.forumPosts.newPost") }}
          </RouterLink>
        </Button>
      </template>
    </PersonalPageHeader>

    <div
      v-if="isLoading"
      class="flex flex-col items-center justify-center py-20 gap-4"
    >
      <Loader2 class="h-10 w-10 animate-spin text-primary" />
      <p class="text-sm text-muted-foreground">
        {{ t("personal.forumPosts.loadingPosts") }}
      </p>
    </div>

    <div
      v-else-if="posts.length === 0"
      class="flex flex-col items-center justify-center py-24 rounded-none border-2 border-dashed border-muted/50 bg-muted/5 text-center px-6"
    >
      <div
        class="flex h-16 w-16 items-center justify-center rounded-none bg-muted/50 mb-4"
      >
        <MessageSquare class="h-8 w-8 text-muted-foreground/50" />
      </div>
      <h3 class="text-xl font-bold">{{ t("personal.forumPosts.noPosts") }}</h3>
      <p class="mb-8 mt-2 max-w-[300px] text-sm text-muted-foreground">
        {{ t("personal.forumPosts.noPostsDesc") }}
      </p>
      <Button as-child class="px-8 h-10 font-bold">
        <RouterLink to="/forum/create">{{
          t("personal.forumPosts.createFirst")
        }}</RouterLink>
      </Button>
    </div>

    <div v-else class="grid gap-6">
      <Card
        v-for="post in posts"
        :key="post.id"
        class="group hover:shadow-[var(--shadow-float)] transition-all duration-300 border-muted/60 overflow-hidden rounded-none"
      >
        <div class="flex flex-col sm:flex-row">
          <div class="flex-1 p-6">
            <div class="flex items-center gap-2 mb-3">
              <Badge
                variant="secondary"
                class="bg-primary/5 text-primary border-primary/10 font-semibold hover:bg-primary/10 transition-colors rounded-none"
              >
                {{ post.community?.name ?? "General" }}
              </Badge>
              <div
                class="flex items-center gap-1.5 text-2xs font-semibold text-muted-foreground uppercase tracking-wider"
              >
                <Calendar class="h-3 w-3" />
                {{
                  formatDate(post.createdAt, locale, {
                    month: "short",
                    day: "numeric",
                    year: "numeric",
                  })
                }}
              </div>
            </div>

            <div class="flex items-start justify-between gap-4">
              <div class="space-y-2 flex-1">
                <CardTitle
                  class="text-lg font-semibold group-hover:text-primary transition-colors"
                >
                  <RouterLink
                    :to="{ name: 'forum-thread', params: { postId: post.id } }"
                  >
                    {{ post.title }}
                  </RouterLink>
                </CardTitle>

                <div class="flex flex-wrap items-center gap-2">
                  <SemanticBadge
                    v-if="post.isPinned"
                    color="warning"
                    :label="t('personal.forumPosts.pinned', 'Pinned')"
                    size="xs"
                  />
                  <SemanticBadge
                    v-if="post.isLocked"
                    color="neutral"
                    :label="t('personal.forumPosts.locked', 'Locked')"
                    size="xs"
                  />
                  <Badge
                    v-if="post.flair"
                    variant="secondary"
                    class="rounded-none h-5 text-2xs font-semibold uppercase"
                  >
                    {{ post.flair.text }}
                  </Badge>
                </div>
              </div>

              <DropdownMenu>
                <DropdownMenuTrigger as-child>
                  <Button
                    variant="ghost"
                    size="icon"
                    class="h-8 w-8 rounded-full"
                  >
                    <MoreVertical class="h-4 w-4" />
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end" class="w-40">
                  <DropdownMenuItem as-child class="gap-2">
                    <RouterLink
                      :to="{
                        name: 'forum-thread',
                        params: { postId: post.id },
                      }"
                    >
                      <Eye class="h-4 w-4" />
                      {{ t("personal.forumPosts.viewPost") }}
                    </RouterLink>
                  </DropdownMenuItem>
                  <DropdownMenuItem as-child class="gap-2">
                    <RouterLink
                      :to="{ name: 'forum-edit', params: { postId: post.id } }"
                    >
                      <Pencil class="h-4 w-4" />
                      {{ t("personal.forumPosts.edit") }}
                    </RouterLink>
                  </DropdownMenuItem>
                  <DropdownMenuSeparator />
                  <AlertDialog>
                    <AlertDialogTrigger as-child>
                      <DropdownMenuItem
                        @select.prevent
                        class="text-destructive focus:text-destructive gap-2"
                      >
                        <Trash2 class="h-4 w-4" />
                        {{ t("personal.forumPosts.delete") }}
                      </DropdownMenuItem>
                    </AlertDialogTrigger>
                    <AlertDialogContent>
                      <AlertDialogHeader>
                        <AlertDialogTitle>{{
                          t("personal.forumPosts.deleteDialog.title")
                        }}</AlertDialogTitle>
                        <AlertDialogDescription>
                          {{
                            t("personal.forumPosts.deleteDialog.description")
                          }}
                        </AlertDialogDescription>
                      </AlertDialogHeader>
                      <AlertDialogFooter>
                        <AlertDialogCancel>{{
                          t("common.actions.cancel")
                        }}</AlertDialogCancel>
                        <AlertDialogAction
                          @click="handleDelete(post.id)"
                          class="bg-status-error-surface text-foreground-strong border border-destructive hover:bg-status-error-surface/80"
                        >
                          {{ t("common.actions.delete") }}
                        </AlertDialogAction>
                      </AlertDialogFooter>
                    </AlertDialogContent>
                  </AlertDialog>
                </DropdownMenuContent>
              </DropdownMenu>
            </div>
          </div>

          <div
            class="sm:w-48 bg-muted/30 border-l border-t sm:border-t-0 p-6 flex sm:flex-col justify-around items-center gap-4"
          >
            <div class="flex flex-col items-center gap-1 group/stat">
              <MessageSquare
                class="h-5 w-5 text-muted-foreground group-hover/stat:text-primary transition-colors"
              />
              <span class="text-xl font-bold tracking-tight leading-none">{{
                post.stats?.comments ?? 0
              }}</span>
              <span
                class="text-2xs font-semibold text-muted-foreground uppercase tracking-widest"
                >{{ t("personal.forumPosts.stats.comments") }}</span
              >
            </div>
            <div class="flex flex-col items-center gap-1 group/stat">
              <Eye
                class="h-5 w-5 text-muted-foreground group-hover/stat:text-primary transition-colors"
              />
              <span class="text-xl font-bold tracking-tight leading-none">{{
                post.stats?.views ?? 0
              }}</span>
              <span
                class="text-2xs font-semibold text-muted-foreground uppercase tracking-widest"
                >{{ t("personal.forumPosts.stats.views") }}</span
              >
            </div>
          </div>
        </div>
      </Card>
    </div>
  </PersonalPageShell>
</template>
