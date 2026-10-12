package com.wanzhongxiang.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RagRetrievalService {
    // 建立索引规则并检查检索片段

//    @Autowired
//    private EmbeddingModel embeddingModel;
//    @Autowired
//    private RagDocumentLoader ragDocumentLoader;
    @Autowired
    private VectorStore vectorStore;
    @Autowired
    private RagDocumentCatalogService ragDocumentCatalogService;

    // 先定义出来，后续由显式建立方法赋值用作向量库
//    private SimpleVectorStore simpleVectorStore;

    // 取回资料，此时拿到的是候选资料，还没有生成答案
    public List<Document> search(String query){
        // 根据问题检索规则片段

        // 1. 拒绝 null/空白问题，抛 IllegalArgumentException，避免无意义云端请求
        if(query == null || query.isBlank()){
            throw new IllegalArgumentException("问题不能为空");
        }

        List<Document> documentList = searchActive(query);

//        // 2. 实例存储为空时抛 IllegalStateException，明确“规则索引尚未建立”
//        if(simpleVectorStore == null){
//            throw new IllegalStateException("规则索引尚未建立");
//        }
//
//        // 3. 构建 SearchRequest，使用 query、topK=2、similarityThreshold=0.5
//        SearchRequest searchRequest = SearchRequest.builder()
//                .query(query) // 原问题字符：指定查询文本；传文本即可，内部负责向量化
//                .topK(2) // 最多返回 2：限制候选数量，不保证一定有两个结果
//                .similarityThreshold(0.5) // 保留 score >= 0.5 的候选结果
//                .build();
//
//        // 4. 调用已有存储的 similaritySearch，返回结果
//        //    内部将 query 转为向量，与规则向量计算相似度，按阈值过滤并最多返回 topK 个结果，如有异常会向上抛出
//        List<Document> documentList = simpleVectorStore.similaritySearch(searchRequest);

        return documentList;
    }

    // 只从 Milvus 检索已经生效的资料
    public List<Document> searchActive(String query){

        // 1. 拒绝 null/空白问题，抛 IllegalArgumentException，避免无意义云端请求
        if(query == null || query.isBlank()){
            throw new IllegalArgumentException("问题不能为空");
        }

        // 查询生效目录
        List<RagDocumentSpec> activeSpecs = ragDocumentCatalogService.listActiveSpecs();
        if(activeSpecs == null || activeSpecs.isEmpty()){
            return List.of();
        }

        // 声明条件构造器与累加变量
        FilterExpressionBuilder builder = new FilterExpressionBuilder(); // builder负责构造条件
        FilterExpressionBuilder.Op activeFilter = null; // activeFilter保存“已经处理过的全部资料范围”

        //  逐份构造并合并条件
        for (RagDocumentSpec spec : activeSpecs) {
            FilterExpressionBuilder.Op oneVersion = builder.group(
                    builder.and(
                            builder.eq("document_id",spec.getDocumentId()),
                            builder.eq("version",spec.getVersion())
                    )
            );

            // 如果是第一轮，将 oneVersion 赋值给 activeFilter
            if(activeFilter == null){
                activeFilter = oneVersion;
            }else{
                activeFilter = builder.or(activeFilter,oneVersion); // 合并两个条件，满足任意一个返回新的 Op
            }
        }

        // 构建查询请求
        SearchRequest searchRequest = SearchRequest.builder()
                .query(query) // 原问题字符：指定查询文本；传文本即可，内部负责向量化
                .topK(2) // 最多返回 2：限制候选数量，不保证一定有两个结果
                .similarityThreshold(0.5) // 保留 score >= 0.5 的候选结果
                .filterExpression(activeFilter.build()) // 产生过滤表达式
                .build(); // 产生完整请求

        // 执行查询并返回
        List<Document> documentList = vectorStore.similaritySearch(searchRequest);
        return documentList;
    }

    //    // 只操作内存，已不参与新资料流程

//    public int buildIndex(){
//        // 建立当前规则的索引
//
//        // 1. 复用 loadAndSplit，得到全部 Document
//        List<Document> documentList = ragDocumentLoader.loadAndSplit();
//
//        // 2. 用注入的 EmbeddingModel 创建一个新的、局部的 SimpleVectorStore
//        // 可以理解为创建一个新的内存向量库，并指定它使用当前的 embeddingModel 来生成向量
//        SimpleVectorStore simpleVectorStore = SimpleVectorStore.builder(embeddingModel).build();
//
//        // 3. 对这个新对象调用 add，传入刚读出的 Document 列表
//        simpleVectorStore.add(documentList);
//
//        // 4. 全部导入成功后，再把新对象赋给实例字段，返回片段数量
//        this.simpleVectorStore = simpleVectorStore;
//        return documentList.size();
//    }
//
//    // 清空当前规则索引的明确入口
//    public void clearIndex(){
//        // 清空索引但不要赋值为 null，否则会抛异常，返回 [] 即可
//        SimpleVectorStore emptySimpleVectorStore = SimpleVectorStore.builder(embeddingModel).build();
//        // 交给实例字段 this.simpleVectorStore
//        this.simpleVectorStore = emptySimpleVectorStore;
//    }

}
