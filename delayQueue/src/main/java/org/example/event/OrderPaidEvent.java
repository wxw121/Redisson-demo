package org.example.event;

import org.example.entity.Order;

/**
 * 订单支付事件
 */
public class OrderPaidEvent extends OrderEvent {
    public OrderPaidEvent(Object source, Order order) {
        super(source, order);
    }
}
