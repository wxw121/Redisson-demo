package org.example.endpoint;

import io.micrometer.common.util.StringUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.endpoint.CacheMonitor;
import org.example.endpoint.CacheStats;
import org.example.endpoint.CacheWarmer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 缓存监控REST接口
 * 提供缓存监控、预热和管理功能
 */
@Slf4j
@RestController
@RequestMapping("/api/cache")
@RequiredArgsConstructor
@Tag(name = "缓存监控", description = "缓存监控、预热和管理接口")
public class CacheMonitorEndpoint {

    private final CacheMonitor cacheMonitor;
    private final CacheWarmer cacheWarmer;

    @GetMapping("/stats")
    @Operation(summary = "获取缓存统计信息", description = "获取所有缓存的统计信息，包括命中率、访问次数等")
    public ResponseEntity<Map<String, Object>> getCacheStats() {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", new Date());

        try {
            Map<String, CacheStats> stats = cacheMonitor.getAllCacheStats();
            response.put("stats", stats);
            response.put("success", true);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to get cache stats", e);
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @GetMapping("/stats/{cacheName}")
    @Operation(summary = "获取指定缓存的统计信息", description = "获取指定缓存的详细统计信息")
    public ResponseEntity<Map<String, Object>> getCacheStats(
            @Parameter(description = "缓存名称") @PathVariable String cacheName) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", new Date());
        response.put("cacheName", cacheName);

        try {
            CacheStats stats = cacheMonitor.getCacheStats(cacheName);
            if (stats != null) {
                response.put("stats", stats);
                response.put("success", true);
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("error", "Cache not found: " + cacheName);
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Failed to get cache stats for: {}", cacheName, e);
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping("/warm/{cacheName}")
    @Operation(summary = "预热指定缓存", description = "触发指定缓存的预热操作")
    public ResponseEntity<Map<String, Object>> warmCache(
            @Parameter(description = "缓存名称") @PathVariable String cacheName) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", new Date());
        response.put("cacheName", cacheName);

        try {
            String cache = cacheWarmer.warmupCache(cacheName);
            Boolean success = StringUtils.isNotEmpty(cache);
            response.put("success", success);
            if (success) {
                response.put("message", "Cache warming started for: " + cacheName);
                return ResponseEntity.ok(response);
            } else {
                response.put("error", "Failed to start cache warming for: " + cacheName);
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            log.error("Error during cache warming for: {}", cacheName, e);
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping("/warm")
    @Operation(summary = "预热所有缓存", description = "触发所有缓存的预热操作")
    public ResponseEntity<Map<String, Object>> warmAllCaches() {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", new Date());

        try {
            List<String> warmedCaches = cacheWarmer.warmupAllCaches();
            response.put("success", true);
            response.put("warmedCaches", warmedCaches);
            response.put("message", "Cache warming started for all caches");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error during warming all caches", e);
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @DeleteMapping("/{cacheName}")
    @Operation(summary = "清除指定缓存", description = "清除指定缓存的所有数据")
    public ResponseEntity<Map<String, Object>> clearCache(
            @Parameter(description = "缓存名称") @PathVariable String cacheName) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", new Date());
        response.put("cacheName", cacheName);

        try {
            boolean cleared = cacheMonitor.clearCache(cacheName);
            response.put("success", cleared);
            if (cleared) {
                response.put("message", "Cache cleared: " + cacheName);
                return ResponseEntity.ok(response);
            } else {
                response.put("error", "Failed to clear cache: " + cacheName);
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            log.error("Error clearing cache: {}", cacheName, e);
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @DeleteMapping
    @Operation(summary = "清除所有缓存", description = "清除所有缓存的数据")
    public ResponseEntity<Map<String, Object>> clearAllCaches() {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", new Date());

        try {
            List<String> clearedCaches = cacheMonitor.clearAllCaches();
            response.put("success", true);
            response.put("clearedCaches", clearedCaches);
            response.put("message", "All caches cleared");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error clearing all caches", e);
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @GetMapping("/keys/{cacheName}")
    @Operation(summary = "获取缓存键列表", description = "获取指定缓存中的所有键")
    public ResponseEntity<Map<String, Object>> getCacheKeys(
            @Parameter(description = "缓存名称") @PathVariable String cacheName,
            @Parameter(description = "分页大小") @RequestParam(required = false, defaultValue = "100") int pageSize,
            @Parameter(description = "页码") @RequestParam(required = false, defaultValue = "1") int page) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", new Date());
        response.put("cacheName", cacheName);

        try {
            List<String> keys = cacheMonitor.getCacheKeys(cacheName, page, pageSize);
            long totalKeys = cacheMonitor.getCacheSize(cacheName);

            response.put("success", true);
            response.put("keys", keys);
            response.put("page", page);
            response.put("pageSize", pageSize);
            response.put("totalKeys", totalKeys);
            response.put("totalPages", Math.ceil((double) totalKeys / pageSize));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting cache keys for: {}", cacheName, e);
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @GetMapping("/health")
    @Operation(summary = "获取缓存健康状态", description = "获取所有缓存的健康状态信息")
    public ResponseEntity<Map<String, Object>> getCacheHealth() {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", new Date());

        try {
            Map<String, Object> healthInfo = cacheMonitor.getHealthInfo();
            response.put("health", healthInfo);
            response.put("success", true);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting cache health info", e);
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping("/stats/reset/{cacheName}")
    @Operation(summary = "重置指定缓存的统计信息", description = "重置指定缓存的所有统计数据")
    public ResponseEntity<Map<String, Object>> resetCacheStats(
            @Parameter(description = "缓存名称") @PathVariable String cacheName) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", new Date());
        response.put("cacheName", cacheName);

        try {
            boolean reset = cacheMonitor.resetStats(cacheName);
            response.put("success", reset);
            if (reset) {
                response.put("message", "Cache stats reset for: " + cacheName);
                return ResponseEntity.ok(response);
            } else {
                response.put("error", "Failed to reset cache stats for: " + cacheName);
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            log.error("Error resetting cache stats for: {}", cacheName, e);
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping("/stats/reset")
    @Operation(summary = "重置所有缓存的统计信息", description = "重置所有缓存的统计数据")
    public ResponseEntity<Map<String, Object>> resetAllCacheStats() {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", new Date());

        try {
            List<String> resetCaches = cacheMonitor.resetAllStats();
            response.put("success", !resetCaches.isEmpty());
            response.put("resetCaches", resetCaches);
            response.put("message", "Statistics reset for all caches");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error resetting all cache stats", e);
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}