import * as THREE from "three";
import { MeshSurfaceSampler } from "three/examples/jsm/math/MeshSurfaceSampler.js";
import vertexShader from "../shaders/desert.vertex.glsl?raw";
import fragmentShader from "../shaders/desert.fragment.glsl?raw";
import particleUrl from "../assets/particles.png";
import { mulberry32, makeValueNoise } from "./noise";

/**
 * ParticlesDesert — Vue/TS port of the reference landing's desert particle system.
 *
 * The reference samples a sculpted GLTF "desert" mesh; this first version samples a
 * procedurally displaced dune plane (license-clean geometry) so the silhouette is
 * UltiCode's own, while the 3-state morph (desert → mirror-split → black-hole vortex)
 * is identical — it lives entirely in the (rights-secured) vertex shader and is driven
 * by uniform values the scroll timeline tweens directly on `this.uniforms`.
 *
 * Uniform values are transcribed verbatim from the reference ParticlesDesert.js so the
 * shader behaves identically.
 */

export interface ParticlesDesertOptions {
  scene: THREE.Scene;
  /** Shared NDC mouse vector (-1..1), mutated by the scene composable each frame. */
  mouse: THREE.Vector2;
  seed?: number;
  isDesktop?: boolean;
  texture?: THREE.Texture;
}

export class ParticlesDesert {
  readonly points: THREE.Points;
  readonly uniforms: Record<string, THREE.IUniform>;
  private readonly mouse: THREE.Vector2;
  private readonly mouseUniform: THREE.Vector2;
  private readonly texture: THREE.Texture;
  private previousTime: number | null = null;

