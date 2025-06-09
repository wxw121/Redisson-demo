package org.example.endpoint;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.cache.MultiLevelCache;
import org.example.config.MultiLevelCacheManager;
import org.springframework.cache.Cache;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 缓存监控器
 * 提供缓存统计、清理和健康检查等功能
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheMonitor {

    private final MultiLevelCacheManager cacheManager;

    /**
     * 获取所有缓存的统计信息
     */
    public Map<String, CacheStats> getAllCacheStats() {
        Map<String, CacheStats> stats = new HashMap<>();
        Collection<String> cacheNames = cacheManager.getCacheNames();
        
        for (String cacheName : cacheNames) {
            CacheStats cacheStats = getCacheStats(cacheName);
            if (cacheStats != null) {
                stats.put(cacheName, cacheStats);
            }
        }
        
        return stats;
    }

    /**
     * 获取指定缓存的统计信息
     */
    public CacheStats getCacheStats(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache instanceof MultiLevelCache multiLevelCache) {
            return multiLevelCache.getStats();
        }
        return null;
    }

    /**
     * 清除指定缓存
     */
    public boolean clearCache(String cacheName) {
        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("Failed to clear cache: {}", cacheName, e);
            return false;
        }
    }

    /**
     * 清除所有缓存
     */
    public List<String> clearAllCaches() {
        List<String> clearedCaches = new ArrayList<>();
        Collection<String> cacheNames = cacheManager.getCacheNames();
        
        for (String cacheName : cacheNames) {
            if (clearCache(cacheName)) {
                clearedCaches.add(cacheName);
            }
        }
        
        return clearedCaches;
    }

    /**
     * 获取指定缓存的键列表（分页）
     */
    public List<String> getCacheKeys(String cacheName, int page, int pageSize) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache instanceof MultiLevelCache multiLevelCache) {
            return multiLevelCache.getKeys()
                    .stream()
                    .skip((long) (page - 1) * pageSize)
                    .limit(pageSize)
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    /**
     * 获取指定缓存的大小
     */
    public long getCacheSize(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache instanceof MultiLevelCache multiLevelCache) {
            return multiLevelCache.size();
        }
        return 0;
    }

    /**
     * 获取缓存健康信息
     */
    /**
     * 重置指定缓存的统计信息
     */
    public boolean resetStats(String cacheName) {
        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache instanceof MultiLevelCache multiLevelCache) {
                multiLevelCache.getStats().resetStats();
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("Failed to reset stats for cache: {}", cacheName, e);
            return false;
        }
    }

    /**
     * 重置所有缓存的统计信息
     */
    public List<String> resetAllStats() {
        List<String> resetCaches = new ArrayList<>();
        Collection<String> cacheNames = cacheManager.getCacheNames();
        
        for (String cacheName : cacheNames) {
            if (resetStats(cacheName)) {
                resetCaches.add(cacheName);
            }
        }
        
        return resetCaches;
    }

    public Map<String, Object> getHealthInfo() {
        Map<String, Object> healthInfo = new HashMap<>();
        Collection<String> cacheNames = cacheManager.getCacheNames();
        
        // 总体统计
        int totalCaches = cacheNames.size();
        long totalEntries = 0;
        int activeCaches = 0;
        
        // 各缓存状态
        Map<String, Object> cacheStatuses = new HashMap<>();
        
        for (String cacheName : cacheNames) {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache instanceof MultiLevelCache multiLevelCache) {
                Map<String, Object> cacheStatus = new HashMap<>();
                long size = multiLevelCache.size();
                CacheStats stats = multiLevelCache.getStats();
                
                totalEntries += size;
                activeCaches++;
                
                cacheStatus.put("size", size);
                cacheStatus.put("stats", stats);
                cacheStatus.put("status", "active");
                
                cacheStatuses.put(cacheName, cacheStatus);
            }
        }
        
        // 汇总信息
        healthInfo.put("totalCaches", totalCaches);
        healthInfo.put("activeCaches", activeCaches);
        healthInfo.put("totalEntries", totalEntries);
        healthInfo.put("cacheStatuses", cacheStatuses);
        healthInfo.put("timestamp", new Date());
        
        return healthInfo;
    }
}
