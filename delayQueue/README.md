# Delay Queue 子模块

基于Redisson实现的分布式延迟队列服务，专门处理订单超时自动取消等业务场景。

## 核心功能

### 1. 订单生命周期管理
- 订单创建、支付、取消全流程处理
- 订单状态自动流转(PENDING → PAID/CANCELLED)

### 2. 延迟队列实现
- 基于Redisson的RDelayedQueue实现
- 订单超时自动取消机制(默认30分钟)
- 支持动态调整超时时间

### 3. 事件驱动架构
- 订单状态变更事件发布
- 事件监听器处理业务逻辑

## 技术实现

### 主要组件
- **RedissonConfig**: Redisson客户端配置
- **OrderTimeoutListener**: 处理订单超时逻辑
- **OrderServiceImpl**: 订单服务核心实现
- **OrderEventListener**: 处理订单状态变更事件

### 关键实现细节
```java
// 延迟队列初始化
RDelayedQueue<OrderDelayMessage> delayedQueue = 
    redissonClient.getDelayedQueue(orderQueue);

// 添加订单到延迟队列
delayedQueue.offer(new OrderDelayMessage(orderId), 
    orderProperties.getTimeoutMinutes(), TimeUnit.MINUTES);
```

## 配置说明

### 主要配置项
```yaml
order:
  timeout-minutes: 30  # 订单超时时间(分钟)
  
redisson:
  config:
    singleServerConfig:
      address: "redis://localhost:6379"
      database: 0
```

### 自定义配置类
```java
@ConfigurationProperties(prefix = "order")
public class OrderProperties {
    private int timeoutMinutes = 30;
    // getters & setters
}
```

## API接口

### 订单服务接口
| 方法 | 路径 | 描述 |
|------|------|------|
| POST | /api/orders | 创建新订单 |
| PUT  | /api/orders/{id}/pay | 支付订单 |
| PUT  | /api/orders/{id}/cancel | 取消订单 |
| GET  | /api/orders/{id} | 查询订单详情 |



## 扩展建议

1. **多业务场景支持**: 扩展OrderDelayMessage以支持不同类型的延迟任务
2. **监控告警**: 添加延迟队列监控和异常告警
3. **性能优化**: 批量处理延迟任务提高吞吐量
4. **分布式事务**: 集成Seata保证订单操作的原子性

## 常见问题

**Q: 如何调整订单超时时间?**
A: 修改application.yml中的`order.timeout-minutes`配置

**Q: 如何查看延迟队列中的订单?**
A: 通过Redis命令查看`redisson_delay_queue:{queueName}`

## 许可证

[MIT License](../LICENSE)
