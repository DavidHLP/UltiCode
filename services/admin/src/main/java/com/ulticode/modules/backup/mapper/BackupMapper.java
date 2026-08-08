package com.ulticode.modules.backup.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.backup.entity.Backup;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BackupMapper extends BaseMapper<Backup> {
}
