import gsap from "gsap";
import { ScrollTrigger } from "gsap/ScrollTrigger";
import Lenis from "lenis";
import * as THREE from "three";
import type { LandingSceneHandle } from "./useLandingScene";

gsap.registerPlugin(ScrollTrigger);
// Reference camera path: 9 centripetal CatmullRom control points (MainScene.js).
const CAMERA_PATH = new THREE.CatmullRomCurve3(
  [
    new THREE.Vector3(-7.799073, 0.25, 9.103358),
    new THREE.Vector3(-5.05, 0.76, 7.05),
    new THREE.Vector3(-4.313, 0.65, 3.701),
    new THREE.Vector3(-5.74, 0.65, -0.22),
    new THREE.Vector3(-3.051, 0.65, -5.277),
    new THREE.Vector3(3.224, 0.65, -5.606),
    new THREE.Vector3(6.46, 0.65, -0.22),
    new THREE.Vector3(3.034, 0.65, 5.263),
    new THREE.Vector3(-3, 0.65, 6.1),
  ],
  false,
  "centripetal",
);

/**
 * createLandingFader — drives the desert morph uniforms + camera dolly from
 * scroll progress.
 *
 * Normal mode: Lenis smooth scroll (lerp 0.2, duration 1.3, syncTouch) synced to
 * a scrubbed GSAP timeline that maps the 24-step scroll range to progress 0→1.
 *
 * Reduced-motion mode: native scroll (no Lenis, no GSAP) maps scroll position to
 * the same progress → uniforms, then renders one deterministic still per tick.
 *
 * First-version beats (full 11-sub-timeline choreography is a documented backlog
 * item): 0–0.33 desert assembles · 0.33–0.66 mirror split · 0.66–1 black-hole vortex.
 */

export interface LandingFaderOptions {
  scene: LandingSceneHandle;
  /** Tall spacer element whose height defines the scroll length (24 × 100dvh). */
  scroller: HTMLElement;
  reducedMotion: boolean;
}

export interface LandingFaderHandle {
  dispose(): void;
}

const SCROLL_STEPS = 24;
const clamp01 = (v: number) => (v < 0 ? 0 : v > 1 ? 1 : v);

export function createLandingFader(opts: LandingFaderOptions): LandingFaderHandle {
  const { scene, scroller, reducedMotion } = opts;
  const u = scene.desert.uniforms;

  // Map overall scroll progress [0,1] to the morph beats + the CatmullRom camera
  // path (reference: getPointAt arc-length sampling along the 9 control points).
  const applyProgress = (p: number) => {
    (u.uProgress.value as number) = 1 - clamp01(p / 0.33); // desert assembles
    (u.uSplitProgress.value as number) = clamp01((p - 0.33) / 0.33); // mirror split
    (u.uBlackHoleProgress.value as number) = clamp01((p - 0.66) / 0.34) * 0.35; // vortex

    scene.yaw.position.copy(CAMERA_PATH.getPointAt(clamp01(p)));
    // Hand-model cloud: appears early, collapses (uProgress 1->0) mid-scroll,
    // fades before the end. No-op until the async GLTF "hand" resolves.
    const model = scene.model;
    if (model) {
      const appear = clamp01((p - 0.1) / 0.1);
      const fadeOut = clamp01((p - 0.88) / 0.1);
      model.setActive(appear * (1 - fadeOut));
      (model.uniforms.uProgress.value as number) = 1 - clamp01((p - 0.5) / 0.22);
    }
    // Light pillar -> starfield: appears mid-scroll, morphs to stars, fades late.
    scene.light.setActive(
      clamp01((p - 0.2) / 0.1) * (1 - clamp01((p - 0.85) / 0.1)),
    );
    (scene.light.uniforms.uProgressStars.value as number) = clamp01(
      (p - 0.25) / 0.2,
    );
  };

  if (reducedMotion) {
    const onScroll = () => {
      const max = scroller.scrollHeight - window.innerHeight;
      const p = max > 0 ? clamp01(window.scrollY / max) : 0;
      applyProgress(p);
      scene.renderOnce();
    };
    window.addEventListener("scroll", onScroll, { passive: true });
    onScroll();
    return {
      dispose: () => window.removeEventListener("scroll", onScroll),
    };
  }

  const lenis = new Lenis({ lerp: 0.2, duration: 1.3, syncTouch: true });
  lenis.on("scroll", ScrollTrigger.update);
  const tickerFn = (time: number) => lenis.raf(time * 1000);
  gsap.ticker.add(tickerFn);
  gsap.ticker.lagSmoothing(0);

  const proxy = { p: 0 };
  const tl = gsap.timeline({
    defaults: { ease: "none" },
    scrollTrigger: {
      trigger: scroller,
      start: "top top",
      end: "bottom bottom",
      scrub: true,
    },
  });
  tl.to(proxy, {
    p: 1,
    duration: SCROLL_STEPS,
    onUpdate: () => applyProgress(proxy.p),
  });
  // Position the camera at the path start before the first render frame so it
  // never flashes at the rig origin while waiting for the first scrub update.
  applyProgress(0);

  return {
    dispose: () => {
      tl.scrollTrigger?.kill();
      tl.kill();
      gsap.ticker.remove(tickerFn);
      lenis.destroy();
      ScrollTrigger.getAll().forEach((s) => s.kill());
    },
  };
}
