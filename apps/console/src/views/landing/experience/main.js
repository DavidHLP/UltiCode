//Import
import * as THREE from "three";
import {gsap} from 'gsap';
import Resources from "./Resources.js";
import {Observer} from "gsap/Observer";
import {MainScene} from "./MainScene.js";
import Lenis from 'lenis';
import {
    blurPlug, isDesktop,
} from './Utils.js'
import {ScrollTrigger} from "gsap/ScrollTrigger";
import { ASSETS } from "./config.js";
import { getLandingTheme } from "./theme.js";
import { i18n } from "../../../i18n";

// Vue 入口:初始化整个落地页体验,返回销毁函数
export function initLandingExperience() {
blurPlug()

gsap.registerPlugin(Observer,ScrollTrigger);

const lenis = new Lenis({
    autoRaf: true,
    lerp: 0.2,
    duration: 1.3,
    content: document.getElementById('content'),
    wrapper: document.getElementById('scroller'),
    syncTouch: true,
});

lenis.stop()

let destroyed = false;
let scene = null;
let cleanupScene = null;

// Solarized 主题:跟随 <html> 的 dark class(只读,不写)。
let currentTheme = getLandingTheme();
// initMainScene 内部挂上的同步钩子(雾色 lerp 用的颜色实例)
let sceneThemeSync = null;

const handleThemeChange = () => {
    currentTheme = getLandingTheme();
    if (scene) {
        scene.setTheme(currentTheme);
        sceneThemeSync?.();
    }
};

const themeObserver = new MutationObserver(handleThemeChange);
themeObserver.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ["class"],
});

let resources = new Resources(ASSETS);
const loaderProgress = { value: 0 };
const loaderActualProgressClamp = gsap.utils.clamp(0, 1.5);
const loaderVisualProgressClamp = gsap.utils.clamp(0, 1.5);
const loaderTrickleLimit = 1.;
const loaderTrickleSpeed = 0.018;
let loaderActualProgress = 0;
const renderLoaderProgress = () => {
    gsap.set('.loader span', {
        width: `${loaderVisualProgressClamp(loaderProgress.value) * 100}%`,
    });
};
let loaderProgressTween = null;
const tweenLoaderProgress = (value, duration = 0.45, ease = "power2.out", onComplete = null) => {
    if (loaderProgressTween) {
        loaderProgressTween.kill();
    }

    loaderProgressTween = gsap.to(loaderProgress, {
        value: loaderVisualProgressClamp(value),
        duration,
        ease,
        onUpdate: renderLoaderProgress,
        onComplete,
        overwrite: true,
    });
};
const startLoaderTrickle = () => {
    if (loaderActualProgress >= 1 || loaderProgress.value >= loaderTrickleLimit) {
        return;
    }

    const distance = loaderTrickleLimit - loaderProgress.value;
    tweenLoaderProgress(loaderTrickleLimit, distance / loaderTrickleSpeed, "none");
};
const syncLoaderProgress = (progress) => {
    loaderActualProgress = loaderActualProgressClamp(progress);

    if (loaderActualProgress >= 1) {
        tweenLoaderProgress(1, 0.35);
        return;
    }

    if (loaderActualProgress > loaderProgress.value) {
        tweenLoaderProgress(loaderActualProgress, 0.45, "power2.out", startLoaderTrickle);
    } else {
        startLoaderTrickle();
    }
};

gsap.set('.loader span', { width: '0%' });
gsap.set('.loader i', { autoAlpha: 1 });
gsap.set('.loader a', { autoAlpha: 0 });

resources.on('progress', (group) => {
    const progress = group.loaded / group.toLoad;

    syncLoaderProgress(progress);
});

