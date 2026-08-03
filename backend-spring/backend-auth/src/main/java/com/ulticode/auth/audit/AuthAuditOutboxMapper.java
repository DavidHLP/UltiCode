package com.ulticode.auth.audit;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Insert-only mapper for {@link AuthAuditOutboxRecord}
 * (P7-AUDIT-SINK-OWNER-BINDING-001).
 *
 * <p>Deliberately exposes only {@link BaseMapper} operations: the
 * {@code auth_rw} grant on {@code admin.audit_outbox} is INSERT-only, and
 * claiming/processing remains owned by the single dispatcher in backend-admin.
 * Do not add claim/process methods here.
 */
@Mapper
public interface AuthAuditOutboxMapper extends BaseMapper<AuthAuditOutboxRecord> {
}
