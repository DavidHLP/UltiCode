// 可标记实体的字段
export interface FlaggableEntity {
  is_flagged: boolean;
  flagged_reason: string | null;
  flagged_at: Date | null;
}

// 可软删除实体的字段
export interface SoftDeletableEntity {
  is_deleted: boolean;
  deleted_at: Date | null;
  deleted_by: string | null;
}

// 组合接口
export interface ModeratedEntity extends FlaggableEntity, SoftDeletableEntity {}

// Prisma 更新数据类型
export type FlagUpdateData = Pick<
  FlaggableEntity,
  'is_flagged' | 'flagged_at'
> & {
  flagged_reason?: string;
};

export type UnflagUpdateData = {
  is_flagged: false;
  flagged_at: null;
  flagged_reason: null;
};

export type SoftDeleteUpdateData = Pick<
  SoftDeletableEntity,
  'is_deleted' | 'deleted_at' | 'deleted_by'
>;

export type RestoreUpdateData = {
  is_deleted: false;
  deleted_at: null;
  deleted_by: null;
};
