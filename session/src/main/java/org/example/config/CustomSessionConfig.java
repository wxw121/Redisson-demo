package org.example.config;

import lombok.RequiredArgsConstructor;
import org.example.listener.SessionConcurrencyListener;
import org.example.listener.SessionSecurityAuditListener;
import org.example.listener.SessionStatisticsListener;
import org.example.session.SessionConcurrencyService;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.SessionRepository;
import org.springframework.session.config.annotation.web.http.EnableSpringHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

import java.time.Duration;

@Configuration
@EnableSpringHttpSession
public class CustomSessionConfig {

    @Value("${server.servlet.session.timeout:1800}")
    private int sessionTimeoutSeconds;


    @Bean
    public CookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("RSESSION");
        serializer.setCookiePath("/");
        serializer.setDomainNamePattern("^.+?\\.(\\w+\\.[a-z]+)$");
        serializer.setCookieMaxAge(sessionTimeoutSeconds);
        serializer.setUseSecureCookie(false); // 开发环境设置为false，生产环境应设置为true
        serializer.setUseHttpOnlyCookie(true);
        return serializer;
    }

    /**
     * 配置会话并发监听器
     */
    @Bean
    public SessionConcurrencyListener sessionConcurrencyListener(
            RedissonClient redissonClient
    ) {
        return new SessionConcurrencyListener(redissonClient);
    }

    /**
     * 配置会话统计监听器
     */
    @Bean
    public SessionStatisticsListener sessionStatisticsListener(
            RedissonClient redissonClient
    ) {
        return new SessionStatisticsListener(redissonClient);
    }

    /**
     * 配置会话安全审计监听器
     */
    @Bean
    public SessionSecurityAuditListener sessionSecurityAuditListener(
            RedissonClient redissonClient
    ) {
        return new SessionSecurityAuditListener(redissonClient);
    }

    /**
     * 配置会话清理任务
     */
    @Bean
    public SessionCleanupTask sessionCleanupTask(
            SessionConcurrencyService concurrencyService
    ) {
        return new SessionCleanupTask(concurrencyService, sessionTimeoutSeconds);
    }

    /**
     * 会话清理任务类
     */
    @RequiredArgsConstructor
    public static class SessionCleanupTask {
        private final SessionConcurrencyService concurrencyService;
        private final long sessionTimeout;

        /**
         * 执行清理任务
         */
        public void cleanup() {
            concurrencyService.cleanupExpiredSessions(sessionTimeout);
        }
    }
}
