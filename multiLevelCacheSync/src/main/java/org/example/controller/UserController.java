package org.example.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.User;
import org.example.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户控制器
 * 提供用户相关的REST API
 */
@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "用户管理", description = "用户CRUD操作和缓存管理")
public class UserController {

    private final UserService userService;

    @GetMapping
    @Operation(summary = "获取所有用户", description = "返回系统中的所有用户")
    public ResponseEntity<List<User>> getAllUsers() {
        log.debug("REST request to get all users");
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据ID获取用户", description = "根据用户ID返回用户信息")
    public ResponseEntity<User> getUserById(
            @Parameter(description = "用户ID") @PathVariable Long id) {
        log.debug("REST request to get user by id: {}", id);
        User user = userService.getUser(id);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(user);
    }

    @PostMapping
    @Operation(summary = "创建用户", description = "创建新用户并返回创建的用户信息")
    public ResponseEntity<User> createUser(
            @Parameter(description = "用户信息") @RequestBody User user) {
        log.debug("REST request to create user: {}", user);
        if (user.getId() != null) {
            return ResponseEntity.badRequest().build();
        }
        User result = userService.createUser(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新用户", description = "根据ID更新用户信息")
    public ResponseEntity<User> updateUser(
            @Parameter(description = "用户ID") @PathVariable Long id,
            @Parameter(description = "用户信息") @RequestBody User user) {
        log.debug("REST request to update user: {}", user);
        if (user.getId() == null || !user.getId().equals(id)) {
            return ResponseEntity.badRequest().build();
        }
        if (!userService.exists(id)) {
            return ResponseEntity.notFound().build();
        }
        User result = userService.updateUser(user);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除用户", description = "根据ID删除用户")
    public ResponseEntity<Void> deleteUser(
            @Parameter(description = "用户ID") @PathVariable Long id) {
        log.debug("REST request to delete user: {}", id);
        if (!userService.exists(id)) {
            return ResponseEntity.notFound().build();
        }
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/name/{name}")
    @Operation(summary = "根据名称查找用户", description = "根据名称模糊查询用户")
    public ResponseEntity<List<User>> getUsersByName(
            @Parameter(description = "用户名称") @PathVariable String name) {
        log.debug("REST request to get users by name: {}", name);
        return ResponseEntity.ok(userService.getUsersByUsernameLike(name));
    }


    @PutMapping("/{id}/status")
    @Operation(summary = "更新用户状态", description = "启用或禁用用户")
    public ResponseEntity<User> updateUserStatus(
            @Parameter(description = "用户ID") @PathVariable Long id,
            @Parameter(description = "是否启用") @RequestParam boolean enabled) {
        log.debug("REST request to update user status: {}, enabled: {}", id, enabled);
        if (!userService.exists(id)) {
            return ResponseEntity.notFound().build();
        }
        User result = userService.updateStatus(id, enabled);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询用户", description = "分页获取用户列表")
    public ResponseEntity<List<User>> getUsersByPage(
            @Parameter(description = "页码") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int size) {
        log.debug("REST request to get users by page: {}, size: {}", page, size);
        return ResponseEntity.ok(userService.findByPage(page, size));
    }

    @GetMapping("/stats")
    @Operation(summary = "获取用户统计信息", description = "获取用户相关的统计数据")
    public ResponseEntity<Map<String, Object>> getUserStats() {
        log.debug("REST request to get user statistics");
        return ResponseEntity.ok(userService.getStats());
    }

    @GetMapping("/count")
    @Operation(summary = "获取用户总数", description = "获取系统中的用户总数")
    public ResponseEntity<Long> getUserCount() {
        log.debug("REST request to get user count");
        return ResponseEntity.ok(userService.count());
    }

    // 缓存管理相关API

    @DeleteMapping("/{id}/cache")
    @Operation(summary = "清除指定用户缓存", description = "清除指定用户ID的缓存")
    public ResponseEntity<Void> clearUserCache(
            @Parameter(description = "用户ID") @PathVariable Long id) {
        log.debug("REST request to clear cache for user: {}", id);
        userService.clearCache(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/cache")
    @Operation(summary = "清除所有用户缓存", description = "清除所有用户相关的缓存")
    public ResponseEntity<Void> clearAllUserCache() {
        log.debug("REST request to clear all user caches");
        userService.clearAllCache();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/cache/preload")
    @Operation(summary = "预加载用户缓存", description = "预加载指定数量的用户到缓存中")
    public ResponseEntity<Void> preloadUserCache(
            @Parameter(description = "预加载数量") @RequestParam(defaultValue = "100") int limit) {
        log.debug("REST request to preload user cache with limit: {}", limit);
        userService.preloadCache(limit);
        return ResponseEntity.noContent().build();
    }
}
