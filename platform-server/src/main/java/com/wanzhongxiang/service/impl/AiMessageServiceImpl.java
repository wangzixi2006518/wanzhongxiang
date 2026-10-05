package com.wanzhongxiang.service.impl;

import com.wanzhongxiang.constant.MessageConstant;
import com.wanzhongxiang.entity.AiConversation;
import com.wanzhongxiang.entity.AiMessage;
import com.wanzhongxiang.exception.AiConversationBusyException;
import com.wanzhongxiang.exception.AiConversationNotFoundException;
import com.wanzhongxiang.mapper.AiConversationMapper;
import com.wanzhongxiang.mapper.AiMessageMapper;
import com.wanzhongxiang.service.AiConversationService;
import com.wanzhongxiang.service.AiMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AiMessageServiceImpl implements AiMessageService {

    @Autowired
    private AiConversationMapper aiConversationMapper;
    @Autowired
    private AiMessageMapper aiMessageMapper;
    @Autowired
    private AiConversationService aiConversationService;

    // 查询当前管理员的会话历史
    @Override
    public List<AiMessage> listOwnedByConversationId(String conversationId, Long employeeId) {

        // 检查当前会话归属
        // 用会话 id + 当前登录管理员 id 一起查询，才能防止其他人拿到会话信息
        AiConversation ownedById = aiConversationMapper.getOwnedById(conversationId, employeeId);
        if(ownedById == null){
            throw new AiConversationNotFoundException("会话不存在");
        }

        // 调用获取消息列表方法获得消息
        List<AiMessage> aiMessages = aiMessageMapper.listOwnedByConversationId(conversationId, employeeId);

        return aiMessages;
    }

    // 从数据库只读出完整轮次
    @Override
    public List<AiMessage> listTurnByConversationId(String conversationId, Long employeeId) {

        // 检查当前会话归属
        // 用会话 id + 当前登录管理员 id 一起查询，才能防止其他人拿到会话信息
        AiConversation ownedById = aiConversationMapper.getOwnedById(conversationId, employeeId);
        if(ownedById == null){
            throw new AiConversationNotFoundException("会话不存在");
        }

        // 调用获取消息列表方法获得消息
        List<AiMessage> aiMessages = aiMessageMapper.listTurnByConversationId(conversationId, employeeId);

        return aiMessages;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AiConversation beginChat(Long employeeId, String requestedConversationId, String question, String assistantMessageId) {
        // 判断是否是第一次对话
        AiConversation conversation = aiConversationService.resolveForChat(requestedConversationId, employeeId);

        // 按当前管理员归属读取并锁定会话行，保护后续的检查与插入
        AiConversation ownedByIdForUpdate = aiConversationMapper.getOwnedByIdForUpdate(conversation.getId(), employeeId);
        if(ownedByIdForUpdate == null){
            throw new AiConversationNotFoundException("会话不存在");
        }

        // 只统计该会话 role=ASSISTANT 且 status=GENERATING 的消息
        int countGeneratingAssistant = aiMessageMapper.countGeneratingAssistant(conversation.getId());
        if(countGeneratingAssistant > 0){
            throw new AiConversationBusyException("该会话正在生成，请等待结束后再发送");
        }

        // 开启对话时先插入两条信息，一条用户的问题，另一条助手的占位消息（不包含内容）
        String userId = "msg_" + UUID.randomUUID().toString();
        String conversationId = conversation.getId();
        LocalDateTime now = LocalDateTime.now();
        // 用户行
        AiMessage aiMessageUser = new AiMessage();
        aiMessageUser.setId(userId);
        aiMessageUser.setConversationId(conversationId);
        aiMessageUser.setRole("USER");
        aiMessageUser.setContent(question);
        aiMessageUser.setStatus("COMPLETED");
        aiMessageUser.setCreateTime(now);
        aiMessageUser.setUpdateTime(now);
        aiMessageUser.setTurnId(assistantMessageId);

        // 助手行
        AiMessage aiMessageAssistant = new AiMessage();
        aiMessageAssistant.setId(assistantMessageId);
        aiMessageAssistant.setConversationId(conversationId);
        aiMessageAssistant.setRole("ASSISTANT");
        aiMessageAssistant.setContent("");
        aiMessageAssistant.setStatus("GENERATING");
        aiMessageAssistant.setCreateTime(now);
        aiMessageAssistant.setUpdateTime(now);
        aiMessageAssistant.setTurnId(assistantMessageId);

        // 插入数据库
        // 用户
        int insertUser = aiMessageMapper.insert(aiMessageUser);
        if(insertUser != 1){
            throw new IllegalStateException("用户消息写入失败");
        }
        // 助手
        int insertAssistant = aiMessageMapper.insert(aiMessageAssistant);
        if(insertAssistant != 1){
            throw new IllegalStateException("助手消息写入失败");
        }

        return conversation;
    }

    // 更新之前插入的助手占位消息的内容和状态
    @Override
    public void completeAssistant(String messageId, String conversationId, String content) {
        int completed = aiMessageMapper.finishAssistant(messageId, conversationId, content, "COMPLETED", LocalDateTime.now());
        if(completed != 1){
            throw new IllegalStateException("助手消息正常完成更新失败");
        }
    }

    @Override
    public void failAssistant(String messageId, String conversationId, String content) {
        int failed = aiMessageMapper.finishAssistant(messageId, conversationId, content, "FAILED", LocalDateTime.now());
        if(failed != 1){
            throw new IllegalStateException("助手消息失败状态更新失败");
        }
    }

    @Override
    public void cancelAssistant(String messageId, String conversationId, String content) {
        int cancelled = aiMessageMapper.finishAssistant(messageId, conversationId, content, "CANCELLED", LocalDateTime.now());
        if(cancelled != 1){
            throw new IllegalStateException("取消终态更新失败");
        }
    }

}
