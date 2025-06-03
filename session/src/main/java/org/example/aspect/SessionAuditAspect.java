package org.example.aspect;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.example.annotation.SessionAudit;
import org.example.listener.SessionSecurityAuditListener;
import org.example.session.UserSession;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 会话审计切面
 * 负责拦截会话相关操作并记录审计日志
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class SessionAuditAspect {

    private final SessionSecurityAuditListener auditListener;
    private static final String USER_SESSION_KEY = "USER_SESSION";

    /**
     * 定义切点：所有带有@SessionAudit注解的方法
     */
    @Pointcut("@annotation(org.example.annotation.SessionAudit)")
    public void sessionAuditPointcut() {
    }

    /**
     * 环绕通知：记录会话操作的审计日志
     */
    @Around("sessionAuditPointcut()")
    public Object auditSessionOperation(ProceedingJoinPoint joinPoint) throws Throwable {
        // 获取方法签名
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // 获取注解
        SessionAudit auditAnnotation = method.getAnnotation(SessionAudit.class);
        String operationType = auditAnnotation.type();
        String description = auditAnnotation.description();

        // 获取当前会话信息
        UserSession userSession = getCurrentUserSession();
        String username = userSession != null ? userSession.getUsername() : "anonymous";

        // 准备审计详情
        Map<String, Object> details = prepareAuditDetails(joinPoint, userSession);

        try {
            // 记录操作开始
            log.debug("会话操作开始 - 类型: {}, 描述: {}, 用户: {}",
                operationType, description, username);

            // 执行目标方法
            Object result = joinPoint.proceed();

            // 记录操作成功
            auditListener.logSecurityEvent(
                username,
                operationType,
                description + " - 成功",
                details
            );

            return result;
        } catch (Exception e) {
            // 记录操作失败
            details.put("errorMessage", e.getMessage());
            details.put("errorType", e.getClass().getName());

            auditListener.logSecurityEvent(
                username,
                operationType,
                description + " - 失败",
                details
            );

            throw e;
        }
    }

    /**
     * 获取当前用户会话
     */
    private UserSession getCurrentUserSession() {
        try {
            ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            HttpServletRequest request = attributes.getRequest();
            HttpSession session = request.getSession(false);

            if (session != null) {
                return (UserSession) session.getAttribute(USER_SESSION_KEY);
            }
        } catch (Exception e) {
            log.debug("获取当前会话失败: {}", e.getMessage());
        }

        return null;
    }

    /**
     * 准备审计详情
     */
    private Map<String, Object> prepareAuditDetails(JoinPoint joinPoint, UserSession userSession) {
        Map<String, Object> details = new HashMap<>();

        // 添加方法信息
        details.put("method", joinPoint.getSignature().toShortString());

        // 添加参数信息（过滤敏感信息）
        String[] paramNames = ((MethodSignature) joinPoint.getSignature()).getParameterNames();
        Object[] args = joinPoint.getArgs();

        Map<String, Object> params = new HashMap<>();
        for (int i = 0; i < paramNames.length; i++) {
            // 过滤敏感参数
            if (!paramNames[i].toLowerCase().contains("password")) {
                params.put(paramNames[i], args[i]);
            } else {
                params.put(paramNames[i], "******");
            }
        }
        details.put("parameters", params);

        // 添加会话信息
        if (userSession != null) {
            details.put("sessionId", userSession.getSessionId());
            details.put("ipAddress", userSession.getIpAddress());
            details.put("userAgent", userSession.getUserAgent());
        }

        // 添加请求信息
        try {
            ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            HttpServletRequest request = attributes.getRequest();

            details.put("requestURI", request.getRequestURI());
            details.put("requestMethod", request.getMethod());
            details.put("remoteAddr", request.getRemoteAddr());
        } catch (Exception e) {
            log.debug("获取请求信息失败: {}", e.getMessage());
        }

        return details;
    }
}
