package org.example.listener;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.session.UserSession;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.session.Session;
import org.springframework.session.events.*;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 会话统计监听器
 * 负责收集和维护会话相关的统计信息
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionStatisticsListener implements SessionEventHandler {

    private final RedissonClient redissonClient;
    private static final String USER_SESSION_KEY = "USER_SESSION";

    // Redis key 常量
    private static final String STATS_PREFIX = "session:stats:";
    private static final String ACTIVE_SESSIONS = STATS_PREFIX + "active";
    private static final String TOTAL_SESSIONS = STATS_PREFIX + "total";
    private static final String EXPIRED_SESSIONS = STATS_PREFIX + "expired";
    private static final String DEVICE_STATS = STATS_PREFIX + "devices";
    private static final String USER_STATS = STATS_PREFIX + "users";
    private static final String DURATION_STATS = STATS_PREFIX + "durations";

    @Override
    public void onSessionCreated(SessionCreatedEvent event) {
        String sessionId = event.getSessionId();
        Session session = event.getSession();

        if (session != null) {
            UserSession userSession = session.getAttribute(USER_SESSION_KEY);
            if (userSession != null) {
                // 更新活跃会话计数
                incrementCounter(ACTIVE_SESSIONS);
                // 更新总会话计数
                incrementCounter(TOTAL_SESSIONS);

                // 更新设备类型统计
                String deviceType = userSession.getDeviceType();
                incrementCounter(DEVICE_STATS + ":" + deviceType);

                // 更新用户统计
                String username = userSession.getUsername();
                incrementCounter(USER_STATS + ":" + username);

                log.debug("会话创建统计更新 - 用户: {}, 会话ID: {}, 设备类型: {}",
                    username, sessionId, deviceType);
            }
        }
    }

    @Override
    public void onSessionExpired(SessionExpiredEvent event) {
        handleSessionEnd(event.getSessionId(), "过期");
        incrementCounter(EXPIRED_SESSIONS);
    }

    @Override
    public void onSessionDeleted(SessionDeletedEvent event) {
        handleSessionEnd(event.getSessionId(), "删除");
    }

    @Override
    public void onSessionDestroyed(SessionDestroyedEvent event) {
        handleSessionEnd(event.getSessionId(), "销毁");
    }

    /**
     * 处理会话结束事件
     */
    private void handleSessionEnd(String sessionId, String endType) {
        // 减少活跃会话计数
        decrementCounter(ACTIVE_SESSIONS);

        // 记录会话时长统计
        RMap<String, UserSession> sessions = redissonClient.getMap("sessions");
        UserSession userSession = sessions.get(sessionId);

        if (userSession != null) {
            long duration = System.currentTimeMillis() - userSession.getCreationTime();
            updateDurationStats(duration);

            // 减少用户活跃会话计数
            decrementCounter(USER_STATS + ":" + userSession.getUsername());

            log.debug("会话{}统计更新 - 用户: {}, 会话ID: {}, 持续时间: {}ms",
                endType, userSession.getUsername(), sessionId, duration);
        }
    }

    /**
     * 增加计数器值
     */
    private void incrementCounter(String key) {
        RAtomicLong counter = redissonClient.getAtomicLong(key);
        counter.incrementAndGet();
    }

    /**
     * 减少计数器值
     */
    private void decrementCounter(String key) {
        RAtomicLong counter = redissonClient.getAtomicLong(key);
        counter.decrementAndGet();
    }

    /**
     * 更新会话时长统计
     */
    private void updateDurationStats(long duration) {
        String durationKey = getDurationRange(duration);
        incrementCounter(DURATION_STATS + ":" + durationKey);
    }

    /**
     * 获取时长范围标识
     */
    private String getDurationRange(long duration) {
        Duration d = Duration.ofMillis(duration);

        if (d.compareTo(Duration.ofMinutes(5)) <= 0) return "0-5min";
        if (d.compareTo(Duration.ofMinutes(15)) <= 0) return "5-15min";
        if (d.compareTo(Duration.ofMinutes(30)) <= 0) return "15-30min";
        if (d.compareTo(Duration.ofHours(1)) <= 0) return "30-60min";
        if (d.compareTo(Duration.ofHours(2)) <= 0) return "1-2h";
        if (d.compareTo(Duration.ofHours(4)) <= 0) return "2-4h";
        return "4h+";
    }

    /**
     * 获取活跃会话数
     */
    public long getActiveSessionCount() {
        RAtomicLong counter = redissonClient.getAtomicLong(ACTIVE_SESSIONS);
        return counter.get();
    }

    /**
     * 获取总会话数
     */
    public long getTotalSessionCount() {
        RAtomicLong counter = redissonClient.getAtomicLong(TOTAL_SESSIONS);
        return counter.get();
    }

    /**
     * 获取过期会话数
     */
    public long getExpiredSessionCount() {
        RAtomicLong counter = redissonClient.getAtomicLong(EXPIRED_SESSIONS);
        return counter.get();
    }

    /**
     * 获取设备类型分布
     */
    public Map<String, Long> getDeviceTypeDistribution() {
        Map<String, Long> distribution = new HashMap<>();
        String[] deviceTypes = {"DESKTOP", "MOBILE", "TABLET", "UNKNOWN"};

        for (String deviceType : deviceTypes) {
            RAtomicLong counter = redissonClient.getAtomicLong(DEVICE_STATS + ":" + deviceType);
            distribution.put(deviceType, counter.get());
        }

        return distribution;
    }

    /**
     * 获取会话时长分布
     */
    public Map<String, Long> getSessionDurationDistribution() {
        Map<String, Long> distribution = new HashMap<>();
        String[] ranges = {"0-5min", "5-15min", "15-30min", "30-60min", "1-2h", "2-4h", "4h+"};

        for (String range : ranges) {
            RAtomicLong counter = redissonClient.getAtomicLong(DURATION_STATS + ":" + range);
            distribution.put(range, counter.get());
        }

        return distribution;
    }

    /**
     * 获取用户会话分布
     */
    public Map<String, Long> getUserSessionDistribution() {
        Map<String, Long> distribution = new HashMap<>();
        RMap<String, UserSession> sessions = redissonClient.getMap("sessions");

        for (UserSession session : sessions.values()) {
            String username = session.getUsername();
            RAtomicLong counter = redissonClient.getAtomicLong(USER_STATS + ":" + username);
            distribution.put(username, counter.get());
        }

        return distribution;
    }

    /**
     * 清理过期的统计数据
     */
    public void cleanupStaleStats() {
        // 清理设备统计
        String[] deviceTypes = {"DESKTOP", "MOBILE", "TABLET", "UNKNOWN"};
        for (String deviceType : deviceTypes) {
            RAtomicLong counter = redissonClient.getAtomicLong(DEVICE_STATS + ":" + deviceType);
            if (counter.get() == 0) {
                counter.delete();
            }
        }

        // 清理用户统计
        RMap<String, UserSession> sessions = redissonClient.getMap("sessions");
        for (String username : getUserSessionDistribution().keySet()) {
            RAtomicLong counter = redissonClient.getAtomicLong(USER_STATS + ":" + username);
            if (counter.get() == 0) {
                counter.delete();
            }
        }

        log.info("清理过期统计数据完成");
    }

    /**
     * 重置统计数据
     */
    public void resetStats() {
        redissonClient.getKeys().deleteByPattern(STATS_PREFIX + "*");
        log.info("统计数据已重置");
    }

    /**
     * 获取指定时间范围内的会话统计
     */
    public Map<String, Long> getSessionStatsByTimeRange(Instant start, Instant end) {
        Map<String, Long> stats = new HashMap<>();

        stats.put("active", getActiveSessionCount());
        stats.put("total", getTotalSessionCount());
        stats.put("expired", getExpiredSessionCount());

        return stats;
    }

    /**
     * 获取会话统计快照
     */
    public SessionStats getSessionStats() {
        return new SessionStats(
            getActiveSessionCount(),
            getTotalSessionCount(),
            getExpiredSessionCount(),
            getDeviceTypeDistribution(),
            getSessionDurationDistribution(),
            getUserSessionDistribution()
        );
    }

    /**
     * 会话统计数据快照
     */
    @Getter
    public static class SessionStats {
        private final long activeCount;
        private final long totalCount;
        private final long expiredCount;
        private final Map<String, Long> deviceDistribution;
        private final Map<String, Long> durationDistribution;
        private final Map<String, Long> userDistribution;
        private final Instant timestamp;

        public SessionStats(
            long activeCount,
            long totalCount,
            long expiredCount,
            Map<String, Long> deviceDistribution,
            Map<String, Long> durationDistribution,
            Map<String, Long> userDistribution
        ) {
            this.activeCount = activeCount;
            this.totalCount = totalCount;
            this.expiredCount = expiredCount;
            this.deviceDistribution = deviceDistribution;
            this.durationDistribution = durationDistribution;
            this.userDistribution = userDistribution;
            this.timestamp = Instant.now();
        }
    }
}
