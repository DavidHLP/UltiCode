import { describe, it, expect, vi, beforeEach } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import type { RouteLocationNormalized } from "vue-router";

// --- Mocks ---------------------------------------------------------------
// Mutable route/router so each test can configure the mode without re-mocking.
// The route mock satisfies the full RouteLocationNormalized shape so it can be
// passed to resolveAuthoringMode without an `as never` (or any) cast — every
// field the type requires is stubbed here, and the tests only mutate
// name / params / query via the setRoute helper below.
const { mockRoute, mockRouter } = vi.hoisted(() => {
  const route = {
    name: "" as RouteLocationNormalized["name"],
    params: {} as RouteLocationNormalized["params"],
    query: {} as RouteLocationNormalized["query"],
    path: "/",
    fullPath: "/",
    hash: "",
    matched: [],
    meta: {},
    redirectedFrom: undefined,
  } as unknown as RouteLocationNormalized;
  const router = { push: vi.fn(), back: vi.fn() };
  return { mockRoute: route, mockRouter: router };
});

vi.mock("vue-router", () => ({
  useRoute: () => mockRoute,
  useRouter: () => mockRouter,
}));

// i18n passthrough: t(key) returns the key, so tests assert on stable keys
// rather than locale-rendered strings. Preserve the rest of vue-i18n
// (createI18n etc.) so the app's i18n module still loads.
vi.mock("vue-i18n", async (importOriginal) => {
  const actual = await importOriginal<typeof import("vue-i18n")>();
  return {
    ...actual,
    useI18n: () => ({ t: (key: string) => key }),
  };
});

vi.mock("vue-sonner", () => ({
  toast: {
    error: vi.fn(),
    success: vi.fn(),
    info: vi.fn(),
    warning: vi.fn(),
  },
}));

vi.mock("@/stores/auth", () => ({
  useAuthStore: () => ({ fetchCurrentUserId: () => "user-1" }),
}));

vi.mock("@/api/solution", () => ({
  createSolution: vi.fn(),
  fetchSolution: vi.fn(),
  fetchUserSolutions: vi.fn(),
  updateSolution: vi.fn(),
}));

vi.mock("@/api/submission", () => ({
  fetchSubmission: vi.fn(),
  fetchBestSubmission: vi.fn(),
}));

vi.mock("@/api/problem", () => ({
  fetchProblemById: vi.fn(),
}));

vi.mock("@/api/topic", () => ({
  fetchSolutionTopics: vi.fn(),
}));

// Import after mocks are registered.
import { useSolutionAuthoring, resolveAuthoringMode } from "../useSolutionAuthoring";
import { ApiError } from "@/utils/request";
import { createSolution, fetchUserSolutions } from "@/api/solution";
import { fetchSubmission } from "@/api/submission";
import { fetchSolutionTopics } from "@/api/topic";
import { toast } from "vue-sonner";

function setRoute(
  name: string,
  params: RouteLocationNormalized["params"] = {},
  query: RouteLocationNormalized["query"] = {},
): void {
  mockRoute.name = name;
  mockRoute.params = params;
  mockRoute.query = query;
}

describe("resolveAuthoringMode — param-overload subtlety", () => {
  it("treats route.params.id as solutionId in solution-edit", () => {
    setRoute("solution-edit", { id: "sol-7" });
    expect(resolveAuthoringMode(mockRoute)).toEqual({
      kind: "edit",
      solutionId: "sol-7",
    });
  });

  it("treats route.params.id as problemId in solution-create", () => {
    setRoute("solution-create", { id: "123" });
    expect(resolveAuthoringMode(mockRoute)).toEqual({
      kind: "create-from-problem",
      problemId: "123",
    });
  });

  it("reads route.query.submissionId for solution-create-from-submission", () => {
    setRoute("solution-create-from-submission", {}, { submissionId: "sub-9" });
    expect(resolveAuthoringMode(mockRoute)).toEqual({
      kind: "create-from-submission",
      submissionId: "sub-9",
    });
  });

  it("returns null for an unknown route", () => {
    setRoute("problem-detail", { slug: "two-sum" });
    expect(resolveAuthoringMode(mockRoute)).toBeNull();
  });
});

