package com.wanzhongxiang.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.*;

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

    public List<Document> loadAndSplit(RagDocumentSpec spec){

        // 判断没有空字段，防止信息不完整
        if(spec == null
                || spec.getDocumentId() == null || spec.getDocumentId().isBlank()
                || spec.getDocumentTitle() == null || spec.getDocumentTitle().isBlank()
                || spec.getVersion() == null || spec.getVersion().isBlank()
                || spec.getSourceType() == null || spec.getSourceType().isBlank()
                || spec.getSourcePath() == null || spec.getSourcePath().isBlank()){
            throw new IllegalStateException("信息不完整");
        }

        // 用 TextReader 读取 sourcePath，自定义metadata
        TextReader textReader = new TextReader(spec.getSourcePath());

        Map<String, Object> metadata = textReader.getCustomMetadata();
        metadata.put("document_id", spec.getDocumentId());
        metadata.put("document_title", spec.getDocumentTitle());
        metadata.put("version", spec.getVersion());
        metadata.put("source_type", spec.getSourceType());
        metadata.put("source_path", spec.getSourcePath());

        //  TextReader读取当前TXT，得到包含整份正文的Document
        Document document = textReader.read().get(0);
        String text = document.getText();
        if(text == null || text.isBlank()){
            throw new IllegalArgumentException("资料正文不能为空");
        }

        // 识别章节，标题与正文分开
        List<Document> sections = new ArrayList<>();

        int sectionNo = 0;
        String sectionTitle = null;
        StringBuilder sectionBody = new StringBuilder();

        for (String line : text.lines().toList()) {
            // 如果以 ## 开头
            if(line.startsWith("## ")){

                // 新标题出现，保存上一章
                if(sectionTitle != null){
                    addSection(sections,document,sectionNo,sectionTitle,sectionBody.toString());
                }

                // 开始新的一章
                sectionTitle = line.substring(3).strip();
                if(sectionTitle.isBlank()){
                    throw new IllegalArgumentException("标题不能为空");
                }

                sectionNo++; // 编号 + 1
                sectionBody.setLength(0); // 遇到新的章节标题，清空上一章的正文

            }else {
                if (sectionTitle == null){
                    // 未出现标题，不接受无归属正文
                    if(!line.isBlank()){
                        throw new IllegalArgumentException("正文必须位于章节标题之后");
                    }
                }else {
                    sectionBody.append(line).append("\n");
                }
            }
        }
        // 循环结束先检查是否有章节
        if (sectionNo == 0) {
            throw new IllegalArgumentException("文档中没有找到章节");
        }
        addSection(sections,document,sectionNo,sectionTitle,sectionBody.toString());

        // 构建 TokenTextSplitter
        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(400) // 每个片段的目标大小约为 400 Token
                .withMinChunkSizeChars(100) // 标点或换行的断点位置超过这个字符门槛时，才优先在那里切开
                .withMinChunkLengthToEmbed(0) // 保留非空的短片段，避免丢掉很短的尾部正文
                .build();

        // 接收最终值用于返回
        List<Document> finalChunks = new ArrayList<>();

        // 补片段身份
        for (Document section : sections) {
            int chunkNo = 1;
            // 切分当前章节，让切出来的片段也带上 metadata
            List<Document> chunks = splitter.split(List.of(section));

            for(Document chunk : chunks){
                // 从片段中取出来复制成自己的 map
                Map<String,Object> chunkMetadata = new HashMap<>(chunk.getMetadata());

                // 当前片段的业务编号
                String chunkId = spec.getDocumentId()
                        + "_" + spec.getVersion()
                        + "_" + chunkMetadata.get("section_id")
                        + "_c" + chunkNo;

                // 根据业务编号生成稳定UUID
                String stableId = UUID.nameUUIDFromBytes(
                        chunkId.getBytes(StandardCharsets.UTF_8)
                ).toString();

                // 补充片段编号
                chunkMetadata.put("chunk_no",chunkNo);
                chunkMetadata.put("chunk_id",chunkId);

                // 交给 Document
                Document finalDocument = chunk.mutate()
                        .id(stableId)
                        .metadata(chunkMetadata)
                        .text(chunkMetadata.get("section_title") + "\n"  + chunk.getText())
                        .build();
                // 放入最终返回的结果列表
                finalChunks.add(finalDocument);

                chunkNo++;
            }
        }
        return finalChunks;
    }

    private void addSection(List<Document> sections, Document document,int sectionNo,String title,String body){
        // 检查正文、复制metadata、补章节信息、组装Document

        // 1.检查 body
        if (body == null || body.isBlank()){
            throw new IllegalArgumentException("正文内容不能为空");
        }

        // 创建独立元数据
        Map<String,Object> map = new HashMap<>(document.getMetadata());
        map.put("section_id","s" + sectionNo);
        map.put("section_title",title);

        // 将正文和元数据组装成章节 Document，加入 sections
        Document section = Document.builder()
                .text(body) // 设置章节正文
                .metadata(map) // 设置章节元数据
                .build();
        sections.add(section);
    }

}
