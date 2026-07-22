import * as THREE from "three";

/**
 * AwardsTunnel — faithful port of the reference Awards.js (aboutluca.com).
 *
 * Six textured PlaneGeometry cards ride a z-track (z = -index * 10.4, totalDepth
 * 52, travel 66.56 with 7.28 start/end offsets). The track is rendered to an
 * independent MSAA x4 WebGLRenderTarget, then composited as a full-screen quad
 * attached to the main camera via a screen-space shader that applies radial
 * distortion (x0.075) + tangential noise (x0.055) + chromatic aberration (x0.01).
 *
 * Adaptations from the reference (documented):
 *  - Asset discovery: the reference reads a DOM `#awards` / `<img>` source. The
 *    native landing has no such DOM, so an explicit six-path card manifest is
 *    passed and loaded directly (avoids a zero-card RT).
 *  - Tunnel framing: the reference renders the RT with the shared main camera,
 *    but its scroll-path points the main camera down +Z at the awards beat while
 *    the cards live at -Z (the activation timeline was not in the extracted
 *    source). A dedicated `awardsCamera` frames the tunnel so the cards are
 *    always visible; the screen quad still attaches to the main camera so the
 *    composite overlays the main view exactly as in the reference.
 *
 * Shaders (card + screen) are verbatim ports of the rights-secured source.
 */

const CARD_VERTEX = /* glsl */ `
varying vec2 vUv;
void main() {
    vUv = uv;
    gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
}
`;

const CARD_FRAGMENT = /* glsl */ `
varying vec2 vUv;
uniform sampler2D uMap;
uniform float uOpacity;
uniform float uGroupOpacity;
uniform float uImageIntensity;
void main() {
    vec4 tex = texture2D(uMap, vUv);
    float finalAlpha = tex.a * uOpacity * uGroupOpacity;
    gl_FragColor = vec4(tex.rgb * uImageIntensity, finalAlpha);
}
`;

const SCREEN_VERTEX = /* glsl */ `
varying vec2 vUv;
void main() {
    vUv = uv;
    gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
}
`;

const SCREEN_FRAGMENT = /* glsl */ `
varying vec2 vUv;
uniform sampler2D uSceneMap;
uniform vec2 uMouse;
uniform vec2 uResolution;
uniform float uTime;
uniform float uMouseRadius;
uniform float uMouseStrength;

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
    return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
}

void main() {
    vec2 ndc = vUv * 2.0 - 1.0;
    vec2 delta = ndc - uMouse;
    delta.x *= uResolution.x / max(uResolution.y, 1.0);
    float distMouse = length(delta);
    float mouseInfluence = 1.0 - smoothstep(0.0, uMouseRadius, distMouse);
    mouseInfluence = pow(mouseInfluence, 1.25);
    vec2 dirMouse = distMouse > 0.0001 ? normalize(ndc - uMouse) : vec2(0.0);
    vec2 tangentMouse = vec2(-dirMouse.y, dirMouse.x);
    float jitter = noise(vUv * 18.0 + uTime * 0.075) - 0.5;
    vec2 warp = dirMouse * mouseInfluence * uMouseStrength * 0.075;
    warp += tangentMouse * jitter * mouseInfluence * uMouseStrength * 0.055;
    vec2 sampleUv = clamp(vUv - warp, vec2(0.001), vec2(0.999));
    vec2 chroma = dirMouse * mouseInfluence * uMouseStrength * 0.01;
    vec4 base = texture2D(uSceneMap, sampleUv);
    float red = texture2D(uSceneMap, clamp(sampleUv - chroma, vec2(0.001), vec2(0.999))).r;
    float blue = texture2D(uSceneMap, clamp(sampleUv + chroma, vec2(0.001), vec2(0.999))).b;
    gl_FragColor = vec4(red, base.g, blue, base.a);
}
`;

export interface AwardCardDef {
  url: string;
}

export interface AwardsOptions {
  mouse: THREE.Vector2;
  cards: AwardCardDef[];
  active?: number;
  progress?: number;
  groupOpacity?: number;
  position?: THREE.Vector3;
  scale?: number;
}

interface AwardItem {
  mesh: THREE.Mesh<THREE.PlaneGeometry, THREE.ShaderMaterial>;
  basePosition: THREE.Vector3;
  aspect: number;
}

export class AwardsTunnel {
  // Track geometry config (reference Awards.js defaults / MainScene.js options).
  private readonly step = 10.4;
  private readonly cardHeight = 2.1;
  private readonly imageIntensity = 0.28;
  private readonly baseOpacity = 0.0;
  private readonly focusOpacity = 1.0;
  private readonly visibilitySpanRatio = 0.58;
  private readonly visibilitySoftRatio = 0.2;
  private readonly depthPeekOpacity = 0.18;
  private readonly depthPeekSpanRatio = 1.15;
  private readonly startOffsetRatio = 0.7;
  private readonly endOffsetRatio = 0.7;
  private readonly mouseRadius = 0.5;
  private readonly mouseStrength = 0.1;

