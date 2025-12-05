import { Entity, Column, PrimaryColumn, ManyToOne, JoinColumn } from 'typeorm';
import { ForumPost } from './forum-post.entity';
import { ForumUser } from './forum-user.entity';

@Entity('forum_comments')
export class ForumComment {
  @PrimaryColumn({ length: 40 })
  id: string;

  @Column({ length: 40 })
  post_id: string;

  @ManyToOne(() => ForumPost)
  @JoinColumn({ name: 'post_id' })
  post: ForumPost;

  @Column({ length: 40, nullable: true })
  parent_id: string | null;

  @ManyToOne(() => ForumComment)
  @JoinColumn({ name: 'parent_id' })
  parent: ForumComment | null;

  @Column({ length: 60 })
  author_id: string;

  @ManyToOne(() => ForumUser)
  @JoinColumn({ name: 'author_id' })
  author: ForumUser;

  @Column({ type: 'text' })
  body: string;

  @Column({ type: 'text', nullable: true })
  markdown: string;

  @Column({ default: 0 })
  upvotes: number;

  @Column()
  created_at: Date;

  @Column({ nullable: true })
  edited_at: Date;

  @Column({ default: false })
  is_pinned: boolean;

  @Column({ default: false })
  is_locked: boolean;
}
