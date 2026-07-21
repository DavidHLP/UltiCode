import { ShaderPass } from "three/examples/jsm/postprocessing/ShaderPass.js";

/**
 * DitherEffect — screen-space hash dither applied to dark regions only.
 *
 * Ports the reference DitherEffect (which extended the `postprocessing`
 * package's Effect) to a three.js `ShaderPass`, since the scene uses three's
 * built-in EffectComposer. Strength defaults to 0.001 (the reference MainScene
 * override of the construction default 0.015). Call `update(delta)` each frame
 * to advance the slow time-based hash drift.
 */

const vertexShader = /* glsl */ `
varying vec2 vUv;
void main() {
  vUv = uv;
  gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
}
`;

const fragmentShader = /* glsl */ `
uniform sampler2D tDiffuse;
uniform float uStrength;
uniform float uTime;
varying vec2 vUv;

float hash(vec2 p) {
  p = fract(p * vec2(123.34, 456.21));
  p += dot(p, p + 34.45);
  return fract(p.x * p.y);
}

void main() {
  vec4 color = texture2D(tDiffuse, vUv);
  float n = hash(gl_FragCoord.xy + uTime * 0.01) - 0.5;
  float luma = dot(color.rgb, vec3(0.299, 0.587, 0.114));
  // Restrict the dither to dark regions (low luma).
  float lowMask = 1.0 - smoothstep(0.02, 0.18, luma);
  color.rgb += n * uStrength * lowMask;
  gl_FragColor = color;
}
`;

export class DitherEffect extends ShaderPass {
  constructor(strength = 0.001) {
    super({
      uniforms: {
        tDiffuse: { value: null },
        uStrength: { value: strength },
        uTime: { value: 0 },
      },
      vertexShader,
      fragmentShader,
    });
  }

  /** Advance the slow hash drift by the frame delta. */
  update(delta: number): void {
    this.uniforms.uTime.value += delta;
  }
}
