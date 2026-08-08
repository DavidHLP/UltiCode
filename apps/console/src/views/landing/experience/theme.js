// Solarized 主题配色(Ethan Schoonover),落地页专用。
// 落地页不写 `data-theme`/`dark` class(归 shared/theme 管),只读
// <html> 上的 `dark` class 来决定用哪套调色板;DOM 侧由 bundle.css
// 末尾的 html.dark / html:not(.dark) 规则负责,WebGL 场景侧由本模块负责。
import * as THREE from "three";

export const SOLARIZED = {
    base03: "#002b36",
    base02: "#073642",
    base01: "#586e75",
    base00: "#657b83",
    base0: "#839496",
    base1: "#93a1a1",
    base2: "#eee8d5",
    base3: "#fdf6e3",
    yellow: "#b58900",
    orange: "#cb4b16",
    red: "#dc322f",
    magenta: "#d33682",
    violet: "#6c71c4",
    blue: "#268bd2",
    cyan: "#2aa198",
    green: "#859900",
};

// dark: 页面为 Solarized Dark(base03 底、base1 强调)
// light: 页面为 Solarized Light(base3 底、base01 强调)
export const LANDING_THEMES = {
    dark: {
        clearColor: SOLARIZED.base03,
        fog: { dark: SOLARIZED.base03, light: SOLARIZED.base1 },
        // 无缝循环归零时雾色由 fogFade 渐变为 fog.light(原为纯黑→纯白)
        fogFade: SOLARIZED.base03,
        desert: { colorA: SOLARIZED.base01, colorB: SOLARIZED.base1 },
        light: { colorA: SOLARIZED.base01, colorB: SOLARIZED.base2 },
        text: SOLARIZED.base1,
        textStroke: SOLARIZED.base03,
        mouse: SOLARIZED.base1,
    },
    light: {
        clearColor: SOLARIZED.base3,
        fog: { dark: SOLARIZED.base2, light: SOLARIZED.base01 },
        fogFade: SOLARIZED.base3,
        desert: { colorA: SOLARIZED.base1, colorB: SOLARIZED.base01 },
        light: { colorA: SOLARIZED.base1, colorB: SOLARIZED.base00 },
        text: SOLARIZED.base01,
        textStroke: SOLARIZED.base3,
        mouse: SOLARIZED.base01,
    },
};

export function getLandingThemeName() {
    return document.documentElement.classList.contains("dark") ? "dark" : "light";
}

export function getLandingTheme() {
    return LANDING_THEMES[getLandingThemeName()];
}

// `applySceneTheme` was folded into `MainScene.setTheme` during the
// Scene deepening. This module now exports only the palette data and
// the DOM-side theme read helpers; subsystem knowledge lives on the
// seam.
