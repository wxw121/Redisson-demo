package org.example.endpoint;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.cache.MultiLevelCache;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 缓存统计信息
 * 用于收集和展示缓存使用情况的统计数据
 */
@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CacheStats {

    private String cacheName;
    private Map<String, CacheStatInfo> cacheStats = new ConcurrentHashMap<>();
    private long totalRequests;
    private long totalHits;
    private long totalMisses;
    private long totalLoads;
    private long totalEvictions;
    private double hitRatio;
    private long totalLoadTime;
    private double averageLoadPenalty;

    /**
     * 获取指定缓存的统计信息
     * @param cacheName 缓存名称
     * @return 缓存统计信息
     */
    public CacheStatInfo getCacheStatInfo(String cacheName) {
        return cacheStats.computeIfAbsent(cacheName, name -> new CacheStatInfo(name));
    }

    /**
     * 更新总体统计信息
     */
    public void updateTotalStats() {
        totalRequests = 0;
        totalHits = 0;
        totalMisses = 0;
        totalLoads = 0;
        totalEvictions = 0;
        totalLoadTime = 0;

        for (CacheStatInfo statInfo : cacheStats.values()) {
            totalRequests += statInfo.getRequestCount();
            totalHits += statInfo.getHitCount();
            totalMisses += statInfo.getMissCount();
            totalLoads += statInfo.getLoads().sum();
            totalEvictions += statInfo.getEvictionCount();
            totalLoadTime += statInfo.getTotalLoadTime();
        }

        hitRatio = totalRequests == 0 ? 0.0 : (double) totalHits / totalRequests;
        averageLoadPenalty = totalLoads == 0 ? 0.0 : (double) totalLoadTime / totalLoads;
    }

    /**
     * 重置所有统计信息
     */
    public void resetStats() {
        cacheStats.values().forEach(CacheStatInfo::reset);
        totalRequests = 0;
        totalHits = 0;
        totalMisses = 0;
        totalLoads = 0;
        totalEvictions = 0;
        totalLoadTime = 0;
        hitRatio = 0.0;
        averageLoadPenalty = 0.0;
    }

    /**
     * 更新Caffeine缓存统计信息
     * @param caffeineStats Caffeine缓存统计信息
     */
    public void updateCaffeineStats(com.github.benmanes.caffeine.cache.stats.CacheStats caffeineStats) {
        CacheStatInfo statInfo = getCacheStatInfo(cacheName);
        statInfo.updateCaffeineStats(caffeineStats);
        updateTotalStats();
    }

    /**
     * 记录缓存命中
     * @param level 缓存级别名称
     */
    public void recordHit(String level) {
        CacheStatInfo statInfo = getCacheStatInfo(level);
        statInfo.recordHit();
        updateTotalStats();
    }

    /**
     * 记录缓存未命中
     * @param level 缓存级别名称
     */
    public void recordMiss(String level) {
        CacheStatInfo statInfo = getCacheStatInfo(level);
        statInfo.recordMiss();
        updateTotalStats();
    }

    /**
     * 记录缓存加载
     * @param level 缓存级别名称
     */
    public void recordLoad(String level) {
        CacheStatInfo statInfo = getCacheStatInfo(level);
        statInfo.recordLoad();
        updateTotalStats();
    }

    /**
     * 记录缓存放入操作
     * @param level 缓存级别名称
     */
    public void recordPut(String level) {
        CacheStatInfo statInfo = getCacheStatInfo(level);
        statInfo.recordPut();
        updateTotalStats();
    }

    /**
     * 记录缓存驱逐操作
     * @param level 缓存级别名称
     */
    public void recordEviction(String level) {
        CacheStatInfo statInfo = getCacheStatInfo(level);
        statInfo.recordEviction();
        updateTotalStats();
    }

    /**
     * 合并另一个CacheStats实例的统计信息
     * @param other 要合并的CacheStats实例
     */
    public void merge(CacheStats other) {
        if (other == null) return;
        
        other.getCacheStats().forEach((level, otherStats) -> {
            CacheStatInfo currentStats = getCacheStatInfo(level);
            currentStats.merge(otherStats);
        });
        
        updateTotalStats();
    }

    /**
     * 单个缓存的统计信息
     */
    @Data
    public static class CacheStatInfo {
        private String name;
        private long hitCount;
        private long missCount;
        private long requestCount;
        private long evictionCount;
        private long loadSuccessCount;
        private long loadFailureCount;
        private long totalLoadTime;
        private double hitRate;
        private double averageLoadPenalty;
        private long estimatedSize;
        private final LongAdder loads = new LongAdder();
        private final LongAdder hits = new LongAdder();
        private final LongAdder misses = new LongAdder();
        private final LongAdder puts = new LongAdder();
        private final LongAdder evictions = new LongAdder();

        public CacheStatInfo(String name) {
            this.name = name;
        }

        /**
         * 记录缓存放入操作
         */
        public void recordPut() {
            puts.increment();
        }

        /**
         * 记录缓存驱逐操作
         */
        public void recordEviction() {
            evictions.increment();
            this.evictionCount = evictions.sum();
        }

        /**
         * 合并另一个CacheStatInfo实例的统计信息
         * @param other 要合并的CacheStatInfo实例
         */
        public void merge(CacheStatInfo other) {
            if (other == null) return;
            
            // 合并计数器
            for (long i = 0; i < other.hits.sum(); i++) hits.increment();
            for (long i = 0; i < other.misses.sum(); i++) misses.increment();
            for (long i = 0; i < other.loads.sum(); i++) loads.increment();
            for (long i = 0; i < other.puts.sum(); i++) puts.increment();
            for (long i = 0; i < other.evictions.sum(); i++) evictions.increment();
            
            // 合并其他统计信息
            this.loadSuccessCount += other.loadSuccessCount;
            this.loadFailureCount += other.loadFailureCount;
            this.totalLoadTime += other.totalLoadTime;
            
            // 更新统计数据
            updateStats();
        }

        /**
         * 记录缓存命中
         */
        public void recordHit() {
            hits.increment();
            updateStats();
        }

        /**
         * 记录缓存未命中
         */
        public void recordMiss() {
            misses.increment();
            updateStats();
        }

        /**
         * 记录缓存加载
         */
        public void recordLoad() {
            loads.increment();
        }

        /**
         * 更新统计信息
         */
        private void updateStats() {
            this.requestCount = hits.sum() + misses.sum();
            this.hitCount = hits.sum();
            this.missCount = misses.sum();
            this.evictionCount = evictions.sum();
            this.hitRate = requestCount == 0 ? 0.0 : (double) hitCount / requestCount;
        }

        /**
         * 更新Caffeine缓存统计信息
         */
        public void updateCaffeineStats(com.github.benmanes.caffeine.cache.stats.CacheStats caffeineStats) {
            this.hitCount = caffeineStats.hitCount();
            this.missCount = caffeineStats.missCount();
            this.requestCount = caffeineStats.requestCount();
            this.loadSuccessCount = caffeineStats.loadSuccessCount();
            this.loadFailureCount = caffeineStats.loadFailureCount();
            this.totalLoadTime = caffeineStats.totalLoadTime();
            this.evictionCount = caffeineStats.evictionCount();
            this.hitRate = caffeineStats.hitRate();
            this.averageLoadPenalty = caffeineStats.averageLoadPenalty();
        }

        /**
         * 重置统计信息
         */
        public void reset() {
            hits.reset();
            misses.reset();
            loads.reset();
            hitCount = 0;
            missCount = 0;
            requestCount = 0;
            loadSuccessCount = 0;
            loadFailureCount = 0;
            totalLoadTime = 0;
            evictionCount = 0;
            hitRate = 0.0;
            averageLoadPenalty = 0.0;
            estimatedSize = 0;
        }
    }
}
