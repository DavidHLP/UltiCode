uniform vec2  uResolution;
uniform float uSize;
uniform float uProgress;

uniform vec3  uColorA;
uniform vec3  uColorB;

attribute vec3  aPositionTarget;

attribute float aSize;

varying vec3  vColor;
varying float vCamFade;
varying float vEdgeFade;

uniform vec2 uDesertMin;
uniform vec2 uDesertMax;
uniform float uEdgeFadeWidth;

uniform vec3 uMorphDir;
uniform float uMorphFadeStart;
uniform float uMorphFadeEnd;

uniform float uHeightStart;
uniform float uHeightEnd;
uniform float uCollapseStrength;

attribute float aRnd;
attribute float aRnd2;
varying float vRnd;
varying float vRnd2;
varying float vNoise;

uniform float uTime;

attribute vec3 aNormal;
varying vec3 vN;

uniform vec2 uMouse;
uniform float uMouseRadius;
uniform float uMouseStrength;
uniform float uMouseDepthFalloff;

uniform float uSplitProgress;
uniform float uMirrorGap;

uniform float uBlackHoleProgress;
uniform vec3 uBlackHoleCenter;
uniform float uBlackHoleRadius;
uniform float uBlackHoleSpin;
uniform float uBlackHoleDepth;
uniform vec3 uBlackHoleDepthDir;
uniform float uBlackHoleTunnelRadius;
uniform float uBlackHoleTunnelThickness;
uniform float uBlackHoleSpiralSpeed;
uniform float uBlackHoleOrbitTime;
uniform float uBlackHoleSpiralPull;

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

    float noiseOrigin = simplexNoise3d(position * 0.2);
    float noiseTarget = simplexNoise3d(aPositionTarget * 0.2);

    float noiseOrigin2 = simplexNoise3d(position) * 2.0;
    vec3 tmp = aPositionTarget;
    float noiseTarget2 = simplexNoise3d(tmp) * 2.0;
    float noiseMix2    = mix(noiseOrigin2, noiseTarget2, uProgress);
    float noise2       = smoothstep(-1.0, 1.0, noiseMix2);

    float noiseMix     = mix(noiseOrigin, noiseTarget, uProgress);
    float noise        = smoothstep(-1.0, 1.0, noiseMix);

    vec3 morphDir = normalize(uMorphDir);
    float axisPos = dot(position, morphDir);

    float fadeStart = min(uMorphFadeStart, uMorphFadeEnd);
    float fadeEnd   = max(uMorphFadeStart, uMorphFadeEnd);

    float dirMask = 1.0 - smoothstep(fadeStart, fadeEnd, axisPos);

    float heightMask = smoothstep(uHeightStart, uHeightEnd, position.y);
    float heightDelay = (1.0 - heightMask) * 0.14;

    float duration = 0.34;

    float spatialDelay = dirMask * 0.40;
    float noiseDelay   = noise2 * 0.16;

    float rnd = fract(vRnd * 13.37 + vRnd2);
    rnd = floor(rnd * 18.0) / 18.0;
    float randomDelay = rnd * 0.05;

    float delay = spatialDelay + heightDelay + noiseDelay + randomDelay;
    float endT  = min(delay + duration, 1.0);

    float mp = smoothstep(delay, endT, uProgress);

    tmp.y *= noise+vRnd+0.4;

    vec3 mixedPosition = mix(position, tmp, mp);

    float collapse = sin(mp * 3.1415926);
    mixedPosition.y -= collapse * uCollapseStrength;

    float fallRnd   = fract(sin(dot(position.xy + aPositionTarget.xy, vec2(12.9898, 78.233))) * 43758.5453);
    float fallMask  = step(0.996, fallRnd);

    float fallSpeed = mix(0.6, 1.8, aRnd) * 0.003;
    float fallRange = mix(2.0, 6.0, aRnd2);
    float fallT     = fract(uTime * fallSpeed + aRnd2);

    mixedPosition.y += fallT * fallRange * fallMask;

    float moveRnd = fract(sin(dot(position.xz + aPositionTarget.yx, vec2(91.173, 17.731))) * 43758.5453);
    float moveMask = step(0.5, moveRnd);

    float moveAmp  = mix(0.01, 0.04, aRnd2);
    float moveTime = uTime * 0.05 + aRnd * 10.0;

    vec3 noiseOffset = vec3(
    simplexNoise3d(vec3(position.xy * 0.3, moveTime)),
    simplexNoise3d(vec3(position.yz * 0.3, moveTime + 10.0)),
    simplexNoise3d(vec3(position.zx * 0.3, moveTime + 20.0))
    );

    mixedPosition += noiseOffset * moveAmp * moveMask;





    // ---------------------------
    // SPLIT DOWN MIRRORED
    // ---------------------------

    float splitMirrorX = 0.0;

    float splitRnd = floor(fract(aRnd * 17.13 + aRnd2 * 31.77) * 2.0);
    float splitMask = splitRnd;

    vec3 lowerPosition = mixedPosition;

    // specchio laterale della shape morphata
    lowerPosition.y = splitMirrorX - (mixedPosition.y - splitMirrorX);

    // separa la copia specchiata lungo l'asse orizzontale
    lowerPosition.y += uMirrorGap;

    // ---------------------------------
    // delayed transition
    // ---------------------------------

    float splitNoise = simplexNoise3d(vec3(
    mixedPosition.xz * 0.18,
    13.7 + aRnd * 5.0
    ));
    splitNoise = smoothstep(-1.0, 1.0, splitNoise);

    float splitRndDelay = fract(aRnd * 23.17 + aRnd2 * 41.93);
    splitRndDelay = floor(splitRndDelay * 12.0) / 12.0;

    float splitHeightMask = smoothstep(-2.0, 3.0, mixedPosition.y);
    float splitHeightDelay = (1.0 - splitHeightMask) * 0.18;

    float splitSpatial = smoothstep(-8.0, 8.0, mixedPosition.x) * 0.22;
    float splitNoiseDelay = splitNoise * 0.18;
    float splitRandomDelay = splitRndDelay * 0.14;

    float splitDelay = splitSpatial + splitNoiseDelay + splitRandomDelay + splitHeightDelay;
    float splitDuration = 0.34;
    float splitEnd = min(splitDelay + splitDuration, 1.0);

    float splitT = smoothstep(splitDelay, splitEnd, uSplitProgress) * splitMask;

    // piccolo arco durante il trasferimento
    float splitArc = sin(splitT * 3.1415926);
    lowerPosition.y += splitArc * 0.35;

    // mix finale
    mixedPosition = mix(mixedPosition, lowerPosition, splitT);


    // ---------------------------
    // BLACK HOLE
    // ---------------------------

    float blackHoleNoise = simplexNoise3d(vec3(
    position.xz * 0.14,
    31.7 + aRnd * 5.0
    ));
    blackHoleNoise = smoothstep(-1.0, 1.0, blackHoleNoise);

    float blackHoleRndDelay = fract(aRnd * 29.71 + aRnd2 * 11.43);
    blackHoleRndDelay = floor(blackHoleRndDelay * 14.0) / 14.0;

    float blackHoleDelay = blackHoleNoise * 0.20 + blackHoleRndDelay * 0.16;
    float blackHoleDuration = 0.48;
    float blackHoleEnd = min(blackHoleDelay + blackHoleDuration, 1.0);

    float blackHoleT = smoothstep(blackHoleDelay, blackHoleEnd, uBlackHoleProgress);
    blackHoleT = blackHoleT * blackHoleT * (3.0 - 2.0 * blackHoleT);

    vec3 blackHoleLocal = mixedPosition - uBlackHoleCenter;

    float blackHoleReach = 1.0 - smoothstep(uBlackHoleRadius, uBlackHoleRadius + 6.0, length(blackHoleLocal));
    float blackHoleAmount = blackHoleT * blackHoleReach;


    // ---------------------------
    // MOUSE INTERACTION
    // ---------------------------

    vec4 modelPositionPre = modelMatrix * vec4(mixedPosition, 1.0);
    vec4 viewPositionPre  = viewMatrix * modelPositionPre;
    vec4 clipPre          = projectionMatrix * viewPositionPre;

    vec2 ndc = clipPre.xy / max(clipPre.w, 0.0001);


    vec2 diff = ndc - uMouse;

    // distort
    float warp1 = simplexNoise3d(vec3(ndc * 3.0, aRnd * 10.0));
    float warp2 = simplexNoise3d(vec3(ndc.yx * 4.5 + vec2(7.3, 1.9), aRnd2 * 10.0));

    vec2 warpedDiff = diff + vec2(warp1, warp2) * 0.06;

    float radiusJitter = 1.0 + 0.18 * simplexNoise3d(vec3(ndc * 5.0, aRnd * 20.0));
    float localRadius = uMouseRadius * radiusJitter;

    float distMouse = length(warpedDiff);

    float mouseInfluence = 1.0 - smoothstep(0.0, localRadius, distMouse);
    mouseInfluence = pow(mouseInfluence, 1.35);

    // anti-hole
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

    // flutter locale per non svuotare troppo
    float flutterNoise = simplexNoise3d(vec3(
    mixedPosition.xy * 2.5 + vec2(aRnd, aRnd2) * 10.0,
    0.8
    ));

    vec3 flutter =
    (camRight * cos(flutterNoise * 6.2831) +
    camUp    * sin(flutterNoise * 6.2831)) *
    (0.025 * mouseInfluence);

    mixedPosition += flutter;







    // ---------------------------
    // transform finale
    // ---------------------------

    vec4 modelPosition = modelMatrix * vec4(mixedPosition, 1.0);

    vec3 blackHoleAxis = length(uBlackHoleDepthDir) > 0.0001 ? normalize(uBlackHoleDepthDir) : vec3(0.0, 0.0, -1.0);
    vec3 blackHoleRef = abs(blackHoleAxis.y) < 0.9 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0);
    vec3 blackHoleSideA = normalize(cross(blackHoleRef, blackHoleAxis));
    vec3 blackHoleSideB = normalize(cross(blackHoleAxis, blackHoleSideA));

    vec3 blackHoleCenterWorld = (modelMatrix * vec4(uBlackHoleCenter, 1.0)).xyz;
    vec3 blackHoleTunnelWorld = modelPosition.xyz - blackHoleCenterWorld;
    float blackHoleAxial = dot(blackHoleTunnelWorld, blackHoleAxis);
    vec3 blackHoleRadial = blackHoleTunnelWorld - blackHoleAxis * blackHoleAxial;
    float blackHoleRadialDist = length(blackHoleRadial);

    vec3 blackHoleRadialDir = blackHoleRadialDist > 0.0001
    ? blackHoleRadial / blackHoleRadialDist
    : normalize(blackHoleSideA * cos(aRnd * 6.2831853) + blackHoleSideB * sin(aRnd * 6.2831853));

    float blackHoleTunnelT = pow(blackHoleAmount, 1.15);
    float blackHoleAngle = atan(dot(blackHoleRadialDir, blackHoleSideB), dot(blackHoleRadialDir, blackHoleSideA));
    float blackHoleBaseAxial = blackHoleAxial;
    float blackHoleBaseRadius = blackHoleRadialDist;
    float blackHoleRadiusN = clamp(blackHoleBaseRadius / max(uBlackHoleRadius, 0.001), 0.0, 1.0);

    float blackHoleShellNoise = simplexNoise3d(vec3(position.xy * 0.2, 81.3 + aRnd * 3.0));
    float blackHoleShellRadius = uBlackHoleTunnelRadius + blackHoleShellNoise * uBlackHoleTunnelThickness;
    blackHoleShellRadius += (aRnd - 0.5) * uBlackHoleTunnelThickness;

    float blackHoleLane = aRnd * 0.82 + aRnd2 * 0.18 + blackHoleNoise * 0.035;
    blackHoleLane = floor(blackHoleLane * 18.0) / 18.0;

    float blackHoleVortexT = smoothstep(0.02, 0.54, uBlackHoleProgress) * blackHoleReach;
    blackHoleVortexT = blackHoleVortexT * blackHoleVortexT * (3.0 - 2.0 * blackHoleVortexT);

    float blackHoleFormT = smoothstep(0.34, 0.70, uBlackHoleProgress) * blackHoleReach;
    blackHoleFormT = max(blackHoleFormT, blackHoleTunnelT * 0.82);

    float blackHoleOrbitT = 1.0;
    float blackHolePullBias = mix(0.72, 1.18, aRnd2);
    float blackHoleInward = uBlackHoleSpiralPull * blackHoleFormT * blackHoleOrbitT * blackHolePullBias;
    blackHoleInward = clamp(blackHoleInward, 0.0, 0.82);

    float blackHoleTargetRadius = mix(blackHoleBaseRadius, blackHoleShellRadius, blackHoleFormT);
    blackHoleTargetRadius *= 1.0 - blackHoleInward;
    blackHoleTargetRadius = max(0.1, blackHoleTargetRadius);

    float blackHoleSpiralAngle =
    blackHoleAngle
    + uBlackHoleSpin * 6.2831853 * blackHoleVortexT * (1.12 - blackHoleRadiusN * 0.32)
    + (aRnd2 - 0.5) * 0.65 * blackHoleVortexT
    + uBlackHoleOrbitTime * 2.4 * blackHoleFormT * blackHoleOrbitT;

    float blackHoleDepthOffset = uBlackHoleDepth * blackHoleLane;
    blackHoleDepthOffset *= blackHoleFormT;
    blackHoleDepthOffset += uBlackHoleDepth * 0.28 * blackHoleVortexT;
    blackHoleDepthOffset += uBlackHoleDepth * 0.35 * blackHoleTunnelT;
    blackHoleDepthOffset += (aRnd - 0.5) * uBlackHoleDepth * 0.06 * blackHoleFormT;

    vec3 blackHoleRingDir =
    blackHoleSideA * cos(blackHoleSpiralAngle) +
    blackHoleSideB * sin(blackHoleSpiralAngle);

    vec3 blackHoleTunnelPosition =
    blackHoleCenterWorld +
    blackHoleAxis * (blackHoleBaseAxial + blackHoleDepthOffset) +
    blackHoleRingDir * blackHoleTargetRadius;

    modelPosition.xyz = blackHoleTunnelPosition;

    vec4 viewPosition  = viewMatrix * modelPosition;

    vN = normalize(normalMatrix * aNormal);

    float dist = -viewPosition.z;

    if (dist <= 0.0001) {
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
        gl_PointSize = 0.0;
        vCamFade = 0.0;
        vColor = mix(uColorA, uColorB, noise);
        return;
    }

    float nearFadeStart = 0.25;
    float nearFadeEnd   = 1.00;
    vCamFade = smoothstep(nearFadeStart, nearFadeEnd, dist);
    vCamFade *= mix(0.75, 1.0, vRnd);

    gl_Position = projectionMatrix * viewPosition;

    float sizeRnd = mix(0.35, 1.35, aSize);
    sizeRnd *= mix(0.75, 1.25, vRnd);

    float breathe = 1.0 + 0.06 * sin(uTime * 0.8 + vRnd2 * 10.0);

    float px = uSize * sizeRnd * breathe;
    px *= 1.0 + blackHoleAmount * 0.45;

    gl_PointSize = px * (uResolution.y / dist);
    gl_PointSize = clamp(gl_PointSize, 1.0, 140.0);

    vColor = mix(uColorA, uColorB, noise);

    vNoise = noise;

    vec2 p3 = mixedPosition.xz;

    float distLeft   = p3.x - uDesertMin.x;
    float distRight  = uDesertMax.x - p3.x;
    float distBottom = p3.y - uDesertMin.y;
    float distTop    = uDesertMax.y - p3.y;

    float edgeDist = min(min(distLeft, distRight), min(distBottom, distTop));
    vEdgeFade = smoothstep(0.0, uEdgeFadeWidth, edgeDist);
}
