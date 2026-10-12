package com.wanzhongxiang.rag;

import lombok.Data;

@Data
public class RagDocumentSpec {

    private String documentId;
    private String documentTitle;
    private String version;
    private String sourceType;
    private String sourcePath;


}
