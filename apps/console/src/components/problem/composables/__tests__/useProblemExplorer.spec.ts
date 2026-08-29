/**
 * Direct Vitest unit tests for `useProblemExplorer`.
 *
 * The composable is the single owner of the ProblemExplorer behaviour the
 * template cannot test on its own: filter state, debounced request
 * scheduling, pagination, tag derivation, random-pick, and failure/empty
 * state. It has exactly one production caller (`ProblemExplorer.vue`), so
 * this suite exercises the public composable surface directly through a
 * minimal host component (needed because the composable registers an
 * `onMounted` initial load).
 *
 * Fake timers are used for the whole suite so the 300ms search debounce can
 * be asserted deterministically and no armed timer leaks across tests. The
 * `@/api/problem` boundary is mocked — no real backend is touched.
 */
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { defineComponent, h, type PropType } from "vue";
import { mount, type VueWrapper } from "@vue/test-utils";
import { CheckCircle2, FileEdit } from "lucide-vue-next";
import type { Problem } from "@/types/problem";
import type { ProblemExplorerProps } from "../../type";
import { useProblemExplorer } from "../useProblemExplorer";

// --- Mock handles ---------------------------------------------------------
// `vi.hoisted` guarantees these exist before any hoisted `vi.mock` factory
// runs, so the factories can close over them without a TDZ issue.
const mocks = vi.hoisted(() => ({
  fetchProblems: vi.fn(),
  fetchRandomProblem: vi.fn(),
  routerPush: vi.fn(),
  toastError: vi.fn(),
}));

vi.mock("@/api/problem", () => ({
  fetchProblems: mocks.fetchProblems,
  fetchRandomProblem: mocks.fetchRandomProblem,
}));

vi.mock("vue-router", () => ({
  useRouter: () => ({ push: mocks.routerPush }),
}));

vi.mock("vue-i18n", () => ({
  // Return the message key verbatim so assertions match on stable strings.
  useI18n: () => ({ t: (key: string) => key }),
}));

vi.mock("vue-sonner", () => ({
  toast: { error: mocks.toastError, warning: mocks.toastError },
}));

vi.mock("lucide-vue-next", () => ({
  CheckCircle2: { name: "CheckCircle2" },
  FileEdit: { name: "FileEdit" },
  CircleDot: { name: "CircleDot" },
  LayoutGrid: { name: "LayoutGrid" },
  Code2: { name: "Code2" },
  Database: { name: "Database" },
  Terminal: { name: "Terminal" },
  Cpu: { name: "Cpu" },
}));

// --- Host component -------------------------------------------------------
// `useProblemExplorer` calls `onMounted`, so it must run inside a component
// setup phase. The host exposes the composable return for direct driving.
type Explorer = ReturnType<typeof useProblemExplorer>;
interface HostVm {
  explorer: Explorer;
}

const Host = defineComponent({
  props: {
    problems: { type: Array as PropType<Problem[]>, default: undefined },
    initialCategory: { type: String, default: undefined },
    editable: { type: Boolean, default: undefined },
  },
  setup(props) {
    const explorer = useProblemExplorer(props);
    return { explorer };
  },
  render: () => h("div", { "data-testid": "host" }),
});

function explorerOf(wrapper: VueWrapper): Explorer {
  return (wrapper.vm as unknown as HostVm).explorer;
}

// --- Fixtures -------------------------------------------------------------
const emptyPage = {
  items: [] as Problem[],
  total: 0,
  page: 1,
  pageSize: 50,
  totalPages: 1,
};

function page(items: Problem[], totalPages = 1, total = items.length) {
  return { items, total, page: 1, pageSize: 50, totalPages };
}

let idCounter = 0;
function makeProblem(
  overrides: Partial<Problem> & Pick<Problem, "title" | "slug">,
): Problem {
  idCounter += 1;
  return {
    id: overrides.id ?? idCounter,
    title: overrides.title,
    slug: overrides.slug,
    difficulty: overrides.difficulty ?? "EASY",
    acceptance_rate: overrides.acceptance_rate ?? 50,
    tags: overrides.tags ?? [],
    status: overrides.status,
    isPremium: overrides.isPremium,
    hasSolution: overrides.hasSolution,
  };
}