  private groupOpacity: number;
  active: number;
  private progress: number;

  private readonly mouse: THREE.Vector2;
  private readonly textureLoader = new THREE.TextureLoader();
  private readonly clearColor = new THREE.Color();

  readonly renderScene = new THREE.Scene();
  readonly renderTarget: THREE.WebGLRenderTarget;
  /** Dedicated camera framing the tunnel (cards live at -Z, decoupled from the
   * scroll-driven main camera which points the wrong way at the awards beat). */
  readonly awardsCamera: THREE.PerspectiveCamera;
  private readonly group = new THREE.Group();
  private readonly track = new THREE.Group();

  private readonly screenGeometry = new THREE.PlaneGeometry(1, 1, 1, 1);
  private readonly screenMaterial: THREE.ShaderMaterial;
  readonly screenMesh: THREE.Mesh;

  private items: AwardItem[] = [];
  private totalDepth = 0;

  constructor(opts: AwardsOptions) {
    this.mouse = opts.mouse;
    this.active = THREE.MathUtils.clamp(opts.active ?? 0, 0, 1);
    this.progress = THREE.MathUtils.clamp(opts.progress ?? 0, 0, 1);
    this.groupOpacity = opts.groupOpacity ?? 0;

    this.renderTarget = new THREE.WebGLRenderTarget(1, 1, {
      depthBuffer: true,
      stencilBuffer: false,
      samples: 4,
    });
    this.renderTarget.texture.name = "AwardsGalleryRenderTarget";

    // Dedicated framing: sits just in front of the first card, looks down -Z
    // into the receding tunnel. FOV matches the main camera.
    this.awardsCamera = new THREE.PerspectiveCamera(40, 1, 0.1, 250);
    this.awardsCamera.position.set(0, 0, 0.001);
    this.awardsCamera.lookAt(0, 0, -25);

    this.group.name = "AwardsGallery";
    this.track.name = "AwardsGalleryTrack";
    this.group.add(this.track);
    this.group.position.copy(opts.position ?? new THREE.Vector3(0, 0, 0));
    this.group.scale.setScalar(opts.scale ?? 1);
    this.group.visible = this.active > 0.001;
    this.renderScene.add(this.group);

    this.screenMaterial = new THREE.ShaderMaterial({
      transparent: true,
      depthTest: false,
      depthWrite: false,
      uniforms: {
        uSceneMap: { value: this.renderTarget.texture },
        uMouse: { value: this.mouse },
        uResolution: { value: new THREE.Vector2(1, 1) },
        uTime: { value: 0 },
        uMouseRadius: { value: this.mouseRadius },
        uMouseStrength: { value: this.mouseStrength },
      },
      vertexShader: SCREEN_VERTEX,
      fragmentShader: SCREEN_FRAGMENT,
    });
    this.screenMesh = new THREE.Mesh(this.screenGeometry, this.screenMaterial);
    this.screenMesh.name = "AwardsGalleryScreenPass";
    this.screenMesh.frustumCulled = false;
    this.screenMesh.renderOrder = 9999;
    this.screenMesh.visible = this.active > 0.001;

    this.addObjects(opts.cards);
    this.resize(
      Math.max(1, window.innerWidth),
      Math.max(1, window.innerHeight),
      window.devicePixelRatio || 1,
    );
    this.updateGallery();
  }

  private addObjects(cards: AwardCardDef[]): void {
    cards.forEach((card, index) => {
      const texture = this.textureLoader.load(card.url, (tex) => {
        if (tex.image && tex.image.width && tex.image.height) {
          const loaded = tex.image.width / tex.image.height;
          item.aspect = loaded;
          mesh.scale.set(this.cardHeight * loaded, this.cardHeight, 1);
        }
      });
      texture.colorSpace = THREE.SRGBColorSpace;

      const geometry = new THREE.PlaneGeometry(1, 1, 24, 24);
      const material = new THREE.ShaderMaterial({
        transparent: true,
        depthWrite: false,
        side: THREE.DoubleSide,
        uniforms: {
          uMap: { value: texture },
          uOpacity: { value: 0 },
          uGroupOpacity: { value: this.groupOpacity },
          uImageIntensity: { value: this.imageIntensity },
        },
        vertexShader: CARD_VERTEX,
        fragmentShader: CARD_FRAGMENT,
      });
      const mesh = new THREE.Mesh(geometry, material);

      const basePosition = new THREE.Vector3(0, 0, -index * this.step);
      const aspect = 0.74;
      mesh.position.copy(basePosition);
      mesh.scale.set(this.cardHeight * aspect, this.cardHeight, 1);

      const item: AwardItem = { mesh, basePosition, aspect };
      this.items.push(item);
      this.track.add(mesh);
    });
    this.totalDepth = Math.max(0, (this.items.length - 1) * this.step);
  }

  setActive(value: number): void {
    this.active = THREE.MathUtils.clamp(value, 0, 1);
    this.group.visible = this.active > 0.001;
    this.screenMesh.visible = this.active > 0.001;
  }

