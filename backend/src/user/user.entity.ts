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
}
