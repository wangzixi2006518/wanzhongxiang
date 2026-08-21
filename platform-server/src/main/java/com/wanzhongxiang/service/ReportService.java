package com.wanzhongxiang.service;

import com.wanzhongxiang.vo.OrderReportVO;
import com.wanzhongxiang.vo.SalesTop10ReportVO;
import com.wanzhongxiang.vo.TurnoverReportVO;
import com.wanzhongxiang.vo.UserReportVO;

import javax.servlet.http.HttpServletResponse;
import java.time.LocalDate;

public interface ReportService {

    // 统计指定区间内的交易金额数据
    TurnoverReportVO getTurnoverStatistics(LocalDate begin,LocalDate end);

    // 统计指定区间内的用户数据
    UserReportVO getUserStatistics(LocalDate begin, LocalDate end);

    // 统计指定区间内的订单数据
    OrderReportVO getOrderStatistics(LocalDate begin, LocalDate end);

    // 销量排名 top10
    SalesTop10ReportVO getSalesTop10(LocalDate begin, LocalDate end);

    // 导出运营数据报表
    void exportBusinessData(HttpServletResponse response);
}
