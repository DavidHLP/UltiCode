uniform float uTime;

varying vec2 vUv;
varying vec3 vWorldPos;

void main() {
    vUv = uv;

    vec3 pos = position;

    vec4 worldPos = modelMatrix * vec4(pos, 1.0);
    vWorldPos = worldPos.xyz;

    gl_Position = projectionMatrix * viewMatrix * worldPos;
}
