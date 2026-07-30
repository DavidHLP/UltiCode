package com.ulticode.modules.admin.mapper;

import org.apache.ibatis.annotations.Select;

/**
 * Test-only mapper proving that backend-admin scans mapper subpackages.
 */
public interface AdminDatasourceCanaryMapper {

    @Select("SELECT 1")
    int selectOne();
}
