package com.ulticode.modules.i18n.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.i18n.entity.Translation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * MyBatis-Plus mapper for Translation entity.
 * Provides standard CRUD operations through BaseMapper plus custom queries.
 */
@Mapper
public interface TranslationMapper extends BaseMapper<Translation> {

    /**
     * Find all translations for a specific entity and locale.
     *
     * @param entityType the type of entity
     * @param entityId   the entity ID
     * @param locale     the locale code
     * @return list of translations
     */
    @Select("SELECT * FROM translations WHERE entity_type = #{entityType} AND entity_id = #{entityId} AND locale = #{locale}")
    List<Translation> findByEntityAndLocale(
            @Param("entityType") String entityType,
            @Param("entityId") String entityId,
            @Param("locale") String locale
    );

    /**
     * Find all translations for multiple entities and a specific locale.
     * Note: entityIds should be a comma-separated string of quoted IDs to prevent SQL injection.
     *
     * @param entityType the type of entity
     * @param entityIds  comma-separated string of entity IDs (pre-formatted)
     * @param locale     the locale code
     * @return list of translations
     */
    @Select("SELECT * FROM translations WHERE entity_type = #{entityType} AND entity_id IN (${entityIds}) AND locale = #{locale}")
    List<Translation> findByEntitiesAndLocale(
            @Param("entityType") String entityType,
            @Param("entityIds") String entityIds,
            @Param("locale") String locale
    );
}
