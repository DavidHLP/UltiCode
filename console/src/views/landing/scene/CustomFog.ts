import * as THREE from "three";
import vertexShader from "../shaders/fog.vertex.glsl?raw";
import fragmentShader from "../shaders/fog.fragment.glsl?raw";
import smokeUrl from "../assets/cloud.png";

/**
 * CustomFog — Vue/TS port of the reference landing's camera-following dual-plane
 * FBM fog billboard. Two planes (front/back) with domain-warped value-noise FBM,
 * a smoke-texture mask, and mouse UV-space raycast thinning.
 *
 * Per-plane params transcribed from reference CustomFog.js (front: opacity 0.015 /
 * density 0.36 / speed 0.111; back: 0.015 / 0.24 / 0.07, mouse warp ×1.1).
 */

export interface CustomFogOptions {
  scene: THREE.Scene;
  camera: THREE.PerspectiveCamera;
  /** Shared NDC mouse vector (-1..1). */
  mouse: THREE.Vector2;
  texture?: THREE.Texture;
}

interface PlaneParams {
  opacity: number;
  density: number;
  speed: number;
  noiseScale: number;
  mouse: THREE.Vector2;
  mouseStrength: number;
  mouseHole: number;
  mouseWarp: number;
}

export class CustomFog {
  readonly group: THREE.Group;
  private readonly camera: THREE.PerspectiveCamera;
  private readonly mouse: THREE.Vector2;
  private readonly raycaster = new THREE.Raycaster();
  private readonly frontMouse = new THREE.Vector2(9999, 9999);
  private readonly backMouse = new THREE.Vector2(9999, 9999);
  private readonly frontMaterial: THREE.ShaderMaterial;
  private readonly backMaterial: THREE.ShaderMaterial;
  private readonly frontMesh: THREE.Mesh;
  private readonly backMesh: THREE.Mesh;
  private readonly texture: THREE.Texture;
  private readonly camWorld = new THREE.Vector3();
  private readonly camForward = new THREE.Vector3();

  constructor(opts: CustomFogOptions) {
    this.camera = opts.camera;
    this.mouse = opts.mouse;
    this.group = new THREE.Group();
    opts.scene.add(this.group);

    this.texture = opts.texture ?? new THREE.TextureLoader().load(smokeUrl);
    this.texture.colorSpace = THREE.SRGBColorSpace;

    const geometry = new THREE.PlaneGeometry(9, 6, 1, 1);

    const make = (p: PlaneParams) =>
      new THREE.ShaderMaterial({
        vertexShader,
        fragmentShader,
        uniforms: {
          uTime: { value: 0 },
          uOpacity: { value: p.opacity },
          uDensity: { value: p.density },
          uSpeed: { value: p.speed },
          uNoiseScale: { value: p.noiseScale },
          uColorDark: { value: new THREE.Color("#0a0a0a") },
          uColorLight: { value: new THREE.Color("#1e1e1e") },
          uSmokeTex: { value: this.texture },
          uMouse: { value: p.mouse },
          uMouseRadius: { value: 0.22 },
          uMouseStrength: { value: p.mouseStrength },
          uMouseHoleStrength: { value: p.mouseHole },
          uMouseWarp: { value: p.mouseWarp },
        },
        transparent: true,
        depthWrite: false,
        depthTest: false,
        blending: THREE.NormalBlending,
      });

    this.frontMaterial = make({
      opacity: 0.015,
      density: 0.36,
      speed: 0.111,
      noiseScale: 2.5,
      mouse: this.frontMouse,
      mouseStrength: 0.6,
      mouseHole: 0.5,
      mouseWarp: 0.5,
    });
    this.backMaterial = make({
      opacity: 0.015,
      density: 0.24,
      speed: 0.07,
      noiseScale: 2.5,
      mouse: this.backMouse,
      mouseStrength: 0.6 * 0.7,
      mouseHole: 0.5 * 0.8,
      mouseWarp: 0.5 * 1.1,
    });

    this.frontMesh = new THREE.Mesh(geometry, this.frontMaterial);
    this.backMesh = new THREE.Mesh(geometry, this.backMaterial);
    this.backMesh.position.set(0, 0, -1.5);
    this.backMesh.scale.set(1.2, 1.2, 1);
    this.group.add(this.backMesh);
    this.group.add(this.frontMesh);
  }

  /** Per-frame: billboard-follow the camera, advance time, raycast mouse → UV thin. */
  update(time: number): void {
    this.camera.getWorldPosition(this.camWorld);
    // Billboard: place the fog ahead of the camera along its real forward axis
    // (a world-space offset would put it behind whenever the camera looks −Z).
    this.camera.getWorldDirection(this.camForward);
    this.group.position.copy(this.camWorld).addScaledVector(this.camForward, 5.0);
    this.group.position.y += 0.88;
    this.group.quaternion.copy(this.camera.quaternion);

    this.frontMaterial.uniforms.uTime.value = time;
    this.backMaterial.uniforms.uTime.value = time;
    this.traceMouse(this.frontMesh, this.frontMouse);
    this.traceMouse(this.backMesh, this.backMouse);
  }

  private traceMouse(mesh: THREE.Mesh, target: THREE.Vector2): void {
    this.raycaster.setFromCamera(this.mouse, this.camera);
    const hit = this.raycaster.intersectObject(mesh, false)[0];
    if (hit && hit.uv) target.set(hit.uv.x, hit.uv.y);
    else target.set(9999, 9999);
  }

  resize(): void {
    /* fog is screen-space billboard; no resize dependency */
  }

  dispose(): void {
    this.frontMesh.geometry.dispose();
    this.frontMaterial.dispose();
    this.backMaterial.dispose();
    this.texture.dispose();
    this.group.removeFromParent();
  }
}
