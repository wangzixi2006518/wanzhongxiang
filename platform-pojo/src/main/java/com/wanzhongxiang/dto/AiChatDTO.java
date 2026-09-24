package com.wanzhongxiang.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
public class AiChatDTO {

    private String message;
    private String conversationId;
    private String mode;

}
