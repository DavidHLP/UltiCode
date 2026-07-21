uniform float uTime;
uniform sampler2D uTexture;

// master
uniform float uOpacity;        // 0..1
uniform float uGlow;           // 0..20 (moltiplica luminosità colore)
uniform float uAlpha;          // 0..2  (moltiplica alpha finale)

// shape
uniform float uCore;           // 0..0.5  (raggio core)
uniform float uCoreSoft;       // 0..0.5  (sfumatura core)
uniform float uHalo;           // 0..0.8  (raggio halo)
uniform float uHaloSoft;       // 0..0.8  (sfumatura halo)
uniform float uHaloStrength;   // 0..2    (quanto pesa l'halo)
uniform float uEdge;           // 0..1    (cutoff bordo sprite)
uniform float uEdgeSoft;       // 0..1    (softness bordo)

uniform vec3 uArmFadeDir;
uniform float uArmFadeStart;
uniform float uArmFadeEnd;

varying vec3 vModelPos;

varying float vRnd;
varying float vRnd2;
varying vec3 vN;

// lighting
uniform vec3  uLightDir;   // in view space, normalized
uniform float uBase;       // 0..0.2
uniform float uDiffuse;    // 0..1
uniform float uRim;        // 0..1
uniform float uSpec;       // 0..2
uniform float uSpecPow;    // 8..64
uniform float uLightWrap;  // 0..1

// --- helpers ---
float hash12(vec2 p){
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

mat2 rot(float a){
    float s = sin(a), c = cos(a);
    return mat2(c,-s,s,c);
}


float bayer4x4(vec2 p) {
    int x = int(mod(p.x, 4.0));
    int y = int(mod(p.y, 4.0));

    int index = x + y * 4;

    float dither[16];
    dither[0]=0.0; dither[1]=8.0; dither[2]=2.0; dither[3]=10.0;
    dither[4]=12.0; dither[5]=4.0; dither[6]=14.0; dither[7]=6.0;
    dither[8]=3.0; dither[9]=11.0; dither[10]=1.0; dither[11]=9.0;
    dither[12]=15.0; dither[13]=7.0; dither[14]=13.0; dither[15]=5.0;

    return dither[index] / 16.0;
}


void main()
{
    // base uv
    vec2 uv = gl_PointCoord;

    // --- UV WARP per-particle (toglie "timbro" rotondo uguale) ---
    vec2 pp = uv - 0.5;

    // rotazione stabile per particella
    float ang = vRnd * 6.2831853;
    pp = rot(ang) * pp;

    // warp "irregolare" (stabile + un filo animato)
    float w1 = hash12(pp * 7.0 + vRnd  * 10.0);
    float w2 = hash12(pp.yx * 11.0 + vRnd2 * 20.0);
    vec2 warp = (vec2(w1, w2) - 0.5);

    // strength varia per particella
    float warpStr = mix(0.12, 0.35, vRnd2);
    pp += warp * warpStr;

    uv = clamp(pp + 0.5, 0.0, 1.0);

    // sample texture (usiamo soprattutto alpha)
    vec4 t = texture2D(uTexture, uv);

    // --- core/halo/edge come prima (ma su d calcolata dopo warp) ---
    vec2 p  = uv - 0.5;
    float d = length(p);

    float coreInner = max(uCore - uCoreSoft, 0.0001);
    float coreOuter = uCore;
    float core = smoothstep(coreOuter, coreInner, d);

    float haloInner = max(uHalo - uHaloSoft, 0.0001);
    float haloOuter = uHalo;
    float halo = smoothstep(haloOuter, haloInner, d);
    halo *= uHaloStrength;

    float edgeInner = max(uEdge - uEdgeSoft, 0.0001);
    float edgeOuter = uEdge;
    float edge = smoothstep(edgeOuter, edgeInner, d);

    //Alpha
    float a = (core + halo) * edge;
    float outA = clamp(a, 0.0, 1.0);

    // shape alpha * texture alpha * controlli
    outA *= t.a;
    outA *= uOpacity * uAlpha;



    float armPos = dot(vModelPos, normalize(uArmFadeDir));
    float fadeStart = min(uArmFadeStart, uArmFadeEnd);
    float fadeEnd   = max(uArmFadeStart, uArmFadeEnd);

    float t1 = smoothstep(fadeStart, fadeEnd, armPos);
    float armFade = pow(t1, .2);



    // opzionale: taglia i residui super trasparenti
    if(outA < 0.01) discard;



    float g = hash12(gl_FragCoord.xy + vRnd * 1000.0);
    if (t.a < g * 0.9) discard;

    // --- fake lighting (granelli che prendono luce) ---
    vec3 N = normalize(vN);
    vec3 L = normalize(uLightDir);
    vec3 V = vec3(0.0, 0.0, 1.0);

    // diffuse wrap (più leggibile tipo sabbia)
    float ndl = dot(N, L);
    float diff = clamp((ndl + uLightWrap) / (1.0 + uLightWrap), 0.0, 1.0);

    // rim
    float rim = pow(1.0 - clamp(dot(N, V), 0.0, 1.0), 2.0);

    // spec (micro glints)
    vec3 H = normalize(L + V);
    float spec = pow(max(dot(N, H), 0.0), uSpecPow) * uSpec;

    // spezza lo spec: solo alcuni frammenti lo prendono
    float spMask = smoothstep(0.65, 1.0, hash12(gl_FragCoord.xy * 0.5 + vRnd2 * 999.0));
    spec *= spMask;

    // base “non nero”
    vec3 col = vec3(uBase);
    col += diff * uDiffuse;
    col += rim  * uRim;
    col += spec;

    col *= 0.1;
    col *= uGlow;





    float flow = hash12(vModelPos.xy * 4.0 * 0.2);
    armFade *= mix(0.85, 1.15, flow);


    col = max(col, vec3(0.02));

    gl_FragColor = vec4(col, outA)*armFade;







}
