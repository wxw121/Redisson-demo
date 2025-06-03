package org.example.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.session.UserSession;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.session.Session;
import org.springframework.session.events.*;
import org.springframework.stereotype.Component;

/**
 * 会话并发监听器
 * 负责监听和处理会话并发相关的事件
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionConcurrencyListener implements SessionEventHandler {

    private final RedissonClient redissonClient;
    private static final String USER_SESSION_KEY = "USER_SESSION";
    private static final String SESSIONS_KEY = "sessions";

    @Override
    public void onSessionCreated(SessionCreatedEvent event) {
        String sessionId = event.getSessionId();
        Session session = event.getSession();

        if (session != null) {
            UserSession userSession = session.getAttribute(USER_SESSION_KEY);
            if (userSession != null) {
                // 将会话信息保存到Redis
                RMap<String, UserSession> sessions = redissonClient.getMap(SESSIONS_KEY);
                sessions.put(sessionId, userSession);

                log.debug("会话创建 - 用户: {}, 会话ID: {}", userSession.getUsername(), sessionId);
            }
        }
    }

    @Override
    public void onSessionExpired(SessionExpiredEvent event) {
        handleSessionEnd(event.getSessionId(), "过期");
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
        RMap<String, UserSession> sessions = redissonClient.getMap(SESSIONS_KEY);
        UserSession userSession = sessions.remove(sessionId);

        if (userSession != null) {
            log.debug("会话{} - 用户: {}, 会话ID: {}",
                endType, userSession.getUsername(), sessionId);
        }
    }

    /**
     * 获取活跃会话数量
     */
    public long getActiveSessionCount() {
        RMap<String, UserSession> sessions = redissonClient.getMap(SESSIONS_KEY);
        return sessions.size();
    }

    /**
     * 获取指定用户的活跃会话数量
     */
    public long getUserActiveSessionCount(String username) {
        RMap<String, UserSession> sessions = redissonClient.getMap(SESSIONS_KEY);
        return sessions.values().stream()
            .filter(session -> username.equals(session.getUsername()))
            .count();
    }

    /**
     * 检查用户是否有活跃会话
     */
    public boolean hasActiveSession(String username) {
        return getUserActiveSessionCount(username) > 0;
    }

    /**
     * 获取所有活跃会话
     */
    public RMap<String, UserSession> getActiveSessions() {
        return redissonClient.getMap(SESSIONS_KEY);
    }

    /**
     * 清理指定用户的所有会话
     */
    public void clearUserSessions(String username) {
        RMap<String, UserSession> sessions = redissonClient.getMap(SESSIONS_KEY);

        // 找出用户的所有会话
        sessions.entrySet().stream()
            .filter(entry -> username.equals(entry.getValue().getUsername()))
            .forEach(entry -> {
                String sessionId = entry.getKey();
                sessions.remove(sessionId);
                log.info("清理用户会话 - 用户: {}, 会话ID: {}", username, sessionId);
            });
    }

    /**
     * 检查会话是否存在
     */
    public boolean isSessionExists(String sessionId) {
        RMap<String, UserSession> sessions = redissonClient.getMap(SESSIONS_KEY);
        return sessions.containsKey(sessionId);
    }

    /**
     * 获取会话信息
     */
    public UserSession getSession(String sessionId) {
        RMap<String, UserSession> sessions = redissonClient.getMap(SESSIONS_KEY);
        return sessions.get(sessionId);
    }

    /**
     * 更新会话信息
     */
    public void updateSession(String sessionId, UserSession userSession) {
        RMap<String, UserSession> sessions = redissonClient.getMap(SESSIONS_KEY);
        sessions.put(sessionId, userSession);
    }

    /**
     * 获取用户的所有会话
     */
    public java.util.List<UserSession> getUserSessions(String username) {
        RMap<String, UserSession> sessions = redissonClient.getMap(SESSIONS_KEY);
        return sessions.values().stream()
            .filter(session -> username.equals(session.getUsername()))
            .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 检查并发会话限制
     */
    public boolean isWithinConcurrencyLimits(String username, int maxSessions) {
        long activeCount = getUserActiveSessionCount(username);
        return activeCount < maxSessions;
    }
}
