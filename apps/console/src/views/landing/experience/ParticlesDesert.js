import DesertV from "./shaders/desert/vertex.glsl?raw";
import DesertF from "./shaders/desert/fragment.glsl?raw";
import * as THREE from "three";
import { MeshSurfaceSampler } from "three/examples/jsm/math/MeshSurfaceSampler.js";
import gsap from "gsap";
import {isDesktop} from "./Utils";

export class ParticlesDesert {
    constructor(data) {
        this.models = data.models;
        this.particlesTexture = data.particlesTexture;
        this.gui = data.gui;

        this.mouse = data.mouse; // può essere object {x,y} o Vector2

        this.particles = {};
        this.particles.index = 0;

        const sizes = {
            width: window.innerWidth,
            height: window.innerHeight,
            pixelRatio: Math.min(window.devicePixelRatio, 1.25)
        };

        const mesh = this.models.scene.getObjectByName("desert");


        if (
            !mesh ||
            !mesh.isMesh ||
            !mesh.geometry ||
            !mesh.geometry.attributes ||
            !mesh.geometry.attributes.position
        ) {
            console.error('Mesh "desert" non trovata o non valida', mesh);
            return;
        }



        const sampler = new MeshSurfaceSampler(mesh).build();


        this.particles.maxCount = parseInt(mesh.geometry.attributes.position.count/2);

        console.log(this.particles.maxCount)

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

            const x = (Math.random() - 0.5) * 50.4;
            const y = -2.2;
            const z = (Math.random() - 0.5) * 50.4;

            targetArray[i3 + 0] = x;
            targetArray[i3 + 1] = y;
            targetArray[i3 + 2] = z;
        }







        this.particles.geometry.setAttribute(
            "aPositionTarget",
            new THREE.Float32BufferAttribute(targetArray, 3)
        );


        let tunnelRadius = 3.034;

        if (isDesktop()) {
            tunnelRadius = 7.034;

        }

        this.particles.colorA = "#757575";
        this.particles.colorB = "#ffffff";

        const VSA = DesertV;
        const FSA = DesertF;

        // uniform mouse robusto
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
                uProgress: { value: 1 },

                uColorA: { value: new THREE.Color(this.particles.colorA) },
                uColorB: { value: new THREE.Color(this.particles.colorB) },

                uCornerRadius : { value: 5 },

                uTime: { value: 0 },

                uMouse: { value: this.mouseUniform },
                uMouseRadius: { value: 0.22 },       // raggio influenza in NDC
                uMouseStrength: { value: 0.18 },     // forza spinta
                uMouseDepthFalloff: { value: 1.0 },  // attenua con distanza camera

                uTexture: { value: this.particlesTexture },

                uOpacity: { value: 1.0 },
                uAlpha: { value: 2.0 },
                uGlow: { value: 2.5 },

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

                uDesertMin: { value: new THREE.Vector2(-11.095, -8.591) },
                uDesertMax: { value: new THREE.Vector2(6.555, 15.099) },
                uEdgeFadeWidth: { value: 3.437 },


                uSplitProgress: { value: 0.0 },
                uMirrorGap: { value: 7. },

