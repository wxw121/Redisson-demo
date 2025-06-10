package org.example.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 缓存配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "cache")
public class CacheProperties {

    /**
     * 是否启用缓存
     */
    private boolean enabled = true;

    /**
     * 节点ID，用于标识当前应用实例
     * 如果不配置，将自动生成UUID
     */
    private String nodeId;

    /**
     * 本地缓存配置（L1）
     */
    private LocalCacheProperties local = new LocalCacheProperties();

    /**
     * Redis缓存配置（L2）
     */
    private RedisCacheProperties redis = new RedisCacheProperties();

    /**
     * 防护配置
     */
    private ProtectionProperties protection = new ProtectionProperties();

    /**
     * 同步配置
     */
    private SyncProperties sync = new SyncProperties();

    /**
     * 事件配置
     */
    private EventProperties event = new EventProperties();

    /**
     * 统计配置
     */
    private StatsProperties stats = new StatsProperties();

    /**
     * 批量操作配置
     */
    private BatchProperties batch = new BatchProperties();

    /**
     * 默认缓存配置
     */
    private CacheConfig defaultConfig = new CacheConfig();

    /**
     * 特定缓存的配置
     * key: 缓存名称
     * value: 缓存配置
     */
    private Map<String, CacheConfig> caches = new HashMap<>();

    /**
     * 本地缓存配置（L1）
     */
    @Data
    public static class LocalCacheProperties {
        /**
         * 是否启用本地缓存
         */
        private boolean enabled = true;

        /**
         * 初始容量
         */
        private int initialCapacity = 100;

        /**
         * 最大容量
         */
        private int maximumSize = 10000;

        /**
         * 写入后过期时间（秒）
         */
        private long expireAfterWrite = 300;

        /**
         * 访问后过期时间（秒）
         */
        private long expireAfterAccess = 300;

        /**
         * 是否启用统计
         */
        private boolean statsEnabled = true;
    }

    /**
     * Redis缓存配置（L2）
     */
    @Data
    public static class RedisCacheProperties {
        /**
         * 是否启用Redis缓存
         */
        private boolean enabled = true;

        /**
         * 过期时间（秒）
         */
        private long timeToLive = 1800;

        /**
         * 键前缀
         */
        private String keyPrefix = "cache:";
    }

    /**
     * 防护配置
     */
    @Data
    public static class ProtectionProperties {
        /**
         * 缓存击穿防护
         */
        private LockProperties lock = new LockProperties();

        /**
         * 缓存穿透防护
         */
        private NullValueProperties nullValue = new NullValueProperties();

        /**
         * 缓存雪崩防护
         */
        private TtlRandomizationProperties ttlRandomization = new TtlRandomizationProperties();

        /**
         * 缓存击穿防护
         */
        @Data
        public static class LockProperties {
            /**
             * 是否启用锁防护
             */
            private boolean enabled = true;

            /**
             * 锁等待时间（毫秒）
             */
            private long waitTime = 3000;

            /**
             * 锁租约时间（毫秒）
             */
            private long leaseTime = 30000;
        }

        /**
         * 缓存穿透防护
         */
        @Data
        public static class NullValueProperties {
            /**
             * 是否启用空值缓存
             */
            private boolean enabled = true;

            /**
             * 空值缓存过期时间（秒）
             */
            private long timeout = 300;
        }

        /**
         * 缓存雪崩防护
         */
        @Data
        public static class TtlRandomizationProperties {
            /**
             * 是否启用TTL随机化
             */
            private boolean enabled = true;

            /**
             * 随机变化范围（百分比）
             */
            private double variance = 0.1;
        }
    }

    /**
     * 同步配置
     */
    @Data
    public static class SyncProperties {
        /**
         * 是否启用缓存同步
         */
        private boolean enabled = true;

        /**
         * 同步主题
         */
        private String topic = "cache:events";

        /**
         * 批量大小
         */
        private int batchSize = 100;

        /**
         * 队列容量
         */
        private int queueCapacity = 1000;
    }

    /**
     * 事件配置
     */
    @Data
    public static class EventProperties {
        /**
         * 是否启用事件
         */
        private boolean enabled = true;

        /**
         * 事件处理配置
         */
        private HandlerProperties handler = new HandlerProperties();

        /**
         * 事件去重配置
         */
        private DedupProperties dedup = new DedupProperties();

        /**
         * 事件处理配置
         */
        @Data
        public static class HandlerProperties {
            /**
             * 线程池大小
             */
            private int poolSize = 4;

            /**
             * 队列容量
             */
            private int queueCapacity = 1000;
        }

