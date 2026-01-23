import type { InjectionKey, Ref } from "vue";
import type { ProblemDetail } from "@/types/problem-detail";
import type { ProblemRunResult } from "@/types/test-results";

export interface ProblemContext {
  problem: Ref<ProblemDetail | null>;
  runResult: Ref<ProblemRunResult | null>;
  contestId: Ref<string | null>;
}

export const ProblemContextKey: InjectionKey<ProblemContext> =
  Symbol("ProblemContext");

export const ToggleSidePanelKey: InjectionKey<() => void> =
  Symbol("ToggleSidePanel");

export const ToggleNotesKey: InjectionKey<() => void> = Symbol("ToggleNotes");
