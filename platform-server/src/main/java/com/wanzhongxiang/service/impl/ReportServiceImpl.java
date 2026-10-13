package com.wanzhongxiang.service.impl;

import com.wanzhongxiang.dto.BusinessPeriodStatsDTO;
import com.wanzhongxiang.dto.GoodsSalesDTO;
import com.wanzhongxiang.entity.Orders;
import com.wanzhongxiang.exception.BusinessComparisonParameterException;
import com.wanzhongxiang.mapper.OrderMapper;
import com.wanzhongxiang.mapper.UserMapper;
import com.wanzhongxiang.service.ReportService;
import com.wanzhongxiang.service.WorkspaceService;
import com.wanzhongxiang.vo.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ReportServiceImpl implements ReportService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private WorkspaceService workspaceService;

    // 统计指定区间内的交易金额数据
    @Override
    public TurnoverReportVO getTurnoverStatistics(LocalDate begin, LocalDate end) {
        // 当前集合用于存放 begin 到 end 范围内的日期
        List<LocalDate> dateList = new ArrayList<>();
        dateList.add(begin);
        // 范围内的每一天加到 list
        while (!begin.equals(end)){
            // 计算指定日期的后 1 天对应的日期
            begin = begin.plusDays(1);
            dateList.add(begin);
        }

        List<Double> turnoverList = new ArrayList<>(); // 存放每日交易金额
        for (LocalDate date : dateList) {
            // 查询 date 日期对应的交易金额，交易金额 -> 状态为“已完成”的订单金额合计
            // 获取一天的开始与结束
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);

            // select sum(amount) from orders where order_time > ?(beginTime) and order_time < ?(endTime) and status = 5
            Map map = new HashMap<>();
            map.put("begin",beginTime);
            map.put("end",endTime);
            map.put("status", Orders.COMPLETED);
            Double turnover = orderMapper.sumByMap(map);
            turnover = turnover == null ? 0.0 : turnover;
            turnoverList.add(turnover);
        }

        // 封装返回结果数据
        return TurnoverReportVO.builder()
                .dateList(StringUtils.join(dateList,","))
                .turnoverList(StringUtils.join(turnoverList,","))
                .build();
    }

    // 统计指定区间内的用户数据
    @Override
    public UserReportVO getUserStatistics(LocalDate begin, LocalDate end) {
        // 存放 begin 到 end 每天对应的日期
        List<LocalDate> dateList = new ArrayList<>();
        dateList.add(begin);
        // 范围内的每一天加到 list
        while (!begin.equals(end)){
            begin = begin.plusDays(1);
            dateList.add(begin);
        }

        // 存每天新增用户数量
        // select count(id) from user where create_time < ? and create_time > ?
        List<Integer> newUserList = new ArrayList<>();
        // 存每天总用户数量
        // select count(id) from user where create_time < ?
        List<Integer> totalUserList  = new ArrayList<>();

        for (LocalDate date : dateList) {
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);

            Map map = new HashMap();
            map.put("end",endTime);
            // 总用户数量
            Integer totalUser = userMapper.countByMap(map);

            map.put("begin",beginTime);
            // 新增用户数量
            Integer newUser = userMapper.countByMap(map);

            totalUserList.add(totalUser);
            newUserList.add(newUser);
        }
        // 封装返回结果数据
        return UserReportVO.builder()
                .dateList(StringUtils.join(dateList,","))
                .totalUserList(StringUtils.join(totalUserList,","))
                .newUserList(StringUtils.join(newUserList,","))
                .build();
    }

    // 统计指定区间内的订单数据
    @Override
    public OrderReportVO getOrderStatistics(LocalDate begin, LocalDate end) {
        // 存放 begin 到 end 每天对应的日期
        List<LocalDate> dateList = new ArrayList<>();
        dateList.add(begin);
        // 范围内的每一天加到 list
        while (!begin.equals(end)){
            begin = begin.plusDays(1);
            dateList.add(begin);
        }

        // 存放每天订单总数
        List<Integer> orderCountList = new ArrayList<>();
        // 存放每天有效订单数
        List<Integer> validOrderCountList = new ArrayList<>();

        // 查询每一天的有效订单数和总数
        for (LocalDate date : dateList) {
            // 查询每天订单总数
            // select count(id) from orders where order_time > ? and order_time < ?
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);
            Integer orderCount = getOrderCount(beginTime, endTime, null);

            // 查询每天有效订单数
            // select count(id) from orders where order_time > ? and order_time < ? and status = ?
            Integer validOrderCount = getOrderCount(beginTime, endTime, Orders.COMPLETED);

            orderCountList.add(orderCount);
            validOrderCountList.add(validOrderCount);
        }
        // 计算时间区间内的订单总数
        Integer totalOrderCount = orderCountList.stream().reduce(Integer::sum).get();

        // 计算时间区间内的有效订单数
        Integer validOrderCount = validOrderCountList.stream().reduce(Integer::sum).get();

        // 计算订单完成率
        Double orderCompletionRate = 0.0;
        if(totalOrderCount != 0){
            orderCompletionRate = validOrderCount.doubleValue() / totalOrderCount;
        }
        // 封装到 VO 并返回
        return OrderReportVO.builder()
                .dateList(StringUtils.join(dateList,","))
                .orderCountList(StringUtils.join(orderCountList,","))
                .validOrderCountList(StringUtils.join(validOrderCountList,","))
                .totalOrderCount(totalOrderCount)
                .validOrderCount(validOrderCount)
                .orderCompletionRate(orderCompletionRate)
                .build();
    }

    // 销量排名 top10
    @Override
    public SalesTop10ReportVO getSalesTop10(LocalDate begin, LocalDate end) {
        LocalDateTime beginTime = LocalDateTime.of(begin, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MAX);

        // 获取 VO 中的属性 nameList numberList
        List<GoodsSalesDTO> salesTop10 = orderMapper.getSalesTop10(beginTime, endTime);
        List<String> names = salesTop10.stream().map(GoodsSalesDTO::getName).collect(Collectors.toList());
        String nameList = StringUtils.join(names, ",");
        List<Integer> numbers = salesTop10.stream().map(GoodsSalesDTO::getNumber).collect(Collectors.toList());
        String numberList = StringUtils.join(numbers, ",");

        // 封装返回结果数据
        return SalesTop10ReportVO.builder()
                .nameList(nameList)
                .numberList(numberList)
                .build();
    }

    // 导出运营数据报表
    @Override
    public void exportBusinessData(HttpServletResponse response) {
        // 1.查询数据库，获取营业数据---查询最近30天的运营数据
        LocalDate dateBegin = LocalDate.now().minusDays(30);
        LocalDate dateEnd = LocalDate.now().minusDays(1);
        // 类型处理  LocalDate -> LocalDateTime
        LocalDateTime begin = LocalDateTime.of(dateBegin, LocalTime.MIN);
        LocalDateTime end = LocalDateTime.of(dateBegin, LocalTime.MAX);
        // 查询概览数据
        BusinessDataVO businessDataVO = workspaceService.getBusinessData(begin, end);

        // 2.通过 POI 写入到 Excel 文件中
        InputStream in = this.getClass().getClassLoader().getResourceAsStream("template/运营数据报表模板.xlsx");
        try {
            // 基于模板文件创建新的 Excel 文件
            XSSFWorkbook excel = new XSSFWorkbook(in);
            // 获取 sheet 标签页
            XSSFSheet sheet = excel.getSheet("Sheet1");
            // 填充数据---时间
            // 获取第二行第二个单元格
            sheet.getRow(1).getCell(1).setCellValue("时间：" + dateBegin + "至" + dateEnd);
            // 获得第四行的数据
            XSSFRow row = sheet.getRow(3);
            row.getCell(2).setCellValue(businessDataVO.getTurnover());
            row.getCell(4).setCellValue(businessDataVO.getOrderCompletionRate());
            row.getCell(6).setCellValue(businessDataVO.getNewUsers());
            // 获得第五行的数据
            row = sheet.getRow(4);
            row.getCell(2).setCellValue(businessDataVO.getValidOrderCount());
            row.getCell(4).setCellValue(businessDataVO.getUnitPrice());
            // 明细数据
            for (int i = 0; i < 30; i++) {
                LocalDate date = dateBegin.plusDays(i);
                // 查询某一天的营业数据
                BusinessDataVO businessData = workspaceService.getBusinessData(LocalDateTime.of(date, LocalTime.MIN), LocalDateTime.of(date, LocalTime.MAX));
                // 获得某一行
                row = sheet.getRow(7 + i);
                row.getCell(1).setCellValue(date.toString());
                row.getCell(2).setCellValue(businessData.getTurnover());
                row.getCell(3).setCellValue(businessData.getValidOrderCount());
                row.getCell(4).setCellValue(businessData.getOrderCompletionRate());
                row.getCell(5).setCellValue(businessData.getUnitPrice());
                row.getCell(6).setCellValue(businessData.getNewUsers());
            }

            // 3.通过输出流，将 Excel 下载到客户端浏览器
            ServletOutputStream out = response.getOutputStream();
            excel.write(out);

            // 4.关闭资源
            out.close();
            excel.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public BusinessComparisonVO compareBusiness(LocalDate currentBegin, LocalDate currentEnd, LocalDate baselineBegin, LocalDate baselineEnd) {
        // 比较选定日期间的数据

        // 先进行日期检查
        validateComparisonRanges(currentBegin,currentEnd,baselineBegin,baselineEnd);

        // 查询两段对比的日期数据
        BusinessPeriodStatsDTO currentStats = queryPeriodStats(currentBegin, currentEnd);
        BusinessPeriodStatsDTO baselineStats = queryPeriodStats(baselineBegin, baselineEnd);

        // 读取两个金额并规范为2位小数
        BigDecimal currentTurnover  = currentStats.getTurnover().setScale(2, RoundingMode.HALF_UP);
        BigDecimal baselineTurnover = baselineStats.getTurnover().setScale(2, RoundingMode.HALF_UP);

        // 计算两个周期的客单价和营业差额
        BigDecimal currentUnitPrice  = calculateUnitPrice(currentTurnover, currentStats.getValidOrderCount());
        BigDecimal baselineUnitPrice = calculateUnitPrice(baselineTurnover, baselineStats.getValidOrderCount());
        BigDecimal turnoverDifference  = currentTurnover.subtract(baselineTurnover);

        // 计算营业额变化率
        BigDecimal turnoverChangeRate; // 变化率
        if(baselineTurnover.compareTo(BigDecimal.ZERO) == 0){ // 判断基准营业额是否为 0
            turnoverChangeRate = null;
        }else{ // 计算变化率
            turnoverChangeRate = turnoverDifference.divide(
                    baselineTurnover, // 除数
                    4, // 结果保留 4 位小数
                    RoundingMode.HALF_UP // 四舍五入
            );
        }

        // 封装返回
        BusinessComparisonVO result = new BusinessComparisonVO();

        result.setCurrentBegin(currentBegin);
        result.setCurrentEnd(currentEnd);
        result.setBaselineBegin(baselineBegin);
        result.setBaselineEnd(baselineEnd);

        result.setCurrentValidOrderCount(currentStats.getValidOrderCount());
        result.setBaselineValidOrderCount(baselineStats.getValidOrderCount());

        result.setCurrentTurnover(currentTurnover);
        result.setBaselineTurnover(baselineTurnover);

        result.setCurrentUnitPrice(currentUnitPrice);
        result.setBaselineUnitPrice(baselineUnitPrice);

        result.setTurnoverDifference(turnoverDifference);
        result.setTurnoverChangeRate(turnoverChangeRate);

        return result;
    }

    // 根据条件统计订单数量
    private Integer getOrderCount(LocalDateTime begin,LocalDateTime end,Integer status){
        Map map = new HashMap<>();
        map.put("begin",begin);
        map.put("end",end);
        map.put("status",status);

        return orderMapper.countByMap(map);
    }

    // 校验对比时间范围
    private void validateComparisonRanges(LocalDate currentBegin, LocalDate currentEnd,
                                          LocalDate baselineBegin, LocalDate baselineEnd){
        // 在查询数据库前，检查用户传入的两个时间段是否合法

        // 检查四个日期是否为空
        if(currentBegin == null || currentEnd == null || baselineBegin == null || baselineEnd == null){
            throw new BusinessComparisonParameterException("日期参数不能为空");
        }

        // 检查每个周期的开始日期不能晚于结束日期
        if(currentBegin.isAfter(currentEnd)){
            throw new BusinessComparisonParameterException("开始日期不能晚于结束日期");
        }
        if (baselineBegin.isAfter(baselineEnd)) {
            throw new BusinessComparisonParameterException("开始日期不能晚于结束日期");
        }

        // 检查两个周期的结束日期必须早于今天
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        if (!currentEnd.isBefore(today) || !baselineEnd.isBefore(today)) {
            throw new BusinessComparisonParameterException("统计周期结束日期必须早于今天");
        }

        // 检查每个周期最多31天
        long currentDays = ChronoUnit.DAYS.between(currentBegin, currentEnd) + 1;
        long baselineDays = ChronoUnit.DAYS.between(baselineBegin, baselineEnd) + 1;
        if(currentDays > 31 || baselineDays > 31){
            throw new BusinessComparisonParameterException("统计周期不能超过31天");
        }

        // 两个周期的天数必须相同
        if (currentDays != baselineDays) {
            throw new BusinessComparisonParameterException("当前周期与基准周期的天数必须相同");
        }

        // 基准周期必须完全早于当前周期
        if (!baselineEnd.isBefore(currentBegin)) {
            throw new BusinessComparisonParameterException("基准周期结束日期必须早于当前周期开始日期");
        }
    }

    // 查询某个周期的营业数据
    private BusinessPeriodStatsDTO queryPeriodStats(LocalDate beginDate, LocalDate endDate){
        // 查询指定时间范围内的有效订单数量和营业额

        // 将 LocalDate 转换为 LocalDateTime
        LocalDateTime begin = beginDate.atStartOfDay();
        LocalDateTime endExclusive = endDate.plusDays(1).atStartOfDay();

        // 调用查询方法
        BusinessPeriodStatsDTO businessPeriodStatsDTO = orderMapper.aggregateCompletedPeriod(begin, endExclusive, Orders.COMPLETED);
        if(businessPeriodStatsDTO == null || businessPeriodStatsDTO.getValidOrderCount() == null || businessPeriodStatsDTO.getTurnover() == null){
            throw new IllegalStateException("经营统计结果不完整");
        }
        return businessPeriodStatsDTO;
    }

    // 计算客单价
    private BigDecimal calculateUnitPrice(BigDecimal turnover, long validOrderCount){
        // 根据营业额和有效订单数计算平均每单金额

        // 如果笔数为 0 返回 0.0
        if(validOrderCount == 0){
            return BigDecimal.ZERO // 表示数字 0
                    .setScale(2); // 表示设置两位小数
        }

        // 营业额 / 有效订单数，保留两位小数，四舍五入
        return turnover.divide(
                BigDecimal.valueOf(validOrderCount), // 除数
                2, // 保留两位小数
                RoundingMode.HALF_UP // 四舍五入
        );
    }

}
