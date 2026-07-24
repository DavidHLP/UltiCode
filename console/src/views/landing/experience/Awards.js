import * as THREE from "three";
import { gsap } from "gsap";

const AWARDS_VERTEX_SHADER = `
varying vec2 vUv;

void main() {
    vUv = uv;
    gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
}
`;

const AWARDS_FRAGMENT_SHADER = `
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

const AWARDS_SCREEN_VERTEX_SHADER = `
varying vec2 vUv;

void main() {
    vUv = uv;
    gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
}
`;

const AWARDS_SCREEN_FRAGMENT_SHADER = `
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

    return mix(a, b, u.x) +
           (c - a) * u.y * (1.0 - u.x) +
           (d - b) * u.x * u.y;
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

export class Awards {
    constructor(options = {}) {
        this.scene = options.scene || null;
        this.gui = options.gui || null;
        this.mouse = options.mouse || null;

        this.sourceSelector = options.sourceSelector || "#awards";
        this.position = options.position || new THREE.Vector3(0, 0, 0);
        this.rotation = options.rotation || new THREE.Euler(0, 0, 0);
        this.scaleValue = options.scale ?? 1;
        this.active = THREE.MathUtils.clamp(options.active ?? 1, 0, 1);

        this.progressState = {
            value: THREE.MathUtils.clamp(options.progress ?? 0, 0, 1)
        };

        this.onIndexChange = options.onIndexChange || null;

        this.step = options.step ?? 10.4;
        this.cardHeight = options.cardHeight ?? 2.1;
        this.sideOffset = options.sideOffset ?? 1.15;
        this.depthTilt = options.depthTilt ?? 0.12;
        this.baseOpacity = options.baseOpacity ?? 0.0;
        this.focusOpacity = options.focusOpacity ?? 1.0;
        this.imageIntensity = options.imageIntensity ?? 0.28;
        this.groupOpacity = options.groupOpacity ?? 0.0;
        this.visibilitySpanRatio = options.visibilitySpanRatio ?? 0.58;
        this.visibilitySoftRatio = options.visibilitySoftRatio ?? 0.2;
        this.depthPeekOpacity = options.depthPeekOpacity ?? 0.18;
        this.depthPeekSpanRatio = options.depthPeekSpanRatio ?? 1.15;
        this.startOffsetRatio = options.startOffsetRatio ?? 0.7;
        this.endOffsetRatio = options.endOffsetRatio ?? 0.7;
        this.mouseRadius = options.mouseRadius ?? 0.5;
        this.mouseStrength = options.mouseStrength ?? 0.1;
        this.renderScale = options.renderScale ?? 1;

        this.items = [];
        this.currentIndex = -1;
        this.totalDepth = 0;
        this.width = Math.max(1, window.innerWidth || 1);
        this.height = Math.max(1, window.innerHeight || 1);
        this.pixelRatio = 1;

        this.renderScene = new THREE.Scene();
        this.renderTarget = new THREE.WebGLRenderTarget(1, 1, {
            depthBuffer: true,
            stencilBuffer: false,
            samples: 4
        });
        this.renderTarget.texture.name = "AwardsGalleryRenderTarget";
        this._clearColor = new THREE.Color();

        this.group = new THREE.Group();
        this.group.name = "AwardsGallery";

        this.track = new THREE.Group();
        this.track.name = "AwardsGalleryTrack";
        this.group.add(this.track);

        this.textureLoader = new THREE.TextureLoader();
        this.source = document.querySelector(this.sourceSelector);

        if (!this.source) {
            console.error(`Awards: source "${this.sourceSelector}" not found.`);
            return;
        }

        this.images = Array.from(this.source.querySelectorAll("img"));

        if (!this.images.length) {
            console.error(`Awards: no images found inside "${this.sourceSelector}".`);
            return;
        }

        this.addObjects();

        this.group.position.copy(this.position);
        this.group.rotation.copy(this.rotation);
        this.group.scale.setScalar(this.scaleValue);
        this.group.visible = this.active > 0.001;
        this.renderScene.add(this.group);

        this.setupScreenPass();

        this.updateGallery(0, true);
    }