  setGroupOpacity(value: number): void {
    this.groupOpacity = value;
    for (const item of this.items) {
      item.mesh.material.uniforms.uGroupOpacity.value = value;
    }
  }

  setProgress(value: number): void {
    this.progress = THREE.MathUtils.clamp(value, 0, 1);
    this.updateGallery();
  }

  update(time: number): void {
    if (!this.items.length || this.active <= 0.001) return;
    this.screenMaterial.uniforms.uTime.value = time;
    this.screenMaterial.uniforms.uMouseRadius.value = this.mouseRadius;
    this.screenMaterial.uniforms.uMouseStrength.value = this.mouseStrength;
    for (const item of this.items) {
      item.mesh.material.uniforms.uGroupOpacity.value = this.groupOpacity;
    }
    this.updateGallery();
  }

  private updateGallery(): void {
    const progress = THREE.MathUtils.clamp(this.progress, 0, 1);
    const startOffset = this.step * this.startOffsetRatio;
    const endOffset = this.step * this.endOffsetRatio;
    const travelDepth = this.totalDepth + startOffset + endOffset;
    const trackZ = -startOffset + progress * travelDepth;
    this.track.position.z = trackZ;

    for (const item of this.items) {
      const localZ = item.basePosition.z + trackZ;
      const distanceToFocus = Math.abs(localZ);
      const visibilityEnd = this.step * this.visibilitySpanRatio;
      const visibilityStart = visibilityEnd * this.visibilitySoftRatio;
      const focusT = 1 - THREE.MathUtils.smoothstep(
        distanceToFocus,
        visibilityStart,
        visibilityEnd,
      );
      const depthPeekT = 1 - THREE.MathUtils.smoothstep(
        distanceToFocus,
        visibilityEnd,
        this.step * this.depthPeekSpanRatio,
      );
      const behindT = localZ < 0 ? depthPeekT : 0;

      item.mesh.position.x = 0.5;
      const idx = this.items.indexOf(item);
      if (idx % 2) item.mesh.position.x = -0.5;
      item.mesh.position.z = item.basePosition.z;
      item.mesh.material.uniforms.uOpacity.value = Math.max(
        THREE.MathUtils.lerp(this.baseOpacity, this.focusOpacity, focusT),
        behindT * this.depthPeekOpacity,
      );
      item.mesh.renderOrder = Math.round(focusT * 100);
    }
  }

  /** Attach the screen quad to the main camera so the composite overlays the
   * main view (full-screen at distance 1, scaled to fill the FOV). */
  updateScreenPlane(camera: THREE.Camera): void {
    const persp = camera as THREE.PerspectiveCamera;
    const distance = 1;
    const fov = persp.fov ?? 40;
    const aspect = persp.aspect ?? 1;
    const height = 2 * Math.tan(THREE.MathUtils.degToRad(fov * 0.5)) * distance;
    const width = height * aspect;
    this.screenMesh.position.set(0, 0, -distance);
    this.screenMesh.scale.set(width, height, 1);
    if (this.screenMesh.parent !== camera) camera.add(this.screenMesh);
  }

  render(renderer: THREE.WebGLRenderer, camera: THREE.Camera): void {
    if (!this.items.length || this.active <= 0.001) return;
    this.updateScreenPlane(camera);
    this.awardsCamera.aspect = (camera as THREE.PerspectiveCamera).aspect ?? 1;
    this.awardsCamera.updateProjectionMatrix();
    this.group.updateMatrixWorld(true);

    const currentTarget = renderer.getRenderTarget();
    const currentClear = renderer.getClearColor(this.clearColor);
    const currentAlpha = renderer.getClearAlpha();
    const currentAuto = renderer.autoClear;

    renderer.autoClear = true;
    renderer.setRenderTarget(this.renderTarget);
    renderer.setClearColor(0x000000, 0);
    renderer.clear(true, true, true);
    renderer.render(this.renderScene, this.awardsCamera);
    renderer.setRenderTarget(currentTarget);
    renderer.setClearColor(currentClear, currentAlpha);
    renderer.autoClear = currentAuto;
  }

  resize(width: number, height: number, pixelRatio: number): void {
    const w = Math.max(1, width);
    const h = Math.max(1, height);
    const pr = Math.max(0.5, pixelRatio);
    this.renderTarget.setSize(Math.ceil(w * pr), Math.ceil(h * pr));
    this.screenMaterial.uniforms.uResolution.value.set(w, h);
    this.awardsCamera.aspect = w / h;
    this.awardsCamera.updateProjectionMatrix();
  }

  dispose(): void {
    for (const item of this.items) {
      item.mesh.geometry.dispose();
      const tex = item.mesh.material.uniforms.uMap.value as THREE.Texture | undefined;
      tex?.dispose?.();
      item.mesh.material.dispose();
    }
    this.items = [];
    this.group.parent?.remove(this.group);
    this.screenMesh.parent?.remove(this.screenMesh);
    this.screenGeometry.dispose();
    this.screenMaterial.dispose();
    this.renderTarget.dispose();
  }
}
