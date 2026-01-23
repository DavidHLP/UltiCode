import { Treemap, StringAccessor } from '@unovis/ts';
export declare const VisTreemapSelectors: typeof import("@unovis/ts/dist/components/treemap/style");
declare const _default: <Datum>(__VLS_props: any, __VLS_ctx?: {
    attrs: any;
    emit: any;
    slots: any;
}, __VLS_expose?: any, __VLS_setup?: Promise<{
    props: {
        duration?: number;
        events?: {
            [selector: string]: {
                touchstart?: import("@unovis/ts").VisEventCallback<any>;
                touchmove?: import("@unovis/ts").VisEventCallback<any>;
                touchend?: import("@unovis/ts").VisEventCallback<any>;
                mouseover?: import("@unovis/ts").VisEventCallback<any>;
                mousemove?: import("@unovis/ts").VisEventCallback<any>;
                mousedown?: import("@unovis/ts").VisEventCallback<any>;
                mouseup?: import("@unovis/ts").VisEventCallback<any>;
                click?: import("@unovis/ts").VisEventCallback<any>;
                auxclick?: import("@unovis/ts").VisEventCallback<any>;
                contextmenu?: import("@unovis/ts").VisEventCallback<any>;
                dblclick?: import("@unovis/ts").VisEventCallback<any>;
                mouseenter?: import("@unovis/ts").VisEventCallback<any>;
                mouseleave?: import("@unovis/ts").VisEventCallback<any>;
                mouseout?: import("@unovis/ts").VisEventCallback<any>;
                scroll?: import("@unovis/ts").VisEventCallback<any>;
                select?: import("@unovis/ts").VisEventCallback<any>;
                wheel?: import("@unovis/ts").VisEventCallback<any>;
                pointerdown?: import("@unovis/ts").VisEventCallback<any>;
                pointerup?: import("@unovis/ts").VisEventCallback<any>;
                pointerout?: import("@unovis/ts").VisEventCallback<any>;
                pointermove?: import("@unovis/ts").VisEventCallback<any>;
            };
        };
        attributes?: {
            [selector: string]: {
                [attr: string]: string | number | boolean | ((datum: any) => string | number | boolean);
            };
        };
        data?: Datum[];
        id?: (d: Datum, i: number) => string | number;
        value?: import("@unovis/ts").NumericAccessor<Datum>;
        layers: StringAccessor<Datum>[];
        labelOffsetX?: number;
        labelOffsetY?: number;
        labelFit?: import("@unovis/ts").FitMode;
        labelTrimMode?: import("@unovis/ts").TrimMode;
        numberFormat?: (value: number) => string;
        tileLabel?: (node: import("@unovis/ts").TreemapNode<Datum>) => string;
        tileColor?: import("@unovis/ts").ColorAccessor<import("@unovis/ts").TreemapNode<Datum>>;
        tilePadding?: number;
        tilePaddingTop?: number;
        labelInternalNodes?: boolean;
        tileBorderRadius?: number;
        tileBorderRadiusFactor?: number;
        enableLightnessVariance?: boolean;
        enableTileLabelFontSizeVariation?: boolean;
        tileLabelSmallFontSize?: number;
        tileLabelMediumFontSize?: number;
        tileLabelLargeFontSize?: number;
        showTileClickAffordance?: boolean;
        lightnessVariationAmount?: number;
        minTileSizeForLabel?: number;
    } & import("vue").VNodeProps & import("vue").AllowedComponentProps & import("vue").ComponentCustomProps;
    expose(exposed: import("vue").ShallowUnwrapRef<{
        component: import("vue").Ref<Treemap<Datum>, Treemap<Datum>>;
    }>): void;
    attrs: any;
    slots: {};
    emit: any;
}>) => import("vue").VNode<import("vue").RendererNode, import("vue").RendererElement, {
    [key: string]: any;
}> & {
    __ctx?: any;
};
export default _default;
