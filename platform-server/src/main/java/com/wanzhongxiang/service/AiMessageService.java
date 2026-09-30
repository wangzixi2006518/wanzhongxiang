package com.wanzhongxiang.service;

import com.wanzhongxiang.entity.AiConversation;
import com.wanzhongxiang.entity.AiMessage;

import java.util.List;

public interface AiMessageService {

    List<AiMessage> listOwnedByConversationId(String conversationId,Long employeeId);

    List<AiMessage> listTurnByConversationId(String conversationId,Long employeeId);

    AiConversation beginChat(Long employeeId, String requestedConversationId, String question, String assistantMessageId);

    void completeAssistant(String messageId, String conversationId, String content);

    void failAssistant(String messageId, String conversationId, String content);

    void cancelAssistant(String messageId, String conversationId, String content);


}
