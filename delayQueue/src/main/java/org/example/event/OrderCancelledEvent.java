package org.example.event;

import lombok.Getter;
import org.example.entity.Order;

/**
 * 订单取消事件
 */
@Getter
public class OrderCancelledEvent extends OrderEvent {
    private final String cancelReason;

    public OrderCancelledEvent(Object source, Order order, String cancelReason) {
        super(source, order);
        this.cancelReason = cancelReason;
    }
}
