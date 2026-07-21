uniform vec2  uResolution;
uniform float uSize;
uniform float uProgress;

attribute vec3  aPositionTarget;
attribute float aSize;

varying vec3 vModelPos;

attribute float aRnd;
attribute float aRnd2;
varying float vRnd;
varying float vRnd2;

uniform float uTime;
uniform vec3 uArmFadeDir;
uniform float uArmFadeStart;
uniform float uArmFadeEnd;

attribute vec3 aNormal;
varying vec3 vN;

uniform float uFly;
uniform float uFlyAmp;
uniform vec3  uWind;

uniform vec2 uMouse;
uniform float uMouseRadius;
uniform float uMouseStrength;
uniform float uMouseDepthFalloff;

//	Simplex 3D Noise
vec4 permute(vec4 x){ return mod(((x*34.0)+1.0)*x, 289.0); }
vec4 taylorInvSqrt(vec4 r){ return 1.79284291400159 - 0.85373472095314 * r; }

float simplexNoise3d(vec3 v)
{
    const vec2  C = vec2(1.0/6.0, 1.0/3.0);
    const vec4  D = vec4(0.0, 0.5, 1.0, 2.0);

    vec3 i  = floor(v + dot(v, C.yyy));
    vec3 x0 = v - i + dot(i, C.xxx);

    vec3 g = step(x0.yzx, x0.xyz);
    vec3 l = 1.0 - g;
    vec3 i1 = min(g.xyz, l.zxy);
    vec3 i2 = max(g.xyz, l.zxy);

    vec3 x1 = x0 - i1 + 1.0 * C.xxx;
    vec3 x2 = x0 - i2 + 2.0 * C.xxx;
    vec3 x3 = x0 - 1.0 + 3.0 * C.xxx;

    i = mod(i, 289.0);
    vec4 p = permute(
    permute(
    permute(i.z + vec4(0.0, i1.z, i2.z, 1.0))
    + i.y + vec4(0.0, i1.y, i2.y, 1.0))
    + i.x + vec4(0.0, i1.x, i2.x, 1.0));

    float n_ = 1.0/7.0;
    vec3  ns = n_ * D.wyz - D.xzx;

    vec4 j = p - 49.0 * floor(p * ns.z * ns.z);

    vec4 x_ = floor(j * ns.z);
    vec4 y_ = floor(j - 7.0 * x_);

    vec4 x = x_ * ns.x + ns.yyyy;
    vec4 y = y_ * ns.x + ns.yyyy;
    vec4 h = 1.0 - abs(x) - abs(y);

    vec4 b0 = vec4(x.xy, y.xy);
    vec4 b1 = vec4(x.zw, y.zw);

    vec4 s0 = floor(b0) * 2.0 + 1.0;
    vec4 s1 = floor(b1) * 2.0 + 1.0;
    vec4 sh = -step(h, vec4(0.0));

    vec4 a0 = b0.xzyw + s0.xzyw * sh.xxyy;
    vec4 a1 = b1.xzyw + s1.xzyw * sh.zzww;

    vec3 p0 = vec3(a0.xy, h.x);
    vec3 p1 = vec3(a0.zw, h.y);
    vec3 p2 = vec3(a1.xy, h.z);
    vec3 p3 = vec3(a1.zw, h.w);

    vec4 norm = taylorInvSqrt(vec4(dot(p0,p0), dot(p1,p1), dot(p2,p2), dot(p3,p3)));
    p0 *= norm.x;
    p1 *= norm.y;
    p2 *= norm.z;
    p3 *= norm.w;

    vec4 m = max(0.6 - vec4(dot(x0,x0), dot(x1,x1), dot(x2,x2), dot(x3,x3)), 0.0);
    m = m * m;

    return 42.0 * dot(m*m, vec4(dot(p0,x0), dot(p1,x1), dot(p2,x2), dot(p3,x3)));
}

