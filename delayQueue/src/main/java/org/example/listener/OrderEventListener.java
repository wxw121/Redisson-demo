package org.example.listener;

import lombok.extern.slf4j.Slf4j;
import org.example.entity.Order;
import org.example.event.OrderCancelledEvent;
import org.example.event.OrderCreatedEvent;
import org.example.event.OrderPaidEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 订单事件监听器
 */
@Slf4j
@Component
public class OrderEventListener {

    /**
     * 处理订单创建事件
     */
    @Async
    @EventListener
    public void handleOrderCreatedEvent(OrderCreatedEvent event) {
        Order order = event.getOrder();
        log.info("订单创建事件: 订单号={}, 金额={}", order.getOrderNo(), order.getAmount());
        // 这里可以添加订单创建后的业务逻辑，如发送通知等
    }

    /**
     * 处理订单支付事件
     */
    @Async
    @EventListener
    public void handleOrderPaidEvent(OrderPaidEvent event) {
        Order order = event.getOrder();
        log.info("订单支付事件: 订单号={}, 金额={}", order.getOrderNo(), order.getAmount());
        // 这里可以添加订单支付后的业务逻辑，如发货、积分处理等
    }

    /**
     * 处理订单取消事件
     */
    @Async
    @EventListener
    public void handleOrderCancelledEvent(OrderCancelledEvent event) {
        Order order = event.getOrder();
        log.info("订单取消事件: 订单号={}, 取消原因={}", order.getOrderNo(), event.getCancelReason());
        // 这里可以添加订单取消后的业务逻辑，如库存恢复、退款等
    }
}
