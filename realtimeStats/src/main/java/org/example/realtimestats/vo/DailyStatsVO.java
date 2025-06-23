package org.example.realtimestats.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyStatsVO {
    private Long contentId;
    private LocalDate statsDate;
    private Long pvCount;
    private Long uvCount;
    private Long ipCount;
}
