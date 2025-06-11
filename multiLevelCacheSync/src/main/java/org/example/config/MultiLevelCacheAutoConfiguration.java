package org.example.config;

import lombok.extern.slf4j.Slf4j;
import org.example.listener.CacheEventListener;
import org.example.listener.RedissonCacheEventListener;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;

/**
 * 多级缓存自动配置
 */
@Slf4j
@AutoConfiguration
@ConditionalOnProperty(prefix = "cache", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MultiLevelCacheAutoConfiguration {

    /**
     * 配置缓存事件监听器
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(RedissonClient.class)
    public CacheEventListener cacheEventListener(
            RedissonClient redissonClient,
            CacheProperties cacheProperties) {
        log.info("Configuring RedissonCacheEventListener");
        return new RedissonCacheEventListener(
                redissonClient,
                null, // 先设置为null，后续会通过setter注入
                cacheProperties,
                cacheProperties.getNodeId()
        );
    }

    /**
     * 配置多级缓存管理器
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean
    @ConditionalOnBean(RedissonClient.class)
    public CacheManager cacheManager(
            RedissonClient redissonClient,
            CacheProperties cacheProperties,
            @Lazy CacheEventListener cacheEventListener) {
        log.info("Configuring MultiLevelCacheManager");
        return new MultiLevelCacheManager(
                redissonClient,
                cacheProperties,
                cacheEventListener
        );
    }

    /**
     * 配置缓存监控端点
     */
    @Bean
    @ConditionalOnProperty(prefix = "cache", name = "stats-enabled", havingValue = "true", matchIfMissing = true)
    public CacheMonitorEndpoint cacheMonitorEndpoint(MultiLevelCacheManager cacheManager) {
        log.info("Configuring CacheMonitorEndpoint");
        return new CacheMonitorEndpoint(cacheManager);
    }
}
