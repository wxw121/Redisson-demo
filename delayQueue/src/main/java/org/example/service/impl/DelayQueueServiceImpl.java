package org.example.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.config.OrderProperties;
import org.example.entity.Order;
import org.example.exception.BusinessException;
import org.example.service.DelayQueueService;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class DelayQueueServiceImpl implements DelayQueueService {
    private final RedissonClient redissonClient;
    private final OrderProperties orderProperties;
    private static final String ORDER_TIMEOUT_QUEUE = "order:timeout:queue";


    /**
     * 添加订单到延迟队列，使用配置的默认超时时间
     *
     * @param order 订单信息
     */
    public void addOrderToDelayQueue(Order order) {
        addOrderToDelayQueue(order, orderProperties.getPaymentTimeoutMinutes());
    }

    /**
     * 添加订单到延迟队列，使用指定的超时时间
     *
     * @param order 订单信息
     * @param timeoutMinutes 超时时间（分钟）
     */
    public void addOrderToDelayQueue(Order order, long timeoutMinutes) {
        try {
            RBlockingQueue<Order> blockingQueue = redissonClient.getBlockingQueue(ORDER_TIMEOUT_QUEUE);
            RDelayedQueue<Order> delayedQueue = redissonClient.getDelayedQueue(blockingQueue);

            // 将订单添加到延迟队列，设置超时时间
            delayedQueue.offer(order, timeoutMinutes, TimeUnit.MINUTES);

            log.info("订单已加入延迟队列: 订单号={}, 支付超时时间={}分钟",
                    order.getOrderNo(), timeoutMinutes);
        } catch (Exception e) {
            log.error("添加订单到延迟队列失败: 订单号={}", order.getOrderNo(), e);
            throw new BusinessException("添加订单到延迟队列失败");
        }
    }

    /**
     * 从延迟队列中移除订单
     *
     * @param order 订单信息
     */
    public void removeOrderFromDelayQueue(Order order) {
        try {
            RBlockingQueue<Order> blockingQueue = redissonClient.getBlockingQueue(ORDER_TIMEOUT_QUEUE);
            RDelayedQueue<Order> delayedQueue = redissonClient.getDelayedQueue(blockingQueue);

            // 从延迟队列中移除订单
            delayedQueue.remove(order);

            log.info("订单已从延迟队列移除: 订单号={}", order.getOrderNo());
        } catch (Exception e) {
            log.error("从延迟队列移除订单失败: 订单号={}", order.getOrderNo(), e);
            // 这里我们只记录日志，不抛出异常，因为订单可能已经被处理
        }
    }
}
