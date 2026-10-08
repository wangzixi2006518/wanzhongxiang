package com.wanzhongxiang.service;

import com.wanzhongxiang.rag.RuleRetrievalContext;
import reactor.core.publisher.Flux;

public interface AiChatService {

    String AiChat(String message);

    Flux<String> AiChatFlux(String message, String conversationId, Long employeeId, RuleRetrievalContext retrievalContext);

    void clearConversationMemory(String conversationId);

}
