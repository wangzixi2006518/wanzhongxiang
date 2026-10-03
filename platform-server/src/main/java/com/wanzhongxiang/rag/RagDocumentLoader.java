package com.wanzhongxiang.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class RagDocumentLoader {
    // 负责读取本地规则文档、补充元数据，并按 Token 切分为可用于 RAG 的 Document 片段

    public List<Document> loadAndSplit(){

        String classpath = "knowledge/order-processing-rules-v1.txt";

        // 读取固定资料 -> 设置来源 metadata -> 切分 -> 标识片段 -> 返回
        // 1.创建 TextReader
        TextReader textReader = new TextReader(classpath);

        // 2.自定义metadata
        Map<String, Object> metadata = textReader.getCustomMetadata();
        metadata.put("document_id","wx_order_rules");
        metadata.put("document_title","万众享订单处理规则说明");
        metadata.put("version","v1-20261002");
        metadata.put("source_type","code-derived");
        metadata.put("source_path",classpath);

        // 3.调用 read() 得到原文列表，并用局部变量保留，便于调试比较切分前后的数量与正文
        List<Document> read = textReader.read();

        // 4.构建 TokenTextSplitter，调用 split(原文列表)
        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(400) // 每个片段的目标大小约为 400 Token
                .withMinChunkSizeChars(100) // 标点或换行的断点位置超过这个字符门槛时，才优先在那里切开
                .withMinChunkLengthToEmbed(0) // 保留非空的短片段，避免丢掉很短的尾部正文
                .build();
        List<Document> chunks = splitter.split(read);

        // 5.遍历返回片段，加入编号
        int chunkNo = 1;
        for(Document chunk : chunks){
            chunk.getMetadata().put("chunk_no",chunkNo);
            chunk.getMetadata().put("chunk_id","wx_order_rules_v1-20261002_chunk_" + chunkNo);
            chunkNo++;
        }

        return chunks;
    }

}
