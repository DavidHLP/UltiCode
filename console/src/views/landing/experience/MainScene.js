import * as THREE from 'three';
import { ParticlesModel } from "./ParticlesModel";
import {ParticlesDesert} from "./ParticlesDesert";
import {ParticlesLight} from "./ParticlesLight";
import {MousePointer} from "./MousePointer";
import {gsap} from 'gsap';
//import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import {MSDFText} from './MSDFText';
import {Awards} from "./Awards";
//import {SystemDialog} from './SystemDialog.js';
import {isDesktop} from './Utils.js';
import { TEMPLATE_URL } from './config.js';

import {
    EffectComposer,
    RenderPass,
    EffectPass,
    ToneMappingEffect,
    ToneMappingMode,
    BloomEffect,

} from "postprocessing";


import { DitherEffect } from './DitherEffect';
import {CustomFog} from "./CustomFog";


export class MainScene {

    constructor(options){

        //this.gui = new GUI();
        //this.gui.close();

        const desktop = isDesktop();
        const maxDevicePixelRatio = Math.min(window.devicePixelRatio || 1, 1.25);
        const baseMinPixelRatio = desktop ? 0.85 : 1;

        this.time = 0;
        this.maxDevicePixelRatio = maxDevicePixelRatio;
        this.devicePixelRatio = maxDevicePixelRatio;

        // 帧率上限:setAnimationLoop 跟随显示器 vsync,在 120/144/240Hz
        // 高刷屏上会无意义地满速渲染,把 GPU 占满(实测 240Hz 下 GPU 常驻
        // ~80%)。该场景是慢速氛围动画,60fps 足够流畅,超出的帧直接跳过。
        // 60Hz 显示器上帧间隔天然 >= 阈值,行为不变。
        this.minFrameInterval = 1000 / 60;
        this.lastRenderTime = -Infinity;
        this.particlePerformance = {
            enabled: true,
            ratio: 1,
            pixelRatio: maxDevicePixelRatio,
            baseMinPixelRatio,
            minPixelRatio: Math.min(baseMinPixelRatio, maxDevicePixelRatio),
            maxPixelRatio: maxDevicePixelRatio,
            balancedPixelRatio: Math.min(maxDevicePixelRatio, 1),
            pixelRatioDropRatio: desktop ? 0.75 : 0.55,
            pixelRatioCriticalFps: desktop ? 42 : 36,
            minRatio: desktop ? 0.45 : 0.3,
            maxRatio: 1,
            targetFps: desktop ? 55 : 50,
            recoverFps: desktop ? 58 : 56,
            criticalFps: 42,
            sampleFrames: 0,
            sampleStartedAt: performance.now(),
            warmupUntil: performance.now() + 1600,
            sampleInterval: 700,
            cooldown: 650,
            lastChangeAt: 0,
            lowStreak: 0,
            highStreak: 0,
            fps: 60
        };

        console.log('quality adptive')

        // base
        this.container = options.dom;
        this.resources = options.resources;

        //camera look
        this.mouse = new THREE.Vector2(0, 0);
        this.mouseTarget = new THREE.Vector2(0, 0);
        // target rotazione camera da mouse
        this.cameraMouseTarget = {
            x: 0,
            y: 0,
        };

        // limiti rotazione
        this.cameraMouseStrength = {
            x: 0.08, // su/giù
            y: 0.12, // sx/dx
        };
        // bind
        this.onMouseMove = this.onMouseMove.bind(this);

        // scena
        this.scene = new THREE.Scene();

        this.width = window.innerWidth;
        this.height = window.innerHeight;


        // CAMERA
        this.cameraTarget = new THREE.Object3D();
        this.cameraTarget.position.set(0.36, 0.26, -.22);

        const material = new THREE.MeshBasicMaterial({
            color: new THREE.Color().setHSL(Math.random(), 0.7, 0.5)
        });

        const helperGeometry = new THREE.SphereGeometry(0.125, 16, 16);
        this.helper = new THREE.Mesh(helperGeometry, material);


        this.scene.add(this.cameraTarget);
        //this.scene.add(this.helper);

        this.customCameraRotation = { x: 0, y: 0, z: 0 };
        this.quickCameraRotationX = gsap.quickTo(this.customCameraRotation, "x", {
            duration: 1.2,
            ease: "power3.out",
        });
        this.quickCameraRotationY = gsap.quickTo(this.customCameraRotation, "y", {
            duration: 1.2,
            ease: "power3.out",
        });
        this.baseCameraRotation = { x: 0, y: 0., z: 0 };
        this.targetCameraRotation = { x: 0, y: 0 };

        this.camera = new THREE.PerspectiveCamera(40, this.width / this.height, 0.1, 250);

        // ROTAZIONE Y
        this.cameraYaw = new THREE.Object3D();

        // ROTAZIONE X
        this.cameraPitch = new THREE.Object3D();

        this.camera.position.set(0, 0, 0);

        this.cameraYaw.add(this.cameraPitch);
        this.cameraPitch.add(this.camera);

        this.scene.add(this.cameraYaw);


        this.path = [
            new THREE.Vector3(-7.799073, 0.25, 9.103358),
            new THREE.Vector3(-5.05, 0.76, 7.05),
            new THREE.Vector3(-4.313, 0.65,  3.701),
            new THREE.Vector3(-5.740, 0.65, -0.220),
            new THREE.Vector3(-3.051, 0.65, -5.277),
            new THREE.Vector3( 3.224, 0.65, -5.606),
            new THREE.Vector3( 6.460, 0.65, -0.220),
            new THREE.Vector3( 3.034, 0.65,  5.263),
            new THREE.Vector3(-3, 0.65,  6.1)
        ];




        this.cameraYaw.position.set(
            this.path[0].x,
            this.path[0].y,
            this.path[0].z
        );

        // temp vectors per non allocare ogni frame
        this._cameraOrigin = new THREE.Vector3();
        this._cameraTargetWorld = new THREE.Vector3();
        this._cameraDir = new THREE.Vector3();


        // renderer
        // antialias 关闭:画面经 EffectComposer 离屏渲染,默认帧缓冲的 MSAA
        // 纯浪费显存;粒子/文字本身的视觉由后处理链保证。
        this.renderer = new THREE.WebGLRenderer({ antialias: false, alpha: true });
        this.renderer.outputColorSpace = THREE.SRGBColorSpace;
        this.renderer.setPixelRatio(this.devicePixelRatio);
        this.container.appendChild(this.renderer.domElement);


        this.renderer.setClearColor(0x101010, 0);




        // bind UNA volta
        this.render = this.render.bind(this);

        // start
        this.start();
    }

