import { Entity, Column, PrimaryColumn, ManyToOne, JoinColumn } from 'typeorm';
import { ForumPost } from './post.entity';
import { ForumUser } from './user.entity';

@Entity('forum_comments')
export class ForumComment {
  @PrimaryColumn({ length: 40 })
  id: string;

  @Column({ name: 'post_id', length: 40 })
  postId: string;

  @ManyToOne(() => ForumPost)
  @JoinColumn({ name: 'post_id' })
  post: ForumPost;

  @Column({ name: 'parent_id', length: 40, nullable: true })
  parentId: string | null;

  @ManyToOne(() => ForumComment)
  @JoinColumn({ name: 'parent_id' })
  parent: ForumComment | null;

  @Column({ name: 'author_id', length: 60 })
  authorId: string;

  @ManyToOne(() => ForumUser)
  @JoinColumn({ name: 'author_id' })
  author: ForumUser;

  @Column({ type: 'text' })
  body: string;

  @Column({ type: 'text', nullable: true })
  markdown: string;

  @Column({ name: 'created_at' })
  createdAt: Date;

  @Column({ name: 'edited_at', nullable: true })
  editedAt: Date;

  @Column({ name: 'is_pinned', default: false })
  isPinned: boolean;

  @Column({ name: 'is_locked', default: false })
  isLocked: boolean;
}
