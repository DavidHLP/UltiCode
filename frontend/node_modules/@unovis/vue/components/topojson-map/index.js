import { defineComponent as d, inject as m, computed as v, ref as f, onMounted as h, nextTick as k, onUnmounted as y, watch as s, createElementBlock as C, openBlock as F } from "vue";
import { TopoJSONMap as l } from "@unovis/ts";
import { useForwardProps as _, arePropsEqual as x } from "../../utils/props.js";
import { componentAccessorKey as B } from "../../utils/context.js";
const T = { "data-vis-component": "" }, j = l.selectors, D = /* @__PURE__ */ d({
  __name: "index",
  props: {
    projection: { type: [Function, Object] },
    topojson: {},
    mapFeatureName: {},
    mapFitToPoints: { type: Boolean },
    zoomFactor: {},
    disableZoom: { type: Boolean },
    zoomExtent: {},
    zoomDuration: {},
    linkWidth: {},
    linkColor: {},
    linkCursor: {},
    linkId: {},
    linkSource: { type: Function },
    linkTarget: { type: Function },
    areaId: {},
    areaColor: {},
    areaCursor: {},
    pointColor: {},
    pointRadius: {},
    pointStrokeWidth: {},
    pointCursor: {},
    longitude: {},
    latitude: {},
    pointLabel: {},
    pointLabelPosition: {},
    pointLabelTextBrightnessRatio: {},
    pointId: { type: Function },
    heatmapMode: { type: Boolean },
    heatmapModeBlurStdDeviation: {},
    heatmapModeZoomLevelThreshold: {},
    duration: {},
    events: {},
    attributes: {},
    data: {}
  },
  setup(c, { expose: u }) {
    const t = m(B), i = c, a = v(() => t.data.value ?? i.data), n = _(i), e = f();
    return h(() => {
      k(() => {
        var o;
        e.value = new l(n.value), (o = e.value) == null || o.setData(a.value), t.update(e.value);
      });
    }), y(() => {
      var o;
      (o = e.value) == null || o.destroy(), t.destroy();
    }), s(n, (o, r) => {
      var p;
      x(o, r) || (p = e.value) == null || p.setConfig(n.value);
    }), s(a, () => {
      var o;
      (o = e.value) == null || o.setData(a.value);
    }), u({
      component: e
    }), (o, r) => (F(), C("div", T));
  }
});
export {
  j as VisTopoJSONMapSelectors,
  D as default
};
//# sourceMappingURL=index.js.map
