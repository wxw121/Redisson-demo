package org.example.event;

import org.example.entity.Order;

/**
 * 订单创建事件
 */
public class OrderCreatedEvent extends OrderEvent {
    public OrderCreatedEvent(Object source, Order order) {
        super(source, order);
    }
}
