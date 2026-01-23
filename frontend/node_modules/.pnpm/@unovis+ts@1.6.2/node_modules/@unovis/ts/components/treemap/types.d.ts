import { HierarchyRectangularNode } from 'd3-hierarchy';
export declare type TreemapDatum<Datum> = {
    key?: string;
    index?: number;
    datum?: Datum;
};
export interface TreemapNode<Datum> extends HierarchyRectangularNode<TreemapDatum<Datum>> {
    _id: string;
    _fill?: string;
    _fillOpacity?: number | null;
}
