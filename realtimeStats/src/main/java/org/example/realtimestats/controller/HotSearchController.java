package org.example.realtimestats.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.realtimestats.entity.HotSearch;
import org.example.realtimestats.service.HotSearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 热搜词控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/hotsearch")
public class HotSearchController {

    @Autowired
    private HotSearchService hotSearchService;

    /**
     * 记录搜索关键词
     */
    @PostMapping("/record")
    public ResponseEntity<Map<String, Object>> recordSearch(@RequestParam("keyword") String keyword) {
        try {
            boolean recorded = hotSearchService.recordSearch(keyword);
            Long hotValue = hotSearchService.getHotValue(keyword);

            Map<String, Object> result = new HashMap<>();
            result.put("recorded", recorded);
            result.put("keyword", keyword);
            result.put("hotValue", hotValue);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("记录搜索关键词失败，keyword=" + keyword, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 增加搜索词的热度
     */
    @PostMapping("/increment")
    public ResponseEntity<Map<String, Object>> incrementHotValue(
            @RequestParam("keyword") String keyword,
            @RequestParam("increment") Long increment) {
        try {
            Boolean isSuccess = hotSearchService.incrementHotValue(keyword, increment);

            Map<String, Object> result = new HashMap<>();
            result.put("keyword", keyword);
            result.put("isSuccess", isSuccess);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("增加热搜词热度失败，keyword=" + keyword + "，increment=" + increment, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取热搜榜
     */
    @GetMapping("/list")
    public ResponseEntity<List<HotSearch>> getHotSearchList(
            @RequestParam(value = "limit", defaultValue = "10") Integer limit) {
        try {
            List<HotSearch> hotSearchList = hotSearchService.getHotSearchList(limit);
            return ResponseEntity.ok(hotSearchList);
        } catch (Exception e) {
            log.error("获取热搜榜失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取搜索词的热度值
     */
    @GetMapping("/hot-value")
    public ResponseEntity<Long> getHotValue(@RequestParam("keyword") String keyword) {
        try {
            Long hotValue = hotSearchService.getHotValue(keyword);
            return ResponseEntity.ok(hotValue);
        } catch (Exception e) {
            log.error("获取热搜词热度失败，keyword=" + keyword, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 手动触发同步热搜数据到数据库
     */
    @PostMapping("/sync")
    public ResponseEntity<Void> syncHotSearchData() {
        try {
            hotSearchService.syncHotSearchesToDb();
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("同步热搜数据失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}