    setupScreenPass() {
        this.screenGeometry = new THREE.PlaneGeometry(1, 1, 1, 1);
        this.screenMaterial = new THREE.ShaderMaterial({
            transparent: true,
            depthTest: false,
            depthWrite: false,
            uniforms: {
                uSceneMap: { value: this.renderTarget.texture },
                uMouse: { value: this.mouse || new THREE.Vector2(9999, 9999) },
                uResolution: { value: new THREE.Vector2(this.width, this.height) },
                uTime: { value: 0.0 },
                uMouseRadius: { value: this.mouseRadius },
                uMouseStrength: { value: this.mouseStrength }
            },
            vertexShader: AWARDS_SCREEN_VERTEX_SHADER,
            fragmentShader: AWARDS_SCREEN_FRAGMENT_SHADER
        });

        this.screenMesh = new THREE.Mesh(this.screenGeometry, this.screenMaterial);
        this.screenMesh.name = "AwardsGalleryScreenPass";
        this.screenMesh.frustumCulled = false;
        this.screenMesh.renderOrder = 9999;
        this.screenMesh.visible = this.active > 0.001;
    }

    addObjects() {
        const centeredCount = (this.images.length - 1) * 0.5;

        this.images.forEach((img, index) => {
            const texture = this.textureLoader.load(img.currentSrc || img.src);
            texture.colorSpace = THREE.SRGBColorSpace;

            const geometry = new THREE.PlaneGeometry(1, 1, 24, 24);
            const material = new THREE.ShaderMaterial({
                transparent: true,
                depthWrite: false,
                side: THREE.DoubleSide,
                uniforms: {
                    uMap: { value: texture },
                    uOpacity: { value: 0.0 },
                    uGroupOpacity: { value: this.groupOpacity },
                    uImageIntensity: { value: this.imageIntensity },
                    uTime: { value: 0.0 },
                    uMouse: { value: this.mouse || new THREE.Vector2(9999, 9999) },
                    uMouseRadius: { value: this.mouseRadius },
                    uMouseStrength: { value: this.mouseStrength }
                },
                vertexShader: AWARDS_VERTEX_SHADER,
                fragmentShader: AWARDS_FRAGMENT_SHADER
            });

            const mesh = new THREE.Mesh(geometry, material);

            const centeredIndex = index - centeredCount;
            const side = index % 2 === 0 ? -1 : 1;

            const basePosition = new THREE.Vector3(
                0,
                0,
                -index * this.step
            );

            const baseRotation = new THREE.Euler(
                0,
                0,
                0
            );

            const aspect = img.naturalWidth && img.naturalHeight
                ? img.naturalWidth / img.naturalHeight
                : 0.74;

            mesh.position.copy(basePosition);
            mesh.rotation.copy(baseRotation);
            mesh.scale.set(this.cardHeight * aspect, this.cardHeight, 1);

            const item = {
                img,
                mesh,
                basePosition,
                baseRotation,
                title: img.dataset.title || String(index + 1),
                aspect
            };

            this.items.push(item);
            this.track.add(mesh);

            texture.onUpdate = null;
            if (texture.image && texture.image.width && texture.image.height) {
                const loadedAspect = texture.image.width / texture.image.height;
                item.aspect = loadedAspect;
                mesh.scale.set(this.cardHeight * loadedAspect, this.cardHeight, 1);
            }
        });

        this.totalDepth = Math.max(0, (this.items.length - 1) * this.step);
    }

    setActive(value) {
        this.active = THREE.MathUtils.clamp(value, 0, 1);
        this.group.visible = this.active > 0.001;
        if (this.screenMesh) {
            this.screenMesh.visible = this.active > 0.001;
        }
    }

    setGroupOpacity(value) {
        this.groupOpacity = value;
        this.items.forEach((item) => {
            item.mesh.material.uniforms.uGroupOpacity.value = value;
        });
    }

    tweenGroupOpacity(value, duration = 0.8, ease = "power2.out") {
        gsap.to(this, {
            groupOpacity: value,
            duration,
            ease,
            onUpdate: () => {
                this.setGroupOpacity(this.groupOpacity);
            }
        });
    }

    setPosition(x, y, z) {
        this.group.position.set(x, y, z);
    }

    setRotation(x, y, z) {
        this.group.rotation.set(x, y, z);
    }

    setScale(value) {
        this.scaleValue = value;
        this.group.scale.setScalar(value);
    }

    resize(width = window.innerWidth, height = window.innerHeight, pixelRatio = window.devicePixelRatio || 1) {
        if (!this.renderTarget || !this.screenMaterial) return;

        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        this.pixelRatio = Math.max(0.5, pixelRatio) * this.renderScale;

        const targetWidth = Math.max(1, Math.ceil(this.width * this.pixelRatio));
        const targetHeight = Math.max(1, Math.ceil(this.height * this.pixelRatio));

        this.renderTarget.setSize(targetWidth, targetHeight);
        this.screenMaterial.uniforms.uResolution.value.set(this.width, this.height);
    }

