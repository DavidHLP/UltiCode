uniform float uTime;
uniform float uOpacity;
uniform float uDensity;
uniform float uSpeed;
uniform float uNoiseScale;
uniform vec3 uColorDark;
uniform vec3 uColorLight;
uniform sampler2D uSmokeTex;

uniform vec2 uMouse;              // NDC -1..1
uniform float uMouseRadius;       // in UV space approx
uniform float uMouseStrength;     // warp intensity
uniform float uMouseHoleStrength; // local thinning
uniform float uMouseWarp;         // irregularity amount

varying vec2 vUv;

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 34.45);
    return fract(p.x * p.y);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);

    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));

    vec2 u = f * f * (3.0 - 2.0 * f);

    return mix(a, b, u.x)
    + (c - a) * u.y * (1.0 - u.x)
    + (d - b) * u.x * u.y;
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;

    for (int i = 0; i < 5; i++) {
        v += noise(p) * a;
        p *= 2.0;
        a *= 0.5;
    }

    return v;
}

void main() {
    vec2 uv = vUv;
    float t = uTime * uSpeed;

    // mouse NDC -> UV
    vec2 mouseUv = uMouse;

    // -----------------------------------------
    // mouse field distortion
    // -----------------------------------------
    vec2 diff = uv - mouseUv;

    float warp1 = fbm(uv * 6.0 + vec2(0.0, uTime * 0.015));
    float warp2 = fbm(uv.yx * 7.5 + vec2(4.7, -uTime * 0.012));

    vec2 warpedDiff = diff + (vec2(warp1, warp2) - 0.5) * uMouseWarp;

    float radiusJitter = 1.0 + (fbm(uv * 9.0 + 3.7) - 0.5) * 0.35;
    float localRadius = uMouseRadius * radiusJitter;

    float distMouse = length(warpedDiff);

    float mouseInfluence = 1.0 - smoothstep(0.0, localRadius, distMouse);
    mouseInfluence = pow(mouseInfluence, 1.35);

    float centerSoft = 1.0 - smoothstep(0.0, localRadius * 0.35, distMouse);
    float antiHole = 1.0 - centerSoft * 0.4;
    mouseInfluence *= antiHole;

    vec2 dir = distMouse > 0.0001 ? normalize(warpedDiff) : vec2(0.0);
    vec2 tangent = vec2(-dir.y, dir.x);

    float swirlNoise = fbm(uv * 10.0 + uTime * 0.012 + vec2(7.3, 1.1));
    float swirl = (swirlNoise - 0.5) * 1.2;

    // UV deformati localmente: più swirl che hole
    vec2 fogUv = uv;
    fogUv += dir * (uMouseStrength * 0.35 * mouseInfluence);
    fogUv += tangent * (uMouseStrength * swirl * mouseInfluence);

    // =========================================================
    // LAYER 1 - FOG BASE SOFT
    // =========================================================
    vec2 baseP = fogUv * uNoiseScale;
    baseP += vec2(t * 0.08, -t * 0.04);

    float baseN = fbm(baseP);
    baseN = smoothstep(0.25, 0.85, baseN);

    float baseVertical =
    smoothstep(0.0, 0.2, fogUv.y) *
    (1.0 - smoothstep(0.75, 1.0, fogUv.y));

    float baseHorizontal =
    smoothstep(0.0, 0.1, fogUv.x) *
    (1.0 - smoothstep(0.9, 1.0, fogUv.x));

    float baseFogShape = baseVertical * baseHorizontal;
    float baseFogVariation = mix(0.65, 1.0, baseN);
    float baseFog = baseFogShape * baseFogVariation;

    // =========================================================
    // LAYER 2 - FOG WARPED / SHAPED
    // =========================================================
    vec2 smP = fogUv * uNoiseScale;

    float smQx = fbm(smP + vec2(0.0,  t * 0.12));
    float smQy = fbm(smP + vec2(4.7, -t * 0.08));
    vec2 smQ = vec2(smQx, smQy);

    vec2 smR;
    smR.x = fbm(smP + smQ + vec2(1.7, 9.2) + t * 0.10);
    smR.y = fbm(smP + smQ + vec2(8.3, 2.8) - t * 0.07);

    float smRawN = fbm(smP + smR * 2.2);
    float smShapedN = smoothstep(0.35, 0.9, smRawN);

    float smVertical =
    smoothstep(0.0, 0.18, fogUv.y) *
    (1.0 - smoothstep(0.72, 1.0, fogUv.y));

    float smHorizontal =
    smoothstep(0.0, 0.08, fogUv.x) *
    (1.0 - smoothstep(0.92, 1.0, fogUv.x));

    float smFogShape = smVertical * smHorizontal;
    float smVariation = mix(0.68, 1.0, smShapedN);
    float smFog = smFogShape * smVariation;

    // =========================================================
    // MERGE
    // =========================================================
    float fog = mix(baseFog, smFog, 0.45);

    float topFade = 1.0 - smoothstep(0.55, 1.0, fogUv.y);
    fog *= topFade;

    // =========================================================
    // TEXTURE MASK
    // =========================================================
    vec2 texUv = fogUv * 1.35 + vec2(t * 0.015, -t * 0.01);
    float tex = texture2D(uSmokeTex, texUv).r;
    tex = smoothstep(0.35, 0.75, tex);

    float texMask = mix(0.15, 1.0, tex);

    // local thinning, ma non buco netto
    float localThin = 1.0 - mouseInfluence * uMouseHoleStrength;

    float alpha = fog * texMask * uDensity * uOpacity * localThin;
    alpha = clamp(alpha, 0.0, 1.0);

    // =========================================================
    // COLOR
    // =========================================================
    float colorMask = mix(baseN, smShapedN, 0.5);
    colorMask *= mix(0.85, 1.0, tex);

    // lieve brightening nel campo mouse
    colorMask += mouseInfluence * 0.05;

    vec3 fogColor = mix(uColorDark, uColorLight, clamp(colorMask, 0.0, 1.0));

    gl_FragColor = vec4(fogColor, alpha);
}
