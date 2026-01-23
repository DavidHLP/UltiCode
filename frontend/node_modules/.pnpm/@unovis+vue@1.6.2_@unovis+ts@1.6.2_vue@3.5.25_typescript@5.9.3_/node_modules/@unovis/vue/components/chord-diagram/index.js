import { defineComponent as p, inject as m, computed as v, ref as g, onMounted as h, nextTick as f, onUnmounted as _, watch as l, createElementBlock as C, openBlock as k } from "vue";
import { ChordDiagram as c } from "@unovis/ts";
import { useForwardProps as x, arePropsEqual as L } from "../../utils/props.js";
import { componentAccessorKey as b } from "../../utils/context.js";
const D = { "data-vis-component": "" }, B = c.selectors, I = /* @__PURE__ */ p({
  __name: "index",
  props: {
    angleRange: {},
    cornerRadius: {},
    highlightedNodeId: {},
    highlightedLinkIds: {},
    linkColor: {},
    linkValue: {},
    nodeLevels: {},
    nodeWidth: {},
    nodeColor: {},
    nodeLabel: {},
    nodeLabelColor: {},
    nodeLabelAlignment: {},
    padAngle: {},
    radiusScaleExponent: {},
    duration: {},
    events: {},
    attributes: {},
    data: {}
  },
  setup(i, { expose: u }) {
    const t = m(b), r = i, a = v(() => t.data.value ?? r.data), n = x(r), o = g();
    return h(() => {
      f(() => {
        var e;
        o.value = new c(n.value), (e = o.value) == null || e.setData(a.value), t.update(o.value);
      });
    }), _(() => {
      var e;
      (e = o.value) == null || e.destroy(), t.destroy();
    }), l(n, (e, s) => {
      var d;
      L(e, s) || (d = o.value) == null || d.setConfig(n.value);
    }), l(a, () => {
      var e;
      (e = o.value) == null || e.setData(a.value);
    }), u({
      component: o
    }), (e, s) => (k(), C("div", D));
  }
});
export {
  B as VisChordDiagramSelectors,
  I as default
};
//# sourceMappingURL=index.js.map
