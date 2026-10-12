package com.wanzhongxiang.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RagVersionImportDTO {

    @NotBlank(message = "文档ID不能为空")
    @Size(max = 64,message = "不能超过64字")
    private String documentId;
    @NotBlank(message = "版本不能为空")
    @Size(max = 64,message = "不能超过64字")
    private String version;

}
