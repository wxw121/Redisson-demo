package org.example.cache.event;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 缓存事件
 * 用于在不同节点间同步缓存操作
 */
@Data
@Builder
public class CacheEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 事件类型
     */
    private EventType eventType;

    /**
     * 缓存名称
     */
    private String cacheName;

    /**
     * 缓存键
     */
    private Object key;

    /**
     * 缓存值（可选，仅在PUT事件中使用）
     */
    private Object value;

    /**
     * 事件发生时间
     */
    private LocalDateTime timestamp;

    /**
     * 事件源节点ID（用于防止消息循环）
     */
    private String sourceNodeId;

    /**
     * 事件ID（用于去重）
     */
    private String eventId;

    /**
     * 缓存过期时间（毫秒，可选）
     */
    private Long ttl;

    /**
     * 批量操作的键值对（仅在BATCH_PUT事件中使用）
     */
    private Map<Object, Object> entries;

    /**
     * 批量删除的键列表（仅在BATCH_REMOVE事件中使用）
     */
    private List<Object> keys;

    /**
     * 事件类型枚举
     */
    public enum EventType {
        /**
         * 更新或新增缓存
         */
        PUT,
        
        /**
         * 删除单个缓存
         */
        REMOVE,
        
        /**
         * 清空指定缓存名称下的所有缓存
         */
        CLEAR,
        
        /**
         * 更新缓存过期时间
         */
        EXPIRE,
        
        /**
         * 批量删除缓存（支持模式匹配）
         */
        REMOVE_PATTERN,
        
        /**
         * 缓存预热
         */
        PREFETCH,
        
        /**
         * 缓存同步请求
         */
        SYNC_REQUEST,
        
        /**
         * 缓存同步响应
         */
        SYNC_RESPONSE,

        /**
         * 批量更新或新增缓存
         */
        BATCH_PUT,

        /**
         * 批量删除缓存
         */
        BATCH_REMOVE
    }

    /**
     * 创建PUT事件
     */
    public static CacheEvent createPutEvent(String cacheName, Object key, Object value, String sourceNodeId, Long ttl) {
        return CacheEvent.builder()
                .eventType(EventType.PUT)
                .cacheName(cacheName)
                .key(key)
                .value(value)
                .timestamp(LocalDateTime.now())
                .sourceNodeId(sourceNodeId)
                .eventId(generateEventId())
                .ttl(ttl)
                .build();
    }

    /**
     * 创建REMOVE事件
     */
    public static CacheEvent createRemoveEvent(String cacheName, Object key, String sourceNodeId) {
        return CacheEvent.builder()
                .eventType(EventType.REMOVE)
                .cacheName(cacheName)
                .key(key)
                .timestamp(LocalDateTime.now())
                .sourceNodeId(sourceNodeId)
                .eventId(generateEventId())
                .build();
    }

    /**
     * 创建CLEAR事件
     */
    public static CacheEvent createClearEvent(String cacheName, String sourceNodeId) {
        return CacheEvent.builder()
                .eventType(EventType.CLEAR)
                .cacheName(cacheName)
                .timestamp(LocalDateTime.now())
                .sourceNodeId(sourceNodeId)
                .eventId(generateEventId())
                .build();
    }

    /**
     * 创建REMOVE_PATTERN事件
     */
    public static CacheEvent createRemovePatternEvent(String cacheName, String pattern, String sourceNodeId) {
        return CacheEvent.builder()
                .eventType(EventType.REMOVE_PATTERN)
                .cacheName(cacheName)
                .key(pattern)
                .timestamp(LocalDateTime.now())
                .sourceNodeId(sourceNodeId)
                .eventId(generateEventId())
                .build();
    }

    /**
     * 创建EXPIRE事件
     */
    public static CacheEvent createExpireEvent(String cacheName, Object key, Long ttl, String sourceNodeId) {
        return CacheEvent.builder()
                .eventType(EventType.EXPIRE)
                .cacheName(cacheName)
                .key(key)
                .ttl(ttl)
                .timestamp(LocalDateTime.now())
                .sourceNodeId(sourceNodeId)
                .eventId(generateEventId())
                .build();
    }

    /**
     * 生成事件ID
     * 使用时间戳和随机数组合生成唯一ID
     */
    private static String generateEventId() {
        return System.currentTimeMillis() + "-" + 
               Math.abs(System.nanoTime()) + "-" + 
               Thread.currentThread().getId();
    }

    /**
     * 创建批量PUT事件
     *
     * @param cacheName 缓存名称
     * @param entries 批量键值对
     * @param sourceNodeId 源节点ID
     * @param ttl 过期时间（毫秒）
     * @return 缓存事件
     */
    public static CacheEvent createBatchPutEvent(String cacheName, Map<Object, Object> entries, String sourceNodeId, Long ttl) {
        return CacheEvent.builder()
                .eventType(EventType.BATCH_PUT)
                .cacheName(cacheName)
                .entries(entries)
                .timestamp(LocalDateTime.now())
                .sourceNodeId(sourceNodeId)
                .eventId(generateEventId())
                .ttl(ttl)
                .build();
    }

    /**
     * 创建批量REMOVE事件
     *
     * @param cacheName 缓存名称
     * @param keys 批量键列表
     * @param sourceNodeId 源节点ID
     * @return 缓存事件
     */
    public static CacheEvent createBatchRemoveEvent(String cacheName, List<Object> keys, String sourceNodeId) {
        return CacheEvent.builder()
                .eventType(EventType.BATCH_REMOVE)
                .cacheName(cacheName)
                .keys(keys)
                .timestamp(LocalDateTime.now())
                .sourceNodeId(sourceNodeId)
                .eventId(generateEventId())
                .build();
    }
}
