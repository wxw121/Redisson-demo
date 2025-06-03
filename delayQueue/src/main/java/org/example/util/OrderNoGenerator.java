package org.example.util;

import lombok.RequiredArgsConstructor;
import org.example.config.OrderProperties;
import org.example.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 订单号生成器
 */
@Component
@RequiredArgsConstructor
public class OrderNoGenerator {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final AtomicInteger SEQUENCE = new AtomicInteger(0);
    private static final int MAX_SEQUENCE = 9999;

    private final OrderProperties orderProperties;

    /**
     * 生成订单号
     * 格式：年月日时分秒 + 4位序列号
     * 示例：20240315120000 + 0001 = 202403151200000001
     *
     * @return 订单号
     */
    public String generate() {
        String timestamp = LocalDateTime.now().format(DATE_TIME_FORMATTER);

        int sequence = SEQUENCE.incrementAndGet();
        if (sequence > MAX_SEQUENCE) {
            synchronized (SEQUENCE) {
                if (SEQUENCE.get() > MAX_SEQUENCE) {
                    SEQUENCE.set(0);
                }
                sequence = SEQUENCE.incrementAndGet();
            }
        }

        return String.format("%s%s%04d", 
            orderProperties.getOrderNoPrefix(), timestamp, sequence);
    }

    /**
     * 解析订单号中的创建时间
     *
     * @param orderNo 订单号
     * @return 订单创建时间
     */
    public LocalDateTime parseCreateTime(String orderNo) {
        if (orderNo == null || orderNo.length() < orderProperties.getOrderNoPrefix().length() + 14) {
            throw new BusinessException("Invalid order number format");
        }
        // 去除前缀
        String noPrefix = orderNo.substring(orderProperties.getOrderNoPrefix().length());
        String dateTimeStr = noPrefix.substring(0, 14);
        return LocalDateTime.parse(dateTimeStr, DATE_TIME_FORMATTER);
    }
}
