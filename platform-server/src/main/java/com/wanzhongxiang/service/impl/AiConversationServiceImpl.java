package com.wanzhongxiang.service.impl;

import com.wanzhongxiang.constant.MessageConstant;
import com.wanzhongxiang.entity.AiConversation;
import com.wanzhongxiang.exception.AccountNotFoundException;
import com.wanzhongxiang.exception.AiConversationNotFoundException;
import com.wanzhongxiang.mapper.AiConversationMapper;
import com.wanzhongxiang.result.Result;
import com.wanzhongxiang.service.AiConversationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class AiConversationServiceImpl implements AiConversationService {

    @Autowired
    private AiConversationMapper aiConversationMapper;

    @Override
    public AiConversation create(Long employeeId, String title) {

        AiConversation aiConversation = new AiConversation();
        aiConversation.setEmployeeId(employeeId);

        String id = "conv_" + UUID.randomUUID().toString(); // 每个会话的 id
        aiConversation.setId(id);

        String titleName;
        if(title == null || title.isBlank()){
            titleName = "新会话";
        }else {
            titleName = title.trim(); // 去掉首尾多余空格
        }
        aiConversation.setTitle(titleName);

        LocalDateTime now = LocalDateTime.now();
        aiConversation.setCreateTime(now);
        aiConversation.setUpdateTime(now);

        // 新增会话
        int insert = aiConversationMapper.insert(aiConversation);
        // 如果新增会话失败返回错误
        if(insert != 1){
            throw new RuntimeException("新增会话失败");
        }

        // 新增成功
        return aiConversation;
    }

    @Override
    public List<AiConversation> listByEmployeeId(Long employeeId) {
        // 调用对话列表并返回
        List<AiConversation> aiConversations = aiConversationMapper.listByEmployeeId(employeeId);
        return aiConversations;
    }

    @Override
    public AiConversation resolveForChat(String conversationId,Long employeeId) {
        // 判断当前对话id是否为空（首次为空）
        if(conversationId == null || conversationId.isBlank()){
            return create(employeeId, null);
        }
        // 不为空判断是否存在
        AiConversation ownedById = aiConversationMapper.getOwnedById(conversationId, employeeId);
        if(ownedById == null){
            throw new AiConversationNotFoundException("会话不存在");
        }
        // 存在直接返回
        return ownedById;
    }

    @Override
    public void deleteOwnedById(String conversationId, Long employeeId) {
        int deleted = aiConversationMapper.deleteOwnedById(conversationId, employeeId);
        if(deleted == 0){
            throw new AiConversationNotFoundException("会话不存在");
        }
    }

}
