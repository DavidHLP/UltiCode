package com.ulticode.modules.lease;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Persistent CAS operations for cross-replica singleton leases. */
@Mapper
public interface FencedJobLeaseMapper {

    @Insert("""
            INSERT INTO fenced_job_leases
                (lease_name, fence_token, owner_token, leased_until, updated_at)
            VALUES
                (#{leaseName}, 1, #{ownerToken},
                 TIMESTAMPADD(MICROSECOND, #{leaseMicros}, CURRENT_TIMESTAMP(3)),
                 CURRENT_TIMESTAMP(3))
            ON DUPLICATE KEY UPDATE
                fence_token = IF(leased_until IS NULL
                    OR leased_until <= CURRENT_TIMESTAMP(3), fence_token + 1, fence_token),
                owner_token = IF(leased_until IS NULL
                    OR leased_until <= CURRENT_TIMESTAMP(3), VALUES(owner_token), owner_token),
                leased_until = IF(leased_until IS NULL
                    OR leased_until <= CURRENT_TIMESTAMP(3), VALUES(leased_until), leased_until),
                updated_at = IF(leased_until IS NULL
                    OR leased_until <= CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), updated_at)
            """)
    int acquireLease(@Param("leaseName") String leaseName,
                     @Param("ownerToken") String ownerToken,
                     @Param("leaseMicros") long leaseMicros);

    @Select("SELECT lease_name, fence_token, owner_token, leased_until, updated_at "
            + "FROM fenced_job_leases WHERE lease_name = #{leaseName}")
    @Results(id = "fencedJobLease", value = {
            @Result(column = "lease_name", property = "leaseName"),
            @Result(column = "fence_token", property = "fenceToken"),
            @Result(column = "owner_token", property = "ownerToken"),
            @Result(column = "leased_until", property = "leasedUntil"),
            @Result(column = "updated_at", property = "updatedAt")
    })
    FencedJobLease findByName(@Param("leaseName") String leaseName);

    @Update("UPDATE fenced_job_leases "
            + "SET leased_until = TIMESTAMPADD(MICROSECOND, #{leaseMicros}, CURRENT_TIMESTAMP(3)), "
            + "updated_at = CURRENT_TIMESTAMP(3) "
            + "WHERE lease_name = #{leaseName} AND owner_token = #{ownerToken} "
            + "AND fence_token = #{fenceToken} AND leased_until > CURRENT_TIMESTAMP(3)")
    int renewLease(@Param("leaseName") String leaseName,
                   @Param("ownerToken") String ownerToken,
                   @Param("fenceToken") long fenceToken,
                   @Param("leaseMicros") long leaseMicros);

    @Update("UPDATE fenced_job_leases SET owner_token = NULL, leased_until = NULL, "
            + "updated_at = CURRENT_TIMESTAMP(3) "
            + "WHERE lease_name = #{leaseName} AND owner_token = #{ownerToken} "
            + "AND fence_token = #{fenceToken}")
    int releaseLease(@Param("leaseName") String leaseName,
                     @Param("ownerToken") String ownerToken,
                     @Param("fenceToken") long fenceToken);

    @Select("SELECT COUNT(*) FROM fenced_job_leases "
            + "WHERE lease_name = #{leaseName} AND owner_token = #{ownerToken} "
            + "AND fence_token = #{fenceToken} AND leased_until > CURRENT_TIMESTAMP(3)")
    int isHeld(@Param("leaseName") String leaseName,
               @Param("ownerToken") String ownerToken,
               @Param("fenceToken") long fenceToken);
}
