package org.example;

import lombok.extern.slf4j.Slf4j;
import org.example.config.CacheProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.ApplicationContext;

/**
 * 多级缓存应用程序启动类
 */
@Slf4j
@EnableCaching
@SpringBootApplication
public class MultiLevelCacheApplication {

    public static void main(String[] args) {
        ApplicationContext context = SpringApplication.run(MultiLevelCacheApplication.class, args);

        // 打印应用信息
        String appName = context.getEnvironment().getProperty("spring.application.name");
        String port = context.getEnvironment().getProperty("server.port");
        String contextPath = context.getEnvironment().getProperty("server.servlet.context-path");

        log.info("----------------------------------------");
        log.info("Application is running!");
        log.info("Application Name: {}", appName);
        log.info("Server Port: {}", port);
        log.info("Context Path: {}", contextPath);
        log.info("Cache Monitor: http://localhost:{}{}/actuator/cache-monitor", port, contextPath);
        log.info("Swagger UI: http://localhost:{}{}/swagger-ui.html", port, contextPath);
        log.info("----------------------------------------");
    }
}
