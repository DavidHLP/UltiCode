// Solarized 主题配色(Ethan Schoonover),落地页专用。
// 落地页不写 `data-theme`/`dark` class(归 shared/theme 管),只读
// <html> 上的 `dark` class 来决定用哪套调色板;DOM 侧由 bundle.css
// 末尾的 html.dark / html:not(.dark) 规则负责,WebGL 场景侧由本模块负责。
// 16 个 canonical 值由 @ulticode/design-system 的 SOLARIZED_PALETTE
// 单点提供(TASK-004 公共接口),本模块不再本地复制调色板。
import { SOLARIZED_PALETTE } from '@ulticode/design-system';

// dark: 页面为 Solarized Dark(base03 底、base2 文字、cyan 强调)
// light: 页面为 Solarized Light(base3 底、base01 文字、blue 强调)
export const LANDING_THEMES = {
    dark: {
        clearColor: SOLARIZED_PALETTE.base03,
        fog: { dark: SOLARIZED_PALETTE.base03, light: SOLARIZED_PALETTE.base1 },
        // 无缝循环归零时雾色由 fogFade 渐变为 fog.light(原为纯黑→纯白)
        fogFade: SOLARIZED_PALETTE.base03,
        desert: { colorA: SOLARIZED_PALETTE.base01, colorB: SOLARIZED_PALETTE.cyan, glow: 2.5 },
        light: { colorA: SOLARIZED_PALETTE.cyan, colorB: SOLARIZED_PALETTE.base2, glow: 1.0 },
        text: SOLARIZED_PALETTE.base2,
        textStroke: SOLARIZED_PALETTE.base03,
        mouse: SOLARIZED_PALETTE.blue,
    },
    light: {
        clearColor: SOLARIZED_PALETTE.base3,
        fog: { dark: SOLARIZED_PALETTE.base2, light: SOLARIZED_PALETTE.base1 },
        fogFade: SOLARIZED_PALETTE.base3,
        desert: { colorA: SOLARIZED_PALETTE.base00, colorB: SOLARIZED_PALETTE.cyan, glow: 1.1 },
        light: { colorA: SOLARIZED_PALETTE.cyan, colorB: SOLARIZED_PALETTE.base1, glow: 0.8 },
        text: SOLARIZED_PALETTE.base01,
        textStroke: SOLARIZED_PALETTE.base0,
        mouse: SOLARIZED_PALETTE.blue,
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
