import type { InjectionKey, Component } from "vue";

export const PanelComponentMapKey: InjectionKey<Record<number, Component>> =
  Symbol("PanelComponentMap");