describe("useSolutionAuthoring", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
    setRoute("");
  });

  describe("Accepted-submission gate (create-from-submission)", () => {
    it("redirects via onGateFailure and toasts when the submission is non-Accepted", async () => {
      setRoute("solution-create-from-submission", {}, { submissionId: "sub-1" });
      vi.mocked(fetchSubmission).mockResolvedValue({
        id: "sub-1",
        problem_id: 42,
        status: "Wrong Answer",
        language: "java",
        code: "class Solution {}",
        runtime: 0,
        memory: 0,
        created_at: "",
      });
      vi.mocked(fetchSolutionTopics).mockResolvedValue({ topics: [] });
      const onGateFailure = vi.fn();
      const onPublishSuccess = vi.fn();

      const authoring = useSolutionAuthoring({
        onGateFailure,
        onPublishSuccess,
        onCollisionRecovery: vi.fn(),
      });
      await authoring.init();

      // The invariant fires the toast and the gate adapter with the
      // problem id as the slug fallback (slug is unknown at this point).
      expect(toast.error).toHaveBeenCalledWith(
        "solution.messages.acceptedRequired",
      );
      expect(onGateFailure).toHaveBeenCalledTimes(1);
      expect(onGateFailure).toHaveBeenCalledWith({ problemSlug: "42" });
      // Must NOT have proceeded to seed the editor from a non-Accepted submission.
      expect(authoring.editorContent.value).toBe("");
      // No publish-side success callback should fire from an init path.
      expect(onPublishSuccess).not.toHaveBeenCalled();
    });

    it("seeds the editor from an Accepted submission without firing the gate", async () => {
      setRoute("solution-create-from-submission", {}, { submissionId: "sub-ok" });
      vi.mocked(fetchSubmission).mockResolvedValue({
        id: "sub-ok",
        problem_id: 7,
        status: "Accepted",
        language: "Python",
        code: "print(1)",
        runtime: 0,
        memory: 0,
        created_at: "",
      });
      vi.mocked(fetchSolutionTopics).mockResolvedValue({ topics: [] });
      const onGateFailure = vi.fn();

      const authoring = useSolutionAuthoring({
        onGateFailure,
        onPublishSuccess: vi.fn(),
        onCollisionRecovery: vi.fn(),
      });
      await authoring.init();

      expect(onGateFailure).not.toHaveBeenCalled();
      expect(toast.error).not.toHaveBeenCalled();
      // Language is lowercased and the submission code is injected into the fence.
      expect(authoring.language.value).toBe("python");
      expect(authoring.editorContent.value).toContain("```python");
      expect(authoring.editorContent.value).toContain("print(1)");
      expect(authoring.resolvedProblemId.value).toBe("7");
    });
  });

  describe('publish "already exists" collision recovery', () => {
    it("invokes onCollisionRecovery with the resolved existing solution id", async () => {
      // Configure create-from-problem so the composable has a problemId and
      // is in create mode, then drive publish() directly. init() is avoided
      // to keep the collision assertion isolated from best-submission fetches.
      setRoute("solution-create", { id: "123" });
      vi.mocked(fetchSolutionTopics).mockResolvedValue({ topics: [] });
      vi.mocked(createSolution).mockRejectedValue(
        new ApiError("Solution already exists for this problem", 409),
      );
      vi.mocked(fetchUserSolutions).mockResolvedValue({
        items: [
          {
            id: "sol-99",
            problem_id: "123",
            title: "existing",
            summary: "",
            language: "java",
            tags: [],
            score: 0,
            publishedAt: "",
          },
        ],
        total: 1,
      });

      const onPublishSuccess = vi.fn();
      const onCollisionRecovery = vi.fn();
      const authoring = useSolutionAuthoring({
        onPublishSuccess,
        onGateFailure: vi.fn(),
        onCollisionRecovery,
      });
      // Set the create-mode state publish() reads; skip the init() fetches.
      authoring.resolvedProblemId.value = "123";
      authoring.isEditMode.value = false;
      authoring.title.value = "My solution";
      authoring.editorContent.value = "body content";

      await authoring.publish();

      // Collision recovery informs the user and delegates the redirect to the
      // adapter — the composable no longer hardcodes the solution-edit route.
      expect(toast.info).toHaveBeenCalledWith("solution.messages.alreadyExists");
      expect(onCollisionRecovery).toHaveBeenCalledTimes(1);
      expect(onCollisionRecovery).toHaveBeenCalledWith({
        solutionId: "sol-99",
        problemSlug: null,
      });
      // The composable must not navigate on its own — the view owns the route.
      expect(mockRouter.push).not.toHaveBeenCalled();
      // Happy-path adapter must NOT fire when the collision path took over.
      expect(onPublishSuccess).not.toHaveBeenCalled();
      // The generic error toast is bypassed because recovery succeeded.
      expect(toast.error).not.toHaveBeenCalled();
    });

    it("falls back to the error toast when collision is detected but no existing solution is returned", async () => {
      vi.mocked(createSolution).mockRejectedValue(
        new ApiError("already exists", 409),
      );
      vi.mocked(fetchUserSolutions).mockResolvedValue({ items: [], total: 0 });

      const onCollisionRecovery = vi.fn();
      const authoring = useSolutionAuthoring({
        onPublishSuccess: vi.fn(),
        onGateFailure: vi.fn(),
        onCollisionRecovery,
      });
      authoring.resolvedProblemId.value = "55";
      authoring.isEditMode.value = false;
      authoring.title.value = "t";
      authoring.editorContent.value = "c";

      await authoring.publish();

      expect(onCollisionRecovery).not.toHaveBeenCalled();
      expect(mockRouter.push).not.toHaveBeenCalled();
      expect(toast.error).toHaveBeenCalledWith("already exists");
    });
  });

  describe("unknown-route failure surfacing", () => {
    it("sets initError and leaves refs empty instead of silently rendering", async () => {
      // A route that does not match any authoring record must not leave the
      // view to render against uninitialized refs — init() short-circuits to
      // an explicit initError state.
      setRoute("something-else", { slug: "two-sum" });
      vi.mocked(fetchSolutionTopics).mockResolvedValue({ topics: [] });

      const authoring = useSolutionAuthoring({
        onPublishSuccess: vi.fn(),
        onGateFailure: vi.fn(),
        onCollisionRecovery: vi.fn(),
      });

      expect(authoring.initError.value).toBeNull();
      await authoring.init();

      // initError is set to the i18n key (passthrough mock returns the key).
      expect(authoring.initError.value).toBe("solution.messages.unknownRoute");
      // Editor / topic refs stay at their initial empty values — no silent
      // partial render.
      expect(authoring.title.value).toBe("");
      expect(authoring.editorContent.value).toBe("");
      expect(authoring.topicOptions.value).toEqual([]);
      // Topics must not be loaded for an unresolvable route.
      expect(fetchSolutionTopics).not.toHaveBeenCalled();
      // No navigation / toasts fire from the unknown-route path.
      expect(mockRouter.push).not.toHaveBeenCalled();
      expect(mockRouter.back).not.toHaveBeenCalled();
      expect(toast.error).not.toHaveBeenCalled();
    });
  });
});
