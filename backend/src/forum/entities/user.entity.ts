import { Entity, Column, PrimaryColumn } from 'typeorm';

@Entity('forum_users')
export class ForumUser {
  @PrimaryColumn({ length: 60 })
  username: string;

  @Column({ length: 255, nullable: true })
  avatar: string;

  @Column({ default: 0 })
  karma: number;
}