let sceneInitialized = false;
const initMainScene = () => {
    if (sceneInitialized || destroyed) {
        return;
    }

    sceneInitialized = true;
    scene = new MainScene({
        dom : document.querySelector('.canvas'),
        resources : resources,
    });
    scene.setTheme(currentTheme);

    // ----------------------------------------------------------------
    // The orchestrator below drives the Scene entirely through the
    // deep Scene interface (setTextSection{Active,Reveal} /
    // setParticleActive / setParticleUniform / setCameraPosition /
    // setAwardsActive / setSectionDepth / setMousePointerSize /
    // resetAllTextSections / setTheme). Direct reads of state
    // (e.g. `scene.path[N].x`, `scene.mousePointer.params.size`) still
    // hit the underlying object because they are read-only state
    // that is part of the public subsystem interface.
    //
    // Documented exception — gsap tween targets. GSAP timelines
    // (e.g. `gsap.to(scene.desert.particles.material.uniforms.uProgress,
    // { value: 1 })`) hand the tween engine a target object whose
    // properties the engine mutates at every animation frame.
    // Wrapping these in a per-frame `onUpdate: () => scene.setX(...)`
    // would add per-frame work for marginal architectural gain and
    // change tween semantics. The tween is the orchestrator's
    // *intent* — `uProgress` from 0 to 1 — the seam method call
    // before/after the tween is what the seam covers. This is a
    // bounded, documented exception: imperative *writes* go through
    // the seam; per-frame *reads* inside gsap onUpdate callbacks
    // are not sealed by design.
    // ----------------------------------------------------------------

    const siteAudio = (() => {
        const trackDefinitions = {
            vision: resources.items.visionSound,
            craft: resources.items.craftSound,
            experience: resources.items.experienceSound,
        };
        const audioContext = Object.values(trackDefinitions).find(Boolean)?.audioContext;

        if (!audioContext) {
            return {
                start: () => {},
                crossfadeTo: () => {},
                resetForLoop: () => {},
                toggleMute: () => {},
                playHover: () => {},
            };
        }

        const masterGain = audioContext.createGain();
        masterGain.gain.value = 0;
        masterGain.connect(audioContext.destination);

        const hoverGain = audioContext.createGain();
        hoverGain.gain.value = 0.18;
        hoverGain.connect(masterGain);

        const tracks = Object.entries(trackDefinitions).reduce((acc, [name, item]) => {
            if (!item?.buffer) {
                return acc;
            }

            const gain = audioContext.createGain();
            gain.gain.value = 0;
            gain.connect(masterGain);

            acc[name] = {
                buffer: item.buffer,
                gain,
                source: null,
            };

            return acc;
        }, {});

        let started = false;
        let currentTrack = null;
        let muted = false;
        let lastHoverTime = 0;
        const targetVolume = 0.72;
        const crossfadeDuration = 2.2;
        const hoverSound = resources.items.hoverSound;

        const startSources = () => {
            if (started) {
                return;
            }

            started = true;
            Object.values(tracks).forEach((track) => {
                const source = audioContext.createBufferSource();
                source.buffer = track.buffer;
                source.loop = true;
                source.connect(track.gain);
                source.start(0);
                track.source = source;
            });
        };

        const setGain = (gainNode, value, duration = crossfadeDuration) => {
            const now = audioContext.currentTime;
            gainNode.gain.cancelScheduledValues(now);
            gainNode.gain.setValueAtTime(gainNode.gain.value, now);
            gainNode.gain.linearRampToValueAtTime(value, now + duration);
        };

        const setMasterVolume = (duration = 0.35) => {
            setGain(masterGain, muted ? 0 : targetVolume, duration);
        };

        const crossfadeTo = (name, duration = crossfadeDuration, force = false) => {
            if (!tracks[name] || (!force && currentTrack === name)) {
                return;
            }

            startSources();
            currentTrack = name;

            Object.entries(tracks).forEach(([trackName, track]) => {
                setGain(track.gain, trackName === name ? 1 : 0, duration);
            });
        };

        const start = async () => {
            if (audioContext.state === 'suspended') {
                await audioContext.resume();
            }

            startSources();
            crossfadeTo('vision', 0.8);
            setMasterVolume(1.2);
        };

        const resetForLoop = () => {
            startSources();
            crossfadeTo('vision', 0.6, true);
            setMasterVolume(0.6);
        };

        const toggleMute = () => {
            muted = !muted;
            setMasterVolume(0.25);

            return muted;
        };

        const playHover = () => {
            if (!started || muted || !hoverSound?.buffer) {
                return;
            }

            const now = audioContext.currentTime;
            if (now - lastHoverTime < 0.06) {
                return;
            }

            lastHoverTime = now;

            const source = audioContext.createBufferSource();
            source.buffer = hoverSound.buffer;
            source.playbackRate.value = 0.96 + Math.random() * 0.08;
            source.connect(hoverGain);
            source.start(0);
        };

        return {
            start,
            crossfadeTo,
            resetForLoop,
            toggleMute,
            playHover,
        };
    })();

    const audioToggle = document.querySelector('.js-audio-toggle');
    const audioWave = document.querySelector('.js-audio-wave');
    const audioWaveXs = [1, 2.14, 3.29, 4.43, 5.57, 6.71, 7.86, 9];
    let audioWaveMuted = false;
    let audioWaveEnergy = 1;

    let audioWaveRaf = 0;
    const animateAudioWave = () => {
        if (audioWave) {
            audioWaveEnergy += ((audioWaveMuted ? 0.08 : 1) - audioWaveEnergy) * 0.08;

            const time = performance.now() * 0.0022;
            const points = audioWaveXs.map((x, index) => {
                const phase = time + index * 0.82;
                const base = 5 + Math.sin(phase) * 1.55 + Math.sin(phase * 0.54 + 1.2) * 0.72;
                const y = 5.05 + (base - 5.05) * audioWaveEnergy;

                return `${x.toFixed(2)},${y.toFixed(2)}`;
            });

            audioWave.setAttribute('points', points.join(' '));
        }

        audioWaveRaf = requestAnimationFrame(animateAudioWave);
    };

    animateAudioWave();

    const setAudioToggleState = (muted) => {
        if (!audioToggle) {
            return;
        }

        audioWaveMuted = muted;
        audioToggle.classList.toggle('is-muted', muted);
        audioToggle.setAttribute('aria-pressed', muted ? 'true' : 'false');
        audioToggle.setAttribute('aria-label', i18n.global.t(muted ? 'landing.footer.audioOn' : 'landing.footer.audioOff'));
    };

    audioToggle?.addEventListener('click', () => {
        setAudioToggleState(siteAudio.toggleMute());
    });

    const pointerDefaultSize = scene.mousePointer?.params.size;

    if (scene.mousePointer && pointerDefaultSize) {
        const pointerHoverTargets = [
            { selector: '.header__lnk--2', multiplier: 5.65 },
            { selector: '.footer__social-lnk', multiplier: 3 },
            { selector: '.footer__audio-toggle', multiplier: 3 },
        ];

        const tweenPointerSize = (size, duration = 0.35) => {
            gsap.to(scene.mousePointer.params, {
                size,
                duration,
                ease: "power2.out",
                overwrite: true,
                onUpdate: () => {
                    scene.setMousePointerSize(scene.mousePointer.params.size);
                }
            });
        };

        pointerHoverTargets.forEach((target) => {
            document.querySelectorAll(target.selector).forEach((element) => {
                element.addEventListener('mouseenter', () => {
                    siteAudio.playHover();
                    tweenPointerSize(pointerDefaultSize * target.multiplier);
                });

                element.addEventListener('mouseleave', () => {
                    tweenPointerSize(pointerDefaultSize, 0.45);
                });
            });
        });
    }

    const cameraOrbitPoints = scene.path.slice(1, 9).map((point) => point.clone());
    const cameraOrbitCurve = new THREE.CatmullRomCurve3(
        cameraOrbitPoints,
        false,
        "centripetal"
    );
    const cameraOrbitState = { progress: 0 };


    gsap.timeline({
        defaults: {
            overwrite: "auto",
        },
    })
    .to('.loader i', {
        autoAlpha: 0,
        duration: 0.6,
        delay: .4,
        ease: "power1.out",
    })
    .to('.loader a', {
        autoAlpha: 1,
        duration: 0.6,
        ease: "power1.out",
    })




    var subtimeline1 = gsap.timeline({
        paused:true,
        defaults: { ease: 'none' },
        smoothChildTiming: true,
    });

    subtimeline1
    .add('start')
    .to(scene.scrollTo,{
        revealProgress: 2,
        duration: .25,
        overwrite: true,
        onUpdate:()=>{
            scene.setTextSectionReveal('scrollTo', scene.scrollTo.revealProgress)
        }
    },'start')
        /*
        .to(scene.scrollTo.position,{
            x:-6,
            z: 4.5,
            duration: .25,
            onUpdate:()=>{
                scene.scrollTo.setPosition(scene.scrollTo.position.x,scene.scrollTo.position.y,scene.scrollTo.position.z);
            }
        },'start')

         */
    .to(scene.cameraYaw.position,{
        y: 1.25,
    },'start')
    .to(scene.desert.particles.material.uniforms.uProgress,{
        value: 0,
    },'start')
    .to(scene.desert.particles.material.uniforms.uDesertMax.value,{
        x: -3,
        duration: .125,
    },'start')
    .to(scene.desert.particles.material.uniforms.uDesertMin.value,{
        y: 5.6,
        duration: .125,
    },'start')
    .to(scene.desert.particles.material.uniforms.uGlow,{
        value: 2,
    },'start')
    .to(scene.hand,{
        active: 1,
        duration: 0.01,
        onUpdate: () => {
            scene.setParticleActive('hand', scene.hand.active)
        }
    })
    .to(scene.light,{
        active: 1,
        duration: 0.01,
        onUpdate: () => {
            scene.setParticleActive('light', scene.light.active)
        }
    },'<')



    var subtimeline2 = gsap.timeline({
        paused:true,
        defaults: { ease: 'none' },
        smoothChildTiming: true,
    });

    subtimeline2
    .add('start')
    .to(scene.scrollTo,{
        active:0,
        onUpdate:()=>{
            scene.setTextSectionActive('scrollTo', scene.scrollTo.active)
        }
    },'start')
    .to(scene.desert.particles.material.uniforms.uDesertMax.value,{
        x: 10,
    },'start')
    .to(scene.desert.particles.material.uniforms.uDesertMin.value,{
        y: -10,
    },'start')
    .to(scene.vision,{
        revealProgress: 1,
        onUpdate:()=>{
            scene.setTextSectionReveal('vision', scene.vision.revealProgress)
        }
    },'start')
    .add('out')
        .to(scene.vision,{
            revealProgress: 2,
            delay: .125,
            onUpdate:()=>{
                scene.setTextSectionReveal('vision', scene.vision.revealProgress)
            }
        },'out')
        .to(scene.cameraYaw.position,{
            x: scene.path[1].x,
            y: scene.path[1].y,
            z: scene.path[1].z,
        },'out')
        .to(scene.aboutR[0],{
            active:1,
            onUpdate:()=>{
                scene.setTextSectionActive('aboutR', scene.aboutR[0].active)
            }
        },'out')
        .to(scene.aboutR[1],{
            active:1,
            onUpdate:()=>{
                scene.setTextSectionActive('aboutR', scene.aboutR[1].active)
            }
        },'out')
        .to(scene.aboutR[2],{
            active:1,
            onUpdate:()=>{
                scene.setTextSectionActive('aboutR', scene.aboutR[2].active)
            }
        },'out')
        .to(scene.aboutR[3],{
            active:1,
            onUpdate:()=>{
                scene.setTextSectionActive('aboutR', scene.aboutR[3].active)
            }
        },'out')
        .to(scene.aboutR[4],{
            active:1,
            onUpdate:()=>{
                scene.setTextSectionActive('aboutR', scene.aboutR[4].active)
            }
        },'out')
        .to(scene.aboutR[5],{
            active:1,
            onUpdate:()=>{
                scene.setTextSectionActive('aboutR', scene.aboutR[5].active)
            }
        },'out')



    var subtimeline3 = gsap.timeline({
        paused:true,
        defaults: { ease: 'none' },
        smoothChildTiming: true,
    });

    var aboutRow1 = { val : 0 }
    var aboutRow2 = { val : 0 }
    var aboutRow3 = { val : 0 }
    var aboutRow4 = { val : 0 }
    var aboutRow5 = { val : 0 }
    var aboutRow6 = { val : 0 }
    var agencyRow1 = { val : 0 }
    var agencyRow2 = { val : 0 }
    var agencyRow3 = { val : 0 }
    var agencyRow4 = { val : 0 }
    var clientRow1 = { val : 0 }
    var clientRow2 = { val : 0 }
    var clientRow3 = { val : 0 }
    var clientRow4 = { val : 0 }
    var clientRow5 = { val : 0 }
    var clientRow6 = { val : 0 }
    var clientRow7 = { val : 0 }
    var clientRow8 = { val : 0 }
    var recognitionRow1 = { val : 0 }
    var recognitionRow2 = { val : 0 }
    var recognitionRow3 = { val : 0 }
    var recognitionRow4 = { val : 0 }

    subtimeline3
    .add('start')
    .to(scene.vision,{
        active:0,
        onUpdate:()=>{
            scene.setTextSectionActive('vision', scene.vision.active)
        }
    },'start')
    .to(aboutRow1,{
        val: 1,
        onUpdate: ()=> {
            scene.setTextLineReveal('aboutR', 0, aboutRow1.val)
        }
    })
    .to(aboutRow2,{
        val: 1,
        onUpdate: ()=> {
            scene.setTextLineReveal('aboutR', 1, aboutRow2.val)
        }
    })
    .to(aboutRow3,{
        val: 1,
        onUpdate: ()=> {
            scene.setTextLineReveal('aboutR', 2, aboutRow3.val)
        }
    })
    .to(aboutRow4,{
        val: 1,
        onUpdate: ()=> {
            scene.setTextLineReveal('aboutR', 3, aboutRow4.val)
        }
    })
    .to(aboutRow5,{
        val: 1,
        onUpdate: ()=> {
            scene.setTextLineReveal('aboutR', 4, aboutRow5.val)
        }
    })
    .to(aboutRow6,{
        val: 1,
        onUpdate: ()=> {
            scene.setTextLineReveal('aboutR', 5, aboutRow6.val)
        }
    })
        .to({},{
            duration: .2
        })
    .add('out')
    .to(aboutRow1,{
        val: 2,
        onUpdate: ()=> {
            scene.setTextLineReveal('aboutR', 0, aboutRow1.val)
        }
    },'out')
    .to(aboutRow2,{
        val: 2,
        onUpdate: ()=> {
            scene.setTextLineReveal('aboutR', 1, aboutRow2.val)
        }
    },'out+=0.125')
    .to(aboutRow3,{
        val: 2,
        onUpdate: ()=> {
            scene.setTextLineReveal('aboutR', 2, aboutRow3.val)
        }
    },'out+=0.25')
    .to(aboutRow4,{
        val: 2,
        onUpdate: ()=> {
            scene.setTextLineReveal('aboutR', 3, aboutRow4.val)
        }
    },'out+=0.375')
    .to(aboutRow5,{
        val: 2,
        onUpdate: ()=> {
            scene.setTextLineReveal('aboutR', 4, aboutRow5.val)
        }
    },'out+=0.5')
    .to(aboutRow6,{
        val: 2,
        onUpdate: ()=> {
            scene.setTextLineReveal('aboutR', 5, aboutRow6.val)
        }
    },'out+=0.625')


    var subtimeline4 = gsap.timeline({
        paused:true,
        defaults: { ease: 'none' },
        smoothChildTiming: true,
    });

    subtimeline4
    .add('start')
    .to(scene.aboutR[0],{
        active:0,
        onUpdate:()=>{
            scene.setTextSectionActive('aboutR', scene.aboutR[0].active)
        }
    },'start')
    .to(scene.aboutR[1],{
        active:0,
        onUpdate:()=>{
            scene.setTextSectionActive('aboutR', scene.aboutR[1].active)
        }
    },'start')
    .to(scene.aboutR[2],{
        active:0,
        onUpdate:()=>{
            scene.setTextSectionActive('aboutR', scene.aboutR[2].active)
        }
    },'start')
    .to(scene.aboutR[3],{
        active:0,
        onUpdate:()=>{
            scene.setTextSectionActive('aboutR', scene.aboutR[3].active)
        }
    },'start')
    .to(scene.aboutR[4],{
        active:0,
        onUpdate:()=>{
            scene.setTextSectionActive('aboutR', scene.aboutR[4].active)
        }
    },'start')
    .to(scene.aboutR[5],{
        active:1,
        onUpdate:()=>{
            scene.setTextSectionActive('aboutR', scene.aboutR[5].active)
        }
    },'start')
    .to(scene.craft,{
        active:1,
        onUpdate:()=>{
            scene.setTextSectionActive('craft', scene.craft.active)
        }
    },'start')
    .to(cameraOrbitState,{
        progress: 1,
        duration: (cameraOrbitPoints.length - 1) * 0.5,
        onUpdate:()=>{
            cameraOrbitCurve.getPointAt(
                cameraOrbitState.progress,
                scene.cameraYaw.position
            );
        }
    },'start')
    .add('craftreveal')
    .to(scene.craft,{
        revealProgress: 1,
        onUpdate:()=>{
            if (scene.craft.revealProgress > 0.02) {
                siteAudio.crossfadeTo('craft');
            }

            scene.setTextSectionReveal('craft', scene.craft.revealProgress)
        }
    })

    if (!isDesktop()) {
        subtimeline4
        .to(scene.cameraTarget.position,{
            y: 2,
            onUpdate:()=>{

            }
        },'craftreveal')
        .to(scene.cameraYaw.position,{

            y: 2,

        },'craftreveal')
    }

    var subtimeline5 = gsap.timeline({
        paused:true,
        defaults: { ease: 'none' },
        smoothChildTiming: true,
    });

    subtimeline5
    .add('start')
    .to(scene.cameraTarget.position,{
        y: 4,
        onUpdate:()=>{

        }
    },'start')
    .to(scene.cameraYaw.position,{

        y: 4,

    },'start')
    .to(scene.light.particles.material.uniforms.uProgressStars,{
        value: 1,
    },'start')
    .to(scene.light.particles.material.uniforms.uMouseStrength,{
        value: 0.375,
    },'start')
    .to(scene.light.particles.material.uniforms.uMouseRadius,{
        value: .25,
    },'start')
    .to(scene.light.particles.material.uniforms.uGlow,{
        value: 0.3,
    },'start')
    .to(scene.craft,{
        revealProgress: 2,
        delay: .1,
        duration: .4,
        onUpdate:()=>{
            scene.setTextSectionReveal('craft', scene.craft.revealProgress)
        }
    },'start')
    .to(scene.hand.particles.material.uniforms.uProgress,{
        value: 1,
        delay: .2,
        duration: .3,
    },'start')



    var subtimelinePortfolio = gsap.timeline({
        paused:true,
        defaults: { ease: 'none' },
        smoothChildTiming: true,
    });
    subtimelinePortfolio
        .add('revealAgency')
        .to(scene.agencyR[0],{
            active:1,
            onUpdate:()=>{
                scene.setTextSectionActive('agencyR', scene.agencyR[0].active)
            }
        },'revealAgency')
        .to(scene.agencyR[1],{
            active:1,
            onUpdate:()=>{
                scene.setTextSectionActive('agencyR', scene.agencyR[1].active)
            }
        },'revealAgency')
        .to(scene.agencyR[2],{
            active:1,
            onUpdate:()=>{
                scene.setTextSectionActive('agencyR', scene.agencyR[2].active)
            }
        },'revealAgency')
        .to(scene.agencyR[3],{
            active:1,
            onUpdate:()=>{
                scene.setTextSectionActive('agencyR', scene.agencyR[3].active)
            }
        },'revealAgency')
        .to(agencyRow1,{
            val: 1,
            onUpdate: ()=> {

                scene.setTextLineReveal('agencyR', 0, agencyRow1.val)
            }
        },'revealAgency')
        .to(agencyRow2,{
            val: 1,
            onUpdate: ()=> {
                scene.setTextLineReveal('agencyR', 1, agencyRow2.val)
            }
        })
        .to(agencyRow3,{
            val: 1,
            onUpdate: ()=> {
                scene.setTextLineReveal('agencyR', 2, agencyRow3.val)
            }
        })
        .to(agencyRow4,{
            val: 1,
            onUpdate: ()=> {
                scene.setTextLineReveal('agencyR', 3, agencyRow4.val)
            }
        })
        .add('clients')
        .to(scene.clientsR[0],{
            active:1,
            onUpdate:()=>{
                scene.setTextSectionActive('clientsR', scene.clientsR[0].active)
            }
        },'clients')
        .to(scene.clientsR[1],{
            active:1,
            onUpdate:()=>{
                scene.setTextSectionActive('clientsR', scene.clientsR[1].active)
            }
        },'clients')
        .to(scene.clientsR[2],{
            active:1,
            onUpdate:()=>{
                scene.setTextSectionActive('clientsR', scene.clientsR[2].active)
            }
        },'clients')
        .to(scene.clientsR[3],{
            active:1,
            onUpdate:()=>{
                scene.setTextSectionActive('clientsR', scene.clientsR[3].active)
            }
        },'clients')
        .to(scene.clientsR[4],{
            active:1,
            onUpdate:()=>{
                scene.setTextSectionActive('clientsR', scene.clientsR[4].active)
            }
        },'clients')
        .to(scene.clientsR[5],{
            active:1,
            onUpdate:()=>{
                scene.setTextSectionActive('clientsR', scene.clientsR[5].active)
            }
        },'clients')
        .to(scene.clientsR[6],{
            active:1,
            onUpdate:()=>{
                scene.setTextSectionActive('clientsR', scene.clientsR[6].active)
            }
        },'clients')
        .to(scene.clientsR[7],{
            active:1,
            onUpdate:()=>{
                scene.setTextSectionActive('clientsR', scene.clientsR[7].active)
            }
        },'clients')
        .to(clientRow1,{
            val: 1,
            onUpdate: ()=> {
                scene.setTextLineReveal('clientsR', 0, clientRow1.val)
            }
        },'clients')
        .to(clientRow2,{
            val: 1,
            onUpdate: ()=> {
                scene.setTextLineReveal('clientsR', 1, clientRow2.val)
            }
        })
        .to(clientRow3,{
            val: 1,
            onUpdate: ()=> {
                scene.setTextLineReveal('clientsR', 2, clientRow3.val)
            }
        })
        .to(clientRow4,{
            val: 1,
            onUpdate: ()=> {
                scene.setTextLineReveal('clientsR', 3, clientRow4.val)
            }
        })
        .to(clientRow5,{
            val: 1,
            onUpdate: ()=> {
                scene.setTextLineReveal('clientsR', 4, clientRow5.val)
            }
        })
        .to(clientRow6,{
            val: 1,
            onUpdate: ()=> {
                scene.setTextLineReveal('clientsR', 5, clientRow6.val)
            }
        })
        .to(clientRow7,{
            val: 1,
            onUpdate: ()=> {
                scene.setTextLineReveal('clientsR', 6, clientRow7.val)
            }
        })
        .to(clientRow8,{
            val: 1,
            onUpdate: ()=> {
                scene.setTextLineReveal('clientsR', 7, clientRow8.val)
            }
        })
        .to({},{
            duration:.2
        })
        .add('outAgency')
        .add('outClients', 'outAgency+=0.125')
        .to(agencyRow1,{
            val: 2,
            onUpdate: ()=> {
                scene.setTextLineReveal('agencyR', 0, agencyRow1.val)
            }
        },'outAgency')
        .to(agencyRow2,{
            val: 2,
            onUpdate: ()=> {
                scene.setTextLineReveal('agencyR', 1, agencyRow2.val)
            }
        },'outAgency')
        .to(agencyRow3,{
            val: 2,
            onUpdate: ()=> {
                scene.setTextLineReveal('agencyR', 2, agencyRow3.val)
            }
        },'outAgency')
        .to(agencyRow4,{
            val: 2,
            onUpdate: ()=> {
                scene.setTextLineReveal('agencyR', 3, agencyRow4.val)
            }
        },'outAgency')
        .to(clientRow1,{
            val: 2,
            onUpdate: ()=> {
                scene.setTextLineReveal('clientsR', 0, clientRow1.val)
            }
        },'outClients')
        .to(clientRow2,{
            val: 2,
            onUpdate: ()=> {
                scene.setTextLineReveal('clientsR', 1, clientRow2.val)
            }
        },'outClients')
        .to(clientRow3,{
            val: 2,
            onUpdate: ()=> {
                scene.setTextLineReveal('clientsR', 2, clientRow3.val)
            }
        },'outClients')
        .to(clientRow4,{
            val: 2,
            onUpdate: ()=> {
                scene.setTextLineReveal('clientsR', 3, clientRow4.val)
            }
        },'outClients')
        .to(clientRow5,{
            val: 2,
            onUpdate: ()=> {
                scene.setTextLineReveal('clientsR', 4, clientRow5.val)
            }
        },'outClients')
        .to(clientRow6,{
            val: 2,
            onUpdate: ()=> {
                scene.setTextLineReveal('clientsR', 5, clientRow6.val)
            }
        },'outClients')
        .to(clientRow7,{
            val: 2,
            onUpdate: ()=> {
                scene.setTextLineReveal('clientsR', 6, clientRow7.val)
            }
        },'outClients')
        .to(clientRow8,{
            val: 2,
            onUpdate: ()=> {
                scene.setTextLineReveal('clientsR', 7, clientRow8.val)
            }
        },'outClients')


    var subtimelineHand = gsap.timeline({
        paused:true,
        defaults: { ease: 'none' },
        smoothChildTiming: true,
    });

    subtimelineHand
    .to(scene.hand.particles.material.uniforms.uProgress,{
        value: 0,
    })
    .to(scene.light.particles.material.uniforms.uProgress,{
        value: 0,
    })


    var subtimeline6 = gsap.timeline({
        paused:true,
        defaults: { ease: 'none' },
        smoothChildTiming: true,
    });

    var awop = { val : 0 }


    subtimeline6


    .add('start')
    .to(scene.light.particles.material.uniforms.uAlpha,{
        value: 0,
        onUpdate:()=>{

        }
    },'start')
    .to(scene.cameraYaw.position,{
        y: 2,
        x: 0,
    },'start')
    .to(scene.cameraTarget.position,{
        z: 0,
        x: 0,
        y: 2,
    },'start')
    .to(scene.light.particles.points.rotation,{
        z: -3.14,
        onUpdate:()=>{

        }
    },'start')
    .to(scene.desert.particles.points.rotation,{
        z: -3.14,
        y: -0.57,
        onUpdate:()=>{

        }
    },'start')
    .to(scene.desert.particles.points.position,{
        y: 5.75,
        x: -2.5,
        z: -5,
        onUpdate:()=>{

        }
    },'start')
    .to(scene.light.particles.points.position,{
        y: -3.,
        x: -1.5,
        onUpdate:()=>{

        }
    },'start')
    .to(scene.desert.particles.material.uniforms.uSplitProgress,{
        value: 1,

    },'start')
    .to(scene.experience,{
        active:1,
        onUpdate:()=>{
            scene.setTextSectionActive('experience', scene.experience.active)
        }
    },'start')
    .to(scene.recognitionR[0],{
        active:1,
        onUpdate:()=>{
            scene.setTextSectionActive('recognitionR', scene.recognitionR[0].active)
        }
    },'start')
    .to(scene.recognitionR[1],{
        active:1,
        onUpdate:()=>{
            scene.setTextSectionActive('recognitionR', scene.recognitionR[1].active)
        }
    },'start')
    .to(scene.recognitionR[2],{
        active:1,
        onUpdate:()=>{
            scene.setTextSectionActive('recognitionR', scene.recognitionR[2].active)
        }
    },'start')
    .to(scene.recognitionR[3],{
        active:1,
        onUpdate:()=>{
            scene.setTextSectionActive('recognitionR', scene.recognitionR[3].active)
        }
    },'start')

    var tlExp = gsap.timeline({
        paused:true,
        defaults: { ease: 'none' },
        smoothChildTiming: true,
    });

    if (isDesktop()) {

        tlExp
            .add('start')
            .to(scene.hand,{
                active: 0,
                duration: 0.05,
                onUpdate: () => {
                    scene.setParticleActive('hand', scene.hand.active)
                }
            },'start')
            .to(scene.light,{
                active: 0,
                duration: 0.05,
                onUpdate: () => {
                    scene.setParticleActive('light', scene.light.active)
                }
            },'start')
            .to(scene.experience,{
                revealProgress: 1,
                onUpdate:()=>{
                    if (scene.experience.revealProgress > 0.02) {
                        siteAudio.crossfadeTo('experience');
                    }

                    scene.setTextSectionReveal('experience', scene.experience.revealProgress)
                }
            },'start')
            .to({},{
                duration: 0.2
            })
            .to(scene.experience,{
                revealProgress: 2,
                duration: 1,
                onUpdate:()=>{
                    scene.setTextSectionReveal('experience', scene.experience.revealProgress)
                }
            })
            .to({},{
                duration: 0.2
            })
            .add('afterexp')
            .to(recognitionRow1,{
                val: 1,
                onUpdate: ()=> {
                    scene.setTextLineReveal('recognitionR', 0, recognitionRow1.val)
                }
            },'afterexp+=0.1')
            .to(recognitionRow2,{
                val: 1,
                onUpdate: ()=> {
                    scene.setTextLineReveal('recognitionR', 1, recognitionRow2.val)
                }
            },'afterexp+=0.175')
            .to(recognitionRow3,{
                val: 1,
                onUpdate: ()=> {
                    scene.setTextLineReveal('recognitionR', 2, recognitionRow3.val)
                }
            },'afterexp+=0.25')
            .to(recognitionRow4,{
                val: 1,
                onUpdate: ()=> {
                    scene.setTextLineReveal('recognitionR', 3, recognitionRow4.val)
                }
            },'afterexp+=0.325')

            .to(scene.awards,{
                active: 1,
                duration: 0.05,
                onUpdate: () => {
                    scene.setAwardsActive(scene.awards.active)
                }
            },'afterexp-=0.15')
            .to(awop,{
                val:1,
                onUpdate : () => {
                    scene.setAwardsGroupOpacity(awop.val)
                }

            },'afterexp')
            .to({},{
                duration: 0.2
            })
            .add('aw', `+=1.6`)
            .to(recognitionRow1,{
                val: 2,
                duration: 0.5,
                onUpdate: ()=> {
                    scene.setTextLineReveal('recognitionR', 0, recognitionRow1.val)
                }
            },'aw')
            .to(recognitionRow2,{
                val: 2,
                duration: 0.5,
                onUpdate: ()=> {
                    scene.setTextLineReveal('recognitionR', 1, recognitionRow2.val)
                }
            },'aw+=0.05')
            .to(recognitionRow3,{
                val: 2,
                duration: 0.5,
                onUpdate: ()=> {
                    scene.setTextLineReveal('recognitionR', 2, recognitionRow3.val)
                }
            },'aw+=0.1')
            .to(recognitionRow4,{
                val: 2,
                duration: 0.5,
                onUpdate: ()=> {
                    scene.setTextLineReveal('recognitionR', 3, recognitionRow4.val)
                }
            },'aw+=0.15')
            .to(scene.awards.progressState,{
                value: 1,
                onUpdate: ()=> {
                  console.log('update')
                },
                duration: 5.8,
            },'afterexp')



    } else {


        tlExp
            .add('start')
            .to(scene.hand,{
                active: 0,
                duration: 0.05,
                onUpdate: () => {
                    scene.setParticleActive('hand', scene.hand.active)
                }
            },'start')
            .to(scene.light,{
                active: 0,
                duration: 0.05,
                onUpdate: () => {
                    scene.setParticleActive('light', scene.light.active)
                }
            },'start')
            .to(scene.experience,{
                revealProgress: 1,
                onUpdate:()=>{
                    if (scene.experience.revealProgress > 0.02) {
                        siteAudio.crossfadeTo('experience');
                    }

                    scene.setTextSectionReveal('experience', scene.experience.revealProgress)
                }
            },'start')
            .to(scene.experience,{
                revealProgress: 2,
                duration: 1,
                onUpdate:()=>{
                    scene.setTextSectionReveal('experience', scene.experience.revealProgress)
                }
            })

            .to(recognitionRow1,{
                val: 1,
                onUpdate: ()=> {
                    scene.setTextLineReveal('recognitionR', 0, recognitionRow1.val)
                }
            })
            .to(recognitionRow2,{
                val: 1,
                onUpdate: ()=> {
                    scene.setTextLineReveal('recognitionR', 1, recognitionRow2.val)
                }
            })
            .to(recognitionRow3,{
                val: 1,
                onUpdate: ()=> {
                    scene.setTextLineReveal('recognitionR', 2, recognitionRow3.val)
                }
            })
            .to(recognitionRow4,{
                val: 1,
                onUpdate: ()=> {
                    scene.setTextLineReveal('recognitionR', 3, recognitionRow4.val)
                }
            })
            .to({},{
                duration: 0.2
            })
            .add('aw', `+=1.6`)
            .to(scene.awards,{
                active: 1,
                duration: 0.05,
                onUpdate: () => {
                    scene.setAwardsActive(scene.awards.active)
                }
            },'aw+=0.65')
            .to(awop,{
                val:1,
                onUpdate : () => {
                    scene.setAwardsGroupOpacity(awop.val)
                }

            },'aw+=0.8')
            .to(recognitionRow1,{
                val: 2,
                duration: 0.5,
                onUpdate: ()=> {
                    scene.setTextLineReveal('recognitionR', 0, recognitionRow1.val)
                }
            },'aw')
            .to(recognitionRow2,{
                val: 2,
                duration: 0.5,
                onUpdate: ()=> {
                    scene.setTextLineReveal('recognitionR', 1, recognitionRow2.val)
                }
            },'aw+=0.05')
            .to(recognitionRow3,{
                val: 2,
                duration: 0.5,
                onUpdate: ()=> {
                    scene.setTextLineReveal('recognitionR', 2, recognitionRow3.val)
                }
            },'aw+=0.05')
            .to(recognitionRow4,{
                val: 2,
                duration: 0.5,
                onUpdate: ()=> {
                    scene.setTextLineReveal('recognitionR', 3, recognitionRow4.val)
                }
            },'aw+=0.05')
            .to({},{
                duration: 0.2
            })

            .to(scene.awards.progressState,{
                value: 1,
                duration: 5.8,
            },'aw+=0.8')




    }







    var tlExpDepth = gsap.timeline({
        paused:true,
        defaults: { ease: 'none' },
        smoothChildTiming: true,
    });

    if (isDesktop()) {

        tlExpDepth
        .add('start')
        .to(scene.desert.particles.points.position,{
            z: 3,
            duration: 1,
        },'start')

        .to(scene.awards.group.position,{
            z: 4,
            duration: 1,
        },'start')

    } else {

        tlExpDepth
        .add('start')
        .to(scene.desert.particles.points.position,{
            z: 1,
            duration: 1,
        },'start')

        .to(scene.awards.group.position,{
            z: 4,
            duration: 1,
        },'start')

    }





    var tlEnd = gsap.timeline({
        paused:true,
        defaults: { ease: 'none' },
        smoothChildTiming: true,
    });

    if (isDesktop()) {

        tlEnd
            .add('start')
            .to(scene.desert.particles.material.uniforms.uBlackHoleProgress,{
                value: 0.35,
                duration: 1,
            },'start')
            .to(scene.finalClaim,{
                active: 1,
                duration: 0.05,
                onUpdate: () => {
                    scene.setTextSectionActive('finalClaim', scene.finalClaim.active)
                }
            },'start+=0.22')
            .to(scene.finalClaim,{
                revealProgress: 1,
                duration: 0.7,
                onUpdate:()=>{
                    scene.setTextSectionReveal('finalClaim', scene.finalClaim.revealProgress)
                }
            },'start+=0.26')
            .to(scene.finalClaim,{
                revealProgress: 2,
                duration: 0.55,
                onUpdate:()=>{
                    scene.setTextSectionReveal('finalClaim', scene.finalClaim.revealProgress)
                }
            },'start+=2.2')


            .to(scene.desert.particles.points.position,{
                z: 30,
                duration: 3,
            },'start')

    } else {

        tlEnd
            .add('start')
            .to(scene.desert.particles.material.uniforms.uBlackHoleProgress,{
                value: 0.35,
                duration: 1,
            },'start')
            .to(scene.finalClaim,{
                active: 1,
                duration: 0.05,
                onUpdate: () => {
                    scene.setTextSectionActive('finalClaim', scene.finalClaim.active)
                }
            },'start+=0.22')
            .to(scene.finalClaim,{
                revealProgress: 1,
                duration: 0.7,
                onUpdate:()=>{
                    scene.setTextSectionReveal('finalClaim', scene.finalClaim.revealProgress)
                }
            },'start+=0.26')
            .to(scene.finalClaim,{
                revealProgress: 2,
                duration: 0.55,
                onUpdate:()=>{
                    scene.setTextSectionReveal('finalClaim', scene.finalClaim.revealProgress)
                }
            },'start+=2.2')


            .to(scene.desert.particles.points.position,{
                z: 25,
                duration: 3,
            },'start')

    }








    var mainTimeline = gsap.timeline({
        paused:true,
        defaults: { ease: 'none' },
        smoothChildTiming: true,
    });

    let isLoopResetting = false;
    let restoreOpacityTween = null;

    const particleOpacityTargets = [
        scene.hand?.particles?.material?.uniforms?.uOpacity,
        scene.desert?.particles?.material?.uniforms?.uOpacity,
        scene.light?.particles?.material?.uniforms?.uOpacity,
    ]
        .filter(Boolean)
        .map((uniform) => ({
            uniform,
            value: uniform.value,
        }));

    const fogLightTargets = [
        scene.fog?.frontMaterial?.uniforms?.uColorLight,
        scene.fog?.backMaterial?.uniforms?.uColorLight,
    ]
        .filter(Boolean);

    // 循环归零时雾色由 fogFade 渐变到 fog.light;主题切换时同步这两个实例
    const fogFadeColor = new THREE.Color(currentTheme.fogFade);
    const fogLightColor = new THREE.Color(currentTheme.fog.light);
    sceneThemeSync = () => {
        fogFadeColor.set(currentTheme.fogFade);
        fogLightColor.set(currentTheme.fog.light);
    };

    const textOpacityTargets = [
        scene.scrollTo,
        scene.vision,
        ...scene.aboutR,
        scene.craft,
        ...scene.agencyR,
        ...scene.clientsR,
        scene.experience,
        ...scene.recognitionR,
        scene.finalClaim,
    ]
        .filter(Boolean)
        .map((item) => ({
            item,
            value: item.opacity ?? item.material?.uniforms?.uOpacity?.value ?? 1,
        }));

    const applyExperienceOpacity = (progress) => {
        particleOpacityTargets.forEach((target) => {
            target.uniform.value = target.value * progress;
        });

        textOpacityTargets.forEach((target) => {
            target.item.setOpacity(target.value * progress);
        });

        scene.awards?.setGroupOpacity(0);
    };

    const applyFogLightColor = (progress) => {
        fogLightTargets.forEach((uniform) => {
            uniform.value.copy(fogFadeColor).lerp(fogLightColor, progress);
        });
    };

    const resetExperienceLoop = () => {
        if (isLoopResetting) {
            return;
        }

        isLoopResetting = true;
        restoreOpacityTween?.kill();
        applyExperienceOpacity(0);
        applyFogLightColor(0);
        siteAudio.resetForLoop();

        mainTimeline.pause(0);
        subtimeline1.progress(0).pause();
        subtimeline2.progress(0).pause();
        subtimeline3.progress(0).pause();
        subtimeline4.progress(0).pause();
        subtimeline5.progress(0).pause();
        subtimelinePortfolio.progress(0).pause();
        subtimelineHand.progress(0).pause();
        subtimeline6.progress(0).pause();
        tlExp.progress(0).pause();
        tlExpDepth.progress(0).pause();
        tlEnd.progress(0).pause();

        scene.hand?.setActive(0);
        scene.light?.setActive(0);
        scene.awards?.setActive(0);
        scene.awards?.setGroupOpacity(0);
        scene.awards?.setProgress?.(0);
        scene.finalClaim?.setActive(0);
        scene.finalClaim?.setRevealProgress(0);

        lenis.scrollTo(0, {
            immediate: true,
            force: true,
        });

        lenis.stop()

        const scrollerElement = document.querySelector('#scroller');
        if (scrollerElement) {
            scrollerElement.scrollTop = 0;
        }

        ScrollTrigger.update();

        const opacityState = { value: 0 };
        restoreOpacityTween = gsap.to(opacityState, {
            value: 1,
            delay:.2,
            duration: 0.8,
            ease: 'power2.out',
            onUpdate: () => {
                applyExperienceOpacity(opacityState.value);
                applyFogLightColor(opacityState.value);
            },
            onComplete: () => {
                applyExperienceOpacity(1);
                applyFogLightColor(1);
                isLoopResetting = false;
                lenis.start()
            }
        });
    };


    mainTimeline
    .to(subtimeline1,{
        progress: 1,
        duration: 0.2,
        onUpdate: ()=> {

        }
    })
    .add('two')
    .to(subtimeline2,{
        progress: 1,
        duration: 0.2,
        onUpdate: ()=> {

        }
    })
    .to(subtimeline3,{
        progress: 1,
        duration: 1.2,
        onUpdate: ()=> {

        }
    })
    .add('four')
    .to(subtimelineHand,{
        progress: 1,
        duration: 0.2,
        onUpdate: ()=> {
        }
    },'four')
    .to(subtimeline4,{
        progress: 1,
        duration: 0.2,
        onUpdate: ()=> {

        }
    },'four')
    .to(subtimeline5,{
        progress: 1,
        duration: 0.2,
        onUpdate: ()=> {

        }
    })
    .to(subtimelinePortfolio,{
        progress: 1,
        duration: 0.2,
        onUpdate: ()=> {

        }
    })
    .to(subtimeline6,{
        progress: 1,
        duration: 0.2,
        onUpdate: ()=> {

        }
    })
    .add('exp')
    .to(tlExp,{
        progress: 1,
        duration: 0.25,
        onUpdate: ()=> {

        }
    },'exp')
    .to(tlExpDepth,{
        progress: 1,
        duration: 0.245,
        delay: 0.005,
        onUpdate: ()=> {

        }
    },'exp')
        .to(tlEnd,{
            progress: 1,
            duration: 0.3,
            onUpdate: ()=> {

            }
        })

    const getMainTimelineProgress = (label, fallback) => {
        const duration = mainTimeline.duration();
        const time = mainTimeline.labels[label];

        if (typeof time !== 'number' || !duration) {
            return fallback;
        }

        return time / duration;
    };

    const getNestedTimelineProgress = (mainLabel, nestedTimeline, nestedLabel, segmentDuration, fallback) => {
        const mainDuration = mainTimeline.duration();
        const mainTime = mainTimeline.labels[mainLabel];
        const nestedDuration = nestedTimeline.duration();
        const nestedTime = nestedTimeline.labels[nestedLabel];

        if (
            typeof mainTime !== 'number' ||
            typeof nestedTime !== 'number' ||
            !mainDuration ||
            !nestedDuration
        ) {
            return fallback;
        }

        return (mainTime + segmentDuration * (nestedTime / nestedDuration)) / mainDuration;
    };

    const audioSectionProgress = {
        craft: getNestedTimelineProgress('four', subtimeline4, 'craftreveal', 0.2, 0.42),
        experience: getMainTimelineProgress('exp', 0.74),
    };

    const syncAudioToScroll = (progress) => {
        if (progress >= audioSectionProgress.experience) {
            siteAudio.crossfadeTo('experience');
            return;
        }

        if (progress >= audioSectionProgress.craft) {
            siteAudio.crossfadeTo('craft');
            return;
        }

        siteAudio.crossfadeTo('vision');
    };










    ScrollTrigger.create({
        scroller: '#scroller',
        trigger: '.steps',
        pinSpacing: false,
        markers: false,
        anticipatePin: 1,
        invalidateOnRefresh:true,
        start: 'top top',    // quando il top della sezione tocca il top del viewport
        end: 'bottom bottom',// quando il bottom tocca il bottom del viewport



        onUpdate: (st) => {




            if (isLoopResetting) {
                return;
            }

            mainTimeline.progress(st.progress)
            syncAudioToScroll(st.progress);

            if (st.direction > 0 && st.progress >= 0.999) {
                requestAnimationFrame(resetExperienceLoop);
            }
        },



    });




    document.querySelector('.loader a').addEventListener('click',()=>{
        siteAudio.start();

        gsap.to('.loader',{
            opacity: 0,
            duration: 1.2,
            onComplete: ()=> {
                gsap.set('.loader',{
                    display: 'none'
                })




            }
        })

        gsap.to({ reveal: 0 },{
            reveal: 1,
            duration: 2,
            delay: .5,
            onUpdate:()=> scene.setTextSectionReveal('scrollTo', gsap.getProperty(arguments[0], 'reveal')),
            onComplete: ()=> {
                lenis.start()
            }

        })

        gsap.to('.canvas',{
            opacity: 1,
            duration: 1.2,
        })

        gsap.to('.header',{
            y: 0,
            yPercent: 0,
            duration: 1.2,
        })
        gsap.to('.footer',{
            y: 0,
            yPercent: 0,
            duration: 1.2,
        })
    })

    cleanupScene = () => {
        cancelAnimationFrame(audioWaveRaf);
        [
            mainTimeline, subtimeline1, subtimeline2, subtimeline3,
            subtimeline4, subtimeline5, subtimelinePortfolio, subtimelineHand,
            subtimeline6, tlExp, tlExpDepth, tlEnd,
        ].forEach((tl) => { tl && tl.kill(); });
        if (restoreOpacityTween) restoreOpacityTween.kill();
        ScrollTrigger.getAll().forEach((t) => t.kill());
        try { scene && scene.destroy(); } catch (e) { /* noop */ }
        try { resources && resources.destroy(); } catch (e) { /* noop */ }
    };
};

resources.on('end', () => {
    loaderActualProgress = 1;
    tweenLoaderProgress(1.5, 0.75, "power2.out", () => {
        window.setTimeout(initMainScene, 80);
    });
});

return () => {
    if (destroyed) {
        return;
    }
    destroyed = true;
    themeObserver.disconnect();
    if (loaderProgressTween) loaderProgressTween.kill();
    if (cleanupScene) {
        cleanupScene();
    } else {
        ScrollTrigger.getAll().forEach((t) => t.kill());
        try { scene && scene.destroy(); } catch (e) { /* noop */ }
        try { resources && resources.destroy(); } catch (e) { /* noop */ }
    }
    gsap.killTweensOf(['.loader', '.loader a', '.loader i', '.canvas', '.header', '.footer']);
    try { lenis.destroy(); } catch (e) { /* noop */ }
    try {
        const ctx = resources.loader && resources.loader.audioContext;
        if (ctx && ctx.state !== 'closed') ctx.close();
    } catch (e) { /* noop */ }
};
}
