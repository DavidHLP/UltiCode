import LightV from "./shaders/light/vertex.glsl?raw";
import LightF from "./shaders/light/fragment.glsl?raw";
import * as THREE from "three";

export class ParticlesLight {
    constructor(data) {
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

        this.particles.maxCount = 1000;

        const posArray = new Float32Array(this.particles.maxCount * 3);

        for (let i = 0; i < this.particles.maxCount; i++) {
            const i3 = i * 3;

            const r = Math.pow(Math.random(), 0.5) * 0.3;
            const theta = Math.random() * Math.PI * 2.0;
            const phi = Math.acos((Math.random() * 2.0) - 1.0);

            const x = r * Math.sin(phi) * Math.cos(theta);
            const y = r * Math.cos(phi);
            const z = r * Math.sin(phi) * Math.sin(theta);

            posArray[i3 + 0] = x;
            posArray[i3 + 1] = y;
            posArray[i3 + 2] = z;
        }

        this.particles.positionAttr = new THREE.Float32BufferAttribute(posArray, 3);
        this.particles.positionAttr.setUsage(THREE.DynamicDrawUsage);

        const sizesArray = new Float32Array(this.particles.maxCount);
        const rndArray = new Float32Array(this.particles.maxCount);
        const rnd2Array = new Float32Array(this.particles.maxCount);

        for (let i = 0; i < this.particles.maxCount; i++) {
            sizesArray[i] = Math.pow(Math.random(), 2.2);
            rndArray[i] = Math.random();
            rnd2Array[i] = Math.random();
        }

        const starArray = new Float32Array(this.particles.maxCount * 3);

        for (let i = 0; i < this.particles.maxCount; i++) {
            const i3 = i * 3;

            const x = -8.5 + Math.random() * 15.0;
            const y = 2.25 + Math.random() * 3.6;
            const z = (Math.random() - 0.5) * 3.5;

            starArray[i3 + 0] = x;
            starArray[i3 + 1] = y;
            starArray[i3 + 2] = z;
        }

        this.particles.geometry = new THREE.BufferGeometry();
        this.particles.geometry.setAttribute("position", this.particles.positionAttr);
        this.particles.geometry.setAttribute("aSize", new THREE.Float32BufferAttribute(sizesArray, 1));
        this.particles.geometry.setAttribute("aRnd", new THREE.Float32BufferAttribute(rndArray, 1));
        this.particles.geometry.setAttribute("aRnd2", new THREE.Float32BufferAttribute(rnd2Array, 1));
        this.particles.geometry.setAttribute("aStarPosition",new THREE.Float32BufferAttribute(starArray, 3));
        this.particles.geometry.computeBoundingSphere();
        this.particles.drawCount = this.particles.maxCount;
        this.particles.geometry.setDrawRange(0, this.particles.drawCount);

        this.particles.colorA = "#8f8f8f";
        this.particles.colorB = "#ffffff";

        const VSA = LightV;
        const FSA = LightF;

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
                uColorA: { value: new THREE.Color(this.particles.colorA) },
                uColorB: { value: new THREE.Color(this.particles.colorB) },

                uTime: { value: 0 },

                uMouse: { value: this.mouseUniform },
                uMouseRadius: { value: 0.18 },
                uMouseStrength: { value: 0.08 },
                uMouseDepthFalloff: { value: 1.0 },

                uTexture: { value: this.particlesTexture },

                uOpacity: { value: 1.0 },
                uAlpha: { value: 1.0 },
                uGlow: { value: 1.0 },

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

                uLightDir: { value: new THREE.Vector3(0.4, 0.9, 0.2).normalize() },
                uBase: { value: 0.01 },
                uDiffuse: { value: 0.6 },
                uRim: { value: 0.35 },
                uSpec: { value: 1.2 },
                uSpecPow: { value: 40.0 },
                uLightWrap: { value: 0.25 },

                uMorphDir: { value: new THREE.Vector3(0, 0, 0) },
                uMorphFadeStart: { value: -2.0 },
                uMorphFadeEnd: { value: 2.0 },
                uHeightStart: { value: 0.0 },
                uHeightEnd: { value: 1.0 },
                uCollapseStrength: { value: 0.05 },

                //stars
                uProgressStars: { value: 0.0 },
                uStarSpread: { value: 2.0 },
                uStarHeight: { value: 1.0 },
                uStarTwinkle: { value: 1.0 },
            },
            blending: THREE.NormalBlending,
            depthWrite: false,
            depthTest: false,
            transparent: true
        });

        this.particles.points = new THREE.Points(
            this.particles.geometry,
            this.particles.material
        );
        this.particles.points.renderOrder = 999;
        this.particles.points.frustumCulled = false;
        this.particles.points.visible = this.active > 0.001;

        if (this.gui) {
            let gui = this.gui;
            const mat = this.particles.material;
            const u = mat.uniforms;
            const setU = (name) => (v) => { if (u[name]) u[name].value = Number(v); };

            gui.add(u.uProgress, "value")
                .min(0)
                .max(1)
                .step(0.001)
                .name("uProgress Light")
                .listen();

            const f = gui.addFolder("Lights Look");
            f.close();

            f.addColor(this.particles, "colorA")
                .onChange(() => { u.uColorA.value.set(this.particles.colorA); })
                .listen();

            f.addColor(this.particles, "colorB")
                .onChange(() => { u.uColorB.value.set(this.particles.colorB); })
                .listen();

            f.add(u.uOpacity, "value", 0, 1, 0.001).name("opacity").onChange(setU("uOpacity"));
            f.add(u.uAlpha, "value", 0, 4, 0.001).name("alpha mult").onChange(setU("uAlpha"));
            f.add(u.uGlow, "value", 0, 20, 0.01).name("glow").onChange(setU("uGlow")).listen();

            const fShape = f.addFolder("Shape");
            fShape.add(u.uCore, "value", 0.01, 0.35, 0.001).name("core radius").onChange(setU("uCore"));
            fShape.add(u.uCoreSoft, "value", 0.00, 0.35, 0.001).name("core soft").onChange(setU("uCoreSoft"));
            fShape.add(u.uHalo, "value", 0.05, 0.90, 0.001).name("halo radius").onChange(setU("uHalo"));
            fShape.add(u.uHaloSoft, "value", 0.00, 0.90, 0.001).name("halo soft").onChange(setU("uHaloSoft"));
            fShape.add(u.uHaloStrength, "value", 0.00, 2.00, 0.001).name("halo strength").onChange(setU("uHaloStrength"));

            const fEdge = f.addFolder("Edge");
            fEdge.add(u.uEdge, "value", 0.25, 0.80, 0.001).name("edge cutoff").onChange(setU("uEdge"));
            fEdge.add(u.uEdgeSoft, "value", 0.00, 0.30, 0.001).name("edge soft").onChange(setU("uEdgeSoft"));

            const fMouse = f.addFolder("Mouse");
            fMouse.add(u.uMouseRadius, "value", 0.01, 0.5, 0.001).name("radius").listen();
            fMouse.add(u.uMouseStrength, "value", 0.0, 1.0, 0.001).name("strength").listen();
            fMouse.add(u.uMouseDepthFalloff, "value", 0.0, 3.0, 0.001).name("depth falloff").listen();

            const fSize = f.addFolder("Size");
            if (u.uSize) {
                fSize.add(u.uSize, "value", 0, 3.0, 0.001).name("point size").onChange(setU("uSize"));
            }


            gui.add(u.uProgressStars, "value")
                .min(0)
                .max(1)
                .step(0.001)
                .name("uProgress Stars")
                .listen();

            const fStars = gui.addFolder("Stars");
            fStars.add(u.uStarSpread, "value", 0.2, 3.0, 0.001).name("spread");
            fStars.add(u.uStarHeight, "value", 0.2, 3.0, 0.001).name("height");
            fStars.add(u.uStarTwinkle, "value", 0.0, 3.0, 0.001).name("twinkle");

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
