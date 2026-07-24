import FogV from "./shaders/fog/vertex.glsl?raw";
import FogF from "./shaders/fog/fragment.glsl?raw";
import * as THREE from "three";

export class CustomFog {
    constructor(data) {
        this.scene = data.scene;
        this.camera = data.camera;
        this.gui = data.gui;
        this.texture = data.texture;
        this.mouse = data.mouse;

        this.group = new THREE.Group();
        this.scene.add(this.group);

        this.raycaster = new THREE.Raycaster();
        this.mouseNDC = new THREE.Vector2();

        this.camrot = data.camrot;

        this.params = {
            x: 0,
            y: 0.88,
            z: 0.5,

            width: 9,
            height: 6,

            opacityFront: 0.015,
            opacityBack: 0.015,

            densityFront: 0.36,
            densityBack: 0.24,

            speedFront: 0.111,
            speedBack: 0.07,

            noiseScaleFront: 2.5,
            noiseScaleBack: 2.5,

            frontDistance: 3.5,
            backDistance: 6.25,

            colorDark: "#000000",
            colorLight: "#ffffff",

            mouseRadius: 3.5,
            mouseStrength: 0.35,
            mouseHoleStrength: 0.22,
            mouseWarp: 0.06
        };

        this._camWorldPos = new THREE.Vector3();
        this._camWorldQuat = new THREE.Quaternion();
        this._camForward = new THREE.Vector3();

        this.frontMouseUniform = new THREE.Vector2(9999, 9999);
        this.backMouseUniform = new THREE.Vector2(9999, 9999);

        const VSA = FogV;
        const FSA = FogF;

        this.geometry = new THREE.PlaneGeometry(this.params.width, this.params.height, 1, 1);

        const createFogMaterial = ({
                                       opacity,
                                       density,
                                       speed,
                                       noiseScale,
                                       mouseUniform
                                   }) => {
            return new THREE.ShaderMaterial({
                vertexShader: VSA,
                fragmentShader: FSA,
                uniforms: {
                    uTime: { value: 0 },
                    uOpacity: { value: opacity },
                    uDensity: { value: density },
                    uSpeed: { value: speed },
                    uNoiseScale: { value: noiseScale },
                    uSmokeTex: { value: this.texture },
                    uColorDark: { value: new THREE.Color(this.params.colorDark) },
                    uColorLight: { value: new THREE.Color(this.params.colorLight) },

                    uMouse: { value: mouseUniform },
                    uMouseRadius: { value: this.params.mouseRadius },
                    uMouseStrength: { value: this.params.mouseStrength },
                    uMouseHoleStrength: { value: this.params.mouseHoleStrength },
                    uMouseWarp: { value: this.params.mouseWarp }
                },
                transparent: true,
                depthWrite: false,
                depthTest: true,
                blending: THREE.NormalBlending,
                side: THREE.DoubleSide
            });
        };

        this.frontMaterial = createFogMaterial({
            opacity: this.params.opacityFront,
            density: this.params.densityFront,
            speed: this.params.speedFront,
            noiseScale: this.params.noiseScaleFront,
            mouseUniform: this.frontMouseUniform
        });

        this.backMaterial = createFogMaterial({
            opacity: this.params.opacityBack,
            density: this.params.densityBack,
            speed: this.params.speedBack,
            noiseScale: this.params.noiseScaleBack,
            mouseUniform: this.backMouseUniform
        });

        this.backMaterial.uniforms.uMouseStrength.value = this.params.mouseStrength * 0.7;
        this.backMaterial.uniforms.uMouseHoleStrength.value = this.params.mouseHoleStrength * 0.8;
        this.backMaterial.uniforms.uMouseWarp.value = this.params.mouseWarp * 1.1;

        this.frontMesh = new THREE.Mesh(this.geometry, this.frontMaterial);
        this.backMesh = new THREE.Mesh(this.geometry, this.backMaterial);

        this.frontMesh.position.set(0, 0, 0);
        this.backMesh.position.set(0, 0, -1.5);

        this.backMesh.scale.set(1.2, 1.2, 1);

        this.group.position.set(this.params.x, this.params.y, this.params.z);
        this.group.add(this.backMesh);
        this.group.add(this.frontMesh);

        if (this.gui) {
            const f = this.gui.addFolder("Fog Debug");

            f.add(this.params, "x", -10, 10, 0.01);
            f.add(this.params, "y", -10, 10, 0.01);
            f.add(this.params, "z", -10, 10, 0.01);

            f.add(this.params, "frontDistance", 0, 10, 0.01);
            f.add(this.params, "backDistance", 0, 10, 0.01);

            f.add(this.params, "opacityFront", 0, 2, 0.001).onChange(v => {
                this.frontMaterial.uniforms.uOpacity.value = v;
            });
            f.add(this.params, "opacityBack", 0, 2, 0.001).onChange(v => {
                this.backMaterial.uniforms.uOpacity.value = v;
            });

            f.add(this.params, "densityFront", 0, 3, 0.001).onChange(v => {
                this.frontMaterial.uniforms.uDensity.value = v;
            });
            f.add(this.params, "densityBack", 0, 3, 0.001).onChange(v => {
                this.backMaterial.uniforms.uDensity.value = v;
            });

            f.add(this.params, "speedFront", 0, 0.2, 0.001).onChange(v => {
                this.frontMaterial.uniforms.uSpeed.value = v;
            });
            f.add(this.params, "speedBack", 0, 0.2, 0.001).onChange(v => {
                this.backMaterial.uniforms.uSpeed.value = v;
            });

            f.add(this.params, "noiseScaleFront", 0.1, 30, 0.01).onChange(v => {
                this.frontMaterial.uniforms.uNoiseScale.value = v;
            });
            f.add(this.params, "noiseScaleBack", 0.1, 30, 0.01).onChange(v => {
                this.backMaterial.uniforms.uNoiseScale.value = v;
            });

            f.addColor(this.params, "colorDark").name("fog dark").onChange(v => {
                this.frontMaterial.uniforms.uColorDark.value.set(v);
                this.backMaterial.uniforms.uColorDark.value.set(v);
            });

            f.addColor(this.params, "colorLight").name("fog light").onChange(v => {
                this.frontMaterial.uniforms.uColorLight.value.set(v);
                this.backMaterial.uniforms.uColorLight.value.set(v);
            });

            const fMouse = f.addFolder("Mouse");
            fMouse.add(this.params, "mouseRadius", 0.01, 0.6, 0.001).onChange(v => {
                this.frontMaterial.uniforms.uMouseRadius.value = v;
                this.backMaterial.uniforms.uMouseRadius.value = v;
            });
            fMouse.add(this.params, "mouseStrength", 0.0, 0.5, 0.001).onChange(v => {
                this.frontMaterial.uniforms.uMouseStrength.value = v;
                this.backMaterial.uniforms.uMouseStrength.value = v;
            });
            fMouse.add(this.params, "mouseHoleStrength", 0.0, 1.0, 0.001).onChange(v => {
                this.frontMaterial.uniforms.uMouseHoleStrength.value = v;
                this.backMaterial.uniforms.uMouseHoleStrength.value = v;
            });
            fMouse.add(this.params, "mouseWarp", 0.0, 0.2, 0.001).onChange(v => {
                this.frontMaterial.uniforms.uMouseWarp.value = v;
                this.backMaterial.uniforms.uMouseWarp.value = v;
            });

            f.open();
        }
    }

