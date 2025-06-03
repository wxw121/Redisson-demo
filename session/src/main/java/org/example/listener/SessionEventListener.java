package org.example.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.session.events.*;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 会话事件监听器
 * 作为事件委托器，将事件分发给专门的监听器处理
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionEventListener {

    private final List<SessionEventHandler> eventHandlers;

    @EventListener
    public void onSessionCreated(SessionCreatedEvent event) {
        String sessionId = event.getSessionId();
        log.debug("会话创建事件分发 - SessionID: {}", sessionId);

        // 按顺序通知所有处理器
        for (SessionEventHandler handler : eventHandlers) {
            try {
                handler.onSessionCreated(event);
            } catch (Exception e) {
                log.error("处理会话创建事件时发生错误 - 处理器: {}, SessionID: {}",
                    handler.getClass().getSimpleName(), sessionId, e);
            }
        }
    }

    @EventListener
    public void onSessionDeleted(SessionDeletedEvent event) {
        String sessionId = event.getSessionId();
        log.debug("会话删除事件分发 - SessionID: {}", sessionId);

        for (SessionEventHandler handler : eventHandlers) {
            try {
                handler.onSessionDeleted(event);
            } catch (Exception e) {
                log.error("处理会话删除事件时发生错误 - 处理器: {}, SessionID: {}",
                    handler.getClass().getSimpleName(), sessionId, e);
            }
        }
    }

    @EventListener
    public void onSessionExpired(SessionExpiredEvent event) {
        String sessionId = event.getSessionId();
        log.debug("会话过期事件分发 - SessionID: {}", sessionId);

        for (SessionEventHandler handler : eventHandlers) {
            try {
                handler.onSessionExpired(event);
            } catch (Exception e) {
                log.error("处理会话过期事件时发生错误 - 处理器: {}, SessionID: {}",
                    handler.getClass().getSimpleName(), sessionId, e);
            }
        }
    }

    @EventListener
    public void onSessionDestroyed(SessionDestroyedEvent event) {
        String sessionId = event.getSessionId();
        log.debug("会话销毁事件分发 - SessionID: {}", sessionId);

        for (SessionEventHandler handler : eventHandlers) {
            try {
                handler.onSessionDestroyed(event);
            } catch (Exception e) {
                log.error("处理会话销毁事件时发生错误 - 处理器: {}, SessionID: {}",
                    handler.getClass().getSimpleName(), sessionId, e);
            }
        }
    }

    /**
     * 获取所有事件处理器
     */
    public List<SessionEventHandler> getEventHandlers() {
        return eventHandlers;
    }

    /**
     * 获取指定类型的事件处理器
     */
    public <T extends SessionEventHandler> T getHandler(Class<T> handlerType) {
        return eventHandlers.stream()
            .filter(handler -> handlerType.isInstance(handler))
            .map(handlerType::cast)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "未找到类型为 " + handlerType.getSimpleName() + " 的事件处理器"));
    }
}
