import type { LucaState } from "@/composables/landing/useLucaStage";

// The nine narrative beats in scroll order. `align` mirrors the type column
// to one side so the centered polyhedron reads against the copy (alternating
// rhythm). The eyebrow / title / subline copy is resolved from i18n by the
// view via `landingLuca.beats.<state>.*`.
export interface LucaBeatConfig {
  state: LucaState;
  align: "left" | "right";
}

export const LUCA_BEATS: readonly LucaBeatConfig[] = [
  { state: "squashed", align: "right" },
  { state: "cracked", align: "left" },
  { state: "snapped", align: "right" },
  { state: "axed", align: "left" },
  { state: "opened", align: "right" },
  { state: "quarteted", align: "left" },
  { state: "timed", align: "right" },
  { state: "still", align: "left" },
  { state: "broken", align: "right" },
] as const;

export const LUCA_BEAT_TOTAL = LUCA_BEATS.length;
