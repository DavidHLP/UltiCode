/**
 * Wasteland renderer — thin three.js adapter over the pure noise/rail/morph
 * model. Dynamically imported by LandingCanvas so three never blocks first
 * paint.
 *
 * One Points cloud carries the whole world: six morph-state position
 * attributes blended in the vertex shader (CPU never touches particles per
 * frame), plus a small dust cloud reusing the same material. Post-feel is
 * fog + soft sprites; grain/vignette live in CSS, not GPU passes.
 */

import * as THREE from "three";
import { buildMorphTargets } from "./morphTargets";
import { CHAPTERS, MORPH_COUNT, PARTICLE_BUDGET } from "./layout";
import {
  buildKeyframes,
  morphFloat,
  sampleCamera,
  type CameraFrame,
  type RailVariant,
} from "./rail";

export interface WastelandLabels {
  /** One short title per chapter (already localized). */
  chapters: string[];
  /** Brand word floated in the growth starfield. */
  brand: string;
}

export interface WastelandSceneOptions {
  canvas: HTMLCanvasElement;
  variant: RailVariant;
  maxDpr: number;
  interactive: boolean;
  labels: WastelandLabels;
}

export interface WastelandScene {
  setProgress(progress: number): void;
  /** Draw one frame; dt drives breathing and damping. */
  render(dt: number): void;
  /** Reduced-motion chapter shot: pin progress, draw once. */
  renderAt(progress: number): void;
  /** Normalized pointer (-1..1). */
  setPointer(nx: number, ny: number): void;
  /** Attraction field for DOM link hovers (monolith gathering). */
  setGather(x: number, y: number, z: number, strength: number): void;
  setSize(width: number, height: number): void;
  dispose(): void;
}

const BG_COLOR = new THREE.Color(0x0a0a0a);
const SILVER = new THREE.Color(0.92, 0.94, 0.97);
const FOG_DENSITY = 0.0065;
const DUST_COUNT = 600;

const VERTEX_SHADER = /* glsl */ `
  attribute vec3 aTarget1;
  attribute vec3 aTarget2;
  attribute vec3 aTarget3;
  attribute vec3 aTarget4;
  attribute vec3 aTarget5;
  attribute vec3 aRandom; // brightness, phase, size

  uniform float uMorph;      // 0..5 continuous chapter coordinate
  uniform float uTime;
  uniform vec3 uMouse;
  uniform float uMouseActive;
  uniform vec3 uGatherPos;
  uniform float uGatherStrength;
  uniform float uPointScale; // projection-dependent pixel scale
  uniform float uBreathAmp;
  uniform float uFogDensity;

  varying float vAlpha;
  varying float vFog;

  void main() {
    // Sequential morph chain: always a continuous blend, never a swap.
    vec3 p = mix(position, aTarget1, clamp(uMorph, 0.0, 1.0));
    p = mix(p, aTarget2, clamp(uMorph - 1.0, 0.0, 1.0));
    p = mix(p, aTarget3, clamp(uMorph - 2.0, 0.0, 1.0));
    p = mix(p, aTarget4, clamp(uMorph - 3.0, 0.0, 1.0));
    p = mix(p, aTarget5, clamp(uMorph - 4.0, 0.0, 1.0));

    // Slow breathing / drift — atmosphere, not waves.
    float breath = sin(uTime * 0.35 + aRandom.y);
    p.y += breath * uBreathAmp;
    p.x += sin(uTime * 0.11 + aRandom.y * 1.7) * uBreathAmp * 0.5;

    // Mouse: a small repulsion field that never breaks the formation.
    vec3 away = p - uMouse;
    float md = length(away);
    float repel = smoothstep(6.0, 0.0, md) * uMouseActive;
    p += normalize(away + vec3(0.0001)) * repel * 1.4;

    // Gather: DOM link hovers pull nearby particles toward a monolith.
    vec3 toward = uGatherPos - p;
    float gd = length(toward);
    float gather = smoothstep(16.0, 0.0, gd) * uGatherStrength;
    p += normalize(toward + vec3(0.0001)) * gather * 2.2;

    vec4 mv = modelViewMatrix * vec4(p, 1.0);
    float dist = max(0.001, -mv.z);

    // Foreground bokeh: nearer particles grow and soften out.
    float nearBlur = smoothstep(12.0, 2.5, dist);
    float size = aRandom.z * (1.0 + nearBlur * 1.8);
    gl_PointSize = size * uPointScale / dist;
    gl_Position = projectionMatrix * mv;

    float alpha = aRandom.x;
    alpha *= smoothstep(1.2, 5.0, dist);            // dissolve extreme close-up
    alpha *= 1.0 - smoothstep(60.0, 240.0, dist) * 0.6; // distance decay
    vAlpha = alpha * (1.0 - nearBlur * 0.45);

    vFog = 1.0 - exp(-uFogDensity * uFogDensity * dist * dist);
  }
`;

