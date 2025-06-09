package org.example.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.example.listener.CacheEventListener;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 多级缓存配置类
 */
@Slf4j
@Configuration
@EnableCaching
public class MultiLevelCacheConfig {

    /**
     * 配置需要使用多级缓存的缓存名称
     */
    public static final Set<String> CACHE_NAMES = Set.of(
            "users"  // 用户缓存
    );

    /**
     * 配置Redisson客户端
     */
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        // 使用单节点模式
        config.useSingleServer()
                .setAddress("redis://localhost:6379")
                .setDatabase(0);

        return Redisson.create(config);
    }

    /**
     * 配置ObjectMapper，支持Java 8时间类型
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    /**
     * 配置多级缓存管理器
     */
    @Bean
    @Primary
    public CacheManager cacheManager(RedissonClient redissonClient, CacheProperties properties, CacheEventListener cacheEventListener) {
        log.info("初始化多级缓存管理器...");
        MultiLevelCacheManager cacheManager = new MultiLevelCacheManager(redissonClient, properties, cacheEventListener);
        
        // 预初始化所有缓存
        for (String cacheName : CACHE_NAMES) {
            cacheManager.getCache(cacheName);
            log.info("已初始化缓存: {}", cacheName);
        }
        
        log.info("多级缓存管理器初始化完成，共初始化 {} 个缓存", CACHE_NAMES.size());
        return cacheManager;
    }
}
