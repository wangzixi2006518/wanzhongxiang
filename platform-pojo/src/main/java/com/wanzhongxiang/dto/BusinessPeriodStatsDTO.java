package com.wanzhongxiang.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class BusinessPeriodStatsDTO {

    private Long validOrderCount;
    private BigDecimal turnover;

}
