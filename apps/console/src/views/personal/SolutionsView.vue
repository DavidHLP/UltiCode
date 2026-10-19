<script setup lang="ts">
import { useAuthStore } from "@/stores/auth";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  CardFooter,
} from "@ulticode/design-system/ui/card";
import { Badge } from "@ulticode/design-system/ui/badge";
import { Button } from "@ulticode/design-system/ui/button";
import {
  ThumbsUp,
  MessageSquare,
  Eye,
  ArrowRight,
  FileCode2,
  MoreVertical,
  Pencil,
  Trash2,
  Calendar,
  Code2,
  Loader2,
  Plus,
} from "@lucide/vue";
import { onMounted, ref } from "vue";
import {
  fetchUserSolutions,
  type SolutionFeedResponse,
  deleteSolution,
} from "@/api/solution";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
  DropdownMenuGroup,
} from "@ulticode/design-system/ui/dropdown-menu";
import { toast } from "vue-sonner";
import { RouterLink, useRouter } from "vue-router";
import { useI18n } from "vue-i18n";
import RetryButton from "@/components/common/loading/RetryButton.vue";
import PersonalPageHeader from "./components/PersonalPageHeader.vue";
import PersonalPageShell from "./components/PersonalPageShell.vue";

const router = useRouter();
const { t, locale } = useI18n();

const solutions = ref<SolutionFeedResponse["items"]>([]);
const loading = ref(true);
const hasUser = ref(false);
const loadError = ref(false);

const handleEdit = (solutionId: string) => {
  router.push({ name: "solution-edit", params: { id: solutionId } });
};

const handleView = (solution: SolutionFeedResponse["items"][number]) => {
  const slugOrId = solution.problem?.slug || solution.problem_id;
  router.push({
    name: "problem-detail",
    params: { slug: slugOrId, tab: "solution" },
  });
};

const handleDelete = async (solutionId: string) => {
  const confirmed = window.confirm(t("personal.solutions.deleteConfirm"));
  if (!confirmed) return;
  try {
    await deleteSolution(solutionId);
    toast.success(t("personal.messages.profileUpdated"));
    // Remove from list
    solutions.value = solutions.value.filter((s) => s.id !== solutionId);
  } catch (error) {
    console.error("Failed to delete solution", error);
    toast.error(t("personal.messages.mutationFailed"));
  }
};

async function loadSolutions() {
  loading.value = true;
  loadError.value = false;
  solutions.value = [];
  try {
    const userId = useAuthStore().fetchCurrentUserId();
    if (!userId) return;
    hasUser.value = true;
    const response = await fetchUserSolutions(userId);
    solutions.value = response.items;
  } catch (e) {
    loadError.value = true;
    console.error("Failed to load solutions", e);
  } finally {
    loading.value = false;
  }
}

onMounted(loadSolutions);
</script>

