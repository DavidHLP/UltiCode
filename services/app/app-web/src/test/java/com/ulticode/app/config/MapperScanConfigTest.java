package com.ulticode.app.config;

import com.ulticode.app.user.port.UserProfileReadMapper;
import com.ulticode.app.user.port.UserReadMapper;
import com.ulticode.modules.dashboard.mapper.DashboardAdminMapper;
import org.apache.ibatis.annotations.Mapper;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;

import static org.assertj.core.api.Assertions.assertThat;

class MapperScanConfigTest {

    @Test
    void mapperScanRegistersAnnotatedMappersButNotPlainOwnerPorts() {
        MapperScan scan = MapperScanConfig.class.getAnnotation(MapperScan.class);

        assertThat(scan.annotationClass()).isEqualTo(Mapper.class);
        assertThat(UserProfileReadMapper.class.isAnnotationPresent(Mapper.class)).isTrue();
        assertThat(UserReadMapper.class.isAnnotationPresent(Mapper.class)).isFalse();
        assertThat(scan.value()).contains("com.ulticode.modules.dashboard.mapper");
        assertThat(DashboardAdminMapper.class.isAnnotationPresent(Mapper.class)).isTrue();
    }
}