    // ---------- lifecycle ----------
    start() {

        //this.setControls()

        this.setupEvents();
        this.setObjects();
        this.resize();



        this.initPostFX();
        this.addGUI()






        this.play();       // avvia loop
    }

    play() {
        this.renderer.setAnimationLoop(this.render);
    }



    // 4) destroy(): 先停渲染循环,再释放全部 GPU 资源
    destroy() {

        this.destroyed = true;

        // 必须停掉 animation loop:否则循环持有 renderer 引用,
        // 每次访问落地页都会留下一个永久渲染的僵尸 WebGL 场景,显存只涨不回收
        this.renderer?.setAnimationLoop(null);

        window.removeEventListener('mousemove', this.onMouseMove);

        if (this._onResize) window.removeEventListener("resize", this._onResize);
        if (this._onVisibilityChange) document.removeEventListener('visibilitychange', this._onVisibilityChange);

        // Awards 使用独立 scene / render target,不在主 scene 遍历范围内
        try { this.awards?.destroy?.(); } catch (e) { /* noop */ }

        // 释放主场景内所有 geometry / material(贴图由 Resources.destroy 统一释放)
        if (this.scene) {
            this.scene.traverse((obj) => {
                obj.geometry?.dispose?.();

                const materials = Array.isArray(obj.material)
                    ? obj.material
                    : (obj.material ? [obj.material] : []);

                materials.forEach((material) => material?.dispose?.());
            });
        }

        // post fx cleanup
        this.composer?.dispose?.();
        this.composer = null;

        // rimuovi canvas webgl
        if (this.renderer?.domElement?.parentNode) {
            this.renderer.domElement.parentNode.removeChild(this.renderer.domElement);
        }

        this.renderer?.dispose?.();
        this.renderer = null;

    }



    // ---------- scene setup ----------
    setObjects() {

        this.addMainModel();



    }



    posrotGui(item,label) {

        const pos = this.gui.addFolder('pos '+label);

        pos.add(item.position, 'x', -50, 50, 0.01).onChange((t)=>{

        }).listen();
        pos.add(item.position, 'y', -50, 50, 0.01).onChange((t)=>{

        }).listen();
        pos.add(item.position, 'z', -50, 50, 0.01).onChange((t)=>{

        }).listen();

        const sc = this.gui.addFolder('scal '+label);

        sc.add(item.scale, 'x', -50, 50, 0.01).onChange((t)=>{

        });
        sc.add(item.scale, 'y', -50, 50, 0.01).onChange((t)=>{

        });
        sc.add(item.scale, 'z', -50, 50, 0.01).onChange((t)=>{

        });

        const rot = this.gui.addFolder('rot '+label);

        if (label == 'camera') {
            rot.add(this.baseCameraRotation, 'x', -50, 50, 0.01).onChange((t)=>{

            });
            rot.add(this.baseCameraRotation, 'y', -50, 50, 0.01).onChange((t)=>{

            });
            rot.add(this.baseCameraRotation, 'z', -50, 50, 0.01).onChange((t)=>{

            });

        } else {
            rot.add(item.rotation, 'x', -50, 50, 0.01).onChange((t)=>{

            }).listen();
            rot.add(item.rotation, 'y', -50, 50, 0.01).onChange((t)=>{

            }).listen();
            rot.add(item.rotation, 'z', -50, 50, 0.01).onChange((t)=>{

            }).listen();

        }




    }



