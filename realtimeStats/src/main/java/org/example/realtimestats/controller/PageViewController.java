package org.example.realtimestats.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.realtimestats.entity.PageView;
import org.example.realtimestats.service.PageViewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 页面访问统计控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/pageview")
public class PageViewController {

    @Autowired
    private PageViewService pageViewService;

    /**
     * 记录页面访问
     */
    @PostMapping
    public ResponseEntity<Boolean> recordPageView(@RequestBody PageView pageView) {
        try {
            boolean result = pageViewService.recordPageView(
                pageView.getContentId(),
                pageView.getUserId(),
                pageView.getIpAddress(),
                pageView.getUserAgent(),
                pageView.getReferer()
            );
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("记录页面访问失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取内容的PV和UV统计
     */
    @GetMapping("/stats/{contentId}")
    public ResponseEntity<Map<String, Object>> getPageViewStats(
            @PathVariable("contentId") Long contentId,
            @RequestParam(value = "startTime", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(value = "endTime", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        try {
            Long pv = pageViewService.getPageViewCount(contentId, startTime, endTime);
            Long uv = pageViewService.getUniqueVisitorCount(contentId, startTime, endTime);

            Map<String, Object> stats = new HashMap<>();
            stats.put("contentId", contentId);
            stats.put("pv", pv);
            stats.put("uv", uv);

            if (startTime != null) {
                stats.put("startTime", startTime);
            }
            if (endTime != null) {
                stats.put("endTime", endTime);
            }

            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("获取页面访问统计失败，contentId=" + contentId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取内容的PV数（页面浏览量）
     */
    @GetMapping("/pv/{contentId}")
    public ResponseEntity<Long> getPageViews(
            @PathVariable("contentId") Long contentId,
            @RequestParam(value = "startTime", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(value = "endTime", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        try {
            Long pv = pageViewService.getPageViewCount(contentId, startTime, endTime);
            return ResponseEntity.ok(pv);
        } catch (Exception e) {
            log.error("获取PV统计失败，contentId=" + contentId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取内容的UV数（独立访客数）
     */
    @GetMapping("/uv/{contentId}")
    public ResponseEntity<Long> getUniqueVisitors(
            @PathVariable("contentId") Long contentId,
            @RequestParam(value = "startTime", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(value = "endTime", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        try {
            Long uv = pageViewService.getUniqueVisitorCount(contentId, startTime, endTime);
            return ResponseEntity.ok(uv);
        } catch (Exception e) {
            log.error("获取UV统计失败，contentId=" + contentId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 手动触发同步页面访问数据到数据库
     */
    @PostMapping("/sync")
    public ResponseEntity<Void> syncPageViewData() {
        try {
            pageViewService.syncPageViewsToDb();
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("同步页面访问数据失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}