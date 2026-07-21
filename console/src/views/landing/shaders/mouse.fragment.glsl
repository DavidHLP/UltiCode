uniform vec2 uTrail[32];
uniform float uTrailLength;
uniform float uSize;
uniform float uOpacity;
uniform float uAspect;
uniform float uFalloff;
uniform float uHeadBoost;
uniform float uTime;
uniform float uNoiseStrength;
uniform float uNoiseScale;
uniform float uNoiseSpeed;
uniform sampler2D uTexture;



varying vec2 vUv;

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
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

    return mix(a, b, u.x) +
    (c - a) * u.y * (1.0 - u.x) +
    (d - b) * u.x * u.y;
}

float blob(vec2 uv, vec2 p, float radius) {
    float d = length(uv - p);
    float x = d / radius;

    float core = exp(-x * x * 6.0);
    float halo = exp(-x * x * 1.8) * 0.3;

    return core + halo;
}

void main() {

    vec2 uv = vUv * 2.0 - 1.0;
    uv.x *= uAspect;

    float n = noise(uv * uNoiseScale + vec2(uTime * uNoiseSpeed, -uTime * uNoiseSpeed * 0.7));
    float noiseFactor = mix(1.0 - uNoiseStrength, 1.0, n);



    float alpha = 0.0;
    float activeCount = max(uTrailLength, 1.0);

    for (int i = 0; i < 31; i++) {
        float fi = float(i);
        float enabled = 1.0 - step(uTrailLength - 1.0, fi);

        vec2 p0 = uTrail[i];
        vec2 p1 = uTrail[i + 1];

        p0.x *= uAspect;
        p1.x *= uAspect;

        float t = 1.0 - (fi / max(uTrailLength, 1.0));
        t = clamp(t, 0.0, 1.0);

        float radius = uSize * mix(0.55, 1.0, t);
        float weight = pow(max(t, 0.0001), uFalloff);

        if (i == 0) {
            weight *= uHeadBoost;
        }

        float a = 0.0;

        a += blob(uv, mix(p0, p1, 0.0), radius);
        a += blob(uv, mix(p0, p1, 0.25), radius * 0.95);
        a += blob(uv, mix(p0, p1, 0.5), radius * 0.9);
        a += blob(uv, mix(p0, p1, 0.75), radius * 0.85);

        alpha += a * weight * enabled * 0.12;
    }

    alpha = clamp(alpha, 0.0, 1.0);

    float tailMask = 1.0 - smoothstep(0.2, 0.75, alpha);


    alpha *= mix(1.0, noiseFactor, tailMask);

    alpha = pow(alpha, 0.9);

    vec3 color = vec3(1.0);
    gl_FragColor = vec4(color * alpha, alpha * uOpacity);
}
