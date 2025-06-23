package org.example.realtimestats.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "realtime-stats")
public class RealtimeStatsProperties {
    private RedisKeyPrefix redisKeyPrefix;
    private SyncConfig sync;

    @Data
    public static class RedisKeyPrefix {
        private String content;
        private String like;
        private String pageView;
        private String uniqueVisitor;
        private String gamePlayer;
        private String hotSearch;
    }

    @Data
    public static class SyncConfig {
        private long interval;
        private int batchSize;
    }

}
