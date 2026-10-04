package com.wanzhongxiang.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDispatchAnalysisVO {

    private Long orderId; // 查询得到订单主键
    private Integer orderStatus; // 查询得到订单状态
    private LocalDateTime queriedAt; // 本次检索返回的规则候选（可能为空）
    private String answer; // 模型回答或标准资料不足
    private List<RagSourceVO> sources; // 从本轮规则核对的真实引用

}
