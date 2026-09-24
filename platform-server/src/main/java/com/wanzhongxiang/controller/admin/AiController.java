package com.wanzhongxiang.controller.admin;

import com.wanzhongxiang.dto.AiChatDTO;
import com.wanzhongxiang.result.Result;
import com.wanzhongxiang.service.AiChatService;
import com.wanzhongxiang.vo.AiChatVO;
import com.wanzhongxiang.vo.AiHealthVO;
import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.apache.xmlbeans.impl.xb.xsdschema.Public;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

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

    @PostMapping("/chat")
    public Result<AiChatVO> chat(@RequestBody AiChatDTO aiChatDTO){

        String s = aiChatService.AiChat(aiChatDTO.getMessage()); // 用户的问题
        AiChatVO aiChatVO = new AiChatVO();
        aiChatVO.setContent(s);

        return Result.success(aiChatVO);
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Map<String,String>>> chatStream(@RequestBody AiChatDTO aiChatDTO){
        //一连串陆续到来的事件 <事件名称 <事件里的数据<例如 {"content":"你好"}>>>

        // 将 AiChatFlux 中的元素处理为 Flux<ServerSentEvent<Map<String, String>>>
        Flux<ServerSentEvent<Map<String, String>>> map = aiChatService.AiChatFlux(aiChatDTO.getMessage()) // 取出用户的问题
                .map(chunk -> // 每收到一个元素就转换一次，每个元素都是模型送来的一段文字，命名为 chunk
                        ServerSentEvent.<Map<String, String>>builder() // 创建一个 SSE 事件
                                .event("delta") // 事件命名为 delta，delta 是接口设计时选的名字，对应前端的 delta
                                .data(Map.of("content", chunk)) // 当前片段放进事件数据
                                .build()); // 事件对象构造完成

        // 结束
        Flux<ServerSentEvent<Map<String, String>>> done = Flux.just(
                ServerSentEvent.<Map<String, String>>builder()
                        .event("done") // 命名为 done -> "完成"
                        .data(Map.<String, String>of()) // {}，结束不用 token
                        .build() // 构造
        );

        // meta 的用途是给本次回答附上元信息，所以需要一个本次消息的唯一 ID
        // 要放在 chatStream 内，否则会创建实例时执行一次，此后的请求都复用这个字段
        String messageId = "msg_" + UUID.randomUUID().toString();

        // 开始时
        ServerSentEvent<Map<String, String>> metaEvent = ServerSentEvent.<Map<String, String>>builder()
                .event("meta") // 命名为 meta -> AI 开始生成之前
                .data(Map.of("messageId",messageId)) // 放进事件数据
                .build(); // 构造

        String errorCode = "MODEL_ERROR";
        String errorMessage = "生成失败，请稍后重试";

        // meta -> delta1 -> delta2 -> ... -> done / meta -> delta1 -> 异常 -> error
        return map.startWith(metaEvent) // map 之前先发送一个 meta 事件
                .concatWith(done) // map 正常结束时发的 done 事件
                .onErrorResume(ex -> { // 发生异常时，切换为备用 Flux
                    return Flux.just(ServerSentEvent.<Map<String,String>>builder()
                            .event("error")
                            .data(Map.of("errorCode",errorCode,
                                    "errorMessage",errorMessage))
                            .build()
                    );
                });
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


}