void main()
{
    vRnd  = aRnd;
    vRnd2 = aRnd2;

    vec3 p = position;

    // -------- Noise (stable per-particle timeline) --------
    float noiseOrigin = simplexNoise3d(position) * 2.0;
    vec3 positionTarget = aPositionTarget;
    float noiseTarget = simplexNoise3d(positionTarget) * 2.0;
    float noiseMix    = mix(noiseOrigin, noiseTarget, uProgress);
    float noise       = smoothstep(-1.0, 1.0, noiseMix);

    // -------- Direction mask: avambraccio -> dita --------
    vec3 armDir = normalize(uArmFadeDir);
    float armPos = dot(p, armDir);

    float fadeStart = min(uArmFadeStart, uArmFadeEnd);
    float fadeEnd   = max(uArmFadeStart, uArmFadeEnd);

    float t1 = 1.0 - smoothstep(fadeStart, fadeEnd, armPos);

    // -------- Staggered morph progress --------
    float duration = 0.35;
    float spatialDelay = t1 * 0.4;
    float noiseDelay = noise * 0.2;
    float delay = spatialDelay + noiseDelay;
    float endT = min(delay + duration, 1.0);
    float mp = smoothstep(delay, endT, uProgress);

    vec3 mixedPosition = mix(p, positionTarget, mp);

    // -------- Random motion --------
    float moveRnd = fract(sin(dot(position.xz + positionTarget.yx, vec2(91.173, 17.731))) * 43758.5453);
    float moveMask = step(0.5, moveRnd);

    float moveAmp  = mix(0.01, 0.02, aRnd2);
    float moveTime = uTime * 0.05 + aRnd * 10.0;

    vec3 noiseOffset = vec3(
    simplexNoise3d(vec3(-position.xy * 0.3, moveTime)),
    simplexNoise3d(vec3(-position.yz * 0.3, moveTime + 10.0)),
    simplexNoise3d(vec3(-position.zx * 0.3, moveTime + 20.0))
    );

    mixedPosition += noiseOffset * moveAmp * moveMask;

    // -------- Continuous falling motion --------
    float fallRnd   = fract(sin(dot(position.xy + positionTarget.xy, vec2(12.9898, 78.233))) * 43758.5453);
    float fallMask  = step(0.95, fallRnd);

    float fallSpeed = mix(0.6, 1.8, aRnd) * 0.003;
    float fallRange = mix(2.0, 6.0, aRnd2);
    float fallT     = fract(uTime * fallSpeed + aRnd2);

    mixedPosition.y -= fallT * fallRange * fallMask;

    // ---------------------------
    // MOUSE INTERACTION
    // ---------------------------

    vec4 modelPositionPre = modelMatrix * vec4(mixedPosition, 1.0);
    vec4 viewPositionPre  = viewMatrix * modelPositionPre;
    vec4 clipPre          = projectionMatrix * viewPositionPre;

    vec2 ndc = clipPre.xy / max(clipPre.w, 0.0001);

    vec2 diff = ndc - uMouse;

    float warp1 = simplexNoise3d(vec3(ndc * 3.0, aRnd * 10.0));
    float warp2 = simplexNoise3d(vec3(ndc.yx * 4.5 + vec2(7.3, 1.9), aRnd2 * 10.0));

    vec2 warpedDiff = diff + vec2(warp1, warp2) * 0.06;

    float radiusJitter = 1.0 + 0.18 * simplexNoise3d(vec3(ndc * 5.0, aRnd * 20.0));
    float localRadius = uMouseRadius * radiusJitter;

    float distMouse = length(warpedDiff);

    float mouseInfluence = 1.0 - smoothstep(0.0, localRadius, distMouse);
    mouseInfluence = pow(mouseInfluence, 1.35);

    float centerSoft = 1.0 - smoothstep(0.0, localRadius * 0.35, distMouse);
    float antiHole = 1.0 - centerSoft * 0.45;
    mouseInfluence *= antiHole;

    float depthAtt = 1.0 / (1.0 + max(-viewPositionPre.z, 0.0) * uMouseDepthFalloff * 0.15);

    vec3 modelNormal = normalize((modelMatrix * vec4(aNormal, 0.0)).xyz);

    vec2 dir2 = distMouse > 0.0001 ? normalize(warpedDiff) : vec2(0.0, 0.0);
    vec2 tangent = vec2(-dir2.y, dir2.x);
    float swirl = (aRnd - 0.5) * 0.6;
    vec2 finalDir2 = normalize(dir2 + tangent * swirl);

    vec3 camRight = vec3(viewMatrix[0][0], viewMatrix[1][0], viewMatrix[2][0]);
    vec3 camUp    = vec3(viewMatrix[0][1], viewMatrix[1][1], viewMatrix[2][1]);

    vec3 screenPushWorld =
    normalize(camRight * finalDir2.x + camUp * finalDir2.y + modelNormal * 0.35);

    float mouseJitter = mix(0.85, 1.15, aRnd);

    mixedPosition += screenPushWorld * (uMouseStrength * mouseInfluence * depthAtt * mouseJitter);
    mixedPosition += modelNormal * (uMouseStrength * 0.25 * mouseInfluence * depthAtt);

    float flutterNoise = simplexNoise3d(vec3(
    mixedPosition.xy * 2.5 + vec2(aRnd, aRnd2) * 10.0,
    0.8
    ));

    vec3 flutter =
    (camRight * cos(flutterNoise * 6.2831) +
    camUp    * sin(flutterNoise * 6.2831)) *
    (0.025 * mouseInfluence);

    mixedPosition += flutter;

    // -------- Transform to view space --------
    vec4 modelPosition = modelMatrix * vec4(mixedPosition, 1.0);
    vec4 viewPosition  = viewMatrix * modelPosition;

    vN = normalize(normalMatrix * aNormal);

    float dist = -viewPosition.z;

    gl_Position = projectionMatrix * viewPosition;

    float sizeRnd = mix(0.35, 1.35, aSize);
    sizeRnd *= mix(0.75, 1.25, vRnd);

    float px = uSize * sizeRnd;

    gl_PointSize = px * (uResolution.y / dist);
    gl_PointSize = clamp(gl_PointSize, 1.0, 140.0);

    vModelPos = mixedPosition;
}