    addMainModel() {



        this.hand = new ParticlesModel({
            mouse: this.mouse,
            models: this.resources.items.scene,
            particlesTexture : this.resources.items.p1,
            active: 0,
            //gui: this.gui
        });

        this.desert = new ParticlesDesert({
            mouse: this.mouse,
            models: this.resources.items.scene,
            particlesTexture : this.resources.items.p1,
            //gui: this.gui
        });



        this.light = new ParticlesLight({
            mouse: this.mouse,
            particlesTexture : this.resources.items.p1,
            active: 0,
            //gui: this.gui,
            camera: this.camera
        });

        this.fog = new CustomFog({
            mouse: this.mouse,
            camera: this.camera,
            camrot: this.baseCameraRotation,
            scene: this.scene,
            //gui: this.gui,
            texture : this.resources.items.fogTexture,
        });


        this.mousePointer = new MousePointer({
            scene: this.scene,
            camera: this.camera,
            mouse: this.mouse,
            //gui: this.gui,
            particlesTexture : this.resources.items.p1,
        });

        this.applyParticleQuality(this.particlePerformance.ratio);


        /*
        this.dialog = new SystemDialog({
            scene: this.scene,
            gui: this.gui,
            mouse: this.mouse,
            width: 1.75,
            height: 1.0,
            cornerRadius: 0.005,
            borderWidth: 0.01,
            borderOpacity: 0.75,

            position: new THREE.Vector3(-2.35, 1., 4.),
            rotation: new THREE.Euler(0, -0.7, 0),
            revealProgress: 0
        });


        this.posrotGui(this.dialog.group)

         */





        const layout = [
            { text: "Every problem is a door.", y: 1.20 },
            { text: "Every submission, a knock.", y: 1.075 },
            { text: "Not to be judged,", y: 0.9 },
            { text: "but to be understood.", y: 0.775 },
            { text: "Between wrong and right,", y: 0.65 },
            { text: "that's where you grow.", y: 0.475 }
        ];

        let aboutGroup = new THREE.Group();
        this.aboutR = [];
        let y = 1;

        layout.forEach((item) => {

            let g = false;

            if (item.y > 2.) {
                g = this.gui
            }

            const line = new MSDFText({
                scene: null,
                gui: g,
                mouse: this.mouse,
                text: item.text,
                scale: 0.0025,
                opacity: 0.3,
                letterSpacing: 0,
                position: new THREE.Vector3(0, y, 0),
                revealProgress: 0.0,
                active: 0,
                atlasPath: `${TEMPLATE_URL}/static/font-mdf/ibm/IBMPlexMono-Medium.png`,
                fontPath: `${TEMPLATE_URL}/static/font-mdf/ibm/IBMPlexMono-Medium-msdf.json`,


            });

            y -= 0.14;




            this.aboutR.push(line);

            aboutGroup.add(line.group);

        });

        aboutGroup.rotation.y = -1.25;
        aboutGroup.position.set(-0.45,0.55,3);
        let s = 0.75;

        if (isDesktop()) {
            aboutGroup.position.set(-0.45,0.55,3);
        } else {
            aboutGroup.position.set(-2.79,0.79,3);
            s = 0.65;
        }


        aboutGroup.scale.set(-s,s,s);

        this.scene.add(aboutGroup);

        //this.posrotGui(aboutGroup,'agency')

        const agencyLayout = [
            { text: "Some chase ratings,", y: 1.20 },
            { text: "some chase the silence", y: 1.075 },
            { text: "of a verdict turning green.", y: 0.9 },
            { text: "Both belong here.", y: 0.775 }
        ];

        this.agencyGroup = new THREE.Group();
        this.agencyR = [];
        let agencyY = 1;

        agencyLayout.forEach((item) => {

            let g = false;

            if (item.y > 2.) {
                g = this.gui
            }

            const line = new MSDFText({
                scene: null,
                gui: g,
                mouse: this.mouse,
                text: item.text,
                scale: 0.0025,
                opacity: 0.3,
                letterSpacing: 0,
                position: new THREE.Vector3(0, agencyY, 0),
                revealProgress: 0.0,
                active: 0,
                atlasPath: `${TEMPLATE_URL}/static/font-mdf/ibm/IBMPlexMono-Medium.png`,
                fontPath: `${TEMPLATE_URL}/static/font-mdf/ibm/IBMPlexMono-Medium-msdf.json`,
            });

            agencyY -= 0.14;

            this.agencyR.push(line);
            this.agencyGroup.add(line.group);
        });

        this.agencyGroup.rotation.y = -1.25;


        if (isDesktop()) {
            this.agencyGroup.position.set(0.47,2.68,1.76);
        } else {
            this.agencyGroup.position.set(-1.,3,1.76);
        }
        this.agencyGroup.scale.set(-s,s,s);

        this.scene.add(this.agencyGroup);

        //this.posrotGui(this.agencyGroup,'agency')

        let clientsLayout = [
            { text: "Python", x: 4, y: 2.5, z: 1.10 },
            { text: "Cpp", x: 3.15, y: 1.35, z: 0.18 },
            { text: "Java", x: 1.78, y: 3.3, z: 0.62 },
            { text: "Rust", x: 2.56, y: 2.62, z: -0.22 },
            { text: "Go", x: 0.92, y: 1.72, z: -0.48 },
            { text: "Kotlin", x: 0.58, y: 2.52, z: 0.09 },
            { text: "JavaScript", x: -0.10, y: 2.02, z: 0.58 },
            { text: "TypeScript", x: -0.18, y: 2.68, z: 1.31 }
        ];

        if (!isDesktop()) {

            clientsLayout = [
                { text: "Python", x: 3.15, y: 3.5, z: 1.10 },
                { text: "Cpp", x: 3., y: 2, z: 0.18 },
                { text: "Java", x: 1.85, y: 3.3, z: 0.62 },
                { text: "Rust", x: 2.56, y: 2.92, z: -0.22 },
                { text: "Go", x: 0.92, y: 1.72, z: -0.48 },
                { text: "Kotlin", x: 0.78, y: 2.22, z: 0.09 },
                { text: "JavaScript", x: 1.60, y: 2.6, z: 0.58 },
                { text: "TypeScript", x: -0.18, y: 2.68, z: 1.31 }
            ];

        }



        this.clientsGroup = new THREE.Group();
        this.clientsR = [];

        clientsLayout.forEach((item, index) => {

            const px = item.x;
            const py = item.y;
            const pz = item.z;

            let roty = -Math.PI;

            if (item.roty) {
                roty = item.roty;
            }




            const line = new MSDFText({
                scene: null,
                gui: false,
                mouse: this.mouse,
                text: item.text,
                scale: 0.0025,
                opacity: 0.3,
                letterSpacing: 0,
                position: new THREE.Vector3(px, py, pz),
                rotation: new THREE.Euler(0., roty, 0),
                revealProgress: 0.0,
                active: 0,
                atlasPath: `${TEMPLATE_URL}/static/font-mdf/ibm/IBMPlexMono-Medium.png`,
                fontPath: `${TEMPLATE_URL}/static/font-mdf/ibm/IBMPlexMono-Medium-msdf.json`,
            });

            this.clientsR.push(line);
            this.clientsGroup.add(line.group);



        });

        this.clientsGroup.rotation.y = 0;
        this.clientsGroup.position.set(0.47,2.68,1.76);
        this.clientsGroup.scale.set(-s,s,s);

        this.scene.add(this.clientsGroup);

        //this.posrotGui(this.clientsGroup,'clients')

        const recognitionLayout = [
            { text: "Step into it.", y: 1 },
            { text: "Solve. Submit. Rise.", y: 0.64 },
            { text: "Every verdict counts.", y: 0.50 },
            { text: "Every coder belongs.", y: 0.36 }
        ];

        this.recognitionGroup = new THREE.Group();
        this.recognitionR = [];

        recognitionLayout.forEach((item) => {
            const line = new MSDFText({
                scene: null,
                gui: false,
                mouse: this.mouse,
                text: item.text,
                scale: 0.0025,
                opacity: 0.3,
                letterSpacing: 0,
                position: new THREE.Vector3(0, item.y, 0),
                revealProgress: 0.0,
                active: 0,
                atlasPath: `${TEMPLATE_URL}/static/font-mdf/ibm/IBMPlexMono-Medium.png`,
                fontPath: `${TEMPLATE_URL}/static/font-mdf/ibm/IBMPlexMono-Medium-msdf.json`,
            });

            this.recognitionR.push(line);
            this.recognitionGroup.add(line.group);
        });

        this.recognitionGroup.rotation.y = 0;

        if (isDesktop()) {
            this.recognitionGroup.position.set(1.35, 1., 0.75);
        } else {
            this.recognitionGroup.position.set(0, 1.45, 0.75);
        }

        this.recognitionGroup.scale.set(-s,s,s);
        this.scene.add(this.recognitionGroup);

        //this.posrotGui(this.recognitionGroup,'recognition')

        this.finalClaimGroup = new THREE.Group();
        this.finalClaim = new MSDFText({
            scene: null,
            gui: false,
            mouse: this.mouse,
            text: 'made to be solved.',
            scale: 0.0025,
            opacity: 0.3,
            letterSpacing: 0,
            position: new THREE.Vector3(0, 0.82, 0),
            revealProgress: 0.0,
            active: 0,
            atlasPath: `${TEMPLATE_URL}/static/font-mdf/ibm/IBMPlexMono-Medium.png`,
            fontPath: `${TEMPLATE_URL}/static/font-mdf/ibm/IBMPlexMono-Medium-msdf.json`,
        });

        this.finalClaimGroup.add(this.finalClaim.group);

        if (isDesktop()) {
            this.finalClaimGroup.position.set(-0.58, 1.45, 0.75);
        } else {
            this.finalClaimGroup.position.set(-0.45, 1.6, 0.75);
        }

        this.finalClaimGroup.scale.set(-s,s,s);
        this.scene.add(this.finalClaimGroup);

        //this.posrotGui(this.finalClaimGroup,'final claim')

        //this.posrotGui(this.cameraTarget,'camera ta')
        //this.posrotGui(this.cameraYaw,'camera')


        // particles
        let l = this.light.particles.points;
        l.position.x = 0.28;
        l.position.y = 1.06;

        this.scene.add(this.hand.particles.points);
        this.scene.add(this.desert.particles.points);
        this.scene.add(l);

        //this.posrotGui(this.light.particles.points,'light')


        if (isDesktop()) {

            this.vision = new MSDFText({
                mouse: this.mouse,
                roughTexture : this.resources.items.p1,
                scene: this.scene,
                text: 'CODE',
                scale: 0.01,
                opacity: 0.3,
                rotation: new THREE.Euler(0, -.6, 0),
                position: new THREE.Vector3(-2.5, .6, 3),
                revealProgress: 0.0,
                active: 1,
                //gui: this.gui,
            });

        } else {
            this.vision = new MSDFText({
                mouse: this.mouse,
                roughTexture : this.resources.items.p1,
                scene: this.scene,
                text: 'CODE',
                scale: 0.007,
                opacity: 0.3,
                rotation: new THREE.Euler(0, -.6, 0),
                position: new THREE.Vector3(-3.5, .6, 3),
                revealProgress: 0.0,
                active: 1,
                //gui: this.gui,
            });
        }


        if (isDesktop()) {

            this.craft = new MSDFText({
                mouse: this.mouse,
                roughTexture : this.resources.items.p1,
                scene: this.scene,
                text: 'CRAFT',
                scale: 0.01,
                opacity: 0.3,
                rotation: new THREE.Euler(0, -.6, 0),
                position: new THREE.Vector3(1.03, 1.08, -0.01),
                revealProgress: 0.0,
                active: 0,
                //gui: this.gui,
            });

        } else {

            this.craft = new MSDFText({
                mouse: this.mouse,
                roughTexture : this.resources.items.p1,
                scene: this.scene,
                text: 'CRAFT',
                scale: 0.007,
                opacity: 0.3,
                letterSpacing: 18,
                rotation: new THREE.Euler(0, -.6, 0),
                position: new THREE.Vector3(-.55, 2.5, -0.01),
                revealProgress: 0.0,
                active: 0,
                //gui: this.gui,
            });

        }


        if (isDesktop()) {

            this.experience = new MSDFText({
                mouse: this.mouse,
                roughTexture : this.resources.items.p1,
                scene: this.scene,
                text: 'MASTERY',
                scale: 0.01,
                opacity: 0.3,
                letterSpacing: 12,
                rotation: new THREE.Euler(0, 0, 0),
                position: new THREE.Vector3(1.85, 2.15, -4.25),
                revealProgress: 0.0,
                active: 0,
                //gui: this.gui,
            });

        } else {

            this.experience = new MSDFText({
                mouse: this.mouse,
                roughTexture : this.resources.items.p1,
                scene: this.scene,
                text: 'MASTERY',
                scale: 0.007,
                opacity: 0.3,
                letterSpacing: 12,
                rotation: new THREE.Euler(0, 0, 0),
                position: new THREE.Vector3(-1.2, 2.15, -4.25),
                revealProgress: 0.0,
                active: 0,
                //gui: this.gui,
            });

        }



        //this.posrotGui(this.experience.group,'exp')



        if (isDesktop()) {

            this.scrollTo = new MSDFText({
                mouse: this.mouse,
                scene: this.scene,
                text: 'SCROLL TO RUN',
                scale: 0.005,
                opacity: 0.3,
                letterSpacing: 10,
                rotation: new THREE.Euler(0, -.7, 0),
                position: new THREE.Vector3(-4, .6, 2.5),
                revealProgress: 0.0,
                //gui: this.gui,
            });

        } else {

            this.scrollTo = new MSDFText({
                mouse: this.mouse,
                scene: this.scene,
                text: 'SCROLL TO RUN',
                scale: 0.0035,
                opacity: 0.3,
                letterSpacing: 10,
                rotation: new THREE.Euler(0, -.7, 0),
                position: new THREE.Vector3(-3.3, .6, 2.5),
                revealProgress: 0.0,
                //gui: this.gui,
            });

        }


        this.awards = new Awards({
            scene: this.scene,
            mouse: this.mouse,
            active: 0,
            progress: 0,
            groupOpacity: 0,
            position: new THREE.Vector3(0, 2, 0),
            scale: 0.25,
            // 半分辨率离屏渲染,显著降低全屏 render target 显存占用
            renderScale: 0.5
        });



        // se hai già orbit controls
        //this.controls = new OrbitControls(this.camera, this.renderer.domElement);


        /*
        this.cameraPathEditor = new SplineEditor({
            scene: this.scene,
            camera: this.camera,
            renderer: this.renderer,
            //orbitControls: this.controls,
            //gui: this.gui,
            name: 'Camera Path',
            curveType: 'centripetal',
            tension: 0.5,
            initialPoints: this.path
        });

         */




        //this.posrotGui(this.awards.group)


    }




