package com.wanzhongxiang.service.impl;

import com.wanzhongxiang.constant.AiPromptConstant;
import com.wanzhongxiang.entity.AiMessage;
import com.wanzhongxiang.rag.RuleRetrievalContext;
import com.wanzhongxiang.service.AiChatService;
import com.wanzhongxiang.service.AiMessageService;
import com.wanzhongxiang.tool.BusinessStatisticsTools;
import com.wanzhongxiang.tool.RuleRetrievalTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@Service
public class AiChatServiceImpl implements AiChatService {

    @Autowired
    private AiMessageService aiMessageService;
    @Autowired
    private BusinessStatisticsTools businessStatisticsTools;
    @Autowired
    private RuleRetrievalTools ruleRetrievalTools;

    private final ChatClient chatClient;
    private final ChatMemory chatMemory = // 负责存，保存不同会话
            MessageWindowChatMemory.builder()
                    .maxMessages(10)
                    .build();
    private final Advisor chatMemoryAdvisor = // 负责用，在请求模型前后操作 chatMemory
            MessageChatMemoryAdvisor.builder(chatMemory)
                    .build();

    public AiChatServiceImpl(ChatClient.Builder builder) {
        chatClient = builder
                .defaultSystem(AiPromptConstant.SYSTEM_PROMPT)
                .build();
    }

    @Override
    public String AiChat(String message) {
        return chatClient.prompt() // 准备处理请求
                .user(message) // 放入用户的问题
                .call() // 请求模型
                .content(); // 取出回答
    }

    @Override
    public Flux<String> AiChatFlux(String message, String conversationId, Long employeeId, RuleRetrievalContext retrievalContext) {
        // 从数据库查询已经完整完成的历史轮次
        List<AiMessage> aiMessages = aiMessageService.listTurnByConversationId(conversationId, employeeId);

        // 最多取最后 8 条，并转换成 Spring AI 能认识的消息
        List<Message> historyMessages = aiMessages
                .stream()
                .skip(Math.max(0, aiMessages.size() - 8)) // 只保留最后 8 条消息
                // AiMessage -> Spring AI 的 Message
                .map(aiMessage -> {
                    // 类型转换
                    if ("USER".equals(aiMessage.getRole())) {
                        return (Message) new UserMessage(aiMessage.getContent());
                    }
                    if ("ASSISTANT".equals(aiMessage.getRole())) {
                        return (Message) new AssistantMessage(aiMessage.getContent());
                    }

                    throw new IllegalStateException("未知角色：" + aiMessage.getRole());
                })
                .toList();

        // 更新上下文记忆：先删除再添加
        chatMemory.clear(conversationId);
        chatMemory.add(conversationId, historyMessages);

        return chatClient.prompt() // 准备处理请求
                .user(message) // 放入用户的问题
                .advisors(advisorSpec -> // 调用 chatMemoryAdvisor 根据 conversationId 去 chatMemory 查历史
                        advisorSpec.advisors(chatMemoryAdvisor) // 调用 chatMemoryAdvisor
                        .param(ChatMemory.CONVERSATION_ID, conversationId) // 这次请求属于 conversationId 这个会话
                )
                .tools(businessStatisticsTools,ruleRetrievalTools)
                .toolContext(Map.of( // Controller 创建记录，Service 传递记录
                        RuleRetrievalContext.CONTEXT_KEY, // 查找用的键
                        retrievalContext // Controller 创建的记录对象
                ))
                .stream() // 请求模型
                .content(); // 取出回答
    }

    @Override
    public void clearConversationMemory(String conversationId) {
        // 删除会话时清理 Memory
        chatMemory.clear(conversationId);
    }

}
