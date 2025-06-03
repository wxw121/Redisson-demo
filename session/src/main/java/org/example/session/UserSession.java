package org.example.session;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 用户会话信息
 * 包含会话的基本属性和操作方法
 */
@Getter
@ToString
public class UserSession implements Serializable {

    private static final long serialVersionUID = 1L;

    // 基本信息
    private final String username;
    private final String sessionId;
    private final String ipAddress;
    private final String userAgent;
    private final String deviceType;
    private final long creationTime;

    // 状态信息
    private long lastAccessTime;
    @Setter
    private boolean authenticated;
    @Setter
    private boolean active;

    // 附加信息
    private final Map<String, Object> attributes;

    /**
     * 构造函数
     */
    public UserSession(String username, String sessionId, String ipAddress, String userAgent) {
        this.username = username;
        this.sessionId = sessionId;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.deviceType = determineDeviceType(userAgent);
        this.creationTime = System.currentTimeMillis();
        this.lastAccessTime = this.creationTime;
        this.authenticated = true;
        this.active = true;
        this.attributes = new HashMap<>();
    }

    /**
     * 更新最后访问时间
     */
    public void updateLastAccessTime() {
        this.lastAccessTime = System.currentTimeMillis();
    }

    /**
     * 获取会话持续时间（毫秒）
     */
    public long getDuration() {
        return System.currentTimeMillis() - creationTime;
    }

    /**
     * 获取最后活动间隔（毫秒）
     */
    public long getLastActivityInterval() {
        return System.currentTimeMillis() - lastAccessTime;
    }

    /**
     * 获取会话创建时间的Instant表示
     */
    public Instant getCreationInstant() {
        return Instant.ofEpochMilli(creationTime);
    }

    /**
     * 获取最后访问时间的Instant表示
     */
    public Instant getLastAccessInstant() {
        return Instant.ofEpochMilli(lastAccessTime);
    }

    /**
     * 获取会话空闲时间
     */
    public Duration getIdleTime() {
        return Duration.ofMillis(getLastActivityInterval());
    }

    /**
     * 获取会话总时长
     */
    public Duration getTotalDuration() {
        return Duration.ofMillis(getDuration());
    }

    /**
     * 使会话失效
     */
    public void invalidate() {
        this.active = false;
    }

    /**
     * 添加属性
     */
    public void setAttribute(String name, Object value) {
        attributes.put(name, value);
    }

    /**
     * 获取属性
     */
    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    /**
     * 移除属性
     */
    public void removeAttribute(String name) {
        attributes.remove(name);
    }

    /**
     * 获取所有属性
     */
    public Map<String, Object> getAttributes() {
        return new HashMap<>(attributes);
    }

    /**
     * 判断设备类型
     */
    private String determineDeviceType(String userAgent) {
        if (userAgent == null) {
            return "UNKNOWN";
        }

        String lowerCaseUserAgent = userAgent.toLowerCase();

        if (lowerCaseUserAgent.contains("mobile") || lowerCaseUserAgent.contains("android") ||
            lowerCaseUserAgent.contains("iphone")) {
            return "MOBILE";
        } else if (lowerCaseUserAgent.contains("tablet") || lowerCaseUserAgent.contains("ipad")) {
            return "TABLET";
        } else {
            return "DESKTOP";
        }
    }

    /**
     * 生成唯一会话标识
     */
    public static String generateSessionId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 创建匿名会话
     */
    public static UserSession createAnonymousSession(String ipAddress, String userAgent) {
        return new UserSession("anonymous", generateSessionId(), ipAddress, userAgent);
    }

    /**
     * 创建系统会话
     */
    public static UserSession createSystemSession() {
        return new UserSession("system", generateSessionId(), "127.0.0.1", "System/1.0");
    }
}
