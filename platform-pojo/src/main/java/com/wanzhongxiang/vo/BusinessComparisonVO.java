package com.wanzhongxiang.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class BusinessComparisonVO {


    // 当前统计周期开始日期
    private LocalDate currentBegin;
    // 当前统计周期结束日期
    private LocalDate currentEnd;
    // 基准（对比）周期开始日期
    private LocalDate baselineBegin;
    // 基准（对比）周期结束日期
    private LocalDate baselineEnd;

    // 当前周期有效订单数
    private Long currentValidOrderCount;
    // 基准周期有效订单数
    private Long baselineValidOrderCount;

    // 当前周期营业额
    private BigDecimal currentTurnover;
    // 基准周期营业额
    private BigDecimal baselineTurnover;

    // 当前周期客单价（营业额 / 有效订单数）
    private BigDecimal currentUnitPrice;
    // 基准周期客单价（营业额 / 有效订单数）
    private BigDecimal baselineUnitPrice;

    // 营业额差值（当前周期营业额 - 基准周期营业额）
    private BigDecimal turnoverDifference;
    // 营业额变化率（营业额差值 / 基准周期营业额）
    // 注意：需约定返回小数还是百分数，并处理基准营业额为 0 的情况
    private BigDecimal turnoverChangeRate;

}
