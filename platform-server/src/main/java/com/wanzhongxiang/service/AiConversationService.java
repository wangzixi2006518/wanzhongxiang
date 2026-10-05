package com.wanzhongxiang.service;

import com.wanzhongxiang.entity.AiConversation;

import java.util.List;

public interface AiConversationService {

    AiConversation create(Long employeeId, String title);

    List<AiConversation> listByEmployeeId(Long employeeId);

    AiConversation resolveForChat(String conversationId,Long employeeId);

    void deleteOwnedById(String conversationId, Long employeeId);

    AiConversation renameOwnedById(String conversationId, Long employeeId, String title);

}
