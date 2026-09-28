package com.wanzhongxiang.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiConversationVO {

    private String id;
    private String title;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
