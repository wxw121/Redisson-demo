package org.example.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.example.cache.MultiLevelCache;
import org.example.listener.CacheEventListener;
import org.example.endpoint.CacheStats;
import org.redisson.api.RedissonClient;
import org.springframework.cache.support.AbstractCacheManager;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 多级缓存管理器
 * 管理本地缓存和分布式缓存的协同工作
 */
@Slf4j
public class MultiLevelCacheManager extends AbstractCacheManager {

    private final RedissonClient redissonClient;
    private final CacheProperties cacheProperties;
    private final CacheEventListener cacheEventListener;
    private final Map<String, MultiLevelCache> cacheMap = new ConcurrentHashMap<>();
    private final String nodeId;

    public MultiLevelCacheManager(
            RedissonClient redissonClient,
            CacheProperties cacheProperties,
            CacheEventListener cacheEventListener) {
        this.redissonClient = redissonClient;
        this.cacheProperties = cacheProperties;
        this.cacheEventListener = cacheEventListener;
        this.nodeId = generateNodeId();

        // 启动缓存事件监听器
        this.cacheEventListener.start();

        log.info("MultiLevelCacheManager initialized with nodeId: {}", nodeId);
    }

    @Override
    protected Collection<org.springframework.cache.Cache> loadCaches() {
        return Collections.emptyList();
    }

    @Override
    protected org.springframework.cache.Cache getMissingCache(String name) {
        return cacheMap.computeIfAbsent(name, this::createMultiLevelCache);
    }

    /**
     * 创建多级缓存
     * @param name 缓存名称
     * @return 多级缓存实例
     */
    private MultiLevelCache createMultiLevelCache(String name) {
        log.info("Creating multi-level cache: {}", name);

        // 获取缓存特定配置
        CacheProperties.CacheConfig cacheConfig = cacheProperties.getCaches().getOrDefault(
                name, cacheProperties.getDefaultConfig());

        // 创建Caffeine缓存
        Cache<Object, Object> caffeineCache = createCaffeineCache(name, cacheConfig);

        // 创建多级缓存
        MultiLevelCache multiLevelCache = new MultiLevelCache(
                name,
                caffeineCache,
                redissonClient,
                cacheProperties,
                nodeId,
                event -> {
                    cacheEventListener.publishEvent(event);
                    return null;
                }
        );

        return multiLevelCache;
    }

    /**
     * 创建Caffeine本地缓存
     * @param name 缓存名称
     * @param cacheConfig 缓存配置
     * @return Caffeine缓存实例
     */
    private Cache<Object, Object> createCaffeineCache(String name, CacheProperties.CacheConfig cacheConfig) {
        Caffeine<Object, Object> caffeineBuilder = Caffeine.newBuilder();

        // 设置初始容量
        if (cacheConfig.getLocal().getInitialCapacity() > 0) {
            caffeineBuilder.initialCapacity(cacheConfig.getLocal().getInitialCapacity());
        }

        // 设置最大容量
        if (cacheConfig.getLocal().getMaximumSize() > 0) {
            caffeineBuilder.maximumSize(cacheConfig.getLocal().getMaximumSize());
        }

        // 设置过期时间
        if (cacheConfig.getLocal().getExpireAfterWrite() > 0) {
            caffeineBuilder.expireAfterWrite(cacheConfig.getLocal().getExpireAfterWrite(), TimeUnit.MILLISECONDS);
        }

        if (cacheConfig.getLocal().getExpireAfterAccess() > 0) {
            caffeineBuilder.expireAfterAccess(cacheConfig.getLocal().getExpireAfterAccess(), TimeUnit.MILLISECONDS);
        }


        // 设置统计收集
        caffeineBuilder.recordStats();

        return caffeineBuilder.build();
    }

    /**
     * 生成唯一的节点ID
     * @return 节点ID
     */
    private String generateNodeId() {
        String configuredNodeId = cacheProperties.getNodeId();
        if (StringUtils.hasText(configuredNodeId)) {
            return configuredNodeId;
        }

        return UUID.randomUUID().toString();
    }
    



    /**
     * 获取节点ID
     * @return 节点ID
     */
    public String getNodeId() {
        return nodeId;
    }

    /**
     * 获取所有缓存的统计信息
     * @return 缓存统计信息，key为缓存名称，value为统计信息
     */
    public Map<String, CacheStats> getCacheStats() {
        Map<String, CacheStats> statsMap = new ConcurrentHashMap<>();
        
        cacheMap.forEach((cacheName, cache) -> {
            try {
                if (cache instanceof MultiLevelCache) {
                    MultiLevelCache multiLevelCache = (MultiLevelCache) cache;
                    CacheStats stats = multiLevelCache.getCacheStats();
                    if (stats != null) {
                        // 确保设置了缓存名称
                        stats.setCacheName(cacheName);
                        statsMap.put(cacheName, stats);
                    } else {
                        log.warn("No stats available for cache: {}", cacheName);
                        // 创建空的统计信息对象
                        CacheStats emptyStats = new CacheStats();
                        emptyStats.setCacheName(cacheName);
                        statsMap.put(cacheName, emptyStats);
                    }
                } else {
                    log.warn("Cache {} is not an instance of MultiLevelCache", cacheName);
                }
            } catch (Exception e) {
                log.error("Error getting stats for cache: " + cacheName, e);
                // 创建空的统计信息对象
                CacheStats errorStats = new CacheStats();
                errorStats.setCacheName(cacheName);
                statsMap.put(cacheName, errorStats);
            }
        });
        
        return statsMap;
    }

    /**
     * 清除所有缓存
     */
    public void clearAll() {
        log.info("Clearing all caches");
        cacheMap.values().forEach(MultiLevelCache::clear);
    }

//    /**
//     * 预热缓存
//     * @param cacheName 缓存名称
//     * @param keys 要预热的键集合
//     */
//    public void preloadCache(String cacheName, Collection<Object> keys) {
//        if (keys == null || keys.isEmpty()) {
//            return;
//        }
//
//        log.info("Preloading cache: {}, keys count: {}", cacheName, keys.size());
//        org.springframework.cache.Cache cache = getCache(cacheName);
//        if (cache == null) {
//            log.warn("Cache not found: {}", cacheName);
//            return;
//        }
//
//        // 这里只是示例，实际预热逻辑需要根据业务实现
//        // 通常需要从数据源加载数据并放入缓存
//    }
}
