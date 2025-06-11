package org.example.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.Order;
import org.example.enums.OrderStatus;
import org.example.service.OrderService;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 订单超时监听器
 * 使用Redisson的延迟队列功能，监听订单超时事件
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutListener implements InitializingBean, ApplicationListener<ContextClosedEvent> {

    private final RedissonClient redissonClient;
    private final OrderService orderService;
    private static final String ORDER_TIMEOUT_QUEUE = "order:timeout:queue";


    private volatile boolean running = true;
    private Thread listenerThread;

    @Override
    public void afterPropertiesSet() {
        // 在Bean初始化完成后启动监听线程
        new Thread(this::startListener, "OrderTimeoutListener").start();
    }

    /**
     * 启动监听器
     */
    private void startListener() {
        RBlockingQueue<Order> blockingQueue = redissonClient.getBlockingQueue(ORDER_TIMEOUT_QUEUE);

        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                // 从队列中获取超时订单
                Order order = blockingQueue.poll(1, TimeUnit.SECONDS);
                if (order != null) {
                    processTimeoutOrder(order);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("订单超时监听器正常停止");
                break;
            } catch (Exception e) {
                if (redissonClient.isShutdown()) {
                    log.info("Redisson客户端已关闭，停止监听器");
                    break;
                }
                log.error("处理超时订单时发生错误", e);
            }
        }
    }

    // 实现spring的关闭事件监听
    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        running = false;
        if (listenerThread != null) {
            listenerThread.interrupt();  // 中断阻塞的poll操作
            try {
                listenerThread.join(2000); // 等待主线程结束。避免僵死线程
                log.info("订单超时监听器正常停止");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 处理超时订单
     */
    private void processTimeoutOrder(Order order) {
        try {
            // 检查订单当前状态，只有未支付的订单才能取消
            if (OrderStatus.UNPAID.equals(order.getStatus())) {
                log.info("订单超时自动取消: 订单号={}", order.getOrderNo());
                orderService.cancelOrder(order.getOrderNo(), "订单超时自动取消");
            }
        } catch (Exception e) {
            log.error("处理超时订单失败: 订单号={}", order.getOrderNo(), e);
        }
    }
}
