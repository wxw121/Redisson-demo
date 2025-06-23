package org.example.realtimestats.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.realtimestats.entity.Content;
import org.example.realtimestats.service.ContentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 内容管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/content")
public class ContentController {

    @Autowired
    private ContentService contentService;

    /**
     * 创建内容
     */
    @PostMapping
    public ResponseEntity<Long> createContent(@RequestBody Content content) {
        try {
            boolean saved = contentService.save(content);
            Long contentId = saved ? content.getId() : null;
            return ResponseEntity.ok(contentId);
        } catch (Exception e) {
            log.error("创建内容失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 更新内容
     */
    @PutMapping("/{id}")
    public ResponseEntity<Boolean> updateContent(@PathVariable("id") Long id, @RequestBody Content content) {
        try {
            content.setId(id);
            boolean result = contentService.updateById(content);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("更新内容失败，id=" + id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 删除内容
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Boolean> deleteContent(@PathVariable("id") Long id) {
        try {
            boolean result = contentService.removeById(id);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("删除内容失败，id=" + id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取内容详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<Content> getContent(@PathVariable("id") Long id) {
        try {
            Content content = contentService.getById(id);
            if (content != null) {
                return ResponseEntity.ok(content);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("获取内容失败，id=" + id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 手动触发同步计数器到数据库
     */
    @PostMapping("/{id}/sync")
    public ResponseEntity<Void> syncCounters(@PathVariable("id") Long id) {
        try {
            contentService.syncCountersToDb(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("同步计数器失败，id=" + id, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
