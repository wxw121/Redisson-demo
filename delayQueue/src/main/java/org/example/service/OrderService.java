package org.example.service;

import org.example.dto.CreateOrderRequest;
import org.example.dto.OrderResponse;
import org.example.entity.Order;

/**
 * 订单服务接口
 */
public interface OrderService {

    /**
     * 创建订单
     *
     * @param request 创建订单请求
     * @return 订单响应
     */
    OrderResponse createOrder(CreateOrderRequest request);

    /**
     * 支付订单
     *
     * @param orderNo 订单号
     * @return 订单响应
     */
    OrderResponse payOrder(String orderNo);

    /**
     * 取消订单
     *
     * @param orderNo 订单号
     * @param reason 取消原因
     * @return 订单响应
     */
    OrderResponse cancelOrder(String orderNo, String reason);

    /**
     * 根据订单号获取订单
     *
     * @param orderNo 订单号
     * @return 订单信息
     */
    Order getOrderByOrderNo(String orderNo);
}
