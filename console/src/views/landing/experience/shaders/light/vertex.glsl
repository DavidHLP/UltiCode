uniform vec2  uResolution;
uniform float uSize;
uniform float uProgress;
uniform vec3  uColorA;
uniform vec3  uColorB;

attribute float aSize;

varying vec3  vColor;
varying float vCamFade;

attribute float aRnd;
attribute float aRnd2;
varying float vRnd;
varying float vRnd2;

uniform float uTime;
varying float vMaskY;
varying vec3 vN;

uniform vec2 uMouse;
uniform float uMouseRadius;
uniform float uMouseStrength;
uniform float uMouseDepthFalloff;

//stars
uniform float uProgressStars;
uniform float uStarSpread;
uniform float uStarHeight;
uniform float uStarTwinkle;

attribute vec3 aStarPosition;

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

    // ---------------------------------
    // 1) base column shape
    // ---------------------------------
    float heightMask = smoothstep(-0.2, 1.2, p.y);
    float pinch = mix(1.0, 0.22, heightMask);
    p.xz *= pinch;

    float baseSpread = smoothstep(0.25, -0.35, p.y);
    p.xz *= mix(1.0, 1.35, baseSpread);

    // ---------------------------------
    // 2) upward motion per-particle
    // ---------------------------------
    float speed = mix(0.005, 0.015, aRnd);
    float rise  = fract(aRnd2 + uTime * speed);

    float colHeight = 1.65;
    float yWrapped = (rise - 0.5) * colHeight;

    p.y += yWrapped;

    // ---------------------------------
    // 3) central attraction
    // ---------------------------------
    float radial = length(p.xz);

    float attract = smoothstep(-0.1, 1.0, p.y);
    float pull = mix(0.0, 0.45, attract);
    p.xz *= (1.0 - pull);

    // ---------------------------------
    // 4) subtle drift
    // ---------------------------------
    float t = uTime * 0.05;

    vec3 drift = vec3(
    simplexNoise3d(p * 2.2 + vec3(0.0, t, 0.0)),
    simplexNoise3d(p * 2.0 + vec3(11.7, t * 0.9, 3.1)),
    simplexNoise3d(p * 2.1 + vec3(23.4, t * 1.1, 7.9))
    );

    p += drift * mix(0.003, 0.012, aRnd);



    // ---------------------------------
    // 6) density / brightness mask
    // ---------------------------------
    radial = length(p.xz);

    float core = 1.0 - smoothstep(0.00, 0.10, radial);
    float mid  = 1.0 - smoothstep(0.08, 0.22, radial);
    float halo = 1.0 - smoothstep(0.18, 0.38, radial);

    float density = core * 1.2 + mid * 0.55 + halo * 0.18;

    float minY = -1.0;
    float maxY =  1.0;
    vMaskY = clamp((p.y - minY) / (maxY - minY), 0.0, 1.0);


    // ---------------------------------
    // 6.5) column -> starfield morph
    // ---------------------------------
    float starsP = smoothstep(0.0, 1.0, uProgressStars);

    vec3 starPos = aStarPosition;
    starPos.xz *= uStarSpread;
    starPos.y *= uStarHeight;

    // piccolo caos organico durante il volo verso il cielo
    float flyNoise = simplexNoise3d(vec3(
    position.xy * 2.0 + vec2(aRnd, aRnd2) * 7.0,
    uTime * 0.08
    ));

    vec3 flyOffset = vec3(
    simplexNoise3d(vec3(position.xy * 1.7 + 3.1, uTime * 0.05)),
    flyNoise,
    simplexNoise3d(vec3(position.yx * 1.9 + 9.4, uTime * 0.05))
    ) * 0.12 * (1.0 - starsP);

    // un po' di apertura radiale mentre salgono
    vec2 radialDir = length(position.xz) > 0.0001 ? normalize(position.xz) : vec2(0.0, 0.0);
    vec3 burst = vec3(radialDir.x, 0.35, radialDir.y) * (0.35 + aRnd * 0.45);
    burst *= smoothstep(0.05, 0.45, starsP) * (1.0 - smoothstep(0.55, 1.0, starsP));

    vec3 starMorphTarget = starPos + flyOffset + burst;


    float localDelay = mix(0.0, 0.35, 1.0 - clamp((p.y + 0.6) / 1.8, 0.0, 1.0));
    localDelay += aRnd * 0.12;

    float particleStarsP = smoothstep(localDelay, 1.0, starsP);
    p = mix(p, starMorphTarget, particleStarsP);



    // ---------------------------------
    // 6.6) subtle star motion
    // ---------------------------------
    float starsDriftP = smoothstep(0.75, 1.0, particleStarsP);

    vec3 starDrift = vec3(
    sin(uTime * 0.018 + aRnd * 6.2831),
    sin(uTime * 0.014 + aRnd2 * 6.2831),
    cos(uTime * 0.016 + (aRnd + aRnd2) * 6.2831)
    ) * 0.035;

    p += starDrift * starsDriftP;

    // ---------------------------------
    // 5) mouse interaction
    // ---------------------------------
    vec4 modelPositionPre = modelMatrix * vec4(p, 1.0);
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

    vec2 dir2 = distMouse > 0.0001 ? normalize(warpedDiff) : vec2(0.0, 0.0);
    vec2 tangent = vec2(-dir2.y, dir2.x);
    float swirl = (aRnd - 0.5) * 0.6;
    vec2 finalDir2 = normalize(dir2 + tangent * swirl);

    vec3 camRight = vec3(viewMatrix[0][0], viewMatrix[1][0], viewMatrix[2][0]);
    vec3 camUp    = vec3(viewMatrix[0][1], viewMatrix[1][1], viewMatrix[2][1]);

    vec3 screenPushWorld =
    normalize(camRight * finalDir2.x + camUp * finalDir2.y + vec3(0.0, 1.0, 0.0) * 0.15);

    float mouseJitter = mix(0.85, 1.15, aRnd);

    p += screenPushWorld * (uMouseStrength * mouseInfluence * depthAtt * mouseJitter);

    float flutterNoise = simplexNoise3d(vec3(
    p.xy * 2.5 + vec2(aRnd, aRnd2) * 10.0,
    0.8
    ));

    vec3 flutter =
    (camRight * cos(flutterNoise * 6.2831) +
    camUp    * sin(flutterNoise * 6.2831)) *
    (0.018 * mouseInfluence);

    p += flutter;



    // ---------------------------------
    // 7) transform
    // ---------------------------------
    vec4 modelPosition = modelMatrix * vec4(p, 1.0);
    vec4 viewPosition  = viewMatrix * modelPosition;
    gl_Position = projectionMatrix * viewPosition;

    float dist = -viewPosition.z;
    if (dist <= 0.0001) {
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
        gl_PointSize = 0.0;
        vCamFade = 0.0;
        vColor = uColorA;
        return;
    }

    vCamFade = smoothstep(0.2, 1.0, dist);

    // ---------------------------------
    // 8) point size
    // ---------------------------------
    float baseSize = mix(0.35, 1.8, pow(aSize, 1.8));

    float starMask = step(0.975, aRnd2) * 0.1;
    float starBoost = starMask * mix(1.8, 4.5, aRnd);

    float flicker = 1.0 + starMask * 0.18 * sin(uTime * 2.2 + aRnd * 20.0);

    float finalSize = (baseSize + starBoost) * flicker;

    gl_PointSize = finalSize * uSize * (uResolution.y / dist);


    float starPhase = smoothstep(0.6, 1.0, uProgressStars);

    float minSize = mix(1.0, 3.0, starPhase);

    gl_PointSize = clamp(gl_PointSize, minSize, 42.0);


    // ---------------------------------
    // 9) color
    // ---------------------------------
    float n = simplexNoise3d(p * 1.8 + vec3(0.0, uTime * 0.08, 0.0));
    float glow = density + n * 0.08;

    vColor = mix(uColorA, uColorB, clamp(glow, 0.0, 1.0));

    vN = normalize(normalMatrix * vec3(0.0, 1.0, 0.0));
}
