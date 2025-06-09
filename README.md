# Redisson Demo Project

基于Spring Boot 3.x和Redisson的分布式应用示例项目，展示Redis在分布式场景下的实际应用。

## 项目概述

本项目展示了Redisson在分布式环境中的多种应用场景，包括：
- 分布式延迟队列（订单超时处理）
- 分布式Session管理
- 分布式锁应用

## 项目结构

```
redisson-demo/
├── delayQueue/          # 分布式延迟队列模块
├── session/             # 分布式Session管理模块
├── multiLevelCacheSync/ # 多级缓存同步模块
├── pom.xml             # 父项目POM文件
└── README.md           # 项目说明文档
```

## 子模块说明

### 1. delayQueue
基于Redisson的RDelayedQueue实现的分布式延迟队列，主要用于处理订单超时自动取消等场景。
- [查看delayQueue模块详细说明](delayQueue/README.md)

### 2. session
基于Spring Session和Redis的分布式会话管理实现，用于多节点间的session共享。
- [查看session模块详细说明](session/README.md)

### 3. multiLevelCacheSync
基于Redisson的多级缓存同步实现，支持本地缓存与Redis的数据同步，提供高性能的缓存访问方案。
- 支持本地缓存与Redis的双向同步
- 实现缓存一致性保证
- 提供批量操作支持
- [查看multiLevelCacheSync模块详细说明](multiLevelCacheSync/README.md)

## 技术栈

### 核心框架
- Spring Boot 3.2.3
- Spring Session 3.1.1
- Redisson 3.31.0

### 数据存储
- MySQL 8.0.33
- Redis (通过Redisson客户端)
- MyBatis-Plus 3.5.7

### 开发工具
- MapStruct 1.5.5.Final
- Lombok 1.18.30
- Maven 3.x

## 环境要求

- JDK 17+
- Maven 3.6+
- MySQL 8.0+
- Redis 6.0+

## 快速开始

### 1. 克隆项目
```bash
git clone https://github.com/your-username/redisson-demo.git
cd redisson-demo
```

### 2. 环境准备
- 确保MySQL服务已启动
- 确保Redis服务已启动
- 创建所需数据库

### 3. 配置修改
修改各模块的`application.yml`文件，配置数据库和Redis连接信息：
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/your_database
    username: your_username
    password: your_password
  data:
    redis:
      host: localhost
      port: 6379
```

### 4. 构建项目
```bash
mvn clean install
```

### 5. 运行模块
```bash
# 运行延迟队列模块
cd delayQueue
mvn spring-boot:run

# 运行session模块
cd ../session
mvn spring-boot:run
```


## 问题排查

### 常见问题
1. Redis连接失败
   - 检查Redis服务是否正常运行
   - 验证Redis连接配置是否正确

2. 数据库连接问题
   - 确认MySQL服务状态
   - 检查数据库用户权限

## 贡献指南

1. Fork 项目
2. 创建特性分支
3. 提交变更
4. 推送到分支
5. 创建Pull Request

## 许可证

本项目采用 [MIT License](LICENSE) 许可证。

## 联系方式

如有问题或建议，请提交 [Issue](https://github.com/your-username/redisson-demo/issues)。
