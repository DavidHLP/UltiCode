import { ref, watch, onUnmounted, type Ref } from "vue";
import type {
  ContestDetail,
  ContestRankingEntry,
  LiveRankingEntry,
} from "@/types/contest";
import { fetchContestRanking, fetchLiveRanking } from "@/api/contest";

export function useContestRankings(
  contestId: Ref<string>,
  contest: Ref<ContestDetail | null>,
) {
  const rankings = ref<(ContestRankingEntry | LiveRankingEntry)[]>([]);
  const page = ref(1);
  const totalPages = ref(1);
  const loading = ref(false);
  const error = ref(false);
  let generation = 0;
  let rankingIntervalId: number | null = null;

  async function loadRankings(nextPage = page.value) {
    if (
      !contest.value ||
      !Number.isInteger(nextPage) ||
      nextPage < 1 ||
      nextPage > totalPages.value
    )
      return;
    const request = ++generation;
    if (nextPage !== page.value) rankings.value = [];
    page.value = nextPage;
    loading.value = true;
    error.value = false;
    try {
      if (contest.value.status === "RUNNING") {
        const rows = await fetchLiveRanking(contestId.value);
        if (request === generation) rankings.value = rows;
        return;
      }
      const rankingRes = await fetchContestRanking(contestId.value, {
        page: nextPage,
        limit: 20,
      });
      if (request !== generation) return;
      rankings.value = rankingRes.items;
      totalPages.value = Math.max(1, rankingRes.totalPages);
    } catch {
      if (request === generation) error.value = true;
    } finally {
      if (request === generation) loading.value = false;
    }
  }

  // Start polling when contest is running, clean up on unmount
  watch(
    contest,
    (value) => {
      generation++;
      rankings.value = [];
      page.value = 1;
      totalPages.value = 1;
      error.value = false;
      loading.value = false;
      if (rankingIntervalId !== null) {
        clearInterval(rankingIntervalId);
        rankingIntervalId = null;
      }
      if (!value) return;
      void loadRankings();
      if (value.status === "RUNNING") {
        rankingIntervalId = window.setInterval(loadRankings, 30000);
      }
    },
    { immediate: true },
  );

  onUnmounted(() => {
    generation++;
    if (rankingIntervalId !== null) {
      clearInterval(rankingIntervalId);
    }
  });

  function getCountryFlag(countryCode: string): string {
    const flags: Record<string, string> = {
      CN: "\u{1F1E8}\u{1F1F3}",
      US: "\u{1F1FA}\u{1F1F8}",
      JP: "\u{1F1EF}\u{1F1F5}",
      KR: "\u{1F1F0}\u{1F1F7}",
      DE: "\u{1F1E9}\u{1F1EA}",
      UK: "\u{1F1EC}\u{1F1E7}",
      FR: "\u{1F1EB}\u{1F1F7}",
      CA: "\u{1F1E8}\u{1F1E6}",
      AU: "\u{1F1E6}\u{1F1FA}",
      SG: "\u{1F1F8}\u{1F1EC}",
    };
    return flags[countryCode] || "\u{1F310}";
  }

  return {
    rankings,
    page,
    totalPages,
    loading,
    error,
    loadRankings,
    getCountryFlag,
  };
}
