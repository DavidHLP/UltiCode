import { Annotations, AnnotationItem } from '@unovis/ts';
export declare const VisAnnotationsSelectors: typeof import("@unovis/ts/dist/components/annotations/style");
declare const _default: import("vue").DefineComponent<import("vue").ExtractPropTypes<{
    items: {
        type: import("vue").PropType<AnnotationItem[]>;
        required: true;
    };
    duration: {
        type: import("vue").PropType<number>;
    };
    events: {
        type: import("vue").PropType<{
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
        }>;
    };
    attributes: {
        type: import("vue").PropType<{
            [selector: string]: {
                [attr: string]: string | number | boolean | ((datum: any) => string | number | boolean);
            };
        }>;
    };
    data: {
        type: import("vue").PropType<never>;
    };
}>, {
    component: import("vue").Ref<Annotations, Annotations>;
}, {}, {}, {}, import("vue").ComponentOptionsMixin, import("vue").ComponentOptionsMixin, {}, string, import("vue").PublicProps, Readonly<import("vue").ExtractPropTypes<{
    items: {
        type: import("vue").PropType<AnnotationItem[]>;
        required: true;
    };
    duration: {
        type: import("vue").PropType<number>;
    };
    events: {
        type: import("vue").PropType<{
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
        }>;
    };
    attributes: {
        type: import("vue").PropType<{
            [selector: string]: {
                [attr: string]: string | number | boolean | ((datum: any) => string | number | boolean);
            };
        }>;
    };
    data: {
        type: import("vue").PropType<never>;
    };
}>> & Readonly<{}>, {}, {}, {}, {}, string, import("vue").ComponentProvideOptions, true, {}, any>;
export default _default;
