package org.example.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.cache.MultiLevelCache;
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

    // Commented out as these are now managed by MultiLevelCacheConfig
    /*
    @Bean
    public CacheManager caffeineCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(localMaximumSize)
                .expireAfterWrite(localExpireAfterWrite, TimeUnit.SECONDS)
                .recordStats());
        return cacheManager;
    }
    */

    /*
    @Bean
    public CacheManager redisCacheManager() {
        GenericJackson2JsonRedisSerializer jsonRedisSerializer = new GenericJackson2JsonRedisSerializer();
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .prefixCacheNameWith(redisKeyPrefix)
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonRedisSerializer))
                .disableCachingNullValues()
                .entryTtl(Duration.ofSeconds(defaultTtl));

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(config)
                .transactionAware()
                .build();
    }
    */

//    /**
//     * 配置多级缓存管理器
//     */
//    @Primary
//    @Bean
//    public CacheManager multiLevelCacheManager(
//            CacheManager caffeineCacheManager,
//            CacheManager redisCacheManager,
//            RedisTemplate<String, Object> redisTemplate) {
//
//        SimpleCacheManager cacheManager = new SimpleCacheManager();
//        List<Cache> caches = new ArrayList<>();
//
//        // 为每个缓存创建多级缓存实例
//        Set<String> cacheNames = new HashSet<>(Arrays.asList(
//                "userCache",
//                "roleCache",

//                "permissionCache"
//        ));
//
//        for (String cacheName : cacheNames) {
//            // 获取本地缓存和Redis缓存
//            Cache localCache = caffeineCacheManager.getCache(cacheName);
//            Cache redisCache = redisCacheManager.getCache(cacheName);
//
//            if (localCache != null && redisCache != null) {
//                // 创建多级缓存
//                MultiLevelCache multiLevelCache = new MultiLevelCache(
//                        cacheName,
//                        localCache,
//                        redisCache,
//                        redisTemplate,
//                        createBloomFilter(cacheName),
//                        getCacheLock(cacheName)
//                );
//
//                // 设置缓存配置
//                multiLevelCache.setDefaultTtl(defaultTtl);
//                // 设置随机过期时间，防止缓存雪崩
//                multiLevelCache.setRandomTtl(true);
//                // 设置空值缓存时间，防止缓存穿透
//                multiLevelCache.setNullValueTtl(60);
//                // 设置重试次数
//                multiLevelCache.setMaxRetries(3);
//                // 设置重试延迟
//                multiLevelCache.setRetryDelay(100);
//
//                caches.add(multiLevelCache);
//            }
//        }
//
//        cacheManager.setCaches(caches);
//        return cacheManager;
//    }


    /**
     * 创建布隆过滤器
     */
    private RBloomFilter<String> createBloomFilter(String cacheName) {
        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter("bf:" + cacheName);
        // 初始化布隆过滤器
        bloomFilter.tryInit(bloomFilterExpectedInsertions, bloomFilterFpp);
        return bloomFilter;
    }

    /**
     * 获取缓存加载锁
     */
    private Object getCacheLock(String cacheName) {
        return cacheLocks.computeIfAbsent(cacheName, k -> new Object());
    }


    /**
     * 获取缓存统计信息
     */
    public Map<String, Object> getCacheStats(String cacheName) {
        Map<String, Object> stats = new HashMap<>();

        // 获取缓存实例
        Cache cache = cacheManager.getCache(cacheName);
        if (cache instanceof CaffeineCache) {
            CaffeineCache caffeineCache = (CaffeineCache) cache;
            com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache = caffeineCache.getNativeCache();
            stats.put("localCache", nativeCache.stats());
        }

        // 获取布隆过滤器统计
        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter("bf:" + cacheName);
        stats.put("bloomFilter", Map.of(
                "size", bloomFilter.count(),
                "expectedInsertions", bloomFilter.getExpectedInsertions(),
                "falseProbability", bloomFilter.getFalseProbability()
        ));

        return stats;
    }
}
