package com.wanzhongxiang.rag;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RagDocumentImportService {

    @Autowired
    private RagDocumentCatalogService ragDocumentCatalogService;
    @Autowired
    private RagIndexService ragIndexService;

    public synchronized int importPendingVersion(String documentId, String version){

        // 参数与 PENDING 检查
        RagDocumentSpec spec = ragDocumentCatalogService.getPendingSpec(documentId, version);

        // 文档索引构建
        int chunkCount = ragIndexService.index(spec);

        // 版本发布
        ragDocumentCatalogService.publishVersion(spec.getDocumentId(),spec.getVersion(),chunkCount);
        return chunkCount;
    }

}
