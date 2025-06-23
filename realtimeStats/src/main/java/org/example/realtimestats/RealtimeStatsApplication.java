package org.example.realtimestats;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 实时统计应用程序入口类
 */
@SpringBootApplication
@EnableTransactionManagement
@MapperScan("org.example.realtimestats.mapper")
public class RealtimeStatsApplication {

    public static void main(String[] args) {
        SpringApplication.run(RealtimeStatsApplication.class, args);
    }
}