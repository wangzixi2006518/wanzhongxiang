package com.wanzhongxiang.rag;

import com.wanzhongxiang.constant.AiPromptConstant;
import com.wanzhongxiang.vo.RagAnswerVO;
import com.wanzhongxiang.vo.RagSourceVO;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class RagAnswerService {

    // 先把 RagRetrievalService 声明成成员变量，然后在构造方法里赋值给它
    @Autowired
    private RagRetrievalService ragRetrievalService;

    private final ChatClient chatClient;

    // 准备依赖和聊天客户端
    // 执行时机：Spring 创建 Service Bean 时
    public RagAnswerService(ChatClient.Builder builder,RagRetrievalService ragRetrievalService){
        // 把检索 Service 保存到字段
        this.ragRetrievalService = ragRetrievalService;
        // 用 builder 设置默认规则问答 System Prompt，再 build() 得到 ChatClient
//        chatClient = builder.defaultSystem(AiPromptConstant.RAG_SYSTEM_PROMPT).build();
        DeepSeekChatOptions build = DeepSeekChatOptions.builder().temperature(0.0).build();
        chatClient = builder.defaultSystem(AiPromptConstant.RAG_SYSTEM_PROMPT).defaultOptions(build).build();
    }

    // 把候选整理成模型能阅读的资料，把资料整理给模型看
    public String buildContext(List<Document> documents){
        // 用一个局部 StringBuilder 遍历候选
        StringBuilder stringBuilder = new StringBuilder();

        for (Document document : documents) {
//            stringBuilder.append("【规则片段】");
//            stringBuilder.append("文档名称：").append(document.getMetadata().get("document_title"));
//            stringBuilder.append("文档编号：").append(document.getMetadata().get("document_id"));
//            stringBuilder.append("版本：").append(document.getMetadata().get("version"));
//            stringBuilder.append("片段编号：").append(document.getMetadata().get("chunk_id"));
//            // 缺失来源字段或正文为空时，明确抛出 IllegalStateException，避免输出伪造来源
//            stringBuilder.append("正文：");
//            StringBuilder textNum = stringBuilder.append(document.getText());
//            if(textNum.isEmpty()){
//                throw new IllegalStateException("必要来源字段缺失或正文为空");
//            }
//            stringBuilder.append("【规则片段结束】");

            // 先校验再 append
            // 1. 先取得正文，并检查 null / 空白
            String text = document.getText();
            if (text == null || text.isBlank()) {
                throw new IllegalStateException("必要来源字段缺失或正文为空");
            }

            // 2. 取得 metadata
            Map<String, Object> metadata = document.getMetadata();
            Object documentTitle = metadata.get("document_title");
            Object documentId = metadata.get("document_id");
            Object version = metadata.get("version");
            Object chunkId = metadata.get("chunk_id");

            // 检查 metadata 是否缺失或空白
            if (documentTitle == null || documentTitle.toString().isBlank()
                    || documentId == null || documentId.toString().isBlank()
                    || version == null || version.toString().isBlank()
                    || chunkId == null || chunkId.toString().isBlank()) {
                throw new IllegalStateException("必要来源字段缺失或正文为空");
            }

            // 3. 全部校验通过以后，再开始拼接
            stringBuilder.append("【规则片段】\n");
            stringBuilder.append("文档名称：").append(documentTitle).append("\n");
            stringBuilder.append("文档编号：").append(documentId).append("\n");
            stringBuilder.append("版本：").append(version).append("\n");
            stringBuilder.append("片段编号：").append(chunkId).append("\n");
            stringBuilder.append("正文：").append(text).append("\n");
            stringBuilder.append("【规则片段结束】\n");
        }

        // 返回给大模型
        return stringBuilder.toString();
    }

    // 串起规则问答
    public String answer(String question){
//        // 1.调用已有 search(question) 复用它的空问题、未建立索引检查
//        List<Document> search = ragRetrievalService.search(question);
//
//        // 2.如果为空直接返回
//        if(search == null || search.isEmpty()){
//            return "当前检索资料不足以回答该问题。";
//        }
//
//        // 3.有候选就调用 buildContext
//        String context = buildContext(search);
//
//        // 4.组成 userContent，分成“管理员问题”和“本轮参考资料”两部分，分别放入原问题和 context
//        StringBuilder userContent = new StringBuilder();
//        userContent.append("管理员问题：").append(question);
//        userContent.append("本轮参考资料").append(context);
//        String userContentString = userContent.toString();
//
//        // 5.请求聊天模型取得回答
//        String chatClientContent = chatClient.prompt()
//                .user(userContentString)
//                .call()
//                .content();
//
//        // 6.判断是否为空并返回
//        if (chatClientContent == null || chatClientContent.isBlank()){
//            throw new IllegalStateException("未得到有效模型文本");
//        }
//        return chatClientContent;

        RagAnswerVO result = answerWithSources(question);
        return result.getAnswer();
    }

    // 输入管理员问题，输出 RagAnswerVO
    public RagAnswerVO answerWithSources(String question){

        // 1.调用 search(question)，把本轮 documents 保存在局部变量
        List<Document> documents = ragRetrievalService.search(question);

        // 2.空候选直接返回资料不足的 answer 和空 sources，不请求大模型
        if(documents == null || documents.isEmpty()){
            return new RagAnswerVO( "当前检索资料不足以回答该问题。",List.of());
        }

        // 3.非空候选用这批 documents 构建 context 并取得模型回答
        String context = buildContext(documents);
        String answer = chatClient.prompt()
                .user("""
                        请根据下面的检索资料回答问题。
                        问题：
                        %s

                        检索资料：
                        %s
                        """.formatted(question,context))
                .call()
                .content();

        // 4.模型返回 null 或空白时，继续抛“未得到有效模型文本”异常
        if(answer == null || answer.isBlank()){
            throw new IllegalStateException("未得到有效模型文本");
        }

        // 5.若回答 strip() 后等于约定的资料不足提示，返回标准提示和空 sources，即使本轮有检索候选
        if("当前检索资料不足以回答该问题。".equals(answer.strip())){
            // 返回一个 RagAnswerVO：
            // answer 设置为标准的资料不足提示
            // sources 设置为 List.of()，即空列表
            RagAnswerVO ragAnswerVO = new RagAnswerVO("当前检索资料不足以回答该问题。",List.of());
            return ragAnswerVO;
        }

        //6.正常回答调用 resolveSources(answer, documents)，用经核对的来源组装 RagAnswerVO
        List<RagSourceVO> ragSourceVOS = resolveSources(answer, documents);
        RagAnswerVO ragAnswerVO = new RagAnswerVO(answer,ragSourceVOS);
        return ragAnswerVO;
    }

    // 把编号查回真实资料
    public List<RagSourceVO> resolveSources(String answer, List<Document> documents){
        //模型回答里说自己引用了哪些 chunk_id，后端去本轮 documents 里验证这些 chunk_id 是不是真的存在，只返回真正被引用的来源
        // 主要是：提取编号 → 查到资料 → 转成 VO

        // 1.提取规定格式的引用编号，按首次出现顺序去重，所以用 Set
        Set<String> citedChunkIds = new LinkedHashSet<>();
        // 用正则表达式从 answer 中提取 wx_order_rules_v1-20261002_chunk_
        Pattern pattern = Pattern.compile("【来源：([^】\\r\\n]+)】");
        Matcher matcher = pattern.matcher(answer); // 去 answer 里面找
        while (matcher.find()){
            citedChunkIds.add(matcher.group(1).strip()); // 把里面的 chunk_id 拿出来
        }

        // 2. 正常回答没有任何来源标记
        if (citedChunkIds.isEmpty()){
            throw new IllegalStateException("规则回答缺少来源标记");
        }

        // 3.保存最终核对通过的来源
        List<RagSourceVO> sources = new ArrayList<>();
        // 遍历模型实际引用的 chunk_id
        for (String citedChunkId : citedChunkIds) {
            Document matchedDocument = null; // 初始化一个 matchedDocument

            for (Document document : documents) {
                // 当前这个 document 自己 metadata 里的 chunk_id 值
                String chunkId = String.valueOf(document.getMetadata().get("chunk_id"));

                // 找到模型引用的 chunkId，这一轮不用继续找了
                if(citedChunkId.equals(chunkId)){
                    matchedDocument = document;
                    break;
                }
            }

            // 没有找到，说明模型引用了不存在的片段
            if(matchedDocument == null){
                throw new IllegalStateException("模型引用了本轮未提供的片段");
            }
            // 把 matchedDocument 转成 RagSourceVO
            RagSourceVO source = new RagSourceVO();
            source.setDocumentId(String.valueOf(matchedDocument.getMetadata().get("document_id")));
            source.setDocumentTitle(String.valueOf(matchedDocument.getMetadata().get("document_title")));
            source.setVersion(String.valueOf(matchedDocument.getMetadata().get("version")));
            source.setChunkId(String.valueOf(matchedDocument.getMetadata().get("chunk_id")));
            source.setText(matchedDocument.getText());
            // 加入到集合
            sources.add(source);
        }
        return sources;
    }



}