    initPostFX() {



        this.composer = new EffectComposer(this.renderer, {
            // LDR 缓冲:全屏 buffer 显存相比 HalfFloat 减半;
            // 该场景的 bloom/tonemap 不依赖 HDR 范围。
            frameBufferType: THREE.UnsignedByteType,
        });

        this.composer.addPass(new RenderPass(this.scene, this.camera));




        this.toneParams = {
            mode: ToneMappingMode.REINHARD,
            resolution: 256,
            whitePoint: 16.0,
            middleGrey: 0.6,
            minLuminance: 0.01,
            averageLuminance: 0.01,
            adaptationRate: 1.0,
            exposure: 1.,
        };

        this.toneMappingEffect = new ToneMappingEffect({
            mode: this.toneParams.mode,
            resolution: this.toneParams.resolution,
            whitePoint: this.toneParams.whitePoint,
            middleGrey: this.toneParams.middleGrey,
            minLuminance: this.toneParams.minLuminance,
            averageLuminance: this.toneParams.averageLuminance,
            adaptationRate: this.toneParams.adaptationRate,
        });

        // exposure sta sul renderer (come demo)
        this.renderer.toneMappingExposure = this.toneParams.exposure;


        this.bloomEffect = new BloomEffect({
            intensity: 2.5,
            mipmapBlur: true,
            luminanceThreshold: 0.01,   // DEBUG: sempre attivo
            luminanceSmoothing: 0.025
        });




        this.ditherEffect = new DitherEffect({
            strength: 0.001
        });




        this.effectPass = new EffectPass(
            this.camera,
            this.bloomEffect,
            this.toneMappingEffect,
            this.ditherEffect
        );

        this.composer.addPass(this.effectPass);


    }


