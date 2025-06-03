package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.dto.CreateOrderRequest;
import org.example.dto.OrderResponse;
import org.example.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 订单控制器
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * 创建订单
     *
     * @param request 创建订单请求
     * @return 订单响应
     */
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@RequestBody CreateOrderRequest request) {
        OrderResponse response = orderService.createOrder(request);
        return ResponseEntity.ok(response);
    }

    /**
     * 支付订单
     *
     * @param orderId 订单ID
     * @return 订单响应
     */
    @PostMapping("/{orderId}/pay")
    public ResponseEntity<OrderResponse> payOrder(@PathVariable Long orderId) {
        OrderResponse response = orderService.payOrder(String.valueOf(orderId));
        return ResponseEntity.ok(response);
    }

    /**
     * 取消订单
     *
     * @param orderId 订单ID
     * @param reason  取消原因
     * @return 订单响应
     */
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(
            @PathVariable Long orderId,
            @RequestParam(required = false, defaultValue = "用户主动取消") String reason) {
        OrderResponse response = orderService.cancelOrder(String.valueOf(orderId), reason);
        return ResponseEntity.ok(response);
    }
}
