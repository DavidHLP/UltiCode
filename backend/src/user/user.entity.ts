import { Entity, Column, PrimaryColumn } from 'typeorm';

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
}
