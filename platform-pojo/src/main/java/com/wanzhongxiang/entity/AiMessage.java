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
public class AiMessage {

    private String id;
    private String conversationId;
    private String role;
    private String content;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String turnId;

}
