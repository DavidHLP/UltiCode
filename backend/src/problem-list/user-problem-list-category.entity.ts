import {
  Entity,
  Column,
  PrimaryGeneratedColumn,
  OneToMany,
  Index,
} from 'typeorm';
import { UserProblemListSave } from './user-problem-list-save.entity';

@Entity('user_problem_list_categories')
@Index(['user_id'])
export class UserProblemListCategory {
  @PrimaryGeneratedColumn('uuid', { name: 'id' })
  id: string;

  @Column({ length: 40 })
  user_id: string;

  @Column({ length: 120 })
  name: string;

  @Column({ default: 0 })
  sort_order: number;

  @Column({ type: 'datetime', default: () => 'CURRENT_TIMESTAMP' })
  created_at: Date;

  @OneToMany(() => UserProblemListSave, (save) => save.category)
  savedLists: UserProblemListSave[];
}