const FRAGMENT_SHADER = /* glsl */ `
  uniform vec3 uColor;
  uniform vec3 uFogColor;

  varying float vAlpha;
  varying float vFog;

  void main() {
    vec2 uv = gl_PointCoord - vec2(0.5);
    float d = length(uv);
    float disc = smoothstep(0.5, 0.1, d);
    float alpha = vAlpha * disc * (1.0 - vFog * 0.75);
    if (alpha < 0.004) discard;
    vec3 color = mix(uColor, uFogColor, vFog);
    gl_FragColor = vec4(color, alpha);
  }
`;

function makeTitleTexture(text: string): THREE.CanvasTexture {
  const canvas = document.createElement("canvas");
  canvas.width = 1024;
  canvas.height = 224;
  const ctx = canvas.getContext("2d");
  if (ctx) {
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    ctx.fillStyle = "rgba(235, 238, 242, 0.9)";
    ctx.textBaseline = "middle";
    ctx.font = "300 92px 'LXGW WenKai', 'Noto Sans SC', sans-serif";
    // Manual letter-spacing for the wide, mechanical chapter titling.
    const spacing = 18;
    const chars = Array.from(text);
    const widths = chars.map((ch) => ctx.measureText(ch).width);
    const total = widths.reduce((a, b) => a + b, 0) + spacing * (chars.length - 1);
    let x = (canvas.width - total) / 2;
    chars.forEach((ch, i) => {
      ctx.fillText(ch, x, canvas.height / 2);
      x += widths[i] + spacing;
    });
  }
  const texture = new THREE.CanvasTexture(canvas);
  texture.anisotropy = 2;
  return texture;
}