  constructor(opts: ParticlesDesertOptions) {
    const { scene, mouse, seed = 13579, isDesktop = true } = opts;
    this.mouse = mouse;
    this.mouseUniform = new THREE.Vector2(9999, 9999);

    // 1. Procedural displaced dune plane -> mesh to surface-sample.
    const rng = mulberry32(seed);
    const noise = makeValueNoise(rng);
    const SIZE = 44;
    const SEG = 220;
    const duneGeo = new THREE.PlaneGeometry(SIZE, SIZE, SEG, SEG);
    const dunePos = duneGeo.attributes.position as THREE.BufferAttribute;
    for (let i = 0; i < dunePos.count; i++) {
      const x = dunePos.getX(i);
      const y = dunePos.getY(i);
      const h =
        noise(x * 0.06, y * 0.06, 4) * 3.2 +
        noise(x * 0.21, y * 0.21, 3) * 0.7;
      dunePos.setZ(i, h);
    }
    duneGeo.computeVertexNormals();
    const duneMesh = new THREE.Mesh(duneGeo);
    const duneNorm = duneGeo.attributes.normal as THREE.BufferAttribute;

    // 2. Surface-sample (reference: count/2 of source mesh verts).
    const sampler = new MeshSurfaceSampler(duneMesh).build();
    const maxCount = Math.floor(dunePos.count / 2);

    const positions = new Float32Array(maxCount * 3);
    const normals = new Float32Array(maxCount * 3);
    const sizes = new Float32Array(maxCount);
    const rnd = new Float32Array(maxCount);
    const rnd2 = new Float32Array(maxCount);
    const targets = new Float32Array(maxCount * 3);

    const p = new THREE.Vector3();
    for (let i = 0; i < maxCount; i++) {
      sampler.sample(p);
      positions[i * 3] = p.x;
      positions[i * 3 + 1] = p.y;
      positions[i * 3 + 2] = p.z;
      const vi = Math.floor(rng() * dunePos.count);
      normals[i * 3] = duneNorm.getX(vi);
      normals[i * 3 + 1] = duneNorm.getY(vi);
      normals[i * 3 + 2] = duneNorm.getZ(vi);
      sizes[i] = Math.pow(rng(), 2.2);
      rnd[i] = rng();
      rnd2[i] = rng();
      // aPositionTarget scatter (reference magnitude (rand-0.5)*~50).
      targets[i * 3] = (rng() - 0.5) * 50.4;
      targets[i * 3 + 1] = (rng() - 0.5) * 36.0;
      targets[i * 3 + 2] = (rng() - 0.5) * 22.0;
    }

    const geometry = new THREE.BufferGeometry();
    geometry.setAttribute("position", new THREE.BufferAttribute(positions, 3));
    geometry.setAttribute("aNormal", new THREE.BufferAttribute(normals, 3));
    geometry.setAttribute("aSize", new THREE.BufferAttribute(sizes, 1));
    geometry.setAttribute("aRnd", new THREE.BufferAttribute(rnd, 1));
    geometry.setAttribute("aRnd2", new THREE.BufferAttribute(rnd2, 1));
    geometry.setAttribute(
      "aPositionTarget",
      new THREE.BufferAttribute(targets, 3),
    );
    geometry.computeBoundingSphere();
    geometry.setDrawRange(0, maxCount);

    // 3. Particle sprite texture (licensed/reusable).
    this.texture =
      opts.texture ?? new THREE.TextureLoader().load(particleUrl);
    this.texture.colorSpace = THREE.SRGBColorSpace;

    const tunnelRadius = isDesktop ? 7.034 : 3.034;

    // 4. Uniforms transcribed verbatim from reference ParticlesDesert.js.
    const dpr = Math.min(window.devicePixelRatio || 1, 1.25);
    this.uniforms = {
      uSize: { value: 0.02 },
      uResolution: {
        value: new THREE.Vector2(
          window.innerWidth * dpr,
          window.innerHeight * dpr,
        ),
      },
      uProgress: { value: 1 },
      uColorA: { value: new THREE.Color("#757575") },
      uColorB: { value: new THREE.Color("#ffffff") },
      uCornerRadius: { value: 5 },
      uTime: { value: 0 },
      uMouse: { value: this.mouseUniform },
      uMouseRadius: { value: 0.22 },
      uMouseStrength: { value: 0.18 },
      uMouseDepthFalloff: { value: 1.0 },
      uTexture: { value: this.texture },
      uOpacity: { value: 1.0 },
      uAlpha: { value: 2.0 },
      uGlow: { value: 2.5 },
      uCore: { value: 0.35 },
      uCoreSoft: { value: 0.06 },
      uHalo: { value: 0.55 },
      uHaloSoft: { value: 0.25 },
      uHaloStrength: { value: 0.6 },
      uEdge: { value: 0.62 },
      uEdgeSoft: { value: 0.12 },
      uCoreBoost: { value: 0.0 },
      uHaloBoost: { value: 0.0 },
      uSaturation: { value: 1.15 },
      uLightDir: { value: new THREE.Vector3(0.4, 0.9, 0.2).normalize() },
      uBase: { value: 0.01 },
      uDiffuse: { value: 0.6 },
      uRim: { value: 0.35 },
      uSpec: { value: 1.2 },
      uSpecPow: { value: 40.0 },
      uLightWrap: { value: 0.25 },
      uMorphDir: { value: new THREE.Vector3(0, 0, 0) },
      uMorphFadeStart: { value: -2.0 },
      uMorphFadeEnd: { value: 2.0 },
      uHeightStart: { value: 0.0 },
      uHeightEnd: { value: 1.0 },
      uCollapseStrength: { value: 0.05 },
      uDesertMin: { value: new THREE.Vector2(-11.095, -8.591) },
      uDesertMax: { value: new THREE.Vector2(6.555, 15.099) },
      uEdgeFadeWidth: { value: 3.437 },
      uSplitProgress: { value: 0.0 },
      uMirrorGap: { value: 7.0 },
      uBlackHoleProgress: { value: 0.0 },
      uBlackHoleCenter: { value: new THREE.Vector3(-4, 3.165, 0.675) },
      uBlackHoleRadius: { value: 44.569 },
      uBlackHoleSpin: { value: 0.308 },
      uBlackHoleDepth: { value: 24.794 },
      uBlackHoleDepthDir: { value: new THREE.Vector3(0, 0, -1) },
      uBlackHoleTunnelRadius: { value: tunnelRadius },
      uBlackHoleTunnelThickness: { value: 3.348 },
      uBlackHoleSpiralSpeed: { value: 0.022 },
      uBlackHoleOrbitTime: { value: 0.1 },
      uBlackHoleSpiralPull: { value: 0.172 },
    };

    const material = new THREE.ShaderMaterial({
      vertexShader,
      fragmentShader,
      uniforms: this.uniforms,
      blending: THREE.NormalBlending,
      depthWrite: false,
      transparent: true,
      depthTest: false,
    });

    this.points = new THREE.Points(geometry, material);
    this.points.frustumCulled = false;
    scene.add(this.points);

    duneGeo.dispose();
  }

  /** Per-frame: advance time, the black-hole orbit, and the shared mouse uniform. */
  update(time: number): void {
    const u = this.uniforms;
    const previousTime = this.previousTime ?? time;
    const delta = Math.min(Math.max(time - previousTime, 0), 0.5);
    this.previousTime = time;
    u.uTime.value = time;
    u.uBlackHoleOrbitTime.value +=
      delta * (u.uBlackHoleSpiralSpeed.value as number);
    this.mouseUniform.set(this.mouse.x, this.mouse.y);
  }

  resize(pixelRatio = Math.min(window.devicePixelRatio || 1, 1.25)): void {
    (this.uniforms.uResolution.value as THREE.Vector2).set(
      window.innerWidth * pixelRatio,
      window.innerHeight * pixelRatio,
    );
  }

  dispose(): void {
    this.points.geometry.dispose();
    (this.points.material as THREE.ShaderMaterial).dispose();
    this.texture.dispose();
  }
}
