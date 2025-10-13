package com.ey.topic.ai.service.strategy.chat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ey.common.security.utils.SecurityUtils;
import com.ey.model.dto.ai.ChatDto;
import com.ey.model.entity.ai.AiHistory;
import com.ey.topic.ai.enums.AiStatusEnums;
import com.ey.topic.ai.mapper.AiHistoryMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.experimental.UtilityClass;

import java.util.List;

@UtilityClass
public class AiChatUtils {

    /**
     * 随机鼓励语
     */
    private static final String[] ENCOURAGEMENTS = {
            "💪 加油！你能行的！",
            "✨ 你可以的，相信自己！",
            "🔥 别放弃，再想想看～",
            "🌟 你已经很棒了，继续加油！",
            "🧠 慢慢来，答案就在前方～",
            "🚀 再试一次，你离成功不远了！",
            "💡 这道题对你来说不是问题！",
            "🎯 坚持到底就是胜利！",
            "🌈 每一次尝试都让你更接近成功！",
            "🌻 你的努力正在开花结果！",
            "⚡ 让智慧之光指引你前进！",
            "🦸 你就是自己的超级英雄！",
            "🌊 像海浪一样永不言弃！",
            "🎯 专注目标，你一定能做到！",
            "🚴 保持平衡，稳步前进！",
            "🧩 每个难题都是成长的拼图！",
            "🏆 冠军的潜力就在你心中！",
            "🌠 梦想就在不远处等着你！",
            "🦉 智慧正在你的脑中闪耀！",
            "⏳ 时间会证明你的坚持！"
    };

    public static String getRandomEncouragement() {
        int index = (int) (Math.random() * ENCOURAGEMENTS.length);
        return ENCOURAGEMENTS[index];
    }

    // 获取上一次对话记录（原 getCurrentHistory 方法）
    public static AiHistory getCurrentHistory(AiHistoryMapper aiHistoryMapper, ChatDto chatDto) {
        Long currentId = SecurityUtils.getCurrentId();
        String currentName = SecurityUtils.getCurrentName();

        Page<AiHistory> aiHistoryPage = new Page<>(1, 1);
        LambdaQueryWrapper<AiHistory> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(AiHistory::getChatId, chatDto.getChatId())
                .eq(AiHistory::getUserId, currentId)
                .eq(AiHistory::getAccount, currentName)
                .orderByDesc(AiHistory::getCreateTime);

        Page<AiHistory> pageResult = aiHistoryMapper.selectPage(aiHistoryPage, queryWrapper);
        return pageResult.getRecords().isEmpty() ? null : pageResult.getRecords().get(0);
    }

    // 查询最近一条发送的题目（原多处重复的查询逻辑）
    public static AiHistory getLastSendTopic(AiHistoryMapper aiHistoryMapper, ChatDto chatDto) {
        Long currentId = SecurityUtils.getCurrentId();
        LambdaQueryWrapper<AiHistory> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(AiHistory::getUserId, currentId)
                .eq(AiHistory::getStatus, AiStatusEnums.SEND_TOPIC.getCode())
                .eq(AiHistory::getChatId, chatDto.getChatId())
                .orderByDesc(AiHistory::getCreateTime)
                .last("limit 1");
        List<AiHistory> list = aiHistoryMapper.selectList(queryWrapper);
        return list.isEmpty() ? null : list.get(0);
    }
}
