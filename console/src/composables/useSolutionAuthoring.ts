import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useI18n } from "vue-i18n";
import { useDebounceFn } from "@vueuse/core";
import { toast } from "vue-sonner";
import type { RouteLocationNormalized } from "vue-router";
import type { Ref, ComputedRef } from "vue";
import { ApiError } from "@/utils/request";
import { useAuthStore } from "@/stores/auth";
import { fetchSolutionTopics } from "@/api/topic";
import {
  createSolution,
  fetchSolution,
  fetchUserSolutions,
  updateSolution,
} from "@/api/solution";
import { fetchProblemById } from "@/api/problem";
import { fetchBestSubmission, fetchSubmission } from "@/api/submission";
import type { SubmissionRecord, SubmissionStatusKey } from "@/types/submission";
import type { SolutionFeedItem } from "@/types/solution";
import type { SolutionTopic } from "@/types/topic";

/**
 * Solution authoring module — owns the create/edit workflow behind one seam so
 * {@link SolutionsEditView} is left with header, topic-picker popover, markdown
 * editor wiring, and the draft indicator.
 *
 * <p>Three router records ("solution-create", "solution-create-from-submission",
 * "solution-edit") point at the same component. The param-overload subtlety is
 * preserved exactly: {@code route.params.id} is the {@code problemId} in
 * create-from-problem but the {@code solutionId} in edit.
 * create-from-submission reads {@code route.query.submissionId} instead.
 *
 * <p>Concentrates: mode resolution (discriminated union replacing the inline
 * {@code route.name} string compare), three init actions owning their
 * fetch+gate+error policy, the Accepted-submission invariant as a named guard
 * ({@link requireAcceptedSubmission}), pure template builders deduplicating
 * the default vs submission-code skeletons, topic state with mode-aware
 * auto-select, draft-saved debounce, and {@link publish} centralizing
 * create/update plus the "already exists" collision recovery. The view
 * destructures the returned handles; navigation policy for the happy path, the
 * Accepted gate, and collision recovery is delegated via
 * {@link SolutionAuthoringOptions} so the composable stays free of the
 * {@code problem-detail} and {@code solution-edit} route names, while the
 * generic {@code router.back()} redirects stay internal. An unresolvable route
 * surfaces as {@link SolutionAuthoringHandle.initError} so the view can render
 * an explicit failure state instead of a silent empty editor.
 *
 * <p>Precedent: {@link useForumThread} is the same shape extracted for the
 * parallel UGC domain.
 */
export type AuthoringMode =
  | { kind: "edit"; solutionId: string }
  | { kind: "create-from-submission"; submissionId: string }
  | { kind: "create-from-problem"; problemId: string };

/**
 * Resolve the three router records into a discriminated authoring mode.
 *
 * <p>{@code route.params.id} is overloaded — it is the {@code problemId} for
 * "solution-create" but the {@code solutionId} for "solution-edit". The
 * "solution-create-from-submission" record ignores {@code params.id} and reads
 * {@code query.submissionId}. Returns {@code null} when the route does not
 * match any known authoring record.
 */
export function resolveAuthoringMode(
  route: RouteLocationNormalized,
): AuthoringMode | null {
  if (route.name === "solution-edit" && route.params.id) {
    return { kind: "edit", solutionId: String(route.params.id) };
  }
  if (
    route.name === "solution-create-from-submission" &&
    route.query.submissionId
  ) {
    return {
      kind: "create-from-submission",
      submissionId: String(route.query.submissionId),
    };
  }
  if (route.name === "solution-create" && route.params.id) {
    return { kind: "create-from-problem", problemId: String(route.params.id) };
  }
  return null;
}

export interface SolutionAuthoringOptions {
  /**
   * Fired after a successful create or update. The view owns the redirect
   * target (typically {@code problem-detail} when a slug is known, else
   * {@code router.back()}).
   */
  onPublishSuccess: (ctx: { problemSlug: string | null }) => void;
  /**
   * Fired when the Accepted-submission gate rejects an explicit submission
   * (create-from-submission with a non-Accepted submission). The view owns the
   * redirect target (typically {@code problem-detail}).
   */
  onGateFailure: (ctx: { problemSlug: string | null }) => void;
  /**
   * Fired when create-publish hits the "already exists" collision and the
   * composable resolves the user's existing solution for the problem. The view
   * owns the redirect target (typically {@code solution-edit} for the resolved
   * id) so the {@code solution-edit} route name stays out of the composable.
   */
  onCollisionRecovery: (ctx: {
    solutionId: string;
    problemSlug: string | null;
  }) => void;
}

