import { Entity, Column, PrimaryColumn, ManyToMany } from 'typeorm';
import { SolutionMeta } from './solution-meta.entity';

@Entity('solution_badges')
export class SolutionBadge {
  @PrimaryColumn({ length: 40 })
  id: string;

  @Column({ length: 60 })
  label: string;

  @ManyToMany(() => SolutionMeta, (meta) => meta.badges)
  solutions: SolutionMeta[];
}
