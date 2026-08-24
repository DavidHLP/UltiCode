import { shallowMount, flushPromises } from "@vue/test-utils";
import type { VueWrapper } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";
import SubmissionsView from "../SubmissionsView.vue";
import {
  fetchProblemSubmissions,
  fetchSubmissionStatuses,
  fetchSubmission,
} from "@/api/submission";
import { ApiError } from "@/utils/request";
import type { SubmissionRecord } from "@/types/submission";

interface RouteState {
  query: Record<string, string>;
}

const routeState = (): RouteState => (globalThis as unknown as { __route: RouteState }).__route;

vi.mock("vue-router", async () => {
  const { reactive } = await import("vue");
  const route = reactive({ query: {} }) as RouteState;
  (globalThis as unknown as { __route: RouteState }).__route = route;
  return {
    useRoute: () => route,
    useRouter: () => ({ replace: vi.fn() }),
  };
});

vi.mock("@/api/submission", () => ({
  fetchProblemSubmissions: vi.fn(),
  fetchSubmissionStatuses: vi.fn(),
  fetchSubmission: vi.fn(),
}));

vi.mock("@/api/contest", () => ({
  fetchContestProblemSubmissions: vi.fn(),
}));

vi.mock("@/composables/useSocket", () => ({
  useSocket: () => ({ onSubmissionResult: () => () => {} }),
}));

vi.mock("@/stores/auth", () => ({
  useAuthStore: () => ({
    isAuthenticated: true,
    fetchCurrentUserId: () => "user-1",
  }),
}));

vi.mock("@/hooks/problem-hooks", () => ({
  problemHooks: { emit: vi.fn().mockResolvedValue(undefined) },
}));

vi.mock("@/composables/useErrorHandler", () => ({
  useErrorHandler: () => ({ handleError: vi.fn() }),
}));

const submission = (id: string): SubmissionRecord =>
  ({
    id,
    problem_id: 1,
    status: "Accepted",
    language: "java",
    runtime: 10,
    memory: 20,
    created_at: "2026-01-01T00:00:00Z",
  }) as SubmissionRecord;

describe("SubmissionsView stale detail-response guard", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    routeState().query = {};
    vi.mocked(fetchSubmissionStatuses).mockResolvedValue([]);
    vi.mocked(fetchProblemSubmissions).mockResolvedValue([
      submission("a"),
      submission("b"),
    ]);
  });

  const mountView = async (): Promise<VueWrapper> => {
    const wrapper = shallowMount(SubmissionsView, {
      props: { problemId: 1 },
    });
    await flushPromises();
    return wrapper;
  };

  const activeDetailId = (wrapper: VueWrapper): string | null => {
    const detail = wrapper.findComponent({ name: "SubmissionsDetail" });
    return detail.exists()
      ? ((detail.props("submission") as SubmissionRecord).id ?? null)
      : null;
  };

  it.each([
    ["late 404", "reject"],
    ["late success", "resolve"],
  ])(
    "%s for selection A does not clobber newer selection B",
    async (_label, mode) => {
      let settleA!: (value?: SubmissionRecord | Error) => void;
      const detailB = submission("b");
      vi.mocked(fetchSubmission).mockImplementation((id: string) => {
        if (id === "a") {
          return new Promise<SubmissionRecord>((resolve, reject) => {
            settleA = (value) => {
              if (value instanceof Error) reject(value);
              else resolve(value as SubmissionRecord);
            };
          });
        }
        return Promise.resolve(detailB);
      });

      const wrapper = await mountView();

      // Select A from the list...
      wrapper
        .findComponent({ name: "SubmissionsListView" })
        .vm.$emit("select", submission("a"));
      await flushPromises();
      // ...then switch to B through the route while A is still in flight.
      routeState().query = { submissionId: "b" };
      await flushPromises();
      expect(activeDetailId(wrapper)).toBe("b");

      // A completes late; the guard must ignore it either way.
      settleA(mode === "reject" ? new ApiError("gone", 404) : submission("a"));
      await flushPromises();

      expect(activeDetailId(wrapper)).toBe("b");
    },
  );
});
