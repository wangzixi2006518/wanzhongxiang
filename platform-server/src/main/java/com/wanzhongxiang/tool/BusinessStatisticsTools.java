package com.wanzhongxiang.tool;

import com.wanzhongxiang.service.WorkspaceService;
import com.wanzhongxiang.vo.BusinessDataVO;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;

@Component
public class BusinessStatisticsTools {

    @Autowired
    private WorkspaceService workspaceService;

    @Tool(name = "query_yesterday_turnover", description = "万众享昨天已完成订单的营业额")
    public String findYesterday() {

        // 昨天的日期
        LocalDate yesterday = LocalDate.now().minusDays(1);
        // 昨天开始日期
        LocalDateTime begin = LocalDateTime.of(yesterday, LocalTime.MIN);
        // 昨天结束日期
        LocalDateTime end = LocalDateTime.of(yesterday, LocalTime.MAX);

        // 获取昨天营业额
        BusinessDataVO businessData = workspaceService.getBusinessData(begin, end);
        String turnover = businessData.getTurnover().toString();
        // 获取昨天已完成订单数
        Integer validOrderCount = businessData.getValidOrderCount();

        if (validOrderCount == 0) {
            return yesterday + "：没有已完成订单，营业额为 " + turnover + " 元";
        }
        // 返回给大模型
        return yesterday + "：已完成订单 " + validOrderCount + " 笔，营业额为 " + turnover + " 元";
    }

    @Tool(name = "query_turnover_by_date", description = "当用户明确指定某个过去日期时，查询该日已完成订单的笔数和营业额；如果用户查询昨天，优先使用查询昨天营业额的工具")
    public String queryTurnoverByDate(@ToolParam(description = "日期，格式 yyyy-MM-dd，例如 2026-09-28") String dateText) {
        // 判断是否合法
        if (dateText == null || dateText.isBlank()) {
            return "日期无效，请提供 yyyy-MM-dd 格式的真实日期";
        }

        LocalDate date;
        try {
            // 解析指定日期
            date = LocalDate.parse(dateText);
        } catch (DateTimeParseException e) {
            return "日期无效，请提供 yyyy-MM-dd 格式的真实日期";
        }

        if (!date.isBefore(LocalDate.now())) {
            return "只能查询今天之前的完整日期";
        }

        // 当天开始日期
        LocalDateTime begin = LocalDateTime.of(date, LocalTime.MIN);
        // 当天结束日期
        LocalDateTime end = LocalDateTime.of(date, LocalTime.MAX);

        // 查询营业数据
        BusinessDataVO businessData = workspaceService.getBusinessData(begin, end);
        // 获取已完成订单数
        Integer validOrderCount = businessData.getValidOrderCount();
        // 获取营业额
        String turnover = businessData.getTurnover().toString();

        if (validOrderCount == 0) {
            return date + "：没有已完成订单，营业额为 " + turnover + " 元";
        }

        // 把结果返回给模型
        return date + "：已完成订单 " + validOrderCount + " 笔，营业额为 " + turnover + " 元";
    }

}
