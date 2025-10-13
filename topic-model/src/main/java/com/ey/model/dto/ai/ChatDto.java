package com.ey.model.dto.ai;

import lombok.Data;

@Data
public class ChatDto {
    // 消息
    private String prompt;
    // 对话id
    private String chatId;
    // 模式
    private String model;
    // 内容
    private String content;
    // 记忆id
    private Long memoryId;
    // 昵称用于渲染
    private String nickname;

}
