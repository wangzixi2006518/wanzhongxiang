package com.wanzhongxiang.controller.admin;

import com.wanzhongxiang.context.BaseContext;
import com.wanzhongxiang.dto.AiChatDTO;
import com.wanzhongxiang.dto.AiConversationCreateDTO;
import com.wanzhongxiang.entity.AiConversation;
import com.wanzhongxiang.entity.AiMessage;
import com.wanzhongxiang.rag.RagRetrievalService;
import com.wanzhongxiang.rag.RagAnswerService;
import com.wanzhongxiang.rag.RuleRetrievalContext;
import com.wanzhongxiang.result.Result;
import com.wanzhongxiang.service.AiChatService;
import com.wanzhongxiang.service.AiConversationService;
import com.wanzhongxiang.service.AiMessageService;
import com.wanzhongxiang.vo.*;
import com.wanzhongxiang.workflow.OrderDispatchWorkflowService;
import io.swagger.annotations.Api;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.annotations.Delete;
import org.apache.xmlbeans.impl.xb.xsdschema.Public;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.wanzhongxiang.constant.AiPromptConstant.AI_HEALTH_TEST;

@RestController
@RequestMapping("/admin/ai")
@Api(tags = "Ai 相关接口")
@Slf4j
public class AiController {

    @Autowired
    private AiChatService aiChatService;
    @Autowired
    private AiConversationService aiConversationService;
    @Autowired
    private AiMessageService aiMessageService;
    @Autowired
    private OrderDispatchWorkflowService orderDispatchWorkflowService;
    @Autowired
    private RagRetrievalService ragRetrievalService;
    @Autowired
    private RagAnswerService ragAnswerService;

