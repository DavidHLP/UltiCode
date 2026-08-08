import * as THREE from 'three';
import { MSDFTextGeometry, uniforms } from '../vendor/three-msdf-text-utils.js';
import { FontLoader } from 'three/examples/jsm/loaders/FontLoader.js';
import { TEMPLATE_URL } from './config.js';

const FONT_CACHE = new Map();

function loadTexture(path) {
    return new Promise((resolve, reject) => {
        new THREE.TextureLoader().load(
            path,
            (texture) => {
                texture.needsUpdate = true;
                resolve(texture);
            },
            undefined,
            reject
        );
    });
}

function loadFont(path) {
    return new Promise((resolve, reject) => {
        new FontLoader().load(path, resolve, undefined, reject);
    });
}

async function loadMSDFFont(atlasPath, fontPath) {
    const key = `${atlasPath}__${fontPath}`;

    if (FONT_CACHE.has(key)) {
        return FONT_CACHE.get(key);
    }

    const promise = Promise.all([
        loadTexture(atlasPath),
        loadFont(fontPath)
    ]).then(([atlas, font]) => ({
        atlas,
        font
    }));

    FONT_CACHE.set(key, promise);

    return promise;
}

export class MSDFText {
    constructor(options = {}) {
        this.scene = options.scene || null;

        this.gui = options.gui || null;
        this.debugFolder = null;
        this.debugParams = null;

        this.time = options.time || 10;
        this.mouse = options.mouse;

        this.roughTexture = options.roughTexture || null;

        this.text = options.text || 'TEXT';
        this.atlasPath = options.atlasPath || `${TEMPLATE_URL}/static/font-mdf/atlas.png`;
        this.fontPath = options.fontPath || `${TEMPLATE_URL}/static/font-mdf/font.json`;

        this.letterSpacing = options.letterSpacing ?? (window.matchMedia('(max-width: 992px)').matches ? 24 : 40);
        this.color = new THREE.Color(options.color ?? 0xffffff);
        this.opacity = options.opacity ?? 1;
        this.revealProgress = options.revealProgress ?? 1;

        this.position = options.position || new THREE.Vector3(0, 0, 0);
        this.rotation = options.rotation || new THREE.Euler(0, Math.PI, 0);
        this.scaleValue = options.scale ?? 1;

        this.group = new THREE.Group();
        this.group.name = `MSDFText_${this.text}`;

        this.geometry = null;
        this.material = null;
        this.mesh = null;

        this.ready = false;
        this.active = options.active ?? 1; // 0 → 1

        this.init();
    }

    update(t, mouse) {
        if (!this.material) return;
        if (this.active <= 0.001) return;

        this.material.uniforms.uTime.value = t;

        if (mouse) {
            this.material.uniforms.uMouse.value.copy(mouse);
        }


    }

    setActive(value) {
        this.active = value;

        // visibilità soft (evita render quando è praticamente spento)
        this.group.visible = value > 0.001;
    }

