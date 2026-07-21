import * as THREE from "three";
import vertexShader from "../shaders/mouse.vertex.glsl?raw";
import fragmentShader from "../shaders/mouse.fragment.glsl?raw";

/**
 * advanceTrail — pure trail ring-buffer step (unit-tested separately).
 *
 * Lerps the smoothed pointer toward the raw mouse, drops the oldest sample, and
 * prepends the new head. Trail length is preserved (MAX_TRAIL).
 */
export function advanceTrail(
  trail: THREE.Vector2[],
  smooth: THREE.Vector2,
  mouse: THREE.Vector2,
  lerpAmount: number,
): void {
  smooth.lerp(mouse, lerpAmount);
  trail.pop();
  trail.unshift(new THREE.Vector2(smooth.x, smooth.y));
}

/**
 * MousePointer — Vue/TS port of the reference cursor: a full-screen quad whose
 * vertex shader bypasses projection (clip-space) and whose fragment shader draws
 * a gaussian-blob trail from a 32-point ring buffer (additive blend). Uniforms
 * transcribed verbatim from reference MousePointer.js.
 */

export interface MousePointerOptions {
  scene: THREE.Scene;
  mouse: THREE.Vector2; // shared NDC vector
  texture?: THREE.Texture;
}

const MAX_TRAIL = 32;

export class MousePointer {
  readonly mesh: THREE.Mesh;
  readonly uniforms: Record<string, THREE.IUniform>;
  private readonly mouse: THREE.Vector2;
  private readonly mouseSmooth: THREE.Vector2;
  private readonly trail: THREE.Vector2[];

  constructor(opts: MousePointerOptions) {
    this.mouse = opts.mouse;
    this.mouseSmooth = new THREE.Vector2(this.mouse.x, this.mouse.y);
    this.trail = [];
    for (let i = 0; i < MAX_TRAIL; i++) {
      this.trail.push(new THREE.Vector2(this.mouse.x, this.mouse.y));
    }

    this.uniforms = {
      uTrail: { value: this.trail.slice() },
      uTrailLength: { value: 20 },
      uSize: { value: 0.015 },
      uOpacity: { value: 1.0 },
      uAspect: { value: window.innerWidth / window.innerHeight },
      uFalloff: { value: 1.25 },
      uHeadBoost: { value: 1.0 },
      uTime: { value: 0 },
      uNoiseStrength: { value: 0.08 },
      uNoiseScale: { value: 18.0 },
      uNoiseSpeed: { value: 0.12 },
      uTexture: { value: opts.texture ?? null },
    };

    const material = new THREE.ShaderMaterial({
      vertexShader,
      fragmentShader,
      uniforms: this.uniforms,
      transparent: true,
      depthWrite: false,
      depthTest: false,
      blending: THREE.AdditiveBlending,
    });

    this.mesh = new THREE.Mesh(new THREE.PlaneGeometry(2, 2, 1, 1), material);
    this.mesh.frustumCulled = false;
    this.mesh.renderOrder = 9999; // draw over the particle clouds
    opts.scene.add(this.mesh);
  }

  update(): void {
    (this.uniforms.uTime.value as number) += 0.016;
    advanceTrail(this.trail, this.mouseSmooth, this.mouse, 0.18);
    const u = this.uniforms.uTrail.value as THREE.Vector2[];
    for (let i = 0; i < MAX_TRAIL; i++) u[i].copy(this.trail[i]);
    this.uniforms.uTrailLength.value = Math.min(20, MAX_TRAIL);
  }

  resize(): void {
    this.uniforms.uAspect.value = window.innerWidth / window.innerHeight;
  }

  dispose(): void {
    this.mesh.geometry.dispose();
    (this.mesh.material as THREE.ShaderMaterial).dispose();
  }
}
