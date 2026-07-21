import gsap from "gsap";

/**
 * createLoopController — pure, unit-testable seamless-loop state machine.
 *
 * Keeps the guard / rearm / dispose logic OUT of the fader (which is hard to
 * drive through Lenis in a test). The fader injects the side-effects
 * (getCanvas, reset) and calls `maybeTrigger(progress, direction)` from its
 * ScrollTrigger onUpdate; the internal fade-in onComplete rearms automatically.
 * Guards: one-shot (no re-entry mid-reset), forward-direction only, and a
 * `disposed` flag so a late fade callback can't touch a torn-down scene.
 */

export interface LoopControllerDeps {
  getCanvas: () => HTMLCanvasElement | null;
  /** Immediate reset side-effect (e.g. lenis.scrollTo(0) + applyProgress(0)). */
  reset: () => void;
}

export interface LoopController {
  /** Call from scroll onUpdate; triggers the fade→reset→fade when at the end. */
  maybeTrigger(progress: number, direction: number): void;
  dispose(): void;
  /** Test/inspection: whether a reset is currently in flight. */
  readonly isLooping: boolean;
}

export function createLoopController(deps: LoopControllerDeps): LoopController {
  let looping = false;
  let disposed = false;
  const fadeOutMs = 400;
  const fadeInMs = 500;

  const state = {
    maybeTrigger(progress: number, direction: number) {
      if (disposed || looping) return;
      if (progress < 0.999 || direction <= 0) return;
      const canvas = deps.getCanvas();
      if (!canvas) return;
      looping = true;
      gsap.to(canvas, {
        opacity: 0,
        duration: fadeOutMs / 1000,
        ease: "power2.out",
        onComplete: () => {
          if (disposed) return;
          deps.reset();
          gsap.to(canvas, {
            opacity: 1,
            duration: fadeInMs / 1000,
            ease: "power2.in",
            onComplete: () => {
              looping = false;
            },
          });
        },
      });
    },
    dispose() {
      disposed = true;
      const canvas = deps.getCanvas();
      if (canvas) gsap.killTweensOf(canvas);
      looping = false;
    },
    get isLooping() {
      return looping;
    },
  };
  return state;
}
