package com.wanzhongxiang.mapper;

import com.wanzhongxiang.entity.OrderDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OrderDetailMapper {

    // 批量插入订单明细数据
    void insertBatch(List<OrderDetail> orderDetailList);

    // 查询订单详情
    @Select("select * from order_detail where order_id = #{orderId}")
    List<OrderDetail> getByOrderId(Long ordersId);
}