    @PostMapping("/chat")
    public Result<AiChatVO> chat(@RequestBody @Valid AiChatDTO aiChatDTO){

        String s = aiChatService.AiChat(aiChatDTO.getMessage()); // 用户的问题
        AiChatVO aiChatVO = new AiChatVO();
        aiChatVO.setContent(s);

        return Result.success(aiChatVO);
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Map<String,Object>>> chatStream(@RequestBody @Valid AiChatDTO aiChatDTO){
        //一连串陆续到来的事件 <事件名称 <事件里的数据<例如 {"content":"你好"}>>>

        // 在回答正文前会发送 meta，告知前端本次 AI 回答的 messageId（给每次 AI 回答编号）
        // 要放在 chatStream 方法内，否则会创建实例时执行一次，此后的请求都复用这个字段
        String messageId = "msg_" + UUID.randomUUID().toString();
        // 读取当前登录管理员 ID
        Long currentId = BaseContext.getCurrentId();
        AiConversation aiConversation = aiMessageService.beginChat(currentId,aiChatDTO.getConversationId(),aiChatDTO.getMessage(),messageId);

        // 整合会话内容
        // CANCEL 可能由另一个线程触发，一个执行append()，另一个toString()，两个线程同时操作同一个对象
        // 所以 StringBuilder 要改成 StringBuffer
        StringBuffer chunkBuffer = new StringBuffer();

        // 创建一个表示 n 秒的时长对象，本身不执行等待
        Duration duration = Duration.ofSeconds(30);

        RuleRetrievalContext retrievalContext = new RuleRetrievalContext();

        // 将 AiChatFlux 中的元素处理为 Flux<ServerSentEvent<Map<String, Object>>>
        Flux<ServerSentEvent<Map<String, Object>>> map = aiChatService.AiChatFlux(aiChatDTO.getMessage(), aiConversation.getId(), currentId,retrievalContext) // 取出用户的问题
                // 模型流停滞超时
                .timeout(duration)
                // 模型源 Flux<String> 每收到一个 chunk 就追加到 StringBuffer（暂存到内存）
                .doOnNext(chunk -> chunkBuffer.append(chunk))
                // 每收到一个元素就转换一次，每个元素都是模型送来的一段文字，命名为 chunk
                .map(chunk ->
                        ServerSentEvent.<Map<String, Object>>builder() // 创建一个 SSE 事件
                                .event("delta") // 事件命名为 delta，delta 是接口设计时选的名字，对应前端的 delta
                                .data(Map.of("content", chunk)) // 当前片段放进事件数据
                                .build()) // 事件对象构造完成
                // publishOn：从这里开始，后面的流式操作换一条适合干阻塞任务的线程来执行
                .publishOn(Schedulers.boundedElastic());
                // 落库完成搬进 tail，不需要 doOnComplete
                // 看到异常之后做的事，ex：上游抛出来的异常
//                .doOnError(ex -> {
//                    aiMessageService.failAssistant(messageId,aiConversation.getId(),chunkBuffer.toString());
//                })
                // 更新内容和状态（写入数据库）
//                .doOnComplete(() -> {
//                    aiMessageService.completeAssistant(messageId,aiConversation.getId(),chunkBuffer.toString());
//                });

        // 结束
        Flux<ServerSentEvent<Map<String, Object>>> done = Flux.just(
                ServerSentEvent.<Map<String, Object>>builder()
                        .event("done") // 命名为 done -> "完成"
                        .data(Map.<String, Object>of()) // {}，结束不用 token
                        .build() // 构造
        );

        // 开始时
        ServerSentEvent<Map<String, Object>> metaEvent = ServerSentEvent.<Map<String, Object>>builder()
                .event("meta") // 命名为 meta -> AI 开始生成之前
                .data(Map.of("messageId",messageId,"conversationId",aiConversation.getId())) // 放进事件数据
                .build(); // 构造

        // 核对校验
        Flux<ServerSentEvent<Map<String, Object>>> tail =  Flux.defer(() -> { // defer：先保存“要做的动作”，被订阅时才执行
            // 从 chunkBuffer 读取完整 answer
            String answer = chunkBuffer.toString();

            // 调用主聊天来源核对方法 resolveSources(answer, retrievalContext)
            List<RagSourceVO> sources = ragAnswerService.resolveSources(answer, retrievalContext);

            // 构造事件名为 sources 的事件
            ServerSentEvent<Map<String,Object>> sourceEvent =
                    ServerSentEvent.<Map<String,Object>>builder()
                            .event("sources")
                            .data(Map.of("messageId", messageId, "sources", sources))
                            .build();

            // 调用一次 completeAssistant，保存同一个完整 answer
            aiMessageService.completeAssistant(messageId,aiConversation.getId(),answer);

            // 先发送来源事件，再接上已有的结束事件流
            return Flux.just(sourceEvent).concatWith(done);
        });

        String errorCode = "MODEL_ERROR";
        String errorMessage = "生成失败，请稍后重试";

        // 正常：meta -> delta... -> sources -> done；失败：meta -> delta... -> error
        return map.startWith(metaEvent) // map 之前先发送一个 meta 事件
                .concatWith(tail) // 模型流正常结束后执行引用核对、完成落库和来源/结束事件，tail：引用核对与正常收尾
                .doOnError(ex -> { // 两段中的任何一段出错，都保存失败状态
                    aiMessageService.failAssistant(messageId,aiConversation.getId(),chunkBuffer.toString());
                })
                .onErrorResume(ex -> { // 发生异常时，切换为备用 Flux
                    log.error("当前 conversationId：{}，当前 messageId：{}，收到异常 ex：",aiConversation.getId(),messageId,ex);
                    return Flux.just(ServerSentEvent.<Map<String,Object>>builder()
                            .event("error")
                            .data(Map.of("errorCode",errorCode,
                                    "errorMessage",errorMessage))
                            .build()
                    );
                })

                // tail 处理正常结束；doOnError 处理模型生成与来源核对的错误
                // 外层 doFinally则能观察包括 CANCEL 在内的终止原因，所以后续要在这里识别取消
                .doFinally(signalType -> {
                    log.info("conversationId = {}, messageId = {}, signalType = {}", aiConversation.getId(), messageId, signalType);
                    if(signalType == SignalType.CANCEL){
                        // 取一次内容快照，把 chunkBuffer 当前的内容复制成一个普通 String
                        String snapshot = chunkBuffer.toString();
                        // 用户取消了 SSE 连接，把已经生成的半截回答保存到数据库，但是数据库操作会阻塞，所以把保存任务交给 boundedElastic 线程池，让它找另一个线程执行
                        Schedulers.boundedElastic().schedule(() -> {
                            try {
                                aiMessageService.cancelAssistant(messageId,aiConversation.getId(),snapshot);
                            } catch (Exception e) {
                                log.error("取消助手消息落库失败，messageId = {}, conversationId = {}", messageId, aiConversation.getId(), e);
                            }
                        });
                    }
                }); // 最后记录日志
    }

    @GetMapping("/health")
    public Result<AiHealthVO> getHealth(){

        // 设置 VO 状态
        AiHealthVO aiHealthVO = new AiHealthVO();
        aiHealthVO.setProvider("deepseek");
        aiHealthVO.setRagReady(false);

        try {
            String answer = aiChatService.AiChat(AI_HEALTH_TEST); // try catch 边界问题，需要放在try 里面

            if(answer != null && !answer.isBlank()){
                aiHealthVO.setStatus("UP");
            }else{
                aiHealthVO.setStatus("DOWN");
            }
        } catch (Exception e) {
            aiHealthVO.setStatus("DOWN");
        }

        return Result.success(aiHealthVO);
    }

    @PostMapping("/conversations")
    public Result<AiConversationVO> conversations(@RequestBody(required = false)AiConversationCreateDTO aiConversationCreateDTO){

        // 有可能是 null 值，null 值要赋值防止空指针异常
        String  title = aiConversationCreateDTO == null ? null : aiConversationCreateDTO.getTitle();

        // 判断长度是否合格
        if (title != null && !title.isBlank()){
            if(title.trim().length() > 60){
                return Result.error("标题不能超过60字");
            }
            log.info("创建会话:{}",title);
        }

        // 调用创建会话方法
        Long currentId = BaseContext.getCurrentId();
        AiConversation aiConversation = aiConversationService.create(currentId, title);

        // 封装 vo
        AiConversationVO vo = toVOByConversation(aiConversation);

        return Result.success(vo);
    }

    // 查看用户会话列表
    @GetMapping("/conversations")
    public Result<List<AiConversationVO>> getAiConversationsList(){

        // 从BaseContext拿到管理员 id 获取对话列表
        Long currentId = BaseContext.getCurrentId();
        List<AiConversation> aiConversations = aiConversationService.listByEmployeeId(currentId);

        // 将每个实体转换成VO
        List<AiConversationVO> aiConversationsListVO = aiConversations.stream()
                .map(this::toVOByConversation)
                .toList();

        return Result.success(aiConversationsListVO);
    }

    // 查看当前 conversationId 会话的信息
    @GetMapping("/conversations/{conversationId}/messages")
    public Result<List<AiMessageVO>> getAiConversationsListById(@PathVariable String conversationId){
        // 获取管理员 ID
        Long currentId = BaseContext.getCurrentId();

        // 调用方法获得当前管理员在当前会话的信息
        List<AiMessage> aiMessages = aiMessageService.listOwnedByConversationId(conversationId, currentId);

        // 封装VO并返回
        List<AiMessageVO> aiMessagesListVO = aiMessages.stream().map(this::toVOByMessage).toList();
        return Result.success(aiMessagesListVO);
    }

    @DeleteMapping("/conversations/{conversationId}")
    public Result deleteConversation(@PathVariable String conversationId){
        Long currentId = BaseContext.getCurrentId();
        aiConversationService.deleteOwnedById(conversationId, currentId);
        aiChatService.clearConversationMemory(conversationId);
        return Result.success();
    }

    @PutMapping("/conversations/{conversationId}/title")
    public Result<AiConversationVO> renameConversation(@PathVariable String conversationId,
                                                      @RequestBody AiConversationCreateDTO body) {
        String title = body.getTitle();
        if (title == null || title.isBlank() || title.strip().length() > 60) {
            return Result.error("标题需为1到60字");
        }
        AiConversation renamed = aiConversationService.renameOwnedById(
                conversationId, BaseContext.getCurrentId(), title);
        return Result.success(toVOByConversation(renamed));
    }

    @PostMapping("/rag/answer")
    public Result<RagAnswerVO> answerRules(@RequestBody AiChatDTO body) {
        String question = body.getMessage();
        if (question == null || question.isBlank() || question.strip().length() > 500) {
            return Result.error("问题需为1到500字");
        }
        return Result.success(ragAnswerService.answerWithSources(question.strip()));
    }

    @GetMapping("/orders/{orderId}/dispatch-analysis")
    public Result<OrderDispatchAnalysisVO> analyzeDispatch(@PathVariable Long orderId){
        // 给 analyze(orderId) 添加管理端 HTTP 入口

        // 判断 orderId 是否合法
        if(orderId <= 0){
            return Result.error("订单 id 必须为正数");
        }

        // 取得结果 VO 并返回
        OrderDispatchAnalysisVO analysisVO = orderDispatchWorkflowService.analyze(orderId);
        return Result.success(analysisVO);
    }

    @PostMapping("/rag/index")
    public Result<Integer> buildRuleIndex(){
        // 准备规则索引
        int buildIndex = ragRetrievalService.buildIndex();
        return Result.success(buildIndex);
    }


    private AiConversationVO toVOByConversation(AiConversation aiConversation){

        // 赋值返回给前端
        AiConversationVO aiConversationVO = new AiConversationVO();
        aiConversationVO.setId(aiConversation.getId());
        aiConversationVO.setTitle(aiConversation.getTitle());
        aiConversationVO.setCreatedAt(aiConversation.getCreateTime());
        aiConversationVO.setUpdatedAt(aiConversation.getUpdateTime());

        return aiConversationVO;
    }

    private AiMessageVO toVOByMessage(AiMessage aiMessage){

        AiMessageVO aiMessageVO = new AiMessageVO();
        aiMessageVO.setId(aiMessage.getId());
        aiMessageVO.setConversationId(aiMessage.getConversationId());
        aiMessageVO.setRole(aiMessage.getRole());
        aiMessageVO.setContent(aiMessage.getContent());
        aiMessageVO.setStatus(aiMessage.getStatus());
        aiMessageVO.setCreatedAt(aiMessage.getCreateTime());
        aiMessageVO.setUpdatedAt(aiMessage.getUpdateTime());
        return aiMessageVO;

    }

}
