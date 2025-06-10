package org.example.util;

import org.example.dto.CreateOrderRequest;
import org.example.dto.OrderResponse;
import org.example.entity.Order;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * 订单对象转换器
 */
@Mapper(componentModel = "spring")
public interface OrderConverter {
    OrderConverter INSTANCE = Mappers.getMapper(OrderConverter.class);

    /**
     * 将创建订单请求转换为订单实体
     *
     * @param request 创建订单请求
     * @return 订单实体
     */
    Order toEntity(CreateOrderRequest request);

    /**
     * 将订单实体转换为订单响应
     *
     * @param order 订单实体
     * @return 订单响应
     */
    OrderResponse toResponse(Order order);
}