    setDebug() {
        if (!this.gui || !this.material) return;

        if (this.debugFolder) return;

        const uniforms = this.material.uniforms;

        this.debugParams = {
            opacity: uniforms.uOpacity.value,
            threshold: uniforms.uThreshold.value,
            alphaTest: uniforms.uAlphaTest.value,

            strokeOutsetWidth: uniforms.uStrokeOutsetWidth.value,
            strokeInsetWidth: uniforms.uStrokeInsetWidth.value,

            revealProgress: uniforms.uRevealProgress.value,

            noiseScale: uniforms.uNoiseScale.value,
            noiseStrength: uniforms.uNoiseStrength.value,
            noiseSoftness: uniforms.uNoiseSoftness.value,
            noiseThreshold: uniforms.uNoiseThreshold.value,
            drift: uniforms.uDrift.value
        };

        this.debugFolder = this.gui.addFolder(`MSDF Text - ${this.text}`);

        this.debugFolder
            .add(this.debugParams, 'opacity', 0, 1, 0.001)
            .name('opacity')
            .onChange((value) => {
                uniforms.uOpacity.value = value;
            });

        this.debugFolder
            .add(this.debugParams, 'threshold', -1, 1, 0.001)
            .name('threshold')
            .onChange((value) => {
                uniforms.uThreshold.value = value;
            });

        this.debugFolder
            .add(this.debugParams, 'alphaTest', 0, 1, 0.001)
            .name('alpha test')
            .onChange((value) => {
                uniforms.uAlphaTest.value = value;
            });

        this.debugFolder
            .add(this.debugParams, 'strokeOutsetWidth', 0, 1, 0.001)
            .name('stroke outset')
            .onChange((value) => {
                uniforms.uStrokeOutsetWidth.value = value;
            });

        this.debugFolder
            .add(this.debugParams, 'strokeInsetWidth', 0, 1, 0.001)
            .name('stroke inset')
            .onChange((value) => {
                uniforms.uStrokeInsetWidth.value = value;
            });

        this.debugFolder
            .add(this.debugParams, 'revealProgress', 0, 2, 0.001)
            .name('reveal')
            .onChange((value) => {
                uniforms.uRevealProgress.value = value;
            });

        const noiseFolder = this.debugFolder.addFolder('noise');

        noiseFolder
            .add(this.debugParams, 'noiseScale', 0.1, 40, 0.01)
            .name('scale')
            .onChange((value) => {
                uniforms.uNoiseScale.value = value;
            });

        noiseFolder
            .add(this.debugParams, 'noiseStrength', 0, 1, 0.001)
            .name('strength')
            .onChange((value) => {
                uniforms.uNoiseStrength.value = value;
            });

        noiseFolder
            .add(this.debugParams, 'noiseSoftness', 0.001, 0.5, 0.001)
            .name('softness')
            .onChange((value) => {
                uniforms.uNoiseSoftness.value = value;
            });

        noiseFolder
            .add(this.debugParams, 'noiseThreshold', 0, 1, 0.001)
            .name('threshold')
            .onChange((value) => {
                uniforms.uNoiseThreshold.value = value;
            });

        noiseFolder
            .add(this.debugParams, 'drift', 0, 3, 0.001)
            .name('drift')
            .onChange((value) => {
                uniforms.uDrift.value = value;
            });

        if (this.debugFolder.close) {
            noiseFolder.close();
        }
    }

    async init() {
        const { atlas, font } = await loadMSDFFont(this.atlasPath, this.fontPath);

        this.atlas = atlas;
        this.font = font;

        this.createMaterial();
        this.createMesh();

        this.group.position.copy(this.position);
        this.group.rotation.copy(this.rotation);
        this.group.scale.setScalar(this.scaleValue);

        this.group.visible = this.active > 0.001;

        if (this.scene) {
            this.scene.add(this.group);
        }

        this.setDebug();
        this.ready = true;
    }

