package com.wanzhongxiang.controller.admin;

import com.wanzhongxiang.result.Result;
import com.wanzhongxiang.service.ReportService;
import com.wanzhongxiang.vo.*;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDate;

// 数据统计相关接口
@RestController
@RequestMapping("/admin/report")
@Api(tags = "数据统计相关接口")
@Slf4j
public class ReportController {

    @Autowired
    private ReportService reportService;

    @GetMapping("/transactionStatistics")
    @ApiOperation("交易金额统计")
    public Result<TurnoverReportVO>  turnoverStatistics(
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate begin,
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end){
        log.info("交易金额数据统计：{}，{}",begin,end);
//        TurnoverReportVO reportVO = reportService.getTurnoverStatistics(begin,end);
//        return Result.success(reportVO);
        return Result.success(reportService.getTurnoverStatistics(begin,end));
    }

    @GetMapping("/userStatistics")
    @ApiOperation("用户统计")
    public Result<UserReportVO> userStatistics(
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate begin,
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end
    ){
        log.info("用户数据统计：{}，{}",begin,end);
        return Result.success(reportService.getUserStatistics(begin,end));
    }

    @GetMapping("/ordersStatistics")
    @ApiOperation("订单统计")
    public Result<OrderReportVO> ordersStatistics(
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate begin,
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end
    ){
        log.info("订单数据统计：{}，{}",begin,end);
        return Result.success(reportService.getOrderStatistics(begin,end));
    }

    @GetMapping("/top10")
    @ApiOperation("销量排名 top10")
    public Result<SalesTop10ReportVO> top10(
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate begin,
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end
    ){
        log.info("销量排名 top10：{}，{}",begin,end);
        return Result.success(reportService.getSalesTop10(begin,end));
    }

    @GetMapping("/export")
    @ApiOperation("导出运营数据报表")
    public void export(HttpServletResponse response){
        reportService.exportBusinessData(response);
    }

    @GetMapping("/businessComparison")
    public Result<BusinessComparisonVO> businessComparison(
            @RequestParam("currentBegin")
            // 日期格式转换注解
            // 告诉 Spring MVC：将前端传来的日期字符串按照 yyyy-MM-dd 格式解析成 LocalDate 对象
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate currentBegin,
            @RequestParam("currentEnd")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate currentEnd,
            @RequestParam("baselineBegin")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate baselineBegin,
            @RequestParam("baselineEnd")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate baselineEnd
            ){
        BusinessComparisonVO businessComparisonVO = reportService.compareBusiness(currentBegin, currentEnd, baselineBegin, baselineEnd);
        return Result.success(businessComparisonVO);
    }


}
