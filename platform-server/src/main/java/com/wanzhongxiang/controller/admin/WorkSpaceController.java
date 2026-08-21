package com.wanzhongxiang.controller.admin;

import com.wanzhongxiang.result.Result;
import com.wanzhongxiang.service.WorkspaceService;
import com.wanzhongxiang.vo.BusinessDataVO;
import com.wanzhongxiang.vo.DishOverViewVO;
import com.wanzhongxiang.vo.OrderOverViewVO;
import com.wanzhongxiang.vo.SetmealOverViewVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 运营中心
 */
@RestController
@RequestMapping("/admin/operations")
@Slf4j
@Api(tags = "运营中心相关接口")
public class WorkSpaceController {

    @Autowired
    private WorkspaceService workspaceService;

    /**
     * 运营中心今日数据查询
     * @return
     */
    @GetMapping("/businessData")
    @ApiOperation("运营中心今日数据查询")
    public Result<BusinessDataVO> businessData(){
        //获得当天的开始时间
        LocalDateTime begin = LocalDateTime.now().with(LocalTime.MIN);
        //获得当天的结束时间
        LocalDateTime end = LocalDateTime.now().with(LocalTime.MAX);

        BusinessDataVO businessDataVO = workspaceService.getBusinessData(begin, end);
        return Result.success(businessDataVO);
    }

    /**
     * 查询订单管理数据
     * @return
     */
    @GetMapping("/overviewOrders")
    @ApiOperation("查询订单管理数据")
    public Result<OrderOverViewVO> orderOverView(){
        return Result.success(workspaceService.getOrderOverView());
    }

    /**
     * 查询商品总览
     * @return
     */
    @GetMapping("/overviewDishes")
    @ApiOperation("查询商品总览")
    public Result<DishOverViewVO> dishOverView(){
        return Result.success(workspaceService.getDishOverView());
    }

    /**
     * 查询组合商品总览
     * @return
     */
    @GetMapping("/overviewSetmeals")
    @ApiOperation("查询组合商品总览")
    public Result<SetmealOverViewVO> setmealOverView(){
        return Result.success(workspaceService.getSetmealOverView());
    }
}
