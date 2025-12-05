import { Entity, Column, PrimaryColumn, OneToMany } from 'typeorm';
import { ProblemTagRelation } from './problem-tag-relation.entity';

@Entity('problem_tags')
export class ProblemTag {
  @PrimaryColumn({ length: 40 })
  id: string;

  @Column({ length: 120 })
  label: string;

  @OneToMany(() => ProblemTagRelation, (relation) => relation.tag)
  problemRelations: ProblemTagRelation[];
}
