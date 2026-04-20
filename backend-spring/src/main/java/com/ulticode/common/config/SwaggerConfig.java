package com.ulticode.common.config;

// TEMPORARILY DISABLED - springdoc 2.x incompatible with Spring Boot 3.2.5
// Re-enable when springdoc supports SB3
// @Configuration
public class SwaggerConfig {
    // @Bean
    // public OpenAPI openAPI() {
    //     return new OpenAPI()
    //         .info(new Info()
    //             .title("UltiCode API")
    //             .description("UltiCode API 文档")
    //             .version("1.0.0")
    //             .contact(new Contact()
    //                 .name("UltiCode Team")
    //                 .email("support@ulticode.com"))
    //             .license(new License()
    //                 .name("MIT License")
    //                 .url("https://opensource.org/licenses/MIT")))
    //         .addSecurityItem(new SecurityRequirement().addList("Bearer"))
    //         .components(new Components()
    //             .addSecuritySchemes("Bearer", new SecurityScheme()
    //                 .type(SecurityScheme.Type.HTTP)
    //                 .scheme("bearer")
    //                 .bearerFormat("JWT")
    //                 .description("JWT 认证，格式: Bearer {token}")));
    // }
}
