package com.wanzhongxiang.tool;

import com.wanzhongxiang.rag.RagAnswerService;
import com.wanzhongxiang.rag.RagRetrievalService;
import com.wanzhongxiang.rag.RuleRetrievalContext;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RuleRetrievalTools {

    @Autowired
    private RagRetrievalService ragRetrievalService;
    @Autowired
    private RagAnswerService ragAnswerService;

    @Tool(name = "search_platform_rules",description = "每次回答万众享平台规则问题时使用，包括对上一轮规则的追问；结合会话补全检索问题，返回本轮规则正文与来源信息。历史回答及旧引用不能替代本次检索。")
    public String searchPlatformRules(@ToolParam(description = "结合会话上下文补全后的完整规则问题") String query, ToolContext toolContext){
        // 创建规则检索工具，将 Rag 检索加入主聊天
        // 工具里只调用 search 和 buildContext 是因为现在主聊天本身就是大模型，已经能够生成答案了

        Object o = toolContext.getContext().get(RuleRetrievalContext.CONTEXT_KEY);

        // 检查类型转换
        if(!(o instanceof RuleRetrievalContext)){
            throw new IllegalStateException("规则检索上下文不存在或类型错误");
        }
        RuleRetrievalContext context = (RuleRetrievalContext) o;

        // 调用 search 方法
        // 放在检查类型转换后面，如果上下文有问题，就会直接抛异常，不会执行后续检索，节省资源
        List<Document> documents = ragRetrievalService.search(query);

        // 判断是否为 null
        if(documents == null || documents.isEmpty()){
            context.recordSearch(List.of()); // 装个空集合
            return "当前检索资料不足以回答该问题。";
        }

        // 有候选，先整理资料
        String result = ragAnswerService.buildContext(documents);
        context.recordSearch(documents);

        // 有候选正常返回
        return result;
    }


}
