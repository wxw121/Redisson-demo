package org.example.config;

import lombok.extern.slf4j.Slf4j;
import org.example.endpoint.CacheStats;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.actuate.health.Health;
import org.springframework.cache.Cache;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 缓存监控端点
 * 提供缓存监控和管理的REST API
 */
@Slf4j
@Endpoint(id = "cache-monitor")
public class CacheMonitorEndpoint {

    private final MultiLevelCacheManager cacheManager;

    public CacheMonitorEndpoint(MultiLevelCacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    /**
     * 获取所有缓存的统计信息
     */
    @ReadOperation
    public List<CacheStats> getCacheStats() {
        Map<String, CacheStats> statsMap = cacheManager.getCacheStats();

        return statsMap.values().stream()
                .sorted((s1, s2) -> s1.getCacheName().compareToIgnoreCase(s2.getCacheName()))
                .collect(Collectors.toList());
    }

    /**
     * 获取指定缓存的统计信息
     */
    @ReadOperation
    public CacheStats getCacheStats(@Selector String cacheName) {
        Map<String, CacheStats> statsMap = cacheManager.getCacheStats();
        return statsMap.getOrDefault(cacheName, CacheStats.builder().cacheName(cacheName).build());
    }

    /**
     * 清除指定缓存
     */
    @WriteOperation
    public Map<String, Object> clearCache(@Selector String cacheName) {
        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
                log.info("Cleared cache: {}", cacheName);
                return Map.of("status", "success", "message", "Cache cleared: " + cacheName);
            } else {
                return Map.of("status", "error", "message", "Cache not found: " + cacheName);
            }
        } catch (Exception e) {
            log.error("Error clearing cache: " + cacheName, e);
            return Map.of(
                "status", "error",
                "message", "Error clearing cache: " + cacheName,
                "error", e.getMessage()
            );
        }
    }

    /**
     * 清除所有缓存
     */
    @WriteOperation
    public Map<String, Object> clearAllCaches() {
        try {
            cacheManager.clearAll();
            log.info("Cleared all caches");
            return Map.of("status", "success", "message", "All caches cleared");
        } catch (Exception e) {
            log.error("Error clearing all caches", e);
            return Map.of(
                "status", "error",
                "message", "Error clearing all caches",
                "error", e.getMessage()
            );
        }
    }

    /**
     * 获取缓存健康状态
     */
    @ReadOperation
    public Health health() {
        try {
            Collection<String> cacheNames = cacheManager.getCacheNames();
            Set<String> activeCaches = cacheNames.stream()
                .filter(name -> cacheManager.getCache(name) != null)
                .collect(Collectors.toSet());

            Map<String, Object> details = new HashMap<>();
            details.put("totalCaches", cacheNames.size());
            details.put("activeCaches", activeCaches.size());
            details.put("nodeId", cacheManager.getNodeId());

            if (activeCaches.size() == cacheNames.size()) {
                return Health.up()
                    .withDetails(details)
                    .build();
            } else {
                return Health.down()
                    .withDetails(details)
                    .build();
            }
        } catch (Exception e) {
            return Health.down()
                .withException(e)
                .build();
        }
    }

    /**
     * 预热指定缓存
     */
    @WriteOperation
    public Map<String, Object> preloadCache(@Selector String cacheName) {
        try {
            // 这里只是一个示例，实际的预热逻辑需要根据业务实现
            // 通常需要从数据源加载数据并放入缓存
            log.info("Preloading cache: {}", cacheName);
            return Map.of(
                "status", "success",
                "message", "Cache preload initiated: " + cacheName,
                "note", "This is a placeholder. Implement actual preload logic."
            );
        } catch (Exception e) {
            log.error("Error preloading cache: " + cacheName, e);
            return Map.of(
                "status", "error",
                "message", "Error preloading cache: " + cacheName,
                "error", e.getMessage()
            );
        }
    }

    /**
     * 获取缓存配置信息
     */
    @ReadOperation
    public Map<String, Object> getCacheConfig(@Selector String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            return Map.of("error", "Cache not found: " + cacheName);
        }

        Map<String, Object> config = new HashMap<>();
        config.put("cacheName", cacheName);
        config.put("cacheType", cache.getClass().getSimpleName());

        // 如果需要，可以添加更多配置信息
        return config;
    }
}
