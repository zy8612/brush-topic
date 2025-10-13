package com.ey.topic.ai.service.strategy.chat;

import com.ey.model.dto.ai.ChatDto;
import reactor.core.publisher.Flux;

public interface ChatStrategy {
    Flux<String> handleChat(ChatDto chatDto);
}
