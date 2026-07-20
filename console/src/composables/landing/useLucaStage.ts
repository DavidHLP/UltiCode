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
  /** Published after the scene has finished playing a dispatched command's animation. */
  completedCommand: Ref<LucaCommand | null>;
  setFragment: (key: string | null) => void;
  requestReverse: () => void;
  requestExplode: () => void;
  /** Report that the scene has finished the animation for a given command id. */
  reportCommandCompleted: (id: number) => void;
  /**
   * Beat-facing intent: dispatches "explode", receives scene completion, then
   * performs the auth-aware navigation to register or forum-home. Keeps the
   * navigate-transition logic in the stage so beat watchers only observe
   * completedCommand and never call the router directly.
   */
  requestFutureTransition: () => void;
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
 *
 * @param root          - ref to the landing root element
 * @param options       - optional actions injected by the landing view
 * @param options.onFutureTransitionComplete  - called by the stage when the
 *              scene has finished the explode animation, so the landing view can
 *              perform the auth-aware router push. Keeps router/auth out of this
 *              module while ensuring exactly-once delivery per explode id.
 */
export interface UseLucaStageOptions {
  onFutureTransitionComplete?: () => void;
}
export function useLucaStage(
  root: Ref<HTMLElement | null>,
  options: UseLucaStageOptions = {},
): LucaStage {
  const state = ref<LucaState>("squashed");
  const progress = ref(0);
  const activeFragment = ref<string | null>(null);
  const command = ref<LucaCommand | null>(null);
  const completedCommand = ref<LucaCommand | null>(null);
  // One-shot channel: each dispatched command lives here until the scene
  // reports it has finished playing its animation. Driven by a Map so we
  // publish the original command (kind + id) on completion.
  const pendingCommands = new Map<number, LucaCommand>();
  let pendingTransitionId: number | null = null;
  let commandId = 0;
  let triggers: ScrollTrigger[] = [];
  const setFragment = (key: string | null) => {
    activeFragment.value = key;
  };
  // Reserve id and store in pending map; do NOT publish yet so callers can set
  // transition state before command.value becomes observable to the scene.
  const createCommand = (kind: LucaCommandKind): LucaCommand => {
    commandId += 1;
    const cmd: LucaCommand = { kind, id: commandId };
    pendingCommands.set(cmd.id, cmd);
    return cmd;
  };
  // Publish a previously created command to the reactive command.value channel.
  const publishCommand = (cmd: LucaCommand) => {
    command.value = cmd;
  };
  const requestReverse = () => {
    publishCommand(createCommand("reverse"));
  };
  const requestExplode = () => {
    publishCommand(createCommand("explode"));
  };

  const requestFutureTransition = () => {
    const cmd = createCommand("explode"); // reserve id, add to pending map
    pendingTransitionId = cmd.id;        // mark before command.value is published
    publishCommand(cmd);                 // now publish so scene reacts
  };

  /**
   * Publish that the scene has finished the animation for a given command id.
   * Fires `completedCommand.value` exactly once per id; subsequent calls with
   * the same id are no-ops. If this id matches `pendingTransitionId` (the
   * explode that should drive navigation), also calls the injected callback so
   * the landing view can perform the auth-aware router push.
   *
   * Terminal unavailable paths (WebGL unavailable, reduced-motion) in LucaScene
   * call this synchronously so navigation fires without waiting for a tween.
   */
  const reportCommandCompleted = (id: number) => {
    const cmd = pendingCommands.get(id);
    if (!cmd) return;
    pendingCommands.delete(id);
    completedCommand.value = cmd;
    if (id === pendingTransitionId) {
      pendingTransitionId = null;
      options.onFutureTransitionComplete?.();
    }
  };

  onMounted(() => {
    if (typeof window === "undefined") return;
    if (typeof window.matchMedia !== "function") return;
    if (prefersReducedMotion() || isMobile()) return;
    const host = root.value;
    if (!host) return;

    gsap.registerPlugin(ScrollTrigger);

    triggers = SECTION_MAP.map((entry) => {
      const el = host.querySelector<HTMLElement>(`.${entry.cls}`);
      if (!el) return null as unknown as ScrollTrigger;

      const apply = () => {
        state.value = entry.state;
      };

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
    completedCommand,
    setFragment,
    requestReverse,
    requestExplode,
    requestFutureTransition,
    reportCommandCompleted,
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
    completedCommand: ref<LucaCommand | null>(null),
    setFragment: () => {},
    requestReverse: () => {},
    requestExplode: () => {},
    reportCommandCompleted: () => {},
    requestFutureTransition: () => {},
  };
  return inject(LUCA_STAGE_KEY, fallback);
}
