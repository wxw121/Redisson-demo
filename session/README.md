# Spring Session Redis 示例

这个示例展示了如何使用 Spring Session 和 Redis 实现分布式会话管理。

## 功能特性

- 分布式会话存储
- 会话并发控制
- 会话操作审计
- 自动会话清理
- RESTful API 接口

## 配置说明

### 核心配置

```yaml
spring:
  session:
    store-type: redis
    redis:
      namespace: spring:session     # Redis key前缀
      repository-type: indexed      # 使用indexed类型支持cron清理
      flush-mode: on_save          # 会话刷新模式
      cleanup-cron: "0 * * * * *"  # 每分钟清理过期会话
```

### 重要配置参数说明

1. **repository-type**
   - `indexed`: 支持基于索引的会话查询和cron清理
   - `default`: 基本的会话存储功能

2. **flush-mode**
   - `on_save`: 在会话数据变更时立即写入Redis
   - `immediate`: 立即刷新所有更改

3. **cleanup-cron**
   - 配置过期会话的清理计划
   - 仅在 repository-type=indexed 时支持
   - 使用标准cron表达式

4. **namespace**
   - Redis中会话数据的key前缀
   - 用于隔离不同应用的会话数据

### 会话超时配置

```yaml
server:
  servlet:
    session:
      timeout: 1800  # 会话超时时间，单位秒
```

## 会话审计

系统使用 `@SessionAudit` 注解实现会话操作审计：

```java
@SessionAudit(
    type = "VIEW_USER_SESSIONS",
    description = "查看用户活跃会话列表",
    logParams = true,
    logResult = true
)
```

### 审计注解参数

- `type`: 操作类型
- `description`: 操作描述
- `logParams`: 是否记录请求参数
- `logResult`: 是否记录响应结果
- `logException`: 是否记录异常信息

## API 接口

### 会话管理接口

1. 查看用户会话列表
```http
GET /api/sessions/users/{username}
```

2. 使指定用户的所有会话失效
```http
DELETE /api/sessions/users/{username}
```

3. 使指定会话失效
```http
DELETE /api/sessions/sessions/{sessionId}
```

### 安全审计接口

1. 获取会话操作审计日志
```http
GET /api/sessions/audit-logs
```

## 最佳实践

1. **会话清理**
   - 建议使用 cron 任务定期清理过期会话
   - 确保 repository-type 设置为 indexed

2. **并发控制**
   - 根据实际需求配置最大并发会话数
   - 可以针对不同用户角色设置不同的限制

3. **安全审计**
   - 在关键操作上添加 @SessionAudit 注解
   - 定期检查审计日志，及时发现异常

4. **性能优化**
   - 合理配置 Redis 连接池参数
   - 适当设置会话超时时间
   - 避免存储过大的会话数据

## 注意事项

1. 确保 Redis 服务可用且配置正确
2. 合理配置会话超时时间，避免会话占用过多资源
3. 定期监控 Redis 内存使用情况
4. 在分布式环境中确保所有节点时间同步
## 许可证

[MIT License](../LICENSE)