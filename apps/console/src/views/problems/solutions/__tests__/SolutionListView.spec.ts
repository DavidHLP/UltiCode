import { flushPromises, shallowMount } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";
import SolutionListView from "../SolutionListView.vue";

const { fetchBestSubmission, fetchUserSolutions, fetchCurrentUserId } =
  vi.hoisted(() => ({
    fetchBestSubmission: vi.fn(),
    fetchUserSolutions: vi.fn(),
    fetchCurrentUserId: vi.fn(),
  }));

vi.mock("@/api/submission", () => ({ fetchBestSubmission }));
vi.mock("@/api/solution", () => ({ fetchUserSolutions }));
vi.mock("@/stores/auth", () => ({
  useAuthStore: () => ({ fetchCurrentUserId }),
}));
vi.mock("vue-router", () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ params: { id: "7" } }),
}));
vi.mock("vue-i18n", async (importOriginal) => {
  const actual = await importOriginal<typeof import("vue-i18n")>();
  return {
    ...actual,
    useI18n: () => ({ t: (key: string) => key }),
  };
});
vi.mock("vue-sonner", () => ({
  toast: { error: vi.fn(), warning: vi.fn() },
}));
vi.mock("@/composables/useErrorHandler", () => ({
  useErrorHandler: () => ({ handleError: vi.fn() }),
}));

describe("SolutionListView", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("does not request personal submissions for an anonymous visitor", async () => {
    fetchCurrentUserId.mockReturnValue(null);

    shallowMount(SolutionListView, {
      props: { items: [], followUp: "follow-up", sortOptions: [], problemId: 7 },
    });
    await flushPromises();

    expect(fetchBestSubmission).not.toHaveBeenCalled();
    expect(fetchUserSolutions).not.toHaveBeenCalled();
  });
});
