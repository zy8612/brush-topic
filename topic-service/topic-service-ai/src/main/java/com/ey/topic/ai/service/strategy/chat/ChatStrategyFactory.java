package com.ey.topic.ai.service.strategy.chat;

import com.ey.topic.ai.constant.AiConstant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ChatStrategyFactory {
    private final Map<String, ChatStrategy> strategyMap;

    // 注入所有 ChatStrategy 实现类，自动匹配策略
    @Autowired
    public ChatStrategyFactory(List<ChatStrategy> strategyList) {
        this.strategyMap = new HashMap<>();
        // 手动绑定策略与模型标识（需与 AiConstant 对应）
        for (ChatStrategy strategy : strategyList) {
            if (strategy instanceof SystemModelStrategy) {
                strategyMap.put(AiConstant.SYSTEM_MODEL, strategy);
            } else if (strategy instanceof AiModelStrategy) {
                strategyMap.put(AiConstant.AI_MODEL, strategy);
            } else if (strategy instanceof MixModelStrategy) {
                strategyMap.put(AiConstant.MIX_MODEL, strategy);
            }
        }
    }

    // 获取策略（默认返回AI模式）
    public ChatStrategy getStrategy(String model) {
        return strategyMap.getOrDefault(model, strategyMap.get(AiConstant.AI_MODEL));
    }
}
