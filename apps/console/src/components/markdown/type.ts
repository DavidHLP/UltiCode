export interface MarkdownEditProps {
  modelValue?: string;
  defaultValue?: string;
  hideHeader?: boolean;
  readOnly?: boolean;
  editorClass?: string;
}

export interface MarkdownViewProps {
  /** Markdown 内容 */
  content: string;
  /** 编辑器 ID，用于区分多个实例 */
  editorId?: string;
}
