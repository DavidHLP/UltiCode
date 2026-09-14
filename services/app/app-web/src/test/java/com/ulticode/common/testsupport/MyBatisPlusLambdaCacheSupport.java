package com.ulticode.common.testsupport;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;

/**
 * Bootstraps the MyBatis-Plus lambda cache for non-Spring unit tests so
 * {@code LambdaQueryWrapper} / {@code LambdaUpdateWrapper} column references
 * resolve. {@code TableInfoHelper.initTableInfo(BuilderAssistant, Class)} is
 * the only public route that initialises a table outside a Spring context;
 * the {@code MapperBuilderAssistant} constructor changed across MyBatis-Plus
 * versions, so it is loaded reflectively from the shaded
 * {@code MybatisMapperBuilderAssistant} class.
 *
 * <p>Call once per entity in a {@code @BeforeAll} from any test that builds
 * a lambda wrapper against that entity without going through Spring.
 */
public final class MyBatisPlusLambdaCacheSupport {

    private MyBatisPlusLambdaCacheSupport() {
    }

    /**
     * Register {@code entityClass} with MyBatis-Plus so lambda wrappers can
     * resolve its column references.
     *
     * @param entityClass the entity to register
     */
    public static void register(Class<?> entityClass) {
        try {
            Class<?> assistantClass = Class.forName(
                    "com.baomidou.mybatisplus.core.MybatisMapperBuilderAssistant");
            MapperBuilderAssistant assistant =
                    (MapperBuilderAssistant) assistantClass
                            .getDeclaredConstructor(Configuration.class, String.class)
                            .newInstance(new Configuration(), "");
            TableInfoHelper.initTableInfo(assistant, entityClass);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Failed to register " + entityClass.getName()
                            + " with MyBatis-Plus TableInfoHelper", e);
        }
    }
}
