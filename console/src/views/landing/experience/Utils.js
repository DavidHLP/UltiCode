import {gsap} from "gsap";
import * as THREE from "three";
// Map number x from range [a, b] to [c, d]
const map = (x, a, b, c, d) => (x - a) * (d - c) / (b - a) + c;

// Linear interpolation
const lerp = (a, b, n) => (1 - n) * a + n * b;

const clamp = (num, min, max) => num <= min ? min : num >= max ? max : num;

// Gets the mouse position
const getMousePos = (e) => {
    let posx = 0;
    let posy = 0;
    if (!e) e = window.event;
    if (e.pageX || e.pageY) {
        posx = e.pageX;
        posy = e.pageY;
    }
    else if (e.clientX || e.clientY)    {
        posx = e.clientX + body.scrollLeft + document.documentElement.scrollLeft;
        posy = e.clientY + body.scrollTop + document.documentElement.scrollTop;
    }

    return { x : posx, y : posy }
};

// Generate a random float.
const getRandomFloat = (min, max) => (Math.random() * (max - min) + min).toFixed(2);

const isMobile = () => {
    return window.matchMedia('(max-width: 576px)').matches;
}

const isTablet = () => {
    return window.matchMedia('(min-width: 577px) and (max-width: 992px)').matches;
}

const isDesktop = () => {
    return window.matchMedia('(min-width: 992px)').matches;
}

const showHeader = (current) => {
    document.querySelector('.header').classList.add('active');

    if (typeof current != 'undefined') {
        document.querySelectorAll('.header__nav-lnk').forEach(el=>{
            el.classList.remove('active');
        });
        document.querySelector(current).classList.add('active');


        document.querySelectorAll('.mobile-menu a').forEach(el=>{
            el.classList.remove('active');
        });
        document.querySelector(current+'-mobile').classList.add('active');

    } else {
        document.querySelectorAll('.header__nav-lnk').forEach(el=>{
            el.classList.remove('active');
        });

        document.querySelectorAll('.mobile-menu a').forEach(el=>{
            el.classList.remove('active');
        });

    }


}

const hideHeader = () => {
    document.querySelector('.header').classList.remove('active');
}

function blurPlug() {
    const blurProperty = gsap.utils.checkPrefix("filter"),
        blurExp = /blur\((.+)?px\)/,
        getBlurMatch = target => (gsap.getProperty(target, blurProperty) || "").match(blurExp) || [];

    gsap.registerPlugin({
        name: "blur",
        get(target) {
            return +(getBlurMatch(target)[1]) || 0;
        },
        init(target, endValue) {
            let data = this,
                filter = gsap.getProperty(target, blurProperty),
                endBlur = "blur(" + endValue + "px)",
                match = getBlurMatch(target)[0],
                index;
            if (filter === "none") {
                filter = "";
            }
            if (match) {
                index = filter.indexOf(match);
                endValue = filter.substr(0, index) + endBlur + filter.substr(index + match.length);
            } else {
                endValue = filter + endBlur;
                filter += filter ? " blur(0px)" : "blur(0px)";
            }
            data.target = target;
            data.interp = gsap.utils.interpolate(filter, endValue);
        },
        render(progress, data) {
            data.target.style[blurProperty] = data.interp(progress);
        }
    });
};

function extendOrbitControlsWithResetAngles(controls) {
    /**
     * Animazione del solo orientamento (angoli) verso polar/azimuth desiderati.
     * Mantiene: target e distanza invariati.
     *
     * @param {number} polar   angolo polare (phi), es. Math.PI/3
     * @param {number} azimuth angolo azimutale (theta), es. 0
     * @param {object} opts    { duration, ease, clampToLimits, disableDuring }
     */
    controls.resetAngles = function (
        polar   = Math.PI / 3,
        azimuth = 0,
        opts    = {},
        callback = () => {}
    ) {
        const {
            duration       = 0.8,
            ease           = "power2.inOut",
            clampToLimits  = true,   // rispetta min/maxPolarAngle e min/maxAzimuthAngle
            disableDuring  = true    // disabilita input durante l’animazione
        } = opts;

        // opzionale: clamp ai limiti dei controls
        const clamp = (v, min, max) => Math.min(Math.max(v, min), max);

        if (clampToLimits) {
            if (Number.isFinite(controls.minPolarAngle) && Number.isFinite(controls.maxPolarAngle)) {
                polar = clamp(polar, controls.minPolarAngle, controls.maxPolarAngle);
            }
            if (Number.isFinite(controls.minAzimuthAngle) && Number.isFinite(controls.maxAzimuthAngle)) {
                azimuth = clamp(azimuth, controls.minAzimuthAngle, controls.maxAzimuthAngle);
            }
        }

        // stato attuale in coordinate sferiche relative al target
        const target = controls.target.clone();
        const curOffset = controls.object.position.clone().sub(target);
        const sph = new THREE.Spherical().setFromVector3(curOffset);
        const radius = sph.radius;

        // kill di eventuale tween precedente
        if (controls.__angleTween) {
            controls.__angleTween.kill();
            controls.__angleTween = null;
        }

        const state = { phi: sph.phi, theta: sph.theta }; // valori di partenza

        if (disableDuring) controls.enabled = false;

        controls.__angleTween = gsap.to(state, {
            phi: polar,
            theta: azimuth,
            duration,
            ease,
            onUpdate: () => {
                // ricostruisci posizione dalla sfera aggiornata
                const newPos = new THREE.Vector3()
                    .setFromSpherical(new THREE.Spherical(radius, state.phi, state.theta))
                    .add(target);

                controls.object.position.copy(newPos);
                controls.update();
            },
            onComplete: () => {
                if (disableDuring) controls.enabled = true;
                controls.__angleTween = null;
                controls.update();
                callback();
            }
        });

        return controls.__angleTween;
    };
}

export { extendOrbitControlsWithResetAngles, blurPlug, map, lerp, clamp, getMousePos, getRandomFloat, isDesktop, isTablet, isMobile, showHeader, hideHeader };