    updateScreenPlane(camera) {
        if (!camera || !this.screenMesh) return;

        const distance = 1;
        const height = 2 * Math.tan(THREE.MathUtils.degToRad(camera.fov * 0.5)) * distance;
        const width = height * camera.aspect;

        this.screenMesh.position.set(0, 0, -distance);
        this.screenMesh.scale.set(width, height, 1);

        if (this.screenMesh.parent !== camera) {
            camera.add(this.screenMesh);
        }
    }

    render(renderer, camera) {
        if (!renderer || !camera || !this.items.length || this.active <= 0.001) return;

        this.updateScreenPlane(camera);
        camera.updateWorldMatrix(true, false);
        this.group.updateMatrixWorld(true);

        const currentRenderTarget = renderer.getRenderTarget();
        const currentClearColor = renderer.getClearColor(this._clearColor);
        const currentClearAlpha = renderer.getClearAlpha();
        const currentAutoClear = renderer.autoClear;

        renderer.autoClear = true;
        renderer.setRenderTarget(this.renderTarget);
        renderer.setClearColor(0x000000, 0);
        renderer.clear(true, true, true);
        renderer.render(this.renderScene, camera);
        renderer.setRenderTarget(currentRenderTarget);
        renderer.setClearColor(currentClearColor, currentClearAlpha);
        renderer.autoClear = currentAutoClear;
    }

    setProgress(progress) {
        this.progressState.value = THREE.MathUtils.clamp(progress, 0, 1);
        this.updateGallery(0, true);
    }

    tweenProgress(progress, duration = 0.8, ease = "power2.out") {
        gsap.to(this.progressState, {
            value: THREE.MathUtils.clamp(progress, 0, 1),
            duration,
            ease
        });
    }

    update(time = 0, force = false) {
        if (!this.items.length) return;
        if (this.active <= 0.001) return;

        this.items.forEach((item) => {
            item.mesh.material.uniforms.uTime.value = time;
            item.mesh.material.uniforms.uGroupOpacity.value = this.groupOpacity;
        });

        this.screenMaterial.uniforms.uTime.value = time;
        this.screenMaterial.uniforms.uMouseRadius.value = this.mouseRadius;
        this.screenMaterial.uniforms.uMouseStrength.value = this.mouseStrength;

        this.updateGallery(time, force);
    }

    updateGallery(time = 0, force = false) {
        const progress = THREE.MathUtils.clamp(this.progressState.value, 0, 1);
        const startOffset = this.step * this.startOffsetRatio;
        const endOffset = this.step * this.endOffsetRatio;
        const travelDepth = this.totalDepth + startOffset + endOffset;
        const trackZ = -startOffset + progress * travelDepth;
        let bestIndex = 0;
        let bestDistance = Infinity;

        this.track.position.z = trackZ;

        this.items.forEach((item, index) => {
            const localZ = item.basePosition.z + trackZ;
            const distanceToFocus = Math.abs(localZ);
            const visibilityEnd = this.step * this.visibilitySpanRatio;
            const visibilityStart = visibilityEnd * this.visibilitySoftRatio;
            const focusT = 1 - THREE.MathUtils.smoothstep(
                distanceToFocus,
                visibilityStart,
                visibilityEnd
            );
            const depthPeekT = 1 - THREE.MathUtils.smoothstep(
                distanceToFocus,
                visibilityEnd,
                this.step * this.depthPeekSpanRatio
            );
            const behindT = localZ < 0 ? depthPeekT : 0;

            if (distanceToFocus < bestDistance) {
                bestDistance = distanceToFocus;
                bestIndex = index;
            }


            item.mesh.position.x = .5;
            if (index % 2) {
                item.mesh.position.x = -.5;
            }
            item.mesh.position.z = item.basePosition.z;
            item.mesh.material.uniforms.uOpacity.value = Math.max(
                THREE.MathUtils.lerp(
                    this.baseOpacity,
                    this.focusOpacity,
                    focusT
                ),
                behindT * this.depthPeekOpacity
            );



            item.mesh.renderOrder = Math.round(focusT * 100);
        });

        if ((force || bestIndex !== this.currentIndex) && this.onIndexChange) {
            this.onIndexChange(this.items[bestIndex], bestIndex);
        }

        this.currentIndex = bestIndex;
    }

    destroy() {
        this.items.forEach((item) => {
            item.mesh.geometry.dispose();
            item.mesh.material.uniforms.uMap.value?.dispose?.();
            item.mesh.material.dispose();
        });

        this.items = [];

        if (this.group.parent) {
            this.group.parent.remove(this.group);
        }

        if (this.screenMesh?.parent) {
            this.screenMesh.parent.remove(this.screenMesh);
        }

        this.screenGeometry?.dispose();
        this.screenMaterial?.dispose();
        this.renderTarget?.dispose();
    }
}
