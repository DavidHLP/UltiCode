import { BlendFunction, Effect } from 'postprocessing';
import { Uniform } from 'three';

const fragmentShader = /* glsl */ `
uniform float uStrength;
uniform float uTime;

// hash semplice screen-space
float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 34.45);
    return fract(p.x * p.y);
}

void mainImage(
    const in vec4 inputColor,
    const in vec2 uv,
    out vec4 outputColor
) {
    vec4 color = inputColor;

    // dithering in screen space
    float n = hash(gl_FragCoord.xy + uTime * 0.01) - 0.5;

    // molto sottile, soprattutto sui bassi valori
    float luma = dot(color.rgb, vec3(0.299, 0.587, 0.114));
    float lowMask = 1.0 - smoothstep(0.02, 0.18, luma);

    color.rgb += n * uStrength * lowMask;

    outputColor = color;
}
`;

export class DitherEffect extends Effect {
    constructor({
                    blendFunction = BlendFunction.NORMAL,
                    strength = 0.015
                } = {}) {
        super('DitherEffect', fragmentShader, {
            blendFunction,
            uniforms: new Map([
                ['uStrength', new Uniform(strength)],
                ['uTime', new Uniform(0)]
            ])
        });
    }

    get strength() {
        return this.uniforms.get('uStrength').value;
    }

    set strength(value) {
        this.uniforms.get('uStrength').value = value;
    }

    update(renderer, inputBuffer, deltaTime) {
        this.uniforms.get('uTime').value += deltaTime;
    }
}
