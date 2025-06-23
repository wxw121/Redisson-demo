package org.example.realtimestats.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.realtimestats.service.LikeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 点赞控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/like")
public class LikeController {

    @Autowired
    private LikeService likeService;

    /**
     * 用户点赞/取消点赞内容
     */
    @PostMapping("/{contentId}")
    public ResponseEntity<Map<String, Object>> likeContent(
            @PathVariable("contentId") Long contentId,
            @RequestParam("userId") Long userId) {
        try {
            boolean liked = likeService.likeContent(userId, contentId);
            Long likeCount = likeService.getLikeCount(contentId);

            Map<String, Object> result = new HashMap<>();
            result.put("liked", liked);
            result.put("likeCount", likeCount);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("处理点赞操作失败，userId=" + userId + "，contentId=" + contentId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取内容的点赞数
     */
    @GetMapping("/count/{contentId}")
    public ResponseEntity<Long> getLikeCount(@PathVariable("contentId") Long contentId) {
        try {
            Long likeCount = likeService.getLikeCount(contentId);
            return ResponseEntity.ok(likeCount);
        } catch (Exception e) {
            log.error("获取点赞数失败，contentId=" + contentId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 检查用户是否已点赞内容
     */
    @GetMapping("/check")
    public ResponseEntity<Boolean> hasUserLiked(
            @RequestParam("userId") Long userId,
            @RequestParam("contentId") Long contentId) {
        try {
            boolean hasLiked = likeService.hasUserLiked(userId, contentId);
            return ResponseEntity.ok(hasLiked);
        } catch (Exception e) {
            log.error("检查点赞状态失败，userId=" + userId + "，contentId=" + contentId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 手动触发同步点赞数据到数据库
     */
    @PostMapping("/{contentId}/sync")
    public ResponseEntity<Void> syncLikeData(@PathVariable("contentId") Long contentId) {
        try {
            likeService.syncLikeDataToDb(contentId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("同步点赞数据失败，contentId=" + contentId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}