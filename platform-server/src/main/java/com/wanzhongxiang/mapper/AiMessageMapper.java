package com.wanzhongxiang.mapper;

import com.wanzhongxiang.entity.AiMessage;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AiMessageMapper {

    // 向数据库会话中插入一段消息
    @Insert("insert into ai_message(id ,conversation_id,role,content, status, create_time, update_time)  " +
            "values (#{id},#{conversationId},#{role},#{content},#{status},#{createTime},#{updateTime})")
    int insert(AiMessage aiMessage);

    // 更新同一消息的最终状态
    @Update("update ai_message set content = #{content},status = #{status},update_time = #{updateTime}  " +
            "where id = #{messageId} and conversation_id = #{conversationId} and role = 'ASSISTANT' and status = 'GENERATING'")
    int finishAssistant(String messageId, String conversationId, String content, String status, LocalDateTime updateTime);

    // 只查询当前管理员拥有的对话记录
    @Select("select m.* from ai_message m inner join ai_conversation c on c.id = m.conversation_id  " +
            "where m.conversation_id = #{conversationId} and c.employee_id = #{employeeId} ORDER BY m.seq ASC")
    List<AiMessage> listOwnedByConversationId(String conversationId, Long employeeId);
}
