package com.wanzhongxiang.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
public class AiChatDTO {

    @NotBlank(message = "问题不能为空")
    @Size(max = 500,message = "问题不能超过500字")
    private String message;
    private String conversationId;
    private String mode;

}
