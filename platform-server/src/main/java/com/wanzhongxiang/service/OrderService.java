package com.wanzhongxiang.service;

import com.wanzhongxiang.dto.*;
import com.wanzhongxiang.result.PageResult;
import com.wanzhongxiang.vo.OrderPaymentVO;
import com.wanzhongxiang.vo.OrderStatisticsVO;
import com.wanzhongxiang.vo.OrderSubmitVO;
import com.wanzhongxiang.vo.OrderVO;

public interface OrderService {

    // 用户下单
    OrderSubmitVO submitService(OrdersSubmitDTO ordersSubmitDTO);

    // 订单支付
    OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception;

    // 支付成功，修改状态
    void paySuccess(String outTradeNo);

    // 查询历史订单
    PageResult pageQueryUser(int page, int pageSize, Integer status);

    // 查询订单详情
    OrderVO details(Long id);

    // 取消顶顶那
    void userCancelById(Long id) throws Exception;

    // 再来一单
    void repetition(Long id);

    //商家端订单搜索
    PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO);

    // 各个状态的订单数量统计
    OrderStatisticsVO statistics();

    // 接单
    void confirm(OrdersConfirmDTO ordersConfirmDTO);

    // 拒单
    void rejection(OrdersRejectionDTO ordersRejectionDTO);

    // 取消订单
    void cancel(OrdersCancelDTO ordersCancelDTO);

    // 派送
    void delivery(Long id);

    // 完成订单
    void complete(Long id);

    // 催单
    void reminder(Long id);
}
