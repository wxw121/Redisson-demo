package org.example.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.config.CacheProperties;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Objects;

/**
 * 缓存同步工具类
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheSyncUtil {

    private final RedissonClient redissonClient;
    private final CacheManager cacheManager;
    private final CacheProperties cacheProperties;
    private final ObjectMapper objectMapper;

    private RTopic topic;
    private String nodeId;

    /**
     * 缓存操作类型
     */
    private enum CacheOpType {
        UPDATE, CLEAR
    }

    /**
     * 缓存同步消息
     * 使用Java 17 Record特性
     */
    private record CacheSyncMessage(
            String nodeId,
            String cacheName,
            Object key,
            Object value,
            CacheOpType opType
    ) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CacheSyncMessage that = (CacheSyncMessage) o;
            return Objects.equals(nodeId, that.nodeId) &&
                   Objects.equals(cacheName, that.cacheName) &&
                   Objects.equals(key, that.key) &&
                   opType == that.opType;
        }

        @Override
        public int hashCode() {
            return Objects.hash(nodeId, cacheName, key, opType);
        }
    }

    @PostConstruct
    public void init() {
        // 生成唯一的节点ID
        this.nodeId = java.util.UUID.randomUUID().toString();

        // 创建Topic并订阅
        this.topic = redissonClient.getTopic(cacheProperties.getTopicName());

        // 订阅缓存同步消息
        this.topic.addListener(String.class, (channel, msg) -> {
            try {
                var message = objectMapper.readValue(msg, CacheSyncMessage.class);

                // 忽略自己发出的消息
                if (nodeId.equals(message.nodeId())) {
                    return;
                }

                // 处理缓存同步消息
                handleCacheSyncMessage(message);

            } catch (JsonProcessingException e) {
                log.error("解析缓存同步消息失败: {}", msg, e);
            }
        });

        log.info("缓存同步工具初始化完成，节点ID: {}", nodeId);
    }

    @PreDestroy
    public void destroy() {
        if (topic != null) {
            topic.removeAllListeners();
        }
    }

    /**
     * 更新缓存并发布同步消息
     */
    public void updateCache(String cacheName, Object key, Object value) {
        try {
            // 发布缓存更新消息
            var message = new CacheSyncMessage(nodeId, cacheName, key, value, CacheOpType.UPDATE);
            topic.publish(objectMapper.writeValueAsString(message));

            log.debug("发布缓存更新消息: cacheName={}, key={}", cacheName, key);

        } catch (JsonProcessingException e) {
            log.error("发布缓存更新消息失败: cacheName={}, key={}", cacheName, key, e);
        }
    }

    /**
     * 清除缓存并发布同步消息
     */
    public void clearCache(String cacheName, Object key) {
        try {
            // 发布缓存清除消息
            var message = new CacheSyncMessage(nodeId, cacheName, key, null, CacheOpType.CLEAR);
            topic.publish(objectMapper.writeValueAsString(message));

            log.debug("发布缓存清除消息: cacheName={}, key={}", cacheName, key);

        } catch (JsonProcessingException e) {
            log.error("发布缓存清除消息失败: cacheName={}, key={}", cacheName, key, e);
        }
    }

    /**
     * 处理缓存同步消息
     */
    private void handleCacheSyncMessage(CacheSyncMessage message) {
        var cache = cacheManager.getCache(message.cacheName());
        if (cache == null) {
            log.warn("缓存不存在: {}", message.cacheName());
            return;
        }

        // 使用Java 17 switch表达式处理不同的操作类型
        var result = switch (message.opType()) {
            case UPDATE -> {
                cache.put(message.key(), message.value());
                yield "更新缓存成功";
            }
            case CLEAR -> {
                cache.evict(message.key());
                yield "清除缓存成功";
            }
        };

        log.debug("处理缓存同步消息: {} - cacheName={}, key={}",
                result, message.cacheName(), message.key());
    }
}
