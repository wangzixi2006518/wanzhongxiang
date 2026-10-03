package com.wanzhongxiang.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RagEmbeddingService {
    // 负责将普通文本或规则文档片段通过 Embedding 模型转换为向量

    @Autowired
    private EmbeddingModel embeddingModel;
    @Autowired
    private RagDocumentLoader ragDocumentLoader;

    public float[] embedText(String text){

        // 判断文本是否合法
        if(text == null || text.isBlank()){
            throw new IllegalArgumentException("文本不能为空");
        }

        // 返回实际向量
        float[] embed = embeddingModel.embed(text);
        return embed;
    }

    public List<float[]> embedRuleChunks(){

        // 复用已有 loadAndSplit 得到 3 个片段
        List<Document> documentList = ragDocumentLoader.loadAndSplit();

        // 把每个片段正文转换成 List<String>，调用批量 embed
        List<String> documentStrings = new ArrayList<>();
        for (Document document : documentList) {
            String documentString = document.getText();
            documentStrings.add(documentString);
        }

        // 一次性批量生成向量
        List<float[]> embeds = embeddingModel.embed(documentStrings);

        // 返回 List<float[]>
        return embeds;
    }

}
