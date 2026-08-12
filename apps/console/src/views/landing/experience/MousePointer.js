import MouseV from "./shaders/mouse/vertex.glsl?raw";
import MouseF from "./shaders/mouse/fragment.glsl?raw";
import * as THREE from 'three';
import { SOLARIZED_PALETTE } from '@ulticode/design-system';

const VSA = MouseV;
const FSA = MouseF;

export class MousePointer {
    constructor(_options) {
        this.scene = _options.scene;
        this.camera = _options.camera;
        this.mouse = _options.mouse;
        this.gui = _options.gui;
        this.particlesTexture = _options.particlesTexture;

        this.MAX_TRAIL = 32;

        this.params = {
            size: 0.015,
            opacity: 1.0,
            trailLength: 20,
            lerp: 0.18,
            falloff: 1.25,
            headBoost: 1,
            noiseStrength: 0.08,
            noiseScale: 18.0,
            noiseSpeed: 0.12,
        };

        this.mouseSmooth = new THREE.Vector2(this.mouse.x, this.mouse.y);
        this.trail = [];

        this.setGeometry();
        this.setMaterial();
        this.setMesh();
        this.initTrail();
        this.setDebug();
    }

    setGeometry() {
        this.geometry = new THREE.PlaneGeometry(2, 2, 1, 1);
    }

    setMaterial() {
        const trailArray = [];

        for (let i = 0; i < this.MAX_TRAIL; i++) {
            trailArray.push(new THREE.Vector2(this.mouse.x, this.mouse.y));
        }

        if (this.particlesTexture) {
            this.particlesTexture.wrapS = THREE.ClampToEdgeWrapping;
            this.particlesTexture.wrapT = THREE.ClampToEdgeWrapping;
            this.particlesTexture.minFilter = THREE.LinearFilter;
            this.particlesTexture.magFilter = THREE.LinearFilter;
            this.particlesTexture.needsUpdate = true;
        }

        this.material = new THREE.ShaderMaterial({
            vertexShader: VSA,
            fragmentShader: FSA,
            transparent: true,
            depthWrite: false,
            depthTest: false,
            blending: THREE.AdditiveBlending,
            uniforms: {
                uTrail: { value: trailArray },
                uColor: { value: new THREE.Color(SOLARIZED_PALETTE.cyan) },
                uTrailLength: { value: this.params.trailLength },
                uSize: { value: this.params.size },
                uOpacity: { value: this.params.opacity },
                uAspect: { value: window.innerWidth / window.innerHeight },
                uFalloff: { value: this.params.falloff },
                uHeadBoost: { value: this.params.headBoost },
                uTime: { value: 0 },
                uNoiseStrength: { value: this.params.noiseStrength },
                uNoiseScale: { value: this.params.noiseScale },
                uNoiseSpeed: { value: this.params.noiseSpeed },
                uTexture: { value: this.particlesTexture || null },
            }
        });

        this.material.frustumCulled = false;
    }

    setMesh() {
        this.mesh = new THREE.Mesh(this.geometry, this.material);
        this.mesh.frustumCulled = false;
        this.scene.add(this.mesh);
    }

    initTrail() {
        this.trail.length = 0;

        for (let i = 0; i < this.MAX_TRAIL; i++) {
            this.trail.push(new THREE.Vector2(this.mouse.x, this.mouse.y));
        }

        for (let i = 0; i < this.MAX_TRAIL; i++) {
            this.material.uniforms.uTrail.value[i].copy(this.trail[i]);
        }
    }

    updateTrail() {
        this.mouseSmooth.lerp(this.mouse, this.params.lerp);

        this.trail.pop();
        this.trail.unshift(new THREE.Vector2(this.mouseSmooth.x, this.mouseSmooth.y));

        for (let i = 0; i < this.MAX_TRAIL; i++) {
            this.material.uniforms.uTrail.value[i].copy(this.trail[i]);
        }

        this.material.uniforms.uTrailLength.value = Math.min(this.params.trailLength, this.MAX_TRAIL);
    }

    update() {
        this.material.uniforms.uTime.value += 0.016;
        this.updateTrail();
    }

    resize() {
        this.material.uniforms.uAspect.value = window.innerWidth / window.innerHeight;
    }

    setSize(value) {
        this.params.size = value;
        this.material.uniforms.uSize.value = value;
    }

    refreshUniforms() {
        this.material.uniforms.uSize.value = this.params.size;
        this.material.uniforms.uOpacity.value = this.params.opacity;
        this.material.uniforms.uFalloff.value = this.params.falloff;
        this.material.uniforms.uHeadBoost.value = this.params.headBoost;
        this.material.uniforms.uTrailLength.value = Math.min(this.params.trailLength, this.MAX_TRAIL);
        this.material.uniforms.uNoiseStrength.value = this.params.noiseStrength;
        this.material.uniforms.uNoiseScale.value = this.params.noiseScale;
        this.material.uniforms.uNoiseSpeed.value = this.params.noiseSpeed;
    }

    setDebug() {
        if (!this.gui) return;

        const f = this.gui.addFolder('Mouse Pointer');

        f.add(this.params, 'size', 0.01, 0.4, 0.001).name('size').onChange(() => {
            this.refreshUniforms();
        });

        f.add(this.params, 'opacity', 0.0, 3.0, 0.01).name('opacity').onChange(() => {
            this.refreshUniforms();
        });

        f.add(this.params, 'trailLength', 1, this.MAX_TRAIL, 1).name('trailLength').onChange(() => {
            this.refreshUniforms();
        });

        f.add(this.params, 'lerp', 0.01, 1.0, 0.01).name('lerp');

        f.add(this.params, 'falloff', 0.2, 3.0, 0.01).name('falloff').onChange(() => {
            this.refreshUniforms();
        });

        f.add(this.params, 'headBoost', 0.0, 3.0, 0.01).name('headBoost').onChange(() => {
            this.refreshUniforms();
        });

        f.add(this.params, 'noiseStrength', 0.0, 0.4, 0.001).name('noiseStrength').onChange(() => {
            this.refreshUniforms();
        });

        f.add(this.params, 'noiseScale', 1.0, 60.0, 0.1).name('noiseScale').onChange(() => {
            this.refreshUniforms();
        });

        f.add(this.params, 'noiseSpeed', 0.0, 1.0, 0.001).name('noiseSpeed').onChange(() => {
            this.refreshUniforms();
        });
    }
}
