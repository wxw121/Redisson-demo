package org.example.realtimestats.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HourlyStatsVO {
    private Long contentId;
    private LocalDate statsDate;
    private Long statsHour;
    private Long pvCount;
    private Long uvCount;
    private Long ipCount;
    private LocalDateTime statsTime;
}