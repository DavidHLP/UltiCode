<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { Paperclip } from "@lucide/vue";
import { formatDateTime } from "@ulticode/datetime-utils";
import { Button } from "@ulticode/design-system/ui/button";
import { Input } from "@ulticode/design-system/ui/input";
import { Checkbox } from "@ulticode/design-system/ui/checkbox";
import { FieldLabel } from "@ulticode/design-system/ui/field";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@ulticode/design-system/ui/dialog";
import {
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from "@ulticode/design-system/ui/tabs";
import { fetchProblems } from "@/api/problem";
import { fetchSubmission, fetchUserSubmissions } from "@/api/submission";
import { fetchSolutionFeed, fetchSolutionComments } from "@/api/solution";
import { fetchForumPosts, fetchForumThread } from "@/api/forum";
import type { ForumPost, ForumComment } from "@/types/forum";
import type { AgentAnalysisContext } from "@/api/agent";
import type { Problem } from "@/types/problem";
import type { SubmissionRecord } from "@/types/submission";
import type { SolutionComment } from "@/types/comment";
import type { SolutionFeedItem } from "@/types/solution";
import { useAuthStore } from "@/stores/auth";
import { isAgentReviewEligible } from "@/views/problems/submissions/agentReviewPolicy";

const props = defineProps<{
  submissionId: string;
  context: AgentAnalysisContext;
  disabled?: boolean;
  hasConversation: boolean;
}>();
const emit = defineEmits<{
  select: [submission: SubmissionRecord, context: AgentAnalysisContext];
}>();
const { t } = useI18n();
const auth = useAuthStore();
const open = ref(false);
defineExpose({
  show: () => {
    if (!props.disabled) open.value = true;
  },
});
const search = ref("");
const problems = ref<Problem[]>([]);
const problem = ref<{ id: number; title: string } | null>(null);
const submissions = ref<SubmissionRecord[]>([]);
const selected = ref<SubmissionRecord | null>(null);
const solutions = ref<SolutionFeedItem[]>([]);
const comments = ref<SolutionComment[]>([]);
const solutionId = ref("");
const posts = ref<ForumPost[]>([]);
const postId = ref("");
const forumComments = ref<ForumComment[]>([]);
const forumPage = ref(1);
const forumHasMore = ref(false);
const forumSearch = ref("");
const filteredPosts = computed(() =>
  posts.value.filter((item) =>
    item.title
      .toLocaleLowerCase()
      .includes(forumSearch.value.trim().toLocaleLowerCase()),
  ),
);
const flattenedForumComments = computed(() => {
  const result: ForumComment[] = [];
  const visit = (items: ForumComment[]) => {
    for (const item of items) {
      result.push(item);
      if (item.replies) visit(item.replies);
    }
  };
  visit(forumComments.value);
  return result;
});
const includeProblem = ref(false);
const references = ref<AgentAnalysisContext["comments"]>([]);
const busy = ref(false);
const error = ref("");
const failedAction = ref<((token: number) => Promise<void>) | null>(null);
const page = ref(1);
const hasMore = ref(false);
let generation = 0;

const flattenedComments = computed(() => {
  const result: SolutionComment[] = [];
  const visit = (items: SolutionComment[]) => {
    for (const item of items) {
      result.push(item);
      if (item.replies) visit(item.replies);
    }
  };
  visit(comments.value);
  return result;
});
function checked(id: string, forum = false) {
  return references.value.some(
    (item) =>
      (forum
        ? item.postId === postId.value
        : item.solutionId === solutionId.value) && item.commentId === id,
  );
}
function toggleComment(id: string, value: boolean, forum = false) {
  references.value = references.value.filter(
    (item) =>
      !(forum
        ? item.postId === postId.value
        : item.solutionId === solutionId.value) || item.commentId !== id,
  );
  if (value && references.value.length < 3)
    references.value.push(
      forum
        ? { postId: postId.value, commentId: id }
        : { solutionId: solutionId.value, commentId: id },
    );
}
async function loadPosts(nextPage = 1) {
  await request(async (token) => {
    const result = await fetchForumPosts({ page: nextPage, pageSize: 10 });
    if (token !== generation) return;
    posts.value = result.items;
    forumPage.value = nextPage;
    forumHasMore.value = nextPage * 10 < result.total;
  });
}
async function choosePost(id: string) {
  postId.value = id;
  forumComments.value = [];
  await request(async (token) => {
    const thread = await fetchForumThread(id);
    if (token === generation) forumComments.value = thread.comments;
  });
}
async function request(action: (token: number) => Promise<void>) {
  const token = ++generation;
  busy.value = true;
  error.value = "";
  failedAction.value = null;
  try {
    await action(token);
  } catch {
    if (token === generation) {
      error.value = t("agent.context.loadFailed");
      failedAction.value = action;
    }
  } finally {
    if (token === generation) busy.value = false;
  }
}
async function searchProblems() {
  await request(async (token) => {
    const result = await fetchProblems({ search: search.value.trim() }, 1, 10);
    if (token === generation) problems.value = result.items;
  });
}
async function loadSubmissions(nextPage = 1) {
  if (!problem.value) return;
  const problemId = problem.value.id;
  await request(async (token) => {
    const result = await fetchUserSubmissions({ problemId, page: nextPage });
    if (token !== generation) return;
    page.value = nextPage;
    submissions.value = result.items;
    hasMore.value = nextPage * 10 < result.total;
  });
}
async function chooseProblem(item: { id: number; title: string }) {
  problem.value = item;
  selected.value = null;
  submissions.value = [];
  references.value = [];
  forumComments.value = [];
  postId.value = "";
  comments.value = [];
  solutions.value = [];
  solutionId.value = "";
  await loadSubmissions();
}
async function chooseSubmission(id: string) {
  await request(async (token) => {
    const item = await fetchSubmission(id);
    if (token !== generation) return;
    if (
      item.problem_id !== problem.value?.id ||
      !isAgentReviewEligible(item, auth.user?.id ?? null, true)
    ) {
      error.value = t("agent.context.ineligible");
      return;
    }
    selected.value = item;
  });
}
async function loadSolutions() {
  if (!problem.value || !selected.value) return;
  await request(async (token) => {
    const result = await fetchSolutionFeed(problem.value!.id);
    if (token === generation) solutions.value = result.items;
  });
}
async function chooseSolution(id: string) {
  solutionId.value = id;
  comments.value = [];
  await request(async (token) => {
    const result = await fetchSolutionComments(id);
    if (token === generation) comments.value = result;
  });
}
function apply() {
  if (!selected.value || busy.value || props.disabled) return;
  emit("select", selected.value, {
    includeProblem: includeProblem.value,
    comments: [...references.value],
  });
  open.value = false;
}
watch([open, () => auth.user?.id, () => props.submissionId], async () => {
  ++generation;
  busy.value = false;
  error.value = "";
  problems.value = [];
  comments.value = [];
  solutions.value = [];
  submissions.value = [];
  posts.value = [];
  forumComments.value = [];
  postId.value = "";
  forumSearch.value = "";
  solutionId.value = "";
  problem.value = null;
  selected.value = null;
  if (!open.value) return;
  includeProblem.value = props.context.includeProblem;
  references.value = [...props.context.comments];
  if (props.submissionId) {
    await request(async (token) => {
      const item = await fetchSubmission(props.submissionId);
      if (token !== generation) return;
      if (!isAgentReviewEligible(item, auth.user?.id ?? null, true)) {
        error.value = t("agent.context.ineligible");
        return;
      }
      selected.value = item;
      problem.value = {
        id: item.problem_id,
        title: item.problem?.title ?? `#${item.problem_id}`,
      };
    });
  } else await searchProblems();
});
</script>

<template>
  <Dialog v-model:open="open">
    <DialogTrigger as-child
      ><Button type="button" variant="outline" size="sm" :disabled="disabled"
        ><Paperclip class="mr-2 size-4" aria-hidden="true" />{{
          t("agent.context.choose")
        }}</Button
      ></DialogTrigger
    >
    <DialogContent class="max-h-[85dvh] overflow-y-auto sm:max-w-2xl">
      <DialogHeader
        ><DialogTitle>{{ t("agent.context.title") }}</DialogTitle
        ><DialogDescription>{{
          t("agent.context.description")
        }}</DialogDescription></DialogHeader
      >
      <Tabs
        default-value="submission"
        @update:model-value="
          $event === 'comments'
            ? loadSolutions()
            : $event === 'forum'
              ? loadPosts()
              : undefined
        "
      >
        <TabsList
          ><TabsTrigger value="submission">{{
            t("agent.context.problemSubmission")
          }}</TabsTrigger
          ><TabsTrigger value="comments" :disabled="!selected">{{
            t("agent.context.solutionComments")
          }}</TabsTrigger
          ><TabsTrigger value="forum" :disabled="!selected">{{
            t("agent.context.forumComments")
          }}</TabsTrigger></TabsList
        >
        <TabsContent value="submission" class="space-y-4">
          <form class="flex gap-2" @submit.prevent="searchProblems">
            <Input
              v-model="search"
              :aria-label="t('agent.context.search')"
              :placeholder="t('agent.context.search')"
            /><Button type="submit" :disabled="busy">{{
              t("agent.context.searchAction")
            }}</Button>
          </form>
          <div
            v-if="problems.length"
            class="max-h-40 space-y-1 overflow-y-auto"
          >
            <Button
              v-for="item in problems"
              :key="item.id"
              variant="ghost"
              class="h-auto w-full justify-start whitespace-normal py-2 text-left"
              :disabled="busy"
              @click="chooseProblem(item)"
              >#{{ item.id }} · {{ item.title }}</Button
            >
          </div>
          <p v-if="problem" class="text-sm font-medium">
            #{{ problem.id }} · {{ problem.title }}
          </p>
          <div
            v-if="submissions.length"
            class="max-h-48 space-y-1 overflow-y-auto"
          >
            <Button
              v-for="item in submissions"
              :key="item.id"
              variant="outline"
              class="h-auto w-full justify-start whitespace-normal py-3 text-left"
              :disabled="busy"
              :aria-pressed="selected?.id === item.id"
              :class="selected?.id === item.id ? 'border-primary bg-muted' : ''"
              @click="chooseSubmission(item.id)"
              >{{ item.language }} · {{ item.status }} ·
              {{
                formatDateTime(item.created_at, undefined, {
                  timeZoneName: "short",
                })
              }}
              · {{ item.id.slice(0, 8) }}</Button
            >
          </div>
          <p
            v-else-if="problem && !selected && !busy"
            class="text-sm text-muted-foreground"
          >
            {{ t("agent.context.noSubmissions") }}
          </p>
          <div v-if="problem && submissions.length" class="flex gap-2">
            <Button
              variant="ghost"
              size="sm"
              :disabled="busy || page <= 1"
              @click="loadSubmissions(page - 1)"
              >{{ t("agent.context.previous") }}</Button
            ><Button
              variant="ghost"
              size="sm"
              :disabled="busy || !hasMore"
              @click="loadSubmissions(page + 1)"
              >{{ t("agent.context.next") }}</Button
            >
          </div>
          <p v-if="selected" class="break-all text-sm">
            {{ t("agent.context.selectedSubmission") }} · {{ selected.id }}
          </p>
          <FieldLabel v-if="selected" class="flex items-center gap-2"
            ><Checkbox
              :model-value="includeProblem"
              @update:model-value="includeProblem = $event === true"
            />{{ t("agent.context.includeProblem") }}</FieldLabel
          >
        </TabsContent>
        <TabsContent value="comments" class="space-y-3">
          <p class="text-xs text-muted-foreground">
            {{ t("agent.context.commentScope") }} · {{ references.length }}/3
          </p>
          <div class="max-h-32 overflow-y-auto">
            <Button
              v-for="item in solutions"
              :key="item.id"
              variant="ghost"
              class="h-auto w-full justify-start whitespace-normal py-2 text-left"
              :disabled="busy"
              :aria-pressed="solutionId === item.id"
              @click="chooseSolution(item.id)"
              >{{ item.title }}</Button
            >
          </div>
          <p
            v-if="!busy && !solutions.length"
            class="text-sm text-muted-foreground"
          >
            {{ t("agent.context.noSolutions") }}
          </p>
          <div class="max-h-56 space-y-3 overflow-y-auto">
            <FieldLabel
              v-for="item in flattenedComments"
              :key="item.id"
              class="flex items-start gap-3 rounded-lg border p-3"
              ><Checkbox
                :model-value="checked(item.id)"
                :disabled="
                  item.content.length > 2000 ||
                  (!checked(item.id) && references.length >= 3)
                "
                @update:model-value="toggleComment(item.id, $event === true)"
              /><span
                class="min-w-0 whitespace-pre-wrap break-words text-sm font-normal"
                >{{ item.content }}</span
              ></FieldLabel
            >
          </div>
          <p
            v-if="solutionId && !busy && !comments.length"
            class="text-sm text-muted-foreground"
          >
            {{ t("agent.context.noComments") }}
          </p>
          <Button
            v-if="references.length"
            variant="ghost"
            size="sm"
            @click="references = []"
            >{{ t("agent.context.clearComments") }}</Button
          >
        </TabsContent>
        <TabsContent value="forum" class="space-y-3">
          <p class="text-xs text-muted-foreground">
            {{ t("agent.context.forumScope") }} · {{ references.length }}/3
          </p>
          <Input
            v-model="forumSearch"
            :aria-label="t('agent.context.filterPosts')"
            :placeholder="t('agent.context.filterPosts')"
          />
          <div class="max-h-32 overflow-y-auto">
            <Button
              v-for="item in filteredPosts"
              :key="item.id"
              variant="ghost"
              class="h-auto w-full justify-start whitespace-normal py-2 text-left"
              :disabled="busy"
              :aria-pressed="postId === item.id"
              @click="choosePost(item.id)"
              >{{ item.title }}</Button
            >
          </div>
          <p
            v-if="!busy && !filteredPosts.length"
            class="text-sm text-muted-foreground"
          >
            {{ t("agent.context.noPosts") }}
          </p>
          <div class="flex gap-2">
            <Button
              variant="ghost"
              size="sm"
              :disabled="busy || forumPage <= 1"
              @click="loadPosts(forumPage - 1)"
              >{{ t("agent.context.previous") }}</Button
            ><Button
              variant="ghost"
              size="sm"
              :disabled="busy || !forumHasMore"
              @click="loadPosts(forumPage + 1)"
              >{{ t("agent.context.next") }}</Button
            >
          </div>
          <div class="max-h-56 space-y-3 overflow-y-auto">
            <FieldLabel
              v-for="item in flattenedForumComments"
              :key="item.id"
              class="flex items-start gap-3 rounded-lg border p-3"
              ><Checkbox
                :model-value="checked(item.id, true)"
                :disabled="
                  item.body.length > 2000 ||
                  (!checked(item.id, true) && references.length >= 3)
                "
                @update:model-value="
                  toggleComment(item.id, $event === true, true)
                "
              /><span
                class="min-w-0 whitespace-pre-wrap break-words text-sm font-normal"
                >{{ item.body }}</span
              ></FieldLabel
            >
          </div>
          <p
            v-if="postId && !busy && !forumComments.length"
            class="text-sm text-muted-foreground"
          >
            {{ t("agent.context.noComments") }}
          </p>
          <Button
            v-if="references.length"
            variant="ghost"
            size="sm"
            @click="references = []"
            >{{ t("agent.context.clearComments") }}</Button
          >
        </TabsContent>
      </Tabs>
      <p v-if="busy" role="status" class="text-sm text-muted-foreground">
        {{ t("agent.workspace.loading") }}
      </p>
      <p v-if="error" role="alert" class="text-sm text-destructive">
        {{ error }}
      </p>
      <Button
        v-if="failedAction"
        variant="outline"
        size="sm"
        :disabled="busy"
        @click="request(failedAction)"
        >{{ t("agent.retry") }}</Button
      >
      <p class="text-xs leading-relaxed text-muted-foreground">
        {{ t("agent.context.disclosure") }}
      </p>
      <p
        v-if="hasConversation && selected && selected.id !== submissionId"
        class="text-sm"
      >
        {{ t("agent.context.newConversation") }}
      </p>
      <DialogFooter
        ><Button :disabled="!selected || busy || disabled" @click="apply">{{
          t("agent.context.apply")
        }}</Button></DialogFooter
      >
    </DialogContent>
  </Dialog>
</template>
