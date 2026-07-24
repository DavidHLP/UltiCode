# 落地页一比一复刻 · 逐区核对清单

参考源:`landing/mirror/site/`(陈野 Chen Ye 换皮版镜像,只读)。
真源码:`landing/source/extracted/assets/src/js/`(sourcemap 提取,注意其中场景文案为换皮前原文,移植时须按 `landing/REPLACE_GUIDE.md` 应用 24 条场景文案替换 + DOM 文案)。
技术拆解:`landing/TEARDOWN.md`。参考截图:`landing/RECON/screenshots/clone-scroll-*.png`。

核对维度(每区):DOM 结构 / 文案 / 颜色 / 字体 / 资源 / 行为。

- [x] 1. 加载器/进入屏:`.loader`("加载中 / 进入")、真实+trickle 混合进度条(150% 过冲)、点击进入后 loader 淡出、`.canvas` opacity 0→1(1.2s)、header/footer 滑入、Lenis start
- [x] 2. Header:左 `Chen Ye`(javascript:void(0))、右 `聊聊`(mailto:hello@chenye.works),类名与镜像一致
- [x] 3. 首屏:MSDF 大字 "SCROLL TO EXPLORE"、desert 沙尘场、hand 点云(15000)、fog 双层 FBM、MousePointer 拖尾、鼠标视差
- [x] 4. VISION 段:字母汇聚大字 VISION、about 6 行宣言(reveal 0→1→2 擦除,换皮后文案,MSDF 71 字符集)、相机 → path[1]
- [x] 5. CRAFT 段:大字 CRAFT、CatmullRom 9 点环游(duration 3.5)、音频 crossfade 到 craft
- [x] 6. 机构宣言 4 行(agency,换皮后文案)
- [x] 7. 客户星图 8 词:Nebula / Kaiten / Molin Roasters / Velocita / Ostra / Fable / Solis / Nordwind Tek(desktop/mobile 各一次)
- [x] 8. EXPERIENCE 转场:大字 EXPERIENCE、light 柱→星野、desert 镜像分裂(uSplitProgress)、粒子组旋转 z:-3.14、音频 crossfade 到 experience
- [x] 9. 奖项隧道:6 张虚构奖项卡(ui/ux/innovation/hm/wod/jury)、轨道移动总行程 66.56、焦点曲线 + 余光 0.18、独立 RT(MSAA×4)+ 屏幕片(鼠标扭曲+色差)、recognition 4 行
- [x] 10. 黑洞收尾:desert 黑洞漩涡(uBlackHoleProgress→0.35)、finalClaim "made to be felt."、desert z→30
- [x] 11. Footer:GitHub / Twitter / Instagram(javascript:void(0))、audio toggle 按钮(双正弦假波形 polyline、aria-label/pressed)
- [x] 12. 无缝循环:progress ≥0.999 → 全站归零 + scrollTo(0) → 0.8s 拉回
- [x] 13. 音频系统:3 轨 loop 交叉淡入(2.2s 线性斜坡、targetVolume 0.72)、hover one-shot(±4% 变调、60ms 节流)、静音 ramp 0.25s、手势解锁(代码逐行移植;headless 冒烟无法听声,以 0 console 错误旁证)
- [x] 14. 响应式/mobile:mobile 参数分支(黑洞半径 3.034、粒子下限 0.3 等)、Lenis syncTouch、竖屏布局对照(390×844 冒烟通过)
- [x] 15. 全局样式:body 背景 rgb(21,21,21)、IBM Plex Mono(本地 woff2)、bundle.css 样式完整落地(挂载时注入 <style>、卸载时移除)、与 console Tailwind 无互相污染
- [x] 16. 资源:scene.glb(draco)、particles ×3、cloud.png、awards ×6、音频 ×4、MSDF 字库 ×2 全部本地化(`console/public/landing/`)且加载无 404

## 移植说明(2026-07-23)

