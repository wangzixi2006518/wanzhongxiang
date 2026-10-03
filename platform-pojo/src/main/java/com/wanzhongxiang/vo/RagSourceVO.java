package com.wanzhongxiang.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagSourceVO {
    // 一条经核对的引用来源

    private String documentId;
    private String documentTitle;
    private String version;
    private String chunkId;
    private String text;

}
