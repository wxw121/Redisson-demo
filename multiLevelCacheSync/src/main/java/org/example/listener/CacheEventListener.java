package org.example.listener;

import org.example.cache.event.CacheEvent;

/**
 * 缓存事件监听器接口
 */
public interface CacheEventListener {

    /**
     * 处理缓存事件
     *
     * @param event 缓存事件
     */
    void onEvent(CacheEvent event);

    /**
     * 获取监听器名称
     *
     * @return 监听器名称
     */
    String getName();

    /**
     * 启动监听器
     */
    void start();

    /**
     * 停止监听器
     */
    void stop();

    /**
     * 发布事件
     *
     * @param event 缓存事件
     */
    void publishEvent(CacheEvent event);
}
