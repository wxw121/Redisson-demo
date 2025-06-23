package org.example.realtimestats.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson配置类
 */
@Configuration
public class RedissonConfig {

    @Value("${spring.redis.host:localhost}")
    private String host;

    @Value("${spring.redis.port:6379}")
    private int port;

    @Value("${spring.redis.password:}")
    private String password;

    @Value("${spring.redis.database:0}")
    private int database;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();

        // 构建Redis地址
        String address = String.format("redis://%s:%d", host, port);

        // 单节点模式配置
        config.useSingleServer()
                .setAddress(address)
                .setDatabase(database);

        // 如果设置了密码，则配置密码
        if (password != null && !password.isEmpty()) {
            config.useSingleServer().setPassword(password);
        }

        // 创建RedissonClient实例
        return Redisson.create(config);
    }
}