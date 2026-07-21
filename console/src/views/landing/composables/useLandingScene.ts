import * as THREE from "three";
import { EffectComposer } from "three/examples/jsm/postprocessing/EffectComposer.js";
import { RenderPass } from "three/examples/jsm/postprocessing/RenderPass.js";
import { UnrealBloomPass } from "three/examples/jsm/postprocessing/UnrealBloomPass.js";
import { OutputPass } from "three/examples/jsm/postprocessing/OutputPass.js";
import { ParticlesDesert } from "../scene/ParticlesDesert";
import { CustomFog } from "../scene/CustomFog";
import { GLTFLoader } from "three/examples/jsm/loaders/GLTFLoader.js";
import { DRACOLoader } from "three/examples/jsm/loaders/DRACOLoader.js";
import { ParticlesModel } from "../scene/ParticlesModel";
import { ParticlesLight } from "../scene/ParticlesLight";

/**
 * createLandingScene — owns the renderer, the yaw→pitch camera rig with mouse
 * parallax, the Bloom + Reinhard post chain, and the desert/fog systems.
 *
 * Reduced-motion is a first-class branch, not an afterthought: when the media
 * query matches, NO requestAnimationFrame loop starts; instead `renderOnce()`
 * renders a deterministic still (uTime frozen at FROZEN_TIME, seeded particle
 * composition) so each scroll position shows a designed, stable frame with no
 * animation. The fader drives the morph uniforms from scroll progress in both
 * modes.
 */

export interface LandingSceneOptions {
  reducedMotion: boolean;
  isDesktop: boolean;
}

export interface LandingSceneHandle {
  readonly desert: ParticlesDesert;
  readonly fog: CustomFog;
  readonly light: ParticlesLight;
  readonly camera: THREE.PerspectiveCamera;
  readonly yaw: THREE.Object3D;
  readonly pitch: THREE.Object3D;
  /** Hand-model particle cloud; null until the async GLTF "hand" mesh resolves. */
  readonly model: ParticlesModel | null;
  /** Render a single frame (used by the reduced-motion path per scroll tick). */
  renderOnce(): void;
  dispose(): void;
}

// Fixed phase so reduced-motion stills are identical on every visit.
const FROZEN_TIME = 1.0;
const PARALLAX_X = 0.08;
const PARALLAX_Y = 0.12;
const MOUSE_LERP = 0.15;

