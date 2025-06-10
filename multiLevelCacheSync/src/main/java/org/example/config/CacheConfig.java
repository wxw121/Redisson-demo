package org.example.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.cache.MultiLevelCache;
import org.example.listener.CacheEventListener;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 缓存配置类
 * 实现多级缓存，并预防缓存雪崩和穿透
 */
@Slf4j
@Configuration
@EnableCaching
public class CacheConfig {

    @Value("${cache.default-ttl:3600}")
    private long defaultTtl;

    @Value("${cache.local.maximum-size:10000}")
    private long localMaximumSize;

    @Value("${cache.local.expire-after-write:300}")
    private long localExpireAfterWrite;

    @Value("${cache.redis.key-prefix:cache:}")
    private String redisKeyPrefix;

    @Value("${cache.bloom-filter.expected-insertions:1000000}")
    private long bloomFilterExpectedInsertions;

    @Value("${cache.bloom-filter.false-positive-probability:0.01}")
    private double bloomFilterFpp;

    private final RedisConnectionFactory redisConnectionFactory;
    private final RedissonClient redissonClient;

    // 缓存加载锁，防止缓存击穿
    private final Map<String, Object> cacheLocks = new ConcurrentHashMap<>();


    private final CacheManager cacheManager;

    public CacheConfig(RedisConnectionFactory redisConnectionFactory,
                       RedissonClient redissonClient,
                       CacheManager cacheManager) {
        this.redisConnectionFactory = redisConnectionFactory;
        this.redissonClient = redissonClient;
        this.cacheManager = cacheManager;
    }

    /**
     * 配置RedisTemplate
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate() {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);

        // 使用StringRedisSerializer来序列化和反序列化redis的key值
        template.setKeySerializer(new StringRedisSerializer());
        // 使用GenericJackson2JsonRedisSerializer来序列化和反序列化redis的value值
        GenericJackson2JsonRedisSerializer jsonRedisSerializer = new GenericJackson2JsonRedisSerializer();
        template.setValueSerializer(jsonRedisSerializer);

        // Hash的key也采用StringRedisSerializer的序列化方式
        template.setHashKeySerializer(new StringRedisSerializer());
        // Hash的value也采用GenericJackson2JsonRedisSerializer的序列化方式
        template.setHashValueSerializer(jsonRedisSerializer);

        template.afterPropertiesSet();
        return template;
    }



//    /**
//     * 创建布隆过滤器
//     */
//    private RBloomFilter<String> createBloomFilter(String cacheName) {
//        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter("bf:" + cacheName);
//        // 初始化布隆过滤器
//        bloomFilter.tryInit(bloomFilterExpectedInsertions, bloomFilterFpp);
//        return bloomFilter;
//    }
//
//    /**
//     * 获取缓存加载锁
//     */
//    private Object getCacheLock(String cacheName) {
//        return cacheLocks.computeIfAbsent(cacheName, k -> new Object());
//    }
//
//
//    /**
//     * 获取缓存统计信息
//     */
//    public Map<String, Object> getCacheStats(String cacheName) {
//        Map<String, Object> stats = new HashMap<>();
//
//        // 获取缓存实例
//        Cache cache = cacheManager.getCache(cacheName);
//        if (cache instanceof CaffeineCache) {
//            CaffeineCache caffeineCache = (CaffeineCache) cache;
//            com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache = caffeineCache.getNativeCache();
//            stats.put("localCache", nativeCache.stats());
//        }
//
//        // 获取布隆过滤器统计
//        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter("bf:" + cacheName);
//        stats.put("bloomFilter", Map.of(
//                "size", bloomFilter.count(),
//                "expectedInsertions", bloomFilter.getExpectedInsertions(),
//                "falseProbability", bloomFilter.getFalseProbability()
//        ));
//
//        return stats;
//    }
}
