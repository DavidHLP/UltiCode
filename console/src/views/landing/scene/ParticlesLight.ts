import * as THREE from "three";
import vertexShader from "../shaders/light.vertex.glsl?raw";
import fragmentShader from "../shaders/light.fragment.glsl?raw";
import particleUrl from "../assets/particles.png";

/**
 * ParticlesLight — Vue/TS port of the reference landing's light pillar → starfield.
 *
 * 1000 fixed points spawned in a small sphere; the licensed light vertex shader
 * extrudes them into a rising cylinder (pillar) then morphs them into a star
 * scatter (uProgressStars). Uniforms transcribed verbatim from reference
 * ParticlesLight.js. renderOrder 999 so it draws over the other clouds.
 */

export interface ParticlesLightOptions {
  scene: THREE.Scene;
  mouse: THREE.Vector2;
  texture?: THREE.Texture;
  active?: number;
}

export class ParticlesLight {
  readonly points: THREE.Points;
  readonly uniforms: Record<string, THREE.IUniform>;
  private active: number;
  private readonly mouse: THREE.Vector2;
  private readonly mouseUniform: THREE.Vector2;
  private readonly texture: THREE.Texture;

  constructor(opts: ParticlesLightOptions) {
    const { scene, mouse, active = 1 } = opts;
    this.mouse = mouse;
    this.mouseUniform = new THREE.Vector2(9999, 9999);
    this.active = THREE.MathUtils.clamp(active, 0, 1);

    const maxCount = 1000;
    const positions = new Float32Array(maxCount * 3);
    const sizes = new Float32Array(maxCount);
    const rnd = new Float32Array(maxCount);
    const rnd2 = new Float32Array(maxCount);
    const stars = new Float32Array(maxCount * 3);
    for (let i = 0; i < maxCount; i++) {
      const i3 = i * 3;
      // Spawn: uniform sphere, radius pow(rand,0.5)*0.3 (reference ParticlesLight.js).
      const r = Math.pow(Math.random(), 0.5) * 0.3;
      const theta = Math.random() * Math.PI * 2.0;
      const phi = Math.acos(Math.random() * 2.0 - 1.0);
      positions[i3] = r * Math.sin(phi) * Math.cos(theta);
      positions[i3 + 1] = r * Math.cos(phi);
      positions[i3 + 2] = r * Math.sin(phi) * Math.sin(theta);
      sizes[i] = Math.pow(Math.random(), 2.2);
      rnd[i] = Math.random();
      rnd2[i] = Math.random();
      // Star target scatter: x[-8.5,6.5], y[2.25,5.85], z±1.75.
      stars[i3] = -8.5 + Math.random() * 15;
      stars[i3 + 1] = 2.25 + Math.random() * 3.6;
      stars[i3 + 2] = (Math.random() - 0.5) * 3.5;
    }

    const geometry = new THREE.BufferGeometry();
    const positionAttr = new THREE.BufferAttribute(positions, 3);
    positionAttr.setUsage(THREE.DynamicDrawUsage);
    geometry.setAttribute("position", positionAttr);
    geometry.setAttribute("aSize", new THREE.BufferAttribute(sizes, 1));
    geometry.setAttribute("aRnd", new THREE.BufferAttribute(rnd, 1));
    geometry.setAttribute("aRnd2", new THREE.BufferAttribute(rnd2, 1));
    geometry.setAttribute("aStarPosition", new THREE.BufferAttribute(stars, 3));
    geometry.computeBoundingSphere();
    geometry.setDrawRange(0, maxCount);

    this.texture = opts.texture ?? new THREE.TextureLoader().load(particleUrl);
    this.texture.colorSpace = THREE.SRGBColorSpace;

    const dpr = Math.min(window.devicePixelRatio || 1, 1.25);
    this.uniforms = {
      uSize: { value: 0.02 },
      uResolution: {
        value: new THREE.Vector2(window.innerWidth * dpr, window.innerHeight * dpr),
      },
      uProgress: { value: 1.0 },
      uColorA: { value: new THREE.Color("#8f8f8f") },
      uColorB: { value: new THREE.Color("#ffffff") },
      uTime: { value: 0 },
      uMouse: { value: this.mouseUniform },
      uMouseRadius: { value: 0.18 },
      uMouseStrength: { value: 0.08 },
      uMouseDepthFalloff: { value: 1.0 },
      uTexture: { value: this.texture },
      uOpacity: { value: 1.0 },
      uAlpha: { value: 1.0 },
      uGlow: { value: 1.0 },
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
      uProgressStars: { value: 0.0 },
      uStarSpread: { value: 2.0 },
      uStarHeight: { value: 1.0 },
      uStarTwinkle: { value: 1.0 },
    };

    const material = new THREE.ShaderMaterial({
      vertexShader,
      fragmentShader,
      uniforms: this.uniforms,
      blending: THREE.NormalBlending,
      depthWrite: false,
      depthTest: false,
      transparent: true,
    });

    this.points = new THREE.Points(geometry, material);
    this.points.renderOrder = 999;
    this.points.frustumCulled = false;
    this.points.visible = this.active > 0.001;
    scene.add(this.points);
  }

  setActive(value: number): void {
    this.active = THREE.MathUtils.clamp(value, 0, 1);
    if (this.points) this.points.visible = this.active > 0.001;
  }

  update(time: number): void {
    if (!this.points || this.active <= 0.001) return;
    this.uniforms.uTime.value = time;
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