<template>
  <PersonalPageShell>
    <PersonalPageHeader
      :title="t('personal.solutions.title')"
      :description="t('personal.solutions.subtitle')"
    >
      <template #actions>
        <Button as-child class="gap-2">
          <RouterLink to="/problemset">
            <Plus class="h-4 w-4" />
            {{ t("personal.solutions.newSolution") }}
          </RouterLink>
        </Button>
      </template>
    </PersonalPageHeader>

    <div
      v-if="loading"
      class="flex flex-col items-center justify-center py-20 gap-4"
    >
      <Loader2 class="h-10 w-10 animate-spin text-primary" />
      <p class="text-sm text-muted-foreground">
        {{ t("personal.solutions.loadingSolutions") }}
      </p>
    </div>

    <div
      v-else-if="!hasUser"
      class="text-center py-20 border-2 border-dashed rounded-lg"
    >
      <p class="text-muted-foreground mb-4">
        {{ t("personal.solutions.loginToView") }}
      </p>
      <Button as-child>
        <RouterLink to="/login">{{ t("personal.profile.signIn") }}</RouterLink>
      </Button>
    </div>

    <div
      v-else-if="loadError"
      role="alert"
      class="space-y-3 rounded-lg border bg-card p-6 text-center [overflow-wrap:anywhere]"
    >
      <p class="text-destructive">{{ t("personal.solutions.loadError") }}</p>
      <RetryButton @retry="loadSolutions" />
    </div>

    <div
      v-else-if="solutions.length > 0"
      class="grid gap-6 md:grid-cols-2 xl:grid-cols-3"
    >
      <Card v-for="sol in solutions" :key="sol.id" class="flex flex-col group">
        <CardHeader class="pb-3">
          <div class="flex items-center justify-between mb-3">
            <Badge variant="outline">
              {{ sol.language }}
            </Badge>
            <div
              class="flex items-center gap-1.5 text-xs font-bold text-muted-foreground uppercase tracking-wider"
            >
              <Calendar class="h-3 w-3" />
              {{
                new Date(sol.created_at).toLocaleDateString(locale, {
                  month: "short",
                  day: "numeric",
                  year: "numeric",
                })
              }}
            </div>
          </div>

          <div class="flex items-start justify-between gap-2">
            <div class="space-y-1 flex-1 min-w-0">
              <CardTitle
                class="line-clamp-2 group-hover:text-primary cursor-pointer"
                @click="handleView(sol)"
              >
                {{ sol.title }}
              </CardTitle>
              <div
                class="flex items-center gap-1.5 text-xs font-medium text-muted-foreground"
              >
                <code2 class="h-3.5 w-3.5" />
                <span class="truncate"
                  >{{ t("personal.submissions.problem") }}:
                  {{ sol.problem?.title || "Unknown" }}</span
                >
              </div>
            </div>

            <DropdownMenu>
              <DropdownMenuTrigger as-child>
                <Button variant="ghost" size="icon" class="w-8">
                  <MoreVertical class="h-4 w-4" />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end" class="w-40">
                <DropdownMenuGroup
                  ><DropdownMenuItem @click="handleView(sol)" class="gap-2">
                    <Eye class="h-4 w-4" /> {{ t("personal.solutions.view") }}
                  </DropdownMenuItem>
                  <DropdownMenuItem @click="handleEdit(sol.id)" class="gap-2">
                    <Pencil class="h-4 w-4" />
                    {{ t("personal.solutions.edit") }}
                  </DropdownMenuItem>
                  <DropdownMenuItem @click="handleDelete(sol.id)" class="gap-2">
                    <Trash2 class="h-4 w-4" />
                    {{ t("personal.solutions.delete") }}
                  </DropdownMenuItem></DropdownMenuGroup
                >
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        </CardHeader>

        <CardContent class="flex-1 pb-4">
          <div class="flex flex-wrap gap-1.5 mt-2">
            <Badge
              v-for="tag in sol.tags"
              :key="tag"
              variant="secondary"
              class="py-0 h-5"
            >
              {{ tag }}
            </Badge>
            <span
              v-if="!sol.tags || sol.tags.length === 0"
              class="text-xs text-muted-foreground italic"
              >{{ t("personal.solutions.noTags") }}</span
            >
          </div>
        </CardContent>

        <CardFooter class="px-4 py-3">
          <div class="flex w-full items-center justify-between">
            <div class="flex items-center gap-4">
              <div
                class="flex items-center gap-1.5 text-muted-foreground group/stat"
              >
                <ThumbsUp
                  class="h-3.5 w-3.5 group-hover/stat:text-primary transition-colors"
                />
                <span class="text-xs font-bold">{{ sol.votes }}</span>
              </div>
              <div
                class="flex items-center gap-1.5 text-muted-foreground group/stat"
              >
                <Eye
                  class="h-3.5 w-3.5 group-hover/stat:text-primary transition-colors"
                />
                <span class="text-xs font-bold">{{ sol.views }}</span>
              </div>
              <div
                class="flex items-center gap-1.5 text-muted-foreground group/stat"
              >
                <MessageSquare
                  class="h-3.5 w-3.5 group-hover/stat:text-primary transition-colors"
                />
                <span class="text-xs font-bold">{{
                  sol.stats?.comments || 0
                }}</span>
              </div>
            </div>
            <Button
              variant="ghost"
              size="sm"
              class="gap-1"
              @click="handleView(sol)"
            >
              {{ t("personal.solutions.details") }}
              <ArrowRight class="h-3 w-3" />
            </Button>
          </div>
        </CardFooter>
      </Card>
    </div>

    <div
      v-else
      class="flex flex-col items-center justify-center py-24 rounded-lg border-2 border-dashed border-muted/50 bg-muted/5 text-center px-6"
    >
      <div
        class="flex h-16 w-16 items-center justify-center rounded-lg bg-muted/50 mb-4"
      >
        <FileCode2 class="h-8 w-8 text-muted-foreground/50" />
      </div>
      <h3 class="text-xl font-bold">
        {{ t("personal.solutions.noSolutions") }}
      </h3>
      <p class="mb-8 mt-2 max-w-[300px] text-sm text-muted-foreground">
        {{ t("personal.solutions.noSolutionsDesc") }}
      </p>
      <Button as-child class="px-8">
        <RouterLink to="/problemset">{{
          t("personal.solutions.startCoding")
        }}</RouterLink>
      </Button>
    </div>
  </PersonalPageShell>
</template>