- 移植目标:`console/src/views/landing/`。`experience/` 为 extracted 源码的逐文件移植(.js,保持原文件划分);着色器由 webpack `module.exports` 内联改为 `?raw` 导入并解包为纯 GLSL;`window.template_url`/`window.assets` 收敛为 `experience/config.js`(`/landing` 前缀);`main.js` 由模块自执行改为 `initLandingExperience()` 工厂,返回完整销毁函数(ScrollTrigger/时间线/lenis/renderer/AudioContext/rAF 全部清理)。
- 换皮文案 24 条替换已全部应用(脚本校验计数:开场 6、机构 4、客户 8×2、奖项 3、收尾 1)。
- `vendor/three-msdf-text-utils.js` 的 `Font` 导入由 `three/examples/jsm/Addons.js` 改为 `.../loaders/FontLoader.js`(避免全量 Addons 打进 chunk,three r170 兼容)。
- `postprocessing@6.39.0` 经 pnpm 安装;three r170 与 r178 无阻塞性 API 差异。
- `experience/`、`vendor/` 为 verbatim 移植/第三方产物,已加入 eslint globalIgnores;`experience/main.d.ts` 提供入口类型。

## Solarized 主题化(2026-07-23)

- 落地页颜色脱离镜像原值(纯黑/纯白),改为跟随应用主题:dark → Solarized Dark,light → Solarized Light。只读 `<html>` 的 `dark` class,不写 `data-theme`。
- DOM 侧:`styles/bundle.css` 末尾追加 `html.dark` / `html:not(.dark)` 覆盖段(body 背景、loader、header、footer、step 文字色)。
- WebGL 侧:新增 `experience/theme.js`(调色板 + `applySceneTheme`),`main.js` 用 MutationObserver 监听 class 变化并同步雾、desert/light 粒子、MSDF 文字(含描边)、清屏色;循环归零的雾色 lerp 起止色随主题更新。`MousePointer` 新增 `uColor` uniform(原着色器硬编码 `vec3(1.0)`)。
- 第 15 项「body 背景 rgb(21,21,21)」自此仅作为镜像基准记录,实际颜色以 Solarized 调色板为准。

## UltiCode 内容化 + i18n(2026-07-23)

- 故事主线由个人作品集改为 UltiCode(在线评测)诗意叙事,3D MSDF 文案保持英文(MSDF 图集仅含 ` !',-.0-9:;?A-Za-z` 71 字形,无法渲染中文,各语言一致):
  - 大字:VISION → CODE、EXPERIENCE → MASTERY、SCROLL TO EXPLORE → SCROLL TO RUN(CRAFT 保留)。
  - about 6 行 / agency 4 行 / recognition 4 行 / finalClaim 全部重写为评测叙事("made to be solved.")。
  - 客户星图 8 词改为编程语言:Python / Cpp / Java / Rust / Go / Kotlin / JavaScript / TypeScript("C++" 的 `+` 不在字符集,用 Cpp)。
- 奖项隧道 6 张 CODA 徽章卡替换为脚本生成的 UltiCode 特性卡(solve/run/compete/rank/share/ulticode,570×806,Solarized 强调色,同款版式),旧图已删除。
- DOM chrome 接入 vue-i18n(新增 `landing.*` 语言模块,zh-CN / en-US):loader、header 品牌(UltiCode)与 CTA(开始刷题 → /problemset)、footer 三个路由链接(题库/竞赛/讨论)、音频按钮 aria-label(`main.js` 经 `i18n.global.t` 取值)。
- Header 右侧按钮由 `border-radius:2rem` 圆形改为方形(`border-radius:0`),邮件链接改为站内路由。

## 验证记录

| 日期 | 验证方式 | 结果 |
|---|---|---|
| 2026-07-23 | `pnpm lint`(相关文件)、`pnpm type-check`、`pnpm test`(61 文件 539 用例)、`pnpm build` | 全部通过 |
| 2026-07-23 | Playwright 冒烟(channel=chrome + swiftshader),1440×900 与 390×844:loader 截图 → 点击「进入」→ 首屏/50%/100% 截图,另补 18%/62%/80% 分段截图;收集 console 错误 | 两种视口均 0 错误;首屏与 `clone-scroll-0.png` 构图一致(SCROLL TO EXPLORE 辉光字/沙丘粒子);18% VISION 字母汇聚、62% 星野、80% 奖项隧道(换皮奖项卡 + CODA/LUMEN 文案)、100% 无缝循环归零均符合预期;截图存 `/tmp/landing-smoke/` |
