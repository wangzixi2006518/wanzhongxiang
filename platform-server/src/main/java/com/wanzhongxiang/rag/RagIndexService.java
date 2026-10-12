package com.wanzhongxiang.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RagIndexService {

    @Autowired
    private RagDocumentLoader ragDocumentLoader;
    @Autowired
    private VectorStore vectorStore;

    public int index(RagDocumentSpec spec){
        // spec 是调用方传入的方法参数，描述要导入哪份资料

        // 声明 List<Document> 接收 loadAndSplit(spec) 的返回值
        List<Document> chunks = ragDocumentLoader.loadAndSplit(spec);

        // 判断是否为空
        if(chunks == null || chunks.isEmpty()){
            throw new IllegalStateException("资料没有可用片段");
        }

        // 创建 builder 构造 Filter
        FilterExpressionBuilder builder = new FilterExpressionBuilder();

        Filter.Expression versionFilter = builder.and( // 两个 .eq 都成立时 .and 成立
                builder.eq("document_id", spec.getDocumentId()),
                builder.eq("version", spec.getVersion())
        ).build();

        // 调用 .delete 删除旧片段
        vectorStore.delete(versionFilter);

        // 获得的片段资料添加到 vectorStore 中
        vectorStore.add(chunks);

        // 返回添加资料片段的大小
        return chunks.size();
    }


}
