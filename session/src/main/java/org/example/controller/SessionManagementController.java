package org.example.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.annotation.SessionAudit;
import org.example.listener.SessionConcurrencyListener;
import org.example.listener.SessionEventListener;
import org.example.listener.SessionSecurityAuditListener;
import org.example.listener.SessionStatisticsListener;
import org.example.session.SessionConcurrencyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话管理控制器
 * 提供会话统计和管理的REST API
 */
@Slf4j
@RestController
@RequestMapping("/api/sessions/management")
@RequiredArgsConstructor
public class SessionManagementController {

    private final SessionEventListener sessionEventListener;
    private final SessionConcurrencyService concurrencyService;

    /**
     * 获取会话统计信息
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getSessionStatistics() {
        SessionStatisticsListener statisticsListener =
            sessionEventListener.getHandler(SessionStatisticsListener.class);

        Map<String, Object> statistics = new HashMap<>();
        statistics.put("activeSessionCount", statisticsListener.getActiveSessionCount());
        statistics.put("totalCreatedSessions", statisticsListener.getTotalSessionCount());
        statistics.put("totalExpiredSessions", statisticsListener.getExpiredSessionCount());

        return ResponseEntity.ok(statistics);
    }

    /**
     * 获取用户的活跃会话
     */
    @SessionAudit(
        type = "VIEW_USER_SESSIONS",
        description = "查看用户活跃会话列表",
        logParams = true,
        logResult = true
    )
    @GetMapping("/users/{username}")
    public ResponseEntity<Map<String, Object>> getUserSessions(@PathVariable String username) {
        List<String> sessions = concurrencyService.getUserSessions(username);

        Map<String, Object> result = new HashMap<>();
        result.put("username", username);
        result.put("activeSessions", sessions);
        result.put("sessionCount", sessions.size());

        return ResponseEntity.ok(result);
    }

    /**
     * 使指定用户的所有会话失效
     */
    @SessionAudit(
        type = "INVALIDATE_USER_SESSIONS",
        description = "使指定用户的所有会话失效",
        logParams = true,
        logResult = true
    )
    @DeleteMapping("/users/{username}")
    public ResponseEntity<Map<String, Object>> invalidateUserSessions(@PathVariable String username) {
//        SessionConcurrencyListener concurrencyListener =
//            sessionEventListener.getHandler(SessionConcurrencyListener.class);

        List<String> sessions = concurrencyService.getUserSessions(username);
        int sessionCount = sessions.size();

        concurrencyService.invalidateAllSessions(username);

        Map<String, Object> result = new HashMap<>();
        result.put("username", username);
        result.put("invalidatedSessions", sessionCount);
        result.put("success", true);

        return ResponseEntity.ok(result);
    }

    /**
     * 使指定会话失效
     */
    @SessionAudit(
        type = "INVALIDATE_SESSION",
        description = "使指定会话失效",
        logParams = true,
        logResult = true
    )
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Map<String, Object>> invalidateSession(@PathVariable String sessionId) {
        boolean exists = concurrencyService.sessionExists(sessionId);

        if (exists) {
            concurrencyService.removeSessionById(sessionId);

            Map<String, Object> result = new HashMap<>();
            result.put("sessionId", sessionId);
            result.put("invalidated", true);

            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 获取最近的安全审计日志
     */
    @GetMapping("/audit-logs")
    public ResponseEntity<List<Map<String, Object>>> getAuditLogs() {
        SessionSecurityAuditListener auditListener =
            sessionEventListener.getHandler(SessionSecurityAuditListener.class);

        return ResponseEntity.ok(auditListener.getRecentAuditLogs());
    }

    /**
     * 设置最大并发会话数
     */
    @PutMapping("/max-concurrent")
    public ResponseEntity<Map<String, Object>> setMaxConcurrentSessions(@RequestParam int maxSessions) {
        if (maxSessions < 1) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "最大并发会话数必须大于0"
            ));
        }

        concurrencyService.setMaxConcurrentSessions(maxSessions);

        Map<String, Object> result = new HashMap<>();
        result.put("maxConcurrentSessions", maxSessions);
        result.put("updated", true);

        return ResponseEntity.ok(result);
    }


}
