package com.wanzhongxiang.task;

import com.wanzhongxiang.entity.Orders;
import com.wanzhongxiang.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

// 定时任务类，定时处理订单状态
@Component
@Slf4j
public class OrderTask {

    @Autowired
    private OrderMapper orderMapper;

    // 处理支付超时订单
    @Scheduled(cron = "0 * * * * ?") // 每分钟触发一次
//    @Scheduled(cron = "1/5 * * * * ?") // 测试
    public void processTimeoutOrder(){
        log.info("定时处理超时订单：{}", LocalDateTime.now());

        // 将超时的订单查出来
        // select * from orders where status = ? and order_time < ?(当前时间减去 15 分钟的时间)
        LocalDateTime time = LocalDateTime.now().plusMinutes(-15);
        List<Orders> ordersList = orderMapper.getByStatusAndOrderTimeLT(Orders.PENDING_PAYMENT, time);

        // 判断
        if (ordersList != null && ordersList.size() > 0){
            for (Orders orders : ordersList) {
                orders.setStatus(Orders.CANCELLED); // 更新为取消状态
                orders.setCancelReason("订单超时，自动取消");
                orders.setCancelTime(LocalDateTime.now());

                orderMapper.update(orders);
            }
        }
    }

    // 处理一直派送中的订单
    @Scheduled(cron = "0 0 1 * * ?") // 每天凌晨一点触发一次
//    @Scheduled(cron = "0/5 * * * * ?") // 测试
    public void processDeliveryOrder(){
        log.info("定时处理派送中订单：{}",LocalDateTime.now());

        LocalDateTime time = LocalDateTime.now().plusMinutes(-60);

        // 将上一个工作日一直处于派送中的订单查出来
        List<Orders> ordersList = orderMapper.getByStatusAndOrderTimeLT(Orders.DELIVERY_IN_PROGRESS, time);

        // 判断
        if (ordersList != null && ordersList.size() > 0) {
            for (Orders orders : ordersList) {
                orders.setStatus(Orders.COMPLETED); // 更新为已完成状态
                orderMapper.update(orders);
            }
        }
    }


}
