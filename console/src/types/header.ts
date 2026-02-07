export interface HeaderModel {
  id: number;
  index: number;
  title: string;
  icon: string;
  color?: string;
  iconColor?: string;
}

export interface HeaderGroup {
  id: string;
  name: string;
  headers: HeaderModel[];
}

export interface LayoutNode {
  id: string;
  type: "container" | "leaf";
  direction?: "horizontal" | "vertical";
  size?: number;
  children?: LayoutNode[];
  groupId?: string;
  groupMetadata?: {
    id: string;
    name: string;
  };
}
