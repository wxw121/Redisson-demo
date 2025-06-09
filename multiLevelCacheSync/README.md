# MultiLevelCache Sync Framework

一个基于Spring Cache、Redisson和Caffeine实现的高性能多级缓存同步框架，提供了本地缓存和分布式缓存的无缝集成，支持灵活的配置和多种缓存防护机制。

## 多级缓存架构

### 1. 缓存层级设计
```
┌─────────────────┐
│   Application   │
└────────┬────────┘
         │
┌────────┴────────┐
│  Cache Manager  │
└────────┬────────┘
         │
    ┌────┴────┐
┌───┴───┐ ┌───┴───┐
│  L1   │ │  L2   │
│ Cache │ │ Cache │
└───┬───┘ └───┬───┘
    │         │
┌───┴───┐ ┌───┴───┐
│Caffeine│ │Redis  │
└───────┘ └───────┘
```

- **L1 (Caffeine)**
  - 本地内存缓存
  - 超快的访问速度
  - 避免网络开销
  - 自动过期清理

- **L2 (Redis)**
  - 分布式缓存
  - 数据持久化
  - 跨节点共享
  - 原子操作支持

### 2. 缓存操作流程

#### 读取操作
```java
protected Object lookup(Object key) {
    String cacheKey = createCacheKey(key);
    
    // 1. 从本地缓存获取
    Object value = caffeineCache.getIfPresent(cacheKey);
    if (value != null) {
        cacheStats.recordHit("L1");
        return value;
    }

    // 2. 从Redis获取
    RMap<Object, Object> map = redissonClient.getMap(name);
    value = map.get(cacheKey);
    if (value != null) {
        cacheStats.recordHit("L2");
        cacheStats.recordMiss("L1");
        // 回填到本地缓存
        caffeineCache.put(cacheKey, value);
        return value;
    }

    // 3. 缓存未命中
    cacheStats.recordMiss("L1");
    cacheStats.recordMiss("L2");
    return null;
}
```

#### 写入操作
```java
public void put(Object key, Object value) {
    String cacheKey = createCacheKey(key);

    // 1. 写入Redis
    RMap<Object, Object> map = redissonClient.getMap(name);
    map.put(cacheKey, value);
    if (cacheProperties.getTimeToLive() > 0) {
        map.expire(Duration.ofMillis(cacheProperties.getTimeToLive()));
    }
    
    // 2. 写入本地缓存
    caffeineCache.put(cacheKey, value);
    
    // 3. 发布缓存更新事件
    publishEvent(CacheEvent.createPutEvent(
        name, cacheKey, value, nodeId, cacheProperties.getTimeToLive()
    ));
}
```

### 3. 缓存防护机制

#### 缓存击穿防护
```java
public <T> T get(Object key, Callable<T> valueLoader) {
    // 1. 获取分布式锁
    RLock lock = redissonClient.getLock(name + ":lock:" + cacheKey);
    try {
        boolean locked = lock.tryLock(
            cacheProperties.getLockWaitTime(),
            cacheProperties.getLockLeaseTime(),
            TimeUnit.MILLISECONDS
        );

        if (!locked) {
            throw new RuntimeException("Failed to acquire lock");
        }

        // 2. 双重检查
        value = lookup(key);
        if (value != null) {
            return (T) value;
        }

        // 3. 加载数据
        value = valueLoader.call();
        if (value != null) {
            put(key, value);
        }
        return (T) value;
    } finally {
        lock.unlock();
    }
}
```

#### 缓存穿透防护
```java
// 配置空值缓存
cache:
  null-value:
    enabled: true
    timeout: 300  # 空值缓存时间（秒）
```

#### 缓存雪崩防护
```java
// 配置随机过期时间
private Duration getRandomizedTtl(Duration baseTtl) {
    long variance = baseTtl.toMillis() / 10; // 10%的变化范围
    long randomizedDelta = ThreadLocalRandom.current().nextLong(-variance, variance);
    return Duration.ofMillis(baseTtl.toMillis() + randomizedDelta);
}
```

### 4. 缓存统计

#### 统计指标
```java
public class CacheStats {
    private String cacheName;
    private Map<String, CacheStatInfo> levelStats;
    
    public static class CacheStatInfo {
        private long hitCount;
        private long missCount;
        private long loadCount;
        private long totalLoadTime;
        private long evictionCount;
        private long estimatedSize;
    }
}
```

#### 统计方法
```java
public void recordHit(String level) {
    CacheStatInfo stats = levelStats.get(level);
    stats.incrementHitCount();
}

public void recordMiss(String level) {
    CacheStatInfo stats = levelStats.get(level);
    stats.incrementMissCount();
}

public void recordLoad(String level) {
    CacheStatInfo stats = levelStats.get(level);
    stats.incrementLoadCount();
}
```

### 5. 缓存事件