    // ---------- layout ----------
    setupEvents(){
        this._onResize = this.resize.bind(this);
        this._onVisibilityChange = this.handleVisibilityChange.bind(this);
        window.addEventListener('resize', this._onResize);
        window.addEventListener('mousemove', this.onMouseMove);
        document.addEventListener('visibilitychange', this._onVisibilityChange);

    }

    // 标签页隐藏时暂停渲染,避免后台空跑 GPU
    handleVisibilityChange() {
        if (this.destroyed || !this.renderer) return;

        if (document.hidden) {
            this.renderer.setAnimationLoop(null);
        } else {
            this.renderer.setAnimationLoop(this.render);
        }
    }

    onMouseMove(e) {
        this.mouseTarget.x = (e.clientX / window.innerWidth) * 2 - 1;
        this.mouseTarget.y = -(e.clientY / window.innerHeight) * 2 + 1;

        this.cameraMouseTarget.x = this.mouseTarget.y * this.cameraMouseStrength.x;
        this.cameraMouseTarget.y = -this.mouseTarget.x * this.cameraMouseStrength.y;

        this.quickCameraRotationX(this.cameraMouseTarget.x);
        this.quickCameraRotationY(this.cameraMouseTarget.y);
    }


    // 6) resize(): aggiorna anche composer + renderTarget + depthTexture
    resize() {

        if (this.mousePointer) {
            this.mousePointer.resize();
        }

        this.width = window.innerWidth;
        this.height = window.innerHeight;
        this.maxDevicePixelRatio = Math.min(window.devicePixelRatio || 1, 1.25);

        if (this.particlePerformance) {
            this.particlePerformance.maxPixelRatio = this.maxDevicePixelRatio;
            this.particlePerformance.minPixelRatio = Math.min(
                this.particlePerformance.baseMinPixelRatio,
                this.maxDevicePixelRatio
            );
            this.particlePerformance.balancedPixelRatio = Math.min(this.maxDevicePixelRatio, 1);
        }

        this.applyRenderPixelRatio(
            Math.min(this.devicePixelRatio, this.maxDevicePixelRatio),
            true
        );

        this.camera.aspect = this.width / this.height;
        this.camera.updateProjectionMatrix();
    }


