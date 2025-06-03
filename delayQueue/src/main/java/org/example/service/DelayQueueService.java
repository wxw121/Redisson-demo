package org.example.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.config.OrderProperties;
import org.example.entity.Order;
import org.example.exception.BusinessException;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 延迟队列服务
 * 使用Redisson实现延迟队列功能
 */
public interface DelayQueueService {


    void addOrderToDelayQueue(Order order);

    /**
     * 添加订单到延迟队列，使用指定的超时时间
     *
     * @param order 订单信息
     * @param timeoutMinutes 超时时间（分钟）
     */
    void addOrderToDelayQueue(Order order, long timeoutMinutes);

    /**
     * 从延迟队列中移除订单
     *
     * @param order 订单信息
     */
    void removeOrderFromDelayQueue(Order order);
}
