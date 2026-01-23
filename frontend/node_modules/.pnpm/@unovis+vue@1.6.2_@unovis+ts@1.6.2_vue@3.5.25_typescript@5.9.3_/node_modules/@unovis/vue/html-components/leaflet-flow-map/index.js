import { defineComponent as f, computed as m, ref as u, onMounted as y, nextTick as v, onUnmounted as L, watch as c, createElementBlock as M, openBlock as w } from "vue";
import { LeafletFlowMap as p } from "@unovis/ts";
import { useForwardProps as g, arePropsEqual as F } from "../../utils/props.js";
const k = p.selectors, b = /* @__PURE__ */ f({
  __name: "index",
  props: {
    sourceLongitude: {},
    sourceLatitude: {},
    targetLongitude: {},
    targetLatitude: {},
    sourcePointRadius: {},
    sourcePointColor: {},
    flowParticleColor: {},
    flowParticleRadius: {},
    flowParticleSpeed: {},
    flowParticleDensity: {},
    onSourcePointClick: { type: Function },
    onSourcePointMouseEnter: { type: Function },
    onSourcePointMouseLeave: { type: Function },
    width: {},
    height: {},
    flyToDuration: {},
    fitViewPadding: {},
    zoomDuration: {},
    initialBounds: {},
    fitBoundsOnUpdate: {},
    fitViewOnInit: { type: Boolean },
    fitViewOnUpdate: { type: Boolean },
    style: {},
    styleDarkTheme: {},
    accessToken: {},
    attribution: {},
    renderer: {},
    onMapInitialized: { type: Function },
    onMapMoveZoom: { type: Function },
    onMapMoveStart: { type: Function },
    onMapMoveEnd: { type: Function },
    onMapZoomStart: { type: Function },
    onMapZoomEnd: { type: Function },
    onMapClick: { type: Function },
    pointLongitude: {},
    pointLatitude: {},
    pointId: {},
    pointShape: {},
    pointColor: {},
    pointRadius: {},
    pointLabel: {},
    pointLabelColor: {},
    pointBottomLabel: {},
    pointCursor: {},
    pointRingWidth: {},
    selectedPointId: {},
    clusterColor: {},
    clusterRadius: {},
    clusterLabel: {},
    clusterLabelColor: {},
    clusterBottomLabel: {},
    clusterRingWidth: {},
    clusterBackground: { type: Boolean },
    clusterExpandOnClick: { type: Boolean },
    clusteringDistance: {},
    colorMap: {},
    topoJSONLayer: {},
    tooltip: {},
    ariaLabel: {},
    duration: {},
    events: {},
    attributes: {},
    data: {}
  },
  setup(s, { expose: d }) {
    const i = s, o = m(() => i.data), n = g(i), t = u(), a = u();
    return y(() => {
      v(() => {
        a.value && (t.value = new p(a.value, n.value, o.value));
      });
    }), L(() => {
      var e;
      (e = t.value) == null || e.destroy();
    }), c(n, (e, l) => {
      var r;
      F(e, l) || (r = t.value) == null || r.setConfig(n.value);
    }), c(o, () => {
      var e;
      (e = t.value) == null || e.setData(o.value);
    }), d({
      component: t
    }), (e, l) => (w(), M("div", {
      "data-vis-leaflet-flow-map": "",
      ref_key: "elRef",
      ref: a
    }, null, 512));
  }
});
export {
  k as VisLeafletFlowMapSelectors,
  b as default
};
//# sourceMappingURL=index.js.map