export interface SolutionAuthoringHandle {
  // --- mode ---
  mode: AuthoringMode | null;
  isEditMode: Ref<boolean>;
  /**
   * Set when {@link init} cannot resolve the route to a known authoring mode
   * (or the mode is missing required params). The view MUST surface this
   * explicitly (banner / redirect) rather than render against empty refs.
   * {@code null} once a valid mode has begun initializing.
   */
  initError: Ref<string | null>;
  // --- editor state ---
  title: Ref<string>;
  editorContent: Ref<string>;
  dynamicTemplate: Ref<string>;
  language: Ref<string>;
  resolvedProblemId: Ref<string>;
  resolvedProblemSlug: Ref<string>;
  // --- topic state ---
  topicOptions: Ref<SolutionTopic[]>;
  selectedTopicIds: Ref<string[]>;
  selectedTopics: ComputedRef<SolutionTopic[]>;
  isLoadingTopics: Ref<boolean>;
  topicLoadError: Ref<string | null>;
  // --- draft indicator ---
  isDraftSaved: Ref<boolean>;
  draftStatus: ComputedRef<string>;
  // --- init actions (own fetch + gate + error policy) ---
  init: () => Promise<void>;
  initEdit: (solutionId: string) => Promise<void>;
  initCreateFromSubmission: (submissionId: string) => Promise<void>;
  initCreateFromProblem: (problemId: string) => Promise<void>;
  // --- Accepted-submission invariant as a named guard ---
  requireAcceptedSubmission: (
    submission: SubmissionRecord,
    options: { explicit: boolean },
  ) => boolean;
  // --- pure template builders ---
  buildDefaultTemplate: () => string;
  buildTemplateFromSubmission: (code: string, lang: string) => string;
  // --- topic + draft actions ---
  loadTopics: () => Promise<void>;
  toggleTopic: (topicId: string) => void;
  removeTopic: (topicId: string) => void;
  markDraftSaved: () => void;
  // --- publish (create/update + collision recovery) ---
  publish: () => Promise<void>;
}

const ACCEPTED: SubmissionStatusKey = "Accepted";