    update(time) {
        this.frontMaterial.uniforms.uTime.value = time;
        this.backMaterial.uniforms.uTime.value = time;

        if (this.camera) {
            this.camera.getWorldPosition(this._camWorldPos);

            this.group.position.copy(this._camWorldPos);
            this.group.position.x += this.params.x;
            this.group.position.y += this.params.y;
            this.group.position.z += this.params.z;

            this.group.rotation.set(this.camrot.x, this.camrot.y, this.camrot.z);

            this.frontMesh.position.set(0, 0, -this.params.frontDistance);
            this.backMesh.position.set(0, 0, -this.params.backDistance);

            this.group.updateMatrixWorld(true);
            this.frontMesh.updateMatrixWorld(true);
            this.backMesh.updateMatrixWorld(true);
        }

        if (this.mouse) {
            this.mouseNDC.set(this.mouse.x, this.mouse.y);
            this.raycaster.setFromCamera(this.mouseNDC, this.camera);

            const frontHit = this.raycaster.intersectObject(this.frontMesh, false);
            const backHit = this.raycaster.intersectObject(this.backMesh, false);

            if (frontHit.length > 0 && frontHit[0].uv) {
                this.frontMouseUniform.set(frontHit[0].uv.x, frontHit[0].uv.y);
            } else {
                this.frontMouseUniform.set(9999, 9999);
            }

            if (backHit.length > 0 && backHit[0].uv) {
                this.backMouseUniform.set(backHit[0].uv.x, backHit[0].uv.y);
            } else {
                this.backMouseUniform.set(9999, 9999);
            }
        }
    }
}
