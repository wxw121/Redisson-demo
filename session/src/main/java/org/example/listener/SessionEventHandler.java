package org.example.listener;

import org.springframework.session.events.SessionCreatedEvent;
import org.springframework.session.events.SessionDeletedEvent;
import org.springframework.session.events.SessionDestroyedEvent;
import org.springframework.session.events.SessionExpiredEvent;

/**
 * 会话事件处理接口
 * 定义了处理会话生命周期事件的标准方法
 */
public interface SessionEventHandler {

    /**
     * 处理会话创建事件
     * @param event 会话创建事件
     */
    void onSessionCreated(SessionCreatedEvent event);

    /**
     * 处理会话过期事件
     * @param event 会话过期事件
     */
    void onSessionExpired(SessionExpiredEvent event);

    /**
     * 处理会话删除事件
     * @param event 会话删除事件
     */
    void onSessionDeleted(SessionDeletedEvent event);

    /**
     * 处理会话销毁事件
     * @param event 会话销毁事件
     */
    void onSessionDestroyed(SessionDestroyedEvent event);
}
