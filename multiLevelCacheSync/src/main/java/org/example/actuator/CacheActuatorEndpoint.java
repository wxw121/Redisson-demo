package org.example.actuator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 缓存Actuator端点
 * 提供Spring Boot Actuator的缓存监控功能
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Endpoint(id = "cache-actuator")
public class CacheActuatorEndpoint {

    private final CacheManager redisCacheManager;
    private final CacheManager caffeineCacheManager;
    private final RedissonClient redissonClient;

    /**
     * 获取所有缓存信息
     */
    @ReadOperation
    public Map<String, Object> cacheInfo() {
        Map<String, Object> info = new HashMap<>();

        // 系统信息
        info.put("timestamp", new Date());
        info.put("nodeId", UUID.randomUUID().toString().substring(0, 8));

        // Redis缓存信息
        info.put("redisCaches", getCacheNames(redisCacheManager));

        // 本地缓存信息
        info.put("localCaches", getCacheNames(caffeineCacheManager));

        // Redis服务器信息
        try {
            Map<String, String> redisInfo = new HashMap<>();
            redisInfo.put("clientName", redissonClient.getConfig().getTransportMode().toString());
            redisInfo.put("threads", String.valueOf(redissonClient.getConfig().getThreads()));
            redisInfo.put("nettyThreads", String.valueOf(redissonClient.getConfig().getNettyThreads()));
            info.put("redisInfo", redisInfo);
        } catch (Exception e) {
            log.error("Error getting Redis info", e);
            info.put("redisInfo", Collections.singletonMap("error", e.getMessage()));
        }

        return info;
    }

    /**
     * 获取指定缓存的详细信息
     */
    @ReadOperation
    public Map<String, Object> cacheDetails(@Selector String cacheName) {
        Map<String, Object> details = new HashMap<>();
        details.put("cacheName", cacheName);
        details.put("timestamp", new Date());

        // Redis缓存详情
        Cache redisCache = redisCacheManager.getCache(cacheName);
        if (redisCache != null) {
            details.put("redisCache", Collections.singletonMap("available", true));
        } else {
            details.put("redisCache", Collections.singletonMap("available", false));
        }

        // 本地缓存详情
        Cache localCache = caffeineCacheManager.getCache(cacheName);
        if (localCache != null) {
            details.put("localCache", Collections.singletonMap("available", true));

            // 如果是Caffeine缓存，尝试获取统计信息
            if (caffeineCacheManager instanceof CaffeineCacheManager) {
                try {
                    // 这里可以添加Caffeine缓存的统计信息
                    // 由于需要反射获取Caffeine的统计信息，这里简化处理
                    details.put("stats", Collections.singletonMap("enabled", true));
                } catch (Exception e) {
                    log.warn("Failed to get Caffeine stats", e);
                }
            }
        } else {
            details.put("localCache", Collections.singletonMap("available", false));
        }

        return details;
    }

    /**
     * 清除指定缓存
     */
    @WriteOperation
    public Map<String, Object> clearCache(@Selector String cacheName) {
        Map<String, Object> result = new HashMap<>();
        result.put("cacheName", cacheName);
        result.put("timestamp", new Date());

        boolean redisCleared = false;
        boolean localCleared = false;

        // 清除Redis缓存
        Cache redisCache = redisCacheManager.getCache(cacheName);
        if (redisCache != null) {
            redisCache.clear();
            redisCleared = true;
        }

        // 清除本地缓存
        Cache localCache = caffeineCacheManager.getCache(cacheName);
        if (localCache != null) {
            localCache.clear();
            localCleared = true;
        }

        result.put("redisCleared", redisCleared);
        result.put("localCleared", localCleared);
        result.put("success", redisCleared || localCleared);

        log.info("Cache '{}' cleared. Redis: {}, Local: {}", cacheName, redisCleared, localCleared);

        return result;
    }

    /**
     * 清除所有缓存
     */
    @WriteOperation
    public Map<String, Object> clearAllCaches() {
        Map<String, Object> result = new HashMap<>();
        result.put("timestamp", new Date());

        // 清除Redis缓存
        List<String> redisCaches = getCacheNames(redisCacheManager);
        for (String cacheName : redisCaches) {
            Cache cache = redisCacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        }

        // 清除本地缓存
        List<String> localCaches = getCacheNames(caffeineCacheManager);
        for (String cacheName : localCaches) {
            Cache cache = caffeineCacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        }

        result.put("redisCachesCleared", redisCaches);
        result.put("localCachesCleared", localCaches);
        result.put("success", true);

        log.info("All caches cleared. Redis caches: {}, Local caches: {}",
                redisCaches.size(), localCaches.size());

        return result;
    }

    /**
     * 获取缓存名称列表
     */
    private List<String> getCacheNames(CacheManager cacheManager) {
        return new ArrayList<>(cacheManager.getCacheNames());
    }
}
