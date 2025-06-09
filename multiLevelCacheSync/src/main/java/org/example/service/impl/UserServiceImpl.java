package org.example.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.example.entity.User;
import org.example.exception.BusinessException;
import org.example.mapper.UserMapper;
import org.example.service.UserService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 用户服务实现类
 * 使用多级缓存
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    /**
     * 根据ID获取用户
     * 使用多级缓存，key为用户ID
     */
    @Cacheable(cacheNames = "users", key = "#id", unless = "#result == null")
    @Override
    public User getUser(Long id) {
        Assert.notNull(id, "用户ID不能为空");
        log.info("从数据库查询用户, id: {}", id);
        return getById(id);
    }

    /**
     * 获取所有用户
     * 使用多级缓存，key为固定值"all"
     * 注意：当数据量大时不建议使用此方法，应使用分页查询
     */
    @Cacheable(cacheNames = "users", key = "'all'")
    @Override
    public List<User> getAllUsers() {
        log.info("从数据库查询所有用户");
        return list();
    }

    /**
     * 分页查询用户
     * 不使用缓存，因为分页数据经常变动
     *
     * @param page     页码
     * @param pageSize 每页大小
     * @param username 用户名（可选，用于模糊查询）
     * @param email    邮箱（可选，用于模糊查询）
     * @return 分页结果
     */
    @Override
    public IPage<User> getUsersByPage(int page, int pageSize, String username, String email) {
        Assert.isTrue(page >= 1, "页码必须大于等于1");
        Assert.isTrue(pageSize >= 1 && pageSize <= 100, "每页大小必须在1-100之间");
        
        log.info("分页查询用户, page: {}, pageSize: {}, username: {}, email: {}", 
                page, pageSize, username, email);
        
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(username)) {
            queryWrapper.like(User::getUsername, username);
        }
        if (StringUtils.isNotBlank(email)) {
            queryWrapper.like(User::getEmail, email);
        }
        queryWrapper.orderByDesc(User::getCreateTime);
        
        return page(new Page<>(page, pageSize), queryWrapper);
    }

    /**
     * 根据用户名模糊查询用户
     * 使用多级缓存，key为用户名
     */
    @Cacheable(cacheNames = "users", key = "'username:' + #username")
    @Override
    public List<User> getUsersByUsernameLike(String username) {
        if (StringUtils.isBlank(username)) {
            return Collections.emptyList();
        }
        log.info("从数据库根据用户名模糊查询用户: {}", username);
        return baseMapper.selectByUsernameLike(username);
    }

    /**
     * 清除指定用户的缓存
     * 包括ID、用户名和邮箱相关的缓存
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @Caching(evict = {
            @CacheEvict(cacheNames = "users", key = "#id"),
            @CacheEvict(cacheNames = "users", key = "'all'")
    })
    public void clearCache(Long id) {
        Assert.notNull(id, "用户ID不能为空");
        log.info("清除用户缓存, id: {}", id);
        
        // 获取用户信息，用于清除用户名和邮箱相关的缓存
        User user = getById(id);
        if (user != null) {
            // 清除用户名和邮箱相关缓存
            cacheEvict("users", "'username:' + '" + user.getUsername() + "'");
            cacheEvict("users", "'email:' + '" + user.getEmail() + "'");
        }
    }

    /**
     * 清除所有用户相关的缓存
     */
    @Override
    @CacheEvict(cacheNames = "users", allEntries = true)
    public void clearAllCache() {
        log.info("清除所有用户缓存");
    }

    /**
     * 获取用户统计信息
     * 包括总用户数、活跃用户数、今日新增用户数等
     */
    @Override
    public Map<String, Object> getStats() {
        log.info("获取用户统计信息");
        
        // 总用户数
        int totalUsers = countUsers();
        
        // 活跃用户数（状态为1的用户）
        LambdaQueryWrapper<User> activeWrapper = new LambdaQueryWrapper<>();
        activeWrapper.eq(User::getStatus, 1);
        int activeUsers = (int) count(activeWrapper);
        
        // 今日新增用户数
        LocalDateTime today = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime tomorrow = today.plusDays(1);
        LambdaQueryWrapper<User> todayWrapper = new LambdaQueryWrapper<>();
        todayWrapper.between(User::getCreateTime, today, tomorrow);
        int newUsersToday = (int) count(todayWrapper);
        
        // 本周新增用户数
        LocalDateTime weekStart = today.minusDays(today.getDayOfWeek().getValue() - 1);
        LambdaQueryWrapper<User> weekWrapper = new LambdaQueryWrapper<>();
        weekWrapper.between(User::getCreateTime, weekStart, tomorrow);
        int newUsersThisWeek = (int) count(weekWrapper);
        
        return Map.of(
            "totalUsers", totalUsers,
            "activeUsers", activeUsers,
            "newUsersToday", newUsersToday,
            "newUsersThisWeek", newUsersThisWeek
        );
    }

    /**
     * 批量保存用户
     * 此方法是saveAll接口的实现，内部调用batchCreateUsers
     */
    @Override
    public List<User> saveAll(List<User> users) {
        return batchCreateUsers(users);
    }

    /**
     * 检查用户是否存在
     */
    @Override
    public boolean exists(Long id) {
        Assert.notNull(id, "用户ID不能为空");
        log.info("检查用户是否存在, id: {}", id);
        return getById(id) != null;
    }

    /**
     * 分页查询用户
     * 此方法是findByPage接口的实现，内部调用page方法
     */
    @Override
    public List<User> findByPage(int page, int size) {
        Assert.isTrue(page >= 1, "页码必须大于等于1");
        Assert.isTrue(size >= 1 && size <= 100, "每页大小必须在1-100之间");
        
        log.info("分页查询用户, page: {}, size: {}", page, size);
        
        Page<User> pageResult = page(new Page<>(page, size), new LambdaQueryWrapper<User>()
                .orderByDesc(User::getCreateTime));
        
        return pageResult.getRecords();
    }

    /**
     * 更新用户状态
     * 清除相关缓存
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @Caching(
        put = {
            @CachePut(cacheNames = "users", key = "#result.id")
        },
        evict = {
            @CacheEvict(cacheNames = "users", key = "'all'"),
            @CacheEvict(cacheNames = "users", key = "'username:' + @userMapper.selectById(#id).username"),
            @CacheEvict(cacheNames = "users", key = "'email:' + @userMapper.selectById(#id).email")
        }
    )
    public User updateStatus(Long id, boolean enabled) {
        Assert.notNull(id, "用户ID不能为空");
        log.info("更新用户状态, id: {}, enabled: {}", id, enabled);
        
        // 获取用户信息
        User user = getById(id);
        if (user == null) {
            throw new BusinessException("用户不存在: " + id);
        }
        
        // 更新状态
        user.setStatus(enabled ? 1 : 0);
        user.setUpdateTime(LocalDateTime.now());
        updateById(user);
        
        return user;
    }

    /**
     * 预加载缓存
     * 将常用的用户数据预先加载到缓存中
     */
    @Override
    public void preloadCache(int limit) {
        Assert.isTrue(limit > 0 && limit <= 1000, "预加载数量必须在1-1000之间");
        log.info("预加载用户缓存, limit: {}", limit);
        
        // 预加载用户总数
        countUsers();
        
        // 预加载最近创建的用户
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByDesc(User::getCreateTime)
                   .last("LIMIT " + limit);
        List<User> recentUsers = list(queryWrapper);
        
        // 将用户逐个加入缓存
        for (User user : recentUsers) {
            // 通过调用getUser方法将用户加入缓存
            getUser(user.getId());
        }
        
        log.info("预加载用户缓存完成, 已加载 {} 个用户", recentUsers.size());
    }

    /**
     * 根据邮箱查询用户
     * 使用多级缓存，key为邮箱
     */
    @Cacheable(cacheNames = "users", key = "'email:' + #email", unless = "#result == null")
    @Override
    public User getUserByEmail(String email) {
        Assert.hasText(email, "邮箱不能为空");
        log.info("从数据库查询用户, email: {}", email);
        
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getEmail, email);
        return getOne(queryWrapper);
    }

    /**
     * 创建用户
     * 更新相关缓存
     */
    @Transactional(rollbackFor = Exception.class)
    @Caching(
            put = {
                    @CachePut(cacheNames = "users", key = "#result.id")
            },
            evict = {
                    @CacheEvict(cacheNames = "users", key = "'all'"),
                    @CacheEvict(cacheNames = "users", key = "'username:' + #user.username"),
                    @CacheEvict(cacheNames = "users", key = "'email:' + #user.email"),
                    @CacheEvict(cacheNames = "users", key = "'count'")
            }
    )
    @Override
    public User createUser(User user) {
        // 参数校验
        Assert.notNull(user, "用户对象不能为空");
        Assert.hasText(user.getUsername(), "用户名不能为空");
        Assert.hasText(user.getEmail(), "邮箱不能为空");
        
        // 检查用户名是否已存在
        LambdaQueryWrapper<User> usernameWrapper = new LambdaQueryWrapper<>();
        usernameWrapper.eq(User::getUsername, user.getUsername());
        if (count(usernameWrapper) > 0) {
            throw new BusinessException("用户名已存在");
        }
        
        // 检查邮箱是否已存在
        LambdaQueryWrapper<User> emailWrapper = new LambdaQueryWrapper<>();
        emailWrapper.eq(User::getEmail, user.getEmail());
        if (count(emailWrapper) > 0) {
            throw new BusinessException("邮箱已存在");
        }
        
        log.info("创建用户: {}", user);
        
        // 设置时间
        LocalDateTime now = LocalDateTime.now();
        user.setCreateTime(now);
        user.setUpdateTime(now);
        
        // 保存用户
        save(user);
        return user;
    }

    /**
     * 更新用户
     * 更新相关缓存
     */
    @Transactional(rollbackFor = Exception.class)
    @Caching(
            put = {
                    @CachePut(cacheNames = "users", key = "#user.id")
            },
            evict = {
                    @CacheEvict(cacheNames = "users", key = "'all'"),
                    @CacheEvict(cacheNames = "users", key = "'username:' + #user.username"),
                    @CacheEvict(cacheNames = "users", key = "'email:' + #user.email")
            }
    )
    @Override
    public User updateUser(User user) {
        // 参数校验
        Assert.notNull(user, "用户对象不能为空");
        Assert.notNull(user.getId(), "用户ID不能为空");
        Assert.hasText(user.getUsername(), "用户名不能为空");
        Assert.hasText(user.getEmail(), "邮箱不能为空");
        
        log.info("更新用户: {}", user);
        
        // 获取原用户信息
        User oldUser = getById(user.getId());
        if (oldUser == null) {
            throw new BusinessException("用户不存在: " + user.getId());
        }
        
        // 检查用户名是否被其他用户使用
        if (!oldUser.getUsername().equals(user.getUsername())) {
            LambdaQueryWrapper<User> usernameWrapper = new LambdaQueryWrapper<>();
            usernameWrapper.eq(User::getUsername, user.getUsername())
                         .ne(User::getId, user.getId());
            if (count(usernameWrapper) > 0) {
                throw new BusinessException("用户名已被其他用户使用");
            }
            // 清除原用户名的缓存
            cacheEvict("users", "'username:' + " + oldUser.getUsername());
        }
        
        // 检查邮箱是否被其他用户使用
        if (!oldUser.getEmail().equals(user.getEmail())) {
            LambdaQueryWrapper<User> emailWrapper = new LambdaQueryWrapper<>();
            emailWrapper.eq(User::getEmail, user.getEmail())
                       .ne(User::getId, user.getId());
            if (count(emailWrapper) > 0) {
                throw new BusinessException("邮箱已被其他用户使用");
            }
            // 清除原邮箱的缓存
            cacheEvict("users", "'email:' + " + oldUser.getEmail());
        }
        
        // 设置更新时间
        user.setUpdateTime(LocalDateTime.now());
        // 保持创建时间不变
        user.setCreateTime(oldUser.getCreateTime());
        
        // 更新用户
        updateById(user);
        
        return user;
    }

    /**
     * 删除用户
     * 清除相关缓存
     */
    @Transactional(rollbackFor = Exception.class)
    @Caching(evict = {
            @CacheEvict(cacheNames = "users", key = "#id"),
            @CacheEvict(cacheNames = "users", key = "'all'"),
            @CacheEvict(cacheNames = "users", key = "'count'")
    })
    @Override
    public boolean deleteUser(Long id) {
        Assert.notNull(id, "用户ID不能为空");
        log.info("删除用户: {}", id);
        
        // 获取用户信息（用于清除用户名和邮箱相关缓存）
        User user = getById(id);
        if (user == null) {
            throw new BusinessException("用户不存在: " + id);
        }
        
        // 清除用户名和邮箱相关缓存
        cacheEvict("users", "'username:' + '" + user.getUsername() + "'");
        cacheEvict("users", "'email:' + '" + user.getEmail() + "'");
        
        return removeById(id);
    }

    /**
     * 批量创建用户
     * 清除所有相关缓存
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = "users", allEntries = true)
    @Override
    public List<User> batchCreateUsers(List<User> users) {
        Assert.notEmpty(users, "用户列表不能为空");
        if (users.size() > 100) {
            throw new BusinessException("批量创建用户数量不能超过100");
        }
        
        // 参数校验
        for (User user : users) {
            Assert.hasText(user.getUsername(), "用户名不能为空");
            Assert.hasText(user.getEmail(), "邮箱不能为空");
        }
        
        // 检查用户名和邮箱是否重复
        List<String> usernames = users.stream().map(User::getUsername).toList();
        List<String> emails = users.stream().map(User::getEmail).toList();
        
        // 检查系统中是否已存在相同的用户名或邮箱
        if (!usernames.isEmpty()) {
            LambdaQueryWrapper<User> usernameWrapper = new LambdaQueryWrapper<>();
            usernameWrapper.in(User::getUsername, usernames);
            if (count(usernameWrapper) > 0) {
                throw new BusinessException("存在重复的用户名");
            }
        }
        
        if (!emails.isEmpty()) {
            LambdaQueryWrapper<User> emailWrapper = new LambdaQueryWrapper<>();
            emailWrapper.in(User::getEmail, emails);
            if (count(emailWrapper) > 0) {
                throw new BusinessException("存在重复的邮箱");
            }
        }
        
        // 设置创建时间和更新时间
        LocalDateTime now = LocalDateTime.now();
        users.forEach(user -> {
            user.setCreateTime(now);
            user.setUpdateTime(now);
        });
        
        log.info("批量创建用户, 数量: {}", users.size());
        saveBatch(users);
        
        return users;
    }

    /**
     * 批量删除用户
     * 清除所有相关缓存
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = "users", allEntries = true)
    @Override
    public boolean batchDeleteUsers(List<Long> ids) {
        Assert.notEmpty(ids, "用户ID列表不能为空");
        if (ids.size() > 100) {
            throw new BusinessException("批量删除用户数量不能超过100");
        }
        
        // 检查所有用户是否存在
        List<User> existingUsers = listByIds(ids);
        if (existingUsers.size() != ids.size()) {
            throw new BusinessException("部分用户不存在");
        }
        
        log.info("批量删除用户, ID列表: {}", ids);
        return removeByIds(ids);
    }

    /**
     * 批量更新用户
     * 清除所有相关缓存
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = "users", allEntries = true)
    @Override
    public boolean batchUpdateUsers(List<User> users) {
        Assert.notEmpty(users, "用户列表不能为空");
        if (users.size() > 100) {
            throw new BusinessException("批量更新用户数量不能超过100");
        }
        
        // 参数校验
        for (User user : users) {
            Assert.notNull(user.getId(), "用户ID不能为空");
            Assert.hasText(user.getUsername(), "用户名不能为空");
            Assert.hasText(user.getEmail(), "邮箱不能为空");
        }
        
        // 获取所有需要更新的用户ID
        List<Long> userIds = users.stream().map(User::getId).toList();
        
        // 检查所有用户是否存在
        List<User> existingUsers = listByIds(userIds);
        if (existingUsers.size() != userIds.size()) {
            throw new BusinessException("部分用户不存在");
        }
        
        // 设置更新时间
        LocalDateTime now = LocalDateTime.now();
        users.forEach(user -> user.setUpdateTime(now));
        
        log.info("批量更新用户, 数量: {}", users.size());
        return updateBatchById(users);
    }

    /**
     * 统计用户数量
     * 使用多级缓存，key为固定值"count"
     */
    @Cacheable(cacheNames = "users", key = "'count'")
    @Override
    public int countUsers() {
        log.debug("从数据库统计用户数量");
        return baseMapper.countUsers();
    }

    /**
     * 按状态查询用户
     * 不使用缓存，因为状态数据可能经常变化
     */
    @Override
    public List<User> getUsersByStatus(Integer status) {
        Assert.notNull(status, "用户状态不能为空");
        log.info("按状态查询用户: {}", status);
        
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getStatus, status)
                   .orderByDesc(User::getCreateTime);
        return list(queryWrapper);
    }

    /**
     * 按创建时间范围查询用户
     * 不使用缓存，因为是范围查询
     */
    @Override
    public List<User> getUsersByCreateTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        Assert.notNull(startTime, "开始时间不能为空");
        Assert.notNull(endTime, "结束时间不能为空");
        Assert.isTrue(startTime.isBefore(endTime), "开始时间必须早于结束时间");
        
        log.info("按创建时间范围查询用户, startTime: {}, endTime: {}", startTime, endTime);
        
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.between(User::getCreateTime, startTime, endTime)
                   .orderByDesc(User::getCreateTime);
        return list(queryWrapper);
    }

    /**
     * 批量查询用户
     * 使用多级缓存，key为用户ID列表的hash值
     */
    @Cacheable(cacheNames = "users", key = "'batch:' + T(org.springframework.util.DigestUtils).md5DigestAsHex(T(java.util.Arrays).toString(#ids).getBytes())")
    @Override
    public List<User> batchGetUsers(List<Long> ids) {
        Assert.notEmpty(ids, "用户ID列表不能为空");
        if (ids.size() > 100) {
            throw new BusinessException("批量查询用户数量不能超过100");
        }
        
        log.info("批量查询用户, ID列表: {}", ids);
        return listByIds(ids);
    }

    /**
     * 手动清除缓存
     * 这个方法会被CacheSyncAspect拦截并同步到其他节点
     */
    @CacheEvict(cacheNames = "#cacheName", key = "#key")
    protected void cacheEvict(String cacheName, String key) {
        log.debug("手动清除缓存: {}, key: {}", cacheName, key);
    }

    /**
     * 统计用户总数
     * 使用多级缓存，key为固定值"total"
     */
    @Cacheable(cacheNames = "users", key = "'total'")
    @Override
    public long count() {
        log.info("从数据库统计用户总数");
        return count(new LambdaQueryWrapper<>());
    }
}