    createMaterial() {
        const baseUniforms = { ...uniforms };

        this.material = new THREE.ShaderMaterial({
            side: THREE.DoubleSide,
            transparent: true,
            depthWrite: false,
            extensions: {
                derivatives: true
            },
            defines: {
                IS_SMALL: false
            },
            uniforms: {
                ...baseUniforms.common,
                ...baseUniforms.rendering,
                ...baseUniforms.strokes,


                uNoiseScale: { value: 20.0 },
                uNoiseStrength: { value: 2 },
                uNoiseSoftness: { value: 0.01 },
                uNoiseThreshold: { value: 0.58 },
                uTime: { value: this.time },
                uDrift: { value: 1.1 },

                uMouse: { value: this.mouse },

                uMouseRadius: { value: 0.18 },
                uMouseStrength: { value: 0.025 },

                uMap: { value: this.atlas },
                uRevealProgress: { value: this.revealProgress }
            },
            vertexShader: `
                attribute vec2 layoutUv;

varying vec2 vUv;
varying vec2 vLayoutUv;
varying vec2 vScreenUv;

void main() {
    vec4 mvPosition = modelViewMatrix * vec4(position, 1.0);
    vec4 clipPosition = projectionMatrix * mvPosition;

    gl_Position = clipPosition;

    vUv = uv;
    vLayoutUv = layoutUv;

    vec2 ndc = clipPosition.xy / clipPosition.w;   // -1..1
    vScreenUv = ndc;
}
            `,
            fragmentShader: `
   varying vec2 vUv;
varying vec2 vLayoutUv;
varying vec2 vScreenUv;

uniform float uOpacity;
uniform float uThreshold;
uniform float uAlphaTest;
uniform vec3 uColor;
uniform vec2 uMouse;
uniform sampler2D uMap;

uniform vec3 uStrokeColor;
uniform float uStrokeOutsetWidth;
uniform float uStrokeInsetWidth;

uniform float uRevealProgress;

uniform float uNoiseScale;      // es 12.0
uniform float uNoiseStrength;   // es 0.35
uniform float uNoiseSoftness;   // es 0.08
uniform float uNoiseThreshold;  // es 0.62
uniform float uTime;
uniform float uDrift;           // es 0.08


uniform float uMouseRadius;
uniform float uMouseStrength;

float median(float r, float g, float b) {
    return max(min(r, g), min(max(r, g), b));
}

float hash(float n) {
    return fract(sin(n) * 43758.5453123);
}

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);

    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));

    vec2 u = f * f * (3.0 - 2.0 * f);

    return mix(a, b, u.x) +
           (c - a) * u.y * (1.0 - u.x) +
           (d - b) * u.x * u.y;
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += noise(p) * a;
        p *= 2.0;
        a *= 0.5;
    }
    return v;
}

float rand(vec2 n) {
    return fract(sin(dot(n, vec2(12.9898, 4.1414))) * 43758.5453);
}

float blockNoiseCheck(vec2 uv, float scale) {
    vec2 gv = floor(uv * scale);
    return rand(gv);
}

void main() {
    vec2 layoutUv = vLayoutUv;

    // =========================
    // 1. BLOCKY DISTORTION
    // =========================
    vec2 gridUv = floor(layoutUv * vec2(18.0, 42.0)) / vec2(18.0, 42.0);
    float blockNoise = noise(gridUv * uNoiseScale + vec2(uTime * uDrift, 0.0));

    vec2 sampleUv = vUv;
    
    // =========================
// MOUSE DISTORTION (same space as uMouse)
// =========================
vec2 deltaMouse = vScreenUv - uMouse;
float distMouse = length(deltaMouse);

float mouseInfluence = 1.0 - smoothstep(0.0, uMouseRadius, distMouse);
vec2 dirMouse = distMouse > 0.0001 ? normalize(deltaMouse) : vec2(0.0);

// repulsione semplice
sampleUv += dirMouse * mouseInfluence * uMouseStrength * 0.5;
sampleUv = clamp(sampleUv, 0.001, 0.999);

// un po' di irregolarità
float mouseJitter = noise(layoutUv * 18.0 + uTime * 0.75);
sampleUv += (mouseJitter - 0.5) * mouseInfluence * uMouseStrength * 0.35;
    
// =========================

    // piccolo offset solo su alcune celle
    float blockMask = smoothstep(0.72, 0.92, blockNoise);
    sampleUv.x += (blockNoise - 0.5) * 0.018 * blockMask;
    sampleUv.y += (blockNoise - 0.5) * 0.010 * blockMask;

    // =========================
    // 2. BASE MSDF
    // =========================
    vec3 s = texture2D(uMap, sampleUv).rgb;
    float sigDist = median(s.r, s.g, s.b) - 0.5;

    #ifdef IS_SMALL
        float afwidth = 1.4142135623730951 / 2.0;
        float alpha = smoothstep(uThreshold - afwidth, uThreshold + afwidth, sigDist);
    #else
        float alpha = clamp(sigDist / fwidth(sigDist) + 0.5, 0.0, 1.0);
    #endif

    float sigDistOutset = sigDist + uStrokeOutsetWidth * 0.5;
    float sigDistInset = sigDist - uStrokeInsetWidth * 0.5;

    #ifdef IS_SMALL
        float afwidthStroke = 1.4142135623730951 / 2.0;
        float outset = smoothstep(uThreshold - afwidthStroke, uThreshold + afwidthStroke, sigDistOutset);
        float inset = 1.0 - smoothstep(uThreshold - afwidthStroke, uThreshold + afwidthStroke, sigDistInset);
    #else
        float outset = clamp(sigDistOutset / fwidth(sigDistOutset) + 0.5, 0.0, 1.0);
        float inset = 1.0 - clamp(sigDistInset / fwidth(sigDistInset) + 0.5, 0.0, 1.0);
    #endif

    float border = outset * inset;

    // =========================
    // 3. SOFT EROSION
    // =========================
    vec2 nUv = layoutUv * uNoiseScale;
    nUv += vec2(uTime * uDrift * 0.35, -uTime * uDrift * 0.12);

    float n1 = fbm(nUv);
    float n2 = noise(floor(nUv * vec2(2.0, 14.0)) / vec2(2.0, 14.0));
    float n = mix(n1, n2, 0.35);

    // bianco noise = buco
    float hole = smoothstep(
        uNoiseThreshold - uNoiseSoftness,
        uNoiseThreshold + uNoiseSoftness,
        n
    );

    float erosionMask = 1.0 - hole;

    // più presente vicino ai bordi che nel pieno
    float edgeMask = 1.0 - smoothstep(0.03, 0.16, abs(sigDist));
    float shapedMask = mix(1.0, erosionMask, edgeMask * 0.9);

    // =========================
    // 4. GLITCH STRIPES SOTTILI
    // =========================
    float stripeBand = floor(layoutUv.y * 80.0) / 80.0;
    float stripeNoise = noise(vec2(stripeBand * 30.0, floor(layoutUv.x * 12.0) + floor(uTime * 6.0)));
    float stripeMask = smoothstep(0.82, 0.96, stripeNoise);

    // le stripe non devono distruggere tutto
    float stripeCut = 1.0 - stripeMask * 0.45 * edgeMask;


// FINAL ALPHA


float pIn  = clamp(uRevealProgress, 0.0, 1.0);
float pOut = clamp(uRevealProgress - 1.0, 0.0, 1.0);

float n4 = blockNoiseCheck(vUv, 28.0);
float noiseOffset = (n4 - 0.5) * 0.14;

// edge deformati
float revealEdge = clamp(pIn + noiseOffset, 0.0, 1.0);
float hideEdge   = clamp(pOut + noiseOffset, 0.0, 1.0);

// reveal / hide
float reveal = step(layoutUv.x, revealEdge);
float hide   = step(layoutUv.x, hideEdge);

// gate iniziale e finale per evitare sporcizia ai bordi
reveal *= step(0.01, pIn);
reveal = mix(reveal, 1.0, step(0.999, pIn));

hide *= step(0.01, pOut);
hide = mix(hide, 1.0, step(0.999, pOut));

float finalReveal = reveal * (1.0 - hide);

float finalMask = mix(1.0, shapedMask * stripeCut, clamp(uNoiseStrength, 0.0, 1.0));
float finalAlpha = alpha * finalMask * finalReveal;


if (finalAlpha < uAlphaTest) discard;

vec4 fillColor = vec4(uColor, uOpacity * finalAlpha);
vec4 strokeColor = vec4(uStrokeColor, uOpacity * border * finalAlpha);

vec4 finalColor = mix(fillColor, strokeColor, border);

gl_FragColor = finalColor;

}
`
        });

        this.material.uniforms.uColor.value = this.color;
        this.material.uniforms.uOpacity.value = this.opacity;
        this.material.uniforms.uThreshold.value = 0.05;
        this.material.uniforms.uAlphaTest.value = 0.01;

        this.material.uniforms.uStrokeColor.value = new THREE.Color(0x000000);
        this.material.uniforms.uStrokeOutsetWidth.value = 0.0;
        this.material.uniforms.uStrokeInsetWidth.value = 0.0;
    }

