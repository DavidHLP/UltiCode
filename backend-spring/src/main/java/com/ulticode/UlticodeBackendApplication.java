package com.ulticode;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * UltiCode Backend Application.
 *
 * <p>Main entry point for the Spring Boot application providing REST API and WebSocket real-time
 * communication for the coding platform.
 */
@SpringBootApplication
@EnableScheduling
@MapperScan("com.ulticode.modules.*.mapper")
public class UlticodeBackendApplication {

  public static void main(String[] args) {
    SpringApplication.run(UlticodeBackendApplication.class, args);
  }
}
