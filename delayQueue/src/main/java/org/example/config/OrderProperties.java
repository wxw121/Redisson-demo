package org.example.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 订单配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "order")
public class OrderProperties {

    /**
     * 支付超时时间（分钟）
     */
    private long paymentTimeoutMinutes = 30;

    /**
     * 最小订单金额
     */
    private BigDecimal minimumAmount = new BigDecimal("0.01");

    /**
     * 最大订单金额
     */
    private BigDecimal maximumAmount = new BigDecimal("100000.00");

    /**
     * 订单号前缀
     */
    private String orderNoPrefix = "ORDER_";
}