        /**
         * 事件去重配置
         */
        @Data
        public static class DedupProperties {
            /**
             * 是否启用去重
             */
            private boolean enabled = true;

            /**
             * 过期时间（秒）
             */
            private long expireAfter = 300;

            /**
             * 最大大小
             */
            private int maxSize = 10000;
        }
    }

    /**
     * 统计配置
     */
    @Data
    public static class StatsProperties {
        /**
         * 是否启用统计
         */
        private boolean enabled = true;

        /**
         * 日志间隔（秒）
         */
        private long logInterval = 300;

        /**
         * 是否启用指标
         */
        private boolean metricsEnabled = true;
    }

    /**
     * 批量操作配置
     */
    @Data
    public static class BatchProperties {
        /**
         * 是否启用批量操作
         */
        private boolean enabled = true;

        /**
         * 最大批量大小
         */
        private int maxSize = 100;

        /**
         * 超时时间（毫秒）
         */
        private long timeout = 5000;
    }

    /**
     * 单个缓存的配置
     */
    @Data
    public static class CacheConfig {
        /**
         * 本地缓存配置
         */
        private LocalCacheConfig local = new LocalCacheConfig();

        /**
         * Redis缓存配置
         */
        private RedisCacheConfig redis = new RedisCacheConfig();

        /**
         * 防护配置
         */
        private CacheProtectionConfig protection = new CacheProtectionConfig();

        /**
         * 同步配置
         */
        private CacheSyncConfig sync = new CacheSyncConfig();

        /**
         * 本地缓存配置
         */
        @Data
        public static class LocalCacheConfig {
            /**
             * 初始容量
             */
            private int initialCapacity = 700;

            /**
             * 最大容量
             */
            private int maximumSize = 1000;

            /**
             * 写入后过期时间（秒）
             */
            private long expireAfterWrite = 600;

            /**
             * 访问后过期时间（秒）
             */
            private long expireAfterAccess = 300;
        }

        /**
         * Redis缓存配置
         */
        @Data
        public static class RedisCacheConfig {
            /**
             * 过期时间（秒）
             */
            private long timeToLive = 1800;
        }

        /**
         * 防护配置
         */
        @Data
        public static class CacheProtectionConfig {
            /**
             * 是否启用空值缓存
             */
            private boolean nullValueEnabled = true;

            /**
             * 是否启用锁防护
             */
            private boolean lockEnabled = true;
        }

        /**
         * 同步配置
         */
        @Data
        public static class CacheSyncConfig {
            /**
             * 是否启用同步
             */
            private boolean enabled = true;
        }
    }

    /**
     * 获取指定缓存的配置
     * 如果没有特定配置，则返回默认配置
     *
     * @param cacheName 缓存名称
     * @return 缓存配置
     */
    public CacheConfig getCacheConfig(String cacheName) {
        return caches.getOrDefault(cacheName, defaultConfig);
    }

    /**
     * 获取本地缓存过期时间（毫秒）
     * 
     * @return 本地缓存过期时间（毫秒）
     */
    public long getLocalTimeToLive() {
        return local.getExpireAfterWrite() * 1000;
    }

    /**
     * 获取Redis缓存过期时间（毫秒）
     * 
     * @return Redis缓存过期时间（毫秒）
     */
    public long getTimeToLive() {
        return redis.getTimeToLive() * 1000;
    }

    /**
     * 获取分布式锁等待时间（毫秒）
     * 
     * @return 分布式锁等待时间（毫秒）
     */
    public long getLockWaitTime() {
        return protection.getLock().getWaitTime();
    }

    /**
     * 获取分布式锁租约时间（毫秒）
     * 
     * @return 分布式锁租约时间（毫秒）
     */
    public long getLockLeaseTime() {
        return protection.getLock().getLeaseTime();
    }

    /**
     * 是否允许缓存null值
     * 
     * @return 是否允许缓存null值
     */
    public boolean isAllowNullValues() {
        return protection.getNullValue().isEnabled();
    }

    /**
     * 是否启用缓存同步
     * 
     * @return 是否启用缓存同步
     */
    public boolean isSyncEnabled() {
        return sync.isEnabled();
    }

    /**
     * 获取缓存事件主题
     * 
     * @return 缓存事件主题
     */
    public String getTopicName() {
        return sync.getTopic();
    }

    /**
     * 获取事件TTL（毫秒）
     * 
     * @return 事件TTL（毫秒）
     */
    public long getEventTtl() {
        return event.getDedup().getExpireAfter() * 1000;
    }

    /**
     * 是否启用统计
     * 
     * @return 是否启用统计
     */
    public boolean isStatsEnabled() {
        return stats.isEnabled();
    }
}
