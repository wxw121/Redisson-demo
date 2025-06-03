package org.example.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.session.UserSession;
import org.redisson.api.RList;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.session.Session;
import org.springframework.session.events.*;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话安全审计监听器
 * 负责记录和管理会话相关的安全事件
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionSecurityAuditListener implements SessionEventHandler {

    private final RedissonClient redissonClient;
    private static final String AUDIT_LOG_KEY = "session:audit:logs";
    private static final String USER_SESSION_KEY = "USER_SESSION";
    private static final int MAX_AUDIT_LOGS = 1000;

    @Override
    public void onSessionCreated(SessionCreatedEvent event) {
        String sessionId = event.getSessionId();
        Session session = event.getSession();

        if (session != null) {
            UserSession userSession = session.getAttribute(USER_SESSION_KEY);
            if (userSession != null) {
                recordAuditEvent(
                    "SESSION_CREATED",
                    sessionId,
                    userSession,
                    "用户会话创建",
                    Map.of(
                        "userAgent", userSession.getUserAgent()
                    )
                );
            }
        }
    }

    @Override
    public void onSessionDeleted(SessionDeletedEvent event) {
        handleSessionEnd(event.getSessionId(), "SESSION_DELETED", "用户会话删除");
    }

    @Override
    public void onSessionExpired(SessionExpiredEvent event) {
        handleSessionEnd(event.getSessionId(), "SESSION_EXPIRED", "用户会话过期");
    }

    @Override
    public void onSessionDestroyed(SessionDestroyedEvent event) {
        handleSessionEnd(event.getSessionId(), "SESSION_DESTROYED", "用户会话销毁");
    }

    /**
     * 处理会话结束事件
     */
    private void handleSessionEnd(String sessionId, String eventType, String description) {
        // 从Redis中获取会话信息
        RMap<String, UserSession> userSessions = redissonClient.getMap("session:user_sessions");
        UserSession userSession = userSessions.get(sessionId);
        
        if (userSession != null) {
            recordAuditEvent(
                eventType,
                sessionId,
                userSession,
                description,
                Map.of(
                    "sessionDuration", System.currentTimeMillis() - userSession.getCreationTime(),
                    "lastActivityTime", userSession.getLastAccessTime()
                )
            );
            
            // 从Redis中移除会话信息
            userSessions.remove(sessionId);
        } else {
            log.warn("处理会话结束事件时未找到会话信息 - SessionID: {}, 事件类型: {}", 
                sessionId, eventType);
        }
    }

    /**
     * 记录审计事件
     */
    private void recordAuditEvent(
            String eventType,
            String sessionId,
            UserSession userSession,
            String description,
            Map<String, Object> details) {
        
        Map<String, Object> auditLog = new HashMap<>();
        auditLog.put("timestamp", Instant.now().toString());
        auditLog.put("eventType", eventType);
        auditLog.put("sessionId", sessionId);
        auditLog.put("username", userSession.getUsername());
        auditLog.put("description", description);
        auditLog.put("userAgent", userSession.getUserAgent());
        auditLog.put("details", details);

        RList<Map<String, Object>> auditLogs = redissonClient.getList(AUDIT_LOG_KEY);
        
        // 使用管道批量操作以提高性能
        auditLogs.addAsync(auditLog);
        
        // 如果超过最大日志数，删除最旧的日志
        if (auditLogs.size() > MAX_AUDIT_LOGS) {
            auditLogs.removeAsync(0);
        }

        log.debug("记录会话审计日志 - 类型: {}, 用户: {}, 会话: {}", 
            eventType, userSession.getUsername(), sessionId);
    }

    /**
     * 获取最近的审计日志
     */
    public List<Map<String, Object>> getRecentAuditLogs() {
        RList<Map<String, Object>> auditLogs = redissonClient.getList(AUDIT_LOG_KEY);
        return new ArrayList<>(auditLogs);
    }

    /**
     * 获取指定用户的审计日志
     */
    public List<Map<String, Object>> getUserAuditLogs(String username) {
        RList<Map<String, Object>> allLogs = redissonClient.getList(AUDIT_LOG_KEY);
        List<Map<String, Object>> userLogs = new ArrayList<>();

        for (Map<String, Object> log : allLogs) {
            if (username.equals(log.get("username"))) {
                userLogs.add(log);
            }
        }

        return userLogs;
    }

    /**
     * 获取指定会话的审计日志
     */
    public List<Map<String, Object>> getSessionAuditLogs(String sessionId) {
        RList<Map<String, Object>> allLogs = redissonClient.getList(AUDIT_LOG_KEY);
        List<Map<String, Object>> sessionLogs = new ArrayList<>();

        for (Map<String, Object> log : allLogs) {
            if (sessionId.equals(log.get("sessionId"))) {
                sessionLogs.add(log);
            }
        }

        return sessionLogs;
    }

    /**
     * 记录安全事件
     * 用于记录由SessionAuditAspect捕获的操作日志
     *
     * @param username      用户名
     * @param operationType 操作类型
     * @param description  操作描述
     * @param details      操作详情
     */
    public void logSecurityEvent(
            String username,
            String operationType,
            String description,
            Map<String, Object> details) {
        
        Map<String, Object> auditLog = new HashMap<>();
        auditLog.put("timestamp", Instant.now().toString());
        auditLog.put("eventType", operationType);
        auditLog.put("username", username);
        auditLog.put("description", description);
        auditLog.put("details", details);

        // 如果details中包含sessionId，添加到主层级
        if (details != null && details.containsKey("sessionId")) {
            auditLog.put("sessionId", details.get("sessionId"));
        }

        RList<Map<String, Object>> auditLogs = redissonClient.getList(AUDIT_LOG_KEY);
        
        // 使用管道批量操作以提高性能
        auditLogs.addAsync(auditLog);
        
        // 如果超过最大日志数，删除最旧的日志
        if (auditLogs.size() > MAX_AUDIT_LOGS) {
            auditLogs.removeAsync(0);
        }

        log.debug("记录安全审计日志 - 类型: {}, 用户: {}, 描述: {}", 
            operationType, username, description);
    }

    /**
     * 清理指定时间之前的审计日志
     */
    public void cleanupAuditLogs(Instant before) {
        RList<Map<String, Object>> auditLogs = redissonClient.getList(AUDIT_LOG_KEY);

        auditLogs.removeIf(a -> {
            String timestamp = (String) a.get("timestamp");
            if (timestamp != null) {
                try {
                    Instant logTime = Instant.parse(timestamp);
                    return logTime.isBefore(before);
                } catch (Exception e) {
                    log.warn("解析审计日志时间戳失败: {}", timestamp, e);
                    return false;
                }
            }
            return false;
        });
    }
}
