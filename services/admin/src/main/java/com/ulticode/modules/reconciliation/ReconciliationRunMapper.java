package com.ulticode.modules.reconciliation;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * MyBatis-Plus mapper for {@link ReconciliationRun} (P5-RECONCILE-001).
 */
@Mapper
public interface ReconciliationRunMapper extends BaseMapper<ReconciliationRun> {

    /** Acquire a connection-scoped lock so scheduled replicas do not overlap. */
    @Select("SELECT GET_LOCK(#{lockName}, 0)")
    Integer tryAcquireLease(@Param("lockName") String lockName);

    /** Release the connection-scoped reconciliation lock. */
    @Select("SELECT RELEASE_LOCK(#{lockName})")
    Integer releaseLease(@Param("lockName") String lockName);
}
