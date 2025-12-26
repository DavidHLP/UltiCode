import { Entity, PrimaryColumn } from 'typeorm';

@Entity('forum_post_tag_relations')
export class ForumPostTagRelation {
  @PrimaryColumn({ name: 'post_id', length: 40 })
  postId: string;

  @PrimaryColumn({ name: 'tag_id', length: 40 })
  tagId: string;
}
