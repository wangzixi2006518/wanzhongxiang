package com.wanzhongxiang.mapper;

import com.github.pagehelper.Page;
import com.wanzhongxiang.dto.GoodsSalesDTO;
import com.wanzhongxiang.dto.OrdersPageQueryDTO;
import com.wanzhongxiang.entity.Orders;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface OrderMapper {

    // 插入订单数据
    void insert(Orders orders);
    /**
     * 根据订单号查询订单
     * @param orderNumber
     */
    @Select("select * from orders where number = #{orderNumber}")
    Orders getByNumber(String orderNumber);

    /**
     * 修改订单信息
     * @param orders
     */
    void update(Orders orders);

    // 分页查询
    Page<Orders> pageQuery(OrdersPageQueryDTO ordersPageQueryDTO);

    // 根据 id 查询订单
    @Select("select * from orders where id = #{id}")
    Orders getById(Long id);

    // 根据状态，分别查询出待接单、待派送、派送中的订单数量
    @Select("select count(id) from orders where status = #{status}")
    Integer countStatus(Integer toBeConfirmed);

    // 将超时的订单查出来
    @Select("select * from orders where status = #{status} and order_time < #{orderTime}")
    List<Orders> getByStatusAndOrderTimeLT(Integer status, LocalDateTime orderTime);

    // 根据动态条件统计交易金额
    Double sumByMap(Map map);

    //  根据动态条件统计订单数量
    Integer countByMap(Map map);

    // 销量排名 top10
    List<GoodsSalesDTO> getSalesTop10(LocalDateTime begin,LocalDateTime end);

}
