package com.wanzhongxiang.workflow;

import com.wanzhongxiang.constant.AiPromptConstant;
import com.wanzhongxiang.constant.MessageConstant;
import com.wanzhongxiang.entity.Orders;
import com.wanzhongxiang.exception.OrderBusinessException;
import com.wanzhongxiang.mapper.OrderMapper;
import com.wanzhongxiang.rag.RagAnswerService;
import com.wanzhongxiang.rag.RagRetrievalService;
import com.wanzhongxiang.vo.OrderDispatchAnalysisVO;
import com.wanzhongxiang.vo.RagSourceVO;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class OrderDispatchWorkflowService {
    // 通过 prepare(Long orderId) 按固定顺序准备材料

    // 构造方法注入现有 OrderMapper 和 RagRetrievalService
    private final OrderMapper orderMapper;
    private final RagRetrievalService ragRetrievalService;
    private final RagAnswerService ragAnswerService;
    private final ChatClient chatClient;

    // 把 context 中的数据整理成一段文字，后面由 analyze() 发给模型
    private static final Map<Integer,String> STATUS_NAMES = Map.of(
            Orders.PENDING_PAYMENT,"待付款",
            Orders.TO_BE_CONFIRMED, "待接单",
            Orders.CONFIRMED, "已接单",
            Orders.DELIVERY_IN_PROGRESS, "派送中",
            Orders.COMPLETED, "已完成",
            Orders.CANCELLED, "已取消"
    );

    public OrderDispatchWorkflowService(OrderMapper orderMapper, RagRetrievalService ragRetrievalService, RagAnswerService ragAnswerService, ChatClient.Builder builder) {
        this.orderMapper = orderMapper;
        this.ragRetrievalService = ragRetrievalService;
        this.ragAnswerService = ragAnswerService;
        DeepSeekChatOptions build = DeepSeekChatOptions.builder().temperature(0.0).build();
        chatClient = builder.defaultSystem(AiPromptConstant.ORDER_DISPATCH_SYSTEM_PROMPT).defaultOptions(build).build();
    }

    // 查询订单、检索规则，给后续流程提供 OrderDispatchContext
    public OrderDispatchContext prepare(Long orderId){
        // 每次准备分析某一笔订单时，由调用者显式调用

        // 1.判断 orderId 是否合法
        if(orderId == null || orderId <= 0){
            throw new IllegalArgumentException("订单 id 必须为正数");
        }

        // 2.调用 getById 查找订单
        Orders orders = orderMapper.getById(orderId);

        // 3.检查Orders.status属于当前六个订单状态
        if(orders == null){
            // 停止检索，没有订单是业务结果，不是规则不足
            throw new  OrderBusinessException(MessageConstant.ORDER_NOT_FOUND); // 抛订单不存在
        }
        Integer status = orders.getStatus(); // 提取出来防止自动拆箱报空指针异常
        if(status == null || status > 6 || status < 1){
            // 停止检索，没有订单是业务结果，不是规则不足
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR); // 抛订单状态异常
        }

        // 4.记录LocalDateTime.now()作为queriedAt，保存本轮候选
        LocalDateTime queriedAt = LocalDateTime.now();
        List<Document> search = ragRetrievalService.search("开始派送需要满足什么条件？");

        // 5.组装 OrderDispatchContext 并返回
        OrderDispatchContext orderDispatchContext = new OrderDispatchContext();
        orderDispatchContext.setOrderId(orders.getId());
        orderDispatchContext.setOrderStatus(orders.getStatus());
        orderDispatchContext.setQueriedAt(queriedAt);
        orderDispatchContext.setRuleDocuments(search);

        return orderDispatchContext;
    }

    // 把订单事实、状态名称和规则整理成文字，给大模型阅读
    private String buildAnalysisInput(OrderDispatchContext context){

        // 从 context 取出数据库事实
        String facts = "【本次数据库查询事实】\n"
                + "订单主键：" + context.getOrderId() + "\n"
                + "状态编码：" + context.getOrderStatus() + "\n"
                + "事实读取时间：" + context.getQueriedAt() + "\n"
                + "订单状态名称：" + STATUS_NAMES.get(context.getOrderStatus());

        // 复用已有方法，把本轮 Document 整理成规则文字
        String rules = "【本轮规则资料】\n"
                + ragAnswerService.buildContext(context.getRuleDocuments());

        // 告诉模型需要完成什么分析
        String task = "【分析任务】\n"
                + "依据上述查询事实和规则，说明这笔订单在读取时刻是否满足开始派送前提，并引用支持结论的规则片段。";

        return facts + "\n\n" + rules + "\n\n" + task;
    }

    // 串起准备、生成、引用核对，给调用者返回分析 VO
    public OrderDispatchAnalysisVO analyze(Long orderId){

        // 1.获得上下文 context，填入 VO
        OrderDispatchContext context = prepare(orderId);
        OrderDispatchAnalysisVO orderDispatchAnalysisVO = new OrderDispatchAnalysisVO();
        orderDispatchAnalysisVO.setOrderId(context.getOrderId());
        orderDispatchAnalysisVO.setOrderStatus(context.getOrderStatus());
        orderDispatchAnalysisVO.setQueriedAt(context.getQueriedAt());

        // 2.判断是否为空
        if(context.getRuleDocuments() == null || context.getRuleDocuments().isEmpty()){
            orderDispatchAnalysisVO.setAnswer("当前检索资料不足以回答该问题。");
            orderDispatchAnalysisVO.setSources(List.of()); // 空集合
            return orderDispatchAnalysisVO;
        }

        // 3.调用 buildAnalysisInput() 整理成文字，用工作流取得回答
        String buildAnalysisInput = buildAnalysisInput(context);
        String answer = chatClient.prompt()
                .user(buildAnalysisInput)
                .call()
                .content();

        // 4.判断回答是否为空
        if(answer == null || answer.isBlank()){
            throw new IllegalStateException("未得到有效模型文本");
        }

        // 5.判断回答是否为标准资料不足文本
        if(answer.strip().equals("当前检索资料不足以回答该问题。")){
            orderDispatchAnalysisVO.setAnswer("当前检索资料不足以回答该问题。");
            orderDispatchAnalysisVO.setSources(List.of());
            return orderDispatchAnalysisVO;
        }

        // 6.正常回答，获得真实资料
        List<RagSourceVO> sources = ragAnswerService.resolveSources(answer, context.getRuleDocuments());
        orderDispatchAnalysisVO.setAnswer(answer);
        orderDispatchAnalysisVO.setSources(sources);
        return orderDispatchAnalysisVO;
    }

}
