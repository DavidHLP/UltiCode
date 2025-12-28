import { Entity, Column, PrimaryColumn } from 'typeorm';

@Entity('forum_users')
export class ForumUser {
  @PrimaryColumn({ length: 40 })
  id: string;

  @Column({ length: 60, unique: true })
  username: string;

  @Column({ length: 255, nullable: true })
  avatar: string | null;

  @Column({ default: 0 })
  karma: number;
}
