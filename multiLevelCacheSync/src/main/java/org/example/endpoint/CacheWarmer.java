package org.example.endpoint;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 缓存预热器
 * 在应用启动时预热缓存，避免系统启动初期的性能问题
 */
@Slf4j
@Component
public class CacheWarmer implements ApplicationListener<ApplicationStartedEvent> {


    private final boolean warmEnabled;


    private final int threadPoolSize;


    private final int timeoutSeconds;

    private final CacheManager multiLevelCacheManager;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedissonClient redissonClient;
    private final ExecutorService executorService;

    // 预热进度跟踪
    private final ConcurrentMap<String, WarmupProgress> warmupProgress = new ConcurrentHashMap<>();

    @Autowired
    public CacheWarmer(CacheManager multiLevelCacheManager,
                      RedisTemplate<String, Object> redisTemplate,
                      RedissonClient redissonClient,
                       @Value("${cache.warmer.enabled:true}") boolean warmEnabled,
                       @Value("${cache.warmer.thread-pool-size:4}") int threadPoolSize,
                       @Value("${cache.warmer.timeout-seconds:300}") int timeoutSeconds) {
        this.multiLevelCacheManager = multiLevelCacheManager;
        this.redisTemplate = redisTemplate;
        this.redissonClient = redissonClient;

        this.warmEnabled = warmEnabled;
        this.threadPoolSize = threadPoolSize;
        this.timeoutSeconds = timeoutSeconds;

        log.info("Cache warmup enabled: {}", warmEnabled);
        log.info("Cache warmup thread pool size: {}", threadPoolSize);
        log.info("Cache warmup timeout: {} seconds", timeoutSeconds);
        this.executorService = Executors.newFixedThreadPool(threadPoolSize);
    }

    @Override
    public void onApplicationEvent(ApplicationStartedEvent event) {
        if (!warmEnabled) {
            log.info("Cache warmup is disabled");
            return;
        }

        log.info("Starting cache warmup process...");
        warmupAllCaches();
    }

    /**
     * 预热所有缓存
     */
    @Async
    public List<String> warmupAllCaches() {
        List<CompletableFuture<String>> futures = new ArrayList<>();
        List<String> successCacheNames = new ArrayList<>();

        // 获取所有缓存名称
        for (String cacheName : multiLevelCacheManager.getCacheNames()) {
            CompletableFuture<String> future = CompletableFuture.supplyAsync(
                    () -> {
                        warmupCache(cacheName);
                        return cacheName;
                    },
                    executorService
            );
            futures.add(future);
        }

        try {
            // 等待所有预热任务完成或超时，并收集成功结果
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(timeoutSeconds, TimeUnit.SECONDS);

            // 获取所有成功的缓存名称
            for (CompletableFuture<String> future : futures) {
                if (!future.isCompletedExceptionally()) {
                    successCacheNames.add(future.get());
                }
            }

            log.info("Cache warmup completed successfully");
            return successCacheNames;
        } catch (TimeoutException e) {
            log.warn("Cache warmup timed out after {} seconds", timeoutSeconds);
        } catch (Exception e) {
            log.error("Error during cache warmup", e);
        }
        return successCacheNames;
    }

    /**
     * 预热指定缓存
     */
    @Async
    public String warmupCache(String cacheName) {
        log.info("Starting warmup for cache: {}", cacheName);
        Cache cache = multiLevelCacheManager.getCache(cacheName);

        if (cache == null) {
            log.warn("Cache not found: {}", cacheName);
            return null;
        }

        // 创建预热进度跟踪器
        WarmupProgress progress = new WarmupProgress(cacheName);
        warmupProgress.put(cacheName, progress);

        try {
            // 获取布隆过滤器
            RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter("bf:" + cacheName);

            // 获取所有相关的Redis键
            String keyPattern = "cache:" + cacheName + "::*";
            long totalKeys = redisTemplate.keys(keyPattern).size();
            progress.setTotalItems((int) totalKeys);

            // 批量处理键
            redisTemplate.keys(keyPattern).forEach(key -> {
                try {
                    // 从Redis获取值
                    Object value = redisTemplate.opsForValue().get(key);
                    if (value != null) {
                        // 提取实际的缓存键
                        String actualKey = key.toString().substring(("cache:" + cacheName + "::").length());

                        // 更新本地缓存
                        cache.put(actualKey, value);

                        // 更新布隆过滤器
                        bloomFilter.add(actualKey);

                        // 更新进度
                        progress.incrementProcessed();

                        if (progress.getProcessedItems() % 100 == 0) {
                            log.debug("Warmed up {} items for cache: {}",
                                    progress.getProcessedItems(), cacheName);
                        }
                    }
                } catch (Exception e) {
                    progress.incrementErrors();
                    log.error("Error warming up key: {} in cache: {}", key, cacheName, e);
                }
            });

            log.info("Completed warmup for cache: {}. Processed: {}, Errors: {}",
                    cacheName, progress.getProcessedItems(), progress.getErrors());



        } catch (Exception e) {
            log.error("Error during cache warmup for: {}", cacheName, e);
            progress.setStatus(WarmupStatus.FAILED);
            return null;
        } finally {
            progress.setStatus(WarmupStatus.COMPLETED);
        }
        // 如果没有错误，则返回缓存名称
        if (progress.getErrors() == 0) {
            return cacheName;
        }
        return null;
    }

    /**
     * 获取预热进度
     */
    public WarmupProgress getProgress(String cacheName) {
        return warmupProgress.get(cacheName);
    }

    /**
     * 获取所有缓存的预热进度
     */
    public ConcurrentMap<String, WarmupProgress> getAllProgress() {
        return warmupProgress;
    }

    /**
     * 预热进度跟踪类
     */
    @Slf4j
    public static class WarmupProgress {
        private final String cacheName;
        private final AtomicInteger processedItems = new AtomicInteger(0);
        private final AtomicInteger errors = new AtomicInteger(0);
        private volatile int totalItems;
        private volatile WarmupStatus status = WarmupStatus.IN_PROGRESS;
        private final long startTime = System.currentTimeMillis();
        private volatile long endTime;

        public WarmupProgress(String cacheName) {
            this.cacheName = cacheName;
        }

        public void incrementProcessed() {
            processedItems.incrementAndGet();
        }

        public void incrementErrors() {
            errors.incrementAndGet();
        }

        public void setTotalItems(int totalItems) {
            this.totalItems = totalItems;
        }

        public void setStatus(WarmupStatus status) {
            this.status = status;
            if (status == WarmupStatus.COMPLETED || status == WarmupStatus.FAILED) {
                this.endTime = System.currentTimeMillis();
            }
        }

        public String getCacheName() {
            return cacheName;
        }

        public int getProcessedItems() {
            return processedItems.get();
        }

        public int getErrors() {
            return errors.get();
        }

        public int getTotalItems() {
            return totalItems;
        }

        public WarmupStatus getStatus() {
            return status;
        }

        public double getProgress() {
            if (totalItems == 0) return 0;
            return (double) processedItems.get() / totalItems * 100;
        }

        public long getDuration() {
            long end = endTime > 0 ? endTime : System.currentTimeMillis();
            return end - startTime;
        }
    }

    /**
     * 预热状态枚举
     */
    public enum WarmupStatus {
        IN_PROGRESS,
        COMPLETED,
        FAILED
    }
}