    updateTargetCameraRotation() {


        this.cameraYaw.getWorldPosition(this._cameraOrigin);
        this.cameraTarget.getWorldPosition(this._cameraTargetWorld);

        this._cameraDir.subVectors(this._cameraTargetWorld, this._cameraOrigin);

        const dx = this._cameraDir.x;
        const dy = this._cameraDir.y;
        const dz = this._cameraDir.z;

        this.targetCameraRotation.y = -Math.atan2(dx, -dz);
        this.targetCameraRotation.x = Math.atan2(
            dy,
            Math.sqrt(dx * dx + dz * dz)
        );

        this.cameraYaw.rotation.y =
            this.targetCameraRotation.y +
            this.baseCameraRotation.y +
            this.customCameraRotation.y;

        this.cameraPitch.rotation.x =
            this.targetCameraRotation.x +
            this.baseCameraRotation.x +
            this.customCameraRotation.x;

        this.camera.rotation.z =
            this.baseCameraRotation.z +
            this.customCameraRotation.z;


        this.fog.camrot = {
            x: this.targetCameraRotation.x + this.baseCameraRotation.x,
            y: this.targetCameraRotation.y + this.baseCameraRotation.y,
            z: this.baseCameraRotation.z
        };

        this.helper.position.copy(this.cameraTarget.position);


    }


