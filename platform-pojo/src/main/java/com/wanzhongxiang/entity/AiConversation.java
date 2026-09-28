package com.wanzhongxiang.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiConversation {

    // 对外的 conversationId，后续由后端生成 conv_ + UUID
    private String id;

    // 会话所属管理员，对应 employee.id
    private Long employeeId;

    //会话列表标题，创建接口后续负责提供默认“新会话”
    private String title;

    // 创建时间
    private LocalDateTime createTime;

    // 更新时间
    private LocalDateTime updateTime;

}
