package com.wanzhongxiang.service;

import reactor.core.publisher.Flux;

public interface AiChatService {

    String AiChat(String message);

    Flux<String> AiChatFlux(String message);

}