    // 7) render(): safe-check shader
    applyParticleQuality(ratio) {
        const perf = this.particlePerformance;
        if (!perf) return;

        const nextRatio = THREE.MathUtils.clamp(ratio, perf.minRatio, perf.maxRatio);
        const changed = Math.abs(nextRatio - perf.ratio) >= 0.001;
        perf.ratio = nextRatio;

        if (changed) {
            [this.hand, this.desert, this.light].forEach((particles) => {
                particles?.setParticleRatio?.(nextRatio);
            });
        }

        return changed;
    }

    applyRenderPixelRatio(pixelRatio, force = false) {
        const perf = this.particlePerformance;
        const minPixelRatio = perf?.minPixelRatio ?? 0.85;
        const maxPixelRatio = this.maxDevicePixelRatio ?? Math.min(window.devicePixelRatio || 1, 1.25);
        const nextPixelRatio = THREE.MathUtils.clamp(pixelRatio, minPixelRatio, maxPixelRatio);
        const changed = Math.abs(nextPixelRatio - this.devicePixelRatio) >= 0.01;

        if (!changed && !force) return false;

        this.devicePixelRatio = nextPixelRatio;

        if (perf) {
            perf.pixelRatio = nextPixelRatio;
        }

        this.renderer?.setPixelRatio(nextPixelRatio);
        this.renderer?.setSize(this.width, this.height);
        this.composer?.setSize(this.width, this.height);

        [this.hand, this.desert, this.light].forEach((particles) => {
            particles?.resize?.(nextPixelRatio);
        });

        this.awards?.resize?.(this.width, this.height, nextPixelRatio);

        return changed;
    }

    updateParticlePerformance(now) {
        const perf = this.particlePerformance;
        if (!perf?.enabled) return;

        if (now < perf.warmupUntil) {
            perf.sampleFrames = 0;
            perf.sampleStartedAt = now;
            return;
        }

        perf.sampleFrames += 1;

        const elapsed = now - perf.sampleStartedAt;
        if (elapsed < perf.sampleInterval) return;

        const sampleFps = (perf.sampleFrames * 1000) / elapsed;
        perf.fps = THREE.MathUtils.lerp(perf.fps, sampleFps, 0.35);
        perf.sampleFrames = 0;
        perf.sampleStartedAt = now;

        if (now - perf.lastChangeAt < perf.cooldown) return;

        let nextRatio = perf.ratio;
        let nextPixelRatio = perf.pixelRatio;

        if (perf.fps < perf.criticalFps) {
            perf.lowStreak = 0;
            perf.highStreak = 0;
            nextRatio -= 0.2;

            if (perf.fps < perf.pixelRatioCriticalFps || nextRatio <= perf.pixelRatioDropRatio) {
                nextPixelRatio -= 0.15;
            }
        } else if (perf.fps < perf.targetFps) {
            perf.lowStreak += 1;
            perf.highStreak = 0;

            if (perf.lowStreak >= 2) {
                perf.lowStreak = 0;
                nextRatio -= 0.1;

                if (nextRatio <= perf.pixelRatioDropRatio) {
                    nextPixelRatio -= nextPixelRatio > perf.balancedPixelRatio ? 0.1 : 0.05;
                }
            }
        } else if (
            perf.fps > perf.recoverFps &&
            (perf.ratio < perf.maxRatio || perf.pixelRatio < perf.maxPixelRatio)
        ) {
            perf.highStreak += 1;
            perf.lowStreak = 0;

            if (perf.highStreak >= 4) {
                perf.highStreak = 0;
                nextRatio += 0.08;
                nextPixelRatio += 0.05;
            }
        } else {
            perf.lowStreak = 0;
            perf.highStreak = 0;
        }

        nextRatio = THREE.MathUtils.clamp(nextRatio, perf.minRatio, perf.maxRatio);
        nextPixelRatio = THREE.MathUtils.clamp(nextPixelRatio, perf.minPixelRatio, perf.maxPixelRatio);

        const particlesChanged = this.applyParticleQuality(nextRatio);
        const pixelRatioChanged = this.applyRenderPixelRatio(nextPixelRatio);

        if (particlesChanged || pixelRatioChanged) {
            perf.lastChangeAt = now;
        }
    }

