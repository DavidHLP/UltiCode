import { Entity, Column, PrimaryColumn } from 'typeorm';

@Entity('global_rankings')
export class GlobalRanking {
  @PrimaryColumn({ length: 40 })
  id: string;

  @Column({ length: 40, unique: true })
  user_id: string;

  @Column({ length: 120 })
  username: string;

  @Column()
  global_rank: number;

  @Column({ default: 1500 })
  rating: number;

  @Column({ default: 1500 })
  max_rating: number;

  @Column({ default: 0 })
  contests_attended: number;

  @Column({ length: 255, nullable: true })
  avatar: string;

  @Column({ length: 10, default: 'CN' })
  country: string;

  @Column({ length: 50, nullable: true })
  badge: string;
}