export function createLandingScene(
  canvas: HTMLCanvasElement,
  opts: LandingSceneOptions,
): LandingSceneHandle {
  const { reducedMotion } = opts;
  const mouse = new THREE.Vector2(9999, 9999);

  const renderer = new THREE.WebGLRenderer({
    canvas,
    antialias: true,
    alpha: true,
    powerPreference: "high-performance",
  });
  // Transparent canvas (alpha 0): the #151515 container shows through, matching
  // the reference (setClearColor(0x101010, 0) over a #151515 body).
  renderer.setClearColor(0x101010, 0);
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  renderer.toneMapping = THREE.ReinhardToneMapping;
  renderer.toneMappingExposure = 1.0;
  const dpr = Math.min(window.devicePixelRatio || 1, 1.25);
  renderer.setPixelRatio(dpr);
  renderer.setSize(window.innerWidth, window.innerHeight);

  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(
    40,
    window.innerWidth / window.innerHeight,
    0.1,
    250,
  );

  // Two-level rig: yaw -> pitch -> camera (reference MainScene camera rig).
  const yaw = new THREE.Object3D();
  const pitch = new THREE.Object3D();
  yaw.add(pitch);
  pitch.add(camera);
  scene.add(yaw);
  camera.position.set(0, 1.0, 11);
  camera.lookAt(0, 0, 0);

  const desert = new ParticlesDesert({ scene, mouse, isDesktop: opts.isDesktop });
  const fog = new CustomFog({ scene, camera, mouse });
  const light = new ParticlesLight({ scene, mouse });
  // Async "hand" model — abort-safe: a late resolve after teardown is dropped.
  let disposed = false;
  const handHolder: { current: ParticlesModel | null } = { current: null };
  const draco = new DRACOLoader();
  draco.setDecoderPath("/landing-assets/draco/");
  draco.setDecoderConfig({ type: "js" }); // pure-JS decoder; no wasm staged
  const gltfLoader = new GLTFLoader();
  gltfLoader.setDRACOLoader(draco);
  gltfLoader.load(
    "/landing-assets/model/scene.glb",
    (gltf) => {
      const hand = gltf.scene.getObjectByName("hand") as THREE.Mesh | null;
      if (hand && hand.isMesh && !disposed) {
        handHolder.current = new ParticlesModel({ scene, mesh: hand, mouse });
      } else if (!disposed) {
        console.warn("[LandingScene] GLTF 'hand' mesh not found; hand cloud skipped");
      }
      // Sampling already copied vertex data into the cloud's own buffers; release
      // the parsed GLTF resources (covers used AND late-resolve-after-dispose).
      gltf.scene.traverse((o) => {
        const m = o as THREE.Mesh;
        if (m.geometry) m.geometry.dispose();
        const mat = m.material;
        if (Array.isArray(mat)) mat.forEach((x) => x.dispose());
        else if (mat) (mat as THREE.Material).dispose();
      });
    },
    undefined,
    (err) => console.error("[LandingScene] GLTF load failed", err),
  );

  // Post chain: RenderPass -> UnrealBloom (≈ reference Bloom 2.5) -> OutputPass
  // (applies Reinhard tone mapping + sRGB).
  const composer = new EffectComposer(renderer);
  composer.addPass(new RenderPass(scene, camera));
  const bloom = new UnrealBloomPass(
    new THREE.Vector2(window.innerWidth, window.innerHeight),
    2.5,
    0.4,
    0.01,
  );
  composer.addPass(bloom);
  composer.addPass(new OutputPass());

  // Mouse parallax targets (kept zero under reduced-motion).
  const targetYaw = { v: 0 };
  const targetPitch = { v: 0 };
  const onPointerMove = (e: PointerEvent) => {
    mouse.x = (e.clientX / window.innerWidth) * 2 - 1;
    mouse.y = -((e.clientY / window.innerHeight) * 2 - 1);
    if (!reducedMotion) {
      targetYaw.v = -mouse.x * PARALLAX_X;
      targetPitch.v = mouse.y * PARALLAX_Y;
    }
  };
  window.addEventListener("pointermove", onPointerMove, { passive: true });

  const onResize = () => {
    const w = window.innerWidth;
    const h = window.innerHeight;
    renderer.setSize(w, h);
    camera.aspect = w / h;
    camera.updateProjectionMatrix();
    composer.setSize(w, h);
    desert.resize(dpr);
    fog.resize();
    light.resize();
    handHolder.current?.resize(dpr);
  };
  window.addEventListener("resize", onResize);

  const clock = new THREE.Clock();
  let raf = 0;
  const lerp = (a: number, b: number, t: number) => a + (b - a) * t;

  const renderFrame = (time: number) => {
    desert.update(time);
    fog.update(time);
    light.update(time);
    handHolder.current?.update(time);
    yaw.rotation.y = lerp(yaw.rotation.y, targetYaw.v, MOUSE_LERP);
    pitch.rotation.x = lerp(pitch.rotation.x, targetPitch.v, MOUSE_LERP);
    composer.render();
  };

  const loop = () => {
    raf = requestAnimationFrame(loop);
    renderFrame(clock.getElapsedTime());
  };

  if (reducedMotion) {
    renderOnce();
  } else {
    loop();
  }

  function renderOnce() {
    renderFrame(FROZEN_TIME);
  }

  const dispose = () => {
    disposed = true;
    cancelAnimationFrame(raf);
    window.removeEventListener("pointermove", onPointerMove);
    window.removeEventListener("resize", onResize);
    desert.dispose();
    fog.dispose();
    light.dispose();
    handHolder.current?.dispose();
    composer.dispose();
    renderer.dispose();
  };

  return {
    desert,
    fog,
    light,
    camera,
    yaw,
    pitch,
    get model() {
      return handHolder.current;
    },
    renderOnce,
    dispose,
  };
}
