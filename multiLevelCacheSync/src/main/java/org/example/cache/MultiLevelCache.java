package org.example.cache;

import com.github.benmanes.caffeine.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import org.example.config.CacheProperties;
import org.example.cache.event.CacheEvent;
import org.example.endpoint.CacheStats;
import org.redisson.api.RLock;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.redisson.spring.cache.NullValue;
import org.springframework.cache.support.AbstractValueAdaptingCache;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;

/**
 * 多级缓存实现
 * L1: Caffeine本地缓存
 * L2: Redis分布式缓存
 */
@Slf4j
public class MultiLevelCache extends AbstractValueAdaptingCache {

    private final String name;
    private final Cache<Object, Object> caffeineCache;
    private final RedissonClient redissonClient;
    private final CacheProperties cacheProperties;
    private final String nodeId;
    private final Function<CacheEvent, Void> eventPublisher;

    // 缓存统计
    private final CacheStats cacheStats = new CacheStats();
    private final Map<String, LongAdder> methodStats = new ConcurrentHashMap<>();

    public MultiLevelCache(String name,
                         Cache<Object, Object> caffeineCache,
                         RedissonClient redissonClient,
                         CacheProperties cacheProperties,
                         String nodeId,
                         Function<CacheEvent, Void> eventPublisher) {
        super(true);
        this.name = name;
        this.caffeineCache = caffeineCache;
        this.redissonClient = redissonClient;
        this.cacheProperties = cacheProperties;
        this.nodeId = nodeId;
        this.eventPublisher = eventPublisher;
        this.cacheStats.setCacheName(name);
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public Object getNativeCache() {
        return this;
    }

    @Override
    protected Object lookup(Object key) {
        String cacheKey = createCacheKey(key);
        
        // 1. 从本地缓存获取
        Object value = caffeineCache.getIfPresent(cacheKey);
        if (value != null) {
            log.debug("Cache hit in L1 cache, name: {}, key: {}", name, cacheKey);
            cacheStats.recordHit("L1");
            return value;
        }

        // 2. 从Redis获取
        RMap<Object, Object> map = redissonClient.getMap(name);
        value = map.get(cacheKey);
        if (value != null) {
            log.debug("Cache hit in L2 cache, name: {}, key: {}", name, cacheKey);
            cacheStats.recordHit("L2");
            cacheStats.recordMiss("L1"); // L1缓存未命中
            // 回填到本地缓存
            caffeineCache.put(cacheKey, value);
            return value;
        }

        log.debug("Cache miss, name: {}, key: {}", name, cacheKey);
        cacheStats.recordMiss("L1");
        cacheStats.recordMiss("L2");
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, Callable<T> valueLoader) {
        String cacheKey = createCacheKey(key);

        // 1. 先尝试获取缓存
        Object value = lookup(key);
        if (value != null) {
            return (T) value;
        }

        // 2. 获取分布式锁防止缓存击穿
        RLock lock = redissonClient.getLock(name + ":lock:" + cacheKey);
        try {
            // 尝试获取锁
            boolean locked = lock.tryLock(
                cacheProperties.getLockWaitTime(),
                cacheProperties.getLockLeaseTime(),
                TimeUnit.MILLISECONDS
            );

            if (!locked) {
                log.warn("Failed to acquire lock for key: {}", cacheKey);
                throw new RuntimeException("Failed to acquire lock");
            }

            // 双重检查，防止其他线程已经加载
            value = lookup(key);
            if (value != null) {
                return (T) value;
            }

            // 3. 调用valueLoader加载数据
            cacheStats.recordLoad("total");
            value = valueLoader.call();
            if (value != null) {
                put(key, value);
            }
            return (T) value;

        } catch (Exception e) {
            log.error("Error loading cache value for key: " + cacheKey, e);
            throw new RuntimeException(e);
        } finally {
            try {
                lock.unlock();
            } catch (Exception e) {
                log.warn("Error unlocking for key: " + cacheKey, e);
            }
        }
    }

    @Override
    public void put(Object key, Object value) {
        String cacheKey = createCacheKey(key);

        // 处理null值
        if (value == null) {
            if (cacheProperties.isAllowNullValues()) {
                log.debug("Caching null value for key: {}", cacheKey);
                putNullValue(cacheKey);
            } else {
                log.debug("Skipping null value for key: {}", cacheKey);
            }
            return;
        }

        // 计算TTL（带随机化防止缓存雪崩）
        long ttl = calculateTtl(cacheProperties.getTimeToLive());

        // 1. 写入Redis
        RMap<Object, Object> map = redissonClient.getMap(name);
        if (ttl > 0) {
            map.put(cacheKey, value);
            map.expire(Duration.ofMillis(ttl));
        } else {
            map.put(cacheKey, value);
        }
        cacheStats.recordPut("L2");

        // 2. 写入本地缓存
        caffeineCache.put(cacheKey, value);
        cacheStats.recordPut("L1");

        // 3. 发布缓存更新事件
        publishEvent(CacheEvent.createPutEvent(
            name,
            cacheKey,
            value,
            nodeId,
            ttl
        ));

        log.debug("Cache put, name: {}, key: {}, ttl: {}", name, cacheKey, ttl);
    }

    /**
     * 缓存null值
     */
    private void putNullValue(String cacheKey) {
        long nullValueTimeout = cacheProperties.getProtection().getNullValue().getTimeout() * 1000;

        // 1. 写入Redis
        RMap<Object, Object> map = redissonClient.getMap(name);
        map.put(cacheKey, NullValue.INSTANCE);
        map.expire(Duration.ofMillis(nullValueTimeout));
        cacheStats.recordPut("L2");

        // 2. 写入本地缓存
        caffeineCache.put(cacheKey, NullValue.INSTANCE);
        cacheStats.recordPut("L1");

        // 3. 发布缓存更新事件
        publishEvent(CacheEvent.createPutEvent(
            name,
            cacheKey,
            NullValue.INSTANCE,
            nodeId,
            nullValueTimeout
        ));
    }

    /**
     * 计算带随机化的TTL，用于防止缓存雪崩
     */
    private long calculateTtl(long baseTtl) {
        if (!cacheProperties.getProtection().getTtlRandomization().isEnabled()) {
            return baseTtl;
        }

        double variance = cacheProperties.getProtection().getTtlRandomization().getVariance();
        double randomFactor = 1 + ((Math.random() * 2 - 1) * variance);
        return (long) (baseTtl * randomFactor);
    }

    @Override
    public void evict(Object key) {
        String cacheKey = createCacheKey(key);

        // 1. 删除Redis缓存
        RMap<Object, Object> map = redissonClient.getMap(name);
        map.remove(cacheKey);
        cacheStats.recordEviction("L2");

        // 2. 删除本地缓存
        caffeineCache.invalidate(cacheKey);
        cacheStats.recordEviction("L1");

        // 3. 发布缓存删除事件
        publishEvent(CacheEvent.createRemoveEvent(name, cacheKey, nodeId));

        log.debug("Cache evict, name: {}, key: {}", name, cacheKey);
    }

    @Override
    public void clear() {
        // 1. 清除Redis缓存
        RMap<Object, Object> map = redissonClient.getMap(name);
        int redisSize = map.size();
        map.clear();
        // 记录Redis缓存清除
        for (int i = 0; i < redisSize; i++) {
            cacheStats.recordEviction("L2");
        }

        // 2. 清除本地缓存
        long caffeineSize = caffeineCache.estimatedSize();
        caffeineCache.invalidateAll();
        // 记录本地缓存清除
        for (int i = 0; i < caffeineSize; i++) {
            cacheStats.recordEviction("L1");
        }

        // 3. 发布缓存清除事件
        publishEvent(CacheEvent.createClearEvent(name, nodeId));

        log.debug("Cache clear, name: {}", name);
    }

    /**
     * 批量删除匹配模式的缓存
     */
    public void evictByPattern(String pattern) {
        if (!StringUtils.hasText(pattern)) {
            return;
        }

        // 1. 删除Redis缓存
        RMap<Object, Object> map = redissonClient.getMap(name);
        map.keySet().stream()
            .filter(k -> k.toString().matches(pattern))
            .forEach(map::remove);

        // 2. 删除本地缓存
        caffeineCache.asMap().keySet().stream()
            .filter(k -> k.toString().matches(pattern))
            .forEach(caffeineCache::invalidate);

        // 3. 发布模式删除事件
        publishEvent(CacheEvent.createRemovePatternEvent(name, pattern, nodeId));

        log.debug("Cache evict by pattern, name: {}, pattern: {}", name, pattern);
    }

    /**
     * 更新缓存过期时间
     */
    public void expire(Object key, long ttl) {
        String cacheKey = createCacheKey(key);

        // 1. 更新Redis过期时间
        RMap<Object, Object> map = redissonClient.getMap(name);
        if (map.containsKey(cacheKey)) {
            map.expire(Duration.ofMillis(ttl));
        }

        // 2. 发布过期时间更新事件
        publishEvent(CacheEvent.createExpireEvent(name, cacheKey, ttl, nodeId));

        log.debug("Cache expire updated, name: {}, key: {}, ttl: {}", name, cacheKey, ttl);
    }

    /**
     * 获取缓存统计信息
     * @return 当前缓存的统计信息
     */
    public CacheStats getCacheStats() {
        // 获取当前缓存统计信息的快照
        CacheStats currentStats = new CacheStats();
        currentStats.setCacheName(name);
        
        try {
            // 更新L1（Caffeine）缓存统计信息
            com.github.benmanes.caffeine.cache.stats.CacheStats caffeineStats = caffeineCache.stats();
            currentStats.updateCaffeineStats(caffeineStats);
            
            // 记录L1缓存的估计大小
            CacheStats.CacheStatInfo l1Stats = currentStats.getCacheStatInfo("L1");
            l1Stats.setEstimatedSize(caffeineCache.estimatedSize());
            
            // 记录L2（Redis）缓存统计信息
            RMap<Object, Object> redisMap = redissonClient.getMap(name);
            CacheStats.CacheStatInfo l2Stats = currentStats.getCacheStatInfo("L2");
            l2Stats.setEstimatedSize(redisMap.size());
            
            // 合并已有的统计信息
            currentStats.merge(this.cacheStats);
            
            // 更新总体统计信息
            currentStats.updateTotalStats();
            
        } catch (Exception e) {
            log.error("Error getting cache stats for cache: " + name, e);
            // 如果出现错误，至少返回基本的统计信息
            currentStats.setCacheName(name);
        }
        
        return currentStats;
    }

    /**
     * @deprecated 使用 {@link #getCacheStats()} 替代
     */
    @Deprecated
    public CacheStats getStats() {
        return getCacheStats();
    }

    /**
     * 处理缓存同步事件
     */
    public void handleCacheEvent(CacheEvent event) {
        // 忽略来自自己的事件
        if (nodeId.equals(event.getSourceNodeId())) {
            return;
        }

        try {
            switch (event.getEventType()) {
                case PUT -> {
                    caffeineCache.put(event.getKey(), event.getValue());
                    log.debug("Synchronized PUT event, name: {}, key: {}", name, event.getKey());
                }
                case REMOVE -> {
                    caffeineCache.invalidate(event.getKey());
                    log.debug("Synchronized REMOVE event, name: {}, key: {}", name, event.getKey());
                }
                case CLEAR -> {
                    caffeineCache.invalidateAll();
                    log.debug("Synchronized CLEAR event, name: {}", name);
                }
                case REMOVE_PATTERN -> {
                    String pattern = event.getKey().toString();
                    caffeineCache.asMap().keySet().stream()
                        .filter(k -> k.toString().matches(pattern))
                        .forEach(caffeineCache::invalidate);
                    log.debug("Synchronized REMOVE_PATTERN event, name: {}, pattern: {}", name, pattern);
                }
                default -> log.warn("Unsupported cache event type: {}", event.getEventType());
            }
        } catch (Exception e) {
            log.error("Error handling cache event: " + event, e);
        }
    }

    /**
     * 创建缓存键
     * 可以在这里添加前缀或者进行其他处理
     */
    private String createCacheKey(Object key) {
        return key.toString();
    }

    /**
     * 发布缓存事件
     */
    private void publishEvent(CacheEvent event) {
        try {
            eventPublisher.apply(event);
        } catch (Exception e) {
            log.error("Error publishing cache event: " + event, e);
        }
    }

    /**
     * 获取所有缓存键
     * @return 缓存中的所有键的字符串列表
     */
    /**
     * 获取所有缓存键
     * @return 缓存中的所有键的字符串列表
     */
    public List<String> getKeys() {
        // 从Redis获取所有键
        RMap<String, Object> map = redissonClient.getMap(name);
        Set<Object> keys = new HashSet<>(map.keySet());
        
        // 合并本地缓存的键
        keys.addAll(caffeineCache.asMap().keySet());
        
        // 转换为String类型的List
        return keys.stream()
                .map(Object::toString)
                .collect(Collectors.toList());
    }

    /**
     * 获取缓存大小
     */
    public long size() {
        RMap<String, Object> map = redissonClient.getMap(name);
        return map.size() + caffeineCache.estimatedSize();
    }

    /**
     * 批量获取缓存值
     * @param keys 键集合
     * @return 键值对映射
     */
    public Map<Object, Object> getAll(Set<Object> keys) {
        if (keys == null || keys.isEmpty()) {
            return new ConcurrentHashMap<>();
        }

        Map<Object, Object> result = new ConcurrentHashMap<>();
        Set<Object> missedKeys = new HashSet<>();

        // 1. 从本地缓存批量获取
        for (Object key : keys) {
            String cacheKey = createCacheKey(key);
            Object value = caffeineCache.getIfPresent(cacheKey);
            if (value != null) {
                result.put(key, value);
                cacheStats.recordHit("L1");
            } else {
                missedKeys.add(cacheKey);
                cacheStats.recordMiss("L1");
            }
        }

        if (!missedKeys.isEmpty()) {
            // 2. 从Redis批量获取未命中的键
            RMap<Object, Object> map = redissonClient.getMap(name);
            Map<Object, Object> redisValues = map.getAll(missedKeys);

            // 3. 处理Redis的结果
            redisValues.forEach((k, v) -> {
                result.put(k, v);
                caffeineCache.put(k, v); // 回填到本地缓存
                cacheStats.recordHit("L2");
            });

            // 记录Redis未命中的键
            missedKeys.removeAll(redisValues.keySet());
            missedKeys.forEach(k -> cacheStats.recordMiss("L2"));
        }

        return result;
    }

    /**
     * 批量写入缓存
     * @param map 要写入的键值对映射
     */
    public void putAll(Map<Object, Object> map) {
        if (map == null || map.isEmpty()) {
            return;
        }

        // 获取批量操作配置
        int batchSize = cacheProperties.getBatch().getMaxSize();
        long timeout = cacheProperties.getBatch().getTimeout();

        // 分批处理
        List<Map<Object, Object>> batches = map.entrySet().stream()
            .collect(Collectors.groupingBy(
                entry -> Math.floorDiv(map.size(), batchSize),
                Collectors.toMap(
                    Map.Entry::getKey,
                    Map.Entry::getValue
                )
            ))
            .values()
            .stream()
            .collect(Collectors.toList());

        for (Map<Object, Object> batch : batches) {
            try {
                processBatchPut(batch, timeout);
            } catch (Exception e) {
                log.error("Error processing batch put for cache: " + name, e);
                throw new RuntimeException("Failed to process batch put", e);
            }
        }
    }

    /**
     * 处理批量写入
     */
    private void processBatchPut(Map<Object, Object> batch, long timeout) {
        // 1. 写入Redis
        RMap<Object, Object> redisMap = redissonClient.getMap(name);
        long ttl = calculateTtl(cacheProperties.getTimeToLive());

        // 使用pipeline批量写入Redis
        redisMap.putAll(batch);
        if (timeout > 0) {
            redisMap.expire(Duration.ofMillis(timeout));
        } else if (ttl > 0) {
            redisMap.expire(Duration.ofMillis(ttl));
        }

        // 2. 写入本地缓存
        batch.forEach((k, v) -> {
            String cacheKey = createCacheKey(k);
            if (v == null && !cacheProperties.isAllowNullValues()) {
                return;
            }
            caffeineCache.put(cacheKey, v != null ? v : NullValue.INSTANCE);
            cacheStats.recordPut("L1");
            cacheStats.recordPut("L2");
        });

        // 3. 发布批量更新事件
        publishEvent(CacheEvent.createBatchPutEvent(
            name,
            batch,
            nodeId,
            ttl
        ));

        log.debug("Batch put completed, cache: {}, size: {}", name, batch.size());
    }

    /**
     * 批量删除缓存
     * @param keys 要删除的键集合
     */
    public void evictAll(Set<Object> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }

        // 获取批量操作配置
        int batchSize = cacheProperties.getBatch().getMaxSize();

        // 分批处理删除
        List<List<Object>> batches = keys.stream()
            .collect(Collectors.groupingBy(
                key -> Math.floorDiv(keys.size(), batchSize)
            ))
            .values()
            .stream()
            .collect(Collectors.toList());

        for (List<Object> batch : batches) {
            try {
                processBatchEvict(batch);
            } catch (Exception e) {
                log.error("Error processing batch evict for cache: " + name, e);
                throw new RuntimeException("Failed to process batch evict", e);
            }
        }
    }

    /**
     * 处理批量删除
     */
    private void processBatchEvict(List<Object> keys) {
        // 1. 从Redis删除
        RMap<Object, Object> redisMap = redissonClient.getMap(name);
        keys.forEach(key -> {
            String cacheKey = createCacheKey(key);
            redisMap.remove(cacheKey);
            cacheStats.recordEviction("L2");
        });

        // 2. 从本地缓存删除
        keys.forEach(key -> {
            String cacheKey = createCacheKey(key);
            caffeineCache.invalidate(cacheKey);
            cacheStats.recordEviction("L1");
        });

        // 3. 发布批量删除事件
        publishEvent(CacheEvent.createBatchRemoveEvent(
            name,
            keys,
            nodeId
        ));

        log.debug("Batch evict completed, cache: {}, size: {}", name, keys.size());
    }
}
