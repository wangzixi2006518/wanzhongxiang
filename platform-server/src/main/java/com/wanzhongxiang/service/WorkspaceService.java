package com.wanzhongxiang.service;

import com.wanzhongxiang.vo.BusinessDataVO;
import com.wanzhongxiang.vo.DishOverViewVO;
import com.wanzhongxiang.vo.OrderOverViewVO;
import com.wanzhongxiang.vo.SetmealOverViewVO;
import java.time.LocalDateTime;

public interface WorkspaceService {

    /**
     * 根据时间段统计营业数据
     * @param begin
     * @param end
     * @return
     */
    BusinessDataVO getBusinessData(LocalDateTime begin, LocalDateTime end);

    /**
     * 查询订单管理数据
     * @return
     */
    OrderOverViewVO getOrderOverView();

    /**
     * 查询商品总览
     * @return
     */
    DishOverViewVO getDishOverView();

    /**
     * 查询组合商品总览
     * @return
     */
    SetmealOverViewVO getSetmealOverView();

}