export function createWastelandScene(
  options: WastelandSceneOptions,
): WastelandScene {
  const { canvas, variant, maxDpr, interactive, labels } = options;

  const renderer = new THREE.WebGLRenderer({
    canvas,
    antialias: false,
    alpha: false,
    powerPreference: "high-performance",
  });
  renderer.setClearColor(BG_COLOR, 1);
  const dpr = Math.min(window.devicePixelRatio || 1, maxDpr);
  renderer.setPixelRatio(dpr);

  const scene = new THREE.Scene();
  scene.background = BG_COLOR;
  const camera = new THREE.PerspectiveCamera(55, 1, 0.1, 600);

  const keyframes = buildKeyframes(variant);
  const count =
    variant === "mobile" ? PARTICLE_BUDGET.mobile : PARTICLE_BUDGET.desktop;
  const targets = buildMorphTargets(count);

  const uniforms = {
    uMorph: { value: 0 },
    uTime: { value: 0 },
    uMouse: { value: new THREE.Vector3(0, -999, 0) },
    uMouseActive: { value: 0 },
    uGatherPos: { value: new THREE.Vector3(0, 0, 0) },
    uGatherStrength: { value: 0 },
    uPointScale: { value: 600 },
    uBreathAmp: { value: 0.22 },
    uFogDensity: { value: FOG_DENSITY },
    uColor: { value: SILVER },
    uFogColor: { value: BG_COLOR },
  };

  const material = new THREE.ShaderMaterial({
    uniforms,
    vertexShader: VERTEX_SHADER,
    fragmentShader: FRAGMENT_SHADER,
    transparent: true,
    depthWrite: false,
    blending: THREE.AdditiveBlending,
  });

  function buildCloud(source: {
    states: Float32Array[];
    random: Float32Array;
    count: number;
  }): THREE.Points {
    const geometry = new THREE.BufferGeometry();
    geometry.setAttribute(
      "position",
      new THREE.BufferAttribute(source.states[0], 3),
    );
    for (let s = 1; s < MORPH_COUNT; s++) {
      geometry.setAttribute(
        `aTarget${s}`,
        new THREE.BufferAttribute(source.states[s], 3),
      );
    }
    geometry.setAttribute(
      "aRandom",
      new THREE.BufferAttribute(source.random, 3),
    );
    return new THREE.Points(geometry, material);
  }

  const cloud = buildCloud(targets);
  scene.add(cloud);

  // Ambient dust: a small always-drifting layer sharing the material.
  const dustRandom = new Float32Array(DUST_COUNT * 3);
  const dustStates: Float32Array[] = [];
  for (let s = 0; s < MORPH_COUNT; s++) {
    dustStates.push(new Float32Array(DUST_COUNT * 3));
  }
  for (let i = 0; i < DUST_COUNT; i++) {
    const x = (Math.sin(i * 12.9898) * 43758.5453) % 1;
    const y = (Math.sin(i * 78.233) * 12543.123) % 1;
    const z = (Math.sin(i * 39.425) * 24634.6345) % 1;
    const px = (Math.abs(x) - 0.5) * 90;
    const py = Math.abs(y) * 26 - 1;
    const pz = 20 - Math.abs(z) * 330;
    for (let s = 0; s < MORPH_COUNT; s++) {
      dustStates[s][i * 3] = px;
      dustStates[s][i * 3 + 1] = py;
      dustStates[s][i * 3 + 2] = pz;
    }
    dustRandom[i * 3] = 0.1 + Math.abs(x) * 0.25;
    dustRandom[i * 3 + 1] = Math.abs(y) * Math.PI * 2;
    dustRandom[i * 3 + 2] = 0.8 + Math.abs(z) * 1.4;
  }
  const dust = buildCloud({
    states: dustStates,
    random: dustRandom,
    count: DUST_COUNT,
  });
  scene.add(dust);

  // In-world chapter titles: canvas-texture planes that fog dims with depth.
  const titleDisposables: { dispose(): void }[] = [];
  CHAPTERS.forEach((chapter, index) => {
    const text = labels.chapters[index];
    if (!text) return;
    const texture = makeTitleTexture(text);
    const geometry = new THREE.PlaneGeometry(30, 6.6);
    const planeMaterial = new THREE.MeshBasicMaterial({
      map: texture,
      transparent: true,
      opacity: 0.5,
      depthWrite: false,
      fog: false,
    });
    const plane = new THREE.Mesh(geometry, planeMaterial);
    plane.position.set(0, 9.5, chapter.titleZ);
    scene.add(plane);
    titleDisposables.push(texture, geometry, planeMaterial);
  });

  // Brand word drifting in the growth starfield.
  if (labels.brand) {
    const texture = makeTitleTexture(labels.brand);
    const geometry = new THREE.PlaneGeometry(40, 8.8);
    const planeMaterial = new THREE.MeshBasicMaterial({
      map: texture,
      transparent: true,
      opacity: 0.35,
      depthWrite: false,
      fog: false,
    });
    const plane = new THREE.Mesh(geometry, planeMaterial);
    plane.position.set(0, 30, -196);
    scene.add(plane);
    titleDisposables.push(texture, geometry, planeMaterial);
  }

  const frame: CameraFrame = { pos: [0, 26, 34], look: [0, 0, -30], fov: 55 };
  const lookTarget = new THREE.Vector3();
  const pointerNdc = new THREE.Vector3();
  const rayDirection = new THREE.Vector3();
  let progress = 0;
  let time = 0;
  let pointerX = 0;
  let pointerY = 0;
  let dampedPointerX = 0;
  let dampedPointerY = 0;
  let gatherTarget = 0;
  let disposed = false;

  function updateMouseWorld(): void {
    if (!interactive || uniforms.uMouseActive.value < 0.01) return;
    // Ray from the camera through the pointer onto a plane ahead of it.
    pointerNdc.set(pointerX, pointerY, 0.5).unproject(camera);
    rayDirection.copy(pointerNdc).sub(camera.position).normalize();
    const planeZ = camera.position.z - 16;
    const t = (planeZ - camera.position.z) / rayDirection.z;
    const mouse = uniforms.uMouse.value as THREE.Vector3;
    mouse
      .copy(camera.position)
      .add(rayDirection.multiplyScalar(Math.max(0, t)));
  }

  return {
    setProgress(nextProgress: number): void {
      if (disposed) return;
      progress = Math.min(1, Math.max(0, nextProgress));
      uniforms.uMorph.value = morphFloat(progress);
    },

    render(dt: number): void {
      if (disposed) return;
      time += Math.min(0.1, Math.max(0, dt));
      uniforms.uTime.value = time;

      sampleCamera(keyframes, progress, frame);
      camera.position.set(frame.pos[0], frame.pos[1], frame.pos[2]);

      const damp = 1 - Math.exp(-dt * 5);
      dampedPointerX += (pointerX - dampedPointerX) * damp;
      dampedPointerY += (pointerY - dampedPointerY) * damp;
      uniforms.uMouseActive.value +=
        ((interactive ? 1 : 0) - uniforms.uMouseActive.value) * damp;
      uniforms.uGatherStrength.value +=
        (gatherTarget - uniforms.uGatherStrength.value) * damp;

      lookTarget.set(
        frame.look[0] + dampedPointerX * 0.7,
        frame.look[1] - dampedPointerY * 0.45,
        frame.look[2],
      );
      camera.up.set(0, 1, 0);
      camera.lookAt(lookTarget);

      if (Math.abs(camera.fov - frame.fov) > 0.01) {
        camera.fov = frame.fov;
        camera.updateProjectionMatrix();
      }

      // Point size follows projection: h/2 / tan(fov/2), times DPR.
      const heightPx = renderer.domElement.height;
      uniforms.uPointScale.value =
        heightPx / (2 * Math.tan(THREE.MathUtils.degToRad(camera.fov) / 2));

      updateMouseWorld();
      renderer.render(scene, camera);
    },

    renderAt(atProgress: number): void {
      this.setProgress(atProgress);
      this.render(0.016);
    },

    setPointer(nx: number, ny: number): void {
      if (!interactive) return;
      pointerX = nx;
      pointerY = ny;
    },

    setGather(x: number, y: number, z: number, strength: number): void {
      (uniforms.uGatherPos.value as THREE.Vector3).set(x, y, z);
      gatherTarget = strength;
    },

    setSize(width: number, height: number): void {
      if (disposed) return;
      renderer.setSize(width, height, false);
      camera.aspect = width / Math.max(1, height);
      camera.updateProjectionMatrix();
    },

    dispose(): void {
      if (disposed) return;
      disposed = true;
      cloud.geometry.dispose();
      dust.geometry.dispose();
      material.dispose();
      for (const disposable of titleDisposables) disposable.dispose();
      renderer.dispose();
    },
  };
}
