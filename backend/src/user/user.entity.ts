import { Entity, Column, PrimaryColumn } from 'typeorm';

export enum UserRole {
  USER = 'USER',
  MODERATOR = 'MODERATOR',
  ADMIN = 'ADMIN',
  SUPER_ADMIN = 'SUPER_ADMIN',
}

@Entity('users')
export class User {
  @PrimaryColumn({ length: 40 })
  id: string;

  @Column({ length: 120, unique: true })
  username: string;

  @Column({ length: 120, nullable: true })
  name: string;

  @Column({ length: 255, nullable: true })
  email: string;

  @Column({ length: 255, nullable: true })
  avatar: string;

  @Column({ length: 255, nullable: true })
  password?: string;

  @Column({ type: 'text', nullable: true })
  bio?: string;

  @Column({ length: 255, nullable: true })
  website?: string;

  @Column({ length: 255, nullable: true })
  github?: string;

  @Column({ length: 255, nullable: true })
  twitter?: string;

  @Column({ length: 255, nullable: true })
  location?: string;

  @Column({ length: 255, nullable: true })
  company?: string;

  @Column({ length: 50, nullable: true })
  preferred_language?: string;

  @Column({ type: 'timestamp', default: () => 'CURRENT_TIMESTAMP' })
  joined_at: Date;

  // Admin fields
  @Column({
    type: 'enum',
    enum: UserRole,
    default: UserRole.USER,
  })
  role: UserRole;

  @Column({ type: 'boolean', default: true })
  is_active: boolean;

  @Column({ type: 'boolean', default: false })
  is_banned: boolean;

  @Column({ type: 'timestamp', nullable: true })
  banned_until?: Date;

  @Column({ type: 'text', nullable: true })
  banned_reason?: string;

  @Column({ type: 'timestamp', nullable: true })
  last_login_at?: Date;

  @Column({ length: 40, nullable: true })
  created_by?: string;

  @Column({ length: 40, nullable: true })
  updated_by?: string;
}
