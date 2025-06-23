# 实时统计模块 (realtimeStats)

基于Spring Boot和Redisson的实时数据统计模块，提供点赞、浏览等行为的实时统计功能。

## 功能特性

- **实时数据收集**：实时记录用户行为数据
- **多种统计维度**：
  - 内容点赞统计
  - 页面浏览统计
  - 独立访客统计
  - 游戏玩家统计
  - 热搜统计
- **批量同步**：定期批量同步数据到持久化存储
- **缓存管理**：可配置的缓存过期策略

## 技术实现

### 核心组件
- Spring Boot 3.x
- Redisson 3.x
- MyBatis-Plus

### Redis数据结构设计
- **点赞数据**：
  - `like:count:{contentId}` - 点赞总数(AtomicLong)
  - `like:record:{contentId}` - 用户点赞记录(Map)
  
- **浏览数据**：
  - `pv:count:{contentId}` - 浏览总数(AtomicLong)
  - `uv:count:{contentId}` - 独立访客数(HyperLogLog)

## 配置说明

### 主要配置项
```yaml
realtime-stats:
  redis-key-prefix:
    content: "content:"  # 内容相关key前缀
    like: "like:"        # 点赞相关key前缀
    pageView: "pv:"      # 页面浏览相关key前缀
    uniqueVisitor: "uv:" # 独立访客相关key前缀
    gamePlayer: "gp:"    # 游戏玩家相关key前缀
    hotSearch: "hs:"     # 热搜相关key前缀
  
  sync:
    interval: 300000     # 同步间隔时间(毫秒)
    batchSize: 100       # 批量同步大小
```

## 快速开始

### 1. 启动依赖服务
- 确保Redis服务已启动
- 确保MySQL服务已启动

### 2. 修改配置
修改`src/main/resources/application.yml`中的Redis和数据库连接信息。

### 3. 运行项目
```bash
mvn spring-boot:run
```



## API文档

### 1. 点赞相关API
- `POST /api/like/{contentId}` - 点赞内容
- `DELETE /api/like/{contentId}` - 取消点赞
- `GET /api/like/{contentId}/count` - 获取点赞数

### 2. 浏览相关API
- `POST /api/view/{contentId}` - 记录浏览
- `GET /api/view/{contentId}/count` - 获取浏览数

## 使用示例

### 记录用户点赞
```java
@RestController
@RequestMapping("/api/like")
public class LikeController {
    
    @Autowired
    private LikeService likeService;
    
    @PostMapping("/{contentId}")
    public void like(@PathVariable Long contentId, 
                    @RequestParam String userId) {
        likeService.like(contentId, userId);
    }
}
```

### 获取内容点赞数
```java
@GetMapping("/{contentId}/count")
public long getLikeCount(@PathVariable Long contentId) {
    return likeService.getLikeCount(contentId);
}
```

## 贡献指南

欢迎通过Pull Request贡献代码，请遵循以下规范：
1. 保持代码风格一致
2. 添加必要的单元测试
3. 更新相关文档

## 许可证

[MIT License](../LICENSE)
