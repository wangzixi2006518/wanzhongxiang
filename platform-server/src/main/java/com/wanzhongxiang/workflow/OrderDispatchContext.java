package com.wanzhongxiang.workflow;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.ai.document.Document;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
public class OrderDispatchContext {
    // 保存当前流程的中间输入材料

    private Long orderId; // 查询得到订单主键
    private Integer orderStatus; // 查询得到订单状态
    private LocalDateTime queriedAt; // 服务端本次读取事实的时间标记
    private List<Document> ruleDocuments; // 本次检索返回的规则候选（可能为空）


}
