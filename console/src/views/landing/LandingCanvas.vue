<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from "vue";

/**
 * Landing-page Three.js backdrop.
 *
 * Rendered as a non-interactive absolute-positioned canvas behind the hero
 * text and workbench. Skipped entirely when:
 *
 * - WebGL is unavailable (jsdom, headless CI, ancient GPU blocked).
 * - The user has `prefers-reduced-motion: reduce` set; we just paint a
 *   single static frame so the layout still has the field but never animates.
 *
 * Visual: a wireframe icosahedron + a wireframe torus floating in a slow
 * orbit, lit by one HemisphereLight. No postprocessing, no assets, no
 * physics; aim for under 200 vertices total so the bundle stays light and
 * 60fps on integrated GPUs.
 */

const canvasRef = ref<HTMLCanvasElement | null>(null);
let cancelled = false;

const hasWebGL = (host: HTMLCanvasElement): boolean => {
  try {
    const ctx =
      host.getContext("webgl2") ||
      host.getContext("webgl") ||
      host.getContext("experimental-webgl");
    return Boolean(ctx);
  } catch {
    return false;
  }
};

const prefersReducedMotion = (): boolean =>
  typeof window !== "undefined" &&
  typeof window.matchMedia !== "undefined" &&
  window.matchMedia("(prefers-reduced-motion: reduce)").matches;

onMounted(async () => {
  if (typeof window === "undefined") return;
  const host = canvasRef.value;
  if (!host) return;

  if (!hasWebGL(host)) {
    host.dataset.threeFallback = "no-webgl";
    return;
  }

  const THREE = await import("three");

  const parent = host.parentElement;
  if (cancelled || !parent) return;

  const cssWidth = parent.clientWidth || 600;
  const cssHeight = parent.clientHeight || 400;
  host.style.width = `${cssWidth}px`;
  host.style.height = `${cssHeight}px`;

  const scene = new THREE.Scene();
  scene.background = null;

  const camera = new THREE.PerspectiveCamera(
    45,
    cssWidth / Math.max(cssHeight, 1),
    0.1,
    100,
  );
  camera.position.set(0, 0.4, 6.5);
  camera.lookAt(0, 0, 0);

  const renderer = new THREE.WebGLRenderer({
    canvas: host,
    alpha: true,
    antialias: true,
  });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
  renderer.setClearColor(0x000000, 0);

  const torus = new THREE.LineSegments(
    new THREE.WireframeGeometry(new THREE.TorusGeometry(1.6, 0.5, 16, 32)),
    new THREE.LineBasicMaterial({
      color: new THREE.Color(0x4cc2ff),
      transparent: true,
      opacity: 0.7,
    }),
  );
  const ico = new THREE.LineSegments(
    new THREE.WireframeGeometry(new THREE.IcosahedronGeometry(1.1, 1)),
    new THREE.LineBasicMaterial({
      color: new THREE.Color(0xff6b35),
      transparent: true,
      opacity: 0.85,
    }),
  );

  const ring = new THREE.Group();
  ring.add(torus);
  ring.add(ico);
  scene.add(ring);

  const core = new THREE.LineSegments(
    new THREE.WireframeGeometry(new THREE.IcosahedronGeometry(0.35, 0)),
    new THREE.LineBasicMaterial({
      color: new THREE.Color(0xe0e6f0),
      transparent: true,
      opacity: 0.55,
    }),
  );
  scene.add(core);

  scene.add(new THREE.HemisphereLight(0xa6c8ff, 0x1a1f2c, 0.6));

  const reduced = prefersReducedMotion();
  let raf = 0;
  const start = performance.now();

  const frame = () => {
    if (cancelled) return;
    const elapsed = (performance.now() - start) / 1000;
    ring.rotation.x = elapsed * 0.18;
    ring.rotation.y = elapsed * 0.24;
    core.rotation.x = -elapsed * 0.4;
    core.rotation.y = -elapsed * 0.3;
    renderer.render(scene, camera);
    if (!reduced) {
      raf = requestAnimationFrame(frame);
    }
  };

  const onResize = () => {
    if (cancelled || !parent) return;
    const w = parent.clientWidth || 600;
    const h = parent.clientHeight || 400;
    camera.aspect = w / Math.max(h, 1);
    camera.updateProjectionMatrix();
    renderer.setSize(w, h, false);
    host.style.width = `${w}px`;
    host.style.height = `${h}px`;
  };

  frame();
  window.addEventListener("resize", onResize, { passive: true });

  onBeforeUnmount(() => {
    cancelled = true;
    if (raf) cancelAnimationFrame(raf);
    window.removeEventListener("resize", onResize);
    scene.traverse((node) => {
      const mesh = node as {
        geometry?: { dispose?: () => void };
        material?: { dispose?: () => void } | { dispose?: () => void }[];
      };
      if (mesh.geometry && typeof mesh.geometry.dispose === "function") {
        mesh.geometry.dispose();
      }
      const disposeMaterial = (material: unknown) => {
        if (
          material &&
          typeof material === "object" &&
          "dispose" in material &&
          typeof (material as { dispose?: unknown }).dispose === "function"
        ) {
          (material as { dispose: () => void }).dispose();
        }
      };
      if (Array.isArray(mesh.material)) {
        mesh.material.forEach(disposeMaterial);
      } else {
        disposeMaterial(mesh.material);
      }
    });
    renderer.dispose();
  });
});
</script>

<template>
  <canvas
    ref="canvasRef"
    class="hero-3d-canvas"
    aria-hidden="true"
    data-testid="hero-3d-canvas"
  ></canvas>
</template>
