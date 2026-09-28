package com.wanzhongxiang.mapper;

import com.wanzhongxiang.entity.AiConversation;
import com.wanzhongxiang.entity.AiMessage;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AiConversationMapper {

    // 向数据库插入一段会话
    @Insert("insert into ai_conversation(id,employee_id,title,create_time,update_time) " +
            "values (#{id},#{employeeId},#{title},#{createTime},#{updateTime})")
    int insert(AiConversation aiConversation);

    @Select("select * from ai_conversation where id = #{conversationId} and employee_id = #{employeeId}")
    AiConversation getOwnedById(String conversationId,Long employeeId);

    @Select("select * from ai_conversation where employee_id = #{employeeId} order by update_time desc,id desc")
    List<AiConversation> listByEmployeeId(Long employeeId);

    @Delete("delete from ai_conversation where id = #{conversationId} and employee_id = #{employeeId}")
    int deleteOwnedById(String conversationId, Long employeeId);

}
