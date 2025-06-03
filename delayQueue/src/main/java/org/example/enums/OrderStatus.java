package org.example.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 订单状态枚举
 */
public enum OrderStatus implements IEnum<Integer> {

    CREATED(0, "已创建"),


    PAID(1, "已支付"),


    CANCELLED(2, "已取消"),


    COMPLETED(3, "已完成"),


    REFUNDED(4, "已退款"),


    UNPAID(5, "未支付"),


    PENDING(6, "待支付");

    private final int value;
    private final String description;

    OrderStatus(int value, String description) {
        this.value = value;
        this.description = description;
    }

    @Override
    public Integer getValue() {
        return this.value;
    }

    public String getDescription() {
        return this.description;
    }
}