#### 事件类型
```java
public enum CacheEventType {
    PUT,           // 缓存写入
    REMOVE,        // 缓存删除
    CLEAR,         // 缓存清空
    EXPIRE,        // 过期时间更新
    REMOVE_PATTERN // 模式删除
}
```

#### 事件处理
```java
public void handleCacheEvent(CacheEvent event) {
    // 忽略自己发出的事件
    if (nodeId.equals(event.getSourceNodeId())) {
        return;
    }

    switch (event.getEventType()) {
        case PUT -> caffeineCache.put(event.getKey(), event.getValue());
        case REMOVE -> caffeineCache.invalidate(event.getKey());
        case CLEAR -> caffeineCache.invalidateAll();
        case REMOVE_PATTERN -> {
            String pattern = event.getKey().toString();
            caffeineCache.asMap().keySet().stream()
                .filter(k -> k.toString().matches(pattern))
                .forEach(caffeineCache::invalidate);
        }
    }
}
```

## 配置示例

### 1. 基础配置
```yaml
cache:
  enabled: true
  node-id: ${HOSTNAME}
  
  # L1缓存配置
  local:
    initial-capacity: 100
    maximum-size: 10000
    expire-after-write: 300
    expire-after-access: 300
    
  # L2缓存配置
  redis:
    host: localhost
    port: 6379
    database: 0
    time-to-live: 1800
```

### 2. 高级配置
```yaml
cache:
  # 防护配置
  protection:
    lock-wait-time: 3000
    lock-lease-time: 30000
    null-value-enabled: true
    null-value-timeout: 300
    
  # 统计配置
  stats:
    enabled: true
    log-interval: 300
    
  # 事件配置
  event:
    enabled: true
    topic: "cache:events"
    batch-size: 100
```

## 使用示例

### 1. 基础使用
```java
@Service
public class UserService {
    
    @Cacheable(value = "userCache", key = "#id")
    public User getUser(Long id) {
        return userRepository.findById(id);
    }
    
    @CachePut(value = "userCache", key = "#user.id")
    public User updateUser(User user) {
        return userRepository.save(user);
    }
    
    @CacheEvict(value = "userCache", key = "#id")
    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }
}
```

### 2. 高级使用
```java
@Service
public class UserService {
    
    @Autowired
    private MultiLevelCache userCache;
    
    public List<User> getUsersByDepartment(String department) {
        // 使用模式删除
        userCache.evictByPattern("user:dept:" + department + ":*");
        
        // 批量更新
        List<User> users = userRepository.findByDepartment(department);
        users.forEach(user -> 
            userCache.put("user:dept:" + department + ":" + user.getId(), user)
        );
        
        return users;
    }
    
    // 自定义过期时间
    public void updateUserWithCustomTtl(User user, long ttl) {
        userCache.put(user.getId(), user);
        userCache.expire(user.getId(), ttl);
    }
}
```

## 性能优化

### 1. 本地缓存优化
```java
// 配置Caffeine
Caffeine.newBuilder()
    .initialCapacity(100)
    .maximumSize(10000)
    .expireAfterWrite(Duration.ofMinutes(5))
    .expireAfterAccess(Duration.ofMinutes(5))
    .recordStats()
```

### 2. Redis优化
```java
// 使用批量操作
public void batchUpdate(Map<String, Object> updates) {
    RBatch batch = redissonClient.createBatch();
    updates.forEach((key, value) -> {
        batch.getMap(name).fastPutAsync(key, value);
    });
    batch.execute();
}
```

### 3. 序列化优化
```java
// 使用高效的序列化方式
@Configuration
public class RedisConfig {
    @Bean
    public RedisTemplate<String, Object> redisTemplate() {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        return template;
    }
}
```

## 监控与维护

### 1. 性能监控
```java
@Scheduled(fixedRate = 60000)
public void monitorCachePerformance() {
    CacheStats stats = cache.getCacheStats();
    
    // 检查命中率
    if (stats.getHitRate() < 0.8) {
        log.warn("Low cache hit rate: {}", stats.getHitRate());
    }
    
    // 检查加载时间
    if (stats.getAverageLoadPenalty() > 50) {
        log.warn("High cache load penalty: {}ms", 
            stats.getAverageLoadPenalty());
    }
}
```

### 2. 缓存维护
```java
@Scheduled(cron = "0 0 * * * *")
public void maintainCache() {
    // 清理过期统计数据
    cacheStats.cleanup();
    
    // 压缩本地缓存
    caffeineCache.cleanUp();
    
    // 检查Redis连接
    checkRedisConnection();
}
```

## 版本历史

### v1.0.0
- 初始版本
- 多级缓存支持
- 基本的同步机制

### v1.1.0 (计划中)
- 增强监控功能
- 优化同步性能
- 支持更多缓存策略
## 许可证

[MIT License](../LICENSE)