    createGeometry(text = this.text) {
        if (this.geometry) {
            this.geometry.dispose();
        }

        this.geometry = new MSDFTextGeometry({
            text,
            font: this.font.data,
            letterSpacing: this.letterSpacing
        });
    }

    createMesh() {
        this.createGeometry(this.text);

        this.mesh = new THREE.Mesh(this.geometry, this.material);

        this.mesh.scale.y = -1;
        this.mesh.renderOrder = 999;
        this.group.add(this.mesh);

    }

    setText(text) {
        this.text = text;
        if (!this.ready) return;

        this.createGeometry(text);
        this.mesh.geometry = this.geometry;
    }

    setPosition(x, y, z = 0) {
        this.group.position.set(x, y, z);
    }

    setScale(x, y = x, z = x) {
        this.group.scale.set(x, y, z);
    }

    setRotation(x, y, z) {
        this.group.rotation.set(x, y, z);
    }

    setColor(color) {
        this.color.set(color);
        if (this.material) {
            this.material.uniforms.uColor.value.copy(this.color);
        }
    }

    setOpacity(value) {
        this.opacity = value;
        if (this.material) {
            this.material.uniforms.uOpacity.value = value;
        }
    }

    setRevealProgress(value) {
        this.revealProgress = value;
        if (this.material) {
            this.material.uniforms.uRevealProgress.value = value;
        }
    }

    getObject() {
        return this.group;
    }

    destroy() {
        if (this.mesh) {
            this.group.remove(this.mesh);
        }

        if (this.geometry) this.geometry.dispose();
        if (this.material) this.material.dispose();

        if (this.scene) {
            this.scene.remove(this.group);
        }
    }
}