export function useSolutionAuthoring(
  options: SolutionAuthoringOptions,
): SolutionAuthoringHandle {
  const route = useRoute();
  const router = useRouter();
  const { t } = useI18n();

  const mode = resolveAuthoringMode(route);

  // --- Init failure (unknown route) ---
  // Surfaces an explicit user-visible error instead of rendering the editor
  // against uninitialized refs when resolveAuthoringMode returns null.
  const initError = ref<string | null>(null);

  const title = ref("");
  const editorContent = ref("");
  const dynamicTemplate = ref("");
  const language = ref<string>("java");

  const resolvedProblemId = ref<string>("");
  const resolvedProblemSlug = ref<string>("");
  const isEditMode = ref(false);
  const solutionId = ref<string>("");

  // --- Topic state ---
  const topicOptions = ref<SolutionTopic[]>([]);
  const selectedTopicIds = ref<string[]>([]);
  const selectedTopics = computed(() =>
    topicOptions.value.filter((topic) =>
      selectedTopicIds.value.includes(topic.id),
    ),
  );
  const isLoadingTopics = ref(false);
  const topicLoadError = ref<string | null>(null);

  // --- Draft indicator ---
  const isDraftSaved = ref(true);
  const draftStatus = computed(() =>
    isDraftSaved.value
      ? t("solution.editor.draftSaved")
      : t("solution.editor.editingDraft"),
  );
  const markDraftSaved = useDebounceFn(() => {
    isDraftSaved.value = true;
  }, 800);

  watch([title, editorContent, selectedTopicIds], () => {
    isDraftSaved.value = false;
    markDraftSaved();
  });

  // --- Pure template builders ---
  function buildDefaultTemplate(): string {
    return `# ${t("solution.template.approach")}

> ${t("solution.template.approachHint")}

# ${t("solution.template.solution")}

> ${t("solution.template.solutionHint")}

# ${t("solution.template.complexity")}

- ${t("solution.template.timeComplexity")}: $O(*)$
- ${t("solution.template.spaceComplexity")}: $O(*)$

# ${t("solution.template.code")}

\`\`\`java {group="solution"}
class Solution {
   public int[] twoSum(int[] nums, int target) {
       for (int i = 0; i < nums.length; i++) {
           for (int j = i + 1; j < nums.length; j++) {
               if (nums[i] + nums[j] == target) {
                   return new int[] { i, j };
               }
           }
       }
       return new int[] {};
   }
}
\`\`\`
`;
  }

  function buildTemplateFromSubmission(code: string, lang: string): string {
    return `# ${t("solution.template.approach")}

> ${t("solution.template.approachHint")}

# ${t("solution.template.solution")}

> ${t("solution.template.solutionHint")}

# ${t("solution.template.complexity")}

- ${t("solution.template.timeComplexity")}: $O(*)$
- ${t("solution.template.spaceComplexity")}: $O(*)$

# ${t("solution.template.code")}

\`\`\`${lang} {group="solution"}
${code}
\`\`\`
`;
  }

  // --- Accepted-submission invariant ---
  function requireAcceptedSubmission(
    submission: SubmissionRecord,
    opts: { explicit: boolean },
  ): boolean {
    if (submission.status === ACCEPTED) return true;
    if (opts.explicit) {
      // Only an explicit, user-selected submission (create-from-submission)
      // is hard-gated. Best-effort lookups (create-from-problem) silently
      // fall back to the default template when the best submission is not
      // Accepted — preserving the original inline semantics.
      toast.error(t("solution.messages.acceptedRequired"));
      options.onGateFailure({
        problemSlug:
          resolvedProblemSlug.value || String(submission.problem_id),
      });
    }
    return false;
  }

  // --- Helpers ---
  async function loadProblemSlug(): Promise<void> {
    if (!resolvedProblemId.value) return;
    try {
      const problem = await fetchProblemById(resolvedProblemId.value);
      resolvedProblemSlug.value = problem.slug;
    } catch (error) {
      console.error("Failed to fetch problem detail", error);
    }
  }

  // --- Init actions ---
  async function initEdit(solutionIdArg: string): Promise<void> {
    isEditMode.value = true;
    solutionId.value = solutionIdArg;
    try {
      const solution: SolutionFeedItem = await fetchSolution(solutionIdArg);
      title.value = solution.title;
      editorContent.value = solution.content ?? "";
      dynamicTemplate.value = solution.content ?? "";
      language.value = solution.language;
      if (solution.tags) {
        selectedTopicIds.value = solution.tags;
      }
      resolvedProblemId.value = solution.problem_id.toString();

      if (resolvedProblemId.value) {
        const problem = await fetchProblemById(resolvedProblemId.value);
        resolvedProblemSlug.value = problem.slug;
      }
    } catch (error) {
      console.error("Failed to load solution", error);
      toast.error(t("solution.messages.loadFailed"));
      router.back();
    }
  }

  async function initCreateFromSubmission(
    submissionId: string,
  ): Promise<void> {
    isEditMode.value = false;
    let submission: SubmissionRecord;
    try {
      submission = await fetchSubmission(submissionId);
    } catch (error) {
      console.error("Failed to fetch submission", error);
      toast.error(t("solution.messages.fetchSubmissionFailed"));
      router.back();
      return;
    }
    if (!requireAcceptedSubmission(submission, { explicit: true })) return;

    if (!resolvedProblemId.value) {
      resolvedProblemId.value = submission.problem_id.toString();
    }
    const lang = submission.language.toLowerCase();
    language.value = lang;
    const md = buildTemplateFromSubmission(submission.code ?? "", lang);
    editorContent.value = md;
    dynamicTemplate.value = md;

    await loadProblemSlug();
  }

  async function initCreateFromProblem(problemId: string): Promise<void> {
    isEditMode.value = false;
    resolvedProblemId.value = problemId;

    let template = buildDefaultTemplate();
    try {
      const best = await fetchBestSubmission(problemId);
      if (best && requireAcceptedSubmission(best, { explicit: false })) {
        const lang = best.language.toLowerCase();
        language.value = lang;
        template = buildTemplateFromSubmission(best.code ?? "", lang);
      }
    } catch {
      // Best-effort lookup — silently fall back to the default template.
    }

    editorContent.value = template;
    dynamicTemplate.value = template;

    await loadProblemSlug();
  }

  // --- Topics ---
  async function loadTopics(): Promise<void> {
    isLoadingTopics.value = true;
    topicLoadError.value = null;
    try {
      const { topics } = await fetchSolutionTopics();
      topicOptions.value = topics;
      if (!selectedTopicIds.value.length && topics.length && !isEditMode.value) {
        // Only auto-select the first topic in create mode.
        selectedTopicIds.value = [topics[0]!.id];
      }
    } catch (error) {
      console.error("Failed to load solution topics", error);
      topicLoadError.value = t("solution.messages.loadTopicsFailed");
    } finally {
      isLoadingTopics.value = false;
    }
  }

  function toggleTopic(topicId: string): void {
    if (selectedTopicIds.value.includes(topicId)) {
      selectedTopicIds.value = selectedTopicIds.value.filter(
        (item) => item !== topicId,
      );
    } else {
      selectedTopicIds.value = [...selectedTopicIds.value, topicId];
    }
  }

  function removeTopic(topicId: string): void {
    selectedTopicIds.value = selectedTopicIds.value.filter(
      (item) => item !== topicId,
    );
  }

  // --- Publish (create/update + collision recovery) ---
  async function publish(): Promise<void> {
    if (!title.value.trim()) {
      toast.error(t("solution.messages.enterTitle"));
      return;
    }
    if (!editorContent.value.trim()) {
      toast.error(t("solution.messages.enterContent"));
      return;
    }

    isDraftSaved.value = false;
    try {
      if (isEditMode.value) {
        await updateSolution(solutionId.value, {
          title: title.value,
          content: editorContent.value,
          language: language.value,
          tags: selectedTopicIds.value,
        });
        toast.success(t("solution.messages.updateSuccess"));
      } else {
        await createSolution(resolvedProblemId.value, {
          title: title.value,
          content: editorContent.value,
          language: language.value,
          tags: selectedTopicIds.value,
        });
        toast.success(t("solution.messages.publishSuccess"));
      }

      isDraftSaved.value = true;
      options.onPublishSuccess({
        problemSlug: resolvedProblemSlug.value || null,
      });
    } catch (error: unknown) {
      console.error("Failed to publish/update solution", error);
      let message = t("solution.messages.publishFailed");
      if (error instanceof ApiError) {
        message = error.message || message;
      }
      // Locale-independent substring match — the backend may localize the
      // "already exists" message, so match on the stable fragment.
      if (
        !isEditMode.value &&
        message.toLowerCase().includes("already exists") &&
        resolvedProblemId.value
      ) {
        const userId = useAuthStore().fetchCurrentUserId();
        if (userId) {
          try {
            const response = await fetchUserSolutions(
              userId,
              resolvedProblemId.value,
            );
            const existing = response.items[0];
            if (existing) {
              toast.info(t("solution.messages.alreadyExists"));
              options.onCollisionRecovery({
                solutionId: existing.id,
                problemSlug: resolvedProblemSlug.value || null,
              });
              return;
            }
          } catch (fetchError) {
            console.error("Failed to fetch existing solution", fetchError);
          }
        }
      }
      toast.error(message);
      isDraftSaved.value = true;
    }
  }

  // --- Dispatch ---
  async function init(): Promise<void> {
    if (!mode) {
      // Unknown / unresolvable route — surface an explicit failure rather than
      // rendering the editor against uninitialized (empty) refs. The view owns
      // how this is presented (banner / redirect) via initError.
      initError.value = t("solution.messages.unknownRoute");
      return;
    }
    if (mode.kind === "edit") {
      await initEdit(mode.solutionId);
    } else if (mode.kind === "create-from-submission") {
      await initCreateFromSubmission(mode.submissionId);
    } else if (mode.kind === "create-from-problem") {
      await initCreateFromProblem(mode.problemId);
    }
    await loadTopics();
  }

  return {
    mode,
    isEditMode,
    initError,
    title,
    editorContent,
    dynamicTemplate,
    language,
    resolvedProblemId,
    resolvedProblemSlug,
    topicOptions,
    selectedTopicIds,
    selectedTopics,
    isLoadingTopics,
    topicLoadError,
    isDraftSaved,
    draftStatus,
    init,
    initEdit,
    initCreateFromSubmission,
    initCreateFromProblem,
    requireAcceptedSubmission,
    buildDefaultTemplate,
    buildTemplateFromSubmission,
    loadTopics,
    toggleTopic,
    removeTopic,
    markDraftSaved,
    publish,
  };
}