/**
 * Flush microtasks (Vue watcher jobs + promise resolutions from the mocked
 * API) without advancing the 300ms search debounce timer.
 */
function settle(): Promise<void> {
  return vi.advanceTimersByTimeAsync(0);
}

async function mountExplorer(props: Partial<ProblemExplorerProps> = {}) {
  const wrapper = mount(Host, { props });
  await settle(); // let the onMounted initial fetch resolve
  return wrapper;
}

beforeEach(() => {
  vi.useFakeTimers();
  mocks.fetchProblems.mockReset();
  mocks.fetchProblems.mockResolvedValue(emptyPage);
  mocks.fetchRandomProblem.mockReset();
  mocks.routerPush.mockReset();
  mocks.toastError.mockReset();
  idCounter = 0;
});

afterEach(() => {
  vi.clearAllTimers();
  vi.useRealTimers();
});

describe("useProblemExplorer", () => {
  describe("initial load", () => {
    it("fetches the first page on mount with the default filter set", async () => {
      const items = [
        makeProblem({ title: "Two Sum", slug: "two-sum", difficulty: "EASY" }),
      ];
      mocks.fetchProblems.mockResolvedValue(page(items, 3, 120));

      const wrapper = await mountExplorer();
      const ex = explorerOf(wrapper);

      expect(mocks.fetchProblems).toHaveBeenCalledTimes(1);
      const [filters, pageNumber, pageSize] =
        mocks.fetchProblems.mock.calls[0];
      expect(filters).toEqual({
        category: "all",
        search: undefined,
        difficulty: undefined,
        status: undefined,
        tag: undefined,
        isPremium: undefined,
      });
      expect(pageNumber).toBe(1);
      expect(pageSize).toBe(50);

      expect(ex.displayedProblems.value.map((p) => p.slug)).toEqual([
        "two-sum",
      ]);
      expect(ex.hasMore.value).toBe(true);
    });

    it("initializes selectedCategory from initialCategory", async () => {
      const wrapper = await mountExplorer({ initialCategory: "algorithms" });
      expect(explorerOf(wrapper).selectedCategory.value).toBe("algorithms");
    });
  });

  describe("Candidate 04 deepening", () => {
    it("does not fetch when props.problems is supplied (supplied mode)", async () => {
      const wrapper = await mountExplorer({
        problems: [makeProblem({ title: "A", slug: "a" })],
      });
      const ex = explorerOf(wrapper);

      // onMounted, watchers, and clearFilters all stay no-op in supplied mode
      expect(mocks.fetchProblems).not.toHaveBeenCalled();
      ex.toggleDifficulty("EASY", true);
      await settle();
      ex.clearFilters();
      await settle();
      expect(mocks.fetchProblems).not.toHaveBeenCalled();
      // the supplied list is the source, not a fetched fallback
      expect(ex.displayedProblems.value.map((p) => p.slug)).toEqual(["a"]);
    });

    it("discards a stale response when a newer filter supersedes it", async () => {
      // The onMounted fetch never resolves (simulating a slow/abandoned
      // request). A later filter toggle must supersede it via the request
      // token, and only the latest EASY-filtered response may land.
      const never = new Promise<never>(() => {});
      mocks.fetchProblems.mockReturnValueOnce(never);
      mocks.fetchProblems.mockResolvedValueOnce(
        page([makeProblem({ title: "Latest", slug: "latest" })]),
      );

      const wrapper = await mountExplorer();
      const ex = explorerOf(wrapper);

      ex.toggleDifficulty("EASY", true);
      await settle();

      // stale onMounted response (pending forever) did not land; latest wins
      expect(ex.displayedProblems.value.map((p) => p.slug)).toEqual(["latest"]);
      expect([...ex.selectedDifficulty.value]).toEqual(["EASY"]);
    });

    it("leaves enrichedProblems.statusIcon undefined (icons owned by displayedProblems)", async () => {
      mocks.fetchProblems.mockResolvedValue(
        page([
          makeProblem({ title: "Solved", slug: "s", status: "solved" }),
          makeProblem({ title: "Attempt", slug: "a", status: "attempted" }),
        ]),
      );
      const wrapper = await mountExplorer();
      const ex = explorerOf(wrapper);

      // enrichedProblems no longer computes the dead CircleDot icon
      expect(ex.enrichedProblems.value.map((p) => p.statusIcon)).toEqual([
        undefined,
        undefined,
      ]);
      // displayedProblems still owns the rendered icons
      expect(ex.displayedProblems.value.map((p) => p.statusIcon)).toEqual([
        CheckCircle2,
        FileEdit,
      ]);
    });
  });

  describe("filter state", () => {
    it("toggles status / difficulty / premium and updates the active-filter computeds", async () => {
      const wrapper = await mountExplorer();
      const ex = explorerOf(wrapper);

      expect(ex.activeFilterCount.value).toBe(0);
      expect(ex.hasActiveFilters.value).toBe(false);

      ex.toggleStatus("solved", true);
      ex.toggleDifficulty("EASY", true);
      ex.toggleDifficulty("MEDIUM", true);
      ex.togglePremium(true, true);

      expect([...ex.selectedStatus.value]).toEqual(["solved"]);
      expect([...ex.selectedDifficulty.value]).toEqual(["EASY", "MEDIUM"]);
      expect(ex.showPremium.value).toBe(true);
      // 1 status + 2 difficulty + 1 premium
      expect(ex.activeFilterCount.value).toBe(4);
      expect(ex.hasActiveFilters.value).toBe(true);

      // removing one difficulty item only removes that item
      ex.toggleDifficulty("EASY", false);
      expect([...ex.selectedDifficulty.value]).toEqual(["MEDIUM"]);

      // duplicate toggle-on is idempotent
      ex.toggleStatus("solved", true);
      expect([...ex.selectedStatus.value]).toEqual(["solved"]);

      // unchecking a non-matching premium value is a no-op
      ex.togglePremium(false, false);
      expect(ex.showPremium.value).toBe(true);

      // unchecking the matching premium value clears it
      ex.togglePremium(true, false);
      expect(ex.showPremium.value).toBeNull();

      // toggle off the last status
      ex.toggleStatus("solved", false);
      expect([...ex.selectedStatus.value]).toEqual([]);
      expect(ex.activeFilterCount.value).toBe(1);
      await settle();
    });

    it("setSelectedCategory / setSelectedTags / setSearchQuery set the underlying refs", async () => {
      const wrapper = await mountExplorer();
      const ex = explorerOf(wrapper);

      ex.setSelectedCategory("algorithms");
      ex.setSelectedTags(["dp", "graph"]);
      ex.setSearchQuery("sum");
      await settle();

      expect(ex.selectedCategory.value).toBe("algorithms");
      expect([...ex.selectedTags.value]).toEqual(["dp", "graph"]);
      expect(ex.searchQuery.value).toBe("sum");
    });

    it("clearFilters resets every filter and triggers a reload", async () => {
      const wrapper = await mountExplorer();
      const ex = explorerOf(wrapper);

      ex.setSelectedTags(["dp"]);
      ex.toggleStatus("solved", true);
      ex.toggleDifficulty("EASY", true);
      ex.togglePremium(true, true);
      ex.setSearchQuery("query");
      await settle();
      expect(ex.hasActiveFilters.value).toBe(true);

      mocks.fetchProblems.mockClear();
      ex.clearFilters();
      await settle();

      expect(ex.searchQuery.value).toBe("");
      expect([...ex.selectedTags.value]).toEqual([]);
      expect([...ex.selectedStatus.value]).toEqual([]);
      expect([...ex.selectedDifficulty.value]).toEqual([]);
      expect(ex.showPremium.value).toBeNull();
      expect(ex.hasActiveFilters.value).toBe(false);
      expect(ex.activeFilterCount.value).toBe(0);
      expect(mocks.fetchProblems).toHaveBeenCalled();
    });
  });

  describe("request scheduling", () => {
    it("debounces searchQuery by 300ms and collapses rapid changes into one fetch", async () => {
      const wrapper = await mountExplorer();
      mocks.fetchProblems.mockClear();
      const ex = explorerOf(wrapper);

      ex.setSearchQuery("two");
      ex.setSearchQuery("two-s");
      ex.setSearchQuery("two-sum");
      await settle();

      // Debounced — the API is not hit before the 300ms window elapses.
      expect(mocks.fetchProblems).not.toHaveBeenCalled();

      // 1ms short of the window: still no fetch.
      await vi.advanceTimersByTimeAsync(299);
      expect(mocks.fetchProblems).not.toHaveBeenCalled();

      // At 300ms exactly one fetch fires with the latest query — Vue's
      // watcher batching plus the composable's clearTimeout collapse the
      // three writes into a single request.
      await vi.advanceTimersByTimeAsync(1);
      expect(mocks.fetchProblems).toHaveBeenCalledTimes(1);
      expect(mocks.fetchProblems.mock.calls[0][0]).toMatchObject({
        search: "two-sum",
      });
    });

    it("triggers an immediate fetch when a non-search filter changes (no debounce)", async () => {
      const wrapper = await mountExplorer();
      mocks.fetchProblems.mockClear();
      const ex = explorerOf(wrapper);

      ex.toggleDifficulty("EASY", true);

      // Non-search filters are not debounced: a microtask flush is enough
      // for the request to fire (contrast with the 300ms search debounce).
      await settle();
      expect(mocks.fetchProblems).toHaveBeenCalledTimes(1);
      expect(mocks.fetchProblems.mock.calls[0][0]).toMatchObject({
        difficulty: "EASY",
      });
    });
  });

  describe("pagination (loadMore)", () => {
    it("appends the next page and preserves existing items", async () => {
      mocks.fetchProblems.mockResolvedValueOnce(
        page([makeProblem({ title: "A", slug: "a" })], 2, 2),
      );
      mocks.fetchProblems.mockResolvedValueOnce(
        page([makeProblem({ title: "B", slug: "b" })], 2, 2),
      );
      const wrapper = await mountExplorer();
      const ex = explorerOf(wrapper);

      expect(ex.hasMore.value).toBe(true);
      mocks.fetchProblems.mockClear();

      ex.loadMore();
      await settle();

      expect(mocks.fetchProblems).toHaveBeenCalledTimes(1);
      expect(mocks.fetchProblems.mock.calls[0][1]).toBe(2);
      expect(ex.displayedProblems.value.map((p) => p.slug)).toEqual([
        "a",
        "b",
      ]);
      expect(ex.hasMore.value).toBe(false);
    });

    it("is a no-op when there are no more pages", async () => {
      mocks.fetchProblems.mockResolvedValueOnce(
        page([makeProblem({ title: "A", slug: "a" })], 1, 1),
      );
      const wrapper = await mountExplorer();
      const ex = explorerOf(wrapper);
      expect(ex.hasMore.value).toBe(false);

      mocks.fetchProblems.mockClear();
      ex.loadMore();
      await settle();

      expect(mocks.fetchProblems).not.toHaveBeenCalled();
    });
  });

  describe("tag derivation", () => {
    it("ranks popularTags by frequency (top 6, ties by locale sort) and lists the rest in otherTags", async () => {
      const problems = [
        makeProblem({ title: "A", slug: "a", tags: ["t1", "t2", "t3"] }),
        makeProblem({ title: "B", slug: "b", tags: ["t1", "t4", "t5"] }),
        makeProblem({ title: "C", slug: "c", tags: ["t1", "t2", "t6"] }),
        makeProblem({
          title: "D",
          slug: "d",
          tags: ["t1", "t2", "t3", "t7"],
        }),
        makeProblem({ title: "E", slug: "e", tags: ["t1", "t8"] }),
      ];
      const wrapper = await mountExplorer({ problems });
      const ex = explorerOf(wrapper);

      // counts: t1=5, t2=3, t3=3, t4..t8=1 (tie -> ascending locale sort)
      expect(ex.popularTags.value).toEqual([
        "t1",
        "t2",
        "t3",
        "t4",
        "t5",
        "t6",
      ]);
      expect(ex.otherTags.value).toEqual(["t7", "t8"]);
    });
  });

  describe("random pick (pickOne)", () => {
    it("navigates to a displayed problem's slug without hitting fetchRandomProblem", async () => {
      mocks.fetchProblems.mockResolvedValueOnce(
        page(
          [
            makeProblem({ title: "A", slug: "slug-a" }),
            makeProblem({ title: "B", slug: "slug-b" }),
          ],
          1,
          2,
        ),
      );
      const wrapper = await mountExplorer();
      const ex = explorerOf(wrapper);
      mocks.routerPush.mockClear();
      mocks.fetchRandomProblem.mockReset();

      await ex.pickOne();
      await settle();

      expect(mocks.routerPush).toHaveBeenCalledTimes(1);
      expect(["/problems/slug-a", "/problems/slug-b"]).toContain(
        mocks.routerPush.mock.calls[0][0],
      );
      expect(mocks.fetchRandomProblem).not.toHaveBeenCalled();
    });

    it("falls back to fetchRandomProblem when nothing is displayed and navigates on success", async () => {
      const wrapper = await mountExplorer();
      const ex = explorerOf(wrapper);
      mocks.fetchRandomProblem.mockResolvedValue(
        makeProblem({ title: "R", slug: "slug-r" }),
      );
      mocks.routerPush.mockClear();

      await ex.pickOne();
      await settle();

      expect(mocks.fetchRandomProblem).toHaveBeenCalledTimes(1);
      expect(mocks.routerPush).toHaveBeenCalledWith("/problems/slug-r");
    });

    it("surfaces an error toast and does not navigate when fetchRandomProblem rejects", async () => {
      const wrapper = await mountExplorer();
      const ex = explorerOf(wrapper);
      mocks.fetchRandomProblem.mockRejectedValue(new Error("boom"));
      mocks.routerPush.mockClear();

      await ex.pickOne();
      await settle();

      expect(mocks.fetchRandomProblem).toHaveBeenCalledTimes(1);
      expect(mocks.routerPush).not.toHaveBeenCalled();
      expect(mocks.toastError).toHaveBeenCalledTimes(1);
      expect(mocks.toastError.mock.calls[0][0]).toBe(
        "problem.explorer.failedToPickRandom",
      );
    });
  });

  describe("failure / empty state", () => {
    it("surfaces an error toast and an empty result when the initial fetch fails", async () => {
      mocks.fetchProblems.mockReset();
      mocks.fetchProblems.mockRejectedValue(new Error("network down"));

      const wrapper = await mountExplorer();
      const ex = explorerOf(wrapper);

      expect(mocks.toastError).toHaveBeenCalledTimes(1);
      expect(mocks.toastError.mock.calls[0][0]).toBe(
        "problem.explorer.failedToLoad",
      );
      expect(ex.displayedProblems.value).toEqual([]);
      expect(ex.hasMore.value).toBe(false);
    });
  });

  describe("props.problems source override", () => {
    it("uses props.problems as the source and maps statusIcon per status for display", async () => {
      const problems = [
        makeProblem({ title: "S", slug: "s", status: "solved" }),
        makeProblem({ title: "A", slug: "a", status: "attempted" }),
        makeProblem({ title: "T", slug: "t", status: "todo" }),
      ];
      const wrapper = await mountExplorer({ problems });
      const ex = explorerOf(wrapper);

      const bySlug = Object.fromEntries(
        ex.displayedProblems.value.map((p) => [p.slug, p]),
      );
      // displayedProblems re-derives icons: solved -> CheckCircle2,
      // attempted -> FileEdit, todo -> none.
      expect(bySlug.s?.statusIcon).toBe(CheckCircle2);
      expect(bySlug.a?.statusIcon).toBe(FileEdit);
      expect(bySlug.t?.statusIcon).toBeUndefined();
    });
  });

  describe("columns", () => {
    it("exposes 4 columns by default and adds an actions column when editable", async () => {
      const wrapper = await mountExplorer();
      expect(explorerOf(wrapper).columns.value.map((c) => c.key)).toEqual([
        "status",
        "title",
        "acceptance",
        "difficulty",
      ]);

      const editableWrapper = await mountExplorer({ editable: true });
      expect(explorerOf(editableWrapper).columns.value.map((c) => c.key)).toEqual([
        "status",
        "title",
        "acceptance",
        "difficulty",
        "actions",
      ]);
    });
  });

  describe("categoryOptions", () => {
    it("mirrors the configured PROBLEM_CATEGORIES values", async () => {
      const wrapper = await mountExplorer();
      const ex = explorerOf(wrapper);
      expect(ex.categoryOptions.value.map((o) => o.value)).toEqual([
        "all",
        "algorithms",
        "database",
        "shell",
        "concurrency",
      ]);
    });
  });
});
