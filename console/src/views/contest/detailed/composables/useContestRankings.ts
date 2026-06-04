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
  let rankingIntervalId: number | null = null;

  async function loadRankings() {
    if (!contest.value) return;
    try {
      if (contest.value.status === "RUNNING") {
        rankings.value = await fetchLiveRanking(contestId.value);
        return;
      }
      const rankingRes = await fetchContestRanking(contestId.value);
      rankings.value = rankingRes.items;
    } catch {
      // Silently ignore ranking load failures; UI shows empty state
    }
  }

  // Start polling when contest is running, clean up on unmount
  watch(
    contest,
    (value) => {
      if (!value) return;
      void loadRankings();
      if (rankingIntervalId !== null) {
        clearInterval(rankingIntervalId);
        rankingIntervalId = null;
      }
      if (value.status === "RUNNING") {
        rankingIntervalId = window.setInterval(loadRankings, 30000);
      }
    },
    { immediate: true },
  );

  onUnmounted(() => {
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
    loadRankings,
    getCountryFlag,
  };
}
