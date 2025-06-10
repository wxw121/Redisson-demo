package org.example.listener;

import lombok.extern.slf4j.Slf4j;
import org.example.cache.MultiLevelCache;
import org.example.config.CacheProperties;
import org.example.cache.event.CacheEvent;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 基于Redisson的缓存事件监听器实现
 * 使用Redis的发布订阅机制实现分布式缓存同步
 */
@Slf4j
@Component
public class RedissonCacheEventListener implements CacheEventListener {

    private final RedissonClient redissonClient;
    private final ObjectProvider<CacheManager> cacheManagerProvider;
    private final CacheProperties cacheProperties;
    private final String nodeId;
    private final String topicName;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Set<String> processedEvents = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> eventTimestamps = new ConcurrentHashMap<>();

    private RTopic topic;
    private int listenerId;

    public RedissonCacheEventListener(
            RedissonClient redissonClient,
            ObjectProvider<CacheManager> cacheManagerProvider,
            CacheProperties cacheProperties,
            @Value("${cache.node-id}") String nodeId) {
        this.redissonClient = redissonClient;
        this.cacheManagerProvider = cacheManagerProvider;
        this.cacheProperties = cacheProperties;
        this.nodeId = nodeId;
        this.topicName = cacheProperties.getTopicName();
    }

    @Override
    public void onEvent(CacheEvent event) {
        // 忽略自己发布的事件
        if (nodeId.equals(event.getSourceNodeId())) {
            return;
        }

        // 检查事件是否已处理（防止重复处理）
        String eventKey = event.getEventId();
        if (processedEvents.contains(eventKey)) {
            log.debug("Ignoring already processed event: {}", eventKey);
            return;
        }

        try {
            // 记录已处理事件
            processedEvents.add(eventKey);
            eventTimestamps.put(eventKey, System.currentTimeMillis());

            // 获取对应的缓存实例
            CacheManager cacheManager = cacheManagerProvider.getObject();
            if (cacheManager == null) {
                log.warn("CacheManager not available");
                return;
            }
            
            Object cache = cacheManager.getCache(event.getCacheName());
            if (cache instanceof MultiLevelCache multiLevelCache) {
                // 处理缓存事件
                multiLevelCache.handleCacheEvent(event);
            } else {
                log.warn("Cache not found or not a MultiLevelCache: {}", event.getCacheName());
            }
        } catch (Exception e) {
            log.error("Error handling cache event: " + event, e);
        }

        // 清理过期的事件记录
        cleanupProcessedEvents();
    }

    @Override
    public String getName() {
        return "RedissonCacheEventListener";
    }

    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            log.info("Starting RedissonCacheEventListener on topic: {}", topicName);
            topic = redissonClient.getTopic(topicName);
            listenerId = topic.addListener(CacheEvent.class, (channel, event) -> onEvent(event));
        }
    }

    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            log.info("Stopping RedissonCacheEventListener");
            if (topic != null) {
                topic.removeListener(listenerId);
            }
        }
    }

    @Override
    public void publishEvent(CacheEvent event) {
        if (!running.get()) {
            log.warn("Cannot publish event, listener is not running");
            return;
        }

        try {
            // 设置事件源节点ID
            if (event.getSourceNodeId() == null) {
                event.setSourceNodeId(nodeId);
            }

            // 发布事件
            topic.publish(event);
            log.debug("Published cache event: {}", event);
        } catch (Exception e) {
            log.error("Error publishing cache event: " + event, e);
        }
    }

    /**
     * 清理过期的事件记录
     * 防止内存泄漏
     */
    private void cleanupProcessedEvents() {
        long now = System.currentTimeMillis();
        long expiryTime = cacheProperties.getEventTtl();

        eventTimestamps.entrySet().removeIf(entry -> {
            if (now - entry.getValue() > expiryTime) {
                processedEvents.remove(entry.getKey());
                return true;
            }
            return false;
        });
    }
}
