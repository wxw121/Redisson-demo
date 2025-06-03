package org.example.session;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RMap;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 会话并发控制服务
 * 负责管理用户的并发会话数量和会话注册
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionConcurrencyService {

    private final RedissonClient redissonClient;
    
    private static final String MAX_SESSIONS_KEY = "config:max-sessions-per-user";
    
    @Value("${session.concurrent.max-sessions-per-user:3}")
    private int defaultMaxSessionsPerUser;

    @Value("${session.concurrent.prevent-login:true}")
    private boolean preventLogin;

    /**
     * 初始化时设置默认的最大会话数
     */
    @PostConstruct
    public void initializeMaxSessions() {
        redissonClient.getBucket(MAX_SESSIONS_KEY).trySet(defaultMaxSessionsPerUser);
    }

    /**
     * 设置每个用户允许的最大并发会话数
     * @param maxSessions 最大会话数（必须大于0）
     * @throws IllegalArgumentException 如果maxSessions小于或等于0
     */
    public void setMaxConcurrentSessions(int maxSessions) {
        if (maxSessions <= 0) {
            throw new IllegalArgumentException("最大会话数必须大于0");
        }
        redissonClient.getBucket(MAX_SESSIONS_KEY).set(maxSessions);
        log.info("已更新最大并发会话数为: {}", maxSessions);
    }

    /**
     * 获取当前配置的最大并发会话数
     * @return 最大并发会话数
     */
    private int getMaxConcurrentSessions() {
        Integer maxSessions = (Integer) redissonClient.getBucket(MAX_SESSIONS_KEY).get();
        return maxSessions != null ? maxSessions : defaultMaxSessionsPerUser;
    }

    private static final String USER_SESSIONS_KEY = "user:sessions:";
    private static final String SESSIONS_KEY = "sessions";

    /**
     * 注册新会话
     * @param username 用户名
     * @param sessionId 会话ID
     * @return 被失效的会话ID列表
     */
    public List<String> registerNewSession(String username, String sessionId) {
        List<String> invalidatedSessions = new ArrayList<>();
        String userSessionsKey = USER_SESSIONS_KEY + username;
        
        // 使用Redisson的RLock确保并发安全
        try {
            // 获取用户会话的分布式锁
            redissonClient.getLock("lock:" + userSessionsKey).lock();
            
            RSet<String> userSessions = redissonClient.getSet(userSessionsKey);
            RMap<String, UserSession> sessions = redissonClient.getMap(SESSIONS_KEY);

            try {
                // 检查是否达到最大并发会话数
                if (userSessions.size() >= getMaxConcurrentSessions()) {
                    if (preventLogin) {
                        log.warn("用户 {} 达到最大并发会话数限制", username);
                        return null;
                    } else {
                        // 获取最老的会话并使其失效
                        String oldestSessionId = findOldestSession(userSessions, sessions);
                        if (oldestSessionId != null) {
                            invalidateSession(username, oldestSessionId);
                            invalidatedSessions.add(oldestSessionId);
                        }
                    }
                }

                // 注册新会话
                userSessions.add(sessionId);
                log.info("用户 {} 注册新会话: {}", username, sessionId);

                return invalidatedSessions;
            } catch (Exception e) {
                log.error("注册新会话时发生错误 - 用户: {}, 会话ID: {}", username, sessionId, e);
                throw new RuntimeException("注册会话失败", e);
            }
        } finally {
            // 确保锁一定会被释放
            redissonClient.getLock("lock:" + userSessionsKey).unlock();
        }
    }

    /**
     * 使会话失效
     * @param username 用户名
     * @param sessionId 会话ID
     */
    public void invalidateSession(String username, String sessionId) {
        RSet<String> userSessions = redissonClient.getSet(USER_SESSIONS_KEY + username);
        RMap<String, UserSession> sessions = redissonClient.getMap(SESSIONS_KEY);

        // 从用户会话集合中移除
        userSessions.remove(sessionId);

        // 从会话映射中移除
        UserSession session = sessions.remove(sessionId);
        if (session != null) {
            session.invalidate();
        }

        log.info("会话已失效 - 用户: {}, 会话ID: {}", username, sessionId);
    }

    /**
     * 检查会话是否有效
     * @param username 用户名
     * @param sessionId 会话ID
     * @return 是否有效
     */
    public boolean isActiveSession(String username, String sessionId) {
        RSet<String> userSessions = redissonClient.getSet(USER_SESSIONS_KEY + username);
        return userSessions.contains(sessionId);
    }

    /**
     * 获取用户的所有会话
     * @param username 用户名
     * @return 会话ID列表
     */
    public List<String> getUserSessions(String username) {
        RSet<String> userSessions = redissonClient.getSet(USER_SESSIONS_KEY + username);
        return new ArrayList<>(userSessions);
    }

    /**
     * 获取用户的活跃会话数
     * @param username 用户名
     * @return 活跃会话数
     */
    public int getActiveSessionCount(String username) {
        RSet<String> userSessions = redissonClient.getSet(USER_SESSIONS_KEY + username);
        return userSessions.size();
    }

    private static final int CLEANUP_BATCH_SIZE = 100;

    /**
     * 清理过期会话
     * @param sessionTimeout 会话超时时间（毫秒）
     */
    @Transactional
    public void cleanupExpiredSessions(long sessionTimeout) {
        RMap<String, UserSession> sessions = redissonClient.getMap(SESSIONS_KEY);
        int totalCleaned = 0;
        String lastKey = null;

        try {
            while (true) {
                // 分批获取会话
                String finalLastKey = lastKey;
                Set<String> batch = sessions.keySet().stream()
                    .filter(key -> finalLastKey == null || key.compareTo(finalLastKey) > 0)
                    .limit(CLEANUP_BATCH_SIZE)
                    .collect(Collectors.toSet());

                if (batch.isEmpty()) {
                    break;
                }

                // 处理当前批次
                for (String sessionId : batch) {
                    try {
                        UserSession session = sessions.get(sessionId);
                        if (session != null && isSessionExpired(session, sessionTimeout)) {
                            invalidateSession(session.getUsername(), sessionId);
                            totalCleaned++;
                            log.debug("清理过期会话 - 用户: {}, 会话ID: {}", session.getUsername(), sessionId);
                        }
                    } catch (Exception e) {
                        log.error("清理会话时发生错误 - 会话ID: {}", sessionId, e);
                        // 继续处理其他会话
                    }
                    lastKey = sessionId;
                }

                // 每批处理完后短暂休息，避免对Redis造成过大压力
                Thread.sleep(100);
            }

            if (totalCleaned > 0) {
                log.info("清理了 {} 个过期会话", totalCleaned);
            }
        } catch (Exception e) {
            log.error("清理过期会话时发生错误", e);
            throw new RuntimeException("清理过期会话失败", e);
        }
    }

    /**
     * 判断会话是否过期
     */
    private boolean isSessionExpired(UserSession session, long sessionTimeout) {
        if (session == null) {
            return true;
        }
        // 检查绝对过期时间
        long absoluteExpireTime = session.getCreationTime() + sessionTimeout;
        if (System.currentTimeMillis() > absoluteExpireTime) {
            return true;
        }
        // 检查最后活动时间
        return session.getLastActivityInterval() > sessionTimeout;
    }

    /**
     * 查找最老的会话
     */
    private String findOldestSession(RSet<String> userSessions, RMap<String, UserSession> sessions) {
        long oldestTime = Long.MAX_VALUE;
        String oldestSessionId = null;

        for (String sessionId : userSessions) {
            UserSession session = sessions.get(sessionId);
            if (session != null && session.getCreationTime() < oldestTime) {
                oldestTime = session.getCreationTime();
                oldestSessionId = sessionId;
            }
        }

        return oldestSessionId;
    }


    /**
     * 获取所有活跃会话
     */
    public List<UserSession> getAllActiveSessions() {
        RMap<String, UserSession> sessions = redissonClient.getMap(SESSIONS_KEY);
        return new ArrayList<>(sessions.values());
    }

    /**
     * 获取指定用户的会话详情
     */
    public List<UserSession> getUserSessionDetails(String username) {
        RSet<String> userSessions = redissonClient.getSet(USER_SESSIONS_KEY + username);
        RMap<String, UserSession> sessions = redissonClient.getMap(SESSIONS_KEY);

        return userSessions.stream()
            .map(sessions::get)
            .filter(session -> session != null)
            .collect(Collectors.toList());
    }

    /**
     * 强制使所有会话失效
     */
    public void invalidateAllSessions(String username) {
        List<String> sessionIds = getUserSessions(username);
        for (String sessionId : sessionIds) {
            invalidateSession(username, sessionId);
        }
        log.info("已使用户 {} 的所有会话失效", username);
    }

    /**
     * 更新会话最后访问时间
     */
    public void updateSessionLastAccessTime(String username, String sessionId) {
        RMap<String, UserSession> sessions = redissonClient.getMap(SESSIONS_KEY);
        UserSession session = sessions.get(sessionId);

        if (session != null && session.getUsername().equals(username)) {
            session.updateLastAccessTime();
            sessions.put(sessionId, session);
        }
    }

    /**
     * 获取会话详情
     */
    public UserSession getSessionDetails(String sessionId) {
        RMap<String, UserSession> sessions = redissonClient.getMap(SESSIONS_KEY);
        return sessions.get(sessionId);
    }

    /**
     * 检查会话是否存在且有效
     * @param sessionId 会话ID
     * @return 如果会话存在且未过期返回true，否则返回false
     */
    public boolean sessionExists(String sessionId) {
        RMap<String, UserSession> sessions = redissonClient.getMap(SESSIONS_KEY);
        UserSession session = sessions.get(sessionId);
        
        if (session == null) {
            return false;
        }

        // 检查会话是否过期
        if (!isSessionExpired(session, getSessionTimeout())) {
            return true;
        }
        // 如果过期，清理会话
        removeSessionById(sessionId);
        return false;

    }

    /**
     * 根据会话ID移除会话
     * @param sessionId 会话ID
     */
    public void removeSessionById(String sessionId) {
        RMap<String, UserSession> sessions = redissonClient.getMap(SESSIONS_KEY);
        UserSession session = sessions.get(sessionId);
        
        if (session != null) {
            String username = session.getUsername();
            String userSessionsKey = USER_SESSIONS_KEY + username;
            
            try {
                // 获取用户会话的分布式锁
                redissonClient.getLock("lock:" + userSessionsKey).lock();
                
                // 从会话映射中移除
                sessions.remove(sessionId);
                
                // 从用户会话集合中移除
                RSet<String> userSessions = redissonClient.getSet(userSessionsKey);
                userSessions.remove(sessionId);
                
                log.info("会话已移除 - 用户: {}, 会话ID: {}", username, sessionId);
            } finally {
                // 确保锁一定会被释放
                redissonClient.getLock("lock:" + userSessionsKey).unlock();
            }
        }
    }

    /**
     * 获取配置的会话超时时间
     * @return 会话超时时间（毫秒）
     */
    private long getSessionTimeout() {
        // 默认30分钟
        return 30 * 60 * 1000L;
    }
}