                uBlackHoleProgress: { value: 0.0 },
                uBlackHoleCenter: { value: new THREE.Vector3(-4., 3.165, 0.675) },
                uBlackHoleRadius: { value: 44.569 },
                uBlackHoleSpin: { value: .308 },
                uBlackHoleDepth: { value: 24.794 },
                uBlackHoleDepthDir: { value: new THREE.Vector3(0.0, 0.0, -1.0) },
                uBlackHoleTunnelRadius: { value: tunnelRadius },
                uBlackHoleTunnelThickness: { value: 3.348 },
                uBlackHoleSpiralSpeed: { value: 0.022 },
                uBlackHoleOrbitTime: { value: 0.1 },
                uBlackHoleSpiralPull: { value: 0.172 },
            },
            blending: THREE.NormalBlending,
            depthWrite: false,
            transparent: true,
            depthTest: false
        });

        this.particles.points = new THREE.Points(
            this.particles.geometry,
            this.particles.material
        );
        this.particles.points.frustumCulled = false;

        if (this.gui) {
            const gui = this.gui;
            const u = this.particles.material.uniforms;

            const setU = (name) => (v) => {
                if (u[name]) u[name].value = Number(v);
            };

            gui.add(u.uProgress, "value").min(0).max(1).step(0.001).name("uProgress D").listen();


            const f = gui.addFolder("Desert Look");
            f.close();

            f.addColor(this.particles, "colorA")
                .onChange(() => u.uColorA.value.set(this.particles.colorA))
                .listen();

            f.addColor(this.particles, "colorB")
                .onChange(() => u.uColorB.value.set(this.particles.colorB))
                .listen();

            f.add(u.uOpacity, "value", 0, 1, 0.001).name("opacity").onChange(setU("uOpacity"));
            f.add(u.uAlpha, "value", 0, 4, 0.001).name("alpha mult").onChange(setU("uAlpha"));
            f.add(u.uGlow, "value", 0, 20, 0.01).name("glow").onChange(setU("uGlow")).listen();

            const fShape = f.addFolder("Shape");
            fShape.add(u.uCore, "value", 0.01, 0.35, 0.001).name("core radius");
            fShape.add(u.uCoreSoft, "value", 0.0, 0.35, 0.001).name("core soft");
            fShape.add(u.uHalo, "value", 0.05, 0.9, 0.001).name("halo radius");
            fShape.add(u.uHaloSoft, "value", 0.0, 0.9, 0.001).name("halo soft");
            fShape.add(u.uHaloStrength, "value", 0.0, 2.0, 0.001).name("halo strength");

            const fEdge = f.addFolder("Edge");
            fEdge.add(u.uDesertMin.value, "x", -50, 50, 0.001).name("desertMin x").listen();
            fEdge.add(u.uDesertMin.value, "y", -50, 50, 0.001).name("desertMin z").listen();
            fEdge.add(u.uDesertMax.value, "x", -50, 50, 0.001).name("desertMax x").listen();
            fEdge.add(u.uDesertMax.value, "y", -50, 50, 0.001).name("desertMax z").listen();
            fEdge.add(u.uEdgeFadeWidth, "value", 0.0, 20.0, 0.001).name("edgeFadeWidth").listen();
            fEdge.open();

            const fMouse = f.addFolder("Mouse");
            fMouse.add(u.uMouseRadius, "value", 0.01, 0.5, 0.001).name("radius").listen();
            fMouse.add(u.uMouseStrength, "value", 0.0, 1.0, 0.001).name("strength").listen();
            fMouse.add(u.uMouseDepthFalloff, "value", 0.0, 3.0, 0.001).name("depth falloff").listen();

            const fSize = f.addFolder("Size");
            fSize.add(u.uSize, "value", 0, 3.0, 0.001).name("point size").listen();



            const fSplit = f.addFolder("Split Mirror");
            fSplit.add(u.uSplitProgress, "value", 0, 1, 0.001).name("split progress").listen();
            fSplit.add(u.uMirrorGap, "value", 0, 10, 0.001).name("mirror gap").listen();

            const fBlackHole = f.addFolder("Black Hole");
            fBlackHole.add(u.uBlackHoleProgress, "value", 0, 1, 0.001).name("progress").listen();
            fBlackHole.add(u.uBlackHoleCenter.value, "x", -30, 30, 0.001).name("center x").listen();
            fBlackHole.add(u.uBlackHoleCenter.value, "y", -20, 20, 0.001).name("center y").listen();
            fBlackHole.add(u.uBlackHoleCenter.value, "z", -30, 30, 0.001).name("center z").listen();
            fBlackHole.add(u.uBlackHoleRadius, "value", 0, 50, 0.001).name("radius").listen();
            fBlackHole.add(u.uBlackHoleSpin, "value", 0, 4, 0.001).name("spin").listen();
            fBlackHole.add(u.uBlackHoleDepth, "value", -40, 40, 0.001).name("depth").listen();
            fBlackHole.add(u.uBlackHoleTunnelRadius, "value", 0.2, 12, 0.001).name("tunnel radius").listen();
            fBlackHole.add(u.uBlackHoleTunnelThickness, "value", 0.0, 4, 0.001).name("tunnel thickness").listen();
            fBlackHole.add(u.uBlackHoleSpiralSpeed, "value", 0.0, 0.12, 0.001).name("spiral speed").listen();
            fBlackHole.add(u.uBlackHoleOrbitTime, "value", 0.0, 20, 0.001).name("orbit time").listen();
            fBlackHole.add(u.uBlackHoleSpiralPull, "value", 0.0, 0.4, 0.001).name("spiral pull").listen();
        }
    }

    update(time) {
        if (!this.particles?.material) return;

        const uniforms = this.particles.material.uniforms;
        const previousTime = this.previousTime ?? time;
        const delta = Math.min(Math.max(time - previousTime, 0), 0.5);
        this.previousTime = time;

        uniforms.uTime.value = time;


        uniforms.uBlackHoleOrbitTime.value += delta * uniforms.uBlackHoleSpiralSpeed.value;


        // supporta sia object {x,y} sia THREE.Vector2
        if (this.mouse) {
            this.mouseUniform.set(this.mouse.x, this.mouse.y);
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

    resize(pixelRatio = Math.min(window.devicePixelRatio, 1.25)) {
        if (!this.particles?.material) return;

        this.particles.material.uniforms.uResolution.value.set(
            window.innerWidth * pixelRatio,
            window.innerHeight * pixelRatio
        );
    }
}
