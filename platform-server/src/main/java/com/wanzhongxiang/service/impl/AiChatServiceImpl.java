package com.wanzhongxiang.service.impl;

import com.wanzhongxiang.constant.AiPromptConstant;
import com.wanzhongxiang.service.AiChatService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class AiChatServiceImpl implements AiChatService {

    private final ChatClient chatClient;

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
    public Flux<String> AiChatFlux(String message) {
        return chatClient.prompt() // 准备处理请求
                .user(message) // 放入用户的问题
                .stream() // 请求模型
                .content(); // 取出回答
    }

}
