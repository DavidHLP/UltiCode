import {
  inject,
  onBeforeUnmount,
  onMounted,
  provide,
  ref,
  type InjectionKey,
  type Ref,
} from "vue";
import gsap from "gsap";
import { ScrollTrigger } from "gsap/ScrollTrigger";

// Static import is safe under vite.config.ts `resolve.dedupe` (single vue copy).

// The landing's 3D device is a visual translation of the copy on the same
// screen. This composable is the single source of truth that binds them: it
// watches the nine narrative sections scroll past and publishes which "state"
// the polyhedron is in, that state's local 0→1 scrub progress, which pillar
// fragment the pointer is hovering (anatomy), and a command channel the
// section-09 CTAs use to ask the scene to play the reverse-harmony or
// explode-to-signup sequence.
//
// Each section owns a ScrollTrigger whose `self.progress` IS the local scrub
// (the section is pinned for one viewport of scroll). No hardcoded timeline
// metrics anywhere; the scene reads `state` + `progress` and plays the
// matching choreography.

/** The nine literal polyhedron states — one per narrative beat. */
export type LucaState =
  | "squashed"
  | "cracked"
  | "snapped"
  | "axed"
  | "opened"
  | "quarteted"
  | "timed"
  | "still"
  | "broken";

/** Section-09 CTA commands dispatched to the 3D scene. */
export type LucaCommandKind = "reverse" | "explode";

export interface LucaCommand {
  kind: LucaCommandKind;
  /** Monotonic id so the scene can react to repeated identical commands. */
  id: number;
}

export interface LucaStage {
  state: Ref<LucaState>;
  progress: Ref<number>;
  activeFragment: Ref<string | null>;
  command: Ref<LucaCommand | null>;
  setFragment: (key: string | null) => void;
  requestReverse: () => void;
  requestExplode: () => void;
}

// `symbol` + typed marker so consumers get the Stage shape, never `unknown`.
export const LUCA_STAGE_KEY: InjectionKey<LucaStage> = Symbol("luca-stage");

// Section class → the polyhedron state it represents. Order is the scroll
// order; it is also the 01/09 counter index.
interface SectionMap {
  cls: string;
  state: LucaState;
}

export const SECTION_MAP: ReadonlyArray<SectionMap> = [
  { cls: "luca-beat-squashed", state: "squashed" },
  { cls: "luca-beat-cracked", state: "cracked" },
  { cls: "luca-beat-snapped", state: "snapped" },
  { cls: "luca-beat-axed", state: "axed" },
  { cls: "luca-beat-opened", state: "opened" },
  { cls: "luca-beat-quarteted", state: "quarteted" },
  { cls: "luca-beat-timed", state: "timed" },
  { cls: "luca-beat-still", state: "still" },
  { cls: "luca-beat-broken", state: "broken" },
];

const prefersReducedMotion = (): boolean =>
  typeof window !== "undefined" &&
  typeof window.matchMedia === "function" &&
  window.matchMedia("(prefers-reduced-motion: reduce)").matches;

const isMobile = (): boolean =>
  typeof window !== "undefined" &&
  typeof window.matchMedia === "function" &&
  window.matchMedia("(max-width: 768px)").matches;

/**
 * Owns the stage bus. Call once in the landing root; the returned object is
 * provided to the 3D scene and the sections via `LUCA_STAGE_KEY`. Children
 * read it with {@link useLucaStageConsumer}.
 */
export function useLucaStage(root: Ref<HTMLElement | null>): LucaStage {
  const state = ref<LucaState>("squashed");
  const progress = ref(0);
  const activeFragment = ref<string | null>(null);
  const command = ref<LucaCommand | null>(null);

  let commandId = 0;
  let triggers: ScrollTrigger[] = [];

  const setFragment = (key: string | null) => {
    activeFragment.value = key;
  };
  const dispatch = (kind: LucaCommandKind) => {
    commandId += 1;
    command.value = { kind, id: commandId };
  };
  const requestReverse = () => dispatch("reverse");
  const requestExplode = () => dispatch("explode");

  onMounted(() => {
    if (typeof window === "undefined") return;
    if (typeof window.matchMedia !== "function") return;
    if (prefersReducedMotion() || isMobile()) return;
    const host = root.value;
    if (!host) return;

    // registerPlugin inside the guard: ScrollTrigger.register eagerly calls
    // matchMedia, which jsdom lacks — a top-level register throws at import.
    gsap.registerPlugin(ScrollTrigger);

    triggers = SECTION_MAP.map((entry) => {
      const el = host.querySelector<HTMLElement>(`.${entry.cls}`);
      if (!el) return null as unknown as ScrollTrigger;

      const apply = () => {
        state.value = entry.state;
      };

      // Pin each beat for one viewport of scroll so its local 0→1 progress
      // IS the scrub the scene plays against (text holds while the polyhedron
      // mutates). onEnter/onEnterBack keep the state label correct both ways.
      return ScrollTrigger.create({
        trigger: el,
        start: "top top",
        end: "+=100%",
        pin: true,
        pinSpacing: true,
        scrub: true,
        invalidateOnRefresh: true,
        onEnter: apply,
        onEnterBack: apply,
        onUpdate: (self) => {
          progress.value = self.progress;
        },
      });
    }).filter(Boolean);

    requestAnimationFrame(() => ScrollTrigger.refresh());
  });

  onBeforeUnmount(() => {
    triggers.forEach((t) => t.kill());
    triggers = [];
  });

  const stage: LucaStage = {
    state,
    progress,
    activeFragment,
    command,
    setFragment,
    requestReverse,
    requestExplode,
  };
  provide(LUCA_STAGE_KEY, stage);
  return stage;
}

/**
 * Inject the stage bus from a landing descendant. Returns a no-op stage when
 * mounted outside the landing (tests / storybook) so consumers never guard.
 */
export function useLucaStageConsumer(): LucaStage {
  const fallback: LucaStage = {
    state: ref<LucaState>("squashed"),
    progress: ref(0),
    activeFragment: ref<string | null>(null),
    command: ref<LucaCommand | null>(null),
    setFragment: () => {},
    requestReverse: () => {},
    requestExplode: () => {},
  };
  return inject(LUCA_STAGE_KEY, fallback);
}
