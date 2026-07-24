import ParticlesV from "./shaders/particles/vertex.glsl?raw";
import ParticlesF from "./shaders/particles/fragment.glsl?raw";
import * as THREE from "three";
import { MeshSurfaceSampler } from "three/examples/jsm/math/MeshSurfaceSampler.js";
import gsap from "gsap";

export class ParticlesModel {
    constructor(data) {
        this.models = data.models;
        this.particlesTexture = data.particlesTexture;
        this.gui = data.gui;
        this.mouse = data.mouse;
        this.active = THREE.MathUtils.clamp(data.active ?? 1, 0, 1);

        this.particles = {};
        this.particles.index = 0;

        const sizes = {
            width: window.innerWidth,
            height: window.innerHeight,
            pixelRatio: Math.min(window.devicePixelRatio, 1.25)
        };

        const mesh = this.models.scene.getObjectByName("hand");

        if (
            !mesh ||
            !mesh.isMesh ||
            !mesh.geometry ||
            !mesh.geometry.attributes ||
            !mesh.geometry.attributes.position
        ) {
            console.error('Mesh "hand" non trovata o non valida', mesh);
            return;
        }

        const sampler = new MeshSurfaceSampler(mesh).build();

        this.particles.maxCount = parseInt(30000 / 2);



        const posArray = new Float32Array(this.particles.maxCount * 3);
        const nrmArray = new Float32Array(this.particles.maxCount * 3);

        const p = new THREE.Vector3();
        const n = new THREE.Vector3();

        const targetCount = Math.min(
            mesh.geometry.attributes.position.count,
            this.particles.maxCount
        );

        for (let i = 0; i < targetCount; i++) {
            sampler.sample(p, n);

            const i3 = i * 3;
            posArray[i3 + 0] = p.x;
            posArray[i3 + 1] = p.y;
            posArray[i3 + 2] = p.z;

            n.normalize();
            nrmArray[i3 + 0] = n.x;
            nrmArray[i3 + 1] = n.y;
            nrmArray[i3 + 2] = n.z;
        }

        for (let i = targetCount; i < this.particles.maxCount; i++) {
            const src = Math.floor(Math.random() * targetCount) * 3;
            const i3 = i * 3;

            posArray[i3 + 0] = posArray[src + 0];
            posArray[i3 + 1] = posArray[src + 1];
            posArray[i3 + 2] = posArray[src + 2];

            nrmArray[i3 + 0] = nrmArray[src + 0];
            nrmArray[i3 + 1] = nrmArray[src + 1];
            nrmArray[i3 + 2] = nrmArray[src + 2];
        }

        this.particles.positionAttr = new THREE.Float32BufferAttribute(posArray, 3);
        this.particles.normalAttr = new THREE.Float32BufferAttribute(nrmArray, 3);

        this.particles.positionAttr.setUsage(THREE.DynamicDrawUsage);
        this.particles.normalAttr.setUsage(THREE.DynamicDrawUsage);

        const sizesArray = new Float32Array(this.particles.maxCount);
        const rndArray = new Float32Array(this.particles.maxCount);
        const rnd2Array = new Float32Array(this.particles.maxCount);

        for (let i = 0; i < this.particles.maxCount; i++) {
            sizesArray[i] = Math.pow(Math.random(), 2.2);
            rndArray[i] = Math.random();
            rnd2Array[i] = Math.random();
        }

        this.particles.geometry = new THREE.BufferGeometry();
        this.particles.geometry.setAttribute("position", this.particles.positionAttr);
        this.particles.geometry.setAttribute("aNormal", this.particles.normalAttr);
        this.particles.geometry.setAttribute("aSize", new THREE.Float32BufferAttribute(sizesArray, 1));
        this.particles.geometry.setAttribute("aRnd", new THREE.Float32BufferAttribute(rndArray, 1));
        this.particles.geometry.setAttribute("aRnd2", new THREE.Float32BufferAttribute(rnd2Array, 1));

        this.particles.geometry.computeBoundingSphere();
        this.particles.drawCount = this.particles.maxCount;
        this.particles.geometry.setDrawRange(0, this.particles.drawCount);

        const targetArray = new Float32Array(this.particles.maxCount * 3);

        for (let i = 0; i < this.particles.maxCount; i++) {
            const i3 = i * 3;

            const x = (Math.random() - 0.5) * 2.0;
            const y = -1.25;
            const z = (Math.random() - 0.5) * 2.0;

            targetArray[i3 + 0] = x;
            targetArray[i3 + 1] = y;
            targetArray[i3 + 2] = z;
        }

        this.particles.geometry.setAttribute(
            "aPositionTarget",
            new THREE.Float32BufferAttribute(targetArray, 3)
        );

        const VSA = ParticlesV;
        const FSA = ParticlesF;

        this.mouseUniform = new THREE.Vector2(9999, 9999);

        this.particles.material = new THREE.ShaderMaterial({
            vertexShader: VSA,
            fragmentShader: FSA,
            uniforms: {
                uSize: { value: 0.02 },
                uResolution: {
                    value: new THREE.Vector2(
                        sizes.width * sizes.pixelRatio,
                        sizes.height * sizes.pixelRatio
                    )
                },
                uProgress: { value: 1.0 },

                uTime: { value: 0 },

                uMouse: { value: this.mouseUniform },
                uMouseRadius: { value: 0.22 },
                uMouseStrength: { value: 0.18 },
                uMouseDepthFalloff: { value: 1.0 },

                uTexture: { value: this.particlesTexture },

                uOpacity: { value: 1.0 },
                uAlpha: { value: 1.0 },
                uGlow: { value: 1.5 },

                uCore: { value: 0.35 },
                uCoreSoft: { value: 0.06 },
                uHalo: { value: 0.55 },
                uHaloSoft: { value: 0.25 },
                uHaloStrength: { value: 0.6 },
                uEdge: { value: 0.62 },
                uEdgeSoft: { value: 0.12 },

                uCoreBoost: { value: 0.0 },
                uHaloBoost: { value: 0.0 },
                uSaturation: { value: 1.15 },

                uArmFadeStart: { value: -0.860 },
                uArmFadeEnd: { value: 0.32 },
                uArmFadeDir: { value: new THREE.Vector3(-0.015, 0.845, 0) },

                uLightDir: { value: new THREE.Vector3(0.4, 0.9, 0.2).normalize() },
                uBase: { value: 0.01 },
                uDiffuse: { value: 0.6 },
                uRim: { value: 0.35 },
                uSpec: { value: 1.2 },
                uSpecPow: { value: 40.0 },
                uLightWrap: { value: 0.25 }
            },
            blending: THREE.NormalBlending,
            depthWrite: false,
            transparent: true
        });

        this.particles.points = new THREE.Points(
            this.particles.geometry,
            this.particles.material
        );
        this.particles.points.frustumCulled = false;
        this.particles.points.visible = this.active > 0.001;

        if (this.gui) {
            let gui = this.gui;
            const u = this.particles.material.uniforms;
            const setU = (name) => (v) => { if (u[name]) u[name].value = Number(v); };

            gui.add(u.uProgress, "value")
                .min(0)
                .max(1)
                .step(0.001)
                .name("uProgress M")
                .listen();

            const f = gui.addFolder("Particles model");
            f.close();

            f.add(u.uOpacity, "value", 0, 1, 0.001).name("opacity").onChange(setU("uOpacity"));
            f.add(u.uAlpha, "value", 0, 2, 0.001).name("alpha mult").onChange(setU("uAlpha"));
            f.add(u.uGlow, "value", 0, 20, 0.01).name("glow").onChange(setU("uGlow"));

            const fShape = f.addFolder("Shape");
            fShape.add(u.uCore, "value", 0.01, 0.35, 0.001).name("core radius").onChange(setU("uCore"));
            fShape.add(u.uCoreSoft, "value", 0.00, 0.35, 0.001).name("core soft").onChange(setU("uCoreSoft"));
            fShape.add(u.uHalo, "value", 0.05, 0.90, 0.001).name("halo radius").onChange(setU("uHalo"));
            fShape.add(u.uHaloSoft, "value", 0.00, 0.90, 0.001).name("halo soft").onChange(setU("uHaloSoft"));
            fShape.add(u.uHaloStrength, "value", 0.00, 2.00, 0.001).name("halo strength").onChange(setU("uHaloStrength"));

            const fAlpha = f.addFolder("Alpha");
            fAlpha.add(u.uArmFadeStart, "value", -10.0, 10.0, 0.001).name("arm fade start");
            fAlpha.add(u.uArmFadeEnd, "value", -10.0, 10.0, 0.001).name("arm fade end");
            fAlpha.add(u.uArmFadeDir.value, "x", -3, 3, 0.001).name("dir x");
            fAlpha.add(u.uArmFadeDir.value, "y", -3, 3, 0.001).name("dir y");
            fAlpha.add(u.uArmFadeDir.value, "z", -3, 3, 0.001).name("dir z");

            const fEdge = f.addFolder("Edge");
            fEdge.add(u.uEdge, "value", 0.25, 0.80, 0.001).name("edge cutoff").onChange(setU("uEdge"));
            fEdge.add(u.uEdgeSoft, "value", 0.00, 0.30, 0.001).name("edge soft").onChange(setU("uEdgeSoft"));

            const fMouse = f.addFolder("Mouse");
            fMouse.add(u.uMouseRadius, "value", 0.01, 0.5, 0.001).name("radius").listen();
            fMouse.add(u.uMouseStrength, "value", 0.0, 1.0, 0.001).name("strength").listen();
            fMouse.add(u.uMouseDepthFalloff, "value", 0.0, 3.0, 0.001).name("depth falloff").listen();

            const fSize = f.addFolder("Size");
            if (u.uSize) {
                fSize.add(u.uSize, "value", 0.0, 3.0, 0.001).name("point size").onChange(setU("uSize"));
            }
        }
    }

    setActive(value) {
        this.active = THREE.MathUtils.clamp(value, 0, 1);

        if (this.particles?.points) {
            this.particles.points.visible = this.active > 0.001;
        }
    }

    setParticleRatio(ratio) {
        if (!this.particles?.geometry) return;

        const nextCount = Math.max(
            1,
            Math.floor(this.particles.maxCount * THREE.MathUtils.clamp(ratio, 0, 1))
        );

        if (nextCount === this.particles.drawCount) return;

        this.particles.drawCount = nextCount;
        this.particles.geometry.setDrawRange(0, nextCount);
    }

    update(time) {
        if (!this.particles?.material) return;
        if (this.active <= 0.001) return;

        this.particles.material.uniforms.uTime.value = time;

        if (this.mouse) {
            this.mouseUniform.set(this.mouse.x, this.mouse.y);
        }
    }

    resize(pixelRatio = Math.min(window.devicePixelRatio, 1.25)) {
        if (!this.particles?.material) return;

        this.particles.material.uniforms.uResolution.value.set(
            window.innerWidth * pixelRatio,
            window.innerHeight * pixelRatio
        );
    }
}
