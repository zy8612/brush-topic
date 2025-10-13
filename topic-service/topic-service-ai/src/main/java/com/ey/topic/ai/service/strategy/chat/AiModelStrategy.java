package com.ey.topic.ai.service.strategy.chat;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ey.common.security.utils.SecurityUtils;
import com.ey.model.dto.ai.ChatDto;
import com.ey.model.entity.ai.AiHistory;
import com.ey.topic.ai.constant.PromptConstant;
import com.ey.topic.ai.enums.AiStatusEnums;
import com.ey.topic.ai.mapper.AiHistoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiModelStrategy implements ChatStrategy {

    private final AiHistoryMapper aiHistoryMapper;
    private final ChatClient chatClient;

    @Override
    public Flux<String> handleChat(ChatDto chatDto) {
        /**
         * 模型模式根据用户输入的题目类型进行发送面试题
         */
        // 获取当前用户Id
        Long currentId = SecurityUtils.getCurrentId();
        // 当前账户
        String currentName = SecurityUtils.getCurrentName();

        // 提示词
        String prompt = null;
        // 查询当前对话记录
        // 获取上一次对话
        AiHistory aiHistory = AiChatUtils.getCurrentHistory(aiHistoryMapper, chatDto);
        // 处理对话逻辑
        if (aiHistory == null) {
            // 1.用户第一次对话需要题目类型的校验
            return verifyPrompt(chatDto, true, currentName, currentId);
        } else {
            // 2.获取上一条记录的状态
            Integer status = aiHistory.getStatus();

            // 上一条记录是ai提出问题
            if (AiStatusEnums.SEND_TOPIC.getCode().equals(status)) {
                // 用户就得输入答案
                prompt = "你提出面试题：" + aiHistory.getContent()
                        + "用户回答：" + chatDto.getPrompt() + "  " + PromptConstant.EVALUATE
                        + "结尾最后一定要一定要返回下面这句话\n" +
                        " > 请输入'**继续**'或者输入新的**题目类型**'";
                // 用户输入答案后将状态改为评估答案
                return saveEvaluateAnswer(prompt, aiHistory, AiStatusEnums.EVALUATE_ANSWER.getCode(), chatDto, currentName, currentId);
            }
            // 上一条记录是评估答案说明ai已经评估完了用户就得输入继续或者新专题
            if (AiStatusEnums.EVALUATE_ANSWER.getCode().equals(status)) {
                // 用户输入继续还是新专题
                if ("继续".equals(chatDto.getPrompt())) {
                    // 查询前1条发出的面试题
                    AiHistory lastTopic = AiChatUtils.getLastSendTopic(aiHistoryMapper, chatDto);
                    if (lastTopic != null) {
                        chatDto.setPrompt(lastTopic.getTitle());
                    }
                }
                return verifyPrompt(chatDto, false, currentName, currentId);
            }
        }
        return null;
    }

    /**
     * 校验用户输入的专题是否合法，并返回不重复的题目给用户
     */
    private Flux<String> verifyPrompt(ChatDto chatDto, boolean isFirst, String currentName, Long currentId) {
        String prompt;
        // 如果不是第一次需要查询发送过的题目，防止发送相同题目
        if (!isFirst) {
            // 获取之前发送过的题目
            LambdaQueryWrapper<AiHistory> aiHistoryLambdaQueryWrapper = new LambdaQueryWrapper<>();
            aiHistoryLambdaQueryWrapper.eq(AiHistory::getUserId, SecurityUtils.getCurrentId());
            aiHistoryLambdaQueryWrapper.eq(AiHistory::getChatId, chatDto.getChatId());
            aiHistoryLambdaQueryWrapper.eq(AiHistory::getStatus, AiStatusEnums.SEND_TOPIC.getCode());
            List<AiHistory> histories = aiHistoryMapper.selectList(aiHistoryLambdaQueryWrapper);
            // 拼接已发送的题目
            String topics = histories.stream()
                    .map(AiHistory::getOriginalTitle) // 映射到originalTitle
                    .filter(title -> title != null && !title.trim().isEmpty()) // 过滤掉null和空字符串
                    .collect(Collectors.joining("\n")); // 使用换行符连接
            prompt = PromptConstant.CHECK_TOPIC_TYPE +
                    "当前对话记录已经出过的面试题\n【：" + topics + "】" +
                    "就不可以在出了\n用户输入的面试题类型：【" + chatDto.getPrompt() + "】";
        } else {
            // 第一次直接发送用户输入的类型
            prompt = PromptConstant.CHECK_TOPIC_TYPE + "\n用户输入的面试题类型：【" + chatDto.getPrompt() + "】";
        }
        // 发起对话
        String content = chatClient.prompt()
                .user(prompt)
                .call()
                .content();
        log.info("verifyPrompt================>?: {}", content);
        // 没通过
        if (content != null && content.equalsIgnoreCase("false")) {
            // 返回一个提示信息给用户
            return Flux.just("❌请输入正确的题目类型\uD83D\uDE0A");
        }
        // 通过了返回题目
        // 构造提示语
        prompt = "### 【" + chatDto.getPrompt() + "】类型 💡\n\n" +
                "## 面试题目：\n" +
                "**" + content + "**\n\n" +
                "> " + AiChatUtils.getRandomEncouragement();

        // 保存
        saveSendTopicHistory(chatDto, prompt, currentName, currentId, content);
        // 返回
        return Flux.just(prompt);
    }

    /**
     * 保存提供面试题的记录
     */
    private void saveSendTopicHistory(ChatDto chatDto, String prompt, String currentName, Long currentId, String originalTitle) {
        // 封装记录
        AiHistory aiHistory = new AiHistory();
        // 查询是是否存在父级id
        LambdaQueryWrapper<AiHistory> aiHistoryLambdaQueryWrapper = new LambdaQueryWrapper<>();
        aiHistoryLambdaQueryWrapper.eq(AiHistory::getChatId, chatDto.getChatId());
        aiHistoryLambdaQueryWrapper.eq(AiHistory::getParent, 1);
        aiHistoryLambdaQueryWrapper.orderByDesc(AiHistory::getCreateTime);
        AiHistory parent = aiHistoryMapper.selectOne(aiHistoryLambdaQueryWrapper);
        // 不存在父级id
        if (ObjectUtil.isEmpty(parent) && chatDto.getMemoryId() == 1) {
            // 设置当前为父层级
            aiHistory.setParent(1);
        } else {
            aiHistory.setParent(0);
        }
        aiHistory.setChatId(chatDto.getChatId());
        aiHistory.setAccount(currentName);
        aiHistory.setUserId(currentId);
        aiHistory.setContent(prompt);
        aiHistory.setTitle(chatDto.getPrompt());
        aiHistory.setStatus(AiStatusEnums.SEND_TOPIC.getCode());
        aiHistory.setMode(chatDto.getModel());
        aiHistory.setOriginalTitle(originalTitle);
        aiHistoryMapper.insert(aiHistory);
    }

    /**
     * 保存评估回答的记录
     */
    private Flux<String> saveEvaluateAnswer(String prompt, AiHistory aiHistory, Integer status, ChatDto chatDto, String currentName, Long currentId) {
        // 拼接信息
        StringBuffer fullReply = new StringBuffer();
        // 获取对回答的评价
        Flux<String> content = chatClient.prompt()
                .user(prompt)
                .stream()
                .content();
        Flux<String> stringFlux = content.flatMap(response -> {
            fullReply.append(response);
            return Flux.just(response);
        }).doOnComplete(() -> {
            log.info("执行完成保存历史记录");
            AiHistory history = new AiHistory();
            // 如果是ai模式需要保存原始题目
            if (StrUtil.isNotBlank(aiHistory.getOriginalTitle())) {
                history.setOriginalTitle(aiHistory.getOriginalTitle());
            }
            history.setChatId(chatDto.getChatId());
            history.setAccount(currentName);
            history.setUserId(currentId);
            history.setContent(fullReply.toString());
            history.setTitle(chatDto.getPrompt());
            history.setStatus(status);
            history.setMode(chatDto.getModel());
            history.setParent(0);
            aiHistoryMapper.insert(history);
        });
        return stringFlux;
    }

}