    render(now = performance.now()) {

        if (this.destroyed) return;

        // 帧率上限:高刷屏跳帧,见构造函数说明
        if (now - this.lastRenderTime < this.minFrameInterval - 0.5) return;
        this.lastRenderTime = now;

        this.mouse.lerp(this.mouseTarget, 0.15);
        this.updateParticlePerformance(now);

        if (this.mousePointer) {
            this.mousePointer.update()
        }

        this.time = now*0.01;


        if (this.dialog) {
            this.dialog.update(this.time, this.mouse);
        }


        if (this.hand) {

            let th = now*0.01;

            this.hand.update(th);


            /*
            const lightWorld = new THREE.Vector3(0.4, 0.9, 0.2).normalize();
            const lightView = lightWorld.clone().transformDirection(this.camera.matrixWorldInverse);
            this.hand.particles.points.material.uniforms.uLightDir.value.copy(lightView);
             */

        }

        if (this.vision) {
            this.vision.update(this.time,this.mouse);
        }

        if (this.craft) {
            this.craft.update(this.time,this.mouse);
        }

        if (this.experience) {
            this.experience.update(this.time,this.mouse);
        }

        if (this.finalClaim) {
            this.finalClaim.update(this.time,this.mouse);
        }

        if (this.awards) {
            this.awards.update(this.time)
        }

        if (this.scrollTo) {

            this.scrollTo.update(this.time,this.mouse);
        }

        if (this.desert) {


            this.desert.update(this.time)

        }
        if (this.light) {

            this.light.update(this.time);

        }

        if (this.fog) {
            this.fog.update(this.time)
        }



        this.updateTargetCameraRotation()

        if (this.awards) {
            this.awards.render(this.renderer, this.camera);
        }

        this.composer ? this.composer.render() : this.renderer.render(this.scene, this.camera);
    }


    // ---------- GUI (facoltativa) ----------
    addGUI() {


        if (!this.gui) return;



        const fTone = this.gui.addFolder("Tone");

        fTone.add(this.toneParams, "exposure", 0.0, 2.0, 0.001).onChange((v) => {
            this.renderer.toneMappingExposure = v;
        });




        fTone.add(this.toneParams, "mode", ToneMappingMode).onChange((v) => {
            this.toneMappingEffect.mode = Number(v);
        });

        fTone.close();


        // assuming you already have:
// this.bloomEffect = new BloomEffect({ intensity, mipmapBlur, luminanceThreshold, luminanceSmoothing });

        this.bloomParams = {
            intensity: this.bloomEffect.intensity ?? 1.5,
            luminanceThreshold: this.bloomEffect.luminanceMaterial?.threshold ?? 0.1,
            luminanceSmoothing: this.bloomEffect.luminanceMaterial?.smoothing ?? 0.2,
            mipmapBlur: !!this.bloomEffect.mipmapBlur,
        };

        const fBloom = this.gui.addFolder("Bloom");

// intensity
        fBloom.add(this.bloomParams, "intensity", 0.0, 15.0, 0.001).onChange((v) => {
            this.bloomEffect.intensity = Number(v);
        });

// threshold / smoothing (postprocessing BloomEffect uses luminanceMaterial internally)
        fBloom.add(this.bloomParams, "luminanceThreshold", 0.0, 1.0, 0.001).onChange((v) => {
            if (this.bloomEffect.luminanceMaterial) {
                this.bloomEffect.luminanceMaterial.threshold = Number(v);
            }
        });

        fBloom.add(this.bloomParams, "luminanceSmoothing", 0.0, 1.0, 0.001).onChange((v) => {
            if (this.bloomEffect.luminanceMaterial) {
                this.bloomEffect.luminanceMaterial.smoothing = Number(v);
            }
        });


        fBloom.close();










    }
}
