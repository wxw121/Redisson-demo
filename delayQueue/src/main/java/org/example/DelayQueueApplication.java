package org.example;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 延迟队列应用程序入口
 */
@EnableAsync // 开启异步支持，用于事件处理
@SpringBootApplication
@EnableTransactionManagement
@MapperScan("org.example.mapper")
public class DelayQueueApplication {
    public static void main(String[] args) {
        SpringApplication.run(DelayQueueApplication.class, args);
    }
}
