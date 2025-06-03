package org.example.controller;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.session.SessionConcurrencyService;
import org.example.session.UserSession;
import org.example.listener.SessionSecurityAuditListener;
import org.example.listener.SessionStatisticsListener;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话管理控制器
 * 提供会话管理相关的REST API
 */
@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
@Slf4j
public class SessionController {

    private final SessionConcurrencyService concurrencyService;
    private final SessionStatisticsListener statisticsListener;
    private static final String USER_SESSION_KEY = "USER_SESSION";

    /**
     * 创建新会话
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(
        @Valid @RequestBody LoginRequest request,
        HttpServletRequest httpRequest,
        HttpSession session
    ) {
        try {
            // 记录登录尝试
            log.info("用户登录尝试 - 用户名: {}, IP: {}, User-Agent: {}", 
                request.getUsername(), 
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent"));

            // 检查并注册新会话
            List<String> invalidatedSessions = concurrencyService.registerNewSession(
                request.getUsername(),
                session.getId()
            );

            if (invalidatedSessions == null) {
                log.warn("登录被拒绝 - 用户: {} 已达到最大并发会话数限制", request.getUsername());
                return ResponseEntity
                    .status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("已达到最大并发会话数限制");
            }

            // 创建新的用户会话
            UserSession userSession = new UserSession(
                request.getUsername(),
                session.getId(),
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent")
            );

            // 保存会话信息
            session.setAttribute(USER_SESSION_KEY, userSession);

            // 记录成功登录
            if (!invalidatedSessions.isEmpty()) {
                log.info("用户: {} 登录成功，已清除 {} 个旧会话", 
                    request.getUsername(), 
                    invalidatedSessions.size());
            } else {
                log.info("用户: {} 登录成功", request.getUsername());
            }

            Map<String, Object> response = new HashMap<>();
            response.put("sessionId", session.getId());
            response.put("invalidatedSessions", invalidatedSessions);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("登录处理失败 - 用户: {}", request.getUsername(), e);
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("登录失败：" + e.getMessage());
        }
    }

    /**
     * 注销会话
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(
        @RequestParam String username,
        @RequestParam String sessionId
    ) {
        if (concurrencyService.isActiveSession(username, sessionId)) {
            concurrencyService.invalidateSession(username, sessionId);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * 获取用户的所有活跃会话
     */
    @GetMapping("/user/{username}")
    public ResponseEntity<?> getUserSessions(@PathVariable String username) {
        List<UserSession> sessions = concurrencyService.getUserSessionDetails(username);
        return ResponseEntity.ok(sessions);
    }

    /**
     * 获取会话统计信息
     */
    @GetMapping("/statistics")
    public ResponseEntity<?> getSessionStatistics() {
        SessionStatisticsListener.SessionStats stats = statisticsListener.getSessionStats();
        return ResponseEntity.ok(stats);
    }

    /**
     * 获取设备类型分布
     */
    @GetMapping("/statistics/devices")
    public ResponseEntity<?> getDeviceDistribution() {
        Map<String, Long> distribution = statisticsListener.getDeviceTypeDistribution();
        return ResponseEntity.ok(distribution);
    }

    /**
     * 获取会话时长分布
     */
    @GetMapping("/statistics/durations")
    public ResponseEntity<?> getDurationDistribution() {
        Map<String, Long> distribution = statisticsListener.getSessionDurationDistribution();
        return ResponseEntity.ok(distribution);
    }

    /**
     * 强制使用户的所有会话失效
     */
    @PostMapping("/user/{username}/invalidate")
    public ResponseEntity<?> invalidateUserSessions(@PathVariable String username) {
        concurrencyService.invalidateAllSessions(username);
        return ResponseEntity.ok().build();
    }

    /**
     * 获取会话详情
     */
    @GetMapping("/{sessionId}")
    public ResponseEntity<?> getSessionDetails(@PathVariable String sessionId) {
        UserSession session = concurrencyService.getSessionDetails(sessionId);
        if (session != null) {
            return ResponseEntity.ok(session);
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * 更新会话最后访问时间
     */
    @PostMapping("/{sessionId}/touch")
    public ResponseEntity<?> touchSession(
        @PathVariable String sessionId,
        @RequestParam String username
    ) {
        if (concurrencyService.isActiveSession(username, sessionId)) {
            concurrencyService.updateSessionLastAccessTime(username, sessionId);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * 获取活跃会话数量
     */
    @GetMapping("/count")
    public ResponseEntity<?> getActiveSessionCount() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("activeCount", statisticsListener.getActiveSessionCount());
        stats.put("totalCount", statisticsListener.getTotalSessionCount());
        stats.put("expiredCount", statisticsListener.getExpiredSessionCount());
        return ResponseEntity.ok(stats);
    }

    /**
     * 登录请求模型
     */
    @Data
    public static class LoginRequest {
        @NotBlank(message = "用户名不能为空")
        private String username;
        
        @NotBlank(message = "密码不能为空")
        private String password;
    }
}
