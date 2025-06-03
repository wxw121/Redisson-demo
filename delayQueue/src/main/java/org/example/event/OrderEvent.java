package org.example.event;

import lombok.Getter;
import org.example.entity.Order;
import org.springframework.context.ApplicationEvent;

/**
 * 订单事件基类
 */
@Getter
public abstract class OrderEvent extends ApplicationEvent {
    private final Order order;

    public OrderEvent(Object source, Order order) {
        super(source);
        this.order = order;
    }
}
