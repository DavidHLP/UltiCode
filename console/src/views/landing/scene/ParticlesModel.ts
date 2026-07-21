import * as THREE from "three";
import { MeshSurfaceSampler } from "three/examples/jsm/math/MeshSurfaceSampler.js";
import vertexShader from "../shaders/particles.vertex.glsl?raw";
import fragmentShader from "../shaders/particles.fragment.glsl?raw";
import particleUrl from "../assets/particles.png";

/**
 * ParticlesModel — Vue/TS port of the reference landing's "hand" point cloud.
 *
 * Samples the GLTF "hand" mesh surface → 15000 points; the vertex shader
 * (rights-secured) drives the collapse morph (uProgress 1→0 toward a flat disc
 * at y = −1.25) with arm-direction lighting fade. Uniforms transcribed verbatim
 * from reference ParticlesModel.js.
 */

export interface ParticlesModelOptions {
  scene: THREE.Scene;
  /** The GLTF "hand" mesh (gltf.scene.getObjectByName("hand")). */
  mesh: THREE.Mesh;
  /** Shared NDC mouse vector. */
  mouse: THREE.Vector2;
  texture?: THREE.Texture;
  active?: number;
}

export class ParticlesModel {
  readonly points: THREE.Points;
  readonly uniforms: Record<string, THREE.IUniform>;
  private active: number;
  private readonly mouse: THREE.Vector2;
  private readonly mouseUniform: THREE.Vector2;
  private readonly texture: THREE.Texture;

  constructor(opts: ParticlesModelOptions) {
    const { scene, mesh, mouse, active = 1 } = opts;
    this.mouse = mouse;
    this.mouseUniform = new THREE.Vector2(9999, 9999);
    this.active = THREE.MathUtils.clamp(active, 0, 1);

    const sampler = new MeshSurfaceSampler(mesh).build();
    const maxCount = Math.floor(30000 / 2); // 15000

    const positions = new Float32Array(maxCount * 3);
    const normals = new Float32Array(maxCount * 3);
    const p = new THREE.Vector3();
    const n = new THREE.Vector3();
    const targetCount = Math.min(
      mesh.geometry.attributes.position.count,
      maxCount,
    );
    for (let i = 0; i < targetCount; i++) {
      sampler.sample(p, n);
      const i3 = i * 3;
      positions[i3] = p.x;
      positions[i3 + 1] = p.y;
      positions[i3 + 2] = p.z;
      n.normalize();
      normals[i3] = n.x;
      normals[i3 + 1] = n.y;
      normals[i3 + 2] = n.z;
    }
    // Pad remaining slots by duplicating sampled points (matches reference).
    for (let i = targetCount; i < maxCount; i++) {
      const src = Math.floor(Math.random() * targetCount) * 3;
      const i3 = i * 3;
      positions[i3] = positions[src];
      positions[i3 + 1] = positions[src + 1];
      positions[i3 + 2] = positions[src + 2];
      normals[i3] = normals[src];
      normals[i3 + 1] = normals[src + 1];
      normals[i3 + 2] = normals[src + 2];
    }

    const sizes = new Float32Array(maxCount);
    const rnd = new Float32Array(maxCount);
    const rnd2 = new Float32Array(maxCount);
    const targets = new Float32Array(maxCount * 3); // collapse disc at y = -1.25
    for (let i = 0; i < maxCount; i++) {
      sizes[i] = Math.pow(Math.random(), 2.2);
      rnd[i] = Math.random();
      rnd2[i] = Math.random();
      const i3 = i * 3;
      targets[i3] = (Math.random() - 0.5) * 2.0;
      targets[i3 + 1] = -1.25;
      targets[i3 + 2] = (Math.random() - 0.5) * 2.0;
    }

    const geometry = new THREE.BufferGeometry();
    geometry.setAttribute("position", new THREE.BufferAttribute(positions, 3));
    geometry.setAttribute("aNormal", new THREE.BufferAttribute(normals, 3));
    geometry.setAttribute("aSize", new THREE.BufferAttribute(sizes, 1));
    geometry.setAttribute("aRnd", new THREE.BufferAttribute(rnd, 1));
    geometry.setAttribute("aRnd2", new THREE.BufferAttribute(rnd2, 1));
    geometry.setAttribute("aPositionTarget", new THREE.BufferAttribute(targets, 3));
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
      uTime: { value: 0 },
      uMouse: { value: this.mouseUniform },
      uMouseRadius: { value: 0.22 },
      uMouseStrength: { value: 0.18 },
      uMouseDepthFalloff: { value: 1.0 },
      uTexture: { value: this.texture },
      uOpacity: { value: 1.0 },
      uAlpha: { value: 1.0 },
      uGlow: { value: 1.5 },
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
      uArmFadeStart: { value: -0.86 },
      uArmFadeEnd: { value: 0.32 },
      uArmFadeDir: { value: new THREE.Vector3(-0.015, 0.845, 0) },
      uLightDir: { value: new THREE.Vector3(0.4, 0.9, 0.2).normalize() },
      uBase: { value: 0.01 },
      uDiffuse: { value: 0.6 },
      uRim: { value: 0.35 },
      uSpec: { value: 1.2 },
      uSpecPow: { value: 40.0 },
      uLightWrap: { value: 0.25 },
    };

    const material = new THREE.ShaderMaterial({
      vertexShader,
      fragmentShader,
      uniforms: this.uniforms,
      blending: THREE.NormalBlending,
      depthWrite: false,
      transparent: true,
    });

    this.points = new THREE.Points(geometry, material);
    this.points.frustumCulled = false;
    this.points.visible = this.active > 0.001;
    scene.add(this.points);
  }

  /** Gate visibility/updates; 0 hides the cloud. */
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
