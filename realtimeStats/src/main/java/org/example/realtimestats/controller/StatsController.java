package org.example.realtimestats.controller;

import lombok.RequiredArgsConstructor;
import org.example.realtimestats.service.PageViewService;
import org.example.realtimestats.vo.DailyStatsVO;
import org.example.realtimestats.vo.HourlyStatsVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final PageViewService pageViewService;

    /**
     * 获取指定内容的每日统计数据
     */
    @GetMapping("/daily")
    public List<DailyStatsVO> getDailyStats(
            @RequestParam Long contentId,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        if (startDate == null) {
            startDate = LocalDate.now().minusDays(7);
        }
        if (endDate == null) {
            endDate = LocalDate.now();
        }
        
        // 将 LocalDate 转换为 LocalDateTime，设置时间为一天的开始和结束
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();
        
        // 使用每日间隔（24*60分钟）
        Map<String, Long> trendStats = pageViewService.getViewTrend(contentId, start, end, 24*60);
        
        // 将 Map<String, Long> 转换为 List<DailyStatsVO>
        List<DailyStatsVO> result = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        
        for (Map.Entry<String, Long> entry : trendStats.entrySet()) {
            LocalDateTime timestamp = LocalDateTime.parse(entry.getKey(), formatter);
            Long pvCount = entry.getValue();
            
            DailyStatsVO statsVO = DailyStatsVO.builder()
                    .contentId(contentId)
                    .statsDate(timestamp.toLocalDate())
                    .pvCount(pvCount)
                    // 注意：UV和IP计数在当前API中不可用，设置为0或者null
                    .uvCount(0L)
                    .ipCount(0L)
                    .build();
            
            result.add(statsVO);
        }
        
        return result;
    }

    /**
     * 获取指定内容的每小时统计数据
     */
    @GetMapping("/hourly")
    public List<HourlyStatsVO> getHourlyStats(
            @RequestParam Long contentId,
            @RequestParam(required = false) LocalDate date) {
        if (date == null) {
            date = LocalDate.now();
        }
        
        // 将 LocalDate 转换为 LocalDateTime，设置时间为一天的开始和结束
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();
        
        // 使用每小时间隔（60分钟）
        Map<String, Long> trendStats = pageViewService.getViewTrend(contentId, start, end, 60);
        
        // 将 Map<String, Long> 转换为 List<HourlyStatsVO>
        List<HourlyStatsVO> result = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        
        for (Map.Entry<String, Long> entry : trendStats.entrySet()) {
            LocalDateTime timestamp = LocalDateTime.parse(entry.getKey(), formatter);
            Long pvCount = entry.getValue();
            
            HourlyStatsVO statsVO = HourlyStatsVO.builder()
                    .contentId(contentId)
                    .statsTime(timestamp)
                    .pvCount(pvCount)
                    // 注意：UV和IP计数在当前API中不可用，设置为0或者null
                    .uvCount(0L)
                    .ipCount(0L)
                    .build();
            
            result.add(statsVO);
        }
        
        return result;
    }

    /**
     * 获取实时访问量（从Redis中获取）
     */
    @GetMapping("/realtime")
    public Long getRealtimePV(@RequestParam Long contentId) {
        Map<String, Object> stats = pageViewService.getRealTimeStats(contentId);
        // 从Map中获取PV计数，如果不存在则返回0
        return stats.containsKey("pv") ? ((Number) stats.get("pv")).longValue() : 0L;
    }
}
