package org.example.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.config.OrderProperties;
import org.example.dto.CreateOrderRequest;
import org.example.dto.OrderResponse;
import org.example.entity.Order;
import org.example.enums.OrderStatus;
import org.example.event.OrderCancelledEvent;
import org.example.event.OrderCreatedEvent;
import org.example.event.OrderPaidEvent;
import org.example.exception.BusinessException;
import org.example.mapper.OrderMapper;
import org.example.service.DelayQueueService;
import org.example.service.OrderService;
import org.example.util.OrderConverter;
import org.example.util.OrderNoGenerator;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 订单服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final OrderNoGenerator orderNoGenerator;
    private final OrderConverter orderConverter;
    private final DelayQueueService delayQueueService;
    private final ApplicationEventPublisher eventPublisher;
    private final RedissonClient redissonClient;
    private final OrderProperties orderProperties;

    @Override
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        // 验证订单金额
        if (request.getAmount().compareTo(orderProperties.getMinimumAmount()) < 0 ||
            request.getAmount().compareTo(orderProperties.getMaximumAmount()) > 0) {
            throw new BusinessException(String.format("订单金额必须在 %s 到 %s 之间",
                orderProperties.getMinimumAmount(), orderProperties.getMaximumAmount()));
        }

        // 生成订单号
        String orderNo = orderNoGenerator.generate();

        // 创建订单
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(request.getUserId());
        order.setProductId(request.getProductId());
        order.setProductName(request.getProductName());
        order.setAmount(request.getAmount());
        order.setStatus(OrderStatus.PENDING);

        // 保存订单
        orderMapper.insert(order);
        log.info("订单创建成功: {}", order);

        // 添加到延迟队列，使用配置的超时时间
        delayQueueService.addOrderToDelayQueue(order, orderProperties.getPaymentTimeoutMinutes());

        // 发布订单创建事件
        eventPublisher.publishEvent(new OrderCreatedEvent(this, order));

        // 返回订单响应
        return orderConverter.toResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse payOrder(String orderNo) {
        // 创建分布式锁
        RLock lock = redissonClient.getLock("order:lock:" + orderNo);
        try {
            // 尝试获取锁，等待5秒，10秒后自动释放
            boolean locked = lock.tryLock(5, 10, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException("操作频繁，请稍后重试");
            }
            
            // 获取订单
            Order order = getOrderByOrderNo(orderNo);

            // 检查订单状态
            if (!OrderStatus.UNPAID.equals(order.getStatus())) {
                throw new BusinessException("订单状态不正确，无法支付");
            }

            // 更新订单状态
            order.setStatus(OrderStatus.PAID);
            order.setPayTime(LocalDateTime.now());
            orderMapper.updateById(order);

            // 从延迟队列中移除订单
            delayQueueService.removeOrderFromDelayQueue(order);

            // 发布订单支付事件
            eventPublisher.publishEvent(new OrderPaidEvent(this, order));

            log.info("订单支付成功: {}", order);

            // 返回订单响应
            return orderConverter.toResponse(order);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("支付操作被中断");
        } finally {
            // 释放锁
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(String orderNo, String reason) {
        // 创建分布式锁
        RLock lock = redissonClient.getLock("order:lock:" + orderNo);
        try {
            // 尝试获取锁，等待5秒，10秒后自动释放
            boolean locked = lock.tryLock(5, 10, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException("操作频繁，请稍后重试");
            }

            // 获取订单
            Order order = getOrderByOrderNo(orderNo);

            // 检查订单状态，只有未支付的订单才能取消
            if (!OrderStatus.UNPAID.equals(order.getStatus())) {
                throw new BusinessException("订单状态不正确，无法取消");
            }

            // 更新订单状态
            order.setStatus(OrderStatus.CANCELLED);
            order.setCancelTime(LocalDateTime.now());
            order.setCancelReason(reason);
            orderMapper.updateById(order);

            // 从延迟队列中移除订单
            delayQueueService.removeOrderFromDelayQueue(order);

            // 发布订单取消事件
            eventPublisher.publishEvent(new OrderCancelledEvent(this, order, reason));

            log.info("订单取消成功: {}, 原因: {}", order, reason);

            // 返回订单响应
            return orderConverter.toResponse(order);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("取消订单操作被中断");
        } finally {
            // 释放锁
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public Order getOrderByOrderNo(String orderNo) {
        Order order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            throw new BusinessException("订单不存在: " + orderNo);
        }
        return order;
    }